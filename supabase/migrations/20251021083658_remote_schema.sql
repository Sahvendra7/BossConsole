
-- ============================================================================
-- BOSS Database Schema - Complete Migration
-- Generated: 2025-10-21
--
-- This file contains the complete database schema including:
--   • Extensions and custom types
--   • Functions for RBAC, authentication, and secrets management
--   • Tables with RLS policies
--   • Grants and default privileges
--   • Triggers for user management
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Section 1: PostgreSQL Session Configuration
-- ----------------------------------------------------------------------------

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;
COMMENT ON SCHEMA "public" IS 'standard public schema';

-- ----------------------------------------------------------------------------
-- Section 2: PostgreSQL Extensions
-- Extensions for encryption, GraphQL, statistics, and UUID generation
-- ----------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";
-- ----------------------------------------------------------------------------
-- Section 3: Custom Types (ENUMs)
-- ----------------------------------------------------------------------------
-- Note: Old ENUMs (app_permission, app_role) have been replaced by tables
-- Only challenge_type is still in use for WebAuthn flows

CREATE TYPE "public"."challenge_type" AS ENUM (
    'registration',
    'authentication'
);
ALTER TYPE "public"."challenge_type" OWNER TO "postgres";
COMMENT ON TYPE "public"."challenge_type" IS 'WebAuthn challenge types: registration or authentication';

-- ----------------------------------------------------------------------------
-- Section 4: Database Functions
-- ----------------------------------------------------------------------------

-- ============================================================
-- 4.1: RBAC (Role-Based Access Control) Functions
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permission_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT id INTO v_role_id FROM public.roles WHERE name = role_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" not found', role_name));
    END IF;

    SELECT id INTO v_permission_id FROM public.permissions WHERE name = permission_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Permission "%s" not found', permission_name));
    END IF;

    INSERT INTO public.role_permissions (role_id, permission_id)
    VALUES (v_role_id, v_permission_id)
    ON CONFLICT (role_id, permission_id) DO NOTHING;

    RETURN jsonb_build_object('success', true, 'message', format('Permission "%s" assigned to role "%s"', permission_name, role_name));
END;
$$;
ALTER FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") IS 'v2025-10-21: Assigns permission to role in TABLE';

