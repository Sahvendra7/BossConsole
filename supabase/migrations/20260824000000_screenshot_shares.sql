-- ============================================================================
-- BOSS Database Schema: Screenshot sharing between organisation members
-- ============================================================================
-- File: 20260824000000_screenshot_shares.sql
-- Description:
--   Backs the screenshot-share plugin. A plugin only gets read-only
--   select()/rpc() access to Supabase via SupabaseDataProvider (see
--   AGENTS.md "Nothing on PluginContext exposes the Supabase access token") --
--   there is no Storage upload path and no realtime channel exposed to
--   plugins. So delivery is: the annotated PNG travels as base64 through a
--   SECURITY DEFINER RPC into a bytea column (same shape as secrets.password_encrypted,
--   minus encryption -- an image is not a credential), and the recipient's
--   plugin instance polls list_received_screenshots() on a timer.
--
--   Recipient scope is deliberately narrow: a share is only allowed between
--   two users who are both ACTIVE members of at least one common organisation
--   (public.is_org_member mirrors the check secret_shares uses). There is no
--   "any BOSS user" path -- that would need a new global user-search surface,
--   which does not exist and is out of scope here.
--
-- Dependencies:
--   - 20260801000000_organisation_tables.sql (organisation_members, is_org_member)
--   - 20260801010000_organisation_permissions_and_guards.sql (is_org_member)
--
-- Tables: 1 (screenshot_shares)
-- Functions: 4 (share_screenshot, list_received_screenshots,
--               list_sent_screenshots, get_screenshot_image,
--               list_shareable_recipients)
-- ============================================================================


-- ============================================================================
-- SECTION 1: screenshot_shares
-- ============================================================================
-- The image lives in the row (bytea), not in Storage -- plugins have no
-- Storage upload primitive today. p_image_base64 in share_screenshot() is
-- capped at ~8MB decoded to keep a single annotated screenshot from being
-- able to bloat this table without bound.

CREATE TABLE IF NOT EXISTS "public"."screenshot_shares" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "sender_id" "uuid" NOT NULL,
    "recipient_id" "uuid" NOT NULL,
    "org_id" "uuid" NOT NULL,
    "image_data" "bytea" NOT NULL,
    "mime_type" "text" DEFAULT 'image/png'::"text" NOT NULL,
    "width" integer,
    "height" integer,
    "note" "text",
    "password_hash" "text",
    "failed_password_attempts" integer DEFAULT 0 NOT NULL,
    "read_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "expires_at" timestamp with time zone DEFAULT ("now"() + interval '14 days') NOT NULL,
    CONSTRAINT "screenshot_shares_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "screenshot_shares_not_self" CHECK ("sender_id" <> "recipient_id"),
    CONSTRAINT "screenshot_shares_image_size" CHECK ("octet_length"("image_data") <= 8388608),
    CONSTRAINT "screenshot_shares_note_length" CHECK ("note" IS NULL OR "char_length"("note") <= 500),
    CONSTRAINT "screenshot_shares_failed_attempts_check" CHECK ("failed_password_attempts" >= 0),
    CONSTRAINT "screenshot_shares_sender_fkey" FOREIGN KEY ("sender_id")
        REFERENCES "auth"."users"("id") ON DELETE CASCADE,
    CONSTRAINT "screenshot_shares_recipient_fkey" FOREIGN KEY ("recipient_id")
        REFERENCES "auth"."users"("id") ON DELETE CASCADE,
    CONSTRAINT "screenshot_shares_org_fkey" FOREIGN KEY ("org_id")
        REFERENCES "public"."organisations"("id") ON DELETE CASCADE
);

ALTER TABLE "public"."screenshot_shares" OWNER TO "postgres";

COMMENT ON TABLE "public"."screenshot_shares" IS 'One shared, annotated screenshot. image_data is the flattened PNG, stored inline because plugins have no Storage upload path. org_id records which shared organisation made the share eligible -- it is provenance, not a live authorization check (removal from the org does not retract an already-sent share, matching how a sent email is not unsent).';

COMMENT ON COLUMN "public"."screenshot_shares"."expires_at" IS 'Default 14 days. Old shares are opportunistically deleted by trigger_cleanup_expired_screenshot_shares, the same probabilistic-on-insert pattern as organisation_handoff_tokens -- this table only ever holds short-lived rows, so a scheduled job is unnecessary.';

COMMENT ON COLUMN "public"."screenshot_shares"."read_at" IS 'Set once, the first time the recipient calls get_screenshot_image() for this row. Drives the unread badge in the plugin''s inbox panel.';

COMMENT ON COLUMN "public"."screenshot_shares"."password_hash" IS 'Optional bcrypt hash (pgcrypto crypt()/gen_salt(''bf'')) of a passphrase the sender set. Null means the share opens with no password prompt. One-way -- get_screenshot_image() verifies against it, nothing ever reads it back.';

