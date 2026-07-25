#!/usr/bin/env bash
#
# Unit test for lib/notarize-dmg.sh notarize_dmg. Runs the function under
# `bash -e` — the mode the release workflows use — because the `set +e` fence
# around the command substitution only matters in that mode, and a
# happy-path-only test would never exercise it.
#
# Apple is faked by putting a stub `xcrun` on PATH, so the real code path runs:
# argument construction, output parsing, the submit-vs-wait decision, and the
# retry accounting. Each scenario's stub records what it was asked to do in
# $TD/calls, and the assertions read that log — which is how the central
# regression is expressed: after a dropped poll, attempt 2 must be a `wait` on
# the existing submission, never a second `submit`.
#
# Run: bash scripts/test/test-notarize-dmg.sh
set -e

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TD="$(mktemp -d)"; trap 'rm -rf "$TD"' EXIT
mkdir -p "$TD/bin"
PATH="$TD/bin:$PATH"

# Credentials the lib expects; the stub asserts they arrive intact.
export APPLE_ID="ci@example.com" APP_PASSWORD="app-specific-pw" TEAM_ID="TEAMID123"
# No real waiting in tests.
export NOTARIZE_RETRY_DELAY=0 NOTARIZE_STAPLE_DELAY=0

DMG="$TD/BOSS-9.2.59.dmg"; echo "not really a dmg" > "$DMG"

pass=0; fail=0
ok()  { echo "  ok: $1"; pass=$((pass + 1)); }
bad() { echo "  FAIL: $1" >&2; fail=$((fail + 1)); }

SUBMISSION_ID="55346c79-b132-4357-bb42-ad5f3a791418"

# The exact shape of the -1001 timeout from run 30067806575: the upload
# succeeded and Apple reported an id, then the status poll died. `statusCode:
# nil` is what distinguishes it from a verdict.
timeout_output() {
  cat <<EOF
Conducting pre-submission checks for BOSS-9.2.59.dmg and initiating connection to the Apple notary service...
Submission ID received
  id: $SUBMISSION_ID
Successfully uploaded file
  id: $SUBMISSION_ID
Waiting for processing to complete.
Current status: In Progress..........Error: HTTPError(statusCode: nil, error: Error Domain=NSURLErrorDomain Code=-1001 "The request timed out." UserInfo={_kCFStreamErrorCodeKey=60})
EOF
}

accepted_output() {
  cat <<EOF
Successfully uploaded file
  id: $SUBMISSION_ID
Waiting for processing to complete.
Current status: Accepted
Processing complete
  id: $SUBMISSION_ID
  status: Accepted
EOF
}

invalid_output() {
  cat <<EOF
Successfully uploaded file
  id: $SUBMISSION_ID
Waiting for processing to complete.
Processing complete
  id: $SUBMISSION_ID
  status: Invalid
EOF
}

# write_stub <<'BODY' — installs $TD/bin/xcrun. The body sees $1.. as the
# xcrun arguments, $CALLS as the log path, and $N as this call's 1-based index.
write_stub() {
  cat > "$TD/bin/xcrun" <<'PREAMBLE'
#!/usr/bin/env bash
CALLS="$TD/calls"
N=$(( $(wc -l < "$CALLS" 2>/dev/null || echo 0) + 1 ))
# Record the subcommand only; credentials are asserted separately.
echo "$1 $2" >> "$CALLS"
# Every notarytool call must carry the credentials, or a "transient" failure
# would really be an auth failure retried three times.
case "$1" in
  notarytool)
    for want in "--apple-id" "$APPLE_ID" "--password" "$APP_PASSWORD" "--team-id" "$TEAM_ID"; do
      # `--` matters: the wanted values are themselves flags like --apple-id,
      # which BSD grep would otherwise parse as its own options.
      printf '%s\n' "$@" | grep -qxF -- "$want" || { echo "STUB: missing arg $want" >&2; exit 99; }
    done
    ;;
esac
PREAMBLE
  cat >> "$TD/bin/xcrun"
  chmod +x "$TD/bin/xcrun"
}

# scenario <name> — resets the call log for a fresh stub
scenario() { : > "$TD/calls"; echo "$1"; }
calls()    { tr '\n' ',' < "$TD/calls" | sed 's/,$//'; }
run()      { set +e; ( set -e; source "$HERE/../lib/notarize-dmg.sh"; notarize_dmg "$DMG" ) > "$TD/out" 2>&1; rc=$?; set -e; }

export TD SUBMISSION_ID

# ---------------------------------------------------------------- accepted
scenario "accepted on first attempt"
write_stub <<STUB
case "\$1 \$2" in
  "notarytool submit") cat <<'OUT'
$(accepted_output)
OUT
  ;;
  "stapler staple") echo "The staple and validate action worked!" ;;
esac
STUB
run
if [[ $rc == 0 ]] && [[ "$(calls)" == "notarytool submit,stapler staple" ]]; then
  ok "accepted first try → one submit, then staple (rc=0)"
