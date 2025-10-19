-- Fix infinite recursion in user_roles RLS policies
-- Replace database queries with JWT claims to avoid circular dependencies

-- Drop problematic policies
DROP POLICY IF EXISTS "Admins can view all roles" ON public.user_roles;
DROP POLICY IF EXISTS "Admins can assign roles" ON public.user_roles;
DROP POLICY IF EXISTS "Admins can remove roles" ON public.user_roles;

-- Recreate policies using JWT claims instead of querying user_roles table

-- Only admins can view all roles (using JWT is_admin claim)
CREATE POLICY "Admins can view all roles"
    ON public.user_roles
    FOR SELECT
    USING (
        COALESCE(
            (auth.jwt() -> 'is_admin')::boolean,
            false
        )
    );

-- Only admins can insert roles (using JWT is_admin claim)
CREATE POLICY "Admins can assign roles"
    ON public.user_roles
    FOR INSERT
    WITH CHECK (
        COALESCE(
            (auth.jwt() -> 'is_admin')::boolean,
            false
        )
    );

-- Only admins can delete roles (except their own admin role)
CREATE POLICY "Admins can remove roles"
    ON public.user_roles
    FOR DELETE
    USING (
        COALESCE(
            (auth.jwt() -> 'is_admin')::boolean,
            false
        )
        -- Prevent removing own admin role
        AND NOT (user_id = auth.uid() AND role = 'admin')
    );

-- Add comments
COMMENT ON POLICY "Admins can view all roles" ON public.user_roles IS
    'Allows users with is_admin=true in JWT to view all user roles. Uses JWT claims to avoid infinite recursion.';

COMMENT ON POLICY "Admins can assign roles" ON public.user_roles IS
    'Allows users with is_admin=true in JWT to assign roles to users. Uses JWT claims to avoid infinite recursion.';

COMMENT ON POLICY "Admins can remove roles" ON public.user_roles IS
    'Allows users with is_admin=true in JWT to remove roles from users (except their own admin role). Uses JWT claims to avoid infinite recursion.';
