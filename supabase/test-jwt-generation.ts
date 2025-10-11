#!/usr/bin/env -S deno run --allow-env --allow-net

import { SignJWT } from "npm:jose@^5.2.0"

/**
 * Test script to verify JWT generation works correctly
 */
async function testJWTGeneration() {
  console.log('🧪 Testing JWT Generation\n')

  // Test JWT secret (use a test value for local testing)
  const testJwtSecret = "test-secret-at-least-32-characters-long-for-hs256-algorithm"
  const testUserId = "929caded-50c2-4b75-9fea-f300264b2b45"
  const testEmail = "test@example.com"

  console.log('📋 Test Parameters:')
  console.log(`  User ID: ${testUserId}`)
  console.log(`  Email: ${testEmail}`)
  console.log(`  JWT Secret Length: ${testJwtSecret.length} characters\n`)

  try {
    const now = Math.floor(Date.now() / 1000)
    const expiresIn = 3600 // 1 hour
    const expiresAt = now + expiresIn

    const secret = new TextEncoder().encode(testJwtSecret)

    console.log('🔑 Generating Access Token...')
    const accessToken = await new SignJWT({
      sub: testUserId,
      email: testEmail,
      role: 'authenticated',
      aal: 'aal1',
      session_id: crypto.randomUUID(),
      is_anonymous: false,
      amr: [{ method: 'passkey', timestamp: now }]
    })
      .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
      .setIssuedAt(now)
      .setExpirationTime(expiresAt)
      .setIssuer('supabase')
      .setAudience('authenticated')
      .sign(secret)

    console.log('✅ Access Token Generated Successfully')
    console.log(`  Token Length: ${accessToken.length} characters`)
    console.log(`  Token Preview: ${accessToken.substring(0, 50)}...`)
    console.log(`  Expires In: ${expiresIn} seconds (1 hour)\n`)

    console.log('🔄 Generating Refresh Token...')
    const refreshToken = await new SignJWT({
      sub: testUserId,
      session_id: crypto.randomUUID()
    })
      .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
      .setIssuedAt(now)
      .setExpirationTime(now + (30 * 24 * 3600)) // 30 days
      .setIssuer('supabase')
      .sign(secret)

    console.log('✅ Refresh Token Generated Successfully')
    console.log(`  Token Length: ${refreshToken.length} characters`)
    console.log(`  Token Preview: ${refreshToken.substring(0, 50)}...`)
    console.log(`  Expires In: ${30 * 24 * 3600} seconds (30 days)\n`)

    // Decode token to verify structure
    console.log('🔍 Decoding Access Token...')
    const parts = accessToken.split('.')
    if (parts.length === 3) {
      const header = JSON.parse(atob(parts[0]))
      const payload = JSON.parse(atob(parts[1]))

      console.log('  Header:', JSON.stringify(header, null, 2))
      console.log('  Payload:', JSON.stringify(payload, null, 2))
    }

    console.log('\n✅ JWT Generation Test PASSED')
    console.log('\n📝 Next Steps:')
    console.log('  1. Deploy Edge Functions to cluster')
    console.log('  2. Set SUPABASE_JWT_SECRET environment variable')
    console.log('  3. Test end-to-end authentication flow')

    return {
      success: true,
      accessToken,
      refreshToken,
      expiresAt,
      expiresIn
    }
  } catch (error) {
    console.error('❌ JWT Generation Test FAILED')
    console.error('  Error:', error.message)
    if (error.stack) {
      console.error('  Stack:', error.stack)
    }
    return {
      success: false,
      error: error.message
    }
  }
}

// Run the test
testJWTGeneration()