CREATE OR REPLACE FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_role_id UUID;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role % does not exist', target_role;
    END IF;

    -- Insert role into table (or do nothing if already exists)
    INSERT INTO public.user_roles (user_id, role_id, assigned_by, assigned_at)
    VALUES (target_user_id, v_role_id, auth.uid(), NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RETURN TRUE;
END;
$$;
ALTER FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") IS 'Assign a role to a user using table-based schema (admin only).';

CREATE OR REPLACE FUNCTION "public"."authorize"("requested_permission" "text") RETURNS boolean
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    user_role_ids UUID[];
BEGIN
    -- Get all role IDs for the current user
    SELECT ARRAY_AGG(role_id) INTO user_role_ids
    FROM public.user_roles
    WHERE user_id = auth.uid();

    -- Check if any of the user's roles have the requested permission
    RETURN EXISTS (
        SELECT 1 FROM public.role_permissions rp
        JOIN public.permissions p ON p.id = rp.permission_id
        WHERE rp.role_id = ANY(user_role_ids)
        AND p.name = requested_permission
    );
END;
$$;
ALTER FUNCTION "public"."authorize"("requested_permission" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."authorize"("requested_permission" "text") IS 'Check if current user has a specific permission via their roles using table-based schema.';

CREATE OR REPLACE FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_role_id UUID;
    v_has_role BOOLEAN;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Check if user has this role
    SELECT EXISTS (
        SELECT 1
        FROM public.user_roles
        WHERE user_id = target_user_id
        AND role_id = v_role_id
    ) INTO v_has_role;

    RETURN v_has_role;
END;
$$;
ALTER FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") IS 'Checks if a user has a specific role by name (backward compatible with RoleService.kt)';
-- ============================================================
-- 4.2: Authentication & Passkey Functions
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."clean_expired_passkey_challenges"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  -- Delete expired challenges (by timestamp)
  DELETE FROM passkey_challenges 
  WHERE expires_at < NOW();
  
  -- Delete old failed/expired sessions (older than 1 hour)
  DELETE FROM passkey_challenges 
  WHERE status IN ('failed', 'expired') 
  AND created_at < NOW() - INTERVAL '1 hour';
  
  -- Mark very old in_progress sessions as expired (older than 15 minutes)
  UPDATE passkey_challenges 
  SET status = 'expired' 
  WHERE status = 'in_progress' 
  AND created_at < NOW() - INTERVAL '15 minutes';
END;
$$;
ALTER FUNCTION "public"."clean_expired_passkey_challenges"() OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."cleanup_expired_completed_authentications"() RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  DELETE FROM completed_authentications 
  WHERE expires_at_timestamp < NOW();
END;
$$;
ALTER FUNCTION "public"."cleanup_expired_completed_authentications"() OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
  challenge_id UUID;
  user_uuid UUID;
BEGIN
  -- Look up user by email
  SELECT id INTO user_uuid 
  FROM auth.users 
  WHERE email = p_user_email 
  AND email_confirmed_at IS NOT NULL;
  
  IF user_uuid IS NULL THEN
    RAISE EXCEPTION 'User not found or email not confirmed: %', p_user_email;
  END IF;
  
  -- Insert challenge with session info
  INSERT INTO passkey_challenges (
    user_id,
    challenge,
    type,
    expires_at,
    session_id,
    status,
    user_email
  ) VALUES (
    user_uuid,
    p_challenge,
    'registration',
    NOW() + INTERVAL '5 minutes',
    p_session_id,
    'pending',
    p_user_email
  ) RETURNING id INTO challenge_id;
  
  RETURN challenge_id;
END;
$$;
ALTER FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") IS 'Helper function to create mobile WebAuthn registration sessions with email lookup';
-- ============================================================
-- 4.3: Role & Permission Management Functions
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text" DEFAULT NULL::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $_$
DECLARE
    v_user_id UUID;
    v_permission_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;
    IF NOT (permission_name ~ '^[a-z][a-z0-9_]{1,30}\.[a-z][a-z0-9_]{1,30}$') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid permission format');
    END IF;
    IF EXISTS (SELECT 1 FROM public.permissions WHERE name = permission_name) THEN
        RETURN jsonb_build_object('success', false, 'error', format('Permission "%s" already exists', permission_name));
    END IF;

    INSERT INTO public.permissions (name, description, is_system)
    VALUES (permission_name, description, false)
    RETURNING id INTO v_permission_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', format('Permission "%s" created', permission_name),
        'permission_id', v_permission_id::text,
        'permission', permission_name
    );
END;
$_$;
ALTER FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text") IS 'v2025-10-21: Creates permission in TABLE';

CREATE OR REPLACE FUNCTION "public"."create_new_role"("role_name" "text", "description" "text" DEFAULT NULL::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $_$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;
    IF NOT (role_name ~ '^[a-z][a-z0-9_]{2,50}$') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid role format');
    END IF;
    IF EXISTS (SELECT 1 FROM public.roles WHERE name = role_name) THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" already exists', role_name));
    END IF;

    INSERT INTO public.roles (name, description, is_system)
    VALUES (role_name, description, false)
    RETURNING id INTO v_role_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', format('Role "%s" created', role_name),
        'role_id', v_role_id::text,
        'role', role_name
    );
END;
$_$;
ALTER FUNCTION "public"."create_new_role"("role_name" "text", "description" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."create_new_role"("role_name" "text", "description" "text") IS 'v2025-10-21: Creates role in TABLE';
-- ============================================================
-- 4.4: Secret Management Functions (Encryption)
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."create_secret"("p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text" DEFAULT NULL::"text", "p_expiration_date" timestamp with time zone DEFAULT NULL::timestamp with time zone, "p_tags" "text"[] DEFAULT NULL::"text"[], "p_twofa_enabled" boolean DEFAULT false, "p_twofa_type" "text" DEFAULT NULL::"text", "p_recovery_codes" "text"[] DEFAULT NULL::"text"[]) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, auth'
    AS $$
DECLARE
    v_secret_id UUID;
    v_encrypted_password TEXT;
    v_encrypted_codes TEXT;
BEGIN
    v_encrypted_password := public.encrypt_text(p_password);

    INSERT INTO public.secrets (
        user_id, website, username, password_encrypted, notes, expiration_date
    )
    VALUES (
        auth.uid(), p_website, p_username, v_encrypted_password, p_notes, p_expiration_date
    )
    RETURNING id INTO v_secret_id;

    IF p_twofa_enabled THEN
        IF p_recovery_codes IS NOT NULL AND array_length(p_recovery_codes, 1) > 0 THEN
            v_encrypted_codes := public.encrypt_text(array_to_json(p_recovery_codes)::text);
        END IF;

        INSERT INTO public.secret_metadata (secret_id, twofa_enabled, twofa_type, recovery_codes_encrypted)
        VALUES (v_secret_id, p_twofa_enabled, p_twofa_type, v_encrypted_codes);
    END IF;

    IF p_tags IS NOT NULL AND array_length(p_tags, 1) > 0 THEN
        INSERT INTO public.secret_tags (secret_id, tag)
        SELECT v_secret_id, unnest(p_tags);
    END IF;

    RETURN jsonb_build_object('success', true, 'secret_id', v_secret_id, 'message', 'Secret created successfully');
EXCEPTION
    WHEN unique_violation THEN
        RETURN jsonb_build_object('success', false, 'error', 'A secret for this website and username already exists');
    WHEN OTHERS THEN
        RETURN jsonb_build_object('success', false, 'error', SQLERRM);
END;
$$;
ALTER FUNCTION "public"."create_secret"("p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) OWNER TO "postgres";

-- ============================================================
-- 4.5: Auth Hooks & Triggers
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."custom_access_token_hook"("event" "jsonb") RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE
    AS $$
DECLARE
    claims jsonb;
    user_roles_array text[];
    primary_role text;
BEGIN
    -- Extract claims from the event
    claims := event->'claims';

    -- Use helper function to fetch all roles for the user
    user_roles_array := public.get_user_roles_for_hook((event->>'user_id')::uuid);

    -- Set primary role (first role, or 'user' if none)
    IF user_roles_array IS NOT NULL AND array_length(user_roles_array, 1) > 0 THEN
        primary_role := user_roles_array[1];
    ELSE
        primary_role := 'user';
    END IF;

    -- Inject custom claims
    IF user_roles_array IS NOT NULL THEN
        -- Set primary role claim
        claims := jsonb_set(claims, '{user_role}', to_jsonb(primary_role));

        -- Set all roles claim
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(user_roles_array));

        -- Set is_admin flag
        IF 'admin' = ANY(user_roles_array) THEN
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(true));
        ELSE
            claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
        END IF;
    ELSE
        -- User has no roles, set defaults
        claims := jsonb_set(claims, '{user_role}', to_jsonb('user'::text));
        claims := jsonb_set(claims, '{user_roles}', to_jsonb(ARRAY['user']::text[]));
        claims := jsonb_set(claims, '{is_admin}', to_jsonb(false));
    END IF;

    -- Update the event with modified claims
    event := jsonb_set(event, '{claims}', claims);

    RETURN event;
END;
$$;
ALTER FUNCTION "public"."custom_access_token_hook"("event" "jsonb") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") IS 'Auth hook that injects user roles into JWT claims (REFRESHED)';

CREATE OR REPLACE FUNCTION "public"."decrypt_text"("ciphertext" "text") RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, extensions'
    AS $$
DECLARE
    encryption_key TEXT;
BEGIN
    IF ciphertext IS NULL THEN
        RETURN NULL;
    END IF;

    encryption_key := public.get_encryption_key();
    RETURN pg_catalog.convert_from(
        extensions.decrypt(  -- <-- Fully qualified!
            pg_catalog.decode(ciphertext, 'base64'::text),
            encryption_key::bytea,
            'aes'::text
        ),
        'utf8'::name
    );
END;
$$;
ALTER FUNCTION "public"."decrypt_text"("ciphertext" "text") OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."delete_permission"("permission_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_perm_record RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT * INTO v_perm_record FROM public.permissions WHERE name = permission_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Permission "%s" not found', permission_name));
    END IF;
    IF v_perm_record.is_system THEN
        RETURN jsonb_build_object('success', false, 'error', 'Cannot delete system permission');
    END IF;

    DELETE FROM public.permissions WHERE name = permission_name;
    RETURN jsonb_build_object('success', true, 'message', format('Permission "%s" deleted', permission_name));
END;
$$;
ALTER FUNCTION "public"."delete_permission"("permission_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."delete_permission"("permission_name" "text") IS 'v2025-10-21: Deletes permission from TABLE';

CREATE OR REPLACE FUNCTION "public"."delete_role"("role_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_role_record RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT * INTO v_role_record FROM public.roles WHERE name = role_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" not found', role_name));
    END IF;
    IF v_role_record.is_system THEN
        RETURN jsonb_build_object('success', false, 'error', 'Cannot delete system role');
    END IF;

    DELETE FROM public.roles WHERE name = role_name;
    RETURN jsonb_build_object('success', true, 'message', format('Role "%s" deleted', role_name));
END;
$$;
ALTER FUNCTION "public"."delete_role"("role_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."delete_role"("role_name" "text") IS 'v2025-10-21: Deletes role from TABLE';

CREATE OR REPLACE FUNCTION "public"."delete_secret"("p_secret_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, auth'
    AS $$
BEGIN
    DELETE FROM public.secrets WHERE id = p_secret_id AND user_id = auth.uid();

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Secret not found or access denied');
    END IF;

    RETURN jsonb_build_object('success', true, 'message', 'Secret deleted successfully');
EXCEPTION
    WHEN OTHERS THEN
        RETURN jsonb_build_object('success', false, 'error', SQLERRM);
END;
$$;
ALTER FUNCTION "public"."delete_secret"("p_secret_id" "uuid") OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."delete_user"("target_user_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    -- Check if caller is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can delete users';
    END IF;

    -- Prevent deleting yourself
    IF target_user_id = auth.uid() THEN
        RAISE EXCEPTION 'Cannot delete your own account';
    END IF;

    -- Prevent deleting other admins (safety measure)
    IF public.is_user_admin(target_user_id) THEN
        RAISE EXCEPTION 'Cannot delete admin users. Remove admin role first.';
    END IF;

    -- Delete user's role assignments
    DELETE FROM public.user_roles WHERE user_id = target_user_id;

    -- Delete user record
    DELETE FROM public.users WHERE id = target_user_id;

    -- Note: Supabase Auth user will need to be deleted separately via Supabase Auth API
    -- This only deletes the public.users record and related data

    RETURN true;
END;
$$;
ALTER FUNCTION "public"."delete_user"("target_user_id" "uuid") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."delete_user"("target_user_id" "uuid") IS 'Delete a user and their associated data (admin only). Cannot delete self or other admins.';

CREATE OR REPLACE FUNCTION "public"."encrypt_text"("plaintext" "text") RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, extensions'
    AS $$
DECLARE
    encryption_key TEXT;
BEGIN
    encryption_key := public.get_encryption_key();
    RETURN pg_catalog.encode(
        extensions.encrypt(  -- <-- Fully qualified!
            plaintext::bytea,
            encryption_key::bytea,
            'aes'::text
        ),
        'base64'::text
    );
END;
$$;
ALTER FUNCTION "public"."encrypt_text"("plaintext" "text") OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."find_user_by_email"("p_email" "text") RETURNS TABLE("id" "uuid", "email" "text")
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  RETURN QUERY
  SELECT au.id, au.email::TEXT
  FROM auth.users au
  WHERE au.email = p_email
  LIMIT 1;
END;
$$;
ALTER FUNCTION "public"."find_user_by_email"("p_email" "text") OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."get_all_permissions"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_permissions JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        ) ORDER BY name
    ) INTO v_permissions FROM public.permissions;

    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_permissions, '[]'::jsonb));
END;
$$;
ALTER FUNCTION "public"."get_all_permissions"() OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_all_permissions"() IS 'v2025-10-21: Returns permissions from TABLE with data key';

CREATE OR REPLACE FUNCTION "public"."get_all_roles"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_roles JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        ) ORDER BY name
    ) INTO v_roles FROM public.roles;

    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_roles, '[]'::jsonb));
