-- QuickBooks token broker.
--
-- Stores the shared QuickBooks OAuth credential server-side and lets the qbo-token Edge Function
-- hand finance.read users a short-lived access token — so no client ever holds the rotating,
-- single-use refresh token (the thing that kept bricking the client-side shared-secret approach).
--
-- Split of secrets:
--   * Intuit app client_id / client_secret  -> Edge Function env secrets (supabase secrets set), never in the DB.
--   * realm_id                               -> this table (a company id, not sensitive).
--   * refresh_token / access_token           -> this table, encrypted at rest via public.encrypt_text
--                                               (they rotate constantly, so they can't live in Vault's
--                                               static-secret model).
--
-- Single-flight refresh: Intuit invalidates the old refresh token the instant a new one is issued,
-- so two concurrent refreshes would brick the credential. qbo_claim_refresh is a compare-and-swap
-- mutex — exactly one caller wins the right to refresh at a time; everyone else reads the cached
-- token or briefly waits.

-- ---------------------------------------------------------------------------
-- State (single row)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS "public"."qbo_token_state" (
    "id" boolean PRIMARY KEY DEFAULT true,
    "realm_id" "text" NOT NULL,
    "refresh_token_enc" "text" NOT NULL,
    "access_token_enc" "text",
    "access_token_expires_at" timestamptz,
    "refresh_lock_until" timestamptz,
    "updated_at" timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT "qbo_token_state_singleton" CHECK ("id")
);

-- Only the service role (which bypasses RLS) may touch this table. No anon/authenticated access.
ALTER TABLE "public"."qbo_token_state" ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON "public"."qbo_token_state" FROM "anon", "authenticated";

-- ---------------------------------------------------------------------------
-- RPCs (SECURITY DEFINER, owned by postgres, service_role only) — mirror the
-- least-privilege pattern used by get_encryption_key / plugin_defined_permissions.
-- ---------------------------------------------------------------------------

-- Fast path: current access token (decrypted) + expiry.
CREATE OR REPLACE FUNCTION "public"."qbo_read_state"()
RETURNS TABLE("realm_id" "text", "access_token" "text", "expires_at" timestamptz)
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    SELECT s."realm_id",
           CASE WHEN s."access_token_enc" IS NULL THEN NULL
                ELSE public.decrypt_text(s."access_token_enc") END,
           s."access_token_expires_at"
    FROM public."qbo_token_state" s
    WHERE s."id";
$$;

-- Compare-and-swap: claim the refresh iff the token is stale/absent AND not already being
-- refreshed. Returns the decrypted refresh token to the single winner; 0 rows to everyone else.
CREATE OR REPLACE FUNCTION "public"."qbo_claim_refresh"("p_margin_seconds" integer, "p_lock_seconds" integer)
RETURNS TABLE("realm_id" "text", "refresh_token" "text")
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    UPDATE public."qbo_token_state" s
       SET "refresh_lock_until" = now() + make_interval(secs => p_lock_seconds)
     WHERE s."id"
       AND (s."access_token_expires_at" IS NULL
            OR s."access_token_expires_at" <= now() + make_interval(secs => p_margin_seconds))
       AND (s."refresh_lock_until" IS NULL OR s."refresh_lock_until" <= now())
    RETURNING s."realm_id", public.decrypt_text(s."refresh_token_enc");
$$;

-- Persist a rotated credential and release the refresh lock.
CREATE OR REPLACE FUNCTION "public"."qbo_store_refreshed"("p_access_token" "text", "p_refresh_token" "text", "p_expires_at" timestamptz)
RETURNS "void"
    LANGUAGE "sql" SECURITY DEFINER SET "search_path" TO ''
AS $$
    UPDATE public."qbo_token_state" s
       SET "access_token_enc" = public.encrypt_text(p_access_token),
           "refresh_token_enc" = public.encrypt_text(p_refresh_token),
           "access_token_expires_at" = p_expires_at,
           "refresh_lock_until" = NULL,
           "updated_at" = now()
     WHERE s."id";
$$;

ALTER FUNCTION "public"."qbo_read_state"() OWNER TO "postgres";
ALTER FUNCTION "public"."qbo_claim_refresh"(integer, integer) OWNER TO "postgres";
ALTER FUNCTION "public"."qbo_store_refreshed"("text", "text", timestamptz) OWNER TO "postgres";

REVOKE ALL ON FUNCTION "public"."qbo_read_state"() FROM PUBLIC, "anon", "authenticated";
REVOKE ALL ON FUNCTION "public"."qbo_claim_refresh"(integer, integer) FROM PUBLIC, "anon", "authenticated";
REVOKE ALL ON FUNCTION "public"."qbo_store_refreshed"("text", "text", timestamptz) FROM PUBLIC, "anon", "authenticated";

GRANT EXECUTE ON FUNCTION "public"."qbo_read_state"() TO "service_role";
GRANT EXECUTE ON FUNCTION "public"."qbo_claim_refresh"(integer, integer) TO "service_role";
GRANT EXECUTE ON FUNCTION "public"."qbo_store_refreshed"("text", "text", timestamptz) TO "service_role";

-- ---------------------------------------------------------------------------
-- One-time seed (run manually, NOT part of this migration — it carries the live
-- refresh token). After a fresh Intuit authorization:
--
--   INSERT INTO public.qbo_token_state (id, realm_id, refresh_token_enc)
--   VALUES (true, '<REALM_ID>', public.encrypt_text('<REFRESH_TOKEN>'))
--   ON CONFLICT (id) DO UPDATE
--     SET realm_id = EXCLUDED.realm_id,
--         refresh_token_enc = EXCLUDED.refresh_token_enc,
--         access_token_enc = NULL,
--         access_token_expires_at = NULL,
--         refresh_lock_until = NULL,
--         updated_at = now();
--
-- To rotate/reconnect later, re-run the same statement with a fresh grant.
-- ---------------------------------------------------------------------------
