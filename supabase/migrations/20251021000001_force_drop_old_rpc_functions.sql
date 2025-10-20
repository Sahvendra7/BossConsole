-- ============================================================================
-- FORCE DROP AND RECREATE: All RPC Functions (Table-based Schema)
-- ============================================================================
--
-- Purpose: Force drop old ENUM-based functions and recreate with table schema
--
-- Issue: Old ENUM functions from 20251019 are still being called despite
--        later migrations attempting to recreate them
--
-- Solution: DROP IF EXISTS with CASCADE, then CREATE OR REPLACE
--
-- Created: 2025-01-21
-- ============================================================================

-- ============================================================================
-- 1. FORCE DROP ALL OLD FUNCTIONS
-- ============================================================================

DROP FUNCTION IF EXISTS public.get_all_roles() CASCADE;
DROP FUNCTION IF EXISTS public.get_all_permissions() CASCADE;
DROP FUNCTION IF EXISTS public.get_role_permissions(TEXT) CASCADE;

-- ============================================================================
-- 2. RECREATE: get_all_roles (table-based schema)
-- ============================================================================

CREATE FUNCTION public.get_all_roles()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_roles JSONB;
BEGIN
    -- Get current user ID
    v_user_id := auth.uid();

    -- Check authentication
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Check admin permission
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Query roles table with all metadata
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
$$;

COMMENT ON FUNCTION public.get_all_roles() IS 'Returns all roles with table-based metadata';

-- ============================================================================
-- 3. RECREATE: get_all_permissions (table-based schema)
-- ============================================================================

CREATE FUNCTION public.get_all_permissions()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_permissions JSONB;
BEGIN
    -- Get current user ID
    v_user_id := auth.uid();

    -- Check authentication
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Check admin permission
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Query permissions table with all metadata
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
$$;

COMMENT ON FUNCTION public.get_all_permissions() IS 'Returns all permissions with table-based metadata';

-- ============================================================================
-- 4. RECREATE: get_role_permissions (table-based schema)
-- ============================================================================

CREATE FUNCTION public.get_role_permissions(role_name TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permissions JSONB;
BEGIN
    -- Get current user ID
    v_user_id := auth.uid();

    -- Check authentication
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Check admin permission
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Get role ID from name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" does not exist', role_name));
    END IF;

    -- Query permissions via role_permissions table (JOIN)
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
$$;

COMMENT ON FUNCTION public.get_role_permissions(TEXT) IS 'Returns permissions for a role using table-based schema';

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '✅ FORCED RECREATION COMPLETE';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE '✅ Old ENUM functions dropped';
    RAISE NOTICE '✅ New table-based functions created';
    RAISE NOTICE '';
    RAISE NOTICE 'Functions recreated:';
    RAISE NOTICE '  1. get_all_roles()';
    RAISE NOTICE '  2. get_all_permissions()';
    RAISE NOTICE '  3. get_role_permissions(role_name)';
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
END $$;
