#!/usr/bin/env bash
#
# macOS notarization for the release workflows, extracted into a sourceable unit
# so the retry logic can be tested without an Apple account (see
# scripts/test/test-notarize-dmg.sh). This file has NO side effects on source,
# and notarize_dmg leaves the caller's shell options as it found them.
#
# Why this exists: run 30067806575 lost release 9.2.59 to a network blip. The
# DMG built, signed and uploaded fine — Apple had the submission — but the
# status poll died with NSURLErrorDomain -1001 ("The request timed out",
# _kCFStreamErrorCodeKey=60 = ETIMEDOUT) after ~10 successful polls, and the
# single-shot `notarytool submit --wait` treated that as a failed
# notarization. `statusCode: nil` is the tell: no HTTP response at all, i.e.
# a dropped connection rather than a verdict from Apple. The submission itself
# very likely went on to be accepted, unwitnessed.
#
# So the retry has to resume the EXISTING submission rather than re-submit:
# re-uploading queues a duplicate and throws away work Apple has already done.
# `notarytool wait <id>` does exactly that.

# Attempts, backoff and per-attempt timeout are env-overridable so the tests
# run instantly. NOTARIZE_RETRY_DELAY is the FIRST delay and doubles from
# there, so the defaults give the network 30+60+120+240s ≈ 7.5 min to come
# back — cheap next to a lost release and a manual re-dispatch.
NOTARIZE_MAX_ATTEMPTS="${NOTARIZE_MAX_ATTEMPTS:-5}"
NOTARIZE_RETRY_DELAY="${NOTARIZE_RETRY_DELAY:-30}"
# Neither `notarytool submit --wait` nor `notarytool wait` has a default
# timeout: both block indefinitely. A half-open connection can therefore leave
# notarytool hung rather than erroring, and then the retry loop below never
# gets control and the job burns to GitHub's 6h ceiling — the same lost
# release, harder to diagnose. Bounding each attempt is what makes "N bounded
# attempts" actually bounded.
NOTARIZE_ATTEMPT_TIMEOUT="${NOTARIZE_ATTEMPT_TIMEOUT:-30m}"
# Stapling fetches the ticket from Apple; error 68 is usually just ticket
# propagation lag, which wants minutes rather than seconds.
NOTARIZE_STAPLE_ATTEMPTS="${NOTARIZE_STAPLE_ATTEMPTS:-5}"
NOTARIZE_STAPLE_DELAY="${NOTARIZE_STAPLE_DELAY:-30}"

# Anchored UUID match for the submission-ID latch. The latch is STICKY — every
# later attempt does `notarytool wait <id>` — so a loose match that captures
# garbage turns a recoverable blip into a guaranteed all-attempts failure, with
# a useless `notarytool log <garbage>` at the end. Exactly what this file
# exists to prevent, so match Apple's UUID shape or nothing.
_NOTARIZE_ID_RE='^[[:space:]]*id: [0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$'

# Failures that no amount of retrying will fix, and that must NOT be reported
# as network trouble: an expired app-specific password or an empty team id
# otherwise costs three attempts, minutes of sleeps, and a log line pointing
# the on-call reader away from the real cause.
_NOTARIZE_FATAL_RE='HTTP status code: 40[013]|Unauthorized|[Ii]nvalid credentials|unable to (validate|authenticate)'

