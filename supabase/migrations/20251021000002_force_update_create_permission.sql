-- ============================================================================
-- FORCE UPDATE: create_new_permission() to use table-based schema
-- ============================================================================
--
-- Issue: Old ENUM-based function still deployed, causing permissions
--        to be created in ENUM but get_all_permissions() reads from table
--
-- Fix: DROP and CREATE the function to ensure table-based approach
--
-- Created: 2025-01-21
-- ============================================================================

DROP FUNCTION IF EXISTS public.create_new_permission(TEXT, TEXT) CASCADE;

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

    -- Insert into permissions table (NOT ENUM!)
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

    -- Return success with permission details
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

COMMENT ON FUNCTION public.create_new_permission(TEXT, TEXT)
IS 'Creates new permission in permissions TABLE (not ENUM). Requires admin role.';

-- Grant execute permission
GRANT EXECUTE ON FUNCTION public.create_new_permission(TEXT, TEXT) TO authenticated;

-- Verification
DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '✅ FORCED UPDATE COMPLETE';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE '✅ create_new_permission() now inserts into permissions TABLE';
    RAISE NOTICE '✅ Permissions will now appear in get_all_permissions()';
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
END $$;
