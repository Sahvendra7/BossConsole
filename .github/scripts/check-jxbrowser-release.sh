#!/usr/bin/env bash
#
# Detect the newest fully-published JxBrowser release and decide whether the
# BOSS Chromium branding workflow should be dispatched for it.
#
# Inputs (env):
#   TARGET_VERSION   optional version override for manual/test runs
#   SOURCE_REPO      repository containing the branding workflow
#   RELEASES_REPO    repository containing published Chromium releases
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
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

emit() { printf '%s\n' "$1" >> "$OUT"; }
noop() {
  local reason="$1"
  echo "→ $reason"
  emit "should_dispatch=false"
  emit "reason=$reason"
  exit 0
}

for command in curl gh jq; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ERROR: required command is unavailable: $command" >&2
    exit 1
  }
done

latest="${TARGET_VERSION:-}"
if [[ -z "$latest" ]]; then
  metadata="$(curl -fsSL "$META_URL")"
  latest="$(sed -n 's:.*<release>\([^<]*\)</release>.*:\1:p' <<< "$metadata" | head -1)"
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
  if ! curl -fsSIL "$artifact_url" >/dev/null; then
    echo "::warning::JxBrowser $latest is in metadata, but $artifact is not resolvable yet"
    noop "artifact_not_ready"
  fi
done

tag="chromium-v$latest"
required_assets=(
  boss-chromium-macos-arm64.zip
  boss-chromium-macos-x64.zip
  boss-chromium-windows-x64.zip
  boss-chromium-linux-x64.zip
  boss-chromium-linux-arm64.zip
)

release_json=""
release_error="$(mktemp)"
trap 'rm -f "$release_error"' EXIT
if release_json="$(gh release view "$tag" --repo "$RELEASES_REPO" --json isDraft,assets 2>"$release_error")"; then
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
  fi
elif ! grep -qiE 'release not found|HTTP 404|Not Found' "$release_error"; then
  echo "ERROR: could not check $tag in $RELEASES_REPO" >&2
  cat "$release_error" >&2
  exit 1
fi

# The branding workflow exposes the version in run-name, making this check
# deterministic without downloading run logs or inspecting event payloads.
run_title="BOSS Chromium for JxBrowser $latest"
active_status="$(
  gh run list \
    --repo "$SOURCE_REPO" \
    --workflow "$WORKFLOW_FILE" \
    --limit 100 \
    --json displayTitle,status \
    --jq '.[] | select(.status != "completed") | [.displayTitle, .status] | @tsv' |
    awk -F '\t' -v title="$run_title" '$1 == title { print $2; exit }'
)"
if [[ -n "$active_status" ]]; then
  echo "Matching branding run is already $active_status"
  noop "release_run_active"
fi

emit "should_dispatch=true"
emit "reason=release_missing"
echo "✓ JxBrowser $latest is ready and has no complete BOSS Chromium release"
