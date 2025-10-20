-- ============================================================================
-- BOSS RBAC Migration: CUTOVER - Activate New Table-Based Schema
-- ============================================================================
-- Phase 5 (CUTOVER): Rename tables to activate new schema
--
-- ⚠️  CRITICAL MIGRATION - READ BEFORE EXECUTING ⚠️
--
-- This migration performs the cutover from ENUM-based to table-based schema.
-- It renames tables to activate the new structure.
--
-- Prerequisites (MUST BE COMPLETED FIRST):
-- ✅ Phase 1: 20251020_migrate_roles_to_tables.sql
-- ✅ Phase 2: 20251020_update_rbac_functions.sql
-- ✅ Phase 3: 20251020_update_auth_hook.sql
-- ✅ Verification: Test auth hook produces correct JWT claims
-- ✅ Verification: Test RPC functions work with new tables
-- ✅ Verification: Confirm data counts match between old and new tables
--
-- What This Migration Does:
-- 1. Rename old tables to *_old (backup)
-- 2. Rename new tables to their final names (activate)
-- 3. Rename indexes to match
-- 4. Update is_user_admin function to use renamed table
-- 5. Revoke access from old tables
--
-- Rollback Plan (if issues occur):
-- - Rename tables back: ALTER TABLE user_roles_old RENAME TO user_roles;
-- - Revert auth hook to use old tables
-- - Keep new tables as *_new for debugging
--
-- ============================================================================

-- ============================================================================
-- 1. PRE-CUTOVER VERIFICATION
-- ============================================================================

DO $$
DECLARE
    v_user_roles_old_count INT;
    v_user_roles_new_count INT;
    v_role_permissions_old_count INT;
    v_role_permissions_new_count INT;
BEGIN
    -- Count records in old vs new tables
    SELECT COUNT(*) INTO v_user_roles_old_count FROM public.user_roles;
    SELECT COUNT(*) INTO v_user_roles_new_count FROM public.user_roles_new;
    SELECT COUNT(*) INTO v_role_permissions_old_count FROM public.role_permissions;
    SELECT COUNT(*) INTO v_role_permissions_new_count FROM public.role_permissions_new;

    RAISE NOTICE '📊 Pre-Cutover Verification:';
    RAISE NOTICE '  user_roles: old=%, new=%', v_user_roles_old_count, v_user_roles_new_count;
    RAISE NOTICE '  role_permissions: old=%, new=%', v_role_permissions_old_count, v_role_permissions_new_count;

    -- Halt if counts don't match
    IF v_user_roles_old_count != v_user_roles_new_count THEN
        RAISE EXCEPTION 'CUTOVER ABORTED: user_roles count mismatch (old=%, new=%)',
            v_user_roles_old_count, v_user_roles_new_count;
    END IF;

    IF v_role_permissions_old_count != v_role_permissions_new_count THEN
        RAISE EXCEPTION 'CUTOVER ABORTED: role_permissions count mismatch (old=%, new=%)',
            v_role_permissions_old_count, v_role_permissions_new_count;
    END IF;

    RAISE NOTICE '✅ Verification passed - proceeding with cutover';
END;
$$;

-- ============================================================================
-- 2. RENAME OLD TABLES (BACKUP)
-- ============================================================================

-- Rename old user_roles table
ALTER TABLE IF EXISTS public.user_roles RENAME TO user_roles_old;

-- Rename old role_permissions table
ALTER TABLE IF EXISTS public.role_permissions RENAME TO role_permissions_old;

-- Rename old indexes (to avoid conflicts)
ALTER INDEX IF EXISTS idx_user_roles_user_id RENAME TO idx_user_roles_old_user_id;
ALTER INDEX IF EXISTS idx_user_roles_role RENAME TO idx_user_roles_old_role;
ALTER INDEX IF EXISTS idx_user_roles_assigned_by RENAME TO idx_user_roles_old_assigned_by;
ALTER INDEX IF EXISTS idx_role_permissions_role RENAME TO idx_role_permissions_old_role;
ALTER INDEX IF EXISTS idx_role_permissions_permission RENAME TO idx_role_permissions_old_permission;

-- Rename old policies (to avoid conflicts)
DO $$
BEGIN
    -- User roles policies
    ALTER POLICY "Users can view their own roles" ON public.user_roles_old RENAME TO "Users can view their own roles (old)";
    ALTER POLICY "Admins can view all roles" ON public.user_roles_old RENAME TO "Admins can view all roles (old)";
    ALTER POLICY "Admins can assign roles" ON public.user_roles_old RENAME TO "Admins can assign roles (old)";
    ALTER POLICY "Admins can remove roles" ON public.user_roles_old RENAME TO "Admins can remove roles (old)";
    ALTER POLICY "Service role full access to user_roles" ON public.user_roles_old RENAME TO "Service role full access to user_roles (old)";
    ALTER POLICY "Allow auth admin to read user roles" ON public.user_roles_old RENAME TO "Allow auth admin to read user roles (old)";

    -- Role permissions policies
    ALTER POLICY "Anyone can view role permissions" ON public.role_permissions_old RENAME TO "Anyone can view role permissions (old)";
    ALTER POLICY "Admins can manage permissions" ON public.role_permissions_old RENAME TO "Admins can manage permissions (old)";
    ALTER POLICY "Service role full access to role_permissions" ON public.role_permissions_old RENAME TO "Service role full access to role_permissions (old)";
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Note: Some old policies may not exist (this is okay)';
END;
$$;

