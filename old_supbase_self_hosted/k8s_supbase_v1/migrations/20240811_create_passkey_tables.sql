-- Create user_passkeys table for storing WebAuthn credentials
CREATE TABLE user_passkeys (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  credential_id TEXT UNIQUE NOT NULL,
  public_key TEXT NOT NULL,
  display_name TEXT NOT NULL,
  transports TEXT[] DEFAULT ARRAY['internal'],
  created_at BIGINT DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000,
  last_used_at BIGINT,
  active BOOLEAN DEFAULT true,
  attestation_object TEXT, -- Store for potential future verification
  created_by_ip INET DEFAULT inet_client_addr(),
  user_agent TEXT
);

-- Create passkey_challenges table for temporary challenge storage
CREATE TABLE passkey_challenges (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  challenge TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('registration', 'authentication')),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  created_by_ip INET DEFAULT inet_client_addr()
);

-- Create indexes for performance
CREATE INDEX idx_user_passkeys_user_id ON user_passkeys(user_id) WHERE active = true;
CREATE INDEX idx_user_passkeys_credential_id ON user_passkeys(credential_id) WHERE active = true;
CREATE INDEX idx_passkey_challenges_user_id ON passkey_challenges(user_id);
CREATE INDEX idx_passkey_challenges_challenge ON passkey_challenges(challenge);
CREATE INDEX idx_passkey_challenges_expires_at ON passkey_challenges(expires_at);

-- Create function to clean up expired challenges
CREATE OR REPLACE FUNCTION clean_expired_passkey_challenges()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  DELETE FROM passkey_challenges 
  WHERE expires_at < NOW();
END;
$$;

-- Create a scheduled job to clean up expired challenges (runs every 10 minutes)
-- Note: This uses pg_cron extension if available in your Supabase setup
-- If not available, you can handle cleanup in your application code
SELECT cron.schedule(
  'clean-passkey-challenges',
  '*/10 * * * *', -- Every 10 minutes
  'SELECT clean_expired_passkey_challenges();'
);

-- Row Level Security (RLS) policies
ALTER TABLE user_passkeys ENABLE ROW LEVEL SECURITY;
ALTER TABLE passkey_challenges ENABLE ROW LEVEL SECURITY;

-- Policy for user_passkeys: Users can only see their own passkeys
CREATE POLICY "Users can view their own passkeys" ON user_passkeys
  FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own passkeys" ON user_passkeys
  FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own passkeys" ON user_passkeys
  FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own passkeys" ON user_passkeys
  FOR DELETE USING (auth.uid() = user_id);

-- Policy for passkey_challenges: Users can only access their own challenges
CREATE POLICY "Users can view their own challenges" ON passkey_challenges
  FOR SELECT USING (auth.uid() = user_id OR user_id IS NULL);

CREATE POLICY "Users can insert their own challenges" ON passkey_challenges
  FOR INSERT WITH CHECK (auth.uid() = user_id OR user_id IS NULL);

-- Service role can access everything (for Edge Functions)
CREATE POLICY "Service role can access all passkeys" ON user_passkeys
  FOR ALL USING (auth.jwt() ->> 'role' = 'service_role');

CREATE POLICY "Service role can access all challenges" ON passkey_challenges
  FOR ALL USING (auth.jwt() ->> 'role' = 'service_role');

-- Grant necessary permissions
GRANT ALL ON user_passkeys TO service_role;
GRANT ALL ON passkey_challenges TO service_role;
GRANT USAGE ON SEQUENCE user_passkeys_id_seq TO service_role;
GRANT USAGE ON SEQUENCE passkey_challenges_id_seq TO service_role;

-- Create views for easier querying
CREATE VIEW active_user_passkeys AS
SELECT 
  id,
  user_id,
  credential_id,
  display_name,
  transports,
  created_at,
  last_used_at
FROM user_passkeys 
WHERE active = true;

-- Grant access to the view
GRANT SELECT ON active_user_passkeys TO authenticated, service_role;

-- Add comments for documentation
COMMENT ON TABLE user_passkeys IS 'WebAuthn/FIDO2 passkey credentials for users';
COMMENT ON TABLE passkey_challenges IS 'Temporary storage for WebAuthn challenges during registration/authentication';
COMMENT ON COLUMN user_passkeys.credential_id IS 'Base64url-encoded credential ID from WebAuthn';
COMMENT ON COLUMN user_passkeys.public_key IS 'Base64url-encoded public key for signature verification';
COMMENT ON COLUMN user_passkeys.transports IS 'Available transport methods (internal, usb, nfc, ble, hybrid)';
COMMENT ON COLUMN user_passkeys.attestation_object IS 'Base64url-encoded attestation object (optional)';
COMMENT ON COLUMN passkey_challenges.type IS 'Challenge type: registration or authentication';
COMMENT ON COLUMN passkey_challenges.expires_at IS 'Challenge expiration timestamp (typically 5 minutes)';

-- Insert some sample data for testing (remove in production)
-- This will be useful for development/testing
DO $$
BEGIN
  -- Only insert if we're not in production
  IF current_setting('app.environment', true) != 'production' THEN
    -- Sample passkey challenge (will be cleaned up automatically)
    INSERT INTO passkey_challenges (challenge, type, expires_at) 
    VALUES (
      'sample-challenge-for-testing-only',
      'registration',
      NOW() + INTERVAL '5 minutes'
    );
  END IF;
END
$$;