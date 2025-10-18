#!/bin/bash

# Run BOSS with local Supabase configuration
# This script sets environment variables to point to local Supabase instance

echo "🏠 Running BOSS with LOCAL Supabase configuration"
echo ""
echo "Configuration:"
echo "  SUPABASE_URL: http://localhost:54321"
echo "  SUPABASE_ANON_KEY: sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"
echo "  SUPABASE_FUNCTION_URL: http://localhost:54321/functions/v1"
echo ""

# Local Supabase configuration (using localhost for WebAuthn compatibility)
export SUPABASE_URL=http://localhost:54321
export SUPABASE_ANON_KEY=sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH
export SUPABASE_FUNCTION_URL=http://localhost:54321/functions/v1

# Run the application
./gradlew run
