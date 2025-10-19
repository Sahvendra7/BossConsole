-- Add function to delete users (admin only)
-- This function allows admins to delete user accounts and all associated data

CREATE OR REPLACE FUNCTION public.delete_user(
    target_user_id UUID
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if caller is admin
    IF NOT public.is_user_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Only admins can delete users';
    END IF;

    -- Prevent deleting yourself
    IF target_user_id = auth.uid() THEN
        RAISE EXCEPTION 'Cannot delete your own account';
    END IF;

    -- Prevent deleting other admins (safety measure)
    IF public.is_user_admin(target_user_id) THEN
        RAISE EXCEPTION 'Cannot delete admin users. Remove admin role first.';
    END IF;

    -- Delete user's role assignments
    DELETE FROM public.user_roles WHERE user_id = target_user_id;

    -- Delete user record
    DELETE FROM public.users WHERE id = target_user_id;

    -- Note: Supabase Auth user will need to be deleted separately via Supabase Auth API
    -- This only deletes the public.users record and related data

    RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

COMMENT ON FUNCTION public.delete_user(UUID) IS
    'Delete a user and their associated data (admin only). Cannot delete self or other admins.';
