#!/bin/bash

# Run BOSS with production Supabase configuration
# This script sets environment variables to point to production Supabase instance

echo "☁️  Running BOSS with PRODUCTION Supabase configuration"
echo ""
echo "Configuration:"
echo "  SUPABASE_URL: https://api.risaboss.com"
echo "  SUPABASE_FUNCTION_URL: https://api.risaboss.com/functions/v1"
echo ""

# Production Supabase configuration (from local.properties)
export SUPABASE_URL=https://api.risaboss.com
export SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBjbndxYW1xZG5zYWRyYW51Zmp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkxMDUwMzMsImV4cCI6MjA3NDY4MTAzM30.WZ6jSKuqM2EMyZLgoGJnI8Bn_Sdwk6plW0PkVNLIYVY
export SUPABASE_FUNCTION_URL=https://api.risaboss.com/functions/v1

# Run the application
./gradlew run
