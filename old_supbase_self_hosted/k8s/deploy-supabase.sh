#!/bin/bash

# Self-hosted Supabase on GKE Deployment Script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[SUPABASE]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Default values
PROJECT_ID="boss-455616"
CLUSTER_NAME="boss-cluster"
ZONE="us-central1-a"
NAMESPACE="default"
DOMAIN="yourdomain.com"

# Function to show usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy self-hosted Supabase to GKE cluster

OPTIONS:
    -p, --project-id PROJECT_ID    GCP Project ID (default: boss-455616)
    -c, --cluster-name NAME        GKE cluster name (default: boss-cluster)
    -z, --zone ZONE               GKE zone (default: us-central1-a)
    -n, --namespace NAMESPACE     Kubernetes namespace (default: default)
    -d, --domain DOMAIN           Your domain name (default: yourdomain.com)
    --postgres-only               Deploy only PostgreSQL
    --auth-only                   Deploy only Auth service
    --rest-only                   Deploy only REST API
    --realtime-only              Deploy only Realtime service
    --storage-only               Deploy only Storage service
    --ingress-only               Deploy only Ingress
    -h, --help                   Show this help message

Examples:
    $0 --domain example.com
    $0 --postgres-only
    $0 --domain example.com --namespace supabase

EOF
}

# Parse command line arguments
DEPLOY_ALL=true
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--project-id)
            PROJECT_ID="$2"
            shift 2
            ;;
        -c|--cluster-name)
            CLUSTER_NAME="$2"
            shift 2
            ;;
        -z|--zone)
            ZONE="$2"
            shift 2
            ;;
        -n|--namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -d|--domain)
            DOMAIN="$2"
            shift 2
            ;;
        --postgres-only)
            DEPLOY_ALL=false
            DEPLOY_POSTGRES=true
            shift
            ;;
        --auth-only)
            DEPLOY_ALL=false
            DEPLOY_AUTH=true
            shift
            ;;
        --rest-only)
            DEPLOY_ALL=false
            DEPLOY_REST=true
            shift
            ;;
        --realtime-only)
            DEPLOY_ALL=false
            DEPLOY_REALTIME=true
            shift
            ;;
        --storage-only)
            DEPLOY_ALL=false
            DEPLOY_STORAGE=true
            shift
            ;;
        --ingress-only)
            DEPLOY_ALL=false
            DEPLOY_INGRESS=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

print_status "Starting self-hosted Supabase deployment to GKE..."
echo "Configuration:"
echo "  Project ID: $PROJECT_ID"
echo "  Cluster: $CLUSTER_NAME"
echo "  Zone: $ZONE"
echo "  Namespace: $NAMESPACE"
echo "  Domain: $DOMAIN"
echo

# Check prerequisites
print_status "Checking prerequisites..."

if ! command -v kubectl &> /dev/null; then
    print_error "kubectl is not installed"
    exit 1
fi

if ! command -v gcloud &> /dev/null; then
    print_error "gcloud CLI is not installed"
    exit 1
fi

# Set up GCP authentication and context
print_status "Setting up GCP context..."
gcloud config set project $PROJECT_ID
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID

# Create namespace if it doesn't exist
if [ "$NAMESPACE" != "default" ]; then
    print_status "Creating namespace: $NAMESPACE"
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
    kubectl config set-context --current --namespace=$NAMESPACE
fi

# Update domain in ingress files
print_status "Updating domain configuration..."
if [ "$DOMAIN" != "yourdomain.com" ]; then
    sed -i.bak "s/yourdomain\.com/$DOMAIN/g" ingress/supabase-ingress.yaml
fi

# Generate secure passwords and JWT secret
print_status "Generating secure credentials..."
POSTGRES_PASSWORD=$(openssl rand -base64 32)
JWT_SECRET=$(openssl rand -base64 64)

print_warning "Generated credentials (SAVE THESE):"
echo "  PostgreSQL Password: $POSTGRES_PASSWORD"
echo "  JWT Secret: $JWT_SECRET"
echo

