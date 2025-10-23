-- ============================================================================
-- Fix NULL handling in get_user_secrets_with_shared function
-- Issue: Attempting to decrypt NULL values causes "Wrong key or corrupt data"
-- Fix: Use CASE statements to check for NULL before attempting decryption
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
        COALESCE(
            jsonb_agg(st.tag) FILTER (WHERE st.tag IS NOT NULL),
            '[]'::jsonb
        ) as tags,
        jsonb_build_object(
            'twofa_enabled', COALESCE(sm.twofa_enabled, false),
            'twofa_type', sm.twofa_type,
            'twofa_secret', sm.twofa_secret,
            'recovery_codes', CASE
                WHEN sm.recovery_codes_encrypted IS NOT NULL THEN
                    (pgp_sym_decrypt(sm.recovery_codes_encrypted::bytea, get_encryption_key())::text)::jsonb
                ELSE
                    '[]'::jsonb
            END
        ) as metadata,
        s.created_at,
        s.updated_at,
        a.is_owner,
        a.shared_by_email,
        a.access_level
    FROM accessible_secrets a
    JOIN public.secrets s ON s.id = a.id
    LEFT JOIN public.secret_tags st ON st.secret_id = s.id
    LEFT JOIN public.secret_metadata sm ON sm.secret_id = s.id
    GROUP BY
        s.id,
        s.website,
        s.username,
        s.password_encrypted,
        s.notes,
        s.expiration_date,
        s.created_at,
        s.updated_at,
        a.is_owner,
        a.shared_by_email,
        a.access_level,
        sm.twofa_enabled,
        sm.twofa_type,
        sm.twofa_secret,
        sm.recovery_codes_encrypted
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

ALTER FUNCTION public.get_user_secrets_with_shared(INTEGER, INTEGER) OWNER TO postgres;
COMMENT ON FUNCTION public.get_user_secrets_with_shared IS 'Returns user''s own secrets + secrets shared with them - Fixed NULL handling to prevent decryption errors';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Fixed NULL handling in get_user_secrets_with_shared function';
    RAISE NOTICE '   - Added CASE statement to check for NULL before decrypting recovery_codes_encrypted';
    RAISE NOTICE '   - Prevents "Wrong key or corrupt data" error when metadata is missing';
END $$;
