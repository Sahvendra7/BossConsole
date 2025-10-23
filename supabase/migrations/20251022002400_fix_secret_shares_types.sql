-- ============================================================================
-- Fix type mismatch in get_secret_shares function
-- Issue: varchar(255) columns need to be cast to text for return type
-- ============================================================================

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
        u.email::text as shared_with_user_email,
        ss.shared_with_role_id,
        r.name::text as shared_with_role_name,
        ss.access_level,
        sb.email::text as shared_by_email,
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
COMMENT ON FUNCTION public.get_secret_shares IS 'List all shares for a secret (owner/admin only) - Fixed type casting';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Fixed type mismatch in get_secret_shares function';
    RAISE NOTICE '   - Cast email and name columns to TEXT';
END $$;
