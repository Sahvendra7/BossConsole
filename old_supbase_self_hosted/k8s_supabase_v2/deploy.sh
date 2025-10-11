#!/bin/bash

# Supabase v2 - Community Kubernetes Deployment Script

set -e

echo "🚀 Deploying Supabase v2 on GKE..."

# Configuration
PROJECT="boss-455616"
CLUSTER="supabase"
ZONE="us-central1-a"
NAMESPACE="supabase"

# Step 1: Get cluster credentials
echo "📡 Connecting to GKE cluster..."
gcloud container clusters get-credentials $CLUSTER \
  --zone $ZONE \
  --project $PROJECT

# Step 2: Create namespace if not exists
echo "🏗️ Creating namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Step 3: Create ConfigMap for Edge Functions
if [ -f "functions/passkey-functions.ts" ]; then
    echo "📄 Creating Edge Functions ConfigMap..."
    kubectl create configmap supabase-functions-config \
      --from-file=passkey-functions.ts=functions/passkey-functions.ts \
      -n $NAMESPACE \
      --dry-run=client -o yaml | kubectl apply -f -
fi

# Step 4: Create required secrets
echo "🔐 Creating secrets..."
kubectl create secret generic supabase-dashboard \
  --from-literal=username=admin \
  --from-literal=password=admin123 \
  -n $NAMESPACE \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 5: Check if charts exist
if [ ! -d "charts/supabase" ]; then
    echo "❌ Helm charts not found! Cloning..."
    git clone https://github.com/supabase-community/supabase-kubernetes.git temp
    mv temp/charts .
    rm -rf temp
fi

# Step 6: Deploy or upgrade Supabase
echo "🎯 Installing/Upgrading Supabase..."
helm upgrade --install supabase \
  ./charts/supabase \
  --namespace $NAMESPACE \
  --values values.yaml \
  --timeout 10m \
  --wait

# Step 7: Check deployment status
echo "✅ Checking deployment status..."
kubectl get pods -n $NAMESPACE

echo "🌐 Getting service endpoints..."
kubectl get svc -n $NAMESPACE

# Get Kong LoadBalancer IP
KONG_IP=$(kubectl get svc supabase-supabase-kong -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
echo ""
echo "📋 Kong LoadBalancer IP: $KONG_IP"
echo ""
echo "Once the LoadBalancer IP is assigned, update your DNS:"
echo "  api.risaboss.com -> $KONG_IP"
echo ""
echo "Monitor pods: kubectl get pods -n $NAMESPACE -w"
echo "View logs: kubectl logs -n $NAMESPACE -l app.kubernetes.io/name=supabase"