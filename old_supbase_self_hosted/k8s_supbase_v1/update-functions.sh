#!/bin/bash

# Quick update script for BOSS Edge Functions
# Use this for rapid development iterations

set -e

FUNCTION_NAME="$1"

if [ -z "$FUNCTION_NAME" ]; then
    echo "🔄 Quick update: All BOSS Edge Functions..."
    ./deploy-all-functions.sh
    exit 0
fi

echo "🔄 Quick update: $FUNCTION_NAME function..."

# Check if we're in the right directory
if [ ! -d "functions" ]; then
    echo "❌ Functions directory not found. Please run this script from the supabase/ directory."
    exit 1
fi

# Check if the specific function exists
if [ ! -f "functions/$FUNCTION_NAME/index.ts" ]; then
    echo "❌ Function $FUNCTION_NAME not found or missing index.ts"
    echo "Available functions:"
    for func_dir in functions/*/; do
        if [ -d "$func_dir" ] && [ -f "$func_dir/index.ts" ]; then
            echo "  - $(basename "$func_dir")"
        fi
    done
    exit 1
fi

echo "📦 Updating ConfigMap with $FUNCTION_NAME..."

# Get all existing functions and update the specific one
CONFIG_MAP_CMD="kubectl create configmap supabase-functions-config --dry-run=client -o yaml"

for func_dir in functions/*/; do
    if [ -d "$func_dir" ] && [ -f "$func_dir/index.ts" ]; then
        func_name=$(basename "$func_dir")
        CONFIG_MAP_CMD="$CONFIG_MAP_CMD --from-file=${func_name}.ts=functions/${func_name}/index.ts"
    fi
done

eval "$CONFIG_MAP_CMD | kubectl apply -f -"

echo "🔄 Restarting deployment..."
kubectl rollout restart deployment/supabase-functions

echo "✅ Update complete! Check status with:"
echo "   kubectl rollout status deployment/supabase-functions"
echo "   kubectl logs -l app=supabase,component=functions --tail=20"
echo ""
echo "Usage:"
echo "   ./update-functions.sh [function-name]  # Update specific function"
echo "   ./update-functions.sh                 # Update all functions"