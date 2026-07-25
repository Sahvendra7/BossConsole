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
}

/**
 * Queues the database responses a full assertion ceremony walks through.
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
  }, 'select')

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
  }, 'select')

  mockClient.mockResponse('user_passkeys', { data: { id: 'passkey-row-1' }, error: null }, 'update')
  mockClient.mockResponse('completed_authentications', { data: [{ id: 'completed-1' }], error: null }, 'insert')
  mockClient.mockResponse('passkey_challenges', { data: { id: 'challenge-row-1' }, error: null }, 'delete')
  mockClient.mockResponse('users', { data: { id: userId, email: TEST_EMAIL }, error: null }, 'select')
  mockClient.mockAuthUser(TEST_EMAIL, userId)
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    issuedChallenge,
    'Mismatched Passkey'
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    challenge,
    'Wrong User Passkey'
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    challenge,
    'Foreign RP Passkey'
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

  for (const invalid of ['not!!!valid!!!base64!!', '', 'AAAAA!']) {
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    challenge,
    'RS256 Passkey'
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    challenge,
    'ES256 Passkey'
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
    TEST_USER_ID,
    spoofed as RegistrationCredential,
    challenge,
    'Spoofed Credential Id'
  )

  assertEquals(result.success, false)
  assertEquals((result as { error?: string }).error, 'Credential id does not match the attestation')
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
    TEST_USER_ID,
    registration.credential as RegistrationCredential,
    challenge,
    'Legacy Schema Passkey'
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
// authenticatorData parsing
// ============================================================================

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

Deno.test("checkAuthStatus - replays the stored session instead of minting a new one", async () => {
  const mockClient = createMockSupabaseClient()

  mockClient.mockResponse('passkey_challenges', {
    data: null,
    error: { code: 'PGRST116', message: 'Challenge consumed' }
  }, 'select')

  mockClient.mockResponse('completed_authentications', {
    data: {
      session_id: 'session-poll',
      user_id: TEST_USER_ID,
      email: TEST_EMAIL,
      access_token: 'stored-access-token',
      refresh_token: 'stored-refresh-token',
      expires_at: Math.floor(Date.now() / 1000) + 3600,
      created_at: new Date().toISOString()
    },
    error: null
  }, 'select')

  const result = await checkAuthStatus(mockClient as unknown as SupabaseClient, 'session-poll')

  assertEquals(result.status, 'completed')
  const completed = result as { accessToken?: string; refreshToken?: string; email?: string }
  assertEquals(completed.accessToken, 'stored-access-token')
  assertEquals(completed.refreshToken, 'stored-refresh-token')
  assertEquals(completed.email, TEST_EMAIL)

  // No new session was minted: nothing looked the user up to generate one
  const mintedSession = mockClient.getQueryHistory().some(entry => entry.table === 'users')
  assertEquals(mintedSession, false, "A completed poll should not mint another session")
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
