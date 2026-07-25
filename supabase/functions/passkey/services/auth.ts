import type { SupabaseClient } from "@supabase/supabase-js"
import { generateChallenge, storeChallenge } from "../utils/challenge.ts"
import {
  verifyChallenge,
  consumeChallengeRow,
  findPasskeyByCredentialId,
  getUserPasskeys,
  findUserByEmail,
  getUserWithEmail,
  recordPasskeyUse
} from "../utils/database.ts"
import { verifySignature } from "../utils/crypto.ts"
import { ChallengeType } from "../types/challenge.ts"
import { withErrorHandler, withStatusErrorHandler } from "../utils/error-handler.ts"
import { generateSupabaseAccessToken } from "../utils/jwt.ts"
import { ALLOWED_ORIGINS, getAllowedRpIds, getRpId } from "../utils/config.ts"
import { normalizeBase64Url } from "../utils/base64.ts"
import {
  challengeMatches,
  COSE_ALG_ES256,
  evaluateSignCounter,
  matchRpIdHash,
  parseAuthenticatorDataBase64,
  parseClientDataJSON
} from "../utils/webauthn.ts"

// Re-exported for the routes and for callers that imported it from here before
// it moved to utils/config.ts (single source of truth).
export { ALLOWED_ORIGINS }

export interface AuthenticationCredential {
  id: string
  rawId: string
  type: string
  response: {
    clientDataJSON: string
    authenticatorData: string
    signature: string
    userHandle?: string
  }
}

/**
 * Generates an authentication challenge for a user
 */
export const generateAuthChallenge = withErrorHandler(
  async (supabase: SupabaseClient, email: string, sessionId?: string) => {
    console.log('🔑 Generating authentication challenge for email:', email)

    // Use utility function for scalable user lookup
    const userResult = await findUserByEmail(supabase, email)

    if (!userResult.success || !userResult.user) {
      console.error('User not found with email:', email)
      return {
        success: false,
        error: 'User not found'
      }
    }

    const userId = userResult.user.id
    console.log('Resolved email to user ID:', userId)

    // Get user's passkeys
    const passkeyResult = await getUserPasskeys(supabase, userId)

    if (!passkeyResult.success) {
      console.error('Error fetching user passkeys:', passkeyResult.error)
      return {
        success: false,
        error: 'Failed to fetch user credentials'
      }
    }

    const userPasskeys = passkeyResult.passkeys || []

    if (userPasskeys.length === 0) {
      return {
        success: false,
        error: 'No passkeys found for user'
      }
    }

    // Generate and store challenge
    const challenge = generateChallenge()
    const storeResult = await storeChallenge(supabase, challenge, ChallengeType.Authentication, {
      userId,
      sessionId
    })

    if (!storeResult.success) {
      return {
        success: false,
        error: storeResult.error || 'Failed to store challenge'
      }
    }

    // Build allowed credentials list
    const allowedCredentials = userPasskeys.map(pk => ({
      id: pk.credential_id,
      type: 'public-key',
      transports: pk.transports || ['internal']
    }))

    const rpId = getRpId()

    return {
      success: true,
      challenge,
      timeout: 60000,
      rpId,
      userVerification: 'preferred',
      allowCredentials: allowedCredentials,
      sessionId
    }
  },
  'Failed to generate authentication challenge',
  '🔑'
)

/**
 * Completes an authentication ceremony by verifying the credential
 */
