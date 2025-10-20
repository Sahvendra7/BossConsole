-- ============================================================================
-- BOSS RBAC Migration: ENUMs to Tables
-- ============================================================================
-- Phase 1: Create new table-based schema for roles and permissions
--
-- Purpose:
-- - Migrate from PostgreSQL ENUMs to tables for full CRUD support
-- - Enable deletion of roles and permissions
-- - Protect system roles (user, admin) with is_system flag
-- - Maintain backward compatibility during migration
--
-- Migration Strategy:
-- 1. Create new tables: roles, permissions
-- 2. Migrate data from ENUMs to tables
-- 3. Create new mapping tables with FK to new tables
-- 4. Migrate data from old mapping tables
-- 5. Enable RLS and create policies
--
-- Next Steps:
-- - Phase 2: Update RPC functions
-- - Phase 3: Update auth hook
-- - Phase 4: Cutover (rename tables)
-- ============================================================================

-- ============================================================================
-- 1. CREATE ROLES TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.roles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_system BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE public.roles IS 'Application roles (table-based replacement for app_role enum). Supports full CRUD operations with system role protection.';
COMMENT ON COLUMN public.roles.name IS 'Unique role name (e.g., "user", "admin", "developer")';
COMMENT ON COLUMN public.roles.description IS 'Optional human-readable description';
COMMENT ON COLUMN public.roles.is_system IS 'System roles (user, admin) cannot be deleted';

-- ============================================================================
-- 2. CREATE PERMISSIONS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.permissions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_system BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE public.permissions IS 'Application permissions (table-based replacement for app_permission enum). Supports full CRUD operations with system permission protection.';
COMMENT ON COLUMN public.permissions.name IS 'Unique permission name in domain.action format (e.g., "users.read")';
COMMENT ON COLUMN public.permissions.description IS 'Optional human-readable description';
COMMENT ON COLUMN public.permissions.is_system IS 'System permissions cannot be deleted';

-- ============================================================================
-- 3. MIGRATE DATA FROM ENUMS TO TABLES
-- ============================================================================

-- Migrate roles from app_role enum
INSERT INTO public.roles (name, is_system, description)
SELECT
    enumlabel,
    enumlabel IN ('user', 'admin') AS is_system,
    CASE enumlabel
        WHEN 'user' THEN 'Default role for all users'
        WHEN 'admin' THEN 'Administrative role with full permissions'
        ELSE 'Custom role'
    END
FROM pg_enum
WHERE enumtypid = 'public.app_role'::regtype
ON CONFLICT (name) DO NOTHING;

-- Migrate permissions from app_permission enum
INSERT INTO public.permissions (name, is_system, description)
SELECT
    enumlabel,
    true AS is_system,  -- All initial permissions are system permissions
    CASE enumlabel
        WHEN 'users.read' THEN 'View user information'
        WHEN 'users.write' THEN 'Create and update users'
        WHEN 'workspaces.read' THEN 'View workspaces'
        WHEN 'workspaces.write' THEN 'Create and update workspaces'
        WHEN 'workspaces.delete' THEN 'Delete workspaces'
        WHEN 'plugins.install' THEN 'Install plugins'
        WHEN 'plugins.manage' THEN 'Manage installed plugins'
        WHEN 'admin.access' THEN 'Access administrative features'
        ELSE 'System permission'
    END
FROM pg_enum
WHERE enumtypid = 'public.app_permission'::regtype
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- 4. CREATE NEW MAPPING TABLES (WITH FK TO NEW TABLES)
-- ============================================================================

