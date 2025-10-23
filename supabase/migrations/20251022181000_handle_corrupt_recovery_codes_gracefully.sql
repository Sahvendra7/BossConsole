-- ============================================================================
-- Handle corrupt recovery codes gracefully in get_user_secrets_with_shared
-- Issue: Corrupted recovery_codes_encrypted causes "Wrong key or corrupt data"
--        and crashes the entire query
-- Fix: Wrap decryption in exception handler to return empty array on failure
-- ============================================================================

-- Helper function to safely decrypt recovery codes
CREATE OR REPLACE FUNCTION public.safe_decrypt_recovery_codes(
    encrypted_data TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF encrypted_data IS NULL THEN
        RETURN '[]'::jsonb;
    END IF;

    BEGIN
        RETURN (pgp_sym_decrypt(encrypted_data::bytea, get_encryption_key())::text)::jsonb;
    EXCEPTION
        WHEN OTHERS THEN
            -- If decryption fails (corrupt data, wrong key, etc.), return empty array
            RETURN '[]'::jsonb;
    END;
END;
$$;

ALTER FUNCTION public.safe_decrypt_recovery_codes(TEXT) OWNER TO postgres;
COMMENT ON FUNCTION public.safe_decrypt_recovery_codes IS 'Safely decrypt recovery codes, returning empty array on failure';

-- Update get_user_secrets_with_shared to use safe decryption
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
            'recovery_codes', safe_decrypt_recovery_codes(sm.recovery_codes_encrypted)
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
COMMENT ON FUNCTION public.get_user_secrets_with_shared IS 'Returns user''s own secrets + secrets shared with them - Gracefully handles corrupt recovery codes';

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Added graceful handling for corrupt recovery codes';
    RAISE NOTICE '   - Created safe_decrypt_recovery_codes() helper function';
    RAISE NOTICE '   - Returns empty array instead of crashing on decryption errors';
    RAISE NOTICE '   - Prevents "Wrong key or corrupt data" from breaking the query';
END $$;
