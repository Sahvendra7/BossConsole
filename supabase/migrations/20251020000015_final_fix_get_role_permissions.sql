-- ============================================================================
-- FINAL FIX: Recreate get_role_permissions
-- ============================================================================
--
-- Purpose: Force recreate get_role_permissions with table-based schema
--
-- Created: 2025-01-19
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

DO $$
BEGIN
    RAISE NOTICE '✅ get_role_permissions() forcibly recreated with table-based schema';
END $$;