export const completeAuthentication = withErrorHandler(
  async (
    supabase: SupabaseClient,
    credential: AuthenticationCredential,
    challenge: string
  ) => {
    console.log('🔐 Starting authentication completion')

    const { clientDataJSON, authenticatorData, signature } = credential.response

    // Decode client data. Decoding is base64url-tolerant (the reference web
    // client sends base64url), and the raw bytes are kept so the hash that goes
    // into signature verification is exactly what the authenticator signed.
    const { bytes: clientDataBytes, data: clientData } = parseClientDataJSON(clientDataJSON)

    // Verify ceremony type
    if (clientData.type !== 'webauthn.get') {
      return {
        success: false,
        error: 'Invalid ceremony type - expected webauthn.get'
      }
    }

    // Verify origin here too, not only in the route: the service is the layer
    // that decides whether an assertion is acceptable.
    if (!ALLOWED_ORIGINS.includes(clientData.origin)) {
      console.error('❌ Assertion origin is not allowed')
      return {
        success: false,
        error: 'Invalid origin'
      }
    }

    // Verify the challenge inside the *signed* client data is the challenge this
    // ceremony was issued. Without this the signature proves nothing about
    // freshness: it stays valid for the challenge it was originally produced
    // for, while only the caller-supplied copy is checked against storage.
    if (!challengeMatches(clientData.challenge, challenge)) {
      console.error('❌ Assertion challenge mismatch between clientDataJSON and request body')
      return {
        success: false,
        error: 'Challenge mismatch - clientDataJSON challenge does not match the issued challenge'
      }
    }

    // Verify challenge (but don't consume yet), keyed on the signed value
    const signedChallenge = normalizeBase64Url(clientData.challenge)
    const challengeResult = await verifyChallenge(
      supabase,
      signedChallenge,
      ChallengeType.Authentication
    )

    if (!challengeResult.success) {
      return {
        success: false,
        error: challengeResult.error || 'Invalid challenge'
      }
    }

    // Find passkey by credential ID
    const passkeyResult = await findPasskeyByCredentialId(
      supabase,
      credential.id
    )

    if (!passkeyResult.success || !passkeyResult.passkey) {
      return {
        success: false,
        error: 'Passkey not found'
      }
    }

    const passkey = passkeyResult.passkey

    // The credential must belong to the user this challenge was issued for.
    // `storeChallenge` always records user_id on the authentication path, so the
    // null guard is defensive rather than an opt-out: a row without a user_id
    // cannot be produced by any current code path.
    const challengeUserId = challengeResult.challengeData?.user_id
    if (challengeUserId && passkey.user_id && challengeUserId !== passkey.user_id) {
      console.error('❌ Credential does not belong to the user this challenge was issued for')
      return {
        success: false,
        error: 'Credential does not belong to this challenge'
      }
    }

    // Parse authenticator data for the relying party hash and signature counter
    const authData = parseAuthenticatorDataBase64(authenticatorData)

    // User Presence is mandatory (WebAuthn L2 §7.2 step 15). A browser will not
    // produce an assertion without it; a non-browser client can.
    if (!authData.userPresent) {
      console.error('❌ Assertion has no User Present flag')
      return {
        success: false,
        error: 'User presence flag not set'
      }
    }

    // Verify the authenticator signed for our relying party. Credentials
    // registered after this change are pinned to the exact rpId recorded at
    // registration; legacy rows fall back to the configured allow-list.
    const candidateRpIds = passkey.rp_id ? [passkey.rp_id] : getAllowedRpIds()
    const matchedRpId = await matchRpIdHash(authData.rpIdHash, candidateRpIds)
    if (!matchedRpId) {
      console.error('❌ Assertion rpIdHash does not match the expected relying party')
      return {
        success: false,
        error: 'Relying party mismatch - assertion was produced for a different rpId'
      }
    }

    // Verify signature using the algorithm this credential was registered with
    const signatureValid = await verifySignature(
      passkey.public_key,
      signature,
      authenticatorData,
      clientDataBytes,
      passkey.public_key_alg ?? COSE_ALG_ES256
    )

    if (!signatureValid) {
      return {
        success: false,
        error: 'Invalid signature'
      }
    }

    // Signature counter: reject a counter that failed to advance (cloned
    // authenticator), while tolerating authenticators that never keep one.
    // Checked after the signature, so the counter is only trusted once the
    // authenticator data is known to be authentic (WebAuthn L2 §7.2 step 21).
    const counter = evaluateSignCounter(passkey.sign_count, authData.signCount)
    if (!counter.ok) {
      console.error('❌ Signature counter regression for passkey:', passkey.id, counter.reason)
      return {
        success: false,
        error: 'Signature counter did not increase - possible cloned authenticator'
      }
    }

    const challengeData = challengeResult.challengeData

    // Consume the challenge now, before any session is granted, and only
    // continue if *this* request is the one that consumed it.
    //
    // This used to happen only when the challenge carried a session_id (the QR
    // flow), so on the direct path the row survived a completed ceremony for the
    // rest of its 5-minute TTL — and the same captured assertion could be
    // replayed against it, indistinguishably, for an authenticator whose counter
    // is always 0. Consumption is unconditional and atomic; it runs after the
    // signature check so that an unauthenticated request cannot burn a pending
    // ceremony.
    const consumeResult = await consumeChallengeRow(supabase, challengeData.id)
    if (!consumeResult.consumed) {
      console.error('❌ Refusing to complete: challenge was not consumed by this request')
      return {
        success: false,
        error: 'Challenge already used'
      }
    }

    // Record the successful assertion (last used + signature counter). With a
    // real counter this is a compare-and-set: losing it means a concurrent
    // assertion already claimed this counter value.
    const useResult = await recordPasskeyUse(supabase, passkey.id, counter.nextValue)
    if (!useResult.advanced) {
      console.error('❌ Signature counter was claimed concurrently for passkey:', passkey.id)
      return {
        success: false,
        error: 'Signature counter did not increase - possible cloned authenticator'
      }
    }

    // Store completed authentication if there's a session_id
    console.log('🔍 Challenge data:', {
      has_session_id: !!challengeData.session_id,
      session_id: challengeData.session_id,
      user_id: passkey.user_id
    })

    if (challengeData.session_id) {
      console.log('💾 Storing completed authentication for session:', challengeData.session_id)
      const { error: insertError } = await supabase
        .from('completed_authentications')
        .insert({
          challenge: signedChallenge,  // Required NOT NULL field
          session_id: challengeData.session_id,
          user_id: passkey.user_id,
          created_at: new Date().toISOString()
        })
        .select()

      if (insertError) {
        // The challenge is already consumed at this point, so the client has to
        // start a new ceremony rather than retry this one. That is the safe
        // direction: never leave a used challenge live to keep a retry cheap.
        console.error('❌ Failed to store completed authentication:', insertError)
        return {
          success: false,
          error: `Failed to store authentication result: ${insertError.message || insertError.code || 'Unknown error'}`
        }
      }

      console.log('✅ Stored completed authentication')
    } else {
      console.warn('⚠️ No session_id in challenge data - completed authentication not stored')
    }

    console.log('✅ Authentication successful for user:', passkey.user_id)

    // Fetch user email using helper function (DRY)
    const userResult = await getUserWithEmail(supabase, passkey.user_id)

    if (!userResult.success || !userResult.user) {
      console.error('❌ Failed to fetch user email for session:', userResult.error)
      return {
        success: true,
        userId: passkey.user_id,
        passkeyId: passkey.id
      }
    }

    const userEmail = userResult.user.email

    // Generate Supabase session for authenticated user
    console.log('🎫 Generating Supabase session for user:', userEmail)

    const tokens = await generateSupabaseAccessToken(supabase, userEmail)

    console.log('✅ Generated Supabase session successfully')

    // Park the session on the completed_authentications row so a cross-device
    // poller replays these tokens instead of minting a new session per poll.
    if (challengeData.session_id) {
      const { error: tokenStoreError } = await supabase
        .from('completed_authentications')
        .update({
          email: userEmail,
          access_token: tokens.accessToken,
          refresh_token: tokens.refreshToken,
          // Column is documented as epoch milliseconds; the API speaks seconds
          expires_at: toEpochMillis(tokens.expiresAt)
        })
        .eq('session_id', challengeData.session_id)

      if (tokenStoreError) {
        // Non-fatal: /auth/status will mint a session instead of replaying one
        console.error('⚠️ Failed to persist session on completed authentication:', tokenStoreError)
      }
    }

    return {
      success: true,
      userId: passkey.user_id,
      email: userEmail,
      passkeyId: passkey.id,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresAt: tokens.expiresAt
    }
  },
  'Failed to complete authentication',
  '🔐'
)

