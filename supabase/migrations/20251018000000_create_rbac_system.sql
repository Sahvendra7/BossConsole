-- ============================================================================
-- BOSS RBAC System - Role-Based Access Control
-- ============================================================================
-- This migration implements a complete RBAC system using Supabase Custom Claims
--
-- Components:
-- 1. app_role enum - Defines available roles (extensible for plugins)
-- 2. app_permission enum - Defines granular permissions (future use)
-- 3. user_roles table - Maps users to roles (many-to-many)
-- 4. role_permissions table - Maps roles to permissions (many-to-many)
-- 5. Updated trigger - Auto-assigns 'user' role on signup
-- 6. Helper functions - Role management and checking
--
-- Features:
-- - Default 'user' role on signup
-- - Admin-only role assignment
-- - Audit trail (assigned_by, assigned_at)
-- - Plugin-extensible role system
-- - Row Level Security enforcement
-- ============================================================================

-- ============================================================================
-- 1. CREATE ENUMS
-- ============================================================================

-- Define application roles
-- Note: To add plugin roles, use: ALTER TYPE app_role ADD VALUE 'plugin_role_name';
CREATE TYPE public.app_role AS ENUM (
    'user',      -- Default role for all users
    'admin'      -- Administrative role with full permissions
);

COMMENT ON TYPE public.app_role IS 'Application roles. Extensible via ALTER TYPE for plugin-specific roles.';

-- Define application permissions (for future fine-grained access control)
-- Note: Permissions can be added via: ALTER TYPE app_permission ADD VALUE 'new.permission';
CREATE TYPE public.app_permission AS ENUM (
    'users.read',
    'users.write',
    'workspaces.read',
    'workspaces.write',
    'workspaces.delete',
    'plugins.install',
    'plugins.manage',
    'admin.access'
);

COMMENT ON TYPE public.app_permission IS 'Granular permissions for role-based access control.';

-- ============================================================================
-- 2. CREATE TABLES
-- ============================================================================

-- User Roles Table: Maps users to their assigned roles
CREATE TABLE IF NOT EXISTS public.user_roles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role public.app_role NOT NULL,
    assigned_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure a user can only have each role once
    UNIQUE (user_id, role)
);

COMMENT ON TABLE public.user_roles IS 'Maps users to their assigned roles. Users can have multiple roles.';
COMMENT ON COLUMN public.user_roles.assigned_by IS 'The user (typically admin) who assigned this role.';
COMMENT ON COLUMN public.user_roles.assigned_at IS 'Timestamp when the role was assigned.';

-- Role Permissions Table: Maps roles to their permissions (future use)
CREATE TABLE IF NOT EXISTS public.role_permissions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    role public.app_role NOT NULL,
    permission public.app_permission NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure a role can only have each permission once
    UNIQUE (role, permission)
);

COMMENT ON TABLE public.role_permissions IS 'Maps roles to permissions for fine-grained access control.';

-- ============================================================================
-- 3. CREATE INDEXES FOR PERFORMANCE
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON public.user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON public.user_roles(role);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_by ON public.user_roles(assigned_by);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON public.role_permissions(role);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON public.role_permissions(permission);

-- ============================================================================
-- 4. ENABLE ROW LEVEL SECURITY
-- ============================================================================

ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.role_permissions ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 5. CREATE RLS POLICIES
-- ============================================================================

-- User Roles Policies
-- Users can view their own roles
CREATE POLICY "Users can view their own roles"
    ON public.user_roles
    FOR SELECT
    USING (auth.uid() = user_id);

-- Only admins can view all roles
CREATE POLICY "Admins can view all roles"
    ON public.user_roles
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Only admins can insert roles
CREATE POLICY "Admins can assign roles"
    ON public.user_roles
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Only admins can delete roles (except their own admin role)
CREATE POLICY "Admins can remove roles"
    ON public.user_roles
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            WHERE ur.user_id = auth.uid() AND ur.role = 'admin'
        )
        -- Prevent removing own admin role
        AND NOT (user_id = auth.uid() AND role = 'admin')
    );

-- Service role can access everything
CREATE POLICY "Service role full access to user_roles"
    ON public.user_roles
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- Role Permissions Policies
-- Everyone can read permissions
CREATE POLICY "Anyone can view role permissions"
    ON public.role_permissions
    FOR SELECT
    USING (true);

-- Only admins can modify permissions
CREATE POLICY "Admins can manage permissions"
    ON public.role_permissions
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Service role can access everything
CREATE POLICY "Service role full access to role_permissions"
    ON public.role_permissions
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- ============================================================================
-- 6. UPDATE handle_new_user() TRIGGER TO ASSIGN DEFAULT ROLE
-- ============================================================================

