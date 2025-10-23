-- ============================================================================
-- Fix decryption method in get_user_secrets_with_shared function
-- Issue: Using pgp_sym_decrypt() but secrets are encrypted with AES + base64
-- Fix: Use decrypt_text() like get_user_secrets() does
-- Also: Simplify by using subqueries instead of LEFT JOIN + GROUP BY
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
        a.is_owner,
        a.shared_by_email,
        a.access_level
    FROM accessible_secrets a
    JOIN public.secrets s ON s.id = a.id
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

ALTER FUNCTION public.get_user_secrets_with_shared(INTEGER, INTEGER) OWNER TO postgres;
COMMENT ON FUNCTION public.get_user_secrets_with_shared IS 'Returns user''s own secrets + secrets shared with them - Uses correct AES+base64 decryption';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Fixed decryption method in get_user_secrets_with_shared function';
    RAISE NOTICE '   - Changed from pgp_sym_decrypt() to decrypt_text()';
    RAISE NOTICE '   - Now uses correct AES + base64 decryption (same as admin panel)';
    RAISE NOTICE '   - Simplified by using subqueries instead of LEFT JOIN + GROUP BY';
END $$;
