/**
 * WebAuthn Verification Tests
 *
 * These cover the server-side checks a browser does not perform for us:
 * - the signed clientDataJSON carries the challenge this ceremony issued
 * - authenticatorData.rpIdHash is a hash of one of our RP IDs
 * - the signature counter does not go backwards
 * - payloads decode in either base64 alphabet
 * - both advertised algorithms (ES256, RS256) actually verify
 *
 * Every credential here is built with real Web Crypto keys and real signatures,
 * so the assertions exercise the production verification path.
 */

import { assertEquals, assertExists, assertNotEquals } from "jsr:@std/assert"
import type { SupabaseClient } from "@supabase/supabase-js"
import { createMockSupabaseClient, type MockSupabaseClient } from "./helpers/mocks.ts"
import { completeAuthentication, checkAuthStatus } from "../services/auth.ts"
import { completeRegistration, type RegistrationCredential } from "../services/registration.ts"
import { generateChallenge } from "../utils/challenge.ts"
import { COSE_ALG_ES256, COSE_ALG_RS256, evaluateSignCounter, parseAuthenticatorData } from "../utils/webauthn.ts"
import { getAllowedRpIds, rpIdMatchesOrigin } from "../utils/config.ts"
import { decodeBase64Any, encodedValuesMatch, normalizeBase64Url } from "../utils/base64.ts"
import {
  buildAlphabetSensitiveClientDataJSON,
  buildAuthenticatorData,
  createAssertion,
  createRegistrationCredential,
  encodePayload,
  rpIdHashFor,
  storedPublicKey,
  TEST_ORIGIN,
  TEST_RP_ID
} from "./helpers/webauthn.ts"

const TEST_JWT_SECRET = "test-secret-key-for-verification-tests-minimum-32-characters-required"
const TEST_USER_ID = "user-verification-1"
const TEST_EMAIL = "verify@example.com"

Deno.env.set('JWT_SECRET', TEST_JWT_SECRET)
// Pin the relying party so tests do not depend on SUPABASE_URL
Deno.env.set('PASSKEY_RP_ID', TEST_RP_ID)

interface AuthFlowMockOptions {
  challenge: string
  storedPublicKey: string
  credentialId: string
  alg?: number
  signCount?: number | null
  rpId?: string | null
  userId?: string
  challengeUserId?: string
  sessionId?: string | null
  /** false = the challenge row was already consumed by another request */
  consumed?: boolean
  /** false = the compare-and-set counter write matched no row */
  counterAdvanced?: boolean
}

/**
 * Queues the database responses a full assertion ceremony walks through.
 *
 * The challenge row and the passkey row are registered with `match`, so they are
 * only served to a query that filtered on that exact challenge / credential id.
 * A lookup keyed on any other value gets "no rows found", the way the database
 * would answer — that is what makes these tests able to fail if the lookup key
 * regresses to the caller-supplied copy.
 */
function mockAuthFlow(mockClient: MockSupabaseClient, options: AuthFlowMockOptions) {
  const userId = options.userId ?? TEST_USER_ID
  const sessionId = options.sessionId === undefined ? 'session-verification' : options.sessionId

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-row-1',
      challenge: options.challenge,
      type: 'authentication',
      user_id: options.challengeUserId ?? userId,
      session_id: sessionId,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select', { match: { challenge: options.challenge, type: 'authentication' } })

  mockClient.mockResponse('user_passkeys', {
    data: {
      id: 'passkey-row-1',
      user_id: userId,
      credential_id: options.credentialId,
      public_key: options.storedPublicKey,
      public_key_alg: options.alg ?? COSE_ALG_ES256,
      sign_count: options.signCount === undefined ? 0 : options.signCount,
      rp_id: options.rpId === undefined ? TEST_RP_ID : options.rpId,
      display_name: 'Verification Passkey',
      transports: ['internal'],
      active: true
    },
    error: null
  }, 'select', { match: { credential_id: options.credentialId, active: true } })

  mockClient.mockResponse('user_passkeys', {
    data: options.counterAdvanced === false ? [] : [{ id: 'passkey-row-1' }],
    error: null
  }, 'update')
  mockClient.mockResponse('completed_authentications', { data: [{ id: 'completed-1' }], error: null }, 'insert')
  mockClient.mockResponse('passkey_challenges', {
    data: options.consumed === false ? [] : [{ id: 'challenge-row-1' }],
    error: null
  }, 'delete')
  mockClient.mockResponse('users', { data: { id: userId, email: TEST_EMAIL }, error: null }, 'select')
  mockClient.mockAuthUser(TEST_EMAIL, userId)
}

/** The eq filters a query carried, for asserting on lookup keys */
function eqFiltersFor(
  mockClient: MockSupabaseClient,
  table: string,
  operation: string
): Array<{ column: string; value: unknown }> {
  const entry = mockClient.getQueryHistory().find(
    query => query.table === table && query.operation === operation
  )
  return (entry?.params.eq ?? []) as Array<{ column: string; value: unknown }>
}

// ============================================================================
// Item 1 — the assertion challenge must be the one this ceremony issued
// ============================================================================

