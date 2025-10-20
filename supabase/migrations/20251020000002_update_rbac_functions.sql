-- ============================================================================
-- BOSS RBAC Migration: Update RPC Functions for Table-Based Schema
-- ============================================================================
-- Phase 2: Update all RPC functions to work with new table-based schema
--
-- Purpose:
-- - Update existing functions to work with roles/permissions tables
-- - Add new functions for deletion operations
-- - Maintain API compatibility (same function names and signatures)
--
-- Functions Updated:
-- 1-7: Dynamic role management functions (from 20251019 migration)
-- 8-9: NEW delete functions
-- 10-12: Helper functions (from 20251018 migration)
--
-- ============================================================================

-- ============================================================================
-- HELPER: Check if user is admin (using NEW tables)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.is_user_admin_new(check_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles_new ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = 'admin'
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.is_user_admin_new(UUID) IS 'Check if user is admin using NEW table-based schema. Used during migration.';

-- ============================================================================
-- 1. CREATE NEW ROLE (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.create_new_role(
    role_name TEXT,
    description TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
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

    -- Check if user is admin (using OLD table during migration)
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

    -- Check if role already exists
    IF EXISTS (SELECT 1 FROM public.roles WHERE name = role_name) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Role "%s" already exists', role_name)
        );
    END IF;

    -- Insert into roles table
    BEGIN
        INSERT INTO public.roles (name, description, is_system)
        VALUES (role_name, description, false)
        RETURNING id INTO v_role_id;
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Failed to create role: %s', SQLERRM)
        );
    END;

    -- Return success with role ID
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Role "%s" created successfully', role_name),
        'role_id', v_role_id,
        'role', role_name,
        'description', description
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.create_new_role(TEXT, TEXT) IS 'Create a new role in the roles table (admin only).';

-- ============================================================================
-- 2. CREATE NEW PERMISSION (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.create_new_permission(
    permission_name TEXT,
    description TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_permission_id UUID;
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

    -- Check if permission already exists
    IF EXISTS (SELECT 1 FROM public.permissions WHERE name = permission_name) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" already exists', permission_name)
        );
    END IF;

    -- Insert into permissions table
    BEGIN
        INSERT INTO public.permissions (name, description, is_system)
        VALUES (permission_name, description, false)
        RETURNING id INTO v_permission_id;
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Failed to create permission: %s', SQLERRM)
        );
    END;

    -- Return success with permission ID
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" created successfully', permission_name),
        'permission_id', v_permission_id,
        'permission', permission_name,
        'description', description
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.create_new_permission(TEXT, TEXT) IS 'Create a new permission in the permissions table (admin only).';

-- ============================================================================
-- 3. GET ALL ROLES (TABLE VERSION)
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

    -- Query all roles from roles table
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY created_at
    ) INTO v_roles
    FROM public.roles;

    -- Return roles
    RETURN jsonb_build_object(
        'success', true,
        'roles', COALESCE(v_roles, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.get_all_roles() IS 'Get all roles from the roles table (admin only).';

-- ============================================================================
-- 4. GET ALL PERMISSIONS (TABLE VERSION)
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

    -- Query all permissions from permissions table
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY created_at
    ) INTO v_permissions
    FROM public.permissions;

    -- Return permissions
    RETURN jsonb_build_object(
        'success', true,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.get_all_permissions() IS 'Get all permissions from the permissions table (admin only).';

-- ============================================================================
-- 5. ASSIGN PERMISSION TO ROLE (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.assign_permission_to_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permission_id UUID;
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

    -- Get permission ID
    SELECT id INTO v_permission_id
    FROM public.permissions
    WHERE name = permission_name;

    IF v_permission_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" does not exist', permission_name)
        );
    END IF;

    -- Insert into role_permissions_new (ON CONFLICT DO NOTHING handles duplicates)
    INSERT INTO public.role_permissions_new (role_id, permission_id, created_at)
    VALUES (v_role_id, v_permission_id, NOW())
    ON CONFLICT (role_id, permission_id) DO NOTHING;

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

COMMENT ON FUNCTION public.assign_permission_to_role(TEXT, TEXT) IS 'Assign a permission to a role using table-based schema (admin only).';

-- ============================================================================
-- 6. REMOVE PERMISSION FROM ROLE (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.remove_permission_from_role(
    role_name TEXT,
    permission_name TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permission_id UUID;
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

    -- Get permission ID
    SELECT id INTO v_permission_id
    FROM public.permissions
    WHERE name = permission_name;

    IF v_permission_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" does not exist', permission_name)
        );
    END IF;

    -- Delete from role_permissions_new
    DELETE FROM public.role_permissions_new
    WHERE role_id = v_role_id AND permission_id = v_permission_id;

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

COMMENT ON FUNCTION public.remove_permission_from_role(TEXT, TEXT) IS 'Remove a permission from a role using table-based schema (admin only).';

-- ============================================================================
-- 7. GET ROLE PERMISSIONS (TABLE VERSION)
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

    -- Query permissions for the role
    SELECT jsonb_agg(p.name ORDER BY p.name)
    INTO v_permissions
    FROM public.role_permissions_new rp
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
        'error', format('Failed to get role permissions: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.get_role_permissions(TEXT) IS 'Get all permissions for a role using table-based schema (admin only).';

-- ============================================================================
-- 8. DELETE ROLE (NEW FUNCTION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_role(role_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_is_system BOOLEAN;
    v_user_count INT;
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

    -- Get role details
    SELECT id, is_system INTO v_role_id, v_is_system
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Role "%s" does not exist', role_name)
        );
    END IF;

    -- Protect system roles
    IF v_is_system THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Cannot delete system role "%s"', role_name)
        );
    END IF;

    -- Check how many users have this role
    SELECT COUNT(*) INTO v_user_count
    FROM public.user_roles_new
    WHERE role_id = v_role_id;

    -- Delete role (CASCADE will remove from user_roles_new and role_permissions_new)
    DELETE FROM public.roles WHERE id = v_role_id;

    -- Return success with stats
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Role "%s" deleted successfully', role_name),
        'role', role_name,
        'users_affected', v_user_count
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', format('Failed to delete role: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.delete_role(TEXT) IS 'Delete a non-system role (admin only). System roles are protected.';

-- ============================================================================
-- 9. DELETE PERMISSION (NEW FUNCTION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_permission(permission_name TEXT)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_permission_id UUID;
    v_is_system BOOLEAN;
    v_role_count INT;
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

    -- Get permission details
    SELECT id, is_system INTO v_permission_id, v_is_system
    FROM public.permissions
    WHERE name = permission_name;

    IF v_permission_id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Permission "%s" does not exist', permission_name)
        );
    END IF;

    -- Protect system permissions
    IF v_is_system THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', format('Cannot delete system permission "%s"', permission_name)
        );
    END IF;

    -- Check how many roles have this permission
    SELECT COUNT(*) INTO v_role_count
    FROM public.role_permissions_new
    WHERE permission_id = v_permission_id;

    -- Delete permission (CASCADE will remove from role_permissions_new)
    DELETE FROM public.permissions WHERE id = v_permission_id;

    -- Return success with stats
    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" deleted successfully', permission_name),
        'permission', permission_name,
        'roles_affected', v_role_count
    );
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', format('Failed to delete permission: %s', SQLERRM)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = '';

COMMENT ON FUNCTION public.delete_permission(TEXT) IS 'Delete a non-system permission (admin only). System permissions are protected.';

-- ============================================================================
-- 10. UPDATE ASSIGN ROLE TO USER (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.assign_role_to_user(
    target_user_id UUID,
    target_role TEXT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_role_id UUID;
BEGIN
    -- Check if caller is admin (using OLD table during migration)
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can assign roles';
    END IF;

    -- Get role ID
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role "%" does not exist', target_role;
    END IF;

    -- Insert role into NEW table (or do nothing if already exists)
    INSERT INTO public.user_roles_new (user_id, role_id, assigned_by, assigned_at)
    VALUES (target_user_id, v_role_id, auth.uid(), NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.assign_role_to_user(UUID, TEXT) IS 'Assign a role to a user using table-based schema (admin only).';

-- ============================================================================
-- 11. UPDATE REMOVE ROLE FROM USER (TABLE VERSION)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.remove_role_from_user(
    target_user_id UUID,
    target_role TEXT
)
RETURNS BOOLEAN AS $$
DECLARE
    v_role_id UUID;
    v_is_system BOOLEAN;
BEGIN
    -- Check if caller is admin (using OLD table during migration)
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can remove roles';
    END IF;

    -- Get role ID and system flag
    SELECT id, is_system INTO v_role_id, v_is_system
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role "%" does not exist', target_role;
    END IF;

    -- Prevent removing admin role from self
    IF target_user_id = auth.uid() AND target_role = 'admin' THEN
        RAISE EXCEPTION 'Cannot remove your own admin role';
    END IF;

    -- Prevent removing the baseline 'user' role from anyone
    IF target_role = 'user' THEN
        RAISE EXCEPTION 'Cannot remove the baseline user role. All users must have the user role.';
    END IF;

    -- Remove role from NEW table
    DELETE FROM public.user_roles_new
    WHERE user_id = target_user_id AND role_id = v_role_id;

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.remove_role_from_user(UUID, TEXT) IS 'Remove a role from a user using table-based schema (admin only). Cannot remove own admin role or user role.';

-- ============================================================================
-- 12. GRANT EXECUTE PERMISSIONS
-- ============================================================================

-- Allow authenticated users to call these functions (admin check is inside)
GRANT EXECUTE ON FUNCTION public.is_user_admin_new(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_new_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_new_permission(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_all_roles() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_all_permissions() TO authenticated;
GRANT EXECUTE ON FUNCTION public.assign_permission_to_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_permission_from_role(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_role_permissions(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_role(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_permission(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.assign_role_to_user(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_role_from_user(UUID, TEXT) TO authenticated;

-- ============================================================================
-- MIGRATION COMPLETE (PHASE 2)
-- ============================================================================
--
-- ✅ All 7 dynamic role management functions updated for table-based schema
-- ✅ 2 new delete functions added (delete_role, delete_permission)
-- ✅ 3 helper functions updated (assign/remove role from user)
-- ✅ System role/permission protection implemented
-- ✅ Cascade deletes configured for data integrity
--
-- Next Steps:
-- 1. Phase 3: Run 20251020_update_auth_hook.sql
-- 2. Phase 4: Run 20251020_cutover_tables.sql
-- 3. Update Kotlin code
-- 4. Test all operations
--
-- Note: Functions still use OLD table for admin checks (is_user_admin)
--       until after cutover. This maintains compatibility during migration.
-- ============================================================================
