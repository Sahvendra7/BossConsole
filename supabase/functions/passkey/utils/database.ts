import type { SupabaseClient } from "@supabase/supabase-js"
import { ChallengeType } from "../types/challenge.ts"

export interface PasskeyRecord {
  id: string
  user_id: string
  credential_id: string
  public_key: string
  display_name: string
  transports: string[]
  created_at: number
  last_used_at?: number
  active: boolean
  /** COSE algorithm of public_key (-7 ES256, -257 RS256) */
  public_key_alg?: number
  /** Last signature counter seen from this authenticator (0 = counter unsupported) */
  sign_count?: number
  /** RP ID this credential was registered for */
  rp_id?: string | null
}

export async function verifyChallenge(
  supabase: SupabaseClient,
  challenge: string,
  type: ChallengeType
) {
  console.log('🔍 verifyChallenge called with:', {
    challenge: challenge.substring(0, 20) + '...',
    type
  })

  try {
    const { data: challengeData, error } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', challenge)
      .eq('type', type)
      .single()

    console.log('🔍 verifyChallenge result:', { found: !!challengeData, error: error?.message })

    if (error || !challengeData) {
      console.error('Challenge verification failed:', error)
      return { success: false, error: 'Invalid or expired challenge' }
    }

    const expiresAt = new Date(challengeData.expires_at)
    if (expiresAt < new Date()) {
      return { success: false, error: 'Challenge expired' }
    }

    return { success: true, challengeData }
  } catch (error) {
    console.error('Challenge verification error:', error)
    return { success: false, error: 'Challenge verification failed' }
  }
}

export async function verifyAndConsumeChallenge(
  supabase: SupabaseClient,
  challenge: string,
  type: ChallengeType
) {
  console.log('🔥 verifyAndConsumeChallenge called with:', {
    challenge: challenge.substring(0, 20) + '...',
    type
  })

  try {
    const { data, error } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', challenge)
      .eq('type', type)
      .gt('expires_at', new Date().toISOString())
      .single()

    if (error || !data) {
      console.error('Challenge not found or expired:', error)
      return { success: false, error: 'Invalid or expired challenge' }
    }

    // Delete the challenge after successful verification
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('id', data.id)

    console.log('Challenge verified and consumed successfully')
    return { success: true, challenge: data }
  } catch (error) {
    console.error('Exception verifying challenge:', error)
    return { success: false, error: (error as Error).message }
  }
}

export async function storePasskeyInDB(
  supabase: SupabaseClient,
  passkey: Omit<PasskeyRecord, 'id' | 'created_at' | 'active'>
) {
  console.log('storePasskeyInDB called with credential:', passkey.credential_id)

  try {
    const insertData = {
      ...passkey,
      created_at: Date.now(),
      active: true
    }

    const { data, error } = await supabase
      .from('user_passkeys')
      .insert(insertData)
      .select()

    if (error) {
      // The verification columns (public_key_alg, sign_count, rp_id) arrive with
      // migration 20260725000000. If the function is deployed ahead of it,
      // registration would otherwise break outright — degrade instead, loudly.
      if (isUnknownColumnError(error)) {
        console.error(
          '⚠️ user_passkeys is missing the passkey verification columns — apply migration ' +
          '20260725000000_passkey_verification_columns.sql. Storing the credential without them; ' +
          'signature-counter and rpId checks will be unavailable for it.',
          error
        )

        const { public_key_alg: _alg, sign_count: _count, rp_id: _rpId, ...legacyData } = insertData
        const retry = await supabase
          .from('user_passkeys')
          .insert(legacyData)
          .select()

        if (retry.error) {
          console.error('Database error storing passkey:', retry.error)
          return { success: false, error: retry.error.message }
        }

        return { success: true, data: retry.data }
      }

      console.error('Database error storing passkey:', error)
      return { success: false, error: error.message }
    }

    console.log('Passkey stored successfully')
    return { success: true, data }
  } catch (error) {
    console.error('Exception storing passkey:', error)
    return { success: false, error: (error as Error).message }
  }
}

/**
 * True when PostgREST/Postgres rejected the statement because a column does not
 * exist (schema cache miss PGRST204, or undefined_column 42703).
 */