Deno.test("completeAuthentication - rejects a captured assertion replayed against a live challenge", async () => {
  const mockClient = createMockSupabaseClient()

  const capturedChallenge = generateChallenge()
  const liveChallenge = generateChallenge()
  assertNotEquals(capturedChallenge, liveChallenge)

  const registration = await createRegistrationCredential({ challenge: capturedChallenge })

  // An assertion the attacker captured earlier: valid signature, old challenge
  const capturedAssertion = await createAssertion({
    challenge: capturedChallenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 5
  })

  // Replayed alongside whatever challenge is currently live in the database
  mockAuthFlow(mockClient, {
    challenge: liveChallenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    capturedAssertion,
    liveChallenge
  )

  assertEquals(result.success, false, "Replayed assertion must be rejected")
  assertEquals(
    (result as { error?: string }).error,
    'Challenge mismatch - clientDataJSON challenge does not match the issued challenge'
  )
})

Deno.test("completeAuthentication - rejects a body challenge that does not match the signed one", async () => {
  const mockClient = createMockSupabaseClient()

  const issuedChallenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge: issuedChallenge })

  const assertion = await createAssertion({
    challenge: issuedChallenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    clientDataChallenge: generateChallenge() // signed over something else
  })

  mockAuthFlow(mockClient, {
    challenge: issuedChallenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    issuedChallenge
  )

  assertEquals(result.success, false)
  assertExists((result as { error?: string }).error)
})

Deno.test("completeAuthentication - looks the challenge up by the signed value, not the body copy", async () => {
  const mockClient = createMockSupabaseClient()

  // Same challenge, different encoding in the body: standard base64 with padding
  // where the authenticator signed (and storage holds) canonical base64url.
  const signedChallenge = generateChallenge()
  const bodyChallenge = signedChallenge.replace(/-/g, '+').replace(/_/g, '/') + '='

  const registration = await createRegistrationCredential({ challenge: signedChallenge })
  const assertion = await createAssertion({
    challenge: signedChallenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 3
  })

  // Only a lookup keyed on the canonical signed value finds this row
  mockAuthFlow(mockClient, {
    challenge: signedChallenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 2
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    bodyChallenge
  )

  assertEquals(result.success, true, "A re-encoded body copy must still resolve to the stored challenge")

  const filters = eqFiltersFor(mockClient, 'passkey_challenges', 'select')
  assertEquals(
    filters.some(filter => filter.column === 'challenge' && filter.value === signedChallenge),
    true,
    "The storage lookup must be keyed on the signed challenge"
  )
  assertEquals(
    filters.some(filter => filter.column === 'challenge' && filter.value === bodyChallenge),
    false,
    "The caller-supplied encoding must not be used as the lookup key"
  )
})

Deno.test("completeAuthentication - rejects when the signed challenge matches nothing in storage", async () => {
  const mockClient = createMockSupabaseClient()

  // Body and signed copies agree, but no row exists for that challenge
  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey
  })

  mockAuthFlow(mockClient, {
    challenge: generateChallenge(), // a *different* live challenge is stored
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Invalid or expired challenge')
})

Deno.test("completeAuthentication - consumes the challenge even without a sessionId", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 0 // an authenticator that never keeps a counter
  })

  // The direct (non-QR) path: sessionId is optional on /auth/challenge
  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 0,
    sessionId: null
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true)

  const deletes = mockClient.getQueryHistory().filter(
    query => query.table === 'passkey_challenges' && query.operation === 'delete'
  )
  assertEquals(deletes.length, 1, "The challenge must be consumed on the direct path too")
  assertEquals(
    (deletes[0].params.eq as Array<{ column: string; value: unknown }>)
      .some(filter => filter.column === 'id' && filter.value === 'challenge-row-1'),
    true
  )
})

Deno.test("completeAuthentication - rejects a replay once the challenge is consumed", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 0
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 0,
    sessionId: null
  })

  const first = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )
  assertEquals(first.success, true)

  // Second submission of the identical assertion: the row is gone, and even if a
  // stale read returned it the delete would report no row consumed.
  // ...the challenge row has already been deleted by the first ceremony, so the
  // conditional delete reports that this request consumed nothing
  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 0,
    sessionId: null,
    consumed: false
  })

  const replay = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(replay.success, false, "An always-0 counter must not make a replay succeed")
  assertEquals((replay as { error?: string }).error, 'Challenge already used')
})

Deno.test("completeAuthentication - rejects when the counter was claimed concurrently", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 9
  })

  // Compare-and-set loses: another request already advanced the counter past 9
  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 4,
    counterAdvanced: false
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals(
    (result as { error?: string }).error,
    'Signature counter did not increase - possible cloned authenticator'
  )

  const update = mockClient.getQueryHistory().find(
    query => query.table === 'user_passkeys' && query.operation === 'update'
  )
  assertExists(update)
  assertEquals(
    typeof update!.params.or === 'string' && update!.params.or.includes('sign_count.lt.9'),
    true,
    "The counter write must be a compare-and-set"
  )
})

Deno.test("completeRegistration - rejects an attestation whose signed challenge differs", async () => {
  const mockClient = createMockSupabaseClient()

  const issuedChallenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge: generateChallenge() })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-1',
      challenge: issuedChallenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    issuedChallenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Mismatched Passkey' }
  )

  assertEquals(result.success, false)
  assertEquals(
    (result as { error?: string }).error,
    'Challenge mismatch - clientDataJSON challenge does not match the issued challenge'
  )
})

