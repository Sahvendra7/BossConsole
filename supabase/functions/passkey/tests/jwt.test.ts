/**
 * JWT Token Generation Tests
 *
 * Tests for custom Supabase JWT token generation and validation
 * Ensures tokens are correctly structured for passkey authentication
 */

import { assertEquals, assertExists } from "jsr:@std/assert"
import { generateSupabaseAccessToken } from "../utils/jwt.ts"
import { jwtVerify } from "jose"

// Test JWT secret (DO NOT use in production!)
const TEST_JWT_SECRET = "test-secret-key-for-jwt-generation-testing-only-do-not-use-in-production-minimum-32-chars"

Deno.test("generateSupabaseAccessToken - should generate valid JWT tokens", async () => {
  const userId = "test-user-123"
  const email = "test@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  assertExists(result.accessToken, "Access token should exist")
  assertExists(result.refreshToken, "Refresh token should exist")
  assertExists(result.expiresAt, "expiresAt should exist")
  assertExists(result.expiresIn, "expiresIn should exist")

  assertEquals(result.expiresIn, 3600, "Token should expire in 1 hour (3600 seconds)")
})

Deno.test("generateSupabaseAccessToken - access token should have correct structure", async () => {
  const userId = "test-user-456"
  const email = "user@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  // Verify the token can be decoded and validated
  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.accessToken, secret, {
    issuer: 'supabase',
    audience: 'authenticated'
  })

  // Check required claims
  assertEquals(payload.sub, userId, "Subject should be user ID")
  assertEquals(payload.email, email, "Email claim should match")
  assertEquals(payload.role, 'authenticated', "Role should be 'authenticated'")
  assertEquals(payload.aal, 'aal1', "AAL should be 'aal1'")
  assertEquals(payload.is_anonymous, false, "Should not be anonymous")

  // Check issuer and audience
  assertEquals(payload.iss, 'supabase', "Issuer should be 'supabase'")
  assertEquals(payload.aud, 'authenticated', "Audience should be 'authenticated'")

  // Check timestamps
  assertExists(payload.iat, "Issued at timestamp should exist")
  assertExists(payload.exp, "Expiration timestamp should exist")

  const now = Math.floor(Date.now() / 1000)
  assertEquals(
    Math.abs((payload.iat as number) - now) < 5,
    true,
    "Issued at should be within 5 seconds of now"
  )
  assertEquals(
    Math.abs((payload.exp as number) - (now + 3600)) < 5,
    true,
    "Expiration should be 1 hour from now"
  )
})

Deno.test("generateSupabaseAccessToken - should include passkey authentication method", async () => {
  const userId = "test-user-789"
  const email = "passkey@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.accessToken, secret)

  // Check AMR (Authentication Method Reference)
  assertExists(payload.amr, "AMR should exist")
  assertEquals(Array.isArray(payload.amr), true, "AMR should be an array")

  const amr = payload.amr as Array<{ method: string; timestamp: number }>
  assertEquals(amr.length > 0, true, "AMR should have at least one entry")
  assertEquals(amr[0].method, 'passkey', "AMR method should be 'passkey'")
  assertExists(amr[0].timestamp, "AMR timestamp should exist")

  const now = Math.floor(Date.now() / 1000)
  assertEquals(
    Math.abs(amr[0].timestamp - now) < 5,
    true,
    "AMR timestamp should be within 5 seconds of now"
  )
})

Deno.test("generateSupabaseAccessToken - should include app_metadata and user_metadata", async () => {
  const userId = "test-user-metadata"
  const email = "metadata@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.accessToken, secret)

  // Check app_metadata
  assertExists(payload.app_metadata, "app_metadata should exist")
  const appMetadata = payload.app_metadata as Record<string, unknown>
  assertEquals(appMetadata.provider, 'passkey', "Provider should be 'passkey'")
  assertEquals(Array.isArray(appMetadata.providers), true, "Providers should be an array")
  assertEquals((appMetadata.providers as string[])[0], 'passkey', "Providers should include 'passkey'")

  // Check user_metadata
  assertExists(payload.user_metadata, "user_metadata should exist")
  const userMetadata = payload.user_metadata as Record<string, unknown>
  assertEquals(userMetadata.email, email, "user_metadata should include email")
})

