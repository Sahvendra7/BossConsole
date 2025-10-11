#!/bin/bash

# Script to set SUPABASE_JWT_SECRET for Edge Functions
#
# Usage: ./set-jwt-secret.sh <your-jwt-secret>
#
# To get your JWT secret:
# 1. Go to https://supabase.com/dashboard/project/pcnwqamqdnsadranufjv/settings/api
# 2. Copy the JWT Secret from the "Config" section
# 3. Run: ./set-jwt-secret.sh "your-jwt-secret-here"

set -e

if [ -z "$1" ]; then
    echo "❌ Error: JWT secret not provided"
    echo ""
    echo "Usage: ./set-jwt-secret.sh <your-jwt-secret>"
    echo ""
    echo "To get your JWT secret:"
    echo "  1. Go to https://supabase.com/dashboard/project/pcnwqamqdnsadranufjv/settings/api"
    echo "  2. Copy the JWT Secret from the 'Config' section"
    echo "  3. Run this script with the secret as an argument"
    exit 1
fi

JWT_SECRET="$1"
PROJECT_REF="pcnwqamqdnsadranufjv"

echo "🔐 Setting SUPABASE_JWT_SECRET for project $PROJECT_REF..."
echo ""

# Set the secret using Supabase CLI
supabase secrets set SUPABASE_JWT_SECRET="$JWT_SECRET" --project-ref "$PROJECT_REF"

echo ""
echo "✅ JWT secret set successfully!"
echo ""
echo "📋 Current secrets:"
supabase secrets list --project-ref "$PROJECT_REF"
echo ""
echo "🎉 Edge Functions will now be able to generate JWT tokens for passkey authentication"
echo ""
echo "Next steps:"
echo "  1. Test passkey authentication from the desktop app"
echo "  2. Verify tokens are returned in the response"
echo "  3. Confirm session persists across app restarts"
