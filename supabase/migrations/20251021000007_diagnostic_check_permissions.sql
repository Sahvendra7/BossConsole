-- ============================================================================
-- DIAGNOSTIC: Check permissions table state
-- ============================================================================
-- This migration just checks the state and does nothing (safe to run)
-- ============================================================================

DO $$
DECLARE
    perm_count INTEGER;
    perm_record RECORD;
    secrets_delete_exists BOOLEAN;
BEGIN
    -- Count total permissions
    SELECT COUNT(*) INTO perm_count FROM public.permissions;
    RAISE NOTICE 'Total permissions in table: %', perm_count;

    -- Check if secrets.delete exists
    SELECT EXISTS(SELECT 1 FROM public.permissions WHERE name = 'secrets.delete') INTO secrets_delete_exists;
    RAISE NOTICE 'secrets.delete exists: %', secrets_delete_exists;

    -- List all permissions
    RAISE NOTICE '=== ALL PERMISSIONS ===';
    FOR perm_record IN
        SELECT name, is_system, created_at
        FROM public.permissions
        ORDER BY name
    LOOP
        RAISE NOTICE 'Permission: % (system: %, created: %)',
            perm_record.name,
            perm_record.is_system,
            perm_record.created_at;
    END LOOP;

    -- Check for secrets.* pattern
    RAISE NOTICE '=== SECRETS.* PERMISSIONS ===';
    FOR perm_record IN
        SELECT name, created_at
        FROM public.permissions
        WHERE name LIKE 'secrets.%'
        ORDER BY name
    LOOP
        RAISE NOTICE 'Found: %', perm_record.name;
    END LOOP;

END $$;

-- This migration makes no actual changes, safe to run repeatedly
