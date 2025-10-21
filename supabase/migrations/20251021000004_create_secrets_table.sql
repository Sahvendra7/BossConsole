-- ============================================================================
-- BOSS Secret Manager - Database Schema
-- ============================================================================
-- Purpose:
-- - Create tables for secure credential storage
-- - Implement server-side encryption using pgcrypto
-- - Enable RBAC with secrets.write permission
-- - Support metadata (2FA, notes, tags, expiration)
--
-- Security:
-- - Passwords encrypted using pgcrypto symmetric encryption
-- - RLS policies ensure users only access their own secrets
-- - Unique constraint on (user_id, website, username)
-- ============================================================================

-- ============================================================================
-- 1. ENABLE ENCRYPTION EXTENSION
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 2. CREATE SECRETS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.secrets (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    website TEXT NOT NULL,
    username TEXT NOT NULL,
    password_encrypted TEXT NOT NULL,  -- Encrypted using pgcrypto
    notes TEXT,
    expiration_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure unique website:username per user
    CONSTRAINT unique_user_website_username UNIQUE (user_id, website, username)
);

COMMENT ON TABLE public.secrets IS 'Encrypted credential storage for website:username combinations';
COMMENT ON COLUMN public.secrets.website IS 'Website domain or URL';
COMMENT ON COLUMN public.secrets.username IS 'Username/email for the website';
COMMENT ON COLUMN public.secrets.password_encrypted IS 'Password encrypted with pgcrypto';
COMMENT ON COLUMN public.secrets.notes IS 'Optional notes about the credential';
COMMENT ON COLUMN public.secrets.expiration_date IS 'When the password should be rotated';

-- ============================================================================
-- 3. CREATE SECRET_METADATA TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.secret_metadata (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    secret_id UUID NOT NULL REFERENCES public.secrets(id) ON DELETE CASCADE UNIQUE,
    twofa_enabled BOOLEAN DEFAULT false NOT NULL,
    twofa_type TEXT,  -- 'app', 'sms', 'email', 'hardware'
    twofa_secret TEXT,  -- Encrypted TOTP secret if applicable
    recovery_codes_encrypted TEXT,  -- Encrypted JSON array of recovery codes
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Validate 2FA type
    CONSTRAINT valid_twofa_type CHECK (
        twofa_type IS NULL OR
        twofa_type IN ('app', 'sms', 'email', 'hardware')
    )
);

COMMENT ON TABLE public.secret_metadata IS '2FA and recovery code information for secrets';
COMMENT ON COLUMN public.secret_metadata.twofa_enabled IS 'Whether 2FA is enabled for this credential';
COMMENT ON COLUMN public.secret_metadata.twofa_type IS 'Type of 2FA: app, sms, email, hardware';
COMMENT ON COLUMN public.secret_metadata.twofa_secret IS 'Encrypted TOTP secret for authenticator apps';
COMMENT ON COLUMN public.secret_metadata.recovery_codes_encrypted IS 'Encrypted JSON array of backup codes';

-- ============================================================================
-- 4. CREATE SECRET_TAGS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.secret_tags (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    secret_id UUID NOT NULL REFERENCES public.secrets(id) ON DELETE CASCADE,
    tag TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    -- Ensure unique tag per secret
    CONSTRAINT unique_secret_tag UNIQUE (secret_id, tag)
);

COMMENT ON TABLE public.secret_tags IS 'Tags/categories for organizing secrets';
COMMENT ON COLUMN public.secret_tags.tag IS 'Tag name (e.g., "work", "personal", "important")';

-- ============================================================================
-- 5. CREATE INDEXES
-- ============================================================================

CREATE INDEX idx_secrets_user_id ON public.secrets(user_id);
CREATE INDEX idx_secrets_website ON public.secrets(website);
CREATE INDEX idx_secrets_expiration ON public.secrets(expiration_date) WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_secret_metadata_secret_id ON public.secret_metadata(secret_id);
CREATE INDEX idx_secret_tags_secret_id ON public.secret_tags(secret_id);
CREATE INDEX idx_secret_tags_tag ON public.secret_tags(tag);

-- ============================================================================
-- 6. ENCRYPTION HELPER FUNCTIONS
-- ============================================================================

-- Get encryption key from environment (fallback for development)
CREATE OR REPLACE FUNCTION get_encryption_key()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    -- In production, this should come from Supabase secrets
    -- For now, use a default key (CHANGE IN PRODUCTION!)
    RETURN 'default-encryption-key-change-in-production';
