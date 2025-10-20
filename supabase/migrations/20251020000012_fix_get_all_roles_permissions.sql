-- ============================================================================
-- Fix get_all_roles and get_all_permissions Functions
-- ============================================================================
--
-- Purpose: Recreate functions with full table-based metadata
--
-- Issue: Old ENUM-based versions were being reapplied, returning only {name, ordinal}
--        instead of full metadata {id, name, description, isSystem, createdAt, updatedAt}
--
-- Solution: Recreate both functions with correct table queries
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 12)
-- ============================================================================

-- ============================================================================
-- 1. Recreate get_all_roles to return full metadata
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_all_roles()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_roles JSONB;
BEGIN
    -- Get current user ID
    v_user_id := auth.uid();

    -- Check if user is authenticated
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Not authenticated'
        );
    END IF;

    -- Check if user is admin
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Permission denied: Admin role required'
        );
    END IF;

    -- Query all roles from roles table with full metadata
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY name
    ) INTO v_roles
    FROM public.roles;

    RETURN jsonb_build_object(
        'success', true,
        'data', COALESCE(v_roles, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_roles() IS 'Returns all roles from table-based schema with full metadata (admin only)';

-- ============================================================================
-- 2. Recreate get_all_permissions to return full metadata
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_all_permissions()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_permissions JSONB;
BEGIN
    -- Get current user ID
    v_user_id := auth.uid();

    -- Check if user is authenticated
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Not authenticated'
        );
    END IF;

    -- Check if user is admin
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Permission denied: Admin role required'
        );
    END IF;

    -- Query all permissions from permissions table with full metadata
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY name
    ) INTO v_permissions
    FROM public.permissions;

    RETURN jsonb_build_object(
        'success', true,
        'data', COALESCE(v_permissions, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_permissions() IS 'Returns all permissions from table-based schema with full metadata (admin only)';

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
DECLARE
    v_test_result JSONB;
BEGIN
    RAISE NOTICE '✅ Functions recreated with full metadata:';
    RAISE NOTICE '   - get_all_roles() → returns {id, name, description, isSystem, createdAt, updatedAt}';
    RAISE NOTICE '   - get_all_permissions() → returns {id, name, description, isSystem, createdAt, updatedAt}';
    RAISE NOTICE '';
    RAISE NOTICE '🔍 Sample role data:';

    SELECT jsonb_build_object(
        'id', id,
        'name', name,
        'description', description,
        'is_system', is_system,
        'created_at', created_at,
        'updated_at', updated_at
    ) INTO v_test_result
    FROM public.roles
    LIMIT 1;

    RAISE NOTICE '   %', v_test_result;
END $$;