-- ============================================================================
-- 3. RENAME NEW TABLES (ACTIVATE)
-- ============================================================================

-- Rename new user_roles table (ACTIVATE)
ALTER TABLE public.user_roles_new RENAME TO user_roles;

-- Rename new role_permissions table (ACTIVATE)
ALTER TABLE public.role_permissions_new RENAME TO role_permissions;

-- ============================================================================
-- 4. RENAME NEW INDEXES (FINALIZE)
-- ============================================================================

ALTER INDEX idx_user_roles_new_user_id RENAME TO idx_user_roles_user_id;
ALTER INDEX idx_user_roles_new_role_id RENAME TO idx_user_roles_role_id;
ALTER INDEX idx_role_permissions_new_role_id RENAME TO idx_role_permissions_role_id;
ALTER INDEX idx_role_permissions_new_permission_id RENAME TO idx_role_permissions_permission_id;

-- ============================================================================
-- 5. RENAME NEW POLICIES (FINALIZE)
-- ============================================================================

-- User roles policies
ALTER POLICY "Users can view their own roles (new)" ON public.user_roles RENAME TO "Users can view their own roles";
ALTER POLICY "Admins can view all roles (new)" ON public.user_roles RENAME TO "Admins can view all roles";
ALTER POLICY "Admins can assign roles (new)" ON public.user_roles RENAME TO "Admins can assign roles";
ALTER POLICY "Admins can remove roles (new)" ON public.user_roles RENAME TO "Admins can remove roles";
ALTER POLICY "Service role full access to user_roles_new" ON public.user_roles RENAME TO "Service role full access to user_roles";
ALTER POLICY "Allow auth admin to read user roles (new)" ON public.user_roles RENAME TO "Allow auth admin to read user roles";

-- Role permissions policies
ALTER POLICY "Anyone can view role permissions (new)" ON public.role_permissions RENAME TO "Anyone can view role permissions";
ALTER POLICY "Admins can manage role permissions (new)" ON public.role_permissions RENAME TO "Admins can manage role permissions";
ALTER POLICY "Service role full access to role_permissions_new" ON public.role_permissions RENAME TO "Service role full access to role_permissions";

-- ============================================================================
-- 6. UPDATE HELPER FUNCTIONS TO USE RENAMED TABLES
-- ============================================================================