COMMENT ON COLUMN "public"."screenshot_shares"."failed_password_attempts" IS 'Incremented by get_screenshot_image() on every wrong password. At 5, the share is locked out for its remaining lifetime (no further hash comparison is even attempted) -- there is no cooldown/reset, matching this table''s otherwise simple, short-lived-row design.';

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_recipient"
    ON "public"."screenshot_shares" ("recipient_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_sender"
    ON "public"."screenshot_shares" ("sender_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_expires"
    ON "public"."screenshot_shares" ("expires_at");

ALTER TABLE "public"."screenshot_shares" ENABLE ROW LEVEL SECURITY;

-- No INSERT/UPDATE/DELETE policy for authenticated: all writes go through the
-- SECURITY DEFINER RPCs below, matching the organisation/secret_shares convention.
CREATE POLICY "screenshot_shares_select" ON "public"."screenshot_shares"
    FOR SELECT USING ("sender_id" = "auth"."uid"() OR "recipient_id" = "auth"."uid"());

-- Table itself stays locked down (no direct SELECT grant): the RPCs below are
-- the only path, which lets get_screenshot_image() mark read_at as a side
-- effect and list_* project away image_data so a poll never pulls image
-- bytes it doesn't need. A raw client-side postgrest select would bypass both.
REVOKE ALL ON TABLE "public"."screenshot_shares" FROM "anon", "authenticated";
GRANT ALL ON TABLE "public"."screenshot_shares" TO "service_role";


-- Probabilistic cleanup on insert, same shape as
-- trigger_cleanup_expired_handoff_tokens (20260801000000).
CREATE OR REPLACE FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    IF "random"() < 0.1 THEN
        DELETE FROM public.screenshot_shares
        WHERE expires_at < now();
    END IF;
    RETURN NEW;
END;
$$;

ALTER FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"() OWNER TO "postgres";

DROP TRIGGER IF EXISTS "trigger_cleanup_expired_screenshot_shares_on_insert"
    ON "public"."screenshot_shares";
CREATE TRIGGER "trigger_cleanup_expired_screenshot_shares_on_insert"
    AFTER INSERT ON "public"."screenshot_shares"
    FOR EACH ROW EXECUTE FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"();


-- ============================================================================
-- SECTION 2: list_shareable_recipients -- who the caller may send to
-- ============================================================================
-- Distinct active co-members across every organisation the caller belongs to,
-- excluding the caller. One call instead of the plugin orchestrating
-- get_my_organisations + list_organisation_members per org itself.

CREATE OR REPLACE FUNCTION "public"."list_shareable_recipients"(
    "p_query" "text" DEFAULT NULL::"text",
    "p_limit" integer DEFAULT 50
) RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_rows JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_rows
      FROM (
        SELECT DISTINCT ON (u.id)
               u.id AS user_id, u.email,
               COALESCE(
                   u.raw_user_meta_data ->> 'full_name',
                   u.raw_user_meta_data ->> 'name',
                   split_part(u.email, '@', 1)
               ) AS display_name,
               om.org_id, o.name AS org_name
        FROM public.organisation_members om
        JOIN public.organisation_members mine
          ON mine.org_id = om.org_id
         AND mine.user_id = v_actor
         AND mine.status = 'active'
        JOIN auth.users u ON u.id = om.user_id
        JOIN public.organisations o ON o.id = om.org_id
        WHERE om.status = 'active'
          AND om.user_id <> v_actor
          AND (p_query IS NULL OR u.email ILIKE '%' || p_query || '%')
        ORDER BY u.id, o.name
        LIMIT GREATEST(LEAST(COALESCE(p_limit, 50), 200), 1)
      ) t;

    RETURN jsonb_build_object('success', true, 'data', v_rows);
END;
$$;

ALTER FUNCTION "public"."list_shareable_recipients"("text", integer) OWNER TO "postgres";

COMMENT ON FUNCTION "public"."list_shareable_recipients"("text", integer) IS 'Distinct active co-members across every organisation the caller belongs to. Recipient picker for the screenshot-share plugin -- not a general user directory.';


-- ============================================================================
-- SECTION 3: share_screenshot -- the write path
-- ============================================================================

CREATE OR REPLACE FUNCTION "public"."share_screenshot"(
    "p_recipient_id" "uuid",
    "p_image_base64" "text",
    "p_mime_type" "text" DEFAULT 'image/png'::"text",
    "p_width" integer DEFAULT NULL::integer,
    "p_height" integer DEFAULT NULL::integer,
    "p_note" "text" DEFAULT NULL::"text",
    "p_password" "text" DEFAULT NULL::"text"
) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_org_id UUID;
    v_image BYTEA;
    v_share_id UUID;
    v_password_hash TEXT;
    v_recent_count INTEGER;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    IF p_recipient_id IS NULL OR p_recipient_id = v_actor THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid recipient');
    END IF;

    IF p_image_base64 IS NULL OR btrim(p_image_base64) = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'No image data provided');
    END IF;

    IF p_mime_type IS NULL OR p_mime_type NOT IN ('image/png', 'image/jpeg') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unsupported image type');
    END IF;

    IF p_password IS NOT NULL AND char_length(p_password) > 128 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Password must be 128 characters or fewer');
    END IF;

    -- Rolling-24h send quota. The per-row 8MB cap bounds one image but nothing
    -- bounded how many, so table growth was open-ended; 100/day/sender puts a
    -- predictable ceiling (~800MB/day worst case) on it. Checked BEFORE the
    -- base64 decode below so a throttled call costs no decode work, and served
    -- by the existing idx_screenshot_shares_sender index.
    SELECT count(*) INTO v_recent_count
      FROM public.screenshot_shares
     WHERE sender_id = v_actor
       AND created_at > now() - interval '24 hours';

    IF v_recent_count >= 100 THEN
        RETURN jsonb_build_object('success', false, 'error',
            'Daily send limit reached (100 per 24 hours) -- try again later');
    END IF;

    -- A common ACTIVE organisation is the whole authorization check -- the
    -- same relationship list_shareable_recipients offers, re-verified here
    -- rather than trusted from the client.
    SELECT om.org_id INTO v_org_id
    FROM public.organisation_members om
    JOIN public.organisation_members recip
      ON recip.org_id = om.org_id
     AND recip.user_id = p_recipient_id
     AND recip.status = 'active'
    WHERE om.user_id = v_actor AND om.status = 'active'
    LIMIT 1;

    IF v_org_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error',
            'Recipient does not share an organisation with you');
    END IF;

    BEGIN
        v_image := decode(p_image_base64, 'base64');
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object('success', false, 'error', 'Image data is not valid base64');
    END;

    IF octet_length(v_image) = 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Decoded image is empty');
    END IF;

    IF octet_length(v_image) > 8388608 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Screenshot exceeds the 8MB limit');
    END IF;

    IF p_password IS NOT NULL AND btrim(p_password) <> '' THEN
        v_password_hash := extensions.crypt(p_password, extensions.gen_salt('bf'));
    END IF;

    INSERT INTO public.screenshot_shares (
        sender_id, recipient_id, org_id, image_data, mime_type, width, height, note, password_hash
    ) VALUES (
        v_actor, p_recipient_id, v_org_id, v_image, p_mime_type, p_width, p_height, p_note, v_password_hash
    ) RETURNING id INTO v_share_id;

    RETURN jsonb_build_object('success', true, 'share_id', v_share_id::text);
END;
$$;

ALTER FUNCTION "public"."share_screenshot"("uuid", "text", "text", integer, integer, "text", "text") OWNER TO "postgres";

COMMENT ON FUNCTION "public"."share_screenshot"("uuid", "text", "text", integer, integer, "text", "text") IS 'Sends a base64-encoded PNG/JPEG to a co-member of any organisation the caller actively belongs to. Re-checks the common-organisation relationship server-side rather than trusting list_shareable_recipients output. Rate-limited to 100 sends per sender per rolling 24 hours, which together with the 8MB per-row cap is what bounds this table''s growth.';


-- ============================================================================
-- SECTION 4: Read paths -- list_received_screenshots / list_sent_screenshots
-- ============================================================================
-- Both project away image_data: a poll every ~20-30s must stay cheap, and the
-- image is fetched separately, once, via get_screenshot_image.

CREATE OR REPLACE FUNCTION "public"."list_received_screenshots"(
    "p_only_unread" boolean DEFAULT false,
    "p_limit" integer DEFAULT 50,
    "p_offset" integer DEFAULT 0
) RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_rows JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_rows
      FROM (
        SELECT s.id, s.sender_id, u.email AS sender_email, s.note,
               s.width, s.height, s.mime_type, s.created_at, s.read_at,
               (s.password_hash IS NOT NULL) AS has_password
        FROM public.screenshot_shares s
        JOIN auth.users u ON u.id = s.sender_id
        WHERE s.recipient_id = v_actor
          AND s.expires_at > now()
          AND (NOT p_only_unread OR s.read_at IS NULL)
        ORDER BY s.created_at DESC
        LIMIT GREATEST(LEAST(COALESCE(p_limit, 50), 200), 1)
        OFFSET GREATEST(COALESCE(p_offset, 0), 0)
      ) t;

    RETURN jsonb_build_object('success', true, 'data', v_rows);
