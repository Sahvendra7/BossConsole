-- Migration: Dynamic Role and Permission Management
-- Created: 2025-01-19
-- Purpose: Enable admins to create new roles and permissions dynamically at runtime
--
-- Note: This migration uses the existing is_user_admin(UUID) function
-- from the 20251018000000_create_rbac_system.sql migration

-- ============================================================================
-- RPC Function 1: Create New Role
-- ============================================================================
CREATE OR REPLACE FUNCTION public.create_new_role(
    role_name TEXT,
    description TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
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

    -- Validate role name format (lowercase, alphanumeric + underscore, 3-50 chars)
    IF NOT (role_name ~ '^[a-z][a-z0-9_]{2,50}$') THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Invalid role name format. Must be lowercase, start with letter, 3-50 characters, alphanumeric + underscore only'
        );
    END IF;

    -- Check for reserved role names
    IF role_name IN ('user', 'admin', 'authenticated', 'anon', 'service_role', 'postgres') THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Role name is reserved and cannot be used'
        );
    END IF;

    -- Execute ALTER TYPE to add new enum value
    BEGIN
        EXECUTE format('ALTER TYPE public.app_role ADD VALUE IF NOT EXISTS %L', role_name);
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Failed to create role: %s', SQLERRM)
        );
    END;

    -- Return success
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Role "%s" created successfully', role_name),
        'role', role_name,
        'description', description
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 2: Create New Permission
-- ============================================================================
CREATE OR REPLACE FUNCTION public.create_new_permission(
    permission_name TEXT,
    description TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
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

    -- Validate permission name format (domain.action pattern)
    IF NOT (permission_name ~ '^[a-z][a-z0-9_]{1,30}\.[a-z][a-z0-9_]{1,30}$') THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Invalid permission format. Must be domain.action (e.g., "users.read"), lowercase, alphanumeric + underscore'
        );
    END IF;

    -- Execute ALTER TYPE to add new enum value
    BEGIN
        EXECUTE format('ALTER TYPE public.app_permission ADD VALUE IF NOT EXISTS %L', permission_name);
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Failed to create permission: %s', SQLERRM)
        );
    END;

    -- Return success
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" created successfully', permission_name),
        'permission', permission_name,
        'description', description
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 3: Get All Roles
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_all_roles()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_is_admin BOOLEAN;
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

    -- Query all enum values from app_role
    SELECT jsonb_agg(
        jsonb_build_object(
            'name', enumlabel,
            'ordinal', enumsortorder
        )
        ORDER BY enumsortorder
    ) INTO v_roles
    FROM pg_enum
    WHERE enumtypid = 'public.app_role'::regtype;

    -- Return roles
    RETURN jsonb_build_object(
        'success', true,
        'roles', COALESCE(v_roles, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 4: Get All Permissions
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_all_permissions()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_is_admin BOOLEAN;
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

    -- Query all enum values from app_permission
    SELECT jsonb_agg(
        jsonb_build_object(
            'name', enumlabel,
            'ordinal', enumsortorder
        )
        ORDER BY enumsortorder
    ) INTO v_permissions
    FROM pg_enum
    WHERE enumtypid = 'public.app_permission'::regtype;

    -- Return permissions
    RETURN jsonb_build_object(
        'success', true,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 5: Assign Permission to Role
-- ============================================================================
CREATE OR REPLACE FUNCTION public.assign_permission_to_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_is_admin BOOLEAN;
    v_role_exists BOOLEAN;
    v_permission_exists BOOLEAN;
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

    -- Verify role exists
    SELECT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumtypid = 'public.app_role'::regtype
        AND enumlabel = role_name
    ) INTO v_role_exists;

    IF NOT v_role_exists THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Role "%s" does not exist', role_name)
        );
    END IF;

    -- Verify permission exists
    SELECT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumtypid = 'public.app_permission'::regtype
        AND enumlabel = permission_name
    ) INTO v_permission_exists;

    IF NOT v_permission_exists THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" does not exist', permission_name)
        );
    END IF;

    -- Insert into role_permissions (ON CONFLICT DO NOTHING handles duplicates)
    INSERT INTO public.role_permissions (id, role, permission, created_at)
    VALUES (
        gen_random_uuid(),
        role_name::public.app_role,
        permission_name::public.app_permission,
        now()
    )
    ON CONFLICT (role, permission) DO NOTHING;

    -- Return success
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" assigned to role "%s"', permission_name, role_name),
        'role', role_name,
        'permission', permission_name
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', format('Failed to assign permission: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 6: Remove Permission from Role
-- ============================================================================
CREATE OR REPLACE FUNCTION public.remove_permission_from_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_is_admin BOOLEAN;
    v_deleted_count INT;
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

    -- Delete from role_permissions
    DELETE FROM public.role_permissions
    WHERE role = role_name::public.app_role
    AND permission = permission_name::public.app_permission;

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    IF v_deleted_count = 0 THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" is not assigned to role "%s"', permission_name, role_name)
        );
    END IF;

    -- Return success
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" removed from role "%s"', permission_name, role_name),
        'role', role_name,
        'permission', permission_name
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', format('Failed to remove permission: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- RPC Function 7: Get Role Permissions
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_role_permissions(role_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_is_admin BOOLEAN;
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

    -- Query permissions for the role
    SELECT jsonb_agg(permission::text ORDER BY permission::text)
    INTO v_permissions
    FROM public.role_permissions
    WHERE role = role_name::public.app_role;

    -- Return permissions
    RETURN jsonb_build_object(
        'success', true,
        'role', role_name,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', format('Failed to get role permissions: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

-- ============================================================================
-- Grants
-- ============================================================================
-- Allow authenticated users to call these functions (admin check is inside)
GRANT EXECUTE ON FUNCTION public.is_user_admin(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_new_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_new_permission(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_all_roles() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_all_permissions() TO authenticated;
GRANT EXECUTE ON FUNCTION public.assign_permission_to_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_permission_from_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_role_permissions(TEXT) TO authenticated;