-- Update is_user_admin to use the renamed user_roles table
CREATE OR REPLACE FUNCTION public.is_user_admin(check_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = 'admin'
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.is_user_admin(UUID) IS 'Check if user is admin using table-based schema.';

-- Update user_has_role to use renamed user_roles table
CREATE OR REPLACE FUNCTION public.user_has_role(
    check_user_id UUID,
    check_role TEXT
)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = check_role
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.user_has_role(UUID, TEXT) IS 'Check if a user has a specific role using table-based schema.';

-- Update get_user_roles to return role names from renamed table
-- Drop first to allow return type change
DROP FUNCTION IF EXISTS public.get_user_roles(UUID);

CREATE OR REPLACE FUNCTION public.get_user_roles(check_user_id UUID)
RETURNS SETOF TEXT AS $$
BEGIN
    RETURN QUERY
    SELECT r.name FROM public.user_roles ur
    JOIN public.roles r ON r.id = ur.role_id
    WHERE ur.user_id = check_user_id
    ORDER BY ur.assigned_at;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_user_roles(UUID) IS 'Returns all role names assigned to a user using table-based schema.';

-- Update get_user_roles_for_hook to use renamed user_roles table
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

COMMENT ON FUNCTION public.get_user_roles_for_hook(UUID) IS 'Helper function for auth hook to fetch user roles using table-based schema, bypassing RLS.';

-- Update authorize function to use renamed tables
CREATE OR REPLACE FUNCTION public.authorize(
    requested_permission TEXT
)
RETURNS BOOLEAN AS $$
DECLARE
    user_role_ids UUID[];
BEGIN
    -- Get all role IDs for the current user
    SELECT ARRAY_AGG(role_id) INTO user_role_ids
    FROM public.user_roles
    WHERE user_id = auth.uid();

    -- Check if any of the user's roles have the requested permission
    RETURN EXISTS (
        SELECT 1 FROM public.role_permissions rp
        JOIN public.permissions p ON p.id = rp.permission_id
        WHERE rp.role_id = ANY(user_role_ids)
        AND p.name = requested_permission
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.authorize(TEXT) IS 'Check if current user has a specific permission via their roles using table-based schema.';

-- Drop the old is_user_admin_new helper (no longer needed)
DROP FUNCTION IF EXISTS public.is_user_admin_new(UUID);

-- ============================================================================
-- 7. REVOKE ACCESS TO OLD TABLES (OPTIONAL - FOR SAFETY)
-- ============================================================================

-- Revoke SELECT permissions from authenticated users on old tables
REVOKE SELECT ON public.user_roles_old FROM authenticated;
REVOKE SELECT ON public.role_permissions_old FROM authenticated;

-- Note: Service role and supabase_auth_admin still have access for emergency rollback

-- ============================================================================
-- 8. POST-CUTOVER VERIFICATION
-- ============================================================================

DO $$
DECLARE
    v_user_roles_count INT;
    v_role_permissions_count INT;
    v_roles_count INT;
    v_permissions_count INT;
    v_test_user_id UUID;
    v_test_admin_check BOOLEAN;
BEGIN
    -- Count records in active tables
    SELECT COUNT(*) INTO v_user_roles_count FROM public.user_roles;
    SELECT COUNT(*) INTO v_role_permissions_count FROM public.role_permissions;
    SELECT COUNT(*) INTO v_roles_count FROM public.roles;
    SELECT COUNT(*) INTO v_permissions_count FROM public.permissions;

    RAISE NOTICE '';
    RAISE NOTICE '✅ Post-Cutover Verification:';
    RAISE NOTICE '  Active tables (now renamed):';
    RAISE NOTICE '    - user_roles: % records', v_user_roles_count;
    RAISE NOTICE '    - role_permissions: % records', v_role_permissions_count;
    RAISE NOTICE '    - roles: % records', v_roles_count;
    RAISE NOTICE '    - permissions: % records', v_permissions_count;
    RAISE NOTICE '';

    -- Test is_user_admin function
    SELECT user_id INTO v_test_user_id FROM public.user_roles LIMIT 1;
    IF v_test_user_id IS NOT NULL THEN
        SELECT public.is_user_admin(v_test_user_id) INTO v_test_admin_check;
        RAISE NOTICE '  Test is_user_admin(): % (user_id: %)', v_test_admin_check, v_test_user_id;
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE '🎉 CUTOVER COMPLETE!';
    RAISE NOTICE '  Old tables backed up as: user_roles_old, role_permissions_old';
    RAISE NOTICE '  New tables now active as: user_roles, role_permissions';
    RAISE NOTICE '  All functions updated to use new schema';
END;
$$;

-- ============================================================================
-- MIGRATION COMPLETE (PHASE 5 - CUTOVER)
-- ============================================================================
--
-- ✅ Old tables renamed to *_old (backup)
-- ✅ New tables renamed to final names (activated)
-- ✅ Indexes renamed
-- ✅ Policies renamed
-- ✅ Helper functions updated
-- ✅ Access revoked from old tables
--
-- 🎉 NEW TABLE-BASED RBAC SCHEMA NOW ACTIVE!
--
-- Next Steps:
-- 1. Phase 6: Update Kotlin code to support full CRUD
-- 2. Phase 7: Test thoroughly:
--    - Create new roles via UI
--    - Assign permissions to roles
--    - Delete custom roles
--    - Verify system roles cannot be deleted
--    - Test JWT claims in authentication
-- 3. Phase 8: Update documentation
--
-- Rollback Instructions (IF ISSUES OCCUR):
--   -- Revert table renames
--   ALTER TABLE public.user_roles RENAME TO user_roles_new;
--   ALTER TABLE public.role_permissions RENAME TO role_permissions_new;
--   ALTER TABLE public.user_roles_old RENAME TO user_roles;
--   ALTER TABLE public.role_permissions_old RENAME TO role_permissions;
--
--   -- Revert is_user_admin function
--   CREATE OR REPLACE FUNCTION public.is_user_admin(check_user_id UUID)
--   RETURNS BOOLEAN AS $$
--   BEGIN
--       RETURN EXISTS (
--           SELECT 1 FROM public.user_roles
--           WHERE user_id = check_user_id AND role = 'admin'
--       );
--   END;
--   $$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';
--
--   -- Then fix any issues and re-run cutover
--
-- Old Tables Cleanup (AFTER CONFIRMING EVERYTHING WORKS):
--   -- Wait 30 days, then remove old tables:
--   DROP TABLE IF EXISTS public.user_roles_old;
--   DROP TABLE IF EXISTS public.role_permissions_old;
--
-- ============================================================================
