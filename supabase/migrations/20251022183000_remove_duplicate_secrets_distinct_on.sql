-- ============================================================================
-- Remove duplicate secrets when user both owns and is shared the same secret
-- Issue: If user shares their own secret with themselves, it appears twice
-- Fix: Use DISTINCT ON to ensure each secret appears only once
--      Prioritize ownership over sharing (owner > shared)
-- ============================================================================

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
SET search_path TO 'public, pg_catalog, auth'
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
    ),
    unique_secrets AS (
        -- Remove duplicates: if same secret appears as both owner and shared,
        -- keep only the owner entry (ownership takes precedence)
        SELECT DISTINCT ON (a.id)
            a.id,
            a.user_id,
            a.is_owner,
            a.shared_by_email,
            a.access_level
        FROM accessible_secrets a
        ORDER BY a.id, a.is_owner DESC  -- Owner (TRUE) sorts before shared (FALSE)
    )
    SELECT
        s.id,
        s.website,
        s.username,
        public.decrypt_text(s.password_encrypted) AS password,
        s.notes,
        s.expiration_date,
        COALESCE(
            (SELECT jsonb_agg(st.tag) FROM public.secret_tags st WHERE st.secret_id = s.id),
            '[]'::jsonb
        ) AS tags,
        COALESCE(
            (
                SELECT jsonb_build_object(
                    'twofa_enabled', sm.twofa_enabled,
                    'twofa_type', sm.twofa_type,
                    'twofa_secret', sm.twofa_secret,
                    'recovery_codes', CASE
                        WHEN sm.recovery_codes_encrypted IS NOT NULL
                        THEN public.decrypt_text(sm.recovery_codes_encrypted)::jsonb
                        ELSE '[]'::jsonb
                    END
                )
                FROM public.secret_metadata sm WHERE sm.secret_id = s.id
            ),
            '{}'::jsonb
        ) AS metadata,
        s.created_at,
        s.updated_at,
        u.is_owner,
        u.shared_by_email,
        u.access_level
    FROM unique_secrets u
    JOIN public.secrets s ON s.id = u.id
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

ALTER FUNCTION public.get_user_secrets_with_shared(INTEGER, INTEGER) OWNER TO postgres;
COMMENT ON FUNCTION public.get_user_secrets_with_shared IS 'Returns user''s own secrets + secrets shared with them - No duplicates (ownership takes precedence)';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Removed duplicate secrets in get_user_secrets_with_shared function';
    RAISE NOTICE '   - Added DISTINCT ON (id) to ensure each secret appears only once';
    RAISE NOTICE '   - When secret is both owned and shared, owner entry takes precedence';
    RAISE NOTICE '   - Fixes LazyColumn duplicate key error in UI';
END $$;
