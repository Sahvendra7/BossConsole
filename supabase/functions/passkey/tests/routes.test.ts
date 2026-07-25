/**
 * Route-level tests
 *
 * The services own ceremony verification; these cover what only the routes
 * decide — the HTTP status a bad request comes back as. Both complete routes
 * declare 400 and 403, so a payload the server cannot decode must not surface as
 * a 500 carrying a raw exception message.
 */

import { assertEquals } from "jsr:@std/assert"
import { OpenAPIHono } from "@hono/zod-openapi"
import type { PasskeyContext } from "../types/context.ts"
import auth from "../routes/auth.ts"
import register from "../routes/register.ts"
import { createMockSupabaseClient, type MockSupabaseClient } from "./helpers/mocks.ts"
import { TEST_ORIGIN } from "./helpers/webauthn.ts"

function buildApp(mockClient: MockSupabaseClient) {
  const app = new OpenAPIHono<{ Variables: PasskeyContext }>()
  app.use("*", async (ctx, next) => {
    // deno-lint-ignore no-explicit-any
    ctx.set("supabase", mockClient as any)
    await next()
  })
  app.route("/auth", auth)
  app.route("/register", register)
  return app
}

function postJson(app: ReturnType<typeof buildApp>, path: string, body: unknown) {
  return app.request(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  })
}

const encode = (value: unknown) => btoa(JSON.stringify(value))

Deno.test("POST /auth/complete - undecodable clientDataJSON is a 400, not a 500", async () => {
  const app = buildApp(createMockSupabaseClient())

  const response = await postJson(app, '/auth/complete', {
    challenge: 'some-challenge',
    credential: {
      id: 'cred',
      rawId: 'cred',
      type: 'public-key',
      response: {
        clientDataJSON: 'not!!!valid!!!base64!!',
        authenticatorData: 'dGVzdA',
        signature: 'dGVzdA'
      }
    }
  })

  assertEquals(response.status, 400)
  assertEquals((await response.json()).error, 'Invalid clientDataJSON')
})

Deno.test("POST /register/complete - undecodable clientDataJSON is a 400, not a 500", async () => {
  const app = buildApp(createMockSupabaseClient())

  const response = await postJson(app, '/register/complete', {
    userId: 'user-1',
    challenge: 'some-challenge',
    credential: {
      id: 'cred',
      rawId: 'cred',
      type: 'public-key',
      response: {
        clientDataJSON: 'not!!!valid!!!base64!!',
        attestationObject: 'dGVzdA'
      }
    }
  })

  assertEquals(response.status, 400)
  assertEquals((await response.json()).error, 'Invalid clientDataJSON')
})

Deno.test("POST /auth/complete - a disallowed origin is a 403", async () => {
  const app = buildApp(createMockSupabaseClient())

  const response = await postJson(app, '/auth/complete', {
    challenge: 'some-challenge',
    credential: {
      id: 'cred',
      rawId: 'cred',
      type: 'public-key',
      response: {
        clientDataJSON: encode({
          type: 'webauthn.get',
          challenge: 'some-challenge',
          origin: 'https://evil.example.com'
        }),
        authenticatorData: 'dGVzdA',
        signature: 'dGVzdA'
      }
    }
  })

  assertEquals(response.status, 403)
  assertEquals((await response.json()).error, 'Invalid origin')
})

Deno.test("POST /auth/complete - an allowed origin reaches the service", async () => {
  const mockClient = createMockSupabaseClient()
  const app = buildApp(mockClient)

  // No challenge row queued, so the service rejects — the point is that the
  // route let it through rather than short-circuiting on origin.
  const response = await postJson(app, '/auth/complete', {
    challenge: 'some-challenge',
    credential: {
      id: 'cred',
      rawId: 'cred',
      type: 'public-key',
      response: {
        clientDataJSON: encode({
          type: 'webauthn.get',
          challenge: 'some-challenge',
          origin: TEST_ORIGIN
        }),
        authenticatorData: 'dGVzdA',
        signature: 'dGVzdA'
      }
    }
  })

  assertEquals(response.status, 400)
  assertEquals(
    mockClient.getQueryHistory().some(query => query.table === 'passkey_challenges'),
    true,
    'The service should have been reached'
  )
})