Deno.test("completeRegistration - rejects a challenge issued for a different user", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-2',
      challenge,
      type: 'registration',
      user_id: 'someone-else',
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-2' }, error: null }, 'delete')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Wrong User Passkey' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Challenge does not belong to this user')
})

Deno.test("completeAuthentication - rejects a credential that belongs to another user", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    userId: 'passkey-owner',
    challengeUserId: 'challenge-owner'
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Credential does not belong to this challenge')
})

// ============================================================================
// Item 2 — rpIdHash must be a hash of one of our relying party IDs
// ============================================================================

Deno.test("completeAuthentication - rejects an assertion produced for another relying party", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    rpId: 'evil.example.com' // signed for a different RP
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false, "Assertion for a foreign RP must be rejected")
  assertEquals(
    (result as { error?: string }).error,
    'Relying party mismatch - assertion was produced for a different rpId'
  )
})

Deno.test("completeAuthentication - rejects an assertion for an rpId other than the one registered", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  // 'localhost' is an allowed RP ID, but this credential was registered for
  // api.risaboss.com, so the stored rp_id pins it.
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    rpId: 'localhost'
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    rpId: TEST_RP_ID
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals(
    (result as { error?: string }).error,
    'Relying party mismatch - assertion was produced for a different rpId'
  )
})

Deno.test("completeRegistration - rejects an attestation created for another relying party", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({
    challenge,
    rpId: 'evil.example.com'
  })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-3',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-3' }, error: null }, 'delete')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Foreign RP Passkey' }
  )

  assertEquals(result.success, false)
  assertEquals(
    (result as { error?: string }).error,
    'Relying party mismatch - credential was created for a different rpId'
  )
})

Deno.test("completeAuthentication - accepts a legacy credential with no recorded rp_id", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    rpId: null // registered before rp_id was recorded
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true, "Legacy rows must keep working via the allow-list")
})

// ============================================================================
// Item 3 — signature counter
// ============================================================================

Deno.test("completeAuthentication - rejects a signature counter that goes backwards", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 41
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 42
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false, "A counter regression must be rejected")
  assertEquals(
    (result as { error?: string }).error,
    'Signature counter did not increase - possible cloned authenticator'
  )
})

Deno.test("completeAuthentication - rejects a replayed counter value", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 7
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 7
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
})

Deno.test("completeAuthentication - persists an advancing signature counter", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 99
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 12
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true)

  const update = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'update'
  )
  assertExists(update, "The passkey row should be updated after a successful assertion")
  assertEquals((update!.params.data as { sign_count?: number }).sign_count, 99)
})

Deno.test("completeAuthentication - accepts authenticators that always report counter 0", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 0
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 0
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true, "Counter-less authenticators must keep working")

  const update = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'update'
  )
  assertExists(update)
  assertEquals(
    (update!.params.data as { sign_count?: number }).sign_count,
    undefined,
    "No counter should be written for an authenticator that does not keep one"
  )
})

Deno.test("evaluateSignCounter - implements the WebAuthn counter rule", () => {
  assertEquals(evaluateSignCounter(0, 0), { ok: true, counterUnsupported: true, nextValue: null })
  assertEquals(evaluateSignCounter(null, 0), { ok: true, counterUnsupported: true, nextValue: null })
  assertEquals(evaluateSignCounter(null, 3), { ok: true, counterUnsupported: false, nextValue: 3 })
  assertEquals(evaluateSignCounter(3, 4).ok, true)
  assertEquals(evaluateSignCounter(3, 3).ok, false)
  assertEquals(evaluateSignCounter(3, 2).ok, false)
  assertEquals(evaluateSignCounter(3, 0).ok, false)
})

// ============================================================================
// Item 4 — base64 / base64url decoding
// ============================================================================

Deno.test("completeAuthentication - accepts payloads encoded as standard base64", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  // A native client using a standard base64 encoder: signatures are random
  // bytes, so '+' and '/' show up regularly.
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    encoding: 'base64'
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true, "Standard base64 payloads must verify")

  // The credential id travels in the same alphabet as the rest of the payload,
  // so the lookup has to canonicalise it to reach the stored row.
  assertNotEquals(
    assertion.id,
    registration.credentialIdBase64Url,
    "This test only covers the id path if the encodings actually differ"
  )
  const filters = eqFiltersFor(mockClient, 'user_passkeys', 'select')
  assertEquals(
    filters.some(f => f.column === 'credential_id' && f.value === registration.credentialIdBase64Url),
    true,
    "The credential lookup must be keyed on the canonical id"
  )
})

Deno.test("completeRegistration - stores the credential id canonicalised", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  // Standard-base64 payloads, so credential.id arrives padded and may carry +//
  const registration = await createRegistrationCredential({ challenge, encoding: 'base64' })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-credid-canon',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: [{ id: 'challenge-reg-credid-canon' }], error: null }, 'delete')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-canon' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Canonical Id Passkey' }
  )

  assertEquals(result.success, true)

  const insert = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertExists(insert)
  const stored = insert!.params.data as { credential_id: string }
  assertEquals(stored.credential_id, registration.credentialIdBase64Url)
  assertNotEquals(
    registration.credential.id,
    registration.credentialIdBase64Url,
    "This test only means something if the submitted id was not already canonical"
  )
})

