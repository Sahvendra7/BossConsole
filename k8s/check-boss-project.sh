#!/bin/bash

# BOSS Project Status Check Script
# This script checks the current state of BOSS resources in the boss-455616 project

set -e

PROJECT_ID="boss-455616"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[CHECK]${NC} $1"
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

print_status "Checking BOSS project: $PROJECT_ID"
echo

# Set project context
print_status "Setting project context..."
gcloud config set project $PROJECT_ID || {
    print_error "Failed to set project. Please run: gcloud auth login"
    exit 1
}

# Check project access
print_status "Verifying project access..."
gcloud projects describe $PROJECT_ID >/dev/null 2>&1 && {
    print_success "Project access confirmed"
} || {
    print_error "Cannot access project $PROJECT_ID"
    exit 1
}

# Check enabled APIs
print_status "Checking required APIs..."
REQUIRED_APIS=(
    "container.googleapis.com"
    "compute.googleapis.com"
    "logging.googleapis.com"
    "monitoring.googleapis.com"
)

for api in "${REQUIRED_APIS[@]}"; do
    if gcloud services list --enabled --filter="name:$api" --format="value(name)" | grep -q "$api"; then
        print_success "API enabled: $api"
    else
        print_warning "API not enabled: $api"
        echo "  Enable with: gcloud services enable $api"
    fi
done

# Check existing GKE clusters
print_status "Checking existing GKE clusters..."
CLUSTERS=$(gcloud container clusters list --format="value(name,location)" 2>/dev/null || echo "")
if [ -n "$CLUSTERS" ]; then
    print_success "Found existing clusters:"
    echo "$CLUSTERS" | while read -r name location; do
        echo "  - $name (in $location)"
        # Get cluster details
        gcloud container clusters describe "$name" --location="$location" --format="value(status,currentNodeCount)" 2>/dev/null | {
            read -r status nodecount
            echo "    Status: $status, Nodes: $nodecount"
        }
    done
else
    print_warning "No GKE clusters found"
fi

# Check Container Registry images
print_status "Checking Container Registry images..."
IMAGES=$(gcloud container images list --repository="gcr.io/$PROJECT_ID" --format="value(name)" 2>/dev/null || echo "")
if [ -n "$IMAGES" ]; then
    print_success "Found container images:"
    echo "$IMAGES" | while read -r image; do
        echo "  - $image"
        # Check for BOSS images
        if echo "$image" | grep -q "boss"; then
            print_success "Found BOSS image: $image"
            gcloud container images list-tags "$image" --limit=3 --format="table(tags,timestamp)" 2>/dev/null || true
        fi
    done
else
    print_warning "No container images found in gcr.io/$PROJECT_ID"
fi

# Check Kubernetes resources (if cluster exists)
if gcloud container clusters list --format="value(name)" | head -1 >/dev/null 2>&1; then
    CLUSTER_NAME=$(gcloud container clusters list --format="value(name)" | head -1)
    CLUSTER_ZONE=$(gcloud container clusters list --format="value(location)" | head -1)
    
    print_status "Checking Kubernetes resources in cluster: $CLUSTER_NAME"
    
    # Get cluster credentials
    gcloud container clusters get-credentials "$CLUSTER_NAME" --location="$CLUSTER_ZONE" >/dev/null 2>&1 || {
        print_warning "Could not get cluster credentials"
    }
    
    # Check for BOSS resources
    if kubectl get pods -l app=boss >/dev/null 2>&1; then
        print_success "Found BOSS pods:"
        kubectl get pods -l app=boss -o wide
        echo
        print_status "BOSS services:"
        kubectl get services -l app=boss
        echo
        print_status "BOSS ingress:"
        kubectl get ingress -l app=boss
    else
        print_warning "No BOSS resources found in Kubernetes"
    fi
fi

# Summary and recommendations
echo
print_status "=== SUMMARY ==="
echo

if [ -z "$CLUSTERS" ]; then
    print_warning "No GKE cluster found. Next steps:"
    echo "1. Create cluster: cd k8s/cluster && ./setup-gke.sh --project-id $PROJECT_ID"
    echo "2. Deploy BOSS: cd k8s && ./deploy.sh --project-id $PROJECT_ID --build"
else
    print_success "GKE cluster exists. You can:"
    echo "1. Deploy BOSS: cd k8s && ./deploy.sh --project-id $PROJECT_ID --build"
    echo "2. Update existing deployment: kubectl apply -f k8s/deployments/"
fi

echo
print_status "Useful commands for $PROJECT_ID:"
echo "  gcloud config set project $PROJECT_ID"
echo "  gcloud container clusters list"
echo "  gcloud container images list --repository=gcr.io/$PROJECT_ID"
echo "  kubectl get all -l app=boss"