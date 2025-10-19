-- Fix infinite recursion in admin users read policy
-- Use JWT claims instead of querying user_roles table to avoid circular dependency

-- Drop the problematic policy
DROP POLICY IF EXISTS "Admins can read all users" ON public.users;

-- Recreate policy using JWT claims (is_admin claim is already in the token)
-- This avoids querying user_roles table which has its own RLS policies
CREATE POLICY "Admins can read all users"
    ON public.users
    FOR SELECT
    USING (
        -- Check the is_admin claim from the JWT token
        -- The custom_access_token_hook already adds this claim
        COALESCE(
            (auth.jwt() -> 'is_admin')::boolean,
            false
        )
    );

-- Add comment
COMMENT ON POLICY "Admins can read all users" ON public.users IS
    'Allows users with is_admin=true in JWT to view all users. Uses JWT claims to avoid infinite recursion.';
