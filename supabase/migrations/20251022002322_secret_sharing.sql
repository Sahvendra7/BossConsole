-- ============================================================================
-- SECRET SHARING & PRIVILEGE MANAGEMENT
-- ============================================================================
-- Migration: Add secret sharing functionality
-- Issue: #81 - Secret Manager Privilege Management Plugin
-- Date: 2025-10-22
--
-- Features:
-- - Share secrets with individual users or roles
-- - View-only access for shared secrets
-- - Owner and admin control over shares
-- - Full audit trail for all secret operations
-- - Automatic expiration for temporary access
--
-- Security:
-- - RLS policies enforce access control
-- - Only owners and admins can share/unshare secrets
-- - Audit log tracks all operations
-- - Users can only view secrets they own or have been shared with
-- ============================================================================

-- ============================================================================
-- PHASE 1: TABLE CREATION
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1.1: secret_shares - Track which secrets are shared with whom
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.secret_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_id UUID NOT NULL REFERENCES public.secrets(id) ON DELETE CASCADE,
    shared_with_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    shared_with_role_id UUID REFERENCES public.roles(id) ON DELETE CASCADE,
    shared_by UUID NOT NULL REFERENCES auth.users(id),
    access_level TEXT NOT NULL DEFAULT 'read',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    notes TEXT,

    -- Ensure either user_id OR role_id is set, not both
    CONSTRAINT share_target_check CHECK (
        (shared_with_user_id IS NOT NULL AND shared_with_role_id IS NULL) OR
        (shared_with_user_id IS NULL AND shared_with_role_id IS NOT NULL)
    ),

    -- Prevent duplicate shares
    CONSTRAINT unique_user_share UNIQUE (secret_id, shared_with_user_id),
    CONSTRAINT unique_role_share UNIQUE (secret_id, shared_with_role_id)
);

ALTER TABLE public.secret_shares OWNER TO postgres;

-- Table comments
COMMENT ON TABLE public.secret_shares IS 'Secret sharing: assigns view access to users or roles';
COMMENT ON COLUMN public.secret_shares.secret_id IS 'The secret being shared';
COMMENT ON COLUMN public.secret_shares.shared_with_user_id IS 'User who gets access (NULL if role-based)';
COMMENT ON COLUMN public.secret_shares.shared_with_role_id IS 'Role that gets access (NULL if user-based)';
COMMENT ON COLUMN public.secret_shares.shared_by IS 'User who granted the access';
COMMENT ON COLUMN public.secret_shares.access_level IS 'Access level: read (view-only), future: write, admin';
COMMENT ON COLUMN public.secret_shares.expires_at IS 'Optional expiration date for temporary access';
COMMENT ON COLUMN public.secret_shares.notes IS 'Why this secret was shared';

-- Indexes for performance
CREATE INDEX idx_secret_shares_secret_id ON public.secret_shares(secret_id);
CREATE INDEX idx_secret_shares_user_id ON public.secret_shares(shared_with_user_id) WHERE shared_with_user_id IS NOT NULL;
CREATE INDEX idx_secret_shares_role_id ON public.secret_shares(shared_with_role_id) WHERE shared_with_role_id IS NOT NULL;
CREATE INDEX idx_secret_shares_expires_at ON public.secret_shares(expires_at) WHERE expires_at IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 1.2: secret_access_log - Full audit trail for secret operations
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.secret_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_id UUID NOT NULL REFERENCES public.secrets(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    access_granted_via TEXT,
    role_name TEXT,
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB
);

ALTER TABLE public.secret_access_log OWNER TO postgres;

-- Table comments
COMMENT ON TABLE public.secret_access_log IS 'Audit log for all secret access and sharing operations';
COMMENT ON COLUMN public.secret_access_log.operation IS 'Operation type: view, share, unshare, delete, create, update';
COMMENT ON COLUMN public.secret_access_log.access_granted_via IS 'How access was granted: owner, user_share, role_share, admin_override';
COMMENT ON COLUMN public.secret_access_log.role_name IS 'If accessed via role, which role provided access';
COMMENT ON COLUMN public.secret_access_log.metadata IS 'Additional context (JSON)';