/**
 * `completed_authentications.expires_at` is epoch **milliseconds** (see the
 * column comment in 20251023000009_passkey_tables.sql), while the API and the
 * Supabase session speak seconds. Converting at the boundary keeps the column
 * honest and the responses unchanged.
 */
function toEpochMillis(expiresAtSeconds: number): number | null {
  return Number.isFinite(expiresAtSeconds) ? Math.floor(expiresAtSeconds * 1000) : null
}

/**
 * Reads a stored epoch-millisecond expiry, returning null when it is absent or
 * unparseable — callers must treat that as "expired" rather than "valid".
 */
function parseStoredExpiryMillis(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const millis = Number(value)
  return Number.isFinite(millis) ? millis : null
}

/**
 * Checks the status of an authentication session
 */
export const checkAuthStatus = withStatusErrorHandler(
  async (supabase: SupabaseClient, sessionId: string) => {
    console.log('🔍 Checking auth status for session:', sessionId)

    const { data, error } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('session_id', sessionId)
      .eq('type', ChallengeType.Authentication)
      .single()

    if (error || !data) {
      console.log('🔍 Challenge not found or consumed, checking completed_authentications')
      console.log('🔍 Challenge query error:', error)

      // Session might be consumed (authentication complete)
      // Check if there's a completed authentication record
      const { data: completedAuth, error: completedError } = await supabase
        .from('completed_authentications')
        .select('*')
        .eq('session_id', sessionId)
        .single()

      console.log('🔍 Completed auth query result:', {
        found: !!completedAuth,
        error: completedError?.message || completedError?.code,
        data: completedAuth
      })

      if (completedError || !completedAuth) {
        console.log('❌ No completed authentication found for session:', sessionId)
        return {
          status: 'expired' as const,
          message: 'Session not found or expired'
        }
      }

      console.log('✅ Found completed authentication:', completedAuth.user_id)

      // Replay the session recorded when the ceremony completed. Minting a new
      // session on every poll churns auth.sessions rows (and invalidates the
      // refresh token a client may already be using) for clients that keep
      // polling after the first success.
      //
      // Fails closed: a missing or unparseable expiry means the token's remaining
      // life is unknown, so mint a fresh session instead of replaying a possibly
      // dead one. Minting is the fallback path anyway, so this costs nothing.
      const storedExpiryMillis = parseStoredExpiryMillis(completedAuth.expires_at)
      if (
        completedAuth.access_token &&
        completedAuth.refresh_token &&
        storedExpiryMillis !== null &&
        storedExpiryMillis > Date.now() + 30_000
      ) {
        console.log('♻️ Returning the session minted when the ceremony completed')
        return {
          status: 'completed' as const,
          userId: completedAuth.user_id,
          email: completedAuth.email || undefined,
          completedAt: completedAuth.created_at,
          accessToken: completedAuth.access_token,
          refreshToken: completedAuth.refresh_token,
          expiresAt: Math.floor(storedExpiryMillis / 1000)
        }
      }

      // Fetch user email using helper function (DRY)
      const userResult = await getUserWithEmail(supabase, completedAuth.user_id)

      if (!userResult.success || !userResult.user) {
        console.error('❌ Failed to fetch user email:', userResult.error)
        return {
          status: 'completed' as const,
          userId: completedAuth.user_id,
          completedAt: completedAuth.created_at
          // Email will be null, but at least we return the status
        }
      }

      // Generate Supabase session for passkey authentication
      console.log('🎫 Generating Supabase session for passkey auth:', userResult.user.email)

      const tokens = await generateSupabaseAccessToken(supabase, userResult.user.email)

      console.log('✅ Generated Supabase session successfully')

      // Persist it so subsequent polls replay this session instead of minting more
      const { error: tokenStoreError } = await supabase
        .from('completed_authentications')
        .update({
          email: userResult.user.email,
          access_token: tokens.accessToken,
          refresh_token: tokens.refreshToken,
          // Column is documented as epoch milliseconds; the API speaks seconds
          expires_at: toEpochMillis(tokens.expiresAt)
        })
        .eq('session_id', sessionId)

      if (tokenStoreError) {
        console.error('⚠️ Failed to persist session for session id:', sessionId, tokenStoreError)
      }

      return {
        status: 'completed' as const,
        userId: completedAuth.user_id,
        email: userResult.user.email,
        completedAt: completedAuth.created_at,
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresAt: tokens.expiresAt
      }
    }

    // Check if expired
    const expiresAt = new Date(data.expires_at)
    if (expiresAt < new Date()) {
      return {
        status: 'expired' as const,
        message: 'Session expired'
      }
    }

    return {
      status: 'pending' as const,
      expiresAt: Math.floor(expiresAt.getTime() / 1000) // Convert to Unix timestamp
    }
  },
  'Failed to check authentication status',
  '🔍'
)