else
  bad "accepted first try (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi

# ------------------------------------------------- dropped poll → resume wait
# THE regression: a -1001 during polling must resume the submission, not
# re-upload it. Attempt 2 is `notarytool wait`, and there is exactly one submit.
scenario "poll times out, then wait succeeds"
write_stub <<STUB
case "\$1 \$2" in
  "notarytool submit") cat <<'OUT'
$(timeout_output)
OUT
    exit 1 ;;
  "notarytool wait") cat <<'OUT'
$(accepted_output)
OUT
    ;;
  "stapler staple") echo "The staple and validate action worked!" ;;
esac
STUB
run
if [[ $rc == 0 ]] && [[ "$(calls)" == "notarytool submit,notarytool wait,stapler staple" ]]; then
  ok "dropped poll → resumes existing submission, no re-upload (rc=0)"
else
  bad "dropped poll (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi
if [[ $(grep -c "notarytool submit" "$TD/calls") == 1 ]]; then
  ok "exactly one submit across the retry"
else
  bad "expected exactly one submit, got $(grep -c 'notarytool submit' "$TD/calls")"
fi

# ------------------------------------------------------------ Apple verdict
# A rejection is settled: fail fast with the log, no retry attempts.
scenario "Apple returns Invalid"
write_stub <<STUB
case "\$1 \$2" in
  "notarytool submit") cat <<'OUT'
$(invalid_output)
OUT
    exit 1 ;;
  "notarytool log") echo '{"issues":[{"message":"The binary is not signed."}]}' ;;
esac
STUB
run
if [[ $rc != 0 ]] && [[ "$(calls)" == "notarytool submit,notarytool log" ]]; then
  ok "rejection fails fast and fetches the log (no wait retries)"
else
  bad "rejection (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi

# -------------------------------------------- upload dies before an id exists
# No submission to resume, so retrying must re-submit.
scenario "upload fails before Apple reports an id"
write_stub <<STUB
if [[ "\$1 \$2" == "notarytool submit" ]]; then
  if [[ \$N == 1 ]]; then
    echo 'Error: HTTPError(statusCode: nil, error: Error Domain=NSURLErrorDomain Code=-1001 "The request timed out.")'
    exit 1
  fi
  cat <<'OUT'
$(accepted_output)
OUT
  exit 0
fi
[[ "\$1 \$2" == "stapler staple" ]] && echo "The staple and validate action worked!"
STUB
run
if [[ $rc == 0 ]] && [[ "$(calls)" == "notarytool submit,notarytool submit,stapler staple" ]]; then
  ok "no id yet → re-submits, then succeeds (rc=0)"
else
  bad "upload failure (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi

# ------------------------------------------------------------ flaky stapler
# Stapling also talks to Apple, so it gets the same treatment.
scenario "stapler flakes once"
write_stub <<STUB
case "\$1 \$2" in
  "notarytool submit") cat <<'OUT'
$(accepted_output)
OUT
  ;;
  "stapler staple")
    if [[ \$N == 2 ]]; then echo "Error 68: could not validate ticket" >&2; exit 68; fi
    echo "The staple and validate action worked!" ;;
esac
STUB
run
if [[ $rc == 0 ]] && [[ "$(calls)" == "notarytool submit,stapler staple,stapler staple" ]]; then
  ok "stapler retries after a transient failure (rc=0)"
else
  bad "flaky stapler (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi

# ------------------------------------------------------- exhausted attempts
# Persistent outage: bounded attempts, then fail — with the log fetched.
scenario "every attempt times out"
write_stub <<STUB
case "\$1 \$2" in
  "notarytool submit") cat <<'OUT'
$(timeout_output)
OUT
    exit 1 ;;
  "notarytool wait") echo 'Error: HTTPError(statusCode: nil, error: Code=-1001 "The request timed out.")'; exit 1 ;;
  "notarytool log") echo "no log available" ;;
esac
STUB
run
if [[ $rc != 0 ]] && [[ "$(calls)" == "notarytool submit,notarytool wait,notarytool wait,notarytool log" ]]; then
  ok "exhausts 3 bounded attempts then fails (rc=$rc)"
else
  bad "exhausted attempts (rc=$rc calls=$(calls))"; cat "$TD/out" >&2
fi

# --------------------------------------------------------------- missing DMG
scenario "missing DMG"
write_stub <<'STUB'
echo "should not be called" >&2; exit 1
STUB
set +e; ( set -e; source "$HERE/../lib/notarize-dmg.sh"; notarize_dmg "$TD/nope.dmg" ) > "$TD/out" 2>&1; rc=$?; set -e
if [[ $rc != 0 ]] && [[ -z "$(calls)" ]]; then
  ok "missing DMG fails without calling Apple"
else
  bad "missing DMG (rc=$rc calls=$(calls))"
fi

echo "notarize tests: $pass passed, $fail failed"
[[ "$fail" == 0 ]]
