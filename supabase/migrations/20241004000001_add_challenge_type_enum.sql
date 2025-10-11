-- Create enum type for challenge types
-- This ensures type safety at the database level

DO $$ BEGIN
  CREATE TYPE challenge_type AS ENUM ('registration', 'authentication');
EXCEPTION
  WHEN duplicate_object THEN null;
END $$;

-- Drop the existing CHECK constraint before converting type
ALTER TABLE passkey_challenges
  DROP CONSTRAINT IF EXISTS passkey_challenges_type_check;

-- Alter passkey_challenges table to use the enum type
-- Convert existing TEXT column to enum type
ALTER TABLE passkey_challenges
  ALTER COLUMN type TYPE challenge_type
  USING type::TEXT::challenge_type;

COMMENT ON TYPE challenge_type IS 'WebAuthn challenge types: registration or authentication';