# notarize_dmg <dmg_path>
#
# Submits <dmg_path> to Apple, waits for a verdict, and staples the ticket.
# Reads APPLE_ID / APP_PASSWORD / TEAM_ID from the environment (the release
# workflows put them there via $GITHUB_ENV).
#
# Returns 0 only when Apple accepted the submission AND the ticket stapled.
# Retries transient failures; a verdict or a credential problem fails fast.
notarize_dmg() {
  local dmg_path="$1"

  # Capture errexit BEFORE the first fence so it can be restored exactly.
  # `case` rather than `[[ ... ]] && x=1`, whose non-zero result would itself
  # trip errexit when the pattern doesn't match.
  local errexit_was_set=0
  case $- in *e*) errexit_was_set=1 ;; esac

  if [[ -z "$dmg_path" || ! -f "$dmg_path" ]]; then
    echo "❌ DMG file not found for notarization: ${dmg_path:-<empty>}"
    return 1
  fi

  # Fail fast and by name on missing credentials rather than letting a 401
  # masquerade as a network failure below. `${VAR:-}` keeps this `set -u`-safe.
  local missing=()
  [[ -n "${APPLE_ID:-}" ]] || missing+=("APPLE_ID")
  [[ -n "${APP_PASSWORD:-}" ]] || missing+=("APP_PASSWORD")
  [[ -n "${TEAM_ID:-}" ]] || missing+=("TEAM_ID")
  if ((${#missing[@]})); then
    echo "❌ Notarization credentials missing from the environment: ${missing[*]}"
    return 1
  fi

  # Two arrays rather than one plus an optional: on the macOS runners' bash 3.2,
  # expanding an EMPTY array as "${arr[@]}" under `set -u` is an unbound-variable
  # error, so both of these are always non-empty. `creds` is what the log fetch
  # uses (--timeout is only meaningful while waiting).
  local creds=(--apple-id "$APPLE_ID" --password "$APP_PASSWORD" --team-id "$TEAM_ID")
  local wait_args=("${creds[@]}")

  # Probe rather than assume: passing an unsupported flag would fail every
  # attempt instantly and turn a runner-image change into a lost release.
  if xcrun notarytool submit --help 2>&1 | grep -q -- "--timeout"; then
    wait_args+=(--timeout "$NOTARIZE_ATTEMPT_TIMEOUT")
  else
    echo "⚠️ notarytool has no --timeout on this runner; attempts are unbounded"
  fi

  local submission_id="" verdict="" output exit_code attempt delay

  echo "⏱ Up to ${NOTARIZE_MAX_ATTEMPTS} attempts, each bounded at ${NOTARIZE_ATTEMPT_TIMEOUT}"

  for ((attempt = 1; attempt <= NOTARIZE_MAX_ATTEMPTS; attempt++)); do
    # `set +e` around the command substitution is load-bearing: callers run
    # under `bash -e`, where a non-zero notarytool exit propagates at the
    # assignment itself, killing the step BEFORE we can echo the output or
    # fetch the detailed log. The exit code is captured separately so the
    # checks below can still tell success from failure. Restored to whatever
    # the caller had, so sourcing this never silently arms errexit for them.
    set +e
    if [[ -z "$submission_id" ]]; then
      echo "🔐 Submitting DMG for notarization (attempt ${attempt}/${NOTARIZE_MAX_ATTEMPTS})..."
      output=$(xcrun notarytool submit "$dmg_path" "${wait_args[@]}" --wait 2>&1)
    else
      # Resume the existing submission — never re-upload. See the header.
      echo "🔁 Resuming wait on submission ${submission_id} (attempt ${attempt}/${NOTARIZE_MAX_ATTEMPTS})..."
      output=$(xcrun notarytool wait "$submission_id" "${wait_args[@]}" 2>&1)
    fi
    exit_code=$?
    if ((errexit_was_set)); then set -e; fi

    echo "$output"
    echo "📋 notarytool exit code: ${exit_code}"

    # Latch the submission ID the first time Apple reports one, so every later
    # attempt resumes instead of re-submitting. `|| true` documents that
    # no-match is expected: the pipeline only returns 0 today because awk is
    # last, so a later simplification to a bare grep — or adding
    # `set -o pipefail` — would otherwise kill the step under bash -e.
    if [[ -z "$submission_id" ]]; then
      submission_id=$(echo "$output" | grep -m1 -oE "$_NOTARIZE_ID_RE" | awk '{print $2}' || true)
      if [[ -n "$submission_id" ]]; then echo "📋 Submission ID: ${submission_id}"; fi
    fi

    if echo "$output" | grep -q "status: Accepted"; then
      verdict="Accepted"
      break
    fi

    # Apple rejected the build: retrying re-polls a settled verdict and only
    # delays the failure. Stop and let the log explain why.
    if echo "$output" | grep -qE "status: (Invalid|Rejected)"; then
      verdict="Rejected"
      break
    fi

    # Credentials or entitlements — retrying just multiplies the same 401.
    if echo "$output" | grep -qE "$_NOTARIZE_FATAL_RE"; then
      verdict="Fatal"
      break
    fi

    # Deliberately not asserting a cause here: all we know is that no verdict
    # came back. A dropped connection, a wait timeout and an unrecognised
    # notarytool message all land here.
    echo "⚠️ No verdict parsed from notarytool output (exit ${exit_code}) — treating as transient"
    if ((attempt < NOTARIZE_MAX_ATTEMPTS)); then
      delay=$((NOTARIZE_RETRY_DELAY * (1 << (attempt - 1))))
      echo "   Retrying in ${delay}s"
      sleep "$delay"
    fi
  done

  if [[ "$verdict" != "Accepted" ]]; then
    case "$verdict" in
      Rejected) echo "❌ Notarization rejected by Apple" ;;
      Fatal) echo "❌ Notarization cannot proceed — credentials or account problem, not a retryable failure" ;;
      *) echo "❌ Notarization did not reach a verdict in ${NOTARIZE_MAX_ATTEMPTS} attempts" ;;
    esac

    if [[ -n "$submission_id" ]]; then
      echo "📜 Fetching detailed notarization log..."
      xcrun notarytool log "$submission_id" "${creds[@]}" || true
    fi
    return 1
  fi

  echo "✅ Notarization accepted"

  # Stapling fetches the ticket from Apple, so it fails the same transient way
  # the status poll did — and an unstapled DMG gatekeeps offline installs.
  echo "📎 Stapling notarization to DMG..."
  for ((attempt = 1; attempt <= NOTARIZE_STAPLE_ATTEMPTS; attempt++)); do
    if xcrun stapler staple "$dmg_path"; then
      echo "✅ macOS notarization completed successfully"
      return 0
    fi
    echo "⚠️ Stapling attempt ${attempt}/${NOTARIZE_STAPLE_ATTEMPTS} failed"
    if ((attempt < NOTARIZE_STAPLE_ATTEMPTS)); then
      echo "   Retrying in ${NOTARIZE_STAPLE_DELAY}s"
      sleep "$NOTARIZE_STAPLE_DELAY"
    fi
  done

  echo "❌ Stapling failed after ${NOTARIZE_STAPLE_ATTEMPTS} attempts"
  return 1
}