END;
$$;
ALTER FUNCTION "public"."get_all_roles"() OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_all_roles"() IS 'v2025-10-21: Returns roles from TABLE with data key';

CREATE OR REPLACE FUNCTION "public"."get_encryption_key"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, vault'
    AS $$
DECLARE
    encryption_key TEXT;
BEGIN
    -- Retrieve encryption key from Supabase Vault
    -- This ensures the key is never hardcoded in the codebase
    SELECT decrypted_secret INTO encryption_key
    FROM vault.decrypted_secrets
    WHERE name = 'master_encryption_key';

    IF encryption_key IS NULL THEN
        RAISE EXCEPTION 'Encryption key not found in vault. Please run: SELECT vault.create_secret(''<your-key>'', ''master_encryption_key'', ''Master key for encrypting user secrets'');';
    END IF;

    RETURN encryption_key;
END;
$$;
ALTER FUNCTION "public"."get_encryption_key"() OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."get_role_permissions"("role_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permissions JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT id INTO v_role_id FROM public.roles WHERE name = role_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" not found', role_name));
    END IF;

    SELECT jsonb_agg(p.name ORDER BY p.name)
    INTO v_permissions
    FROM public.role_permissions rp
    JOIN public.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = v_role_id;

    RETURN jsonb_build_object(
        'success', true,
        'role', role_name,
        'permissions', COALESCE(v_permissions, '[]'::jsonb)
    );
END;
$$;
ALTER FUNCTION "public"."get_role_permissions"("role_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_role_permissions"("role_name" "text") IS 'v2025-10-21: Gets role permissions from TABLE';

CREATE OR REPLACE FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_role_id UUID;
    v_permissions JSONB;
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = role_name;

    IF v_role_id IS NULL THEN
        RETURN '[]'::jsonb;
    END IF;

    -- Query role_permissions and JOIN with permissions to get permission names
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', rp.id::text,
            'role', role_name,
            'permission', p.name,
            'created_at', rp.created_at::text
        )
        ORDER BY p.name
    ) INTO v_permissions
    FROM public.role_permissions rp
    JOIN public.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = v_role_id;

    RETURN COALESCE(v_permissions, '[]'::jsonb);
END;
$$;
ALTER FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") IS 'Returns role permissions with names (not UUIDs) for backward compatibility with RoleService.kt';

CREATE OR REPLACE FUNCTION "public"."get_session_status"("p_session_id" "text") RETURNS TABLE("session_id" "text", "status" "text", "user_email" "text", "created_at" timestamp with time zone, "expires_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  RETURN QUERY
  SELECT 
    pc.session_id,
    pc.status,
    pc.user_email,
    pc.created_at,
    pc.expires_at
  FROM passkey_challenges pc
  WHERE pc.session_id = p_session_id
  AND pc.type = 'registration'
  ORDER BY pc.created_at DESC
  LIMIT 1;
END;
$$;
ALTER FUNCTION "public"."get_session_status"("p_session_id" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_session_status"("p_session_id" "text") IS 'Helper function to check the status of a mobile registration session';

CREATE OR REPLACE FUNCTION "public"."get_user_roles"("check_user_id" "uuid") RETURNS SETOF "text"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    RETURN QUERY
    SELECT r.name FROM public.user_roles ur
    JOIN public.roles r ON r.id = ur.role_id
    WHERE ur.user_id = check_user_id
    ORDER BY ur.assigned_at;
END;
$$;
ALTER FUNCTION "public"."get_user_roles"("check_user_id" "uuid") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_user_roles"("check_user_id" "uuid") IS 'Returns all role names assigned to a user using table-based schema.';

CREATE OR REPLACE FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") RETURNS "text"[]
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    RETURN (
        SELECT ARRAY_AGG(r.name ORDER BY ur.assigned_at)
        FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id
    );
END;
$$;
ALTER FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") IS 'Helper function for auth hook - returns user roles from table-based schema (REFRESHED)';

CREATE OR REPLACE FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_roles JSONB;
BEGIN
    -- Query user_roles table and JOIN with roles to get role names
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', ur.id::text,
            'user_id', ur.user_id::text,
            'role', r.name,
            'assigned_by', ur.assigned_by::text,
            'assigned_at', ur.assigned_at::text,
            'created_at', ur.created_at::text
        )
        ORDER BY ur.assigned_at
    ) INTO v_roles
    FROM public.user_roles ur
    JOIN public.roles r ON r.id = ur.role_id
    WHERE ur.user_id = target_user_id;

    RETURN COALESCE(v_roles, '[]'::jsonb);
END;
$$;
ALTER FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") IS 'Returns user roles with role names (not UUIDs) for backward compatibility with RoleService.kt';