Deno.test("completeAuthentication - accepts a base64url clientDataJSON containing - and _", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  const clientData = buildAlphabetSensitiveClientDataJSON({
    type: 'webauthn.get',
    challenge,
    origin: TEST_ORIGIN
  })

  // Guard the guard: this payload must actually exercise both alphabets
  assertEquals(/[-_]/.test(clientData.encoded), true, "Payload should contain base64url-only characters")
  assertEquals(/[+/]/.test(clientData.standard), true, "Payload should contain base64-only characters")

  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    clientDataJSON: clientData
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true, "base64url clientDataJSON must decode and verify")
})

Deno.test("decodeBase64Any - accepts both alphabets and rejects garbage", () => {
  const bytes = new Uint8Array([0xfb, 0xff, 0x3e, 0x7f, 0xfe])
  const standard = "+/8+f/4="
  const urlSafe = "-_8-f_4"

  assertEquals(Array.from(decodeBase64Any(standard)), Array.from(bytes))
  assertEquals(Array.from(decodeBase64Any(urlSafe)), Array.from(bytes))
  assertEquals(Array.from(decodeBase64Any(standard.replace(/=+$/, ''))), Array.from(bytes))

  for (const invalid of ['not!!!valid!!!base64!!', '', 'AAAAA!', '=', '===', '  ']) {
    let threw = false
    try {
      decodeBase64Any(invalid)
    } catch (_error) {
      threw = true
    }
    assertEquals(threw, true, `Should reject ${JSON.stringify(invalid)}`)
  }
})

Deno.test("normalizeBase64Url / encodedValuesMatch - compare across encodings", () => {
  assertEquals(normalizeBase64Url("+/8+f/4="), "-_8-f_4")
  assertEquals(encodedValuesMatch("+/8+f/4=", "-_8-f_4"), true)
  assertEquals(encodedValuesMatch("abc", "abd"), false)
  assertEquals(encodedValuesMatch("", ""), false)
  assertEquals(encodedValuesMatch("abc", "abcd"), false)
})

// ============================================================================
// Item 5 — RS256 credentials are verifiable
// ============================================================================

Deno.test("completeRegistration - stores an RS256 credential as SPKI with its algorithm", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge, alg: COSE_ALG_RS256, signCount: 3 })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-rs256',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-rs256' }, error: null }, 'delete')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-rs256' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'RS256 Passkey' }
  )

  assertEquals(result.success, true, "RS256 registration should succeed")

  const insert = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertExists(insert)
  const stored = insert!.params.data as {
    public_key: string
    public_key_alg: number
    sign_count: number
    rp_id: string
  }
  assertEquals(stored.public_key_alg, COSE_ALG_RS256)
  assertEquals(stored.sign_count, 3)
  assertEquals(stored.rp_id, TEST_RP_ID)
  assertEquals(
    stored.public_key,
    await storedPublicKey(registration.keyPair.publicKey, COSE_ALG_RS256),
    "RS256 keys are stored as SPKI so verification can import them"
  )
})

Deno.test("completeAuthentication - verifies an RS256 assertion", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge, alg: COSE_ALG_RS256 })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    alg: COSE_ALG_RS256,
    signCount: 2
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    alg: COSE_ALG_RS256,
    signCount: 1
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true, "RS256 assertions must verify")
})

Deno.test("completeAuthentication - rejects an RS256 signature over different data", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge, alg: COSE_ALG_RS256 })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    alg: COSE_ALG_RS256,
    signCount: 2
  })

  // Same challenge and rpId, but authenticatorData is swapped after signing
  const tamperedAuthData = await buildAuthenticatorData({ rpId: TEST_RP_ID, signCount: 3 })
  const tampered = {
    ...assertion,
    response: {
      ...assertion.response,
      authenticatorData: encodePayload(tamperedAuthData)
    }
  }

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    alg: COSE_ALG_RS256,
    signCount: 1
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    tampered,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Invalid signature')
})

// ============================================================================
// Happy path (ES256) — the ceremony still completes end to end
// ============================================================================

Deno.test("completeAuthentication - completes a valid ES256 ceremony", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 4
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 3
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true)
  const success = result as { userId?: string; email?: string; accessToken?: string; refreshToken?: string }
  assertEquals(success.userId, TEST_USER_ID)
  assertEquals(success.email, TEST_EMAIL)
  assertExists(success.accessToken)
  assertExists(success.refreshToken)

  // The session is parked on the completed authentication row for pollers
  const tokenUpdate = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'update'
  )
  assertExists(tokenUpdate, "Session should be persisted for /auth/status")
})

Deno.test("completeRegistration - stores an ES256 credential with rp_id and counter", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge, signCount: 0 })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-es256',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-es256' }, error: null }, 'delete')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-es256' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'ES256 Passkey' }
  )

  assertEquals(result.success, true)

  const insert = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertExists(insert)
  const stored = insert!.params.data as {
    public_key: string
    public_key_alg: number
    sign_count: number
    rp_id: string
  }
  assertEquals(stored.public_key_alg, COSE_ALG_ES256)
  assertEquals(stored.public_key, registration.expectedPublicKey)
  assertEquals(stored.rp_id, TEST_RP_ID)
  assertEquals(stored.sign_count, 0)
})

Deno.test("completeRegistration - rejects a credential id that was not attested", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const spoofed = {
    ...registration.credential,
    id: 'some-other-credential-id'
  }

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-credid',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-credid' }, error: null }, 'delete')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    spoofed as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Spoofed Credential Id' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Credential id does not match the attestation')
})