END;
$$;

-- Encrypt text using pgcrypto
CREATE OR REPLACE FUNCTION encrypt_text(plaintext TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    encryption_key TEXT;
BEGIN
    encryption_key := get_encryption_key();
    RETURN encode(
        encrypt(
            plaintext::bytea,
            encryption_key::bytea,
            'aes'
        ),
        'base64'
    );
END;
$$;

-- Decrypt text using pgcrypto
CREATE OR REPLACE FUNCTION decrypt_text(ciphertext TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    encryption_key TEXT;
BEGIN
    IF ciphertext IS NULL THEN
        RETURN NULL;
    END IF;

    encryption_key := get_encryption_key();
    RETURN convert_from(
        decrypt(
            decode(ciphertext, 'base64'),
            encryption_key::bytea,
            'aes'
        ),
        'utf8'
    );
END;
$$;

-- ============================================================================
-- 7. ENABLE ROW LEVEL SECURITY
-- ============================================================================

ALTER TABLE public.secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.secret_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.secret_tags ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 8. CREATE RLS POLICIES FOR SECRETS
-- ============================================================================

-- Users can view their own secrets
CREATE POLICY "Users can view own secrets"
    ON public.secrets
    FOR SELECT
    USING (auth.uid() = user_id);

-- Users can create their own secrets
CREATE POLICY "Users can create own secrets"
    ON public.secrets
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- Users can update their own secrets
CREATE POLICY "Users can update own secrets"
    ON public.secrets
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Users can delete their own secrets
CREATE POLICY "Users can delete own secrets"
    ON public.secrets
    FOR DELETE
    USING (auth.uid() = user_id);

-- ============================================================================
-- 9. CREATE RLS POLICIES FOR SECRET_METADATA
-- ============================================================================

-- Users can view metadata for their own secrets
CREATE POLICY "Users can view own secret metadata"
    ON public.secret_metadata
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_metadata.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- Users can create metadata for their own secrets
CREATE POLICY "Users can create own secret metadata"
    ON public.secret_metadata
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_metadata.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- Users can update metadata for their own secrets
CREATE POLICY "Users can update own secret metadata"
    ON public.secret_metadata
    FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_metadata.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- Users can delete metadata for their own secrets
CREATE POLICY "Users can delete own secret metadata"
    ON public.secret_metadata
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_metadata.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- ============================================================================
-- 10. CREATE RLS POLICIES FOR SECRET_TAGS
-- ============================================================================

-- Users can view tags for their own secrets
CREATE POLICY "Users can view own secret tags"
    ON public.secret_tags
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_tags.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- Users can create tags for their own secrets
CREATE POLICY "Users can create own secret tags"
    ON public.secret_tags
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_tags.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- Users can delete tags from their own secrets
CREATE POLICY "Users can delete own secret tags"
    ON public.secret_tags
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.secrets
            WHERE secrets.id = secret_tags.secret_id
            AND secrets.user_id = auth.uid()
        )
    );

-- ============================================================================
-- 11. CREATE SECRETS.WRITE PERMISSION
-- ============================================================================

-- Add secrets.write permission to permissions table
INSERT INTO public.permissions (name, description, is_system)
VALUES ('secrets.write', 'Create and manage secrets in the secret manager', false)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- 12. CREATE RPC FUNCTIONS
-- ============================================================================

-- Get user secrets with decrypted passwords (paginated)
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
        decrypt_text(s.password_encrypted) AS password,
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
                        THEN decrypt_text(sm.recovery_codes_encrypted)::jsonb
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

-- Search user secrets by website or username
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
        decrypt_text(s.password_encrypted) AS password,
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
                        THEN decrypt_text(sm.recovery_codes_encrypted)::jsonb
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