CREATE OR REPLACE FUNCTION "public"."get_user_secrets"("p_limit" integer DEFAULT 50, "p_offset" integer DEFAULT 0) RETURNS TABLE("id" "uuid", "website" "text", "username" "text", "password" "text", "notes" "text", "expiration_date" timestamp with time zone, "tags" "jsonb", "metadata" "jsonb", "created_at" timestamp with time zone, "updated_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, auth'
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id, s.website, s.username,
        public.decrypt_text(s.password_encrypted) AS password,
        s.notes, s.expiration_date,
        COALESCE((SELECT jsonb_agg(st.tag) FROM public.secret_tags st WHERE st.secret_id = s.id), '[]'::jsonb) AS tags,
        COALESCE((
            SELECT jsonb_build_object(
                'twofa_enabled', sm.twofa_enabled,
                'twofa_type', sm.twofa_type,
                'recovery_codes', CASE WHEN sm.recovery_codes_encrypted IS NOT NULL
                    THEN public.decrypt_text(sm.recovery_codes_encrypted)::jsonb ELSE '[]'::jsonb END
            )
            FROM public.secret_metadata sm WHERE sm.secret_id = s.id
        ), '{}'::jsonb) AS metadata,
        s.created_at, s.updated_at
    FROM public.secrets s
    WHERE s.user_id = auth.uid()
    ORDER BY s.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$;
ALTER FUNCTION "public"."get_user_secrets"("p_limit" integer, "p_offset" integer) OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_role_id UUID;
BEGIN
    -- Insert user into public.users table
    INSERT INTO public.users (id, email, created_at, updated_at)
    VALUES (NEW.id, NEW.email, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

    -- Lookup 'user' role ID from roles table
    SELECT id INTO v_role_id FROM public.roles WHERE name = 'user';

    -- Assign default 'user' role using role_id (UUID) not role enum
    INSERT INTO public.user_roles (user_id, role_id, assigned_by, assigned_at)
    VALUES (NEW.id, v_role_id, NULL, NOW())
    ON CONFLICT (user_id, role_id) DO NOTHING;

    RETURN NEW;
END;
$$;
ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";
COMMENT ON FUNCTION "public"."handle_new_user"() IS 'Automatically creates user record and assigns default "user" role on signup.';

CREATE OR REPLACE FUNCTION "public"."handle_user_email_update"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.users
    SET email = NEW.email, updated_at = NOW()
    WHERE id = NEW.id;
    RETURN NEW;
END;
$$;
ALTER FUNCTION "public"."handle_user_email_update"() OWNER TO "postgres";

-- ============================================================
-- 4.6: User Management Functions
-- ============================================================

CREATE OR REPLACE FUNCTION "public"."is_user_admin"("check_user_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = 'admin'
    );
END;
$$;
ALTER FUNCTION "public"."is_user_admin"("check_user_id" "uuid") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."is_user_admin"("check_user_id" "uuid") IS 'Check if user is admin using table-based schema.';

CREATE OR REPLACE FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_user_id UUID;
    v_role_id UUID;
    v_permission_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied');
    END IF;

    SELECT id INTO v_role_id FROM public.roles WHERE name = role_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Role "%s" not found', role_name));
    END IF;

    SELECT id INTO v_permission_id FROM public.permissions WHERE name = permission_name;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', format('Permission "%s" not found', permission_name));
    END IF;

    DELETE FROM public.role_permissions
    WHERE role_id = v_role_id AND permission_id = v_permission_id;

    RETURN jsonb_build_object('success', true, 'message', format('Permission "%s" removed from role "%s"', permission_name, role_name));
END;
$$;
ALTER FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") IS 'v2025-10-21: Removes permission from role in TABLE';

CREATE OR REPLACE FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
DECLARE
    v_role_id UUID;
    v_current_user_id UUID := auth.uid();
BEGIN
    -- Look up role_id from role name
    SELECT id INTO v_role_id
    FROM public.roles
    WHERE name = target_role;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Role % does not exist', target_role;
    END IF;

    -- Prevent removing own admin role
    IF target_user_id = v_current_user_id AND target_role = 'admin' THEN
        RAISE EXCEPTION 'Cannot remove your own admin role';
    END IF;

    -- Remove role from table
    DELETE FROM public.user_roles
    WHERE user_id = target_user_id AND role_id = v_role_id;

    RETURN TRUE;
END;
$$;
ALTER FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") IS 'Remove a role from a user using table-based schema (admin only). Cannot remove own admin role or user role.';

CREATE OR REPLACE FUNCTION "public"."search_user_secrets"("p_query" "text", "p_limit" integer DEFAULT 50, "p_offset" integer DEFAULT 0) RETURNS TABLE("id" "uuid", "website" "text", "username" "text", "password" "text", "notes" "text", "expiration_date" timestamp with time zone, "tags" "jsonb", "metadata" "jsonb", "created_at" timestamp with time zone, "updated_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, auth'
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id, s.website, s.username,
        public.decrypt_text(s.password_encrypted) AS password,
        s.notes, s.expiration_date,
        COALESCE((SELECT jsonb_agg(st.tag) FROM public.secret_tags st WHERE st.secret_id = s.id), '[]'::jsonb) AS tags,
        COALESCE((
            SELECT jsonb_build_object(
                'twofa_enabled', sm.twofa_enabled,
                'twofa_type', sm.twofa_type,
                'recovery_codes', CASE WHEN sm.recovery_codes_encrypted IS NOT NULL
                    THEN public.decrypt_text(sm.recovery_codes_encrypted)::jsonb ELSE '[]'::jsonb END
            )
            FROM public.secret_metadata sm WHERE sm.secret_id = s.id
        ), '{}'::jsonb) AS metadata,
        s.created_at, s.updated_at
    FROM public.secrets s
    WHERE s.user_id = auth.uid()
    AND (s.website ILIKE '%' || p_query || '%' OR s.username ILIKE '%' || p_query || '%')
    ORDER BY s.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$;
ALTER FUNCTION "public"."search_user_secrets"("p_query" "text", "p_limit" integer, "p_offset" integer) OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."trigger_cleanup_expired_challenges"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  -- Only cleanup occasionally (10% chance) to reduce overhead
  IF random() < 0.1 THEN
    DELETE FROM passkey_challenges
    WHERE expires_at < NOW();
  END IF;

  RETURN NEW;
END;
$$;
ALTER FUNCTION "public"."trigger_cleanup_expired_challenges"() OWNER TO "postgres";
COMMENT ON FUNCTION "public"."trigger_cleanup_expired_challenges"() IS 'Trigger function that probabilistically cleans up expired challenges on insert';

CREATE OR REPLACE FUNCTION "public"."trigger_cleanup_expired_completed_auths"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  -- Only cleanup occasionally (10% chance) to reduce overhead
  IF random() < 0.1 THEN
    DELETE FROM completed_authentications
    WHERE expires_at_timestamp < NOW();
  END IF;

  RETURN NEW;
END;
$$;
ALTER FUNCTION "public"."trigger_cleanup_expired_completed_auths"() OWNER TO "postgres";
COMMENT ON FUNCTION "public"."trigger_cleanup_expired_completed_auths"() IS 'Trigger function that probabilistically cleans up expired completed authentications on insert';

CREATE OR REPLACE FUNCTION "public"."update_secret"("p_secret_id" "uuid", "p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text" DEFAULT NULL::"text", "p_expiration_date" timestamp with time zone DEFAULT NULL::timestamp with time zone, "p_tags" "text"[] DEFAULT NULL::"text"[], "p_twofa_enabled" boolean DEFAULT false, "p_twofa_type" "text" DEFAULT NULL::"text", "p_recovery_codes" "text"[] DEFAULT NULL::"text"[]) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public, pg_catalog, auth'
    AS $$
DECLARE
    v_encrypted_password TEXT;
    v_encrypted_codes TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.secrets WHERE id = p_secret_id AND user_id = auth.uid()) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Secret not found or access denied');
    END IF;

    v_encrypted_password := public.encrypt_text(p_password);

    UPDATE public.secrets
    SET website = p_website, username = p_username, password_encrypted = v_encrypted_password,
        notes = p_notes, expiration_date = p_expiration_date, updated_at = NOW()
    WHERE id = p_secret_id;

    IF p_twofa_enabled THEN
        IF p_recovery_codes IS NOT NULL AND array_length(p_recovery_codes, 1) > 0 THEN
            v_encrypted_codes := public.encrypt_text(array_to_json(p_recovery_codes)::text);
        END IF;

        INSERT INTO public.secret_metadata (secret_id, twofa_enabled, twofa_type, recovery_codes_encrypted)
        VALUES (p_secret_id, p_twofa_enabled, p_twofa_type, v_encrypted_codes)
        ON CONFLICT (secret_id) DO UPDATE
        SET twofa_enabled = p_twofa_enabled, twofa_type = p_twofa_type,
            recovery_codes_encrypted = v_encrypted_codes, updated_at = NOW();
    ELSE
        DELETE FROM public.secret_metadata WHERE secret_id = p_secret_id;
    END IF;

    DELETE FROM public.secret_tags WHERE secret_id = p_secret_id;
    IF p_tags IS NOT NULL AND array_length(p_tags, 1) > 0 THEN
        INSERT INTO public.secret_tags (secret_id, tag)
        SELECT p_secret_id, unnest(p_tags);
    END IF;

    RETURN jsonb_build_object('success', true, 'message', 'Secret updated successfully');
EXCEPTION
    WHEN unique_violation THEN
        RETURN jsonb_build_object('success', false, 'error', 'A secret for this website and username already exists');
    WHEN OTHERS THEN
        RETURN jsonb_build_object('success', false, 'error', SQLERRM);
END;
$$;
ALTER FUNCTION "public"."update_secret"("p_secret_id" "uuid", "p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) OWNER TO "postgres";
CREATE OR REPLACE FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") RETURNS boolean
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = check_user_id AND r.name = check_role
    );
END;
$$;
ALTER FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") OWNER TO "postgres";
COMMENT ON FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") IS 'Check if a user has a specific role using table-based schema.';

SET default_tablespace = '';

SET default_table_access_method = "heap";
-- ----------------------------------------------------------------------------
-- Section 5: Database Tables
-- ----------------------------------------------------------------------------

-- ============================================================
-- 5.1: Authentication Tables (WebAuthn/Passkeys)
-- ============================================================

CREATE TABLE IF NOT EXISTS "public"."user_passkeys" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "credential_id" "text" NOT NULL,
    "public_key" "text" NOT NULL,
    "display_name" "text" NOT NULL,
    "transports" "text"[] DEFAULT ARRAY['internal'::"text"],
    "created_at" bigint DEFAULT (EXTRACT(epoch FROM "now"()) * (1000)::numeric),
    "last_used_at" bigint,
    "active" boolean DEFAULT true,
    "attestation_object" "text",
    "created_by_ip" "inet" DEFAULT "inet_client_addr"(),
    "user_agent" "text"
);
ALTER TABLE "public"."user_passkeys" OWNER TO "postgres";
COMMENT ON TABLE "public"."user_passkeys" IS 'WebAuthn/FIDO2 passkey credentials for users';

COMMENT ON COLUMN "public"."user_passkeys"."credential_id" IS 'Base64url-encoded credential ID from WebAuthn';

COMMENT ON COLUMN "public"."user_passkeys"."public_key" IS 'Base64url-encoded public key for signature verification';

COMMENT ON COLUMN "public"."user_passkeys"."transports" IS 'Available transport methods (internal, usb, nfc, ble, hybrid)';

COMMENT ON COLUMN "public"."user_passkeys"."attestation_object" IS 'Base64url-encoded attestation object (optional)';
-- ----------------------------------------------------------------------------
-- Section 6: Views
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW "public"."active_user_passkeys" WITH ("security_invoker"='on') AS
 SELECT "id",
    "user_id",
    "credential_id",
    "display_name",
    "transports",
    "created_at",
    "last_used_at"
   FROM "public"."user_passkeys"
  WHERE ("active" = true);
ALTER VIEW "public"."active_user_passkeys" OWNER TO "postgres";
CREATE TABLE IF NOT EXISTS "public"."completed_authentications" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "challenge" "text" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "email" "text",
    "session_token" "text",
    "access_token" "text",
    "refresh_token" "text",
    "expires_at" bigint,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "expires_at_timestamp" timestamp with time zone DEFAULT ("now"() + '00:05:00'::interval),
    "session_id" "text"
);
ALTER TABLE "public"."completed_authentications" OWNER TO "postgres";
COMMENT ON TABLE "public"."completed_authentications" IS 'Temporary storage for completed cross-device authentications';