Deno.test("completeRegistration - refuses an RS256 credential when the verification columns are missing", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge, alg: COSE_ALG_RS256 })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-rs256-legacy',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: [{ id: 'challenge-reg-rs256-legacy' }], error: null }, 'delete')

  // Function deployed ahead of migration 20260725000000
  mockClient.mockResponse('user_passkeys', {
    data: null,
    error: { code: 'PGRST204', message: "Could not find the 'public_key_alg' column of 'user_passkeys'" }
  }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'RS256 Legacy Schema Passkey' }
  )

  // Storing it would produce a credential that can never authenticate: SPKI DER
  // in public_key with no algorithm recorded reads back as a raw EC point.
  assertEquals(result.success, false, "An unusable RS256 credential must not be stored")

  const inserts = mockClient.getQueryHistory().filter(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertEquals(inserts.length, 1, "Must not retry the insert without the algorithm column")
})

Deno.test("completeRegistration - still registers when the verification columns are missing", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-legacy',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-reg-legacy' }, error: null }, 'delete')

  // Function deployed ahead of migration 20260725000000
  mockClient.mockResponse('user_passkeys', {
    data: null,
    error: { code: 'PGRST204', message: "Could not find the 'sign_count' column of 'user_passkeys'" }
  }, 'insert')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-legacy' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'Legacy Schema Passkey' }
  )

  assertEquals(result.success, true, "Registration should degrade rather than fail")

  const inserts = mockClient.getQueryHistory().filter(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertEquals(inserts.length, 2, "Should retry once without the new columns")
  const retried = inserts[1].params.data as Record<string, unknown>
  assertEquals('sign_count' in retried, false)
  assertEquals('public_key_alg' in retried, false)
  assertEquals('rp_id' in retried, false)
  assertExists(retried.public_key)
})

// ============================================================================
// User Presence (WebAuthn L2 §7.1 step 16 / §7.2 step 15)
// ============================================================================

Deno.test("completeAuthentication - rejects an assertion without the User Present flag", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 2,
    flags: 0x00 // no UP, no UV — a browser cannot produce this, a native client can
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 1
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'User presence flag not set')
})

Deno.test("completeRegistration - rejects an attestation without the User Present flag", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  // AT set (attested credential data present) but UP clear
  const registration = await createRegistrationCredential({ challenge, flags: 0x40 })

  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: 'challenge-reg-up',
      challenge,
      type: 'registration',
      user_id: TEST_USER_ID,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select')
  mockClient.mockResponse('passkey_challenges', { data: [{ id: 'challenge-reg-up' }], error: null }, 'delete')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: TEST_USER_ID, displayName: 'No UP Passkey' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'User presence flag not set')
})

// ============================================================================
// Relying party allow-list: localhost is not a BOSS-owned host
// ============================================================================

Deno.test("getAllowedRpIds - excludes loopback hosts outside local development", () => {
  const previousRpId = Deno.env.get('PASSKEY_RP_ID')
  const previousUrl = Deno.env.get('SUPABASE_URL')
  const previousAllow = Deno.env.get('PASSKEY_ALLOW_LOCALHOST')

  try {
    Deno.env.delete('PASSKEY_ALLOW_LOCALHOST')
    Deno.env.set('PASSKEY_RP_ID', TEST_RP_ID)
    // The hosted edge runtime: getRpId() maps the `kong` gateway to 'localhost'
    Deno.env.set('SUPABASE_URL', 'http://kong:8000')

    const hosted = getAllowedRpIds()
    assertEquals(hosted.includes('localhost'), false, "Production must not accept a localhost RP ID")
    assertEquals(hosted.includes(TEST_RP_ID), true)

    // Local development opts in explicitly
    Deno.env.set('PASSKEY_ALLOW_LOCALHOST', 'true')
    assertEquals(getAllowedRpIds().includes('localhost'), true)

    // ...as does a genuinely loopback SUPABASE_URL
    Deno.env.delete('PASSKEY_ALLOW_LOCALHOST')
    Deno.env.set('SUPABASE_URL', 'http://127.0.0.1:54321')
    assertEquals(getAllowedRpIds().includes('localhost'), true)

    // An explicitly configured RP ID always wins, even a loopback one
    Deno.env.set('SUPABASE_URL', 'https://api.risaboss.com')
    Deno.env.set('PASSKEY_RP_ID', 'localhost')
    assertEquals(getAllowedRpIds().includes('localhost'), true)
  } finally {
    if (previousRpId === undefined) Deno.env.delete('PASSKEY_RP_ID')
    else Deno.env.set('PASSKEY_RP_ID', previousRpId)
    if (previousUrl === undefined) Deno.env.delete('SUPABASE_URL')
    else Deno.env.set('SUPABASE_URL', previousUrl)
    if (previousAllow === undefined) Deno.env.delete('PASSKEY_ALLOW_LOCALHOST')
    else Deno.env.set('PASSKEY_ALLOW_LOCALHOST', previousAllow)
  }
})

