-- ============================================================================
-- FORCE FIX: get_all_roles() and get_all_permissions() to use correct response format
-- ============================================================================
-- Issue: Functions are returning old format with 'roles' key instead of 'data' key
-- Expected: {"success": true, "data": [...]}
-- Actual: {"roles": [...], "success": true}
-- ============================================================================

-- Drop and recreate get_all_roles with correct format
DROP FUNCTION IF EXISTS public.get_all_roles() CASCADE;

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
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Check if user is admin
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Get all roles from the roles table
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

    -- Return with 'data' key (not 'roles')
    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_roles, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_roles() IS 'Returns all roles with full metadata in format: {"success": true, "data": [...]}';

-- Drop and recreate get_all_permissions with correct format
DROP FUNCTION IF EXISTS public.get_all_permissions() CASCADE;

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
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Check if user is admin
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;

    -- Get all permissions from the permissions table
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

    -- Return with 'data' key (not 'permissions')
    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_permissions, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_all_permissions() IS 'Returns all permissions with full metadata in format: {"success": true, "data": [...]}';

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION public.get_all_roles() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_all_permissions() TO authenticated;
