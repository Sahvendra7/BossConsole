#!/bin/bash

# BOSS Edge Functions - Scalable Deployment Script
# This script automatically discovers and deploys all Edge Functions

set -e

echo "🚀 Deploying All BOSS Edge Functions..."

# Check if we're in the right directory
if [ ! -d "functions" ]; then
    echo "❌ Functions directory not found. Please run this script from the supabase/ directory."
    exit 1
fi

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl not found. Please install kubectl to deploy to Kubernetes."
    exit 1
fi

# Discover all function files and directories
echo "📂 Discovering Edge Functions..."
FUNCTIONS_FOUND=""
FUNCTION_COUNT=0

# Find single-file functions (*.ts)
for func_file in functions/*.ts; do
    if [ -f "$func_file" ]; then
        func_name=$(basename "$func_file" .ts)
        echo "  📄 Found single-file function: $func_name"
        FUNCTIONS_FOUND="$FUNCTIONS_FOUND $func_name"
        FUNCTION_COUNT=$((FUNCTION_COUNT + 1))
    fi
done

# Find directory-based functions (*/index.ts)
for func_dir in functions/*/; do
    if [ -d "$func_dir" ]; then
        func_name=$(basename "$func_dir")
        if [ -f "$func_dir/index.ts" ]; then
            echo "  📁 Found directory function: $func_name"
            FUNCTIONS_FOUND="$FUNCTIONS_FOUND $func_name"
            FUNCTION_COUNT=$((FUNCTION_COUNT + 1))
        else
            echo "  ⚠️  Skipping $func_name directory (no index.ts found)"
        fi
    fi
done

if [ $FUNCTION_COUNT -eq 0 ]; then
    echo "❌ No valid Edge Functions found."
    echo "    Create either:"
    echo "    - Single file: functions/my-function.ts"
    echo "    - Directory: functions/my-function/index.ts"
    exit 1
fi

echo ""
echo "📦 Preparing to deploy $FUNCTION_COUNT function(s): $FUNCTIONS_FOUND"
echo ""

# Build ConfigMap command with all functions
CONFIG_MAP_CMD="kubectl create configmap supabase-functions-config --dry-run=client -o yaml"

for func_name in $FUNCTIONS_FOUND; do
    # Check if this is a single-file function
    if [ -f "functions/${func_name}.ts" ]; then
        echo "📄 Packaging single-file function: ${func_name}.ts"
        CONFIG_MAP_CMD="$CONFIG_MAP_CMD --from-file=${func_name}.ts=functions/${func_name}.ts"
    # Check if this is a directory-based function  
    elif [ -f "functions/${func_name}/index.ts" ]; then
        echo "📁 Packaging directory function: ${func_name}/index.ts"
        # Include main function file
        CONFIG_MAP_CMD="$CONFIG_MAP_CMD --from-file=${func_name}.ts=functions/${func_name}/index.ts"
        
        # Include all other TypeScript files in the function directory
        if [ -d "functions/${func_name}" ]; then
            for ts_file in functions/${func_name}/*.ts; do
                if [ -f "$ts_file" ] && [ "$(basename "$ts_file")" != "index.ts" ]; then
                    filename=$(basename "$ts_file")
                    echo "  📄 Including module: ${filename}"
                    CONFIG_MAP_CMD="$CONFIG_MAP_CMD --from-file=${filename}=${ts_file}"
                fi
            done
        fi
    else
        echo "❌ Error: Could not find function file for ${func_name}"
        echo "    Expected either functions/${func_name}.ts or functions/${func_name}/index.ts"
        exit 1
    fi
done

# Deploy the ConfigMap
echo "📦 Creating ConfigMap with all functions..."
eval "$CONFIG_MAP_CMD | kubectl apply -f -"

echo "✅ ConfigMap updated successfully"

# Restart the deployment
echo "🔄 Restarting deployment..."
kubectl rollout restart deployment/supabase-functions

echo "⏳ Waiting for deployment to be ready..."
kubectl rollout status deployment/supabase-functions --timeout=120s

echo ""
echo "🎉 All BOSS Edge Functions deployed successfully!"
echo ""
echo "📋 Available functions:"
for func_name in $FUNCTIONS_FOUND; do
    echo "   - POST /functions/v1/$func_name"
done
echo ""

# Special note for passkey-functions
if echo "$FUNCTIONS_FOUND" | grep -q "passkey-functions"; then
    echo "🔐 Passkey Functions endpoints:"
    echo "   - POST /functions/v1/passkey-functions?op=reg-challenge"
    echo "   - POST /functions/v1/passkey-functions?op=reg-complete"
    echo "   - POST /functions/v1/passkey-functions?op=auth-challenge"
    echo "   - POST /functions/v1/passkey-functions?op=auth-complete"
    echo "   - POST /functions/v1/passkey-functions?op=list"
    echo "   - POST /functions/v1/passkey-functions?op=delete"
    echo ""
fi

echo "🔧 To add new functions:"
echo "   Option 1 (Simple): Create functions/my-function.ts"
echo "   Option 2 (Modular): Create functions/my-function/index.ts"
echo "   Then run: ./deploy-all-functions.sh"
echo ""
echo "📚 For updates, redeploy using this script after making changes"