Deno.test("completeAuthentication - rejects a localhost assertion for a legacy credential in production", async () => {
  const mockClient = createMockSupabaseClient()
  const previousUrl = Deno.env.get('SUPABASE_URL')

  try {
    Deno.env.set('SUPABASE_URL', 'http://kong:8000')

    const challenge = generateChallenge()
    const registration = await createRegistrationCredential({ challenge })
    const assertion = await createAssertion({
      challenge,
      credentialId: registration.credentialId,
      privateKey: registration.keyPair.privateKey,
      rpId: 'localhost'
    })

    mockAuthFlow(mockClient, {
      challenge,
      storedPublicKey: registration.expectedPublicKey,
      credentialId: registration.credentialIdBase64Url,
      rpId: null // legacy row, so the allow-list decides
    })

    const result = await completeAuthentication(
      mockClient as unknown as SupabaseClient,
      assertion,
      challenge
    )

    assertEquals(result.success, false)
    assertEquals(
      (result as { error?: string }).error,
      'Relying party mismatch - assertion was produced for a different rpId'
    )
  } finally {
    if (previousUrl === undefined) Deno.env.delete('SUPABASE_URL')
    else Deno.env.set('SUPABASE_URL', previousUrl)
  }
})

// ============================================================================
// Origin ↔ rpId correspondence
// ============================================================================

Deno.test("rpIdMatchesOrigin - requires a registrable-domain suffix", () => {
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', 'https://api.risaboss.com'), true)
  assertEquals(rpIdMatchesOrigin('risaboss.com', 'https://api.risaboss.com'), true, 'suffix is legal')
  assertEquals(rpIdMatchesOrigin('localhost', 'http://localhost:54321'), true, 'port is irrelevant')

  // The pairing that satisfies both allow-lists but no browser
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', 'http://localhost:3000'), false)
  assertEquals(rpIdMatchesOrigin('risaboss.com', 'https://evilrisaboss.com'), false, 'not a dot boundary')
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', 'https://risaboss.com'), false, 'RP ID cannot be longer')

  // Custom schemes have no effective domain, so they are exempt rather than rejected
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', 'boss://authenticate'), true)
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', ''), false)
  assertEquals(rpIdMatchesOrigin('api.risaboss.com', undefined), false)
})

Deno.test("completeAuthentication - rejects an rpId that does not correspond to the origin", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  // Ceremony performed at localhost:3000 but signed for api.risaboss.com: both
  // allow-lists are satisfied, the correspondence rule is not.
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    rpId: TEST_RP_ID,
    origin: 'http://localhost:3000',
    signCount: 2
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 1
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, false)
  assertEquals(
    (result as { error?: string }).error,
    'Relying party mismatch - rpId does not correspond to the ceremony origin'
  )
})

// ============================================================================
// Cross-device row hygiene
// ============================================================================

Deno.test("completeAuthentication - upserts the completed authentication on session_id", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })
  const assertion = await createAssertion({
    challenge,
    credentialId: registration.credentialId,
    privateKey: registration.keyPair.privateKey,
    signCount: 6
  })

  mockAuthFlow(mockClient, {
    challenge,
    storedPublicKey: registration.expectedPublicKey,
    credentialId: registration.credentialIdBase64Url,
    signCount: 5
  })

  const result = await completeAuthentication(
    mockClient as unknown as SupabaseClient,
    assertion,
    challenge
  )

  assertEquals(result.success, true)

  // A second row for one client-supplied sessionId would wedge polling at
  // "expired" and fan the token write across both rows.
  const write = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'insert'
  )
  assertExists(write)
  assertEquals(
    (write!.params.upsert as { onConflict?: string } | undefined)?.onConflict,
    'session_id'
  )
  // The row carries its own security window rather than relying on the default
  const written = write!.params.data as { expires_at_timestamp?: string }
  assertExists(written.expires_at_timestamp)
})

// ============================================================================
// authenticatorData parsing
// ============================================================================

Deno.test("parseAuthenticatorData - rejects a zero-length credential id", async () => {
  // An empty Uint8Array is truthy, so a zero-length attested id would encode to
  // "" and slip past the credential-id binding in the registration service.
  const rpIdHash = await rpIdHashFor(TEST_RP_ID)
  const authData = new Uint8Array([
    ...rpIdHash,
    0x45,                    // UP + UV + AT
    0x00, 0x00, 0x00, 0x00,  // signCount
    ...new Array(16).fill(0x00), // aaguid
    0x00, 0x00,              // credentialIdLength = 0
    0xA0                     // empty COSE map
  ])

  let threw = false
  try {
    parseAuthenticatorData(authData)
  } catch (error) {
    threw = true
    assertEquals((error as Error).message.includes('zero-length credential id'), true)
  }
  assertEquals(threw, true)
})

Deno.test("parseAuthenticatorData - reads rpIdHash, flags and counter", async () => {
  const authData = await buildAuthenticatorData({ rpId: TEST_RP_ID, signCount: 0xdeadbeef })
  const parsed = parseAuthenticatorData(authData)

  assertEquals(Array.from(parsed.rpIdHash), Array.from(await rpIdHashFor(TEST_RP_ID)))
  assertEquals(parsed.signCount, 0xdeadbeef, "Counter must be read as unsigned")
  assertEquals(parsed.userPresent, true)
  assertEquals(parsed.userVerified, true)
  assertEquals(parsed.attestedCredentialData, false)
})

Deno.test("parseAuthenticatorData - rejects truncated authenticator data", () => {
  let threw = false
  try {
    parseAuthenticatorData(new Uint8Array(36))
  } catch (_error) {
    threw = true
  }
  assertEquals(threw, true)
})

