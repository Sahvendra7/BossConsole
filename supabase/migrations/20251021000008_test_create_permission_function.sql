-- ============================================================================
-- TEST: Try to create secrets.delete and see what happens
-- ============================================================================

DO $$
DECLARE
    result JSONB;
BEGIN
    -- Temporarily set user context (simulate authenticated admin)
    -- This is a test, so we'll check the function logic directly

    RAISE NOTICE '=== TESTING create_new_permission ===';

    -- Check function exists
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'create_new_permission') THEN
        RAISE NOTICE '✅ Function create_new_permission exists';
    ELSE
        RAISE NOTICE '❌ Function create_new_permission does NOT exist';
    END IF;

    -- Show function definition
    RAISE NOTICE 'Function source (first 500 chars): %',
        SUBSTRING((SELECT prosrc FROM pg_proc WHERE proname = 'create_new_permission' LIMIT 1), 1, 500);

    -- Try direct insert to test table access
    BEGIN
        INSERT INTO public.permissions (name, description, is_system)
        VALUES ('test.permission.temp', 'Test permission', false);

        RAISE NOTICE '✅ Direct INSERT into permissions table works';

        -- Clean up test
        DELETE FROM public.permissions WHERE name = 'test.permission.temp';
        RAISE NOTICE '✅ Cleaned up test permission';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE '❌ Direct INSERT failed: %', SQLERRM;
    END;

END $$;
