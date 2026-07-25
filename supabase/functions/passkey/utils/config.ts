/**
 * Configuration utilities for extracting dynamic values from environment
 */

/**
 * Extract RP ID from Supabase URL
 * Examples:
 * - https://api.risaboss.com -> api.risaboss.com
 * - http://127.0.0.1:54321 -> 127.0.0.1
 */
export function getRpId(): string {
  // Check for custom rpId first (for production with custom domains)
  const customRpId = Deno.env.get("PASSKEY_RP_ID")
  if (customRpId) {
    console.log(`🔧 Using custom PASSKEY_RP_ID: ${customRpId}`)
    return customRpId
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") || ""

  try {
    // Remove protocol
    const withoutProtocol = supabaseUrl
      .replace(/^https?:\/\//, '')

    // Remove port if present
    const host = withoutProtocol.split(':')[0]

    // Remove path if present
    let rpId = host.split('/')[0]

    // WebAuthn requires "localhost" for local development
    // Convert both "127.0.0.1" and "kong" (internal gateway) to "localhost"
    if (rpId === '127.0.0.1' || rpId === 'kong') {
      const original = rpId
      rpId = 'localhost'
      console.log(`🔧 Converted ${original} -> localhost for WebAuthn compatibility`)
    }

    console.log(`🔧 Extracted rpId: ${rpId} from SUPABASE_URL: ${supabaseUrl}`)
    return rpId
  } catch (e) {
    console.error('Failed to extract RP ID from URL:', supabaseUrl, 'Error:', e)
    return 'api.risaboss.com' // Fallback to production
  }
}

/**
 * Get display name for the relying party
 */
export function getRpName(): string {
  return 'BOSS'
}

/**
 * RP IDs that BOSS ceremonies may legitimately be bound to.
 *
 * Why a set and not just `getRpId()`: inside the edge runtime `SUPABASE_URL`
 * points at the internal gateway, not at the domain the browser actually loads
 * (see the note on `generateRegistrationChallenge`). The mobile pages take
 * `rpId` as a query parameter and default it to `api.risaboss.com`, so the
 * effective RP ID of a ceremony is not always derivable from the environment.
 *
 * Every entry here is a BOSS-owned host — the point of the check is to reject
 * assertions minted for a *foreign* relying party, which no entry below is.
 * Deployments can add hosts with `PASSKEY_RP_ID` / `PASSKEY_RP_ID_ALIASES`
 * (comma-separated) without a code change.
 */
const BOSS_RP_IDS: readonly string[] = ['api.risaboss.com', 'risaboss.com', 'localhost']

export function getAllowedRpIds(): string[] {
  const allowed = new Set<string>()

  const customRpId = Deno.env.get("PASSKEY_RP_ID")
  if (customRpId) {
    allowed.add(customRpId.trim())
  }

  const aliases = Deno.env.get("PASSKEY_RP_ID_ALIASES")
  if (aliases) {
    for (const alias of aliases.split(',')) {
      const trimmed = alias.trim()
      if (trimmed) allowed.add(trimmed)
    }
  }

  const derived = getRpId()
  if (derived) {
    allowed.add(derived)
  }

  for (const rpId of BOSS_RP_IDS) {
    allowed.add(rpId)
  }

  return Array.from(allowed)
}
