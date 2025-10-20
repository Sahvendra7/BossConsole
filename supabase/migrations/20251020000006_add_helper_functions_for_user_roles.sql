-- ============================================================================
-- Helper Functions for User Role Management Plugin
-- ============================================================================
--
-- These functions provide backward-compatible interfaces for querying user
-- roles and permissions after the ENUM-to-table migration.
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 6)
--
-- Purpose:
-- - Maintain compatibility with existing RoleService.kt queries
-- - Return data in expected format (with role/permission names, not UUIDs)
-- - Avoid requiring changes to all client-side data models
--
-- Functions:
-- 1. get_user_roles_with_names() - Get user roles with role names
-- 2. check_user_has_role() - Check if user has specific role
-- 3. get_role_permissions_with_names() - Get role permissions with names
--
-- ============================================================================

-- ============================================================================
-- 1. Get User Roles with Role Names
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_user_roles_with_names(target_user_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_roles JSONB;
BEGIN
    -- Query user_roles table and JOIN with roles to get role names
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', ur.id::text,
            'user_id', ur.user_id::text,
            'role', r.name,
            'assigned_by', ur.assigned_by::text,
            'assigned_at', ur.assigned_at::text,
            'created_at', ur.created_at::text
        )
        ORDER BY ur.assigned_at
    ) INTO v_roles
    FROM public.user_roles ur
    JOIN public.roles r ON r.id = ur.role_id
    WHERE ur.user_id = target_user_id;

    RETURN COALESCE(v_roles, '[]'::jsonb);
END;
$$;

COMMENT ON FUNCTION public.get_user_roles_with_names IS
'Returns user roles with role names (not UUIDs) for backward compatibility with RoleService.kt';

-- Grant execute to authenticated users (RLS will filter by user)
GRANT EXECUTE ON FUNCTION public.get_user_roles_with_names TO authenticated;

-- ============================================================================
-- 2. Check if User Has Role
-- ============================================================================

CREATE OR REPLACE FUNCTION public.check_user_has_role(
    target_user_id UUID,
    role_name TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_role_id UUID;
    v_has_role BOOLEAN;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Check if user has this role
    SELECT EXISTS (
        SELECT 1
        FROM public.user_roles
        WHERE user_id = target_user_id
        AND role_id = v_role_id
    ) INTO v_has_role;

    RETURN v_has_role;
END;
$$;

COMMENT ON FUNCTION public.check_user_has_role IS
'Checks if a user has a specific role by name (backward compatible with RoleService.kt)';

-- Grant execute to authenticated users
GRANT EXECUTE ON FUNCTION public.check_user_has_role TO authenticated;

-- ============================================================================
-- 3. Get Role Permissions with Names
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_role_permissions_with_names(role_name TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_role_id UUID;
    v_permissions JSONB;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN '[]'::jsonb;
    END IF;

    -- Query role_permissions and JOIN with permissions to get permission names
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', rp.id::text,
            'role', role_name,
            'permission', p.name,
            'created_at', rp.created_at::text
        )
        ORDER BY p.name
    ) INTO v_permissions
    FROM public.role_permissions rp
    JOIN public.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = v_role_id;

    RETURN COALESCE(v_permissions, '[]'::jsonb);
END;
$$;

COMMENT ON FUNCTION public.get_role_permissions_with_names IS
'Returns role permissions with names (not UUIDs) for backward compatibility with RoleService.kt';

-- Grant execute to authenticated users
GRANT EXECUTE ON FUNCTION public.get_role_permissions_with_names TO authenticated;

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '✅ Helper functions created successfully';
    RAISE NOTICE '   - get_user_roles_with_names()';
    RAISE NOTICE '   - check_user_has_role()';
    RAISE NOTICE '   - get_role_permissions_with_names()';
END $$;
