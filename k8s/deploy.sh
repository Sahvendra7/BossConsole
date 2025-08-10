#!/bin/bash

# BOSS GKE Deployment Script
# This script deploys BOSS application to Google Kubernetes Engine (GKE)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[DEPLOY]${NC} $1"
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
PROJECT_ID=""
CLUSTER_NAME="boss-cluster"
ZONE="us-central1-a"
NAMESPACE="default"
IMAGE_TAG="latest"
BUILD_IMAGE=false
DEPLOY_ALL=true

# Function to show usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy BOSS application to GKE cluster

OPTIONS:
    -p, --project-id PROJECT_ID    GCP Project ID (required)
    -c, --cluster-name NAME        GKE cluster name (default: boss-cluster)
    -z, --zone ZONE               GKE zone (default: us-central1-a)
    -n, --namespace NAMESPACE     Kubernetes namespace (default: default)
    -t, --tag TAG                 Docker image tag (default: latest)
    -b, --build                   Build and push Docker image
    --storage-only                Deploy only storage resources
    --app-only                    Deploy only application resources
    --ingress-only                Deploy only ingress resources
    -h, --help                    Show this help message

Examples:
    $0 --project-id my-project --build
    $0 -p my-project -c boss-prod -n production --app-only

EOF
}

# Parse command line arguments
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
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -b|--build)
            BUILD_IMAGE=true
            shift
            ;;
        --storage-only)
            DEPLOY_ALL=false
            DEPLOY_STORAGE=true
            shift
            ;;
        --app-only)
            DEPLOY_ALL=false
            DEPLOY_APP=true
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

# Validate required parameters
if [ -z "$PROJECT_ID" ]; then
    print_error "Project ID is required. Use -p or --project-id"
    usage
    exit 1
fi

print_status "Starting BOSS deployment to GKE..."
echo "Configuration:"
echo "  Project ID: $PROJECT_ID"
echo "  Cluster: $CLUSTER_NAME"
echo "  Zone: $ZONE"
echo "  Namespace: $NAMESPACE"
echo "  Image Tag: $IMAGE_TAG"
echo "  Build Image: $BUILD_IMAGE"
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

# Build and push Docker image if requested
if [ "$BUILD_IMAGE" = true ]; then
    print_status "Building and pushing Docker image..."
    
    # Configure Docker for GCR
    gcloud auth configure-docker
    
    # Build the application first
    print_status "Building BOSS application..."
    cd ../
    ./gradlew composeApp:packageUberJarForCurrentOS
    
    # Build Docker image
    print_status "Building Docker image..."
    docker build -t gcr.io/$PROJECT_ID/boss:$IMAGE_TAG .
    
    # Push to Google Container Registry
    print_status "Pushing image to GCR..."
    docker push gcr.io/$PROJECT_ID/boss:$IMAGE_TAG
    
    cd k8s/
    print_success "Image built and pushed successfully"
fi

# Update deployment files with project ID and image tag
print_status "Updating deployment files..."
find . -name "*.yaml" -exec sed -i.bak "s/PROJECT_ID/$PROJECT_ID/g" {} \;
find . -name "*.yaml" -exec sed -i.bak "s/:latest/:$IMAGE_TAG/g" {} \;

# Deploy storage resources
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_STORAGE" = true ]; then
    print_status "Deploying storage resources..."
    kubectl apply -f storage/boss-storage.yaml
    
    # Wait for PVCs to be bound
    print_status "Waiting for persistent volumes to be bound..."
    kubectl wait --for=condition=Bound pvc/boss-data-pvc --timeout=300s || print_warning "PVC binding timeout"
fi

# Deploy configuration and secrets
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_APP" = true ]; then
    print_status "Deploying configuration..."
    kubectl apply -f deployments/boss-configmap.yaml
    kubectl apply -f deployments/supabase-config.yaml
fi

# Deploy application
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_APP" = true ]; then
    print_status "Deploying BOSS application..."
    kubectl apply -f deployments/boss-deployment.yaml
    
    # Wait for deployment to be ready
    print_status "Waiting for deployment to be ready..."
    kubectl rollout status deployment/boss-app --timeout=600s
fi

# Deploy services
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_APP" = true ]; then
    print_status "Deploying services..."
    kubectl apply -f services/boss-service.yaml
fi

# Deploy ingress
if [ "$DEPLOY_ALL" = true ] || [ "$DEPLOY_INGRESS" = true ]; then
    print_status "Deploying ingress..."
    kubectl apply -f ingress/boss-ingress.yaml
fi

# Clean up backup files
find . -name "*.yaml.bak" -delete

# Show deployment status
print_status "Deployment completed! Checking status..."
echo

print_status "Pods:"
kubectl get pods -l app=boss

print_status "Services:"
kubectl get services -l app=boss

print_status "Ingress:"
kubectl get ingress -l app=boss

print_status "Storage:"
kubectl get pvc -l app=boss

# Get external IP (if using LoadBalancer)
EXTERNAL_IP=$(kubectl get service boss-loadbalancer -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

if [ -n "$EXTERNAL_IP" ]; then
    print_success "BOSS is accessible at: http://$EXTERNAL_IP"
else
    print_warning "External IP not yet assigned. Check ingress configuration."
fi

print_success "BOSS deployment completed successfully!"
echo
print_status "Useful commands:"
echo "  View logs: kubectl logs -l app=boss -f"
echo "  Scale up: kubectl scale deployment boss-app --replicas=5"
echo "  Update: kubectl set image deployment/boss-app boss-app=gcr.io/$PROJECT_ID/boss:new-tag"
echo "  Delete: kubectl delete -f k8s/"