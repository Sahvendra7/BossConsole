-- ============================================================================
-- Drop Old ENUM-Based RPC Functions
-- ============================================================================
--
-- Purpose: Remove function overloading conflicts after migration to TEXT-based schema
--
-- Issue: The update_rbac_functions migration created TEXT-based versions of functions,
--        but the old ENUM-based versions still exist, causing ambiguity.
--
-- Solution: Explicitly drop the old ENUM-based function signatures
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 7)
-- ============================================================================

-- Drop old ENUM-based assign_role_to_user
DROP FUNCTION IF EXISTS public.assign_role_to_user(UUID, public.app_role);

-- Drop old ENUM-based remove_role_from_user
DROP FUNCTION IF EXISTS public.remove_role_from_user(UUID, public.app_role);

-- Drop old ENUM-based assign_permission_to_role
DROP FUNCTION IF EXISTS public.assign_permission_to_role(public.app_role, public.app_permission);

-- Drop old ENUM-based remove_permission_from_role
DROP FUNCTION IF EXISTS public.remove_permission_from_role(public.app_role, public.app_permission);

-- Verification
DO $$
BEGIN
    RAISE NOTICE '✅ Dropped old ENUM-based RPC functions';
    RAISE NOTICE '   - assign_role_to_user(UUID, app_role)';
    RAISE NOTICE '   - remove_role_from_user(UUID, app_role)';
    RAISE NOTICE '   - assign_permission_to_role(app_role, app_permission)';
    RAISE NOTICE '   - remove_permission_from_role(app_role, app_permission)';
    RAISE NOTICE '';
    RAISE NOTICE '✅ Only TEXT-based functions remain active';
END $$;
