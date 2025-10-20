-- ============================================================================
-- FORCE REFRESH: Auth Hook for JWT Claims
-- ============================================================================
--
-- Purpose: Force Supabase to reload the auth hook by recreating it
--
-- Issue: Supabase auth service caches hook functions, so changes to
--        get_user_roles_for_hook() may not be picked up until cache expires
--
-- Solution: Drop and recreate both functions to force cache refresh
--
-- Created: 2025-01-19
-- ============================================================================

-- ============================================================================
-- 1. Drop and recreate get_user_roles_for_hook
-- ============================================================================

DROP FUNCTION IF EXISTS public.get_user_roles_for_hook(UUID);

CREATE FUNCTION public.get_user_roles_for_hook(check_user_id UUID)
RETURNS text[]
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN (
        SELECT ARRAY_AGG(r.name ORDER BY ur.assigned_at)
        FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id
    );
END;
$$;

COMMENT ON FUNCTION public.get_user_roles_for_hook(UUID) IS
'Helper function for auth hook - returns user roles from table-based schema (REFRESHED)';

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.get_user_roles_for_hook TO supabase_auth_admin;
GRANT ALL ON TABLE public.user_roles TO supabase_auth_admin;
GRANT ALL ON TABLE public.roles TO supabase_auth_admin;

-- ============================================================================
-- 2. Drop and recreate custom_access_token_hook
-- ============================================================================

DROP FUNCTION IF EXISTS public.custom_access_token_hook(jsonb);

CREATE FUNCTION public.custom_access_token_hook(event jsonb)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    claims jsonb;
    user_roles_array text[];
    primary_role text;
BEGIN
    -- Extract claims from the event
    claims := event->'claims';

    -- Use helper function to fetch all roles for the user
    user_roles_array := public.get_user_roles_for_hook((event->>'user_id')::uuid);

    -- Set primary role (first role, or 'user' if none)
    IF user_roles_array IS NOT NULL AND array_length(user_roles_array, 1) > 0 THEN
        primary_role := user_roles_array[1];
    ELSE
        primary_role := 'user';
    END IF;

    -- Inject custom claims
    IF user_roles_array IS NOT NULL THEN
        -- Set primary role claim
        claims := jsonb_set(claims, '{user_role}', to_jsonb(primary_role));

        -- Set all roles claim
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(user_roles_array));

        -- Set is_admin flag
        IF 'admin' = ANY(user_roles_array) THEN
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(true));
        ELSE
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
        END IF;
    ELSE
        -- User has no roles, set defaults
        claims := jsonb_set(claims, '{user_role}', to_jsonb('user'::text));
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(ARRAY['user']::text[]));
        claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
    END IF;

    -- Update the event with modified claims
    event := jsonb_set(event, '{claims}', claims);

    RETURN event;
END;
$$;

COMMENT ON FUNCTION public.custom_access_token_hook(jsonb) IS
'Auth hook that injects user roles into JWT claims (REFRESHED)';

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.custom_access_token_hook TO supabase_auth_admin;
REVOKE EXECUTE ON FUNCTION public.custom_access_token_hook FROM authenticated, anon, public;

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
DECLARE
    v_test_user_id UUID := '9e6af4d5-81ec-44fc-aba7-308223396fb1';
    v_roles text[];
    v_event jsonb;
    v_result jsonb;
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '✅ AUTH HOOK FORCE REFRESHED';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';

    -- Test get_user_roles_for_hook
    SELECT public.get_user_roles_for_hook(v_test_user_id) INTO v_roles;
    RAISE NOTICE '🔍 get_user_roles_for_hook() test:';
    RAISE NOTICE '   User ID: %', v_test_user_id;
    RAISE NOTICE '   Roles returned: %', v_roles;
    RAISE NOTICE '';

    -- Test custom_access_token_hook
    v_event := jsonb_build_object(
        'user_id', v_test_user_id::text,
        'claims', '{}'::jsonb
    );
    v_result := public.custom_access_token_hook(v_event);

    RAISE NOTICE '🔍 custom_access_token_hook() test:';
    RAISE NOTICE '   user_role: %', v_result->'claims'->>'user_role';
    RAISE NOTICE '   user_roles: %', v_result->'claims'->'user_roles';
    RAISE NOTICE '   is_admin: %', v_result->'claims'->>'is_admin';
    RAISE NOTICE '';

    RAISE NOTICE '✅ Auth hook functions recreated';
    RAISE NOTICE '✅ Supabase auth service should pick up changes';
    RAISE NOTICE '';
    RAISE NOTICE '⚠️  Note: May need to wait 1-2 minutes for auth cache to expire';
    RAISE NOTICE '⚠️  Sign out and sign back in to get fresh JWT';
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
END $$;
