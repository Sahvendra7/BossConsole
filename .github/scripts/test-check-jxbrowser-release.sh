#!/usr/bin/env bash
# Repeatable decision-table tests for check-jxbrowser-release.sh.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
detector="$repo_root/.github/scripts/check-jxbrowser-release.sh"
assets_file="$repo_root/.github/chromium-required-assets.txt"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
mock_bin="$test_dir/bin"
mkdir -p "$mock_bin"

cat > "$mock_bin/curl" <<'MOCK_CURL'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *"maven-metadata.xml"* ]]; then
  printf '<metadata><versioning><release>%s</release></versioning></metadata>\n' \
    "${MOCK_VERSION:-9.4.0}"
elif [[ "${MOCK_ARTIFACT_READY:-true}" != "true" ]]; then
  exit 22
fi
MOCK_CURL

cat > "$mock_bin/gh" <<'MOCK_GH'
#!/usr/bin/env bash
set -euo pipefail
case "$1 $2" in
  "release list")
    [[ "${MOCK_RELEASE_LIST_EXIT:-0}" == "0" ]] || exit "$MOCK_RELEASE_LIST_EXIT"
    printf '%s\n' "${MOCK_RELEASE_LIST:-[]}"
    ;;
  "release view")
    printf '%s\n' "${MOCK_RELEASE_VIEW:-}"
    ;;
  "run list")
    printf '%s\n' "${MOCK_RUN_LIST:-[]}"
    ;;
  *)
    echo "Unexpected mock gh call: $*" >&2
    exit 2
    ;;
esac
MOCK_GH
chmod +x "$mock_bin/curl" "$mock_bin/gh"

complete_release='{
  "isDraft": false,
  "assets": [
    {"name":"boss-chromium-macos-arm64.zip"},
    {"name":"boss-chromium-macos-x64.zip"},
    {"name":"boss-chromium-windows-x64.zip"},
    {"name":"boss-chromium-linux-x64.zip"},
    {"name":"boss-chromium-linux-arm64.zip"}
  ]
}'
partial_release='{
  "isDraft": false,
  "assets": [
    {"name":"boss-chromium-macos-arm64.zip"},
    {"name":"boss-chromium-macos-x64.zip"},
    {"name":"boss-chromium-windows-x64.zip"},
    {"name":"boss-chromium-linux-x64.zip"}
  ]
}'
public_release_list='[{"tagName":"chromium-v9.4.0","isDraft":false}]'

run_case() {
  local name="$1"
  local expected_dispatch="$2"
  local expected_reason="$3"
  local artifact_ready="$4"
  local release_list="$5"
  local release_view="$6"
  local run_list="$7"
  local output="$test_dir/$name.output"

  PATH="$mock_bin:$PATH" \
    TARGET_VERSION=9.4.0 \
    MOCK_ARTIFACT_READY="$artifact_ready" \
    MOCK_RELEASE_LIST="$release_list" \
    MOCK_RELEASE_VIEW="$release_view" \
    MOCK_RUN_LIST="$run_list" \
    REQUIRED_ASSETS_FILE="$assets_file" \
    GITHUB_OUTPUT="$output" \
    bash "$detector" > "$test_dir/$name.log"

  grep -Fqx "latest_version=9.4.0" "$output"
  grep -Fqx "should_dispatch=$expected_dispatch" "$output"
  grep -Fqx "reason=$expected_reason" "$output"
  echo "✓ $name"
}

run_case \
  published_complete false release_already_published true \
  "$public_release_list" "$complete_release" '[]'

run_case \
  artifact_not_ready false artifact_not_ready false \
  '[]' '{}' '[]'

run_case \
  active_run false release_run_active true \
  '[]' '{}' \
  '[{"displayTitle":"BOSS Chromium for JxBrowser 9.4.0","status":"in_progress","conclusion":""}]'

run_case \
  release_missing true release_missing true \
  '[]' '{}' '[]'

run_case \
  public_release_repair true release_missing true \
  "$public_release_list" "$partial_release" '[]'

run_case \
  repeated_failures false release_repeatedly_failing true \
  '[]' '{}' \
  '[
    {"displayTitle":"BOSS Chromium for JxBrowser 9.4.0","status":"completed","conclusion":"failure"},
    {"displayTitle":"BOSS Chromium for JxBrowser 9.4.0","status":"completed","conclusion":"timed_out"},
    {"displayTitle":"BOSS Chromium for JxBrowser 9.4.0","status":"completed","conclusion":"startup_failure"}
  ]'

metadata_output="$test_dir/metadata.output"
PATH="$mock_bin:$PATH" \
  MOCK_VERSION=9.4.0 \
  MOCK_RELEASE_LIST='[]' \
  MOCK_RUN_LIST='[]' \
  REQUIRED_ASSETS_FILE="$assets_file" \
  GITHUB_OUTPUT="$metadata_output" \
  bash "$detector" > "$test_dir/metadata.log"
grep -Fqx 'latest_version=9.4.0' "$metadata_output"
echo "✓ metadata_version"

if PATH="$mock_bin:$PATH" \
  TARGET_VERSION='9.4; echo unsafe' \
  REQUIRED_ASSETS_FILE="$assets_file" \
  GITHUB_OUTPUT="$test_dir/invalid.output" \
  bash "$detector" > "$test_dir/invalid.log" 2>&1; then
  echo "Expected invalid version to fail" >&2
  exit 1
fi
echo "✓ invalid_version"

if PATH="$mock_bin:$PATH" \
  TARGET_VERSION=9.4.0 \
  MOCK_RELEASE_LIST_EXIT=1 \
  REQUIRED_ASSETS_FILE="$assets_file" \
  GITHUB_OUTPUT="$test_dir/unreadable.output" \
  bash "$detector" > "$test_dir/unreadable.log" 2>&1; then
  echo "Expected an unreadable releases repository to fail" >&2
  exit 1
fi
echo "✓ unreadable_releases_repo"

echo "All JxBrowser release detector tests passed."
