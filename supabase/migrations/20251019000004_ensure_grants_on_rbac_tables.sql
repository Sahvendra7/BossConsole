-- Ensure authenticated users have proper GRANT permissions on RBAC tables
-- This fixes "permission denied for table user_roles" errors

-- Grant SELECT permission to authenticated role on user_roles
GRANT SELECT ON public.user_roles TO authenticated;

-- Grant SELECT permission to authenticated role on role_permissions
GRANT SELECT ON public.role_permissions TO authenticated;

-- Grant SELECT permission to authenticated role on users (for admin panel)
GRANT SELECT ON public.users TO authenticated;

-- Ensure anon role also has read access (for unauthenticated queries if needed)
GRANT SELECT ON public.user_roles TO anon;
GRANT SELECT ON public.role_permissions TO anon;

-- Service role should have full access
GRANT ALL ON public.user_roles TO service_role;
GRANT ALL ON public.role_permissions TO service_role;
GRANT ALL ON public.users TO service_role;

-- Add comment
COMMENT ON TABLE public.user_roles IS
    'User role assignments. SELECT granted to authenticated for RLS-protected queries.';
