-- ============================================================================
-- FORCE FIX: Recreate get_all_roles and get_all_permissions
-- ============================================================================
--
-- Purpose: Force recreate functions with table-based schema
-- This migration has a newer timestamp to ensure it runs after 20251019
--
-- Created: 2025-01-19
-- ============================================================================

-- Recreate get_all_roles with correct table-based schema
CREATE OR REPLACE FUNCTION public.get_all_roles()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_roles JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY name
    ) INTO v_roles
    FROM public.roles;
    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_roles, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

-- Recreate get_all_permissions with correct table-based schema
CREATE OR REPLACE FUNCTION public.get_all_permissions()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_permissions JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;
    IF NOT public.is_user_admin(v_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Permission denied: Admin role required');
    END IF;
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', id,
            'name', name,
            'description', description,
            'is_system', is_system,
            'created_at', created_at,
            'updated_at', updated_at
        )
        ORDER BY name
    ) INTO v_permissions
    FROM public.permissions;
    RETURN jsonb_build_object('success', true, 'data', COALESCE(v_permissions, '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

DO $$
BEGIN
    RAISE NOTICE '✅ Functions forcibly recreated with table-based schema';
END $$;
