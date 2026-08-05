// google-token — the Google Sheets token broker (service-account based).
//
// A finance.read user's BOSS plugin calls this with their bearer token; we verify it, confirm the
// finance.read permission from the JWT claims (same pattern as qbo-token / plugin-store), then return
// a short-lived Google access token. The plugin uses it to read the curated finance feed sheets.
//
// The credential is a Google **service account** — org-owned, no user OAuth consent, no refresh-token
// rotation. We mint an access token by signing a JWT with the service-account key (RFC 7523 jwt-bearer
// grant). The private key never leaves the server. The minted token is cached to expiry; a single-flight
// lock (public.google_claim_refresh) avoids a thundering herd of mints.
//
// verify_jwt = false in config.toml — this function does its own verification (codebase convention).

import { Hono } from "hono"
import { cors } from "hono/cors"
import { createClient } from "@supabase/supabase-js"

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? ""
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""

const GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
// Read-only Sheets access is all the feeds need. Narrowest scope that works.
const SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly"
const REFRESH_MARGIN_SECONDS = 300 // mint a new token when <5 min of life remains
const LOCK_SECONDS = 30
const RETRY_WAIT_MS = 1500

const supabase = createClient(supabaseUrl, serviceKey)

const app = new Hono().basePath("/google-token")

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

// --- service-account JWT signing (RS256) -------------------------------------------------------

function base64url(bytes: Uint8Array): string {
  let bin = ""
  for (const b of bytes) bin += String.fromCharCode(b)
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

function pemToPkcs8(pem: string): ArrayBuffer {
  // Tolerate keys pasted with literal "\n" as well as real newlines.
  const body = pem
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "")
  const bin = atob(body)
  const buf = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i)
  return buf.buffer
}

async function signServiceAccountJwt(clientEmail: string, privateKeyPem: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header = { alg: "RS256", typ: "JWT" }
  const claims = {
    iss: clientEmail,
    scope: SCOPE,
    aud: GOOGLE_TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  }
  const enc = (o: unknown) => base64url(new TextEncoder().encode(JSON.stringify(o)))
  const unsigned = `${enc(header)}.${enc(claims)}`
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  )
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)))
  return `${unsigned}.${base64url(sig)}`
}

// --- token flow --------------------------------------------------------------------------------

interface TokenResult { access_token: string; expires_at: string }

async function getFreshToken(): Promise<TokenResult> {
  const { data: state, error: readErr } = await supabase.rpc("google_read_state").maybeSingle()
  if (readErr) throw new Error(`state read failed: ${readErr.message}`)
  if (!state) throw new Error("Google Sheets is not connected (no service account seeded)")

  const now = Date.now()
  const stillValid = state.access_token &&
    state.expires_at &&
    new Date(state.expires_at).getTime() > now + REFRESH_MARGIN_SECONDS * 1000
  if (stillValid) {
    return { access_token: state.access_token, expires_at: state.expires_at }
  }

  const { data: claim, error: claimErr } = await supabase
    .rpc("google_claim_refresh", { p_margin_seconds: REFRESH_MARGIN_SECONDS, p_lock_seconds: LOCK_SECONDS })
    .maybeSingle()
  if (claimErr) throw new Error(`claim failed: ${claimErr.message}`)

  if (!claim) {
    // Another invocation is minting. Wait briefly, then re-read.
    await new Promise((r) => setTimeout(r, RETRY_WAIT_MS))
    const { data: after } = await supabase.rpc("google_read_state").maybeSingle()
    if (after?.access_token && after.expires_at && new Date(after.expires_at).getTime() > now) {
      return { access_token: after.access_token, expires_at: after.expires_at }
    }
    throw new Error("token mint in progress, please retry")
  }

  // We won the lock — mint via the jwt-bearer grant.
  const assertion = await signServiceAccountJwt(claim.client_email, claim.private_key)
  const res = await fetch(GOOGLE_TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  })
  if (!res.ok) {
    // Leave the lock to expire so a later call can retry; surface Google's reason.
    const body = await res.text()
    throw new Error(`Google token mint failed (${res.status}): ${body.slice(0, 200)}`)
  }
  const tok = await res.json()
  if (!tok.access_token) throw new Error("Google token mint returned no access_token")

  const expiresAt = new Date(now + (tok.expires_in ?? 3600) * 1000).toISOString()
  const { error: storeErr } = await supabase.rpc("google_store_refreshed", {
    p_access_token: tok.access_token,
    p_expires_at: expiresAt,
  })
  if (storeErr) throw new Error(`failed to persist minted token: ${storeErr.message}`)

  return { access_token: tok.access_token, expires_at: expiresAt }
}

async function handle(c: { req: { header: (n: string) => string | undefined }; json: (b: unknown, s?: number) => Response }) {
  const auth = await authorize(c.req.header("Authorization"))
  if (!auth.ok) return c.json({ error: auth.error }, auth.status)
  try {
    const token = await getFreshToken()
    return c.json(token)
  } catch (e) {
    console.error("google-token error:", (e as Error).message)
    return c.json({ error: (e as Error).message }, 502)
  }
}

app.get("/", handle)
app.post("/", handle)

app.onError((err, c) => {
  console.error("google-token global error:", err)
  return c.json({ error: err.message }, 500)
})

Deno.serve(app.fetch)