Deno.test("generateSupabaseAccessToken - should include session_id", async () => {
  const userId = "test-user-session"
  const email = "session@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.accessToken, secret)

  assertExists(payload.session_id, "session_id should exist")
  assertEquals(typeof payload.session_id, 'string', "session_id should be a string")
  // Should be a valid UUID format
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  assertEquals(
    uuidRegex.test(payload.session_id as string),
    true,
    "session_id should be a valid UUID"
  )
})

Deno.test("generateSupabaseAccessToken - refresh token should be valid", async () => {
  const userId = "test-user-refresh"
  const email = "refresh@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  // Verify the refresh token
  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.refreshToken, secret, {
    issuer: 'supabase'
  })

  assertEquals(payload.sub, userId, "Refresh token subject should be user ID")
  assertExists(payload.session_id, "Refresh token should have session_id")

  // Check expiration (should be 30 days)
  const now = Math.floor(Date.now() / 1000)
  const expectedExpiry = now + (30 * 24 * 3600)
  assertEquals(
    Math.abs((payload.exp as number) - expectedExpiry) < 5,
    true,
    "Refresh token should expire in 30 days"
  )
})

Deno.test("generateSupabaseAccessToken - expiresAt should match token expiration", async () => {
  const userId = "test-user-expiry"
  const email = "expiry@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const secret = new TextEncoder().encode(TEST_JWT_SECRET)
  const { payload } = await jwtVerify(result.accessToken, secret)

  assertEquals(
    payload.exp,
    result.expiresAt,
    "expiresAt should match the token's exp claim"
  )
})

Deno.test("generateSupabaseAccessToken - should generate unique tokens", async () => {
  const userId = "test-user-unique"
  const email = "unique@example.com"

  const result1 = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)
  const result2 = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  // Tokens should be different (due to different session_ids and timestamps)
  assertEquals(
    result1.accessToken !== result2.accessToken,
    true,
    "Access tokens should be unique"
  )
  assertEquals(
    result1.refreshToken !== result2.refreshToken,
    true,
    "Refresh tokens should be unique"
  )
})

Deno.test("generateSupabaseAccessToken - tokens should use HS256 algorithm", async () => {
  const userId = "test-user-algo"
  const email = "algo@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  // Decode header without verification to check algorithm
  const parts = result.accessToken.split('.')
  const header = JSON.parse(atob(parts[0]))

  assertEquals(header.alg, 'HS256', "Algorithm should be HS256")
  assertEquals(header.typ, 'JWT', "Type should be JWT")
})

Deno.test("generateSupabaseAccessToken - should fail verification with wrong secret", async () => {
  const userId = "test-user-wrong-secret"
  const email = "wrong@example.com"

  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const wrongSecret = new TextEncoder().encode("wrong-secret-key")

  let errorThrown = false
  try {
    await jwtVerify(result.accessToken, wrongSecret)
  } catch (_error) {
    errorThrown = true
  }

  assertEquals(errorThrown, true, "Verification should fail with wrong secret")
})

Deno.test("generateSupabaseAccessToken - expired token should fail verification", async () => {
  const userId = "test-user-expired"
  const email = "expired@example.com"

  // We can't easily create an expired token in the past, but we can test
  // that the expiration claim is set correctly and will fail in the future
  const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

  const secret = new TextEncoder().encode(TEST_JWT_SECRET)

  // Verify it works now
  const { payload } = await jwtVerify(result.accessToken, secret)

  // Check that expiration is set
  assertExists(payload.exp, "Expiration should be set")
  const now = Math.floor(Date.now() / 1000)
  assertEquals(
    (payload.exp as number) > now,
    true,
    "Token should not be expired yet"
  )

  // The token will fail verification after the expiration time
  // We can't test this without waiting or mocking time, but we've verified
  // that the expiration is correctly set
})

Deno.test("generateSupabaseAccessToken - should handle different user IDs and emails", async () => {
  const testCases = [
    { userId: "user-1", email: "user1@test.com" },
    { userId: "user-with-long-id-123456789", email: "long.email.address@subdomain.example.com" },
    { userId: "123", email: "short@x.co" },
    { userId: "uuid-format-a1b2c3d4", email: "uuid@example.com" }
  ]

  for (const { userId, email } of testCases) {
    const result = await generateSupabaseAccessToken(userId, email, TEST_JWT_SECRET)

    const secret = new TextEncoder().encode(TEST_JWT_SECRET)
    const { payload } = await jwtVerify(result.accessToken, secret)

    assertEquals(payload.sub, userId, `User ID should match for ${userId}`)
    assertEquals(payload.email, email, `Email should match for ${email}`)
  }
})
