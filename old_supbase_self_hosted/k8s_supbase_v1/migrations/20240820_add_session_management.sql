-- Add session management columns for mobile cross-device WebAuthn flows
-- Migration: Add session support to passkey_challenges table

-- Add new columns for session management
ALTER TABLE passkey_challenges 
ADD COLUMN session_id TEXT,
ADD COLUMN status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'in_progress', 'completed', 'failed', 'expired')),
ADD COLUMN user_email TEXT;

-- Create indexes for new columns
CREATE INDEX idx_passkey_challenges_session_id ON passkey_challenges(session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_passkey_challenges_status ON passkey_challenges(status);
CREATE INDEX idx_passkey_challenges_user_email ON passkey_challenges(user_email) WHERE user_email IS NOT NULL;

-- Update the cleanup function to also clean up failed/expired sessions
CREATE OR REPLACE FUNCTION clean_expired_passkey_challenges()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Delete expired challenges (by timestamp)
  DELETE FROM passkey_challenges 
  WHERE expires_at < NOW();
  
  -- Delete old failed/expired sessions (older than 1 hour)
  DELETE FROM passkey_challenges 
  WHERE status IN ('failed', 'expired') 
  AND created_at < NOW() - INTERVAL '1 hour';
  
  -- Mark very old in_progress sessions as expired (older than 15 minutes)
  UPDATE passkey_challenges 
  SET status = 'expired' 
  WHERE status = 'in_progress' 
  AND created_at < NOW() - INTERVAL '15 minutes';
END;
$$;

-- Add comments for new columns
COMMENT ON COLUMN passkey_challenges.session_id IS 'Unique session ID for tracking cross-device WebAuthn flows';
COMMENT ON COLUMN passkey_challenges.status IS 'Session status: pending, in_progress, completed, failed, expired';
COMMENT ON COLUMN passkey_challenges.user_email IS 'User email for cross-device flows (lookup purposes)';

-- Update RLS policies to handle email-based lookups for mobile flows
CREATE POLICY "Allow session-based access for mobile flows" ON passkey_challenges
  FOR SELECT USING (session_id IS NOT NULL AND (auth.uid() = user_id OR user_id IS NULL));

-- Grant necessary permissions (already covered by existing service_role grants)
-- No additional grants needed

-- Create a utility function to create mobile registration sessions
CREATE OR REPLACE FUNCTION create_mobile_registration_session(
  p_user_email TEXT,
  p_challenge TEXT,
  p_session_id TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  challenge_id UUID;
  user_uuid UUID;
BEGIN
  -- Look up user by email
  SELECT id INTO user_uuid 
  FROM auth.users 
  WHERE email = p_user_email 
  AND email_confirmed_at IS NOT NULL;
  
  IF user_uuid IS NULL THEN
    RAISE EXCEPTION 'User not found or email not confirmed: %', p_user_email;
  END IF;
  
  -- Insert challenge with session info
  INSERT INTO passkey_challenges (
    user_id,
    challenge,
    type,
    expires_at,
    session_id,
    status,
    user_email
  ) VALUES (
    user_uuid,
    p_challenge,
    'registration',
    NOW() + INTERVAL '5 minutes',
    p_session_id,
    'pending',
    p_user_email
  ) RETURNING id INTO challenge_id;
  
  RETURN challenge_id;
END;
$$;

-- Grant execution permission on the utility function
GRANT EXECUTE ON FUNCTION create_mobile_registration_session TO service_role;

-- Add some helpful utility functions for session management
CREATE OR REPLACE FUNCTION get_session_status(p_session_id TEXT)
RETURNS TABLE(
  session_id TEXT,
  status TEXT,
  user_email TEXT,
  created_at TIMESTAMP WITH TIME ZONE,
  expires_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    pc.session_id,
    pc.status,
    pc.user_email,
    pc.created_at,
    pc.expires_at
  FROM passkey_challenges pc
  WHERE pc.session_id = p_session_id
  AND pc.type = 'registration'
  ORDER BY pc.created_at DESC
  LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION get_session_status TO service_role;

-- Update comments
COMMENT ON FUNCTION create_mobile_registration_session IS 'Helper function to create mobile WebAuthn registration sessions with email lookup';
COMMENT ON FUNCTION get_session_status IS 'Helper function to check the status of a mobile registration session';