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
--   two users who are both ACTIVE members of at least one common organisation.
--   The recipient side of that check goes through public.user_is_org_member so
--   it agrees with the rest of the RBAC surface (including its treatment of
--   global admins as members everywhere); is_org_member(org_id) cannot serve
--   here because it only ever asks about auth.uid(). There is no "any BOSS
--   user" path -- that would need a new global user-search surface, which does
--   not exist and is out of scope here.
--
--   Known limitation: the CALLER side is still an explicit organisation_members
--   join, so a global admin holding no explicit membership row sees an empty
--   recipient list and cannot share. Routing the caller through
--   user_is_org_member too would make an admin's picker every user in every
--   organisation, which is a product decision rather than a bug fix.
--
-- Dependencies:
--   - 20260801000000_organisation_tables.sql (organisation_members)
--   - 20260801010000_organisation_permissions_and_guards.sql (user_is_org_member)
--
-- Tables: 1 (screenshot_shares)
-- Functions: 7 (share_screenshot, list_received_screenshots,
--               list_sent_screenshots, get_screenshot_image,
--               list_shareable_recipients, delete_screenshot_share,
--               trigger_cleanup_expired_screenshot_shares)
-- ============================================================================


-- ============================================================================
-- SECTION 1: screenshot_shares
-- ============================================================================
-- The image lives in the row (bytea), not in Storage -- plugins have no
-- Storage upload primitive today.
--
-- The 8MB ceiling on one image is defined by the screenshot_shares_image_size
-- CHECK below and is the single source of truth; share_screenshot() repeats the
-- literal only to return a friendly error instead of a constraint violation,
-- and the plugin repeats it again (MAX_IMAGE_BYTES) to fail before uploading.
-- Change the CHECK and those two follow.

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

-- The CREATE TABLE above is IF NOT EXISTS, so on a database where this table was
-- created BEFORE the password feature it is a no-op and the two password columns
-- never appear. plpgsql bodies are not name-resolved at CREATE time, so every
-- function below would still be created happily and then fail at runtime with
-- "column password_hash does not exist". Adding them explicitly is what makes
-- re-running this file actually converge.
ALTER TABLE "public"."screenshot_shares"
    ADD COLUMN IF NOT EXISTS "password_hash" "text",
    ADD COLUMN IF NOT EXISTS "failed_password_attempts" integer DEFAULT 0 NOT NULL;

DO $do$
BEGIN
    ALTER TABLE public.screenshot_shares
        ADD CONSTRAINT screenshot_shares_failed_attempts_check
        CHECK (failed_password_attempts >= 0);
EXCEPTION WHEN duplicate_object THEN
    NULL;
END
$do$;

COMMENT ON TABLE "public"."screenshot_shares" IS 'One shared, annotated screenshot. image_data is the flattened image -- PNG from the plugin today, though share_screenshot() also accepts image/jpeg and nothing verifies the bytes match the declared mime_type. Stored inline because plugins have no Storage upload path. org_id records which shared organisation made the share eligible -- it is provenance, not a live authorization check: removal from the org does not retract an already-sent share. (Recall is a separate matter and is available -- see delete_screenshot_share.)';

COMMENT ON COLUMN "public"."screenshot_shares"."expires_at" IS 'Default 14 days. Enforced on read by list_*/get_screenshot_image, which filter on expires_at > now(); physical removal is separate and lags by a one-day grace window (trigger_cleanup_expired_screenshot_shares, the same probabilistic-on-insert pattern as organisation_handoff_tokens). A share therefore stops being reachable exactly at expires_at, whether or not its row has been collected yet. Projected by list_* so the plugin can show "expires in N days".';

COMMENT ON COLUMN "public"."screenshot_shares"."read_at" IS 'Set once, the first time the recipient calls get_screenshot_image() for this row. Drives the unread badge in the plugin''s inbox panel.';