-- Indexes for audit queries
CREATE INDEX idx_secret_access_log_secret_id ON public.secret_access_log(secret_id);
CREATE INDEX idx_secret_access_log_user_id ON public.secret_access_log(user_id);
CREATE INDEX idx_secret_access_log_timestamp ON public.secret_access_log(timestamp DESC);
CREATE INDEX idx_secret_access_log_operation ON public.secret_access_log(operation);

-- ============================================================================
-- PHASE 2: ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 2.1: RLS Policies for secret_shares
-- ----------------------------------------------------------------------------
ALTER TABLE public.secret_shares ENABLE ROW LEVEL SECURITY;

-- Users can SELECT shares for:
-- 1. Secrets they own
-- 2. Shares TO them (direct user shares)
-- 3. Shares TO roles they belong to
CREATE POLICY "secret_shares_select" ON public.secret_shares
    FOR SELECT
    USING (
        -- Secret owner can see all shares for their secrets
        secret_id IN (SELECT id FROM public.secrets WHERE user_id = auth.uid())
        OR
        -- User can see shares TO them
        shared_with_user_id = auth.uid()
        OR
        -- User can see role-based shares if they have that role
        shared_with_role_id IN (
            SELECT role_id FROM public.user_roles WHERE user_id = auth.uid()
        )
        OR
        -- Admins can see all shares
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = auth.uid() AND r.name = 'admin'
        )
    );

-- Users can INSERT shares if:
-- 1. They own the secret
-- 2. They are admin (admin override)
CREATE POLICY "secret_shares_insert" ON public.secret_shares
    FOR INSERT
    WITH CHECK (
        -- Only secret owner can create shares
        secret_id IN (SELECT id FROM public.secrets WHERE user_id = auth.uid())
        OR
        -- Admins can override and share any secret
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = auth.uid() AND r.name = 'admin'
        )
    );

-- Users can UPDATE shares if:
-- 1. They own the secret
-- 2. They are admin
CREATE POLICY "secret_shares_update" ON public.secret_shares
    FOR UPDATE
    USING (
        secret_id IN (SELECT id FROM public.secrets WHERE user_id = auth.uid())
        OR
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = auth.uid() AND r.name = 'admin'
        )
    );

-- Users can DELETE shares if:
-- 1. They own the secret
-- 2. They are admin
CREATE POLICY "secret_shares_delete" ON public.secret_shares
    FOR DELETE
    USING (
        secret_id IN (SELECT id FROM public.secrets WHERE user_id = auth.uid())
        OR
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = auth.uid() AND r.name = 'admin'
        )
    );

-- ----------------------------------------------------------------------------
-- 2.2: RLS Policies for secret_access_log
-- ----------------------------------------------------------------------------
ALTER TABLE public.secret_access_log ENABLE ROW LEVEL SECURITY;

-- Users can SELECT their own access logs
-- Admins can see all logs
CREATE POLICY "secret_access_log_select" ON public.secret_access_log
    FOR SELECT
    USING (
        user_id = auth.uid()
        OR
        EXISTS (
            SELECT 1 FROM public.user_roles ur
            JOIN public.roles r ON r.id = ur.role_id
            WHERE ur.user_id = auth.uid() AND r.name = 'admin'
        )
    );

-- Anyone can INSERT logs (for audit trail)
-- This is controlled by SECURITY DEFINER functions
CREATE POLICY "secret_access_log_insert" ON public.secret_access_log
    FOR INSERT
    WITH CHECK (true);

-- No one can UPDATE or DELETE logs (immutable audit trail)
-- Only database superuser/admin can modify via direct SQL