COMMENT ON COLUMN "public"."completed_authentications"."challenge" IS 'WebAuthn challenge that was completed';

COMMENT ON COLUMN "public"."completed_authentications"."user_id" IS 'User who completed the authentication';

COMMENT ON COLUMN "public"."completed_authentications"."expires_at_timestamp" IS 'When this record should be cleaned up';

COMMENT ON COLUMN "public"."completed_authentications"."session_id" IS 'Session ID for polling authentication status in cross-device flows';

CREATE TABLE IF NOT EXISTS "public"."passkey_challenges" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "challenge" "text" NOT NULL,
    "type" "public"."challenge_type" NOT NULL,
    "expires_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "created_by_ip" "inet" DEFAULT "inet_client_addr"(),
    "session_id" "text",
    "status" "text" DEFAULT 'pending'::"text",
    "user_email" "text",
    CONSTRAINT "passkey_challenges_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'in_progress'::"text", 'completed'::"text", 'failed'::"text", 'expired'::"text"])))
);
ALTER TABLE "public"."passkey_challenges" OWNER TO "postgres";
COMMENT ON TABLE "public"."passkey_challenges" IS 'Temporary storage for WebAuthn challenges during registration/authentication';

COMMENT ON COLUMN "public"."passkey_challenges"."type" IS 'Challenge type: registration or authentication';

COMMENT ON COLUMN "public"."passkey_challenges"."expires_at" IS 'Challenge expiration timestamp (typically 5 minutes)';

COMMENT ON COLUMN "public"."passkey_challenges"."session_id" IS 'Unique session ID for tracking cross-device WebAuthn flows';

COMMENT ON COLUMN "public"."passkey_challenges"."status" IS 'Session status: pending, in_progress, completed, failed, expired';

COMMENT ON COLUMN "public"."passkey_challenges"."user_email" IS 'User email for cross-device flows (lookup purposes)';

CREATE TABLE IF NOT EXISTS "public"."permissions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "is_system" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."permissions" OWNER TO "postgres";
COMMENT ON TABLE "public"."permissions" IS 'Application permissions (table-based replacement for app_permission enum). Supports full CRUD operations with system permission protection.';

COMMENT ON COLUMN "public"."permissions"."name" IS 'Unique permission name in domain.action format (e.g., "users.read")';

COMMENT ON COLUMN "public"."permissions"."description" IS 'Optional human-readable description';

COMMENT ON COLUMN "public"."permissions"."is_system" IS 'System permissions cannot be deleted';

CREATE TABLE IF NOT EXISTS "public"."role_permissions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "role_id" "uuid" NOT NULL,
    "permission_id" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."role_permissions" OWNER TO "postgres";
COMMENT ON TABLE "public"."role_permissions" IS 'Maps roles to permissions (NEW table-based version). Will replace role_permissions table.';

COMMENT ON COLUMN "public"."role_permissions"."role_id" IS 'Foreign key to roles table (replaces role enum)';

COMMENT ON COLUMN "public"."role_permissions"."permission_id" IS 'Foreign key to permissions table (replaces permission enum)';
-- ============================================================
-- 5.2: RBAC Tables (Table-based roles/permissions)
-- ============================================================

CREATE TABLE IF NOT EXISTS "public"."roles" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "is_system" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."roles" OWNER TO "postgres";
COMMENT ON TABLE "public"."roles" IS 'Application roles (table-based replacement for app_role enum). Supports full CRUD operations with system role protection.';

COMMENT ON COLUMN "public"."roles"."name" IS 'Unique role name (e.g., "user", "admin", "developer")';

COMMENT ON COLUMN "public"."roles"."description" IS 'Optional human-readable description';

COMMENT ON COLUMN "public"."roles"."is_system" IS 'System roles (user, admin) cannot be deleted';

CREATE TABLE IF NOT EXISTS "public"."secret_metadata" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "secret_id" "uuid" NOT NULL,
    "twofa_enabled" boolean DEFAULT false NOT NULL,
    "twofa_type" "text",
    "twofa_secret" "text",
    "recovery_codes_encrypted" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "valid_twofa_type" CHECK ((("twofa_type" IS NULL) OR ("twofa_type" = ANY (ARRAY['app'::"text", 'sms'::"text", 'email'::"text", 'hardware'::"text"]))))
);
ALTER TABLE "public"."secret_metadata" OWNER TO "postgres";
COMMENT ON TABLE "public"."secret_metadata" IS '2FA and recovery code information for secrets';

COMMENT ON COLUMN "public"."secret_metadata"."twofa_enabled" IS 'Whether 2FA is enabled for this credential';

COMMENT ON COLUMN "public"."secret_metadata"."twofa_type" IS 'Type of 2FA: app, sms, email, hardware';

COMMENT ON COLUMN "public"."secret_metadata"."twofa_secret" IS 'Encrypted TOTP secret for authenticator apps';

COMMENT ON COLUMN "public"."secret_metadata"."recovery_codes_encrypted" IS 'Encrypted JSON array of backup codes';

CREATE TABLE IF NOT EXISTS "public"."secret_tags" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "secret_id" "uuid" NOT NULL,
    "tag" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."secret_tags" OWNER TO "postgres";
COMMENT ON TABLE "public"."secret_tags" IS 'Tags/categories for organizing secrets';

COMMENT ON COLUMN "public"."secret_tags"."tag" IS 'Tag name (e.g., "work", "personal", "important")';
-- ============================================================
-- 5.3: Secret Management Tables (Password Manager)
-- ============================================================

CREATE TABLE IF NOT EXISTS "public"."secrets" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "website" "text" NOT NULL,
    "username" "text" NOT NULL,
    "password_encrypted" "text" NOT NULL,
    "notes" "text",
    "expiration_date" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."secrets" OWNER TO "postgres";
COMMENT ON TABLE "public"."secrets" IS 'Encrypted credential storage for website:username combinations';

COMMENT ON COLUMN "public"."secrets"."website" IS 'Website domain or URL';

COMMENT ON COLUMN "public"."secrets"."username" IS 'Username/email for the website';

COMMENT ON COLUMN "public"."secrets"."password_encrypted" IS 'Password encrypted with pgcrypto';

COMMENT ON COLUMN "public"."secrets"."notes" IS 'Optional notes about the credential';

COMMENT ON COLUMN "public"."secrets"."expiration_date" IS 'When the password should be rotated';

CREATE TABLE IF NOT EXISTS "public"."user_roles" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "role_id" "uuid" NOT NULL,
    "assigned_by" "uuid",
    "assigned_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."user_roles" OWNER TO "postgres";
COMMENT ON TABLE "public"."user_roles" IS 'Maps users to roles (NEW table-based version). Will replace user_roles table.';