-- Create a new secret with encrypted password
CREATE OR REPLACE FUNCTION public.create_secret(
    p_website TEXT,
    p_username TEXT,
    p_password TEXT,
    p_notes TEXT DEFAULT NULL,
    p_expiration_date TIMESTAMPTZ DEFAULT NULL,
    p_tags TEXT[] DEFAULT NULL,
    p_twofa_enabled BOOLEAN DEFAULT false,
    p_twofa_type TEXT DEFAULT NULL,
    p_recovery_codes TEXT[] DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_secret_id UUID;
    v_encrypted_password TEXT;
    v_encrypted_codes TEXT;
BEGIN
    -- Encrypt password
    v_encrypted_password := encrypt_text(p_password);

    -- Insert secret
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
        v_encrypted_password,
        p_notes,
        p_expiration_date
    )
    RETURNING id INTO v_secret_id;

    -- Insert metadata if 2FA is enabled
    IF p_twofa_enabled THEN
        -- Encrypt recovery codes if provided
        IF p_recovery_codes IS NOT NULL AND array_length(p_recovery_codes, 1) > 0 THEN
            v_encrypted_codes := encrypt_text(array_to_json(p_recovery_codes)::text);
        END IF;

        INSERT INTO public.secret_metadata (
            secret_id,
            twofa_enabled,
            twofa_type,
            recovery_codes_encrypted
        )
        VALUES (
            v_secret_id,
            p_twofa_enabled,
            p_twofa_type,
            v_encrypted_codes
        );
    END IF;

    -- Insert tags if provided
    IF p_tags IS NOT NULL AND array_length(p_tags, 1) > 0 THEN
        INSERT INTO public.secret_tags (secret_id, tag)
        SELECT v_secret_id, unnest(p_tags);
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'secret_id', v_secret_id,
        'message', 'Secret created successfully'
    );
EXCEPTION
    WHEN unique_violation THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'A secret for this website and username already exists'
        );
    WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', SQLERRM
        );
END;
$$;

-- Update an existing secret
CREATE OR REPLACE FUNCTION public.update_secret(
    p_secret_id UUID,
    p_website TEXT,
    p_username TEXT,
    p_password TEXT,
    p_notes TEXT DEFAULT NULL,
    p_expiration_date TIMESTAMPTZ DEFAULT NULL,
    p_tags TEXT[] DEFAULT NULL,
    p_twofa_enabled BOOLEAN DEFAULT false,
    p_twofa_type TEXT DEFAULT NULL,
    p_recovery_codes TEXT[] DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_encrypted_password TEXT;
    v_encrypted_codes TEXT;
BEGIN
    -- Verify ownership
    IF NOT EXISTS (
        SELECT 1 FROM public.secrets
        WHERE id = p_secret_id AND user_id = auth.uid()
    ) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Secret not found or access denied'
        );
    END IF;

    -- Encrypt password
    v_encrypted_password := encrypt_text(p_password);

    -- Update secret
    UPDATE public.secrets
    SET
        website = p_website,
        username = p_username,
        password_encrypted = v_encrypted_password,
        notes = p_notes,
        expiration_date = p_expiration_date,
        updated_at = NOW()
    WHERE id = p_secret_id;

    -- Update or create metadata
    IF p_twofa_enabled THEN
        -- Encrypt recovery codes if provided
        IF p_recovery_codes IS NOT NULL AND array_length(p_recovery_codes, 1) > 0 THEN
            v_encrypted_codes := encrypt_text(array_to_json(p_recovery_codes)::text);
        END IF;

        INSERT INTO public.secret_metadata (
            secret_id,
            twofa_enabled,
            twofa_type,
            recovery_codes_encrypted
        )
        VALUES (
            p_secret_id,
            p_twofa_enabled,
            p_twofa_type,
            v_encrypted_codes
        )
        ON CONFLICT (secret_id) DO UPDATE
        SET
            twofa_enabled = p_twofa_enabled,
            twofa_type = p_twofa_type,
            recovery_codes_encrypted = v_encrypted_codes,
            updated_at = NOW();
    ELSE
        -- Remove metadata if 2FA is disabled
        DELETE FROM public.secret_metadata WHERE secret_id = p_secret_id;
    END IF;

    -- Update tags (delete old, insert new)
    DELETE FROM public.secret_tags WHERE secret_id = p_secret_id;
    IF p_tags IS NOT NULL AND array_length(p_tags, 1) > 0 THEN
        INSERT INTO public.secret_tags (secret_id, tag)
        SELECT p_secret_id, unnest(p_tags);
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Secret updated successfully'
    );
EXCEPTION
    WHEN unique_violation THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'A secret for this website and username already exists'
        );
    WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', SQLERRM
        );
END;
$$;

-- Delete a secret (cascades to metadata and tags)
CREATE OR REPLACE FUNCTION public.delete_secret(
    p_secret_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    -- Verify ownership and delete
    DELETE FROM public.secrets
    WHERE id = p_secret_id AND user_id = auth.uid();

    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Secret not found or access denied'
        );
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Secret deleted successfully'
    );
EXCEPTION
    WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', SQLERRM
        );
END;
$$;

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================