END;
$$;

ALTER FUNCTION "public"."list_received_screenshots"(boolean, integer, integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."list_sent_screenshots"(
    "p_limit" integer DEFAULT 50,
    "p_offset" integer DEFAULT 0
) RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_rows JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_rows
      FROM (
        SELECT s.id, s.recipient_id, u.email AS recipient_email, s.note,
               s.width, s.height, s.mime_type, s.created_at, s.read_at,
               (s.password_hash IS NOT NULL) AS has_password
        FROM public.screenshot_shares s
        JOIN auth.users u ON u.id = s.recipient_id
        WHERE s.sender_id = v_actor
          AND s.expires_at > now()
        ORDER BY s.created_at DESC
        LIMIT GREATEST(LEAST(COALESCE(p_limit, 50), 200), 1)
        OFFSET GREATEST(COALESCE(p_offset, 0), 0)
      ) t;

    RETURN jsonb_build_object('success', true, 'data', v_rows);
END;
$$;

ALTER FUNCTION "public"."list_sent_screenshots"(integer, integer) OWNER TO "postgres";


-- ============================================================================
-- SECTION 5: get_screenshot_image -- fetches bytes, marks read as a side effect
-- ============================================================================

CREATE OR REPLACE FUNCTION "public"."get_screenshot_image"("p_share_id" "uuid", "p_password" "text" DEFAULT NULL::"text")
RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_row public.screenshot_shares;
    v_new_attempts INTEGER;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    SELECT * INTO v_row FROM public.screenshot_shares s
    WHERE s.id = p_share_id AND s.expires_at > now();

    IF NOT FOUND OR (v_row.sender_id <> v_actor AND v_row.recipient_id <> v_actor) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Screenshot not found');
    END IF;

    -- The password gate only applies to the RECIPIENT -- the sender who set
    -- it already knows it and must always be able to see their own sent item.
    IF v_row.password_hash IS NOT NULL AND v_row.recipient_id = v_actor THEN
        IF v_row.failed_password_attempts >= 5 THEN
            RETURN jsonb_build_object('success', false, 'error', 'locked');
        END IF;

        IF p_password IS NULL THEN
            RETURN jsonb_build_object('success', false, 'error', 'password_required');
        END IF;

        IF extensions.crypt(p_password, v_row.password_hash) <> v_row.password_hash THEN
            UPDATE public.screenshot_shares
               SET failed_password_attempts = failed_password_attempts + 1
             WHERE id = p_share_id
             RETURNING failed_password_attempts INTO v_new_attempts;

            RETURN jsonb_build_object(
                'success', false,
                'error', 'invalid_password',
                'attempts_remaining', GREATEST(5 - v_new_attempts, 0));
        END IF;
    END IF;

    -- Marked read on first fetch by the RECIPIENT only -- the sender opening
    -- their own sent item must never flip a badge the recipient hasn't earned.
    IF v_row.recipient_id = v_actor AND v_row.read_at IS NULL THEN
        UPDATE public.screenshot_shares SET read_at = now() WHERE id = p_share_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'image_base64', encode(v_row.image_data, 'base64'),
        'mime_type', v_row.mime_type,
        'width', v_row.width,
        'height', v_row.height);
END;
$$;

ALTER FUNCTION "public"."get_screenshot_image"("uuid", "text") OWNER TO "postgres";

COMMENT ON FUNCTION "public"."get_screenshot_image"("uuid", "text") IS 'Returns the base64-encoded image and marks read_at the first time the RECIPIENT (never the sender) fetches it. If the share is password-protected, the RECIPIENT must supply a matching p_password -- errors "password_required"/"invalid_password"/"locked" (5 wrong attempts) let the caller distinguish those cases from a hard failure.';


-- ============================================================================
-- SECTION 6: Grants
-- ============================================================================
-- REVOKE first in every case: 20251023000014_grants.sql's default privileges
-- hand anon EXECUTE on every new function the instant it's created.

REVOKE EXECUTE ON FUNCTION "public"."list_shareable_recipients"("text", integer) FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."list_shareable_recipients"("text", integer) TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."share_screenshot"("uuid", "text", "text", integer, integer, "text", "text") FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."share_screenshot"("uuid", "text", "text", integer, integer, "text", "text") TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."list_received_screenshots"(boolean, integer, integer) FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."list_received_screenshots"(boolean, integer, integer) TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."list_sent_screenshots"(integer, integer) FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."list_sent_screenshots"(integer, integer) TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."get_screenshot_image"("uuid", "text") FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."get_screenshot_image"("uuid", "text") TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"() FROM PUBLIC, "anon", "authenticated";


-- ============================================================================
-- End of File: 20260824000000_screenshot_shares.sql
-- ============================================================================