COMMENT ON COLUMN "public"."screenshot_shares"."password_hash" IS 'Optional bcrypt hash (pgcrypto crypt()/gen_salt(''bf'')) of a passphrase the sender set. Null means the share opens with no password prompt. One-way -- get_screenshot_image() verifies against it, nothing ever reads it back. SCOPE: the gate covers the image BYTES only. note, width/height, sender identity and created_at are returned ungated by list_received_screenshots(), so a sender must not treat the note as protected content.';

COMMENT ON COLUMN "public"."screenshot_shares"."failed_password_attempts" IS 'Incremented by get_screenshot_image() on every wrong password. At 5, the share is locked out for its remaining lifetime (no further hash comparison is even attempted) -- there is no cooldown/reset, matching this table''s otherwise simple, short-lived-row design.';

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_recipient"
    ON "public"."screenshot_shares" ("recipient_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_sender"
    ON "public"."screenshot_shares" ("sender_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_screenshot_shares_expires"
    ON "public"."screenshot_shares" ("expires_at");

ALTER TABLE "public"."screenshot_shares" ENABLE ROW LEVEL SECURITY;

-- No policies at all, deliberately. RLS is enabled and the table has no grants
-- for anon/authenticated, so the SECURITY DEFINER RPCs below are the only path
-- in -- which is what lets get_screenshot_image() gate on the password and mark
-- read_at, and lets list_* project image_data away so a poll never drags image
-- bytes it does not need.
--
-- An earlier `FOR SELECT USING (sender_id = auth.uid() OR recipient_id =
-- auth.uid())` policy was removed rather than kept as documentation: it was
-- unreachable (no SELECT grant), but had a future grants sweep re-granted
-- SELECT it would have handed the recipient image_data and password_hash
-- directly, silently reducing the password gate to decoration. With RLS on and
-- no policy, that same re-grant denies instead -- fail-closed by construction.
DROP POLICY IF EXISTS "screenshot_shares_select" ON "public"."screenshot_shares";

REVOKE ALL ON TABLE "public"."screenshot_shares" FROM "anon", "authenticated";
GRANT ALL ON TABLE "public"."screenshot_shares" TO "service_role";


-- Probabilistic cleanup on insert, same shape as
-- trigger_cleanup_expired_handoff_tokens (20260801000000).
--
-- Genuinely opportunistic, unlike that precedent: this DELETE runs inside the
-- INSERTing caller's transaction, so two concurrent sends whose 10% rolls both
-- hit could deadlock on overlapping expired rows -- and the user would see that
-- as their screenshot failing to send. SKIP LOCKED means a row another
-- transaction is already clearing is simply left for next time, and the
-- EXCEPTION block means no cleanup failure can ever cost someone their share.
--
-- The one-day grace period (rather than deleting at exactly now()) leaves a
-- window in which an expired share can still be inspected while diagnosing why
-- it vanished.
CREATE OR REPLACE FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    IF "random"() < 0.1 THEN
        BEGIN
            DELETE FROM public.screenshot_shares
            WHERE id IN (
                SELECT id FROM public.screenshot_shares
                WHERE expires_at < now() - interval '1 day'
                FOR UPDATE SKIP LOCKED
            );
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;
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
    v_pattern TEXT;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- % and _ are wildcards to ILIKE, so a user typing either would silently
    -- widen their own search (a lone '%' matching everyone). Not an injection
    -- risk -- p_query is a parameter, never concatenated into SQL text -- but
    -- the escaping is what makes the search mean what the user typed.
    IF p_query IS NOT NULL THEN
        v_pattern := '%' || replace(replace(replace(p_query, '\', '\\'), '%', '\%'), '_', '\_') || '%';
    END IF;

    -- The DISTINCT ON collapse has to be ordered by (u.id, ...) to pick one row
    -- per user, so ordering and truncation for the CALLER both have to happen at
    -- an outer level. Doing it inline meant LIMIT took an arbitrary
    -- uuid-ordered slice, and jsonb_agg then emitted it in that same
    -- meaningless order -- so in an org above the limit the picker showed a
    -- random subset in a random order.
    -- The ORDER BY belongs inside jsonb_agg: a subquery's ORDER BY happens to
    -- survive into the aggregate today but nothing guarantees it, and the inner
    -- LIMIT still needs its own ordering to pick the right rows.
    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.display_name, t.email), '[]'::jsonb)
      INTO v_rows
      FROM (
        SELECT * FROM (
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
              -- Matches what the picker actually displays, not just the email:
              -- searching for the name on screen used to return nothing.
              AND (
                  v_pattern IS NULL
                  OR u.email ILIKE v_pattern
                  OR COALESCE(u.raw_user_meta_data ->> 'full_name', '') ILIKE v_pattern
                  OR COALESCE(u.raw_user_meta_data ->> 'name', '') ILIKE v_pattern
              )
            ORDER BY u.id, o.name
        ) d
        ORDER BY d.display_name, d.email
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

-- Adding p_password CHANGED THE SIGNATURE, so the CREATE OR REPLACE below defines
-- a NEW overload rather than replacing the old one -- and the old one keeps its
-- grant to `authenticated`. Left in place, the 6-argument version is a live
-- bypass: it stores no password_hash and skips the send quota entirely.
DROP FUNCTION IF EXISTS "public"."share_screenshot"("uuid", "text", "text", integer, integer, "text");

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
    --
    -- Soft by design: this counts and then inserts without serialising, so
    -- concurrent sends can overshoot 100 slightly. Tightening it would mean
    -- locking the sender's rows on every send to enforce a number that is a
    -- guard rail, not a billing boundary.
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
    --
    -- The recipient side goes through user_is_org_member rather than a second
    -- organisation_members join so this agrees with the rest of the RBAC
    -- surface, global-admin handling included, instead of reimplementing it.
    SELECT om.org_id INTO v_org_id
    FROM public.organisation_members om
    WHERE om.user_id = v_actor
      AND om.status = 'active'
      AND public.user_is_org_member(p_recipient_id, om.org_id)
    LIMIT 1;

    IF v_org_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error',
            'Recipient does not share an organisation with you');
    END IF;

    -- Narrowly scoped to the decoder's own complaint: a bare WHEN OTHERS here
    -- also reported genuine internal faults (OOM, cancellation) as "not valid
    -- base64", which sent anyone debugging one in the wrong direction.
    BEGIN
        v_image := decode(p_image_base64, 'base64');
    -- data_exception is SQLSTATE class 22, which covers decode()'s
    -- invalid_parameter_value (22023) without swallowing internal faults.
    EXCEPTION WHEN data_exception THEN
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
               s.expires_at,
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
               s.expires_at,
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

-- Same overload trap, and here it is the serious one: the single-argument version
-- has no password check at all and stays EXECUTE-able by `authenticated`, so a
-- recipient could read the image bytes of a protected share just by calling the
-- old signature. Dropping it is what makes the gate real rather than advisory.
DROP FUNCTION IF EXISTS "public"."get_screenshot_image"("uuid");

CREATE OR REPLACE FUNCTION "public"."get_screenshot_image"("p_share_id" "uuid", "p_password" "text" DEFAULT NULL::"text")
RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_sender_id UUID;
    v_recipient_id UUID;
    v_password_hash TEXT;
    v_failed_attempts INTEGER;
    v_new_attempts INTEGER;
    v_image BYTEA;
    v_mime TEXT;
    v_width INTEGER;
    v_height INTEGER;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Metadata columns only, deliberately NOT `SELECT *`: loading the whole row
    -- into a record detoasted up to 8MB of image_data before the password check
    -- had run, so every wrong guess and every locked-out call paid for the full
    -- read. The bytes are fetched in a second statement, once authorised.
    SELECT s.sender_id, s.recipient_id, s.password_hash, s.failed_password_attempts
      INTO v_sender_id, v_recipient_id, v_password_hash, v_failed_attempts
      FROM public.screenshot_shares s
     WHERE s.id = p_share_id AND s.expires_at > now();

    IF NOT FOUND OR (v_sender_id <> v_actor AND v_recipient_id <> v_actor) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Screenshot not found');
    END IF;

    -- The password gate only applies to the RECIPIENT -- the sender who set
    -- it already knows it and must always be able to see their own sent item.
    IF v_password_hash IS NOT NULL AND v_recipient_id = v_actor THEN
        IF v_failed_attempts >= 5 THEN
            RETURN jsonb_build_object('success', false, 'error', 'locked');
        END IF;

        IF p_password IS NULL THEN
            RETURN jsonb_build_object('success', false, 'error', 'password_required');
        END IF;

        IF extensions.crypt(p_password, v_password_hash) <> v_password_hash THEN
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
    IF v_recipient_id = v_actor THEN
        UPDATE public.screenshot_shares SET read_at = now()
         WHERE id = p_share_id AND read_at IS NULL;
    END IF;

    -- Authorised: now pay for the image bytes.
    SELECT s.image_data, s.mime_type, s.width, s.height
      INTO v_image, v_mime, v_width, v_height
      FROM public.screenshot_shares s
     WHERE s.id = p_share_id;

    RETURN jsonb_build_object(
        'success', true,
        'image_base64', encode(v_image, 'base64'),
        'mime_type', v_mime,
        'width', v_width,
        'height', v_height);
END;
$$;

ALTER FUNCTION "public"."get_screenshot_image"("uuid", "text") OWNER TO "postgres";

COMMENT ON FUNCTION "public"."get_screenshot_image"("uuid", "text") IS 'Returns the base64-encoded image and marks read_at the first time the RECIPIENT (never the sender) fetches it. If the share is password-protected, the RECIPIENT must supply a matching p_password -- errors "password_required"/"invalid_password"/"locked" (5 wrong attempts) let the caller distinguish those cases from a hard failure.';


-- ============================================================================
-- SECTION 6: delete_screenshot_share -- recall (sender) / dismiss (recipient)
-- ============================================================================
-- The payload here is arbitrary screen content, so "I shared the wrong window"
-- needs an answer better than waiting out the 14-day expiry. One function
-- serves both sides because the row IS the share: the sender deleting it is a
-- recall, the recipient deleting it is a dismissal, and neither leaves anything
-- behind for the other party.
--
-- This is not in tension with org_id being mere provenance ("a sent email is
-- not unsent" applies to losing org membership, not to the sender's own
-- explicit retraction).

CREATE OR REPLACE FUNCTION "public"."delete_screenshot_share"("p_share_id" "uuid")
RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_deleted UUID;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- No expires_at filter: an already-expired row still awaiting cleanup
    -- should be deletable, and the party check is the whole authorization.
    DELETE FROM public.screenshot_shares
     WHERE id = p_share_id
       AND (sender_id = v_actor OR recipient_id = v_actor)
    RETURNING id INTO v_deleted;

    IF v_deleted IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Screenshot not found');
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$;

ALTER FUNCTION "public"."delete_screenshot_share"("uuid") OWNER TO "postgres";

COMMENT ON FUNCTION "public"."delete_screenshot_share"("uuid") IS 'Deletes a share outright. Callable by either party: for the sender it is a recall (the recipient loses access immediately, even if unread), for the recipient a dismissal. Deliberately NOT password-gated -- a recipient who cannot open a protected share must still be able to remove it from their inbox, and deleting reveals nothing.';


-- ============================================================================
-- SECTION 7: Grants
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

REVOKE EXECUTE ON FUNCTION "public"."delete_screenshot_share"("uuid") FROM PUBLIC, "anon";
GRANT  EXECUTE ON FUNCTION "public"."delete_screenshot_share"("uuid") TO "authenticated", "service_role";

REVOKE EXECUTE ON FUNCTION "public"."trigger_cleanup_expired_screenshot_shares"() FROM PUBLIC, "anon", "authenticated";


-- ============================================================================
-- End of File: 20260824000000_screenshot_shares.sql
-- ============================================================================