// ============================================================================
// Item 6 — /auth/status must not mint a session per poll
// ============================================================================

/** A completed_authentications row carrying a live parked session */
function storedSessionRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'completed-row-1',
    session_id: 'session-poll',
    user_id: TEST_USER_ID,
    email: TEST_EMAIL,
    access_token: 'stored-access-token',
    refresh_token: 'stored-refresh-token',
    expires_at: Date.now() + 3_600_000, // epoch milliseconds, per the column comment
    expires_at_timestamp: new Date(Date.now() + 240_000).toISOString(),
    created_at: new Date().toISOString(),
    ...overrides
  }
}

Deno.test("checkAuthStatus - replays the stored session instead of minting a new one", async () => {
  const mockClient = createMockSupabaseClient()

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')

  mockClient.mockResponse('completed_authentications', { data: storedSessionRow(), error: null }, 'select')
  // The one-time-use claim succeeds for this caller
  mockClient.mockResponse('completed_authentications', { data: [{ id: 'completed-row-1' }], error: null }, 'update')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-poll')

  assertEquals(result.status, 'completed')
  const completed = result as { accessToken?: string; refreshToken?: string; email?: string }
  assertEquals(completed.accessToken, 'stored-access-token')
  assertEquals(completed.refreshToken, 'stored-refresh-token')
  assertEquals(completed.email, TEST_EMAIL)

  // No new session was minted: nothing looked the user up to generate one
  const mintedSession = mockClient.getQueryHistory().some(entry => entry.table === 'users')
  assertEquals(mintedSession, false, "A completed poll should not mint another session")

  // The tokens are retired as they are handed over (one-time use)
  const claim = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'update'
  )
  assertExists(claim, "The stored session should be cleared once served")
  const cleared = claim!.params.data as Record<string, unknown>
  assertEquals(cleared.access_token, null)
  assertEquals(cleared.refresh_token, null)
  assertEquals(cleared.expires_at, null)
  // ...conditionally, so two concurrent polls cannot both serve the same pair
  assertEquals(
    (claim!.params.not as Array<{ column: string; operator: string }>)
      .some(f => f.column === 'access_token' && f.operator === 'is'),
    true,
    "The clear must be a compare-and-set on access_token"
  )
})

Deno.test("checkAuthStatus - scopes the lookup to the row's security window", async () => {
  const mockClient = createMockSupabaseClient()

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')
  mockClient.mockResponse('completed_authentications', { data: storedSessionRow(), error: null }, 'select')
  mockClient.mockResponse('completed_authentications', { data: [{ id: 'completed-row-1' }], error: null }, 'update')

  await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-poll')

  // expires_at_timestamp is the documented 5-minute window for this row, and the
  // row now holds session tokens. Cleanup of expired rows is a probabilistic
  // trigger, so the query has to enforce the window itself.
  const lookup = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'select'
  )
  assertExists(lookup)
  assertEquals(
    (lookup!.params.gt as { column: string } | undefined)?.column,
    'expires_at_timestamp',
    "A row past its security window must not be readable"
  )
})

Deno.test("checkAuthStatus - does not serve a stored session twice", async () => {
  const mockClient = createMockSupabaseClient()
  mockClient.mockAuthUser(TEST_EMAIL, TEST_USER_ID)

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')
  mockClient.mockResponse('completed_authentications', { data: storedSessionRow(), error: null }, 'select')
  // The claim finds nothing to clear: another poll already took this pair
  mockClient.mockResponse('completed_authentications', { data: [], error: null }, 'update')
  mockClient.mockResponse('users', { data: { id: TEST_USER_ID, email: TEST_EMAIL }, error: null }, 'select')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-poll')

  assertEquals(result.status, 'completed')
  const completed = result as { accessToken?: string; refreshToken?: string }
  assertNotEquals(
    completed.accessToken,
    'stored-access-token',
    "A pair that was already claimed must not be served again"
  )
  assertExists(completed.accessToken, "The poll should still get a usable session")
})

Deno.test("checkAuthStatus - mints a fresh session when the stored expiry is unknown", async () => {
  const mockClient = createMockSupabaseClient()
  mockClient.mockAuthUser(TEST_EMAIL, TEST_USER_ID)

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')

  // Tokens present but no parseable expiry: their remaining life is unknown, so
  // replaying them could hand back a dead session. Fail closed and mint.
  mockClient.mockResponse('completed_authentications', {
    data: {
      session_id: 'session-null-expiry',
      user_id: TEST_USER_ID,
      email: TEST_EMAIL,
      access_token: 'stored-access-token',
      refresh_token: 'stored-refresh-token',
      expires_at: null,
      created_at: new Date().toISOString()
    },
    error: null
  }, 'select')

  mockClient.mockResponse('users', { data: { id: TEST_USER_ID, email: TEST_EMAIL }, error: null }, 'select')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-null-expiry')

  assertEquals(result.status, 'completed')
  const completed = result as { accessToken?: string; expiresAt?: number }
  assertNotEquals(completed.accessToken, 'stored-access-token', "A token of unknown age must not be replayed")
  assertExists(completed.expiresAt)
})

