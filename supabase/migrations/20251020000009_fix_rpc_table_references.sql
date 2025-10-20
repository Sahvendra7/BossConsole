-- ============================================================================
-- Fix RPC Function Table References After Cutover
-- ============================================================================
--
-- Purpose: Update RPC functions to use renamed tables after cutover
--
-- Issue: Phase 2 (update_rbac_functions) created functions referencing
--        user_roles_new and role_permissions_new, but Phase 5 (cutover)
--        renamed these tables to user_roles and role_permissions.
--
-- Solution: Recreate all RPC functions with correct table references
--
-- Created: 2025-01-19
-- Part of: ENUM to Table Migration (Phase 9)
-- ============================================================================

-- ============================================================================
-- 1. Update is_user_admin helper function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.is_user_admin(check_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = 'admin'
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- 2. Update delete_role function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_role(role_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_role_id UUID;
    v_is_system BOOLEAN;
    v_user_count INT;
BEGIN
    -- Check if user is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Only admins can delete roles'
        );
    END IF;

    -- Look up role by name
    SELECT id, is_system INTO v_role_id, v_is_system
    FROM public.roles
    WHERE name = role_name;

    -- Check if role exists
    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Role not found'
        );
    END IF;

    -- Check if role is a system role
    IF v_is_system THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Cannot delete system role'
        );
    END IF;

    -- Check how many users have this role
    SELECT COUNT(*) INTO v_user_count
    FROM public.user_roles
    WHERE role_id = v_role_id;

    -- Delete role (CASCADE will remove from user_roles and role_permissions)
    DELETE FROM public.roles WHERE id = v_role_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', format('Role "%s" deleted successfully (was assigned to %s users)', role_name, v_user_count)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- 3. Update assign_role_to_user function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.assign_role_to_user(
    target_user_id UUID,
    target_role TEXT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_role_id UUID;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role % does not exist', target_role;
    END IF;

    -- Insert role into table (or do nothing if already exists)
    INSERT INTO public.user_roles (user_id, role_id, assigned_by, assigned_at)
    VALUES (target_user_id, v_role_id, auth.uid(), NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- 4. Update remove_role_from_user function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.remove_role_from_user(
    target_user_id UUID,
    target_role TEXT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_role_id UUID;
    v_current_user_id UUID := auth.uid();
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role % does not exist', target_role;
    END IF;

    -- Prevent removing own admin role
    IF target_user_id = v_current_user_id AND target_role = 'admin' THEN
        RAISE EXCEPTION 'Cannot remove your own admin role';
    END IF;

    -- Remove role from table
    DELETE FROM public.user_roles
    WHERE user_id = target_user_id AND role_id = v_role_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- 5. Update assign_permission_to_role function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.assign_permission_to_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_role_id UUID;
    v_permission_id UUID;
BEGIN
    -- Check if user is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Only admins can assign permissions'
        );
    END IF;

    -- Look up role_id and permission_id
    SELECT id INTO v_role_id FROM public.roles WHERE name = role_name;
    SELECT id INTO v_permission_id FROM public.permissions WHERE name = permission_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Role not found'
        );
    END IF;

    IF v_permission_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Permission not found'
        );
    END IF;

    -- Insert permission (or do nothing if already exists)
    INSERT INTO public.role_permissions (role_id, permission_id)
    VALUES (v_role_id, v_permission_id)
    ON CONFLICT (role_id, permission_id) DO NOTHING;

    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" assigned to role "%s"', permission_name, role_name)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- 6. Update remove_permission_from_role function
-- ============================================================================

CREATE OR REPLACE FUNCTION public.remove_permission_from_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_role_id UUID;
    v_permission_id UUID;
BEGIN
    -- Check if user is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Only admins can remove permissions'
        );
    END IF;

    -- Look up role_id and permission_id
    SELECT id INTO v_role_id FROM public.roles WHERE name = role_name;
    SELECT id INTO v_permission_id FROM public.permissions WHERE name = permission_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Role not found'
        );
    END IF;

    IF v_permission_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Permission not found'
        );
    END IF;

    -- Remove permission
    DELETE FROM public.role_permissions
    WHERE role_id = v_role_id AND permission_id = v_permission_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" removed from role "%s"', permission_name, role_name)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- ============================================================================
-- Verification
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '✅ RPC functions updated to reference correct tables:';
    RAISE NOTICE '   - is_user_admin() → user_roles';
    RAISE NOTICE '   - delete_role() → user_roles';
    RAISE NOTICE '   - assign_role_to_user() → user_roles';
    RAISE NOTICE '   - remove_role_from_user() → user_roles';
    RAISE NOTICE '   - assign_permission_to_role() → role_permissions';
    RAISE NOTICE '   - remove_permission_from_role() → role_permissions';
END $$;
