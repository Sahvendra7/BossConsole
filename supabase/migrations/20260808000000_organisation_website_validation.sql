-- ============================================================================
-- BOSS Database Schema: validate the website on every write path
--
-- File: 20260808000000_organisation_website_validation.sql
--
-- 20260807000000's header claimed the scheme restriction was applied "in the
-- column and again in the RPC". That was true of submit_organisation_request and
-- NOT of update_organisation_settings - which is the path an administrator
-- actually uses, while submit is the one a person uses once.
--
-- The consequence was worse than a missing message. The only remaining guard was
-- organisations_website_check, which raises 23514 and ABORTS THE WHOLE UPDATE, so
-- a mistyped website silently discarded the name, description, visibility, join
-- policy, publish policy and the auto-assign checkbox in the same submission -
-- and the page could only render "The change was refused", naming no field.
-- Reproduced before fixing: typing "zed.example" into the settings form raised
-- the constraint out of the function rather than returning an error object.
--
-- The check now lives in one place both RPCs call, so the two cannot drift again,
-- and create_organisation_internal re-checks on the write path for the same
-- reason the domain right above it does.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- One definition of what a website may be
-- ---------------------------------------------------------------------------
-- Returns the message to show, or NULL when the value is acceptable. NULL input
-- is acceptable: the field is optional everywhere.
--
-- IMMUTABLE and STRICT-free on purpose - it must return NULL for NULL input
-- rather than being skipped, so callers can pass an unset value straight through.
CREATE OR REPLACE FUNCTION "public"."validate_website"("p_website" "text")
RETURNS "text"
    LANGUAGE "plpgsql" IMMUTABLE
    SET "search_path" TO ''
    AS $$
BEGIN
    IF p_website IS NULL OR btrim(p_website) = '' THEN
        RETURN NULL;
    END IF;
    -- http and https only. This value is rendered as a link on pages an
    -- organisation's members read and is supplied by any authenticated user on a
    -- request, before review, so a javascript: or data: URL here would be stored
    -- XSS with a self-service entry point.
    IF btrim(p_website) !~* '^https?://[^[:space:]]+$' THEN
        RETURN 'Website must be a full http:// or https:// address';
    END IF;
    IF length(btrim(p_website)) > 500 THEN
        RETURN 'Website must be 500 characters or fewer';
    END IF;
    RETURN NULL;
END;
$$;

