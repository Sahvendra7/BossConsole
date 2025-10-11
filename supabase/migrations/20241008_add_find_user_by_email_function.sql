-- Create a secure function to find user by email
-- This uses SECURITY DEFINER to access auth.users table
CREATE OR REPLACE FUNCTION find_user_by_email(p_email TEXT)
RETURNS TABLE (
  id UUID,
  email TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  RETURN QUERY
  SELECT au.id, au.email::TEXT
  FROM auth.users au
  WHERE au.email = p_email
  LIMIT 1;
END;
$$;

-- Grant execute permission to authenticated and anon roles
GRANT EXECUTE ON FUNCTION find_user_by_email(TEXT) TO authenticated, anon, service_role;