COMMENT ON COLUMN "public"."user_roles"."role_id" IS 'Foreign key to roles table (replaces role enum)';
-- ============================================================
-- 5.4: User Management Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" "uuid" NOT NULL,
    "email" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);
ALTER TABLE "public"."users" OWNER TO "postgres";
COMMENT ON TABLE "public"."users" IS 'Application user data synced from auth.users';

ALTER TABLE ONLY "public"."completed_authentications"
    ADD CONSTRAINT "completed_authentications_challenge_key" UNIQUE ("challenge");

ALTER TABLE ONLY "public"."completed_authentications"
    ADD CONSTRAINT "completed_authentications_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."passkey_challenges"
    ADD CONSTRAINT "passkey_challenges_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."permissions"
    ADD CONSTRAINT "permissions_name_key" UNIQUE ("name");

ALTER TABLE ONLY "public"."permissions"
    ADD CONSTRAINT "permissions_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."role_permissions"
    ADD CONSTRAINT "role_permissions_new_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."role_permissions"
    ADD CONSTRAINT "role_permissions_new_role_id_permission_id_key" UNIQUE ("role_id", "permission_id");

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "roles_name_key" UNIQUE ("name");

ALTER TABLE ONLY "public"."roles"
    ADD CONSTRAINT "roles_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."secret_metadata"
    ADD CONSTRAINT "secret_metadata_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."secret_metadata"
    ADD CONSTRAINT "secret_metadata_secret_id_key" UNIQUE ("secret_id");

ALTER TABLE ONLY "public"."secret_tags"
    ADD CONSTRAINT "secret_tags_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."secrets"
    ADD CONSTRAINT "secrets_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."secret_tags"
    ADD CONSTRAINT "unique_secret_tag" UNIQUE ("secret_id", "tag");

ALTER TABLE ONLY "public"."secrets"
    ADD CONSTRAINT "unique_user_website_username" UNIQUE ("user_id", "website", "username");

ALTER TABLE ONLY "public"."user_passkeys"
    ADD CONSTRAINT "user_passkeys_credential_id_key" UNIQUE ("credential_id");

ALTER TABLE ONLY "public"."user_passkeys"
    ADD CONSTRAINT "user_passkeys_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."user_roles"
    ADD CONSTRAINT "user_roles_new_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."user_roles"
    ADD CONSTRAINT "user_roles_new_user_id_role_id_key" UNIQUE ("user_id", "role_id");

ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_email_key" UNIQUE ("email");

ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");
-- ----------------------------------------------------------------------------
-- Section 8: Indexes (Performance Optimization)
-- ----------------------------------------------------------------------------

CREATE INDEX "idx_completed_authentications_challenge" ON "public"."completed_authentications" USING "btree" ("challenge");

CREATE INDEX "idx_completed_authentications_expires" ON "public"."completed_authentications" USING "btree" ("expires_at_timestamp");

CREATE INDEX "idx_completed_authentications_session_id" ON "public"."completed_authentications" USING "btree" ("session_id");

CREATE INDEX "idx_passkey_challenges_challenge" ON "public"."passkey_challenges" USING "btree" ("challenge");

CREATE INDEX "idx_passkey_challenges_expires_at" ON "public"."passkey_challenges" USING "btree" ("expires_at");

CREATE INDEX "idx_passkey_challenges_session_id" ON "public"."passkey_challenges" USING "btree" ("session_id") WHERE ("session_id" IS NOT NULL);

CREATE INDEX "idx_passkey_challenges_status" ON "public"."passkey_challenges" USING "btree" ("status");

CREATE INDEX "idx_passkey_challenges_user_email" ON "public"."passkey_challenges" USING "btree" ("user_email") WHERE ("user_email" IS NOT NULL);

CREATE INDEX "idx_passkey_challenges_user_id" ON "public"."passkey_challenges" USING "btree" ("user_id");

CREATE INDEX "idx_permissions_is_system" ON "public"."permissions" USING "btree" ("is_system");

CREATE INDEX "idx_permissions_name" ON "public"."permissions" USING "btree" ("name");

CREATE INDEX "idx_role_permissions_permission_id" ON "public"."role_permissions" USING "btree" ("permission_id");

CREATE INDEX "idx_role_permissions_role_id" ON "public"."role_permissions" USING "btree" ("role_id");

CREATE INDEX "idx_roles_is_system" ON "public"."roles" USING "btree" ("is_system");

CREATE INDEX "idx_roles_name" ON "public"."roles" USING "btree" ("name");

CREATE INDEX "idx_secret_metadata_secret_id" ON "public"."secret_metadata" USING "btree" ("secret_id");

CREATE INDEX "idx_secret_tags_secret_id" ON "public"."secret_tags" USING "btree" ("secret_id");

CREATE INDEX "idx_secret_tags_tag" ON "public"."secret_tags" USING "btree" ("tag");

CREATE INDEX "idx_secrets_expiration" ON "public"."secrets" USING "btree" ("expiration_date") WHERE ("expiration_date" IS NOT NULL);

CREATE INDEX "idx_secrets_user_id" ON "public"."secrets" USING "btree" ("user_id");

CREATE INDEX "idx_secrets_website" ON "public"."secrets" USING "btree" ("website");

CREATE INDEX "idx_user_passkeys_credential_id" ON "public"."user_passkeys" USING "btree" ("credential_id") WHERE ("active" = true);

CREATE INDEX "idx_user_passkeys_user_id" ON "public"."user_passkeys" USING "btree" ("user_id") WHERE ("active" = true);

CREATE INDEX "idx_user_roles_role_id" ON "public"."user_roles" USING "btree" ("role_id");

CREATE INDEX "idx_user_roles_user_id" ON "public"."user_roles" USING "btree" ("user_id");

CREATE INDEX "idx_users_created_at" ON "public"."users" USING "btree" ("created_at");

CREATE INDEX "idx_users_email" ON "public"."users" USING "btree" ("email");

CREATE OR REPLACE TRIGGER "cleanup_expired_challenges_trigger" AFTER INSERT ON "public"."passkey_challenges" FOR EACH STATEMENT EXECUTE FUNCTION "public"."trigger_cleanup_expired_challenges"();

CREATE OR REPLACE TRIGGER "cleanup_expired_completed_auths_trigger" AFTER INSERT ON "public"."completed_authentications" FOR EACH STATEMENT EXECUTE FUNCTION "public"."trigger_cleanup_expired_completed_auths"();

ALTER TABLE ONLY "public"."passkey_challenges"
    ADD CONSTRAINT "passkey_challenges_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."role_permissions"
    ADD CONSTRAINT "role_permissions_new_permission_id_fkey" FOREIGN KEY ("permission_id") REFERENCES "public"."permissions"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."role_permissions"
    ADD CONSTRAINT "role_permissions_new_role_id_fkey" FOREIGN KEY ("role_id") REFERENCES "public"."roles"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."secret_metadata"
    ADD CONSTRAINT "secret_metadata_secret_id_fkey" FOREIGN KEY ("secret_id") REFERENCES "public"."secrets"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."secret_tags"
    ADD CONSTRAINT "secret_tags_secret_id_fkey" FOREIGN KEY ("secret_id") REFERENCES "public"."secrets"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."secrets"
    ADD CONSTRAINT "secrets_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."user_passkeys"
    ADD CONSTRAINT "user_passkeys_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."user_roles"
    ADD CONSTRAINT "user_roles_new_assigned_by_fkey" FOREIGN KEY ("assigned_by") REFERENCES "auth"."users"("id") ON DELETE SET NULL;

ALTER TABLE ONLY "public"."user_roles"
    ADD CONSTRAINT "user_roles_new_role_id_fkey" FOREIGN KEY ("role_id") REFERENCES "public"."roles"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."user_roles"
    ADD CONSTRAINT "user_roles_new_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;

ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;
-- ----------------------------------------------------------------------------
-- Section 9: Row Level Security (RLS) Policies
-- ----------------------------------------------------------------------------

CREATE POLICY "Admins can assign roles" ON "public"."user_roles" FOR INSERT WITH CHECK ("public"."is_user_admin"("auth"."uid"()));

