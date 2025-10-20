-- ============================================================================
-- Enable Custom Access Token Hook
-- ============================================================================
--
-- Purpose: Ensure the custom access token hook is properly configured
--
-- Note: This migration ensures the hook configuration exists.
--       You may also need to enable it via Supabase Dashboard:
--       Authentication → Hooks → Custom Access Token
--
-- Created: 2025-01-19
-- ============================================================================

-- Verify the functions exist
DO $$
DECLARE
    v_hook_exists BOOLEAN;
    v_helper_exists BOOLEAN;
BEGIN
    -- Check if custom_access_token_hook exists
    SELECT EXISTS (
        SELECT 1 FROM pg_proc
        WHERE proname = 'custom_access_token_hook'
        AND pronamespace = 'public'::regnamespace
    ) INTO v_hook_exists;

    -- Check if get_user_roles_for_hook exists
    SELECT EXISTS (
        SELECT 1 FROM pg_proc
        WHERE proname = 'get_user_roles_for_hook'
        AND pronamespace = 'public'::regnamespace
    ) INTO v_helper_exists;

    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '🔍 AUTH HOOK STATUS CHECK';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';

    IF v_hook_exists THEN
        RAISE NOTICE '✅ custom_access_token_hook() exists';
    ELSE
        RAISE WARNING '❌ custom_access_token_hook() NOT FOUND';
    END IF;

    IF v_helper_exists THEN
        RAISE NOTICE '✅ get_user_roles_for_hook() exists';
    ELSE
        RAISE WARNING '❌ get_user_roles_for_hook() NOT FOUND';
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE '📋 Next Steps:';
    RAISE NOTICE '   1. Go to Supabase Dashboard';
    RAISE NOTICE '   2. Navigate to: Authentication → Hooks';
    RAISE NOTICE '   3. Find: Custom Access Token';
    RAISE NOTICE '   4. Set Hook: public.custom_access_token_hook';
    RAISE NOTICE '   5. Click: Save';
    RAISE NOTICE '';
    RAISE NOTICE '⚠️  The hook MUST be enabled in the Dashboard';
    RAISE NOTICE '⚠️  SQL migrations cannot enable it automatically';
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
END $$;