Deno.test("checkAuthStatus - writes the stored expiry in epoch milliseconds", async () => {
  const mockClient = createMockSupabaseClient()
  mockClient.mockAuthUser(TEST_EMAIL, TEST_USER_ID)

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')
  mockClient.mockResponse('completed_authentications', {
    data: { session_id: 'session-units', user_id: TEST_USER_ID, created_at: new Date().toISOString() },
    error: null
  }, 'select')
  mockClient.mockResponse('users', { data: { id: TEST_USER_ID, email: TEST_EMAIL }, error: null }, 'select')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-units')
  assertEquals(result.status, 'completed')

  const update = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'update'
  )
  assertExists(update)
  const written = (update!.params.data as { expires_at: number }).expires_at
  const apiExpiresAt = (result as { expiresAt?: number }).expiresAt
  assertExists(apiExpiresAt)
  // Column is documented as epoch milliseconds; the API response is seconds
  assertEquals(written, apiExpiresAt! * 1000)
  assertEquals(written > Date.now(), true)
})

Deno.test("checkAuthStatus - mints a session when none was recorded yet", async () => {
  const mockClient = createMockSupabaseClient()
  mockClient.mockAuthUser(TEST_EMAIL, TEST_USER_ID)

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')

  mockClient.mockResponse('completed_authentications', {
    data: {
      session_id: 'session-poll-2',
      user_id: TEST_USER_ID,
      created_at: new Date().toISOString()
    },
    error: null
  }, 'select')

  mockClient.mockResponse('users', { data: { id: TEST_USER_ID, email: TEST_EMAIL }, error: null }, 'select')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-poll-2')

  assertEquals(result.status, 'completed')
  const completed = result as { accessToken?: string; refreshToken?: string }
  assertExists(completed.accessToken)

  // ...and it is persisted so the next poll replays it
  const tokenUpdate = mockClient.getQueryHistory().find(
    entry => entry.table === 'completed_authentications' && entry.operation === 'update'
  )
  assertExists(tokenUpdate, "A freshly minted session should be persisted")
})

// ============================================================================
// Who a credential gets enrolled for
//
// The enrolling account is taken from the challenge row, which only a caller
// holding that user's session can create. Nothing in the completion request
// selects it.
// ============================================================================

/** Queues a registration challenge row scoped to the exact challenge value */
function mockRegistrationChallenge(
  mockClient: MockSupabaseClient,
  challenge: string,
  userId: string | null,
  rowId = 'challenge-reg-row'
) {
  mockClient.mockResponse('passkey_challenges', {
    data: {
      id: rowId,
      challenge,
      type: 'registration',
      user_id: userId,
      expires_at: new Date(Date.now() + 300_000).toISOString()
    },
    error: null
  }, 'select', { match: { challenge, type: 'registration' } })

  mockClient.mockResponse('passkey_challenges', { data: [{ id: rowId }], error: null }, 'delete')
}

Deno.test("completeRegistration - enrols against the challenge's user when the body names nobody", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  mockRegistrationChallenge(mockClient, challenge, 'user-owner')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-1' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { displayName: 'First Passkey' }
  )

  assertEquals(result.success, true)

  const insert = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertExists(insert)
  assertEquals(
    (insert!.params.data as { user_id: string }).user_id,
    'user-owner',
    'The credential belongs to whoever the challenge was issued to'
  )
})

Deno.test("completeRegistration - a body userId cannot redirect the enrolment", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  // The challenge belongs to one account; the request names another
  mockRegistrationChallenge(mockClient, challenge, 'user-owner')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-1' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: 'user-someone-else', displayName: 'Redirected Passkey' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Challenge does not belong to this user')
  assertEquals(
    mockClient.getQueryHistory().some(
      entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
    ),
    false,
    'Nothing may be enrolled when the request disagrees with the challenge'
  )
})

Deno.test("completeRegistration - a verified session for another user cannot enrol", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  mockRegistrationChallenge(mockClient, challenge, 'user-owner')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { authenticatedUserId: 'user-someone-else' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Challenge does not belong to this user')
})

Deno.test("completeRegistration - refuses a challenge that is not bound to a user", async () => {
  const mockClient = createMockSupabaseClient()

  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  // No current code path produces this; fail closed rather than enrol nowhere
  mockRegistrationChallenge(mockClient, challenge, null)

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: 'user-owner' }
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Challenge is not bound to a user')
})

Deno.test("completeRegistration - first passkey for an account with none enrols normally", async () => {
  const mockClient = createMockSupabaseClient()

  // Bootstrap: the session comes from email/OTP sign-in, not from a passkey, so
  // an account with no credentials yet can still enrol its first one.
  const challenge = generateChallenge()
  const registration = await createRegistrationCredential({ challenge })

  mockRegistrationChallenge(mockClient, challenge, 'user-brand-new')
  mockClient.mockResponse('user_passkeys', { data: [{ id: 'passkey-first' }], error: null }, 'insert')

  const result = await completeRegistration(
    mockClient as unknown as SupabaseClient,
    registration.credential as RegistrationCredential,
    challenge,
    { claimedUserId: 'user-brand-new', authenticatedUserId: 'user-brand-new', displayName: 'My First Passkey' }
  )

  assertEquals(result.success, true)
  const insert = mockClient.getQueryHistory().find(
    entry => entry.table === 'user_passkeys' && entry.operation === 'insert'
  )
  assertExists(insert)
  assertEquals((insert!.params.data as { user_id: string }).user_id, 'user-brand-new')
})
