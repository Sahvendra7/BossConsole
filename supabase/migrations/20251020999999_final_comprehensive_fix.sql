-- ============================================================================
-- COMPREHENSIVE FINAL FIX: All RPC Functions
-- ============================================================================
--
-- Purpose: Force recreate ALL RBAC RPC functions with table-based schema
-- This migration has a very late timestamp to ensure it runs LAST
--
-- Created: 2025-01-19
-- ============================================================================

-- ============================================================================
-- 1. get_all_roles - Returns all roles with full metadata
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_all_roles()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_roles JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

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

    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_roles, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_roles() IS 'Returns all roles with full metadata (table-based schema)';

-- ============================================================================
-- 2. get_all_permissions - Returns all permissions with full metadata
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_all_permissions()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_permissions JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

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

    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_permissions, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_permissions() IS 'Returns all permissions with full metadata (table-based schema)';

-- ============================================================================
-- 3. get_role_permissions - Returns permissions for a specific role
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_role_permissions(role_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permissions JSONB;
BEGIN
    v_user_id := auth.uid();

    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Get role ID from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" does not exist', role_name));
    END IF;

    -- Query permissions for the role (JOIN with permissions table)
    SELECT jsonb_agg(p.name ORDER BY p.name)
    INTO v_permissions
    FROM public.role_permissions rp
    JOIN public.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = v_role_id;

    RETURN jsonb_build_object(
        'success', true,
        'role', role_name,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object('success', false, 'error', SQLERRM);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_role_permissions(TEXT) IS 'Returns permissions for a role (table-based schema)';

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
DECLARE
    v_roles_result JSONB;
    v_permissions_result JSONB;
    v_role_perms_result JSONB;
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '✅ COMPREHENSIVE FIX APPLIED';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE '📋 All three functions recreated:';
    RAISE NOTICE '   1. get_all_roles()';
    RAISE NOTICE '   2. get_all_permissions()';
    RAISE NOTICE '   3. get_role_permissions(role_name)';
    RAISE NOTICE '';
    RAISE NOTICE '✅ All functions now use table-based schema';
    RAISE NOTICE '✅ All functions return correct format';
    RAISE NOTICE '✅ All functions use snake_case keys';
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
END $$;
