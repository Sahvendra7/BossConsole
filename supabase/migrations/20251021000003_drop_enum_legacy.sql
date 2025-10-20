-- ============================================================================
-- BOSS RBAC Cleanup: Drop Legacy ENUMs and Backup Tables
-- ============================================================================
--
-- Purpose: Remove legacy ENUM-based schema after successful table migration
--
-- What This Does:
-- 1. Drop backup tables (user_roles_old, role_permissions_old)
-- 2. Drop ENUM types (app_role, app_permission)
-- 3. Verify active schema is still intact
--
-- Why This Is Safe:
-- - Cutover to table-based schema completed 30+ days ago
-- - All active tables use UUIDs (role_id, permission_id)
-- - No database functions reference ENUMs anymore
-- - Kotlin enums remain for backward compatibility
--
-- Rollback:
-- - Cannot rollback - ENUMs and old data will be permanently deleted
-- - If issues occur, use table-based schema (already active)
--
-- ============================================================================

-- ============================================================================
-- 1. PRE-CLEANUP VERIFICATION
-- ============================================================================

DO $$
DECLARE
    v_active_user_roles_count INT;
    v_active_role_permissions_count INT;
    v_backup_user_roles_count INT;
    v_backup_role_permissions_count INT;
    v_roles_count INT;
    v_permissions_count INT;
BEGIN
    -- Verify active table-based schema exists and has data
    SELECT COUNT(*) INTO v_active_user_roles_count FROM public.user_roles;
    SELECT COUNT(*) INTO v_active_role_permissions_count FROM public.role_permissions;
    SELECT COUNT(*) INTO v_roles_count FROM public.roles;
    SELECT COUNT(*) INTO v_permissions_count FROM public.permissions;

    -- Count records in backup tables (if they exist)
    SELECT COUNT(*) INTO v_backup_user_roles_count FROM public.user_roles_old;
    SELECT COUNT(*) INTO v_backup_role_permissions_count FROM public.role_permissions_old;

    RAISE NOTICE '';
    RAISE NOTICE '📊 Pre-Cleanup Verification:';
    RAISE NOTICE '  Active tables (table-based schema):';
    RAISE NOTICE '    - user_roles: % records', v_active_user_roles_count;
    RAISE NOTICE '    - role_permissions: % records', v_active_role_permissions_count;
    RAISE NOTICE '    - roles: % records', v_roles_count;
    RAISE NOTICE '    - permissions: % records', v_permissions_count;
    RAISE NOTICE '';
    RAISE NOTICE '  Backup tables (ENUM-based schema - to be deleted):';
    RAISE NOTICE '    - user_roles_old: % records', v_backup_user_roles_count;
    RAISE NOTICE '    - role_permissions_old: % records', v_backup_role_permissions_count;
    RAISE NOTICE '';

    -- Safety check: Ensure active schema has data
    IF v_active_user_roles_count = 0 THEN
        RAISE EXCEPTION 'CLEANUP ABORTED: Active user_roles table is empty!';
    END IF;

    IF v_roles_count = 0 THEN
        RAISE EXCEPTION 'CLEANUP ABORTED: Active roles table is empty!';
    END IF;

    RAISE NOTICE '✅ Verification passed - active schema has data';
    RAISE NOTICE '';
END;
$$;

-- ============================================================================
-- 2. DROP BACKUP TABLES
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '🗑️  Dropping backup tables...';
END;
$$;

-- Drop backup user_roles table (uses app_role ENUM)
DROP TABLE IF EXISTS public.user_roles_old CASCADE;

-- Drop backup role_permissions table (uses app_role and app_permission ENUMs)
DROP TABLE IF EXISTS public.role_permissions_old CASCADE;

DO $$
BEGIN
    RAISE NOTICE '  ✅ Dropped user_roles_old';
    RAISE NOTICE '  ✅ Dropped role_permissions_old';
END;
$$;

-- ============================================================================
-- 3. DROP ENUM TYPES
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '🗑️  Dropping ENUM types...';
END;
$$;

-- Drop app_permission ENUM (no longer referenced by any tables)
DROP TYPE IF EXISTS public.app_permission CASCADE;

-- Drop app_role ENUM (no longer referenced by any tables)
DROP TYPE IF EXISTS public.app_role CASCADE;

DO $$
BEGIN
    RAISE NOTICE '  ✅ Dropped app_permission ENUM';
    RAISE NOTICE '  ✅ Dropped app_role ENUM';
END;
$$;

-- ============================================================================
-- 4. POST-CLEANUP VERIFICATION
-- ============================================================================

DO $$
DECLARE
    v_user_roles_count INT;
    v_role_permissions_count INT;
    v_roles_count INT;
    v_permissions_count INT;
    v_enum_exists BOOLEAN;
BEGIN
    -- Verify active tables still exist and have data
    SELECT COUNT(*) INTO v_user_roles_count FROM public.user_roles;
    SELECT COUNT(*) INTO v_role_permissions_count FROM public.role_permissions;
    SELECT COUNT(*) INTO v_roles_count FROM public.roles;
    SELECT COUNT(*) INTO v_permissions_count FROM public.permissions;

    -- Verify ENUMs are gone
    SELECT EXISTS (
        SELECT 1 FROM pg_type WHERE typname IN ('app_role', 'app_permission')
    ) INTO v_enum_exists;

    RAISE NOTICE '';
    RAISE NOTICE '✅ Post-Cleanup Verification:';
    RAISE NOTICE '  Active tables (still intact):';
    RAISE NOTICE '    - user_roles: % records', v_user_roles_count;
    RAISE NOTICE '    - role_permissions: % records', v_role_permissions_count;
    RAISE NOTICE '    - roles: % records', v_roles_count;
    RAISE NOTICE '    - permissions: % records', v_permissions_count;
    RAISE NOTICE '';
    RAISE NOTICE '  Legacy artifacts:';
    RAISE NOTICE '    - ENUMs exist: %', v_enum_exists;
    RAISE NOTICE '    - user_roles_old: DELETED';
    RAISE NOTICE '    - role_permissions_old: DELETED';
    RAISE NOTICE '';

    IF v_enum_exists THEN
        RAISE WARNING '⚠️  ENUMs still exist (CASCADE may have failed)';
    ELSE
        RAISE NOTICE '🎉 CLEANUP COMPLETE!';
        RAISE NOTICE '  All legacy ENUM artifacts removed';
        RAISE NOTICE '  Table-based RBAC schema fully active';
    END IF;
END;
$$;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
--
-- ✅ Backup tables deleted (user_roles_old, role_permissions_old)
-- ✅ ENUM types deleted (app_role, app_permission)
-- ✅ Active table-based schema verified intact
--
-- Current Schema (ACTIVE):
-- - roles (id, name, description, is_system, created_at, updated_at)
-- - permissions (id, name, description, is_system, created_at, updated_at)
-- - user_roles (id, user_id, role_id, assigned_by, assigned_at, created_at)
-- - role_permissions (id, role_id, permission_id, created_at)
--
-- Kotlin Enums (UNCHANGED - for code compatibility):
-- - AppRole enum (user, admin) - provides type safety
-- - AppPermission enum (8 permissions) - provides type safety
--
-- ============================================================================