# Update secrets with generated passwords
sed -i.bak "s/your-postgres-password-change-me/$POSTGRES_PASSWORD/g" deployments/supabase-*.yaml
sed -i.bak "s/your-jwt-secret-at-least-32-characters-long/$JWT_SECRET/g" deployments/supabase-*.yaml ingress/supabase-ingress.yaml

# Deploy storage classes first
print_status "Ensuring storage classes exist..."
kubectl apply -f storage/boss-storage.yaml || print_warning "Storage classes may already exist"

# Deploy PostgreSQL first (other services depend on it)
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_POSTGRES" = true ]; then
    print_status "Deploying PostgreSQL database..."
    kubectl apply -f deployments/supabase-postgres.yaml
    
    print_status "Waiting for PostgreSQL to be ready..."
    kubectl wait --for=condition=Ready pod -l app=supabase,component=postgres --timeout=300s || print_warning "PostgreSQL timeout - check logs if deployment fails"
fi

# Deploy Auth service
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_AUTH" = true ]; then
    print_status "Deploying Supabase Auth service..."
    kubectl apply -f deployments/supabase-auth.yaml
    
    print_status "Waiting for Auth service to be ready..."
    kubectl wait --for=condition=Available deployment/supabase-auth --timeout=300s || print_warning "Auth service timeout"
fi

# Deploy REST API
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_REST" = true ]; then
    print_status "Deploying Supabase REST API..."
    kubectl apply -f deployments/supabase-rest.yaml
    
    print_status "Waiting for REST API to be ready..."
    kubectl wait --for=condition=Available deployment/supabase-rest --timeout=300s || print_warning "REST API timeout"
fi

# Deploy Realtime service
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_REALTIME" = true ]; then
    print_status "Deploying Supabase Realtime service..."
    kubectl apply -f deployments/supabase-realtime.yaml
    
    print_status "Waiting for Realtime service to be ready..."
    kubectl wait --for=condition=Available deployment/supabase-realtime --timeout=300s || print_warning "Realtime service timeout"
fi

# Deploy Storage service
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_STORAGE" = true ]; then
    print_status "Deploying Supabase Storage service..."
    kubectl apply -f deployments/supabase-storage.yaml
    
    print_status "Waiting for Storage service to be ready..."
    kubectl wait --for=condition=Available deployment/supabase-storage --timeout=300s || print_warning "Storage service timeout"
fi

# Deploy Ingress
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_INGRESS" = true ]; then
    print_status "Deploying Supabase Ingress..."
    kubectl apply -f ingress/supabase-ingress.yaml
fi

# Clean up backup files
find . -name "*.yaml.bak" -delete

# Show deployment status
print_status "Supabase deployment completed! Checking status..."
echo

print_status "Pods:"
kubectl get pods -l app=supabase

print_status "Services:"
kubectl get services -l app=supabase

print_status "Ingress:"
kubectl get ingress -l app=supabase

print_status "Storage:"
kubectl get pvc -l app=supabase

# Get external IP
EXTERNAL_IP=$(kubectl get ingress supabase-ingress -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

if [ -n "$EXTERNAL_IP" ]; then
    print_success "Supabase is accessible at: https://supabase.$DOMAIN"
    print_success "External IP: $EXTERNAL_IP"
else
    print_warning "External IP not yet assigned. It may take a few minutes."
fi

print_success "Self-hosted Supabase deployment completed!"
echo
print_status "API Endpoints:"
echo "  Auth:     https://supabase.$DOMAIN/auth/v1"
echo "  REST:     https://supabase.$DOMAIN/rest/v1"  
echo "  Realtime: wss://supabase.$DOMAIN/realtime/v1"
echo "  Storage:  https://supabase.$DOMAIN/storage/v1"
echo
print_status "Credentials:"
echo "  Database: postgres://supabase_admin:<password>@postgres:5432/postgres"
echo "  JWT Secret: $JWT_SECRET"
echo
print_status "Useful commands:"
echo "  View logs: kubectl logs -l app=supabase -f"
echo "  Scale service: kubectl scale deployment supabase-rest --replicas=5"
echo "  Connect to DB: kubectl exec -it deployment/postgres -- psql -U supabase_admin postgres"
echo "  Port forward: kubectl port-forward service/supabase-auth 9999:9999"