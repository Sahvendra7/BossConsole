import { SignJWT } from "jose"

/**
 * Generates custom Supabase JWT tokens for passkey authentication
 *
 * WHY CUSTOM JWTS:
 * ================
 * Supabase Auth was designed for built-in authentication providers (OAuth, email/password, magic links).
 * For custom authentication providers like WebAuthn/passkeys, we need to:
 * 1. Implement the authentication logic ourselves (verify passkey signatures)
 * 2. Generate Supabase-compatible JWT tokens after successful verification
 * 3. Allow clients to establish sessions using these tokens
 *
 * HOW IT WORKS:
 * =============
 * 1. Client performs passkey authentication (Touch ID, Windows Hello, etc.)
 * 2. Edge Function verifies the passkey signature cryptographically
 * 3. This function generates Supabase-compatible JWT tokens
 * 4. Client imports the session using auth.importSession()
 * 5. Client can now make authenticated requests to Supabase APIs
 *
 * LIMITATIONS:
 * ============
 * - session.user will be null in Supabase-KT clients
 *   This is by design - custom JWTs don't include the full user metadata
 *   that built-in providers include
 * - Clients must persist user data separately (see UserDataStorage in client code)
 * - This is NOT a bug - it's the correct pattern for custom auth providers
 *
 * TOKEN STRUCTURE:
 * ================
 * Access Token Claims:
 * - sub: User ID
 * - email: User email address
 * - role: 'authenticated' (grants access to authenticated endpoints)
 * - aal: 'aal1' (Authentication Assurance Level 1)
 * - amr: [{ method: 'passkey', timestamp }] (Authentication Method Reference)
 * - app_metadata: Provider information
 * - user_metadata: Additional user data
 *
 * Refresh Token:
 * - Simplified implementation (30-day expiry)
 * - Production systems should store refresh tokens securely
 * - Used by Supabase-KT for automatic token renewal
 *
 * SECURITY CONSIDERATIONS:
 * ========================
 * - JWT_SECRET must be kept secure (matches Supabase project JWT secret)
 * - Tokens are only generated AFTER successful passkey verification
 * - Signature verification happens using Web Crypto API
 * - Tokens expire after 1 hour (standard Supabase session duration)
 *
 * RELATED DOCUMENTATION:
 * ======================
 * - See SessionManager.kt (client) for session establishment
 * - See UserDataStorage.kt (client) for user data persistence
 * - See auth.ts (server) for authentication flow
 * - See crypto.ts (server) for signature verification
 *
 * @param userId - The authenticated user's ID from Supabase auth.users
 * @param email - The authenticated user's email address
 * @param jwtSecret - Supabase project JWT secret (from environment)
 * @returns Object containing accessToken, refreshToken, expiresAt, and expiresIn
 */
export async function generateSupabaseAccessToken(
  userId: string,
  email: string,
  jwtSecret: string
): Promise<{ accessToken: string; refreshToken: string; expiresAt: number; expiresIn: number }> {
  const now = Math.floor(Date.now() / 1000)
  const expiresIn = 3600 // 1 hour
  const expiresAt = now + expiresIn

  // Get the JWT secret from environment (this is your Supabase JWT secret)
  const secret = new TextEncoder().encode(jwtSecret)

  // Create the access token with required Supabase claims
  // Including app_metadata and user_metadata for proper user object population
  const accessToken = await new SignJWT({
    sub: userId,
    email: email,
    role: 'authenticated',
    aal: 'aal1', // Authentication Assurance Level
    session_id: crypto.randomUUID(),
    is_anonymous: false,
    // Add custom claim to indicate passkey auth
    amr: [{ method: 'passkey', timestamp: now }],
    // Add metadata for user object population
    app_metadata: {
      provider: 'passkey',
      providers: ['passkey']
    },
    user_metadata: {
      email: email
    }
  })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt(now)
    .setExpirationTime(expiresAt)
    .setIssuer('supabase')
    .setAudience('authenticated')
    .sign(secret)

  // Generate a refresh token (simplified - in production you'd store this)
  const refreshToken = await new SignJWT({
    sub: userId,
    session_id: crypto.randomUUID()
  })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt(now)
    .setExpirationTime(now + (30 * 24 * 3600)) // 30 days
    .setIssuer('supabase')
    .sign(secret)

  return {
    accessToken,
    refreshToken,
    expiresAt,
    expiresIn
  }
}
