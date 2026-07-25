#!/usr/bin/env bash
#
# macOS notarization for the release workflows, extracted into a sourceable unit
# so the retry logic can be tested without an Apple account (see
# scripts/test/test-notarize-dmg.sh). This file has NO side effects on source.
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

# Attempts and backoff are env-overridable so the tests run instantly.
NOTARIZE_MAX_ATTEMPTS="${NOTARIZE_MAX_ATTEMPTS:-3}"
NOTARIZE_RETRY_DELAY="${NOTARIZE_RETRY_DELAY:-30}"
NOTARIZE_STAPLE_ATTEMPTS="${NOTARIZE_STAPLE_ATTEMPTS:-3}"
NOTARIZE_STAPLE_DELAY="${NOTARIZE_STAPLE_DELAY:-15}"

# notarize_dmg <dmg_path>
#
# Submits <dmg_path> to Apple, waits for a verdict, and staples the ticket.
# Reads APPLE_ID / APP_PASSWORD / TEAM_ID from the environment (the release
# workflows put them there via $GITHUB_ENV).
#
# Returns 0 only when Apple accepted the submission AND the ticket stapled.
# Retries transient failures; a verdict from Apple is final and fails fast.
notarize_dmg() {
  local dmg_path="$1"

  if [[ -z "$dmg_path" || ! -f "$dmg_path" ]]; then
    echo "❌ DMG file not found for notarization: ${dmg_path:-<empty>}"
    return 1
  fi

  local creds=(--apple-id "$APPLE_ID" --password "$APP_PASSWORD" --team-id "$TEAM_ID")
  local submission_id="" verdict="" output exit_code attempt

  for ((attempt = 1; attempt <= NOTARIZE_MAX_ATTEMPTS; attempt++)); do
    # `set +e` around the command substitution is load-bearing: callers run
    # under `bash -e`, where a non-zero notarytool exit propagates at the
    # assignment itself, killing the step BEFORE we can echo the output or
    # fetch the detailed log. The exit code is captured separately so the
    # checks below can still tell success from failure.
    set +e
    if [[ -z "$submission_id" ]]; then
      echo "🔐 Submitting DMG for notarization (attempt ${attempt}/${NOTARIZE_MAX_ATTEMPTS})..."
      output=$(xcrun notarytool submit "$dmg_path" "${creds[@]}" --wait 2>&1)
    else
      # Resume the existing submission — never re-upload. See the header.
      echo "🔁 Resuming wait on submission ${submission_id} (attempt ${attempt}/${NOTARIZE_MAX_ATTEMPTS})..."
      output=$(xcrun notarytool wait "$submission_id" "${creds[@]}" 2>&1)
    fi
    exit_code=$?
    set -e

    echo "$output"
    echo "📋 notarytool exit code: ${exit_code}"

    # Latch the submission ID the first time Apple reports one, so every later
    # attempt resumes instead of re-submitting.
    if [[ -z "$submission_id" ]]; then
      submission_id=$(echo "$output" | grep -m1 "id:" | awk '{print $2}')
      [[ -n "$submission_id" ]] && echo "📋 Submission ID: ${submission_id}"
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

    echo "⚠️ No verdict from Apple (transient failure — network, not a rejection)"
    if ((attempt < NOTARIZE_MAX_ATTEMPTS)); then
      echo "   Retrying in ${NOTARIZE_RETRY_DELAY}s"
      sleep "$NOTARIZE_RETRY_DELAY"
    fi
  done

  if [[ "$verdict" != "Accepted" ]]; then
    if [[ "$verdict" == "Rejected" ]]; then
      echo "❌ Notarization rejected by Apple"
    else
      echo "❌ Notarization did not reach a verdict in ${NOTARIZE_MAX_ATTEMPTS} attempts"
    fi

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