ALTER FUNCTION "public"."validate_website"("text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."validate_website"("text") IS 'Returns the error message for an unacceptable organisation website, or NULL. Shared by submit_organisation_request, update_organisation_settings and create_organisation_internal so the rule cannot drift between the path a requester uses and the one an administrator uses.';


CREATE OR REPLACE FUNCTION "public"."submit_organisation_request"(
    "p_name" "text",
    "p_slug" "text",
    "p_description" "text" DEFAULT NULL::"text",
    "p_domain" "text" DEFAULT NULL::"text",
    "p_justification" "text" DEFAULT NULL::"text",
    "p_website" "text" DEFAULT NULL::"text",
    "p_actor_id" "uuid" DEFAULT NULL::"uuid"
) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID;
    v_request_id UUID;
    v_pending_count INTEGER;
    v_domain TEXT;
    v_website TEXT;
BEGIN
    v_actor := public.resolve_org_actor(p_actor_id);
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- A genuinely global permission, so a permission check is the right gate.
    IF NOT public.user_holds_permission(v_actor, 'organisation.create') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    IF p_name IS NULL OR char_length(btrim(p_name)) < 2 OR char_length(btrim(p_name)) > 100 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Name must be 2-100 characters');
    END IF;

    IF p_slug IS NULL OR NOT (p_slug ~ '^[a-z][a-z0-9_]{1,30}$') THEN
        RETURN jsonb_build_object('success', false, 'error',
            'Slug must be 2-31 characters, lowercase letters, digits and underscores, starting with a letter');
    END IF;

    IF public.is_reserved_organisation_slug(p_slug) THEN
        RETURN jsonb_build_object('success', false, 'error',
            format('Slug "%s" is reserved or already in use', p_slug));
    END IF;

    IF EXISTS (SELECT 1 FROM public.organisations o WHERE o.slug = p_slug) THEN
        RETURN jsonb_build_object('success', false, 'error',
            format('An organisation with slug "%s" already exists', p_slug));
    END IF;

    -- Three live requests per person is enough for legitimate use and keeps the
    -- reviewer queue from being floodable.
    SELECT count(*) INTO v_pending_count
    FROM public.organisation_requests r
    WHERE r.requester_id = v_actor AND r.status = 'pending';
    IF v_pending_count >= 3 THEN
        RETURN jsonb_build_object('success', false, 'error',
            'You already have 3 pending organisation requests. Withdraw one before submitting another.');
    END IF;

    IF p_domain IS NOT NULL AND btrim(p_domain) <> '' THEN
        v_domain := lower(btrim(p_domain));
        IF NOT (v_domain ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$') THEN
            RETURN jsonb_build_object('success', false, 'error', 'That is not a valid domain name');
        END IF;
        IF EXISTS (SELECT 1 FROM public.reserved_email_domains red WHERE red.domain = v_domain) THEN
            RETURN jsonb_build_object('success', false, 'error',
                format('"%s" is a reserved email domain and cannot be claimed by an organisation', v_domain));
        END IF;
        IF EXISTS (SELECT 1 FROM public.organisation_domains d WHERE d.domain = v_domain) THEN
            RETURN jsonb_build_object('success', false, 'error',
                format('Domain "%s" is already claimed by another organisation', v_domain));
        END IF;
    END IF;

    -- Validated HERE as well as by the column CHECK, so a typo comes back as a
    -- sentence rather than a constraint violation the caller cannot read.
    --
    -- http and https only. This value is rendered as a link on the organisation
    -- pages, and a javascript: or data: URL there would be script execution from a
    -- field any authenticated user can fill in. The renderer refuses those too;
    -- this is the half that stops one being stored at all.
    v_website := NULLIF(btrim(COALESCE(p_website, '')), '');
    IF public.validate_website(v_website) IS NOT NULL THEN
        RETURN jsonb_build_object('success', false, 'error', public.validate_website(v_website));
    END IF;

    BEGIN
        INSERT INTO public.organisation_requests (
            requester_id, name, slug, description, domain, justification, website, status
        ) VALUES (
            v_actor, btrim(p_name), p_slug, p_description, v_domain, p_justification, v_website, 'pending'
        ) RETURNING id INTO v_request_id;
    EXCEPTION WHEN unique_violation THEN
        -- idx_organisation_requests_pending_slug
        RETURN jsonb_build_object('success', false, 'error',
            format('A pending request for slug "%s" already exists', p_slug));
    END;

    RETURN jsonb_build_object('success', true, 'request_id', v_request_id::text, 'status', 'pending');
END;
$$;


CREATE OR REPLACE FUNCTION "public"."update_organisation_settings"(
    "p_org_id" "uuid",
    "p_name" "text" DEFAULT NULL::"text",
    "p_description" "text" DEFAULT NULL::"text",
    "p_visibility" "text" DEFAULT NULL::"text",
    "p_join_policy" "text" DEFAULT NULL::"text",
    "p_publish_policy" "text" DEFAULT NULL::"text",
    "p_publish_role_id" "uuid" DEFAULT NULL::"uuid",
    "p_clear_publish_role" boolean DEFAULT false,
    "p_website" "text" DEFAULT NULL::"text",
    "p_auto_assign_member_role" boolean DEFAULT NULL::boolean,
    "p_actor_id" "uuid" DEFAULT NULL::"uuid"
) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID;
BEGIN
    v_actor := public.resolve_org_actor(p_actor_id);
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    IF NOT public.user_is_org_admin(v_actor, p_org_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    IF p_visibility IS NOT NULL AND p_visibility NOT IN ('private', 'public') THEN
        RETURN jsonb_build_object('success', false, 'error', 'visibility must be private or public');
    END IF;

    IF p_join_policy IS NOT NULL AND p_join_policy NOT IN ('invite_only', 'request_to_join', 'open') THEN
        RETURN jsonb_build_object('success', false, 'error',
            'join_policy must be invite_only, request_to_join or open');
    END IF;

    IF p_publish_policy IS NOT NULL AND p_publish_policy NOT IN ('owner_only', 'admins', 'members') THEN
        RETURN jsonb_build_object('success', false, 'error',
            'publish_policy must be owner_only, admins or members');
    END IF;

    -- A publish role must be one of THIS organisation's roles, or an organisation
    -- admin could delegate publishing to the holders of an arbitrary global role.
    IF p_publish_role_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.organisation_roles orl
        WHERE orl.org_id = p_org_id AND orl.role_id = p_publish_role_id
    ) THEN
        RETURN jsonb_build_object('success', false, 'error',
            'The publish role must belong to this organisation');
    END IF;

    IF p_name IS NOT NULL AND (char_length(btrim(p_name)) < 2 OR char_length(btrim(p_name)) > 100) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Name must be 2-100 characters');
    END IF;

    -- Checked BEFORE the UPDATE. Without this the only guard was the column CHECK,
    -- which raises 23514 and aborts the WHOLE statement - so a mistyped website
    -- silently discarded the name, description, visibility, join policy, publish
    -- policy and the auto-assign checkbox with it, and the page could only say
    -- "the change was refused". This is the path an administrator actually uses;
    -- submit_organisation_request is the one a person uses once.
    IF public.validate_website(NULLIF(btrim(COALESCE(p_website, '')), '')) IS NOT NULL THEN
        RETURN jsonb_build_object('success', false, 'error',
            public.validate_website(NULLIF(btrim(COALESCE(p_website, '')), '')));
    END IF;

    UPDATE public.organisations
       SET name           = COALESCE(btrim(p_name), name),
           -- NULLIF(p_description, '') is NOT what is wanted here: an empty string is the
           -- caller explicitly CLEARING the description, and COALESCE would then keep the old
           -- one. Empty means empty; only a SQL NULL means leave unchanged.
           description    = CASE WHEN p_description IS NULL THEN description
                                 WHEN btrim(p_description) = '' THEN NULL
                                 ELSE p_description END,
           -- Same empty-means-empty rule the description uses: an empty string
           -- CLEARS the website, and only a SQL NULL leaves it alone.
           website        = CASE WHEN p_website IS NULL THEN website
                                 WHEN btrim(p_website) = '' THEN NULL
                                 ELSE btrim(p_website) END,
           visibility     = COALESCE(p_visibility, visibility),
           join_policy    = COALESCE(p_join_policy, join_policy),
           publish_policy = COALESCE(p_publish_policy, publish_policy),
           -- COALESCE alone can never set a column back to NULL, so "revert to
           -- publish_policy" needs its own explicit signal.
           publish_role_id = CASE WHEN p_clear_publish_role THEN NULL
                                  ELSE COALESCE(p_publish_role_id, publish_role_id) END,
           auto_assign_member_role = COALESCE(p_auto_assign_member_role, auto_assign_member_role),
           updated_at = now()
     WHERE id = p_org_id;

    RETURN jsonb_build_object('success', true);
END;
$$;


-- ---------------------------------------------------------------------------
-- An approver must be able to see the website they are approving
-- ---------------------------------------------------------------------------
-- The whole threat model here is that any authenticated user supplies this value
-- and it ends up as a link on a page other members read. Review is the control
-- that stands between those two facts, and the queue was not showing the field.

CREATE OR REPLACE FUNCTION "public"."list_organisation_requests"(
    "p_status" "text" DEFAULT NULL::"text",
    "p_limit" integer DEFAULT 50,
    "p_offset" integer DEFAULT 0,
    "p_actor_id" "uuid" DEFAULT NULL::"uuid"
) RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_actor UUID;
    v_is_reviewer BOOLEAN;
    v_rows JSONB;
BEGIN
    v_actor := public.resolve_org_actor(p_actor_id);
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    v_is_reviewer := public.user_holds_permission(v_actor, 'organisation.approve');

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_rows
      FROM (
        SELECT r.id, r.requester_id, ru.email AS requester_email,
               r.name, r.slug, r.description, r.domain, r.website, r.justification,
               r.status, r.reviewer_id, vu.email AS reviewer_email,
               r.review_notes, r.reviewed_at, r.created_org_id, r.created_at
        FROM public.organisation_requests r
        LEFT JOIN auth.users ru ON ru.id = r.requester_id
        LEFT JOIN auth.users vu ON vu.id = r.reviewer_id
        WHERE (v_is_reviewer OR r.requester_id = v_actor)
          AND (p_status IS NULL OR r.status = p_status)
        ORDER BY r.created_at DESC
        LIMIT GREATEST(LEAST(COALESCE(p_limit, 50), 200), 1)
        OFFSET GREATEST(COALESCE(p_offset, 0), 0)
      ) t;

    RETURN jsonb_build_object('success', true, 'is_reviewer', v_is_reviewer, 'data', v_rows);
END;
$$;


-- ---------------------------------------------------------------------------
-- Comments DROP FUNCTION removed in 20260807000000
-- ---------------------------------------------------------------------------
-- DROP takes the COMMENT with it as well as the grants. 20260807000000 re-applied
-- the grants and re-added submit_organisation_request's comment, and silently lost
-- these two - including the "service_role ONLY" warning, which is the one a reader
-- most needs.
COMMENT ON FUNCTION "public"."create_organisation_internal"("text", "text", "text", "uuid", "text", "text", "text", boolean, "text", "text", boolean, "text") IS 'Creates an organisation with its two roles, the founder membership and an optional unverified domain. service_role ONLY: it accepts an arbitrary owner id and performs no authorisation of its own, so every caller must have established the actor first.';

COMMENT ON FUNCTION "public"."update_organisation_settings"("uuid", "text", "text", "text", "text", "text", "uuid", boolean, "text", boolean, "uuid") IS 'Updates an organisation''s settings. Organisation admins only, re-derived server-side. An empty string CLEARS description and website; a SQL NULL leaves them unchanged.';