-- ============================================================================
-- PHASE 3: DATABASE FUNCTIONS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1: get_user_secrets_with_shared
--      Returns secrets user owns + secrets shared with them (user/role based)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_user_secrets_with_shared(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
) RETURNS TABLE (
    id UUID,
    website TEXT,
    username TEXT,
    password TEXT,
    notes TEXT,
    expiration_date TIMESTAMPTZ,
    tags JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    is_owner BOOLEAN,
    shared_by_email TEXT,
    access_level TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    WITH accessible_secrets AS (
        -- User's own secrets
        SELECT
            s.id,
            s.user_id,
            TRUE as is_owner,
            NULL::TEXT as shared_by_email,
            'owner'::TEXT as access_level
        FROM public.secrets s
        WHERE s.user_id = auth.uid()

        UNION

        -- Secrets shared directly with user
        SELECT
            s.id,
            s.user_id,
            FALSE as is_owner,
            u.email as shared_by_email,
            ss.access_level
        FROM public.secrets s
        JOIN public.secret_shares ss ON ss.secret_id = s.id
        JOIN auth.users u ON u.id = ss.shared_by
        WHERE ss.shared_with_user_id = auth.uid()
          AND (ss.expires_at IS NULL OR ss.expires_at > NOW())

        UNION

        -- Secrets shared via role membership
        SELECT
            s.id,
            s.user_id,
            FALSE as is_owner,
            u.email as shared_by_email,
            ss.access_level
        FROM public.secrets s
        JOIN public.secret_shares ss ON ss.secret_id = s.id
        JOIN auth.users u ON u.id = ss.shared_by
        WHERE ss.shared_with_role_id IN (
            SELECT role_id FROM public.user_roles WHERE user_id = auth.uid()
        )
        AND (ss.expires_at IS NULL OR ss.expires_at > NOW())
    )
    SELECT
        s.id,
        s.website,
        s.username,
        pgp_sym_decrypt(s.password_encrypted::bytea, get_encryption_key()) as password,
        s.notes,
        s.expiration_date,
        COALESCE(sm.tags, '[]'::jsonb) as tags,
        jsonb_build_object(
            'twofa_enabled', COALESCE(sm.twofa_enabled, false),
            'twofa_type', sm.twofa_type,
            'recovery_codes', COALESCE(sm.recovery_codes, '[]'::jsonb)
        ) as metadata,
        s.created_at,
        s.updated_at,
        a.is_owner,
        a.shared_by_email,
        a.access_level
    FROM accessible_secrets a
    JOIN public.secrets s ON s.id = a.id
    LEFT JOIN public.secret_metadata sm ON sm.secret_id = s.id
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

ALTER FUNCTION public.get_user_secrets_with_shared(INTEGER, INTEGER) OWNER TO postgres;
COMMENT ON FUNCTION public.get_user_secrets_with_shared IS 'Returns user''s own secrets + secrets shared with them (view-only)';

-- ----------------------------------------------------------------------------
-- 3.2: share_secret
--      Share a secret with a user or role
-- ----------------------------------------------------------------------------
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

    -- Get target name for response
    IF p_target_user_id IS NOT NULL THEN
        SELECT email INTO v_target_email
        FROM auth.users
        WHERE id = p_target_user_id;

        IF v_target_email IS NULL THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'User not found'
            );
        END IF;
    ELSE
        SELECT name INTO v_target_role_name
        FROM public.roles
        WHERE id = p_target_role_id;

        IF v_target_role_name IS NULL THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'Role not found'
            );
        END IF;
    END IF;

    -- Get secret website for logging
    SELECT website INTO v_secret_website
    FROM public.secrets
    WHERE id = p_secret_id;

    -- Insert or update share record
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
        p_target_role_id,
        auth.uid(),
        'read',
        p_expires_at,
        p_notes
    )
    ON CONFLICT (secret_id, shared_with_user_id)
    DO UPDATE SET
        expires_at = EXCLUDED.expires_at,
        notes = EXCLUDED.notes,
        created_at = NOW()
    WHERE secret_shares.secret_id = p_secret_id
      AND secret_shares.shared_with_user_id = p_target_user_id;

    -- Handle role conflict separately (PostgreSQL requires this)
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
        created_at = NOW()
    WHERE secret_shares.secret_id = p_secret_id
      AND secret_shares.shared_with_role_id = p_target_role_id
      AND p_target_role_id IS NOT NULL;

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
COMMENT ON FUNCTION public.share_secret IS 'Share a secret with a user or role (owner/admin only)';

