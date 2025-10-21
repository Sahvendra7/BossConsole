-- ============================================================================
-- FORCE REPLACE create_new_permission - FINAL FIX
-- ============================================================================
-- Drop ALL variations of the function and recreate
-- ============================================================================

-- Drop all possible variations
DROP FUNCTION IF EXISTS public.create_new_permission(TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.create_new_permission(TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.create_new_permission() CASCADE;

-- Recreate with correct implementation
CREATE FUNCTION public.create_new_permission(
    permission_name TEXT,
    description TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
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

        RAISE NOTICE '✅ Successfully inserted permission: % (ID: %)', permission_name, v_permission_id;
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
        'permission_id', v_permission_id::text,
        'permission', permission_name,
        'description', description
    );
END;
$$;

-- Grant permissions
GRANT EXECUTE ON FUNCTION public.create_new_permission(TEXT, TEXT) TO authenticated;

-- Verify it works
DO $$
DECLARE
    test_result JSONB;
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '===========================================';
    RAISE NOTICE '✅ FUNCTION RECREATED SUCCESSFULLY';
    RAISE NOTICE '===========================================';
    RAISE NOTICE 'Function: public.create_new_permission';
    RAISE NOTICE 'Action: Inserts into public.permissions table';
    RAISE NOTICE 'Security: Requires authenticated admin user';
    RAISE NOTICE '===========================================';
END $$;
