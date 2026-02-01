import type { SupabaseClient } from "@supabase/supabase-js"

// Simple JWT payload decoder (no verification - Supabase already verified)
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(payload)
  } catch {
    return null
  }
}

/**
 * Extract and verify JWT token from Authorization header
 * Returns the user ID if valid, null if invalid or not present
 */
export async function getUserFromToken(
  supabase: SupabaseClient,
  authHeader: string | undefined
): Promise<{ userId: string, email: string, isAdmin: boolean } | null> {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null
  }

  const token = authHeader.substring(7)

  try {
    const { data: { user }, error } = await supabase.auth.getUser(token)

    if (error || !user) {
      console.error('Error verifying token:', error)
      return null
    }

    // Decode JWT to get is_admin claim
    const payload = decodeJwtPayload(token)
    const isAdmin = payload?.is_admin === true

    return {
      userId: user.id,
      email: user.email || '',
      isAdmin
    }
  } catch (e) {
    console.error('Exception verifying token:', e)
    return null
  }
}

/**
 * Get user's display name from public.users table
 */
export async function getUserDisplayName(
  supabase: SupabaseClient,
  userId: string
): Promise<string> {
  const { data, error } = await supabase
    .from('users')
    .select('email')
    .eq('id', userId)
    .single()

  if (error || !data) {
    console.error('Error getting user display name:', error)
    return 'Unknown'
  }

  // Use email username as display name
  return data.email?.split('@')[0] || 'Unknown'
}
