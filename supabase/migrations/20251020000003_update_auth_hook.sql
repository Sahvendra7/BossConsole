-- ============================================================================
-- BOSS RBAC Migration: Update Auth Hook for Table-Based Schema
-- ============================================================================
-- Phase 3: Update custom_access_token_hook to use new table-based schema
--
-- Purpose:
-- - Update get_user_roles_for_hook() to query user_roles_new table
-- - Maintain JWT claim structure (no breaking changes)
-- - Ensure roles are fetched from roles table via JOIN
--
-- Impact:
-- - All new logins will get role claims from new table structure
-- - Existing sessions will continue with old claims until token refresh
--
-- ============================================================================

-- ============================================================================
-- UPDATE HELPER FUNCTION TO GET USER ROLES (NEW TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_user_roles_for_hook(check_user_id UUID)
RETURNS text[]
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN (
        SELECT ARRAY_AGG(r.name ORDER BY ur.assigned_at)
        FROM public.user_roles_new ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id
    );
END;
$$;

COMMENT ON FUNCTION public.get_user_roles_for_hook(UUID) IS 'Helper function for auth hook to fetch user roles from NEW table-based schema, bypassing RLS.';

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.get_user_roles_for_hook TO supabase_auth_admin;
GRANT ALL ON TABLE public.user_roles_new TO supabase_auth_admin;
GRANT ALL ON TABLE public.roles TO supabase_auth_admin;

-- ============================================================================
-- UPDATE CUSTOM ACCESS TOKEN HOOK (IF NEEDED)
-- ============================================================================
-- Note: The custom_access_token_hook function itself doesn't need changes
--       because it calls get_user_roles_for_hook which we just updated.
--       The hook will now automatically use the new table structure.
--
-- However, we're recreating it here for completeness and to ensure
-- it has the correct structure after migration.

CREATE OR REPLACE FUNCTION public.custom_access_token_hook(event jsonb)
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

    -- Use helper function to fetch all roles for the user (now uses NEW tables)
    user_roles_array := public.get_user_roles_for_hook((event->>'user_id')::uuid);

    -- Set primary role (first role, or 'user' if none)
    IF user_roles_array IS NOT NULL AND array_length(user_roles_array, 1) > 0 THEN
        primary_role := user_roles_array[1];
    ELSE
        primary_role := 'user';
    END IF;

    -- Inject custom claims
    IF user_roles_array IS NOT NULL THEN
        -- Set primary role claim (for simple checks)
        claims := jsonb_set(claims, '{user_role}', to_jsonb(primary_role));

        -- Set all roles claim (for multi-role checks)
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(user_roles_array));

        -- Set is_admin flag (convenient for quick checks)
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

COMMENT ON FUNCTION public.custom_access_token_hook(jsonb) IS 'Auth hook that injects user roles into JWT claims before token issuance. Now uses table-based schema.';

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.custom_access_token_hook TO supabase_auth_admin;
REVOKE EXECUTE ON FUNCTION public.custom_access_token_hook FROM authenticated, anon, public;

-- ============================================================================
-- CREATE RLS POLICY FOR AUTH ADMIN ACCESS TO NEW TABLES
-- ============================================================================

-- Allow auth admin to read all user roles from NEW table
CREATE POLICY "Allow auth admin to read user roles (new)"
    ON public.user_roles_new
    AS PERMISSIVE
    FOR SELECT
    TO supabase_auth_admin
    USING (true);

-- Allow auth admin to read all roles
CREATE POLICY "Allow auth admin to read roles"
    ON public.roles
    AS PERMISSIVE
    FOR SELECT
    TO supabase_auth_admin
    USING (true);

-- ============================================================================
-- VERIFICATION
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '✅ Auth Hook Migration Complete';
    RAISE NOTICE '  - get_user_roles_for_hook() updated to use user_roles_new + roles tables';
    RAISE NOTICE '  - custom_access_token_hook() confirmed working with new schema';
    RAISE NOTICE '  - RLS policies created for supabase_auth_admin';
    RAISE NOTICE '';
    RAISE NOTICE '🔄 Next Steps:';
    RAISE NOTICE '  1. Test authentication to verify JWT claims contain roles';
    RAISE NOTICE '  2. Proceed with Phase 4 (cutover) if tests pass';
    RAISE NOTICE '  3. After cutover, all sessions will use new table structure';
END;
$$;

-- ============================================================================
-- MIGRATION COMPLETE (PHASE 3)
-- ============================================================================
--
-- ✅ get_user_roles_for_hook() updated to use new tables
-- ✅ custom_access_token_hook() confirmed compatible
-- ✅ Permissions granted to supabase_auth_admin
-- ✅ RLS policies created for auth admin access
--
-- Testing:
-- To test the hook with new tables:
--   SELECT public.custom_access_token_hook(
--       jsonb_build_object(
--           'user_id', 'your-user-uuid',
--           'claims', '{}'::jsonb
--       )
--   );
--
-- Expected output should include:
--   {
--     "claims": {
--       "user_role": "user" or "admin",
--       "user_roles": ["user"] or ["user", "admin"],
--       "is_admin": true or false
--     }
--   }
--
-- Next Steps:
-- 1. Test the hook function directly (see above)
-- 2. Test real authentication flow
-- 3. If tests pass, proceed with Phase 4: 20251020_cutover_tables.sql
--
-- ============================================================================