CREATE POLICY "Admins can create permissions" ON "public"."permissions" FOR INSERT WITH CHECK ("public"."is_user_admin"("auth"."uid"()));

CREATE POLICY "Admins can create roles" ON "public"."roles" FOR INSERT WITH CHECK ("public"."is_user_admin"("auth"."uid"()));

CREATE POLICY "Admins can delete non-system permissions" ON "public"."permissions" FOR DELETE USING (((NOT "is_system") AND "public"."is_user_admin"("auth"."uid"())));

CREATE POLICY "Admins can delete non-system roles" ON "public"."roles" FOR DELETE USING (((NOT "is_system") AND "public"."is_user_admin"("auth"."uid"())));

CREATE POLICY "Admins can manage role permissions" ON "public"."role_permissions" USING ("public"."is_user_admin"("auth"."uid"()));

CREATE POLICY "Admins can read all users" ON "public"."users" FOR SELECT USING (COALESCE((("auth"."jwt"() -> 'is_admin'::"text"))::boolean, false));

COMMENT ON POLICY "Admins can read all users" ON "public"."users" IS 'Allows users with is_admin=true in JWT to view all users. Uses JWT claims to avoid infinite recursion.';

CREATE POLICY "Admins can remove roles" ON "public"."user_roles" FOR DELETE USING (("public"."is_user_admin"("auth"."uid"()) AND (NOT (("user_id" = "auth"."uid"()) AND ("role_id" IN ( SELECT "roles"."id"
   FROM "public"."roles"
  WHERE ("roles"."name" = 'admin'::"text")))))));

CREATE POLICY "Admins can update non-system permissions" ON "public"."permissions" FOR UPDATE USING (((NOT "is_system") AND "public"."is_user_admin"("auth"."uid"())));

CREATE POLICY "Admins can update non-system roles" ON "public"."roles" FOR UPDATE USING (((NOT "is_system") AND "public"."is_user_admin"("auth"."uid"())));

CREATE POLICY "Admins can view all roles" ON "public"."user_roles" FOR SELECT USING ("public"."is_user_admin"("auth"."uid"()));

CREATE POLICY "Allow auth admin to read roles" ON "public"."roles" FOR SELECT TO "supabase_auth_admin" USING (true);

CREATE POLICY "Allow auth admin to read user roles" ON "public"."user_roles" FOR SELECT TO "supabase_auth_admin" USING (true);

CREATE POLICY "Allow session-based access for mobile flows" ON "public"."passkey_challenges" FOR SELECT USING ((("session_id" IS NOT NULL) AND (("auth"."uid"() = "user_id") OR ("user_id" IS NULL))));

CREATE POLICY "Anyone can view permissions" ON "public"."permissions" FOR SELECT USING (true);

CREATE POLICY "Anyone can view role permissions" ON "public"."role_permissions" FOR SELECT USING (true);

CREATE POLICY "Anyone can view roles" ON "public"."roles" FOR SELECT USING (true);

CREATE POLICY "Service role can access all challenges" ON "public"."passkey_challenges" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Service role can access all passkeys" ON "public"."user_passkeys" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Service role can manage completed authentications" ON "public"."completed_authentications" TO "service_role" USING (true) WITH CHECK (true);

CREATE POLICY "Service role full access to permissions" ON "public"."permissions" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Service role full access to role_permissions" ON "public"."role_permissions" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Service role full access to roles" ON "public"."roles" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Service role full access to user_roles" ON "public"."user_roles" USING ((("auth"."jwt"() ->> 'role'::"text") = 'service_role'::"text"));