-- User Roles Mapping (NEW: uses role_id instead of role enum)
CREATE TABLE IF NOT EXISTS public.user_roles_new (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES public.roles(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure a user can only have each role once
    UNIQUE (user_id, role_id)
);

COMMENT ON TABLE public.user_roles_new IS 'Maps users to roles (NEW table-based version). Will replace user_roles table.';
COMMENT ON COLUMN public.user_roles_new.role_id IS 'Foreign key to roles table (replaces role enum)';

-- Role Permissions Mapping (NEW: uses role_id and permission_id instead of enums)
CREATE TABLE IF NOT EXISTS public.role_permissions_new (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES public.roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES public.permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure a role can only have each permission once
    UNIQUE (role_id, permission_id)
);

COMMENT ON TABLE public.role_permissions_new IS 'Maps roles to permissions (NEW table-based version). Will replace role_permissions table.';
COMMENT ON COLUMN public.role_permissions_new.role_id IS 'Foreign key to roles table (replaces role enum)';
COMMENT ON COLUMN public.role_permissions_new.permission_id IS 'Foreign key to permissions table (replaces permission enum)';

-- ============================================================================
-- 5. MIGRATE DATA FROM OLD TABLES TO NEW TABLES
-- ============================================================================

-- Migrate user role assignments
INSERT INTO public.user_roles_new (id, user_id, role_id, assigned_by, assigned_at, created_at)
SELECT
    ur.id,
    ur.user_id,
    r.id AS role_id,
    ur.assigned_by,
    ur.assigned_at,
    ur.created_at
FROM public.user_roles ur
JOIN public.roles r ON r.name = ur.role::text
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Migrate role permission assignments
INSERT INTO public.role_permissions_new (id, role_id, permission_id, created_at)
SELECT
    rp.id,
    r.id AS role_id,
    p.id AS permission_id,
    rp.created_at
FROM public.role_permissions rp
JOIN public.roles r ON r.name = rp.role::text
JOIN public.permissions p ON p.name = rp.permission::text
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 6. ENABLE ROW LEVEL SECURITY
-- ============================================================================

ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles_new ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.role_permissions_new ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 7. CREATE INDEXES FOR PERFORMANCE
-- ============================================================================

-- Roles table indexes
CREATE INDEX IF NOT EXISTS idx_roles_name ON public.roles(name);
CREATE INDEX IF NOT EXISTS idx_roles_is_system ON public.roles(is_system);

-- Permissions table indexes
CREATE INDEX IF NOT EXISTS idx_permissions_name ON public.permissions(name);
CREATE INDEX IF NOT EXISTS idx_permissions_is_system ON public.permissions(is_system);

-- User roles mapping indexes
CREATE INDEX IF NOT EXISTS idx_user_roles_new_user_id ON public.user_roles_new(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_new_role_id ON public.user_roles_new(role_id);

-- Role permissions mapping indexes
CREATE INDEX IF NOT EXISTS idx_role_permissions_new_role_id ON public.role_permissions_new(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_new_permission_id ON public.role_permissions_new(permission_id);

-- ============================================================================
-- 8. CREATE RLS POLICIES FOR NEW TABLES
-- ============================================================================

-- ==================== ROLES TABLE POLICIES ====================

-- Everyone can view all roles (for UI dropdowns)
CREATE POLICY "Anyone can view roles"
    ON public.roles
    FOR SELECT
    USING (true);

-- Only admins can insert roles
CREATE POLICY "Admins can create roles"
    ON public.roles
    FOR INSERT
    WITH CHECK (
        public.is_user_admin(auth.uid())
    );

-- Only admins can update roles (except system roles)
CREATE POLICY "Admins can update non-system roles"
    ON public.roles
    FOR UPDATE
    USING (
        NOT is_system
        AND public.is_user_admin(auth.uid())
    );

-- Only admins can delete roles (system roles protected)
CREATE POLICY "Admins can delete non-system roles"
    ON public.roles
    FOR DELETE
    USING (
        NOT is_system
        AND public.is_user_admin(auth.uid())
    );

-- Service role full access
CREATE POLICY "Service role full access to roles"
    ON public.roles
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- ==================== PERMISSIONS TABLE POLICIES ====================

-- Everyone can view all permissions (for UI)
CREATE POLICY "Anyone can view permissions"
    ON public.permissions
    FOR SELECT
    USING (true);

-- Only admins can create permissions
CREATE POLICY "Admins can create permissions"
    ON public.permissions
    FOR INSERT
    WITH CHECK (
        public.is_user_admin(auth.uid())
    );

-- Only admins can update permissions (except system permissions)
CREATE POLICY "Admins can update non-system permissions"
    ON public.permissions
    FOR UPDATE
    USING (
        NOT is_system
        AND public.is_user_admin(auth.uid())
    );

-- Only admins can delete permissions (system permissions protected)
CREATE POLICY "Admins can delete non-system permissions"
    ON public.permissions
    FOR DELETE
    USING (
        NOT is_system
        AND public.is_user_admin(auth.uid())
    );

-- Service role full access
CREATE POLICY "Service role full access to permissions"
    ON public.permissions
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- ==================== USER_ROLES_NEW TABLE POLICIES ====================

-- Users can view their own roles
CREATE POLICY "Users can view their own roles (new)"
    ON public.user_roles_new
    FOR SELECT
    USING (auth.uid() = user_id);

-- Admins can view all roles
CREATE POLICY "Admins can view all roles (new)"
    ON public.user_roles_new
    FOR SELECT
    USING (
        public.is_user_admin(auth.uid())
    );

-- Admins can assign roles
CREATE POLICY "Admins can assign roles (new)"
    ON public.user_roles_new
    FOR INSERT
    WITH CHECK (
        public.is_user_admin(auth.uid())
    );

-- Admins can remove roles (except their own admin role)
CREATE POLICY "Admins can remove roles (new)"
    ON public.user_roles_new
    FOR DELETE
    USING (
        public.is_user_admin(auth.uid())
        -- Prevent removing own admin role
        AND NOT (
            user_id = auth.uid()
            AND role_id IN (SELECT id FROM public.roles WHERE name = 'admin')
        )
    );

-- Service role full access
CREATE POLICY "Service role full access to user_roles_new"
    ON public.user_roles_new
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- ==================== ROLE_PERMISSIONS_NEW TABLE POLICIES ====================

-- Everyone can view role permissions
CREATE POLICY "Anyone can view role permissions (new)"
    ON public.role_permissions_new
    FOR SELECT
    USING (true);

-- Admins can manage role permissions
CREATE POLICY "Admins can manage role permissions (new)"
    ON public.role_permissions_new
    FOR ALL
    USING (
        public.is_user_admin(auth.uid())
    );

-- Service role full access
CREATE POLICY "Service role full access to role_permissions_new"
    ON public.role_permissions_new
    FOR ALL
    USING (auth.jwt() ->> 'role' = 'service_role');

-- ============================================================================
-- 9. GRANT PERMISSIONS
-- ============================================================================

-- Grant read access to authenticated users
GRANT SELECT ON public.roles TO authenticated;
GRANT SELECT ON public.permissions TO authenticated;
GRANT SELECT ON public.user_roles_new TO authenticated;
GRANT SELECT ON public.role_permissions_new TO authenticated;

-- Grant full access to service role
GRANT ALL ON public.roles TO service_role;
GRANT ALL ON public.permissions TO service_role;
GRANT ALL ON public.user_roles_new TO service_role;
GRANT ALL ON public.role_permissions_new TO service_role;

-- ============================================================================
-- 10. VERIFICATION QUERIES
-- ============================================================================

-- Verify data migration counts match
DO $$
DECLARE
    v_roles_count INT;
    v_permissions_count INT;
    v_user_roles_old_count INT;
    v_user_roles_new_count INT;
    v_role_permissions_old_count INT;
    v_role_permissions_new_count INT;
BEGIN
    SELECT COUNT(*) INTO v_roles_count FROM public.roles;
    SELECT COUNT(*) INTO v_permissions_count FROM public.permissions;
    SELECT COUNT(*) INTO v_user_roles_old_count FROM public.user_roles;
    SELECT COUNT(*) INTO v_user_roles_new_count FROM public.user_roles_new;
    SELECT COUNT(*) INTO v_role_permissions_old_count FROM public.role_permissions;
    SELECT COUNT(*) INTO v_role_permissions_new_count FROM public.role_permissions_new;

    RAISE NOTICE '✅ Migration Verification:';
    RAISE NOTICE '  Roles migrated: %', v_roles_count;
    RAISE NOTICE '  Permissions migrated: %', v_permissions_count;
    RAISE NOTICE '  User roles - Old: %, New: %', v_user_roles_old_count, v_user_roles_new_count;
    RAISE NOTICE '  Role permissions - Old: %, New: %', v_role_permissions_old_count, v_role_permissions_new_count;

    IF v_user_roles_old_count != v_user_roles_new_count THEN
        RAISE WARNING '⚠️  User roles count mismatch! Old: %, New: %', v_user_roles_old_count, v_user_roles_new_count;
    END IF;

    IF v_role_permissions_old_count != v_role_permissions_new_count THEN
        RAISE WARNING '⚠️  Role permissions count mismatch! Old: %, New: %', v_role_permissions_old_count, v_role_permissions_new_count;
    END IF;
END;
$$;

-- ============================================================================
-- MIGRATION COMPLETE (PHASE 1)
-- ============================================================================
--
-- ✅ New tables created: roles, permissions
-- ✅ Data migrated from ENUMs to tables
-- ✅ New mapping tables created: user_roles_new, role_permissions_new
-- ✅ Data migrated from old mapping tables
-- ✅ RLS enabled and policies created
-- ✅ Indexes created for performance
--
-- Next Steps:
-- 1. Phase 2: Run 20251020_update_rbac_functions.sql
-- 2. Phase 3: Run 20251020_update_auth_hook.sql
-- 3. Phase 4: Run 20251020_cutover_tables.sql
-- 4. Update Kotlin code to use new schema
-- 5. Test thoroughly
--
-- Note: Old tables (user_roles, role_permissions) and ENUMs are still active
--       until cutover. This allows for zero-downtime migration.
-- ============================================================================
