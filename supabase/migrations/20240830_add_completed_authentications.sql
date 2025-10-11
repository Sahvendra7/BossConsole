-- Migration: Add completed_authentications table for cross-device authentication tracking
-- This table stores completed authentications temporarily for polling

CREATE TABLE IF NOT EXISTS completed_authentications (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  challenge TEXT NOT NULL UNIQUE,
  user_id UUID NOT NULL,
  email TEXT,
  session_token TEXT,
  access_token TEXT,
  refresh_token TEXT,
  expires_at BIGINT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  expires_at_timestamp TIMESTAMP WITH TIME ZONE DEFAULT (NOW() + INTERVAL '5 minutes')
);

-- Index for fast challenge lookups
CREATE INDEX IF NOT EXISTS idx_completed_authentications_challenge 
ON completed_authentications(challenge);

-- Automatically clean up expired records
CREATE INDEX IF NOT EXISTS idx_completed_authentications_expires 
ON completed_authentications(expires_at_timestamp);

-- Set up RLS (Row Level Security)
ALTER TABLE completed_authentications ENABLE ROW LEVEL SECURITY;

-- Allow service role to read/write all records
CREATE POLICY "Service role can manage completed authentications"
ON completed_authentications
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- Clean up function for expired records
CREATE OR REPLACE FUNCTION cleanup_expired_completed_authentications() 
RETURNS void AS $$
BEGIN
  DELETE FROM completed_authentications 
  WHERE expires_at_timestamp < NOW();
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE completed_authentications IS 'Temporary storage for completed cross-device authentications';
COMMENT ON COLUMN completed_authentications.challenge IS 'WebAuthn challenge that was completed';
COMMENT ON COLUMN completed_authentications.user_id IS 'User who completed the authentication';
COMMENT ON COLUMN completed_authentications.expires_at_timestamp IS 'When this record should be cleaned up';