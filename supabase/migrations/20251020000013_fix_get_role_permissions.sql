-- ============================================================================
-- Fix get_role_permissions Function
-- ============================================================================
--
-- Purpose: Recreate get_role_permissions to use table-based schema
--
-- Issue: Old ENUM-based version was being reapplied, referencing nonexistent
--        'permission' column instead of joining with permissions table
--
-- Solution: Recreate function with correct table joins
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 13)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_role_permissions(role_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
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

    -- Get role ID
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Role "%s" does not exist', role_name)
        );
    END IF;

    -- Query permissions for the role (JOIN with permissions table)
    SELECT jsonb_agg(p.name ORDER BY p.name)
    INTO v_permissions
    FROM public.role_permissions rp
    JOIN public.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = v_role_id;

    -- Return permissions
    RETURN jsonb_build_object(
        'success', true,
        'role', role_name,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', SQLERRM
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_role_permissions(TEXT) IS 'Returns all permissions assigned to a role using table-based schema (admin only)';

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
DECLARE
    v_test_result JSONB;
BEGIN
    RAISE NOTICE '✅ get_role_permissions() updated to use table-based schema';
    RAISE NOTICE '   - Now queries role_permissions table';
    RAISE NOTICE '   - JOINs with permissions table to get permission names';
    RAISE NOTICE '';

    -- Test the function
    SELECT public.get_role_permissions('admin') INTO v_test_result;

    RAISE NOTICE '🔍 Test query for "admin" role:';
    RAISE NOTICE '   success: %', v_test_result->>'success';
    RAISE NOTICE '   permissions count: %', jsonb_array_length(v_test_result->'permissions');
END $$;