-- ----------------------------------------------------------------------------
-- 3.3: unshare_secret
--      Revoke access to a secret
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.unshare_secret(
    p_secret_id UUID,
    p_target_user_id UUID DEFAULT NULL,
    p_target_role_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_is_owner BOOLEAN;
    v_is_admin BOOLEAN;
    v_deleted_count INTEGER;
BEGIN
    -- Check authorization
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
            'error', 'Unauthorized: You must be the owner or admin to revoke access'
        );
    END IF;

    -- Delete share record(s)
    DELETE FROM public.secret_shares
    WHERE secret_id = p_secret_id
    AND (
        (p_target_user_id IS NOT NULL AND shared_with_user_id = p_target_user_id)
        OR
        (p_target_role_id IS NOT NULL AND shared_with_role_id = p_target_role_id)
    );

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    IF v_deleted_count = 0 THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'No matching share found to revoke'
        );
    END IF;

    -- Log the unshare action
    INSERT INTO public.secret_access_log (
        secret_id,
        user_id,
        operation,
        access_granted_via,
        metadata
    ) VALUES (
        p_secret_id,
        auth.uid(),
        'unshare',
        CASE WHEN v_is_owner THEN 'owner' ELSE 'admin_override' END,
        jsonb_build_object(
            'target_user_id', p_target_user_id,
            'target_role_id', p_target_role_id
        )
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Access revoked successfully',
        'revoked_count', v_deleted_count
    );
END;
$$;

ALTER FUNCTION public.unshare_secret(UUID, UUID, UUID) OWNER TO postgres;
COMMENT ON FUNCTION public.unshare_secret IS 'Revoke access to a secret (owner/admin only)';

-- ----------------------------------------------------------------------------
-- 3.4: get_secret_shares
--      List all shares for a secret
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_secret_shares(
    p_secret_id UUID
) RETURNS TABLE (
    share_id UUID,
    shared_with_user_id UUID,
    shared_with_user_email TEXT,
    shared_with_role_id UUID,
    shared_with_role_name TEXT,
    access_level TEXT,
    shared_by_email TEXT,
    created_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    notes TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Check if user is owner or admin
    IF NOT EXISTS(
        SELECT 1 FROM public.secrets
        WHERE id = p_secret_id AND user_id = auth.uid()
    ) AND NOT EXISTS(
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = auth.uid() AND r.name = 'admin'
    ) THEN
        RAISE EXCEPTION 'Unauthorized: You must be the owner or admin to view shares';
    END IF;

    RETURN QUERY
    SELECT
        ss.id as share_id,
        ss.shared_with_user_id,
        u.email as shared_with_user_email,
        ss.shared_with_role_id,
        r.name as shared_with_role_name,
        ss.access_level,
        sb.email as shared_by_email,
        ss.created_at,
        ss.expires_at,
        ss.notes
    FROM public.secret_shares ss
    LEFT JOIN auth.users u ON u.id = ss.shared_with_user_id
    LEFT JOIN public.roles r ON r.id = ss.shared_with_role_id
    LEFT JOIN auth.users sb ON sb.id = ss.shared_by
    WHERE ss.secret_id = p_secret_id
    ORDER BY ss.created_at DESC;
END;
$$;

ALTER FUNCTION public.get_secret_shares(UUID) OWNER TO postgres;
COMMENT ON FUNCTION public.get_secret_shares IS 'List all shares for a secret (owner/admin only)';

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================

-- Grant necessary permissions
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT ALL ON public.secret_shares TO authenticated;
GRANT ALL ON public.secret_access_log TO authenticated;

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Secret sharing migration completed successfully';
    RAISE NOTICE '   - Created secret_shares table';
    RAISE NOTICE '   - Created secret_access_log table';
    RAISE NOTICE '   - Applied RLS policies';
    RAISE NOTICE '   - Created 4 database functions';
    RAISE NOTICE '   - Issue #81: Secret privilege management ready';
END $$;
