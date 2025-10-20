-- ============================================================================
-- Diagnose Auth Hook Issue
-- ============================================================================
--
-- Purpose: Debug why auth hook is returning only ["user"] instead of ["user", "admin"]
--
-- Created: 2025-01-19
-- ============================================================================

DO $$
DECLARE
    v_user_id UUID := '9e6af4d5-81ec-44fc-aba7-308223396fb1';
    v_roles_array text[];
    v_role_name text;
    v_count INT;
BEGIN
    RAISE NOTICE '🔍 Diagnosing auth hook for user: %', v_user_id;
    RAISE NOTICE '';

    -- Check 1: Query user_roles table directly
    RAISE NOTICE '1️⃣ Direct query of user_roles table:';
    FOR v_role_name IN
        SELECT r.name
        FROM public.user_roles ur
        JOIN public.roles r ON r.id = ur.role_id
        WHERE ur.user_id = v_user_id
        ORDER BY ur.assigned_at
    LOOP
        RAISE NOTICE '   Role: %', v_role_name;
    END LOOP;

    -- Check 2: Count total roles
    SELECT COUNT(*) INTO v_count
    FROM public.user_roles
    WHERE user_id = v_user_id;
    RAISE NOTICE '   Total roles: %', v_count;
    RAISE NOTICE '';

    -- Check 3: Call get_user_roles_for_hook function
    RAISE NOTICE '2️⃣ Calling get_user_roles_for_hook():';
    SELECT public.get_user_roles_for_hook(v_user_id) INTO v_roles_array;
    RAISE NOTICE '   Result: %', v_roles_array;
    RAISE NOTICE '';

    -- Check 4: Test the full auth hook
    RAISE NOTICE '3️⃣ Testing custom_access_token_hook():';
    DECLARE
        v_event jsonb;
        v_result jsonb;
    BEGIN
        v_event := jsonb_build_object(
            'user_id', v_user_id::text,
            'claims', '{}'::jsonb
        );

        v_result := public.custom_access_token_hook(v_event);

        RAISE NOTICE '   user_role: %', v_result->'claims'->>'user_role';
        RAISE NOTICE '   user_roles: %', v_result->'claims'->'user_roles';
        RAISE NOTICE '   is_admin: %', v_result->'claims'->>'is_admin';
    END;
    RAISE NOTICE '';

    -- Check 5: Verify function definition
    RAISE NOTICE '4️⃣ Checking function source code:';
    DECLARE
        v_source text;
    BEGIN
        SELECT pg_get_functiondef(oid) INTO v_source
        FROM pg_proc
        WHERE proname = 'get_user_roles_for_hook';

        IF v_source LIKE '%user_roles_new%' THEN
            RAISE WARNING '⚠️  Function still references user_roles_new!';
        ELSIF v_source LIKE '%user_roles%' THEN
            RAISE NOTICE '   ✅ Function correctly references user_roles';
        ELSE
            RAISE WARNING '⚠️  Cannot determine table reference';
        END IF;
    END;

END $$;
