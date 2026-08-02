#!/usr/bin/env bash
#
# Detect the newest fully-published JxBrowser release and decide whether the
# BOSS Chromium branding workflow should be dispatched for it.
#
# Inputs (env):
#   TARGET_VERSION   optional version override for manual/test runs
#   SOURCE_REPO      repository containing the branding workflow
#   RELEASES_REPO    repository containing published Chromium releases
#   SOURCE_TOKEN     optional token for reading workflow runs (defaults to GH_TOKEN)
#   RELEASES_TOKEN   optional token for reading releases (defaults to GH_TOKEN)
#   REQUIRED_ASSETS_FILE newline-delimited list of required release assets
#   GITHUB_OUTPUT    GitHub Actions output file (stdout outside Actions)
#
# Outputs:
#   latest_version=<version>
#   should_dispatch=true|false
#   reason=<short machine-readable reason>
set -euo pipefail

META_URL="${META_URL:-https://europe-maven.pkg.dev/jxbrowser/releases/com/teamdev/jxbrowser/jxbrowser/maven-metadata.xml}"
ARTIFACT_BASE_URL="${ARTIFACT_BASE_URL:-https://europe-maven.pkg.dev/jxbrowser/releases/com/teamdev/jxbrowser}"
SOURCE_REPO="${SOURCE_REPO:-risa-labs-inc/BossConsole}"
RELEASES_REPO="${RELEASES_REPO:-risa-labs-inc/BossConsole-Releases}"
WORKFLOW_FILE="${WORKFLOW_FILE:-build-chromium-branding.yml}"
REQUIRED_ASSETS_FILE="${REQUIRED_ASSETS_FILE:-.github/chromium-required-assets.txt}"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

gh_source() {
  if [[ -n "${SOURCE_TOKEN:-${GH_TOKEN:-}}" ]]; then
    GH_TOKEN="${SOURCE_TOKEN:-$GH_TOKEN}" gh "$@"
  else
    gh "$@"
  fi
}

gh_releases() {
  if [[ -n "${RELEASES_TOKEN:-${GH_TOKEN:-}}" ]]; then
    GH_TOKEN="${RELEASES_TOKEN:-$GH_TOKEN}" gh "$@"
  else
    gh "$@"
  fi
}

emit() { printf '%s\n' "$1" >> "$OUT"; }
noop() {
  local reason="$1"
  echo "→ $reason"
  emit "should_dispatch=false"
  emit "reason=$reason"
  exit 0
}

for cmd in curl gh jq; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "ERROR: required command is unavailable: $cmd" >&2
    exit 1
  }
done

[[ -f "$REQUIRED_ASSETS_FILE" ]] || {
  echo "ERROR: required asset list is unavailable: $REQUIRED_ASSETS_FILE" >&2
  exit 1
}
required_assets=()
while IFS= read -r asset; do
  [[ -n "$asset" ]] && required_assets+=("$asset")
done < "$REQUIRED_ASSETS_FILE"

latest="${TARGET_VERSION:-}"
if [[ -z "$latest" ]]; then
  metadata="$(curl -fsSL --retry 3 --retry-all-errors --max-time 30 "$META_URL")"
  latest="$(sed -n 's:.*<release>\([^<]*\)</release>.*:\1:p' <<< "$metadata")"
  latest="${latest%%$'\n'*}"
fi

[[ -n "$latest" ]] || {
  echo "ERROR: TeamDev metadata did not contain a release version" >&2
  exit 1
}
[[ "$latest" =~ ^[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || {
  echo "ERROR: invalid JxBrowser release version: $latest" >&2
  exit 1
}

emit "latest_version=$latest"
echo "JxBrowser candidate: $latest"

# Metadata can become visible just before every platform artifact reaches the
# repository edge. Avoid an expensive six-runner dispatch until all artifacts
# used by the branding matrix are resolvable.
artifacts=(
  jxbrowser
  jxbrowser-linux64
  jxbrowser-linux64-arm
  jxbrowser-mac
  jxbrowser-mac-arm
  jxbrowser-win64
  jxbrowser-win64-arm
)
for artifact in "${artifacts[@]}"; do
  artifact_url="$ARTIFACT_BASE_URL/$artifact/$latest/$artifact-$latest.jar"
  if ! curl -fsSL --retry 3 --retry-all-errors --max-time 30 \
    --range 0-0 --output /dev/null "$artifact_url"; then
    echo "::warning::JxBrowser $latest is in metadata, but $artifact is not resolvable yet"
    noop "artifact_not_ready"
  fi
done

tag="chromium-v$latest"
release_list="$(gh_releases release list \
  --repo "$RELEASES_REPO" \
  --limit 100 \
  --json tagName,isDraft)"
release_metadata="$(jq -c --arg tag "$tag" \
  'first(.[] | select(.tagName == $tag)) // empty' <<< "$release_list")"

if [[ -n "$release_metadata" ]]; then
  release_json="$(gh_releases release view "$tag" \
    --repo "$RELEASES_REPO" \
    --json isDraft,assets)"
  if [[ "$(jq -r '.isDraft' <<< "$release_json")" == "false" ]]; then
    missing_asset=""
    for asset in "${required_assets[@]}"; do
      if ! jq -e --arg asset "$asset" '.assets[] | select(.name == $asset)' <<< "$release_json" >/dev/null; then
        missing_asset="$asset"
        break
      fi
    done
    if [[ -z "$missing_asset" ]]; then
      noop "release_already_published"
    fi
    echo "::warning::$tag exists but is missing $missing_asset; a repair build is required"
  else
    echo "::warning::$tag exists as a draft; a repair build is required"
  fi
fi

# The branding workflow exposes the version in run-name, making this check
# deterministic without downloading run logs or inspecting event payloads.
run_title="BOSS Chromium for JxBrowser $latest"
runs_json="$(gh_source run list \
  --repo "$SOURCE_REPO" \
  --workflow "$WORKFLOW_FILE" \
  --limit 100 \
  --json displayTitle,status,conclusion)"
active_status="$(jq -r --arg title "$run_title" \
  'first(.[] | select(.displayTitle == $title and .status != "completed") | .status) // empty' \
  <<< "$runs_json")"
if [[ -n "$active_status" ]]; then
  echo "Matching branding run is already $active_status"
  noop "release_run_active"
fi

recent_failures="$(jq -r --arg title "$run_title" '
  [.[] | select(.displayTitle == $title and .status == "completed")][0:3]
  | if length == 3 and all(.[];
      .conclusion == "failure"
      or .conclusion == "timed_out"
      or .conclusion == "startup_failure")
    then 3
    else 0
    end
' <<< "$runs_json")"
if [[ "$recent_failures" == "3" ]]; then
  echo "::error::The three most recent $run_title runs failed; automatic retries are paused"
  noop "release_repeatedly_failing"
fi

emit "should_dispatch=true"
emit "reason=release_missing"
echo "✓ JxBrowser $latest is ready and has no complete BOSS Chromium release"
