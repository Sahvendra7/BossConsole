-- ============================================================================
-- Fix share_secret function - prevent double INSERT bug
-- Issue: Both user and role INSERT statements always execute, causing
--        constraint violations when sharing with users
-- Fix: Only execute the relevant INSERT based on whether sharing with
--      user or role
-- ============================================================================

CREATE OR REPLACE FUNCTION public.share_secret(
    p_secret_id UUID,
    p_target_user_id UUID DEFAULT NULL,
    p_target_role_id UUID DEFAULT NULL,
    p_notes TEXT DEFAULT NULL,
    p_expires_at TIMESTAMPTZ DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_is_owner BOOLEAN;
    v_is_admin BOOLEAN;
    v_target_email TEXT;
    v_target_role_name TEXT;
    v_secret_website TEXT;
BEGIN
    -- Check if user is owner or admin
    SELECT EXISTS(
        SELECT 1 FROM public.secrets
        WHERE id = p_secret_id AND user_id = auth.uid()
    ) INTO v_is_owner;

    SELECT EXISTS(
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = auth.uid() AND r.name = 'admin'
    ) INTO v_is_admin;

    IF NOT v_is_owner AND NOT v_is_admin THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Unauthorized: You must be the owner or admin to share this secret'
        );
    END IF;

    -- Validate target (must be user XOR role)
    IF (p_target_user_id IS NULL AND p_target_role_id IS NULL) OR
       (p_target_user_id IS NOT NULL AND p_target_role_id IS NOT NULL) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Must specify either target_user_id OR target_role_id, not both or neither'
        );
    END IF;

    -- Get secret website for logging
    SELECT website INTO v_secret_website
    FROM public.secrets
    WHERE id = p_secret_id;

    -- Handle user share
    IF p_target_user_id IS NOT NULL THEN
        -- Get user email
        SELECT email INTO v_target_email
        FROM auth.users
        WHERE id = p_target_user_id;

        IF v_target_email IS NULL THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'User not found'
            );
        END IF;

        -- Insert or update user share
        INSERT INTO public.secret_shares (
            secret_id,
            shared_with_user_id,
            shared_with_role_id,
            shared_by,
            access_level,
            expires_at,
            notes
        ) VALUES (
            p_secret_id,
            p_target_user_id,
            NULL,
            auth.uid(),
            'read',
            p_expires_at,
            p_notes
        )
        ON CONFLICT (secret_id, shared_with_user_id)
        DO UPDATE SET
            expires_at = EXCLUDED.expires_at,
            notes = EXCLUDED.notes,
            created_at = NOW();
    END IF;

    -- Handle role share
    IF p_target_role_id IS NOT NULL THEN
        -- Get role name
        SELECT name INTO v_target_role_name
        FROM public.roles
        WHERE id = p_target_role_id;

        IF v_target_role_name IS NULL THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'Role not found'
            );
        END IF;

        -- Insert or update role share
        INSERT INTO public.secret_shares (
            secret_id,
            shared_with_user_id,
            shared_with_role_id,
            shared_by,
            access_level,
            expires_at,
            notes
        ) VALUES (
            p_secret_id,
            NULL,
            p_target_role_id,
            auth.uid(),
            'read',
            p_expires_at,
            p_notes
        )
        ON CONFLICT (secret_id, shared_with_role_id)
        DO UPDATE SET
            expires_at = EXCLUDED.expires_at,
            notes = EXCLUDED.notes,
            created_at = NOW();
    END IF;

    -- Log the share action
    INSERT INTO public.secret_access_log (
        secret_id,
        user_id,
        operation,
        access_granted_via,
        metadata
    ) VALUES (
        p_secret_id,
        auth.uid(),
        'share',
        CASE WHEN v_is_owner THEN 'owner' ELSE 'admin_override' END,
        jsonb_build_object(
            'target_user_id', p_target_user_id,
            'target_role_id', p_target_role_id,
            'target_email', v_target_email,
            'target_role_name', v_target_role_name,
            'secret_website', v_secret_website,
            'expires_at', p_expires_at,
            'notes', p_notes
        )
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Secret shared successfully',
        'target_email', v_target_email,
        'target_role', v_target_role_name
    );
END;
$$;

ALTER FUNCTION public.share_secret(UUID, UUID, UUID, TEXT, TIMESTAMPTZ) OWNER TO postgres;
COMMENT ON FUNCTION public.share_secret IS 'Share a secret with a user or role (owner/admin only) - Fixed double INSERT bug';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Fixed share_secret function double INSERT bug';
    RAISE NOTICE '   - Wrapped user INSERT in IF p_target_user_id IS NOT NULL';
    RAISE NOTICE '   - Wrapped role INSERT in IF p_target_role_id IS NOT NULL';
    RAISE NOTICE '   - Only one INSERT executes now, preventing constraint violations';
END $$;
