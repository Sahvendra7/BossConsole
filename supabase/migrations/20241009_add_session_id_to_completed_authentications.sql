-- Add session_id column to completed_authentications table
-- This allows desktop client to poll using session_id instead of challenge

ALTER TABLE completed_authentications
ADD COLUMN IF NOT EXISTS session_id TEXT;

-- Add index for fast session_id lookups
CREATE INDEX IF NOT EXISTS idx_completed_authentications_session_id
ON completed_authentications(session_id);

-- Add comment
COMMENT ON COLUMN completed_authentications.session_id IS 'Session ID for polling authentication status';
