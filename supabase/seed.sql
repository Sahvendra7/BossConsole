-- ============================================================================
-- Seed Data for BOSS Database
-- ============================================================================
-- This file is automatically executed after running `supabase db reset`
-- It populates the database with essential default data.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Default System Roles
-- ----------------------------------------------------------------------------
-- These are the core roles required for the application to function.
-- - 'user': Assigned automatically to all new signups via handle_new_user() trigger
-- - 'admin': Must be manually assigned to specific users (see instructions below)

INSERT INTO public.roles (name, description, is_system)
VALUES
    ('user', 'Default role for all authenticated users', true),
    ('admin', 'Administrator role with full system access', true)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- Manual Admin Assignment Instructions
-- ============================================================================
-- After a user signs up through the normal authentication flow, you can
-- manually assign them the admin role using:
--
-- DO $$
-- DECLARE
--     v_user_id UUID;
--     v_admin_role_id UUID;
-- BEGIN
--     -- Get user ID from email
--     SELECT id INTO v_user_id
--     FROM auth.users
--     WHERE email = 'your-email@example.com';
--
--     -- Get admin role ID
--     SELECT id INTO v_admin_role_id
--     FROM public.roles
--     WHERE name = 'admin';
--
--     -- Assign admin role
--     INSERT INTO public.user_roles (user_id, role_id, assigned_by, assigned_at)
--     VALUES (v_user_id, v_admin_role_id, NULL, NOW())
--     ON CONFLICT (user_id, role_id) DO NOTHING;
-- END $$;
-- ============================================================================
