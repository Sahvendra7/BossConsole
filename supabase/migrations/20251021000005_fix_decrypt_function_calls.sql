-- ============================================================================
-- Fix decrypt_text function calls in RPC functions
-- ============================================================================
-- Issue: RPC functions with SET search_path = '' can't find decrypt_text()
-- Solution: Fully qualify function calls with schema name
-- ============================================================================

-- Drop existing functions first (return types changed)
DROP FUNCTION IF EXISTS public.get_user_secrets(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS public.search_user_secrets(TEXT, INTEGER, INTEGER);
DROP FUNCTION IF EXISTS public.create_secret(TEXT, TEXT, TEXT, TEXT, TIMESTAMPTZ, TEXT[], BOOLEAN, TEXT, TEXT[]);
DROP FUNCTION IF EXISTS public.update_secret(UUID, TEXT, TEXT, TEXT, TEXT, TIMESTAMPTZ, TEXT[], BOOLEAN, TEXT, TEXT[]);
DROP FUNCTION IF EXISTS public.delete_secret(UUID);

-- Fix get_user_secrets to use public.decrypt_text
CREATE OR REPLACE FUNCTION public.get_user_secrets(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    id UUID,
    website TEXT,
    username TEXT,
    password TEXT,
    notes TEXT,
    expiration_date TIMESTAMPTZ,
    tags JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id,
        s.website,
        s.username,
        public.decrypt_text(s.password_encrypted) AS password,
        s.notes,
        s.expiration_date,
        COALESCE(
            (
                SELECT jsonb_agg(st.tag)
                FROM public.secret_tags st
                WHERE st.secret_id = s.id
            ),
            '[]'::jsonb
        ) AS tags,
        COALESCE(
            (
                SELECT jsonb_build_object(
                    'twofa_enabled', sm.twofa_enabled,
                    'twofa_type', sm.twofa_type,
                    'recovery_codes',
                    CASE
                        WHEN sm.recovery_codes_encrypted IS NOT NULL
                        THEN public.decrypt_text(sm.recovery_codes_encrypted)::jsonb
                        ELSE '[]'::jsonb
                    END
                )
                FROM public.secret_metadata sm
                WHERE sm.secret_id = s.id
            ),
            '{}'::jsonb
        ) AS metadata,
        s.created_at,
        s.updated_at
    FROM public.secrets s
    WHERE s.user_id = auth.uid()
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

-- Fix search_user_secrets to use public.decrypt_text
CREATE OR REPLACE FUNCTION public.search_user_secrets(
    p_query TEXT,
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    id UUID,
    website TEXT,
    username TEXT,
    password TEXT,
    notes TEXT,
    expiration_date TIMESTAMPTZ,
    tags JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id,
        s.website,
        s.username,
        public.decrypt_text(s.password_encrypted) AS password,
        s.notes,
        s.expiration_date,
        COALESCE(
            (
                SELECT jsonb_agg(st.tag)
                FROM public.secret_tags st
                WHERE st.secret_id = s.id
            ),
            '[]'::jsonb
        ) AS tags,
        COALESCE(
            (
                SELECT jsonb_build_object(
                    'twofa_enabled', sm.twofa_enabled,
                    'twofa_type', sm.twofa_type,
                    'recovery_codes',
                    CASE
                        WHEN sm.recovery_codes_encrypted IS NOT NULL
                        THEN public.decrypt_text(sm.recovery_codes_encrypted)::jsonb
                        ELSE '[]'::jsonb
                    END
                )
                FROM public.secret_metadata sm
                WHERE sm.secret_id = s.id
            ),
            '{}'::jsonb
        ) AS metadata,
        s.created_at,
        s.updated_at
    FROM public.secrets s
    WHERE s.user_id = auth.uid()
    AND (
        s.website ILIKE '%' || p_query || '%'
        OR s.username ILIKE '%' || p_query || '%'
    )
    ORDER BY s.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$;

-- Fix create_secret to use public.encrypt_text
CREATE OR REPLACE FUNCTION public.create_secret(
    p_website TEXT,
    p_username TEXT,
    p_password TEXT,
    p_notes TEXT DEFAULT NULL,
    p_expiration_date TIMESTAMPTZ DEFAULT NULL,
    p_tags TEXT[] DEFAULT '{}',
    p_twofa_enabled BOOLEAN DEFAULT false,
    p_twofa_type TEXT DEFAULT NULL,
    p_recovery_codes TEXT[] DEFAULT '{}'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_secret_id UUID;
    v_metadata_id UUID;
    v_tag TEXT;
BEGIN
    -- Insert secret with encrypted password
    INSERT INTO public.secrets (
        user_id,
        website,
        username,
        password_encrypted,
        notes,
        expiration_date
    )
    VALUES (
        auth.uid(),
        p_website,
        p_username,
        public.encrypt_text(p_password),
        p_notes,
        p_expiration_date
    )
    RETURNING id INTO v_secret_id;

    -- Insert metadata if 2FA is enabled
    IF p_twofa_enabled THEN
        INSERT INTO public.secret_metadata (
            secret_id,
            twofa_enabled,
            twofa_type,
            recovery_codes_encrypted
        )
        VALUES (
            v_secret_id,
            true,
            p_twofa_type,
            CASE
                WHEN array_length(p_recovery_codes, 1) > 0
                THEN public.encrypt_text(array_to_json(p_recovery_codes)::text)
                ELSE NULL
            END
        );
    END IF;

    -- Insert tags
    IF array_length(p_tags, 1) > 0 THEN
        FOREACH v_tag IN ARRAY p_tags
        LOOP
            INSERT INTO public.secret_tags (secret_id, tag)
            VALUES (v_secret_id, v_tag);
        END LOOP;
    END IF;

    RETURN v_secret_id;
END;
$$;

-- Fix update_secret to use public.encrypt_text
CREATE OR REPLACE FUNCTION public.update_secret(
    p_secret_id UUID,
    p_website TEXT,
    p_username TEXT,
    p_password TEXT,
    p_notes TEXT DEFAULT NULL,
    p_expiration_date TIMESTAMPTZ DEFAULT NULL,
    p_tags TEXT[] DEFAULT '{}',
    p_twofa_enabled BOOLEAN DEFAULT false,
    p_twofa_type TEXT DEFAULT NULL,
    p_recovery_codes TEXT[] DEFAULT '{}'
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_tag TEXT;
BEGIN
    -- Verify ownership
    IF NOT EXISTS (
        SELECT 1 FROM public.secrets
        WHERE id = p_secret_id AND user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'Secret not found or access denied';
    END IF;

    -- Update secret
    UPDATE public.secrets
    SET
        website = p_website,
        username = p_username,
        password_encrypted = public.encrypt_text(p_password),
        notes = p_notes,
        expiration_date = p_expiration_date,
        updated_at = NOW()
    WHERE id = p_secret_id;

    -- Delete existing tags
    DELETE FROM public.secret_tags WHERE secret_id = p_secret_id;

    -- Insert new tags
    IF array_length(p_tags, 1) > 0 THEN
        FOREACH v_tag IN ARRAY p_tags
        LOOP
            INSERT INTO public.secret_tags (secret_id, tag)
            VALUES (p_secret_id, v_tag);
        END LOOP;
    END IF;

    -- Update or create metadata
    IF p_twofa_enabled THEN
        INSERT INTO public.secret_metadata (
            secret_id,
            twofa_enabled,
            twofa_type,
            recovery_codes_encrypted
        )
        VALUES (
            p_secret_id,
            true,
            p_twofa_type,
            CASE
                WHEN array_length(p_recovery_codes, 1) > 0
                THEN public.encrypt_text(array_to_json(p_recovery_codes)::text)
                ELSE NULL
            END
        )
        ON CONFLICT (secret_id)
        DO UPDATE SET
            twofa_enabled = true,
            twofa_type = p_twofa_type,
            recovery_codes_encrypted = CASE
                WHEN array_length(p_recovery_codes, 1) > 0
                THEN public.encrypt_text(array_to_json(p_recovery_codes)::text)
                ELSE NULL
            END,
            updated_at = NOW();
    ELSE
        DELETE FROM public.secret_metadata WHERE secret_id = p_secret_id;
    END IF;

    RETURN true;
END;
$$;

-- Delete secret function (no changes needed, but included for completeness)
CREATE OR REPLACE FUNCTION public.delete_secret(
    p_secret_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    -- Delete will cascade to metadata and tags
    DELETE FROM public.secrets
    WHERE id = p_secret_id AND user_id = auth.uid();

    RETURN FOUND;
END;
$$;

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION public.get_user_secrets TO authenticated;
GRANT EXECUTE ON FUNCTION public.search_user_secrets TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_secret TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_secret TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_secret TO authenticated;
