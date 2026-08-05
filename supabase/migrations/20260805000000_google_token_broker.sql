-- Google Sheets token broker (service-account based).
--
-- Lets the google-token Edge Function hand finance.read users a short-lived Google access token, so
-- the Finances plugin can read the curated feed sheets (payroll/invoices/attribution) without anyone
-- pasting an hourly-expiring OAuth token. Mirrors the QuickBooks broker (qbo_token_state), but the
-- credential is a Google **service account** — org-owned, no user consent, no refresh-token rotation:
-- the function mints an access token by signing a JWT with the service-account key (jwt-bearer grant).
--
-- Everything is stored in this table (secrets encrypted at rest via public.encrypt_text), so it can be
-- provisioned entirely through the SQL Editor with no function-secret / DB-password privileges:
--   * client_email                 -> plaintext (the service-account address; not sensitive).
--   * private_key                  -> encrypted (the service-account private key — the secret).
--   * access_token                 -> encrypted (short-lived; cached to cut token-endpoint calls).
--
-- Single-flight refresh (google_claim_refresh, a compare-and-swap lock) isn't strictly required for a
-- service account (concurrent mints are harmless), but it's kept to avoid a thundering herd of token
-- mints and to match the qbo broker's shape.

-- ---------------------------------------------------------------------------
-- State (single row)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS "public"."google_token_state" (
    "id" boolean PRIMARY KEY DEFAULT true,
    "client_email" "text" NOT NULL,
    "private_key_enc" "text" NOT NULL,
    "access_token_enc" "text",
    "access_token_expires_at" timestamptz,
    "refresh_lock_until" timestamptz,
    "updated_at" timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT "google_token_state_singleton" CHECK ("id")
);

-- Only the service role (which bypasses RLS) may touch this table. No anon/authenticated access.
ALTER TABLE "public"."google_token_state" ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON "public"."google_token_state" FROM "anon", "authenticated";

-- ---------------------------------------------------------------------------
-- RPCs (SECURITY DEFINER, owned by postgres, service_role only) — same least-
-- privilege pattern as the qbo_* broker RPCs.
-- ---------------------------------------------------------------------------

-- Fast path: current access token (decrypted) + expiry.
CREATE OR REPLACE FUNCTION "public"."google_read_state"()
RETURNS TABLE("access_token" "text", "expires_at" timestamptz)
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    SELECT CASE WHEN s."access_token_enc" IS NULL THEN NULL
                ELSE public.decrypt_text(s."access_token_enc") END,
           s."access_token_expires_at"
    FROM public."google_token_state" s
    WHERE s."id";
$$;

-- Compare-and-swap: claim the mint iff the token is stale/absent AND not already being minted.
-- Returns the decrypted service-account credential to the single winner; 0 rows to everyone else.
CREATE OR REPLACE FUNCTION "public"."google_claim_refresh"("p_margin_seconds" integer, "p_lock_seconds" integer)
RETURNS TABLE("client_email" "text", "private_key" "text")
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    UPDATE public."google_token_state" s
       SET "refresh_lock_until" = now() + make_interval(secs => p_lock_seconds)
     WHERE s."id"
       AND (s."access_token_expires_at" IS NULL
            OR s."access_token_expires_at" <= now() + make_interval(secs => p_margin_seconds))
       AND (s."refresh_lock_until" IS NULL OR s."refresh_lock_until" <= now())
    RETURNING s."client_email",
              public.decrypt_text(s."private_key_enc");
$$;

-- Persist a freshly-minted access token and release the lock.
CREATE OR REPLACE FUNCTION "public"."google_store_refreshed"("p_access_token" "text", "p_expires_at" timestamptz)
RETURNS "void"
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    UPDATE public."google_token_state" s
       SET "access_token_enc" = public.encrypt_text(p_access_token),
           "access_token_expires_at" = p_expires_at,
           "refresh_lock_until" = NULL,
           "updated_at" = now()
     WHERE s."id";
$$;

ALTER FUNCTION "public"."google_read_state"() OWNER TO "postgres";
ALTER FUNCTION "public"."google_claim_refresh"(integer, integer) OWNER TO "postgres";
ALTER FUNCTION "public"."google_store_refreshed"("text", timestamptz) OWNER TO "postgres";

REVOKE ALL ON FUNCTION "public"."google_read_state"() FROM PUBLIC, "anon", "authenticated";
REVOKE ALL ON FUNCTION "public"."google_claim_refresh"(integer, integer) FROM PUBLIC, "anon", "authenticated";
REVOKE ALL ON FUNCTION "public"."google_store_refreshed"("text", timestamptz) FROM PUBLIC, "anon", "authenticated";

GRANT EXECUTE ON FUNCTION "public"."google_read_state"() TO "service_role";
GRANT EXECUTE ON FUNCTION "public"."google_claim_refresh"(integer, integer) TO "service_role";
GRANT EXECUTE ON FUNCTION "public"."google_store_refreshed"("text", timestamptz) TO "service_role";

-- ---------------------------------------------------------------------------
-- One-time seed (run manually in the SQL Editor, NOT part of this migration — it
-- carries the service-account private key). From the service-account JSON key file,
-- use client_email and private_key. Paste the private key as a dollar-quoted string
-- so its newlines are preserved verbatim:
--
--   INSERT INTO public.google_token_state (id, client_email, private_key_enc)
--   VALUES (
--     true,
--     'finance-sheets@<project>.iam.gserviceaccount.com',
--     public.encrypt_text($pk$-----BEGIN PRIVATE KEY-----
--   ...full PEM body, newlines intact...
--   -----END PRIVATE KEY-----
--   $pk$)
--   )
--   ON CONFLICT (id) DO UPDATE
--     SET client_email = EXCLUDED.client_email,
--         private_key_enc = EXCLUDED.private_key_enc,
--         access_token_enc = NULL,
--         access_token_expires_at = NULL,
--         refresh_lock_until = NULL,
--         updated_at = now();
--
-- Then share each feed sheet (payroll_feed / invoice_feed / attribution_map) with the
-- service-account email as a Viewer. To rotate, re-run with a fresh key.
-- ---------------------------------------------------------------------------
