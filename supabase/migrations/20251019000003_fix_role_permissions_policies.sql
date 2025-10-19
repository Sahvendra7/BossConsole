-- Fix infinite recursion in role_permissions RLS policies
-- Replace database queries with JWT claims to avoid circular dependencies

-- Drop problematic policy
DROP POLICY IF EXISTS "Admins can manage permissions" ON public.role_permissions;

-- Recreate policy using JWT claims instead of querying user_roles table

-- Only admins can modify permissions (using JWT is_admin claim)
CREATE POLICY "Admins can manage permissions"
    ON public.role_permissions
    FOR ALL
    USING (
        COALESCE(
            (auth.jwt() -> 'is_admin')::boolean,
            false
        )
    );

-- Add comment
COMMENT ON POLICY "Admins can manage permissions" ON public.role_permissions IS
    'Allows users with is_admin=true in JWT to manage role permissions. Uses JWT claims to avoid infinite recursion.';