function isUnknownColumnError(error: { code?: string; message?: string }): boolean {
  if (error.code === 'PGRST204' || error.code === '42703') return true
  const message = error.message?.toLowerCase() ?? ''
  return message.includes('column') && (message.includes('does not exist') || message.includes('not find'))
}

/**
 * Records a successful assertion against a passkey.
 *
 * `signCount` is only written when the authenticator maintains a counter — see
 * `evaluateSignCounter` in utils/webauthn.ts, which returns null for
 * authenticators that always report 0.
 */
export async function recordPasskeyUse(
  supabase: SupabaseClient,
  passkeyId: string,
  signCount: number | null
) {
  const update: Record<string, unknown> = { last_used_at: Date.now() }
  if (signCount !== null) {
    update.sign_count = signCount
  }

  const { error } = await supabase
    .from('user_passkeys')
    .update(update)
    .eq('id', passkeyId)

  if (error) {
    // Non-fatal: the assertion itself was verified. Losing the counter update
    // only costs clone detection on the *next* assertion, so log and continue.
    console.error('Failed to record passkey use:', error)
    return { success: false, error: error.message }
  }

  return { success: true }
}

export async function getUserPasskeys(supabase: SupabaseClient, userId: string) {
  console.log('Getting passkeys for user:', userId)

  try {
    const { data, error } = await supabase
      .from('user_passkeys')
      .select('*')
      .eq('user_id', userId)
      .eq('active', true)

    if (error) {
      console.error('Database error getting passkeys:', error)
      return { success: false, error: error.message }
    }

    console.log(`Found ${data?.length || 0} existing passkeys`)
    return { success: true, passkeys: data }
  } catch (error) {
    console.error('Exception getting passkeys:', error)
    return { success: false, error: (error as Error).message }
  }
}

export async function findPasskeyByCredentialId(
  supabase: SupabaseClient,
  credentialId: string
) {
  console.log('Finding passkey by credential ID:', credentialId)

  try {
    const { data, error } = await supabase
      .from('user_passkeys')
      .select('*')
      .eq('credential_id', credentialId)
      .eq('active', true)
      .single()

    if (error || !data) {
      console.error('Passkey not found:', error)
      return { success: false, error: 'Passkey not found' }
    }

    console.log('Found passkey for user:', data.user_id)
    return { success: true, passkey: data }
  } catch (error) {
    console.error('Exception finding passkey:', error)
    return { success: false, error: (error as Error).message }
  }
}

/**
 * Find user by email - uses RPC function with SECURITY DEFINER for auth.users access
 * This is more scalable than using auth.admin.listUsers()
 */
export async function findUserByEmail(
  supabase: SupabaseClient,
  email: string
) {
  console.log('Finding user by email:', email)

  try {
    const { data, error } = await supabase
      .rpc('find_user_by_email', { p_email: email })

    if (error) {
      console.error('Error finding user:', error)
      return { success: false, error: error.message }
    }

    // RPC returns array, get first result
    const user = data && data.length > 0 ? data[0] : null

    if (!user) {
      console.log('User not found with email:', email)
      return { success: false, error: 'User not found' }
    }

    console.log('Found user:', user.id)
    return { success: true, user }
  } catch (error) {
    console.error('Exception finding user:', error)
    return { success: false, error: (error as Error).message }
  }
}

/**
 * Get user with email from public.users table
 *
 * This helper function consolidates the common pattern of fetching user email
 * from the public.users table. Used in authentication flows to retrieve user
 * information for JWT token generation.
 *
 * @param supabase - Supabase client instance
 * @param userId - User ID to look up
 * @returns Object with success status and user data (id + email) or error
 */
export async function getUserWithEmail(
  supabase: SupabaseClient,
  userId: string
) {
  console.log('Getting user with email for user ID:', userId)

  try {
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('email')
      .eq('id', userId)
      .single()

    if (userError || !userData?.email) {
      console.error('Failed to fetch user email:', userError)
      return {
        success: false,
        error: userError?.message || 'User email not found'
      }
    }

    console.log('Found user email:', userData.email)
    return {
      success: true,
      user: {
        id: userId,
        email: userData.email
      }
    }
  } catch (error) {
    console.error('Exception getting user with email:', error)
    return {
      success: false,
      error: (error as Error).message
    }
  }
}
