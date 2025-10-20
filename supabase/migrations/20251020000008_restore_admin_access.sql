-- ============================================================================
-- Restore Admin Access for shivang.iitk@gmail.com
-- ============================================================================
--
-- Purpose: Restore admin role that was accidentally removed
-- User: shivang.iitk@gmail.com (9e6af4d5-81ec-44fc-aba7-308223396fb1)
--
-- Created: 2025-01-19
-- ============================================================================

-- First, check current roles
DO $$
DECLARE
    v_user_id UUID := '9e6af4d5-81ec-44fc-aba7-308223396fb1';
    v_admin_role_id UUID;
    v_has_admin BOOLEAN;
BEGIN
    -- Get admin role ID
    SELECT id INTO v_admin_role_id FROM public.roles WHERE name = 'admin';

    -- Check if user already has admin role
    SELECT EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_id = v_user_id AND role_id = v_admin_role_id
    ) INTO v_has_admin;

    IF v_has_admin THEN
        RAISE NOTICE '✅ User already has admin role';
    ELSE
        RAISE NOTICE '⚠️  User does NOT have admin role - will assign';

        -- Assign admin role
        INSERT INTO public.user_roles (user_id, role_id, assigned_by, assigned_at, created_at)
        VALUES (
            v_user_id,
            v_admin_role_id,
            v_user_id,  -- Self-assigned
            NOW(),
            NOW()
        )
        ON CONFLICT (user_id, role_id) DO NOTHING;

        RAISE NOTICE '✅ Admin role assigned to shivang.iitk@gmail.com';
    END IF;

    -- Show current roles for user
    RAISE NOTICE '';
    RAISE NOTICE '📋 Current roles for user:';
    DECLARE
        v_role_name TEXT;
    BEGIN
        FOR v_role_name IN
            SELECT r.name
            FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = v_user_id
            ORDER BY r.name
        LOOP
            RAISE NOTICE '   - %', v_role_name;
        END LOOP;
    END;
END $$;
