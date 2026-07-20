// qbo-token — the QuickBooks token broker.
//
// A finance.read user's BOSS plugin calls this with their bearer token; we verify it, confirm the
// finance.read permission from the JWT claims (same pattern as plugin-store), then return a
// short-lived QuickBooks access token. The rotating refresh token never leaves the server.
//
// Refresh is single-flight: Intuit invalidates the old refresh token the instant a new one issues,
// so exactly one caller may refresh at a time (public.qbo_claim_refresh is a compare-and-swap lock).
//
// verify_jwt = false in config.toml — this function does its own verification (codebase convention).

import { Hono } from "hono"
import { cors } from "hono/cors"
import { createClient } from "@supabase/supabase-js"

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? ""
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
const qboClientId = Deno.env.get("QBO_CLIENT_ID") ?? ""
const qboClientSecret = Deno.env.get("QBO_CLIENT_SECRET") ?? ""

// Intuit's OAuth token endpoint is shared across sandbox and production.
const TOKEN_ENDPOINT = "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer"
const REFRESH_MARGIN_SECONDS = 300 // refresh when <5 min of access-token life remains
const LOCK_SECONDS = 30            // how long a claimed refresh holds the CAS lock
const RETRY_WAIT_MS = 1500         // wait for the winner's refresh before re-reading

const supabase = createClient(supabaseUrl, serviceKey)

const app = new Hono().basePath("/qbo-token")

app.use("*", cors({
  origin: ["boss://plugins", "http://localhost:3000", "https://risaboss.com"],
  allowMethods: ["GET", "POST", "OPTIONS"],
  allowHeaders: ["Content-Type", "Authorization", "apikey", "X-API-Key"],
  maxAge: 600,
}))

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const part = token.split(".")[1]
    const json = atob(part.replace(/-/g, "+").replace(/_/g, "/"))
    return JSON.parse(json)
  } catch {
    return null
  }
}

/** Verify the caller and require finance.read (admins pass via is_admin, mirroring public.authorize). */
async function authorize(authHeader: string | undefined): Promise<{ ok: true } | { ok: false; status: 401 | 403; error: string }> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return { ok: false, status: 401, error: "Authentication required" }
  }
  const token = authHeader.slice(7)
  const { data: { user }, error } = await supabase.auth.getUser(token)
  if (error || !user) return { ok: false, status: 401, error: "Authentication required" }

  const payload = decodeJwtPayload(token)
  const isAdmin = payload?.is_admin === true
  const perms = Array.isArray(payload?.user_permissions) ? (payload!.user_permissions as string[]) : []
  if (!isAdmin && !perms.includes("finance.read")) {
    return { ok: false, status: 403, error: "finance.read permission required" }
  }
  return { ok: true }
}

interface TokenResult { realm_id: string; access_token: string; expires_at: string }

async function getFreshToken(): Promise<TokenResult> {
  // Fast path — cached access token still valid.
  const { data: state, error: readErr } = await supabase.rpc("qbo_read_state").maybeSingle()
  if (readErr) throw new Error(`state read failed: ${readErr.message}`)
  if (!state) throw new Error("QuickBooks is not connected (no token state seeded)")

  const now = Date.now()
  const stillValid = state.access_token &&
    state.expires_at &&
    new Date(state.expires_at).getTime() > now + REFRESH_MARGIN_SECONDS * 1000
  if (stillValid) {
    return { realm_id: state.realm_id, access_token: state.access_token, expires_at: state.expires_at }
  }

  // Try to claim the single-flight refresh.
  const { data: claim, error: claimErr } = await supabase
    .rpc("qbo_claim_refresh", { p_margin_seconds: REFRESH_MARGIN_SECONDS, p_lock_seconds: LOCK_SECONDS })
    .maybeSingle()
  if (claimErr) throw new Error(`claim failed: ${claimErr.message}`)

  if (!claim) {
    // Another invocation is refreshing (or just did). Wait briefly, then re-read.
    await new Promise((r) => setTimeout(r, RETRY_WAIT_MS))
    const { data: after } = await supabase.rpc("qbo_read_state").maybeSingle()
    if (after?.access_token && after.expires_at && new Date(after.expires_at).getTime() > now) {
      return { realm_id: after.realm_id, access_token: after.access_token, expires_at: after.expires_at }
    }
    throw new Error("token refresh in progress, please retry")
  }

  // We won the lock — exchange the refresh token with Intuit.
  const basic = btoa(`${qboClientId}:${qboClientSecret}`)
  const res = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: claim.refresh_token }),
  })
  if (!res.ok) {
    // Leave the lock to expire naturally so a later call can retry; surface Intuit's reason.
    const body = await res.text()
    throw new Error(`Intuit refresh failed (${res.status}): ${body.slice(0, 200)}`)
  }
  const tok = await res.json()
  if (!tok.access_token) throw new Error("Intuit refresh returned no access_token")

  const expiresAt = new Date(now + (tok.expires_in ?? 3600) * 1000).toISOString()
  // Intuit rotates the refresh token; keep the new one if returned, else the one we used.
  const newRefresh = tok.refresh_token ?? claim.refresh_token
  const { error: storeErr } = await supabase.rpc("qbo_store_refreshed", {
    p_access_token: tok.access_token,
    p_refresh_token: newRefresh,
    p_expires_at: expiresAt,
  })
  if (storeErr) throw new Error(`failed to persist refreshed token: ${storeErr.message}`)

  return { realm_id: claim.realm_id, access_token: tok.access_token, expires_at: expiresAt }
}

async function handle(c: { req: { header: (n: string) => string | undefined }; json: (b: unknown, s?: number) => Response }) {
  const auth = await authorize(c.req.header("Authorization"))
  if (!auth.ok) return c.json({ error: auth.error }, auth.status)
  try {
    const token = await getFreshToken()
    return c.json(token)
  } catch (e) {
    console.error("qbo-token error:", (e as Error).message)
    return c.json({ error: (e as Error).message }, 502)
  }
}

app.get("/", handle)
app.post("/", handle)

app.onError((err, c) => {
  console.error("qbo-token global error:", err)
  return c.json({ error: err.message }, 500)
})

Deno.serve(app.fetch)
