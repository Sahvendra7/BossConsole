-- Prevent removal of the baseline 'user' role
-- The 'user' role is the default role assigned to all users and should not be removable

-- Update the remove_role_from_user function to prevent removing 'user' role
CREATE OR REPLACE FUNCTION public.remove_role_from_user(
    target_user_id UUID,
    target_role public.app_role
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if caller is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can remove roles';
    END IF;

    -- Prevent removing admin role from self
    IF target_user_id = auth.uid() AND target_role = 'admin' THEN
        RAISE EXCEPTION 'Cannot remove your own admin role';
    END IF;

    -- Prevent removing the baseline 'user' role from anyone
    IF target_role = 'user' THEN
        RAISE EXCEPTION 'Cannot remove the baseline user role. All users must have the user role.';
    END IF;

    -- Remove role
    DELETE FROM public.user_roles
    WHERE user_id = target_user_id AND role = target_role;

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.remove_role_from_user(UUID, public.app_role) IS
    'Remove a role from a user (admin only). Cannot remove own admin role or the baseline user role.';
