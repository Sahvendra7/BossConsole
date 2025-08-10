#!/bin/bash

# BOSS GKE Cluster Setup Script
# This script sets up a GKE cluster for BOSS application

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
PROJECT_ID=""
REGION="us-central1"
ZONE="us-central1-a"
CLUSTER_NAME="boss-cluster"
NODE_COUNT=3

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
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

# Function to show usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

OPTIONS:
    -p, --project-id PROJECT_ID    GCP Project ID (required)
    -r, --region REGION           GCP Region (default: us-central1)
    -z, --zone ZONE               GCP Zone (default: us-central1-a)
    -n, --cluster-name NAME       Cluster name (default: boss-cluster)
    -c, --node-count COUNT        Initial node count (default: 3)
    -h, --help                    Show this help message

Examples:
    $0 --project-id my-project
    $0 -p my-project -r us-west1 -z us-west1-a -n boss-prod-cluster

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--project-id)
            PROJECT_ID="$2"
            shift 2
            ;;
        -r|--region)
            REGION="$2"
            shift 2
            ;;
        -z|--zone)
            ZONE="$2"
            shift 2
            ;;
        -n|--cluster-name)
            CLUSTER_NAME="$2"
            shift 2
            ;;
        -c|--node-count)
            NODE_COUNT="$2"
            shift 2
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

print_status "Starting BOSS GKE cluster setup..."
echo "Configuration:"
echo "  Project ID: $PROJECT_ID"
echo "  Region: $REGION"
echo "  Zone: $ZONE"
echo "  Cluster Name: $CLUSTER_NAME"
echo "  Node Count: $NODE_COUNT"
echo

# Check prerequisites
print_status "Checking prerequisites..."

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    print_error "gcloud CLI is not installed. Please install it first."
    echo "Visit: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    print_warning "kubectl is not installed. Installing..."
    gcloud components install kubectl
fi

# Check if terraform is installed (optional)
if ! command -v terraform &> /dev/null; then
    print_warning "Terraform is not installed. You can still use gcloud commands."
fi

print_success "Prerequisites check completed"

# Authenticate and set project
print_status "Setting up GCP authentication..."
gcloud config set project $PROJECT_ID

# Enable required APIs
print_status "Enabling required GCP APIs..."
gcloud services enable container.googleapis.com
gcloud services enable compute.googleapis.com
gcloud services enable logging.googleapis.com
gcloud services enable monitoring.googleapis.com

print_success "APIs enabled successfully"

# Option 1: Use Terraform (if available)
if command -v terraform &> /dev/null; then
    print_status "Terraform detected. Choose deployment method:"
    echo "1. Use Terraform (recommended)"
    echo "2. Use gcloud commands"
    read -p "Enter choice (1 or 2): " choice
    
    if [ "$choice" = "1" ]; then
        print_status "Using Terraform for deployment..."
        
        # Create terraform.tfvars
        cat > terraform.tfvars << EOF
project_id   = "$PROJECT_ID"
region       = "$REGION"
zone         = "$ZONE"
cluster_name = "$CLUSTER_NAME"
node_count   = $NODE_COUNT
EOF
        
        # Initialize and apply Terraform
        terraform init
        terraform plan
        
        read -p "Do you want to proceed with Terraform apply? (y/N): " confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            terraform apply -auto-approve
            print_success "Cluster created successfully with Terraform"
        else
            print_warning "Deployment cancelled"
            exit 0
        fi
    fi
else
    choice="2"
fi

# Option 2: Use gcloud commands
if [ "$choice" = "2" ]; then
    print_status "Using gcloud commands for deployment..."
    
    # Create the cluster
    print_status "Creating GKE cluster: $CLUSTER_NAME"
    gcloud container clusters create $CLUSTER_NAME \
        --zone=$ZONE \
        --machine-type=e2-standard-4 \
        --num-nodes=$NODE_COUNT \
        --disk-size=100GB \
        --disk-type=pd-standard \
        --image-type=COS_CONTAINERD \
        --enable-network-policy \
        --enable-ip-alias \
        --enable-autoscaling \
        --min-nodes=1 \
        --max-nodes=10 \
        --enable-autorepair \
        --enable-autoupgrade \
        --maintenance-window-start="2024-01-01T02:00:00Z" \
        --maintenance-window-end="2024-01-01T06:00:00Z" \
        --maintenance-window-recurrence="FREQ=WEEKLY;BYDAY=SA" \
        --workload-pool=$PROJECT_ID.svc.id.goog \
        --enable-shielded-nodes \
        --logging=SYSTEM,WORKLOAD \
        --monitoring=SYSTEM
    
    print_success "Cluster created successfully with gcloud"
fi

# Configure kubectl
print_status "Configuring kubectl..."
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID

# Verify cluster
print_status "Verifying cluster setup..."
kubectl cluster-info
kubectl get nodes

print_success "GKE cluster setup completed successfully!"
echo
print_status "Next steps:"
echo "1. Deploy BOSS application: kubectl apply -f ../deployments/"
echo "2. Set up ingress: kubectl apply -f ../ingress/"
echo "3. Configure monitoring and logging"
echo "4. Set up CI/CD pipeline"
echo
print_status "Useful commands:"
echo "  View cluster: gcloud container clusters describe $CLUSTER_NAME --zone $ZONE"
echo "  Delete cluster: gcloud container clusters delete $CLUSTER_NAME --zone $ZONE"
echo "  Get credentials: gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE"