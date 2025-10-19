-- ============================================================================
-- Custom Access Token Auth Hook for RBAC
-- ============================================================================
-- This migration creates the auth hook function that injects user roles
-- into JWT tokens as custom claims.
--
-- Purpose:
-- - Runs automatically before JWT issuance
-- - Fetches user's roles from user_roles table
-- - Injects roles as 'user_role' and 'user_roles' claims
-- - Enables client-side and RLS policy role checking
--
-- Setup After Migration:
-- 1. Go to Supabase Dashboard → Authentication → Hooks
-- 2. Enable "Custom Access Token Hook"
-- 3. Select: public.custom_access_token_hook
--
-- ============================================================================

-- ============================================================================
-- HELPER FUNCTION TO GET USER ROLES (bypasses RLS issues)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_user_roles_for_hook(check_user_id UUID)
RETURNS text[]
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN (
        SELECT ARRAY_AGG(role::text ORDER BY assigned_at)
        FROM public.user_roles
        WHERE user_id = check_user_id
    );
END;
$$;

COMMENT ON FUNCTION public.get_user_roles_for_hook(UUID) IS 'Helper function for auth hook to fetch user roles, bypassing RLS.';

GRANT EXECUTE ON FUNCTION public.get_user_roles_for_hook TO supabase_auth_admin;

-- ============================================================================
-- CREATE CUSTOM ACCESS TOKEN HOOK FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION public.custom_access_token_hook(event jsonb)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    claims jsonb;
    user_roles_array text[];
    primary_role public.app_role;
BEGIN
    -- Extract claims from the event
    claims := event->'claims';

    -- Use helper function to fetch all roles for the user (bypasses RLS)
    user_roles_array := public.get_user_roles_for_hook((event->>'user_id')::uuid);

    -- Set primary role (first role, or 'user' if none)
    IF user_roles_array IS NOT NULL AND array_length(user_roles_array, 1) > 0 THEN
        primary_role := user_roles_array[1]::public.app_role;
    ELSE
        primary_role := 'user'::public.app_role;
    END IF;

    -- Inject custom claims
    IF user_roles_array IS NOT NULL THEN
        -- Set primary role claim (for simple checks)
        claims := jsonb_set(claims, '{user_role}', to_jsonb(primary_role::text));

        -- Set all roles claim (for multi-role checks)
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(user_roles_array));

        -- Set is_admin flag (convenient for quick checks)
        IF 'admin' = ANY(user_roles_array) THEN
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(true));
        ELSE
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
        END IF;
    ELSE
        -- User has no roles, set defaults (FIXED: explicit type casting)
        claims := jsonb_set(claims, '{user_role}', to_jsonb('user'::text));
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(ARRAY['user']::text[]));
        claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
    END IF;

    -- Update the event with modified claims
    event := jsonb_set(event, '{claims}', claims);

    RETURN event;
END;
$$;

COMMENT ON FUNCTION public.custom_access_token_hook(jsonb) IS 'Auth hook that injects user roles into JWT claims before token issuance.';

-- ============================================================================
-- GRANT PERMISSIONS TO SUPABASE AUTH
-- ============================================================================

-- Allow supabase_auth_admin to use the public schema
GRANT USAGE ON SCHEMA public TO supabase_auth_admin;

-- Allow execution of the hook function
GRANT EXECUTE ON FUNCTION public.custom_access_token_hook TO supabase_auth_admin;

-- Revoke from regular users (security)
REVOKE EXECUTE ON FUNCTION public.custom_access_token_hook FROM authenticated, anon, public;

-- Grant read access to user_roles table for auth admin
GRANT ALL ON TABLE public.user_roles TO supabase_auth_admin;

-- Revoke direct access from regular users (RLS will control access)
REVOKE ALL ON TABLE public.user_roles FROM authenticated, anon, public;

-- ============================================================================
-- CREATE RLS POLICY FOR AUTH ADMIN ACCESS
-- ============================================================================

-- Allow auth admin to read all user roles (needed for the hook)
CREATE POLICY "Allow auth admin to read user roles"
    ON public.user_roles
    AS PERMISSIVE
    FOR SELECT
    TO supabase_auth_admin
    USING (true);

-- ============================================================================
-- TESTING THE HOOK
-- ============================================================================

-- To test the hook after enabling it in the dashboard:
--
-- 1. Sign in as a user
-- 2. Decode the JWT access token
-- 3. Check for these claims:
--    - user_role: "user" or "admin"
--    - user_roles: ["user"] or ["user", "admin"]
--    - is_admin: true or false
--
-- Example in JavaScript:
--   import { jwtDecode } from 'jwt-decode';
--   const jwt = jwtDecode(session.access_token);
--   console.log('Role:', jwt.user_role);
--   console.log('All roles:', jwt.user_roles);
--   console.log('Is admin:', jwt.is_admin);
--
-- Example in Kotlin:
--   val jwt = JWT(session.accessToken)
--   val role = jwt.getClaim("user_role").asString()
--   val roles = jwt.getClaim("user_roles").asList(String::class.java)
--   val isAdmin = jwt.getClaim("is_admin").asBoolean()
--
-- ============================================================================

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
--
-- ✅ Auth hook function created
-- ✅ Permissions granted
-- ✅ RLS policy created
--
-- Next Steps:
-- 1. Deploy this migration: `supabase db push`
-- 2. Enable hook in Dashboard: Auth → Hooks → Custom Access Token Hook
-- 3. Test JWT claims with a new login
--
-- ============================================================================