-- Drop existing trigger and function to recreate with role assignment
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    -- Insert user into public.users table
    INSERT INTO public.users (id, email, created_at, updated_at)
    VALUES (NEW.id, NEW.email, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

    -- Assign default 'user' role
    INSERT INTO public.user_roles (user_id, role, assigned_by, assigned_at)
    VALUES (NEW.id, 'user', NULL, NOW())
    ON CONFLICT (user_id, role) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.handle_new_user() IS 'Automatically creates user record and assigns default "user" role on signup.';

-- Recreate trigger
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- ============================================================================
-- 7. HELPER FUNCTIONS
-- ============================================================================

-- Check if a user has a specific role
CREATE OR REPLACE FUNCTION public.user_has_role(
    check_user_id UUID,
    check_role public.app_role
)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_id = check_user_id AND role = check_role
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.user_has_role(UUID, public.app_role) IS 'Check if a user has a specific role.';

-- Check if a user is an admin
CREATE OR REPLACE FUNCTION public.is_user_admin(check_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN public.user_has_role(check_user_id, 'admin');
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.is_user_admin(UUID) IS 'Quick check if a user has admin role.';

-- Get all roles for a user
CREATE OR REPLACE FUNCTION public.get_user_roles(check_user_id UUID)
RETURNS SETOF public.app_role AS $$
BEGIN
    RETURN QUERY
    SELECT role FROM public.user_roles
    WHERE user_id = check_user_id
    ORDER BY assigned_at;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.get_user_roles(UUID) IS 'Returns all roles assigned to a user.';

-- Authorize function: Check if current user has a specific permission
CREATE OR REPLACE FUNCTION public.authorize(
    requested_permission public.app_permission
)
RETURNS BOOLEAN AS $$
DECLARE
    user_roles_arr public.app_role[];
BEGIN
    -- Get all roles for the current user
    SELECT ARRAY_AGG(role) INTO user_roles_arr
    FROM public.user_roles
    WHERE user_id = auth.uid();

    -- Check if any of the user's roles have the requested permission
    RETURN EXISTS (
        SELECT 1 FROM public.role_permissions
        WHERE role = ANY(user_roles_arr)
        AND permission = requested_permission
    );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.authorize(public.app_permission) IS 'Check if current user has a specific permission via their roles.';

-- Assign role to user (admin only)
CREATE OR REPLACE FUNCTION public.assign_role_to_user(
    target_user_id UUID,
    target_role public.app_role
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if caller is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can assign roles';
    END IF;

    -- Insert role (or do nothing if already exists)
    INSERT INTO public.user_roles (user_id, role, assigned_by, assigned_at)
    VALUES (target_user_id, target_role, auth.uid(), NOW())
    ON CONFLICT (user_id, role) DO NOTHING;

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.assign_role_to_user(UUID, public.app_role) IS 'Assign a role to a user (admin only).';

-- Remove role from user (admin only)
CREATE OR REPLACE FUNCTION public.remove_role_from_user(
    target_user_id UUID,
    target_role public.app_role
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if caller is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can remove roles';
    END IF;

    -- Prevent removing admin role from self
    IF target_user_id = auth.uid() AND target_role = 'admin' THEN
        RAISE EXCEPTION 'Cannot remove your own admin role';
    END IF;

    -- Remove role
    DELETE FROM public.user_roles
    WHERE user_id = target_user_id AND role = target_role;

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.remove_role_from_user(UUID, public.app_role) IS 'Remove a role from a user (admin only). Cannot remove own admin role.';

-- ============================================================================
-- 8. SEED DEFAULT ROLE PERMISSIONS
-- ============================================================================

-- Admin has all permissions
INSERT INTO public.role_permissions (role, permission)
VALUES
    ('admin', 'users.read'),
    ('admin', 'users.write'),
    ('admin', 'workspaces.read'),
    ('admin', 'workspaces.write'),
    ('admin', 'workspaces.delete'),
    ('admin', 'plugins.install'),
    ('admin', 'plugins.manage'),
    ('admin', 'admin.access')
ON CONFLICT (role, permission) DO NOTHING;

-- Regular users have basic permissions
INSERT INTO public.role_permissions (role, permission)
VALUES
    ('user', 'users.read'),
    ('user', 'workspaces.read'),
    ('user', 'workspaces.write')
ON CONFLICT (role, permission) DO NOTHING;

-- ============================================================================
-- 9. GRANT PERMISSIONS
-- ============================================================================

-- Grant necessary permissions to authenticated users
GRANT SELECT ON public.user_roles TO authenticated;
GRANT SELECT ON public.role_permissions TO authenticated;

-- Grant full access to service role
GRANT ALL ON public.user_roles TO service_role;
GRANT ALL ON public.role_permissions TO service_role;

-- ============================================================================
-- 10. BACKFILL EXISTING USERS WITH DEFAULT ROLE
-- ============================================================================

-- Assign 'user' role to all existing users who don't have any roles yet
INSERT INTO public.user_roles (user_id, role, assigned_by, assigned_at)
SELECT
    u.id,
    'user'::public.app_role,
    NULL,
    NOW()
FROM public.users u
WHERE NOT EXISTS (
    SELECT 1 FROM public.user_roles ur
    WHERE ur.user_id = u.id
)
ON CONFLICT (user_id, role) DO NOTHING;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
--
-- Next Steps:
-- 1. Create custom_access_token_hook() to inject roles into JWT
-- 2. Enable the hook in Supabase Dashboard (Auth → Hooks)
-- 3. Test role assignment and JWT claims
--
-- To add plugin roles:
--   ALTER TYPE public.app_role ADD VALUE 'plugin_name_role';
--
-- To add plugin permissions:
--   ALTER TYPE public.app_permission ADD VALUE 'plugin.permission';
--
-- ============================================================================
