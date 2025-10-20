-- ============================================================================
-- Fix Auth Hook Table Reference After Cutover
-- ============================================================================
--
-- Purpose: Update auth hook to use renamed tables after cutover
--
-- Issue: Phase 3 (update_auth_hook) created get_user_roles_for_hook()
--        referencing user_roles_new, but Phase 5 (cutover) renamed it to user_roles.
--
-- Solution: Recreate get_user_roles_for_hook() with correct table reference
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 10)
-- ============================================================================

-- ============================================================================
-- Update get_user_roles_for_hook to use renamed user_roles table
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
        FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id
    );
END;
$$;

COMMENT ON FUNCTION public.get_user_roles_for_hook(UUID) IS 'Helper function for auth hook to fetch user roles from table-based schema, bypassing RLS.';

-- Grant permissions (refresh grants after function update)
GRANT EXECUTE ON FUNCTION public.get_user_roles_for_hook TO supabase_auth_admin;
GRANT ALL ON TABLE public.user_roles TO supabase_auth_admin;
GRANT ALL ON TABLE public.roles TO supabase_auth_admin;

-- ============================================================================
-- Update RLS policy for auth admin (if old policy exists for _new table)
-- ============================================================================

-- Drop old policy if it exists (from migration that referenced user_roles_new)
DROP POLICY IF EXISTS "Allow auth admin to read user roles (new)" ON public.user_roles;

-- Drop and recreate the policy to ensure it's correct
DROP POLICY IF EXISTS "Allow auth admin to read user roles" ON public.user_roles;

-- Create fresh policy for auth admin to read user_roles
CREATE POLICY "Allow auth admin to read user roles"
    ON public.user_roles
    AS PERMISSIVE
    FOR SELECT
    TO supabase_auth_admin
    USING (true);

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
DECLARE
    v_test_user_id UUID := '9e6af4d5-81ec-44fc-aba7-308223396fb1';
    v_roles text[];
BEGIN
    RAISE NOTICE '✅ Auth hook updated to use renamed tables';
    RAISE NOTICE '   - get_user_roles_for_hook() → user_roles';
    RAISE NOTICE '   - RLS policy updated for supabase_auth_admin';
    RAISE NOTICE '';

    -- Test the function
    SELECT public.get_user_roles_for_hook(v_test_user_id) INTO v_roles;

    RAISE NOTICE '🔍 Test query for user 9e6af4d5-81ec-44fc-aba7-308223396fb1:';
    RAISE NOTICE '   Roles: %', v_roles;
    RAISE NOTICE '';
    RAISE NOTICE '✅ Auth hook will now correctly fetch roles on login';
END $$;