CREATE POLICY "Users can create own secret metadata" ON "public"."secret_metadata" FOR INSERT WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_metadata"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can create own secret tags" ON "public"."secret_tags" FOR INSERT WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_tags"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can create own secrets" ON "public"."secrets" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can delete own secret metadata" ON "public"."secret_metadata" FOR DELETE USING ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_metadata"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can delete own secret tags" ON "public"."secret_tags" FOR DELETE USING ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_tags"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can delete own secrets" ON "public"."secrets" FOR DELETE USING (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can delete their own passkeys" ON "public"."user_passkeys" FOR DELETE USING (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can insert their own challenges" ON "public"."passkey_challenges" FOR INSERT WITH CHECK ((("auth"."uid"() = "user_id") OR ("user_id" IS NULL)));

CREATE POLICY "Users can insert their own passkeys" ON "public"."user_passkeys" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can read own data" ON "public"."users" FOR SELECT USING (("auth"."uid"() = "id"));

CREATE POLICY "Users can update own data" ON "public"."users" FOR UPDATE USING (("auth"."uid"() = "id"));

CREATE POLICY "Users can update own secret metadata" ON "public"."secret_metadata" FOR UPDATE USING ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_metadata"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can update own secrets" ON "public"."secrets" FOR UPDATE USING (("auth"."uid"() = "user_id")) WITH CHECK (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can update their own passkeys" ON "public"."user_passkeys" FOR UPDATE USING (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can view own secret metadata" ON "public"."secret_metadata" FOR SELECT USING ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_metadata"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can view own secret tags" ON "public"."secret_tags" FOR SELECT USING ((EXISTS ( SELECT 1
   FROM "public"."secrets"
  WHERE (("secrets"."id" = "secret_tags"."secret_id") AND ("secrets"."user_id" = "auth"."uid"())))));

CREATE POLICY "Users can view own secrets" ON "public"."secrets" FOR SELECT USING (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can view their own challenges" ON "public"."passkey_challenges" FOR SELECT USING ((("auth"."uid"() = "user_id") OR ("user_id" IS NULL)));

CREATE POLICY "Users can view their own passkeys" ON "public"."user_passkeys" FOR SELECT USING (("auth"."uid"() = "user_id"));

CREATE POLICY "Users can view their own roles" ON "public"."user_roles" FOR SELECT USING (("auth"."uid"() = "user_id"));

ALTER TABLE "public"."completed_authentications" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."passkey_challenges" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."permissions" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."role_permissions" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."roles" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."secret_metadata" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."secret_tags" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."secrets" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."user_passkeys" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."user_roles" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;
ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";

-- ----------------------------------------------------------------------------
-- Section 10: Grants and Privileges
-- ----------------------------------------------------------------------------

-- ============================================================
-- 10.1: Schema Usage Grants
-- ============================================================

GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";
GRANT USAGE ON SCHEMA "public" TO "supabase_auth_admin";
GRANT ALL ON FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."assign_permission_to_role"("role_name" "text", "permission_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."assign_role_to_user"("target_user_id" "uuid", "target_role" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."authorize"("requested_permission" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."authorize"("requested_permission" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."authorize"("requested_permission" "text") TO "service_role";
GRANT ALL ON FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_user_has_role"("target_user_id" "uuid", "role_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."clean_expired_passkey_challenges"() TO "anon";
GRANT ALL ON FUNCTION "public"."clean_expired_passkey_challenges"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."clean_expired_passkey_challenges"() TO "service_role";

GRANT ALL ON FUNCTION "public"."cleanup_expired_completed_authentications"() TO "anon";
GRANT ALL ON FUNCTION "public"."cleanup_expired_completed_authentications"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."cleanup_expired_completed_authentications"() TO "service_role";

GRANT ALL ON FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_mobile_registration_session"("p_user_email" "text", "p_challenge" "text", "p_session_id" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_new_permission"("permission_name" "text", "description" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."create_new_role"("role_name" "text", "description" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."create_new_role"("role_name" "text", "description" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_new_role"("role_name" "text", "description" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."create_secret"("p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."create_secret"("p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_secret"("p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "service_role";

REVOKE ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") TO "service_role";
GRANT ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") TO "supabase_auth_admin";

GRANT ALL ON FUNCTION "public"."decrypt_text"("ciphertext" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."decrypt_text"("ciphertext" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."decrypt_text"("ciphertext" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."delete_permission"("permission_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_permission"("permission_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_permission"("permission_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."delete_role"("role_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_role"("role_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_role"("role_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."delete_secret"("p_secret_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_secret"("p_secret_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_secret"("p_secret_id" "uuid") TO "service_role";

GRANT ALL ON FUNCTION "public"."delete_user"("target_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."delete_user"("target_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."delete_user"("target_user_id" "uuid") TO "service_role";

GRANT ALL ON FUNCTION "public"."encrypt_text"("plaintext" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."encrypt_text"("plaintext" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."encrypt_text"("plaintext" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."find_user_by_email"("p_email" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."find_user_by_email"("p_email" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."find_user_by_email"("p_email" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_all_permissions"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_all_permissions"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_all_permissions"() TO "service_role";

GRANT ALL ON FUNCTION "public"."get_all_roles"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_all_roles"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_all_roles"() TO "service_role";

GRANT ALL ON FUNCTION "public"."get_encryption_key"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_encryption_key"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_encryption_key"() TO "service_role";

GRANT ALL ON FUNCTION "public"."get_role_permissions"("role_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_role_permissions"("role_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_role_permissions"("role_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_role_permissions_with_names"("role_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_session_status"("p_session_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_session_status"("p_session_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_session_status"("p_session_id" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_user_roles"("check_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_roles"("check_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_roles"("check_user_id" "uuid") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") TO "service_role";
GRANT ALL ON FUNCTION "public"."get_user_roles_for_hook"("check_user_id" "uuid") TO "supabase_auth_admin";

GRANT ALL ON FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_roles_with_names"("target_user_id" "uuid") TO "service_role";

GRANT ALL ON FUNCTION "public"."get_user_secrets"("p_limit" integer, "p_offset" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_secrets"("p_limit" integer, "p_offset" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_secrets"("p_limit" integer, "p_offset" integer) TO "service_role";

GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";

GRANT ALL ON FUNCTION "public"."handle_user_email_update"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_user_email_update"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_user_email_update"() TO "service_role";

GRANT ALL ON FUNCTION "public"."is_user_admin"("check_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."is_user_admin"("check_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."is_user_admin"("check_user_id" "uuid") TO "service_role";

GRANT ALL ON FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."remove_permission_from_role"("role_name" "text", "permission_name" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."remove_role_from_user"("target_user_id" "uuid", "target_role" "text") TO "service_role";

GRANT ALL ON FUNCTION "public"."search_user_secrets"("p_query" "text", "p_limit" integer, "p_offset" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."search_user_secrets"("p_query" "text", "p_limit" integer, "p_offset" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."search_user_secrets"("p_query" "text", "p_limit" integer, "p_offset" integer) TO "service_role";

GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_challenges"() TO "anon";
GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_challenges"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_challenges"() TO "service_role";

GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_completed_auths"() TO "anon";
GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_completed_auths"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trigger_cleanup_expired_completed_auths"() TO "service_role";

GRANT ALL ON FUNCTION "public"."update_secret"("p_secret_id" "uuid", "p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."update_secret"("p_secret_id" "uuid", "p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_secret"("p_secret_id" "uuid", "p_website" "text", "p_username" "text", "p_password" "text", "p_notes" "text", "p_expiration_date" timestamp with time zone, "p_tags" "text"[], "p_twofa_enabled" boolean, "p_twofa_type" "text", "p_recovery_codes" "text"[]) TO "service_role";

GRANT ALL ON FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."user_has_role"("check_user_id" "uuid", "check_role" "text") TO "service_role";

GRANT ALL ON TABLE "public"."user_passkeys" TO "anon";
GRANT ALL ON TABLE "public"."user_passkeys" TO "authenticated";
GRANT ALL ON TABLE "public"."user_passkeys" TO "service_role";

GRANT ALL ON TABLE "public"."active_user_passkeys" TO "anon";
GRANT ALL ON TABLE "public"."active_user_passkeys" TO "authenticated";
GRANT ALL ON TABLE "public"."active_user_passkeys" TO "service_role";

GRANT ALL ON TABLE "public"."completed_authentications" TO "anon";
GRANT ALL ON TABLE "public"."completed_authentications" TO "authenticated";
GRANT ALL ON TABLE "public"."completed_authentications" TO "service_role";

GRANT ALL ON TABLE "public"."passkey_challenges" TO "anon";
GRANT ALL ON TABLE "public"."passkey_challenges" TO "authenticated";
GRANT ALL ON TABLE "public"."passkey_challenges" TO "service_role";

GRANT ALL ON TABLE "public"."permissions" TO "anon";
GRANT ALL ON TABLE "public"."permissions" TO "authenticated";
GRANT ALL ON TABLE "public"."permissions" TO "service_role";

GRANT ALL ON TABLE "public"."role_permissions" TO "anon";
GRANT ALL ON TABLE "public"."role_permissions" TO "authenticated";
GRANT ALL ON TABLE "public"."role_permissions" TO "service_role";

GRANT ALL ON TABLE "public"."roles" TO "anon";
GRANT ALL ON TABLE "public"."roles" TO "authenticated";
GRANT ALL ON TABLE "public"."roles" TO "service_role";
GRANT ALL ON TABLE "public"."roles" TO "supabase_auth_admin";

GRANT ALL ON TABLE "public"."secret_metadata" TO "anon";
GRANT ALL ON TABLE "public"."secret_metadata" TO "authenticated";
GRANT ALL ON TABLE "public"."secret_metadata" TO "service_role";

GRANT ALL ON TABLE "public"."secret_tags" TO "anon";
GRANT ALL ON TABLE "public"."secret_tags" TO "authenticated";
GRANT ALL ON TABLE "public"."secret_tags" TO "service_role";

GRANT ALL ON TABLE "public"."secrets" TO "anon";
GRANT ALL ON TABLE "public"."secrets" TO "authenticated";
GRANT ALL ON TABLE "public"."secrets" TO "service_role";

GRANT ALL ON TABLE "public"."user_roles" TO "anon";
GRANT ALL ON TABLE "public"."user_roles" TO "authenticated";
GRANT ALL ON TABLE "public"."user_roles" TO "service_role";
GRANT ALL ON TABLE "public"."user_roles" TO "supabase_auth_admin";

GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";
-- ============================================================
-- 10.2: Default Privileges
-- ============================================================
-- These grants apply to all current and future objects
-- Individual GRANT statements are redundant and have been removed

ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";

-- ============================================================
-- 10.2: Default Privileges
-- ============================================================
-- These grants apply to all current and future objects
-- Individual GRANT statements are redundant and have been removed

ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";

-- ============================================================
-- 10.2: Default Privileges
-- ============================================================
-- These grants apply to all current and future objects
-- Individual GRANT statements are redundant and have been removed

ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";

-- ============================================================
-- 10.2: Default Privileges
-- ============================================================
-- These grants apply to all current and future objects
-- Individual GRANT statements are redundant and have been removed

ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";

-- ----------------------------------------------------------------------------
-- Section 11: Triggers
-- ----------------------------------------------------------------------------

RESET ALL;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION handle_new_user();

CREATE TRIGGER on_auth_user_email_updated AFTER UPDATE OF email ON auth.users FOR EACH ROW WHEN (((old.email)::text IS DISTINCT FROM (new.email)::text)) EXECUTE FUNCTION handle_user_email_update();

-- ----------------------------------------------------------------------------
-- Section 12: Vault Secrets Setup
-- ----------------------------------------------------------------------------
-- ⚠️  CRITICAL SECURITY SETUP REQUIRED ⚠️
--
-- After running this migration, you MUST create the master encryption key:
--
--   1. Generate a strong 256-bit key:
--      openssl rand -base64 32
--
--   2. Store it in Vault (replace YOUR-GENERATED-KEY with the actual key):
--      SELECT vault.create_secret(
--          'YOUR-GENERATED-KEY',
--          'master_encryption_key',
--          'Master key for encrypting user secrets in secrets table'
--      );
--
--   3. IMPORTANT: Store the key in a secure password manager
--      DO NOT commit the actual key to git
--
--   4. Verify the key is stored:
--      SELECT name, description, created_at
--      FROM vault.decrypted_secrets
--      WHERE name = 'master_encryption_key';
--
-- Without this setup, the encrypt_text() and decrypt_text() functions will fail.
-- See supabase/VAULT_SETUP.md for detailed instructions.
-- ----------------------------------------------------------------------------

-- ============================================================================
-- End of Migration
-- ============================================================================
