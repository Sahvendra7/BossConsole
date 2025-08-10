# BOSS GKE Deployment Guide

This directory contains all the necessary configuration files and scripts to deploy BOSS (Business OS + Simulator) on Google Kubernetes Engine (GKE).

## 📁 Directory Structure

```
k8s/
├── cluster/                    # GKE cluster setup
│   ├── main.tf                # Terraform configuration
│   ├── gke-cluster.yaml       # YAML cluster config
│   ├── setup-gke.sh          # Cluster setup script
│   └── terraform.tfvars.example
├── deployments/               # Application deployments
│   ├── boss-deployment.yaml  # Main BOSS deployment
│   ├── boss-configmap.yaml   # Configuration and secrets
│   └── supabase-config.yaml  # Supabase integration
├── services/                  # Kubernetes services
│   └── boss-service.yaml     # Service definitions
├── ingress/                   # Ingress configuration
│   └── boss-ingress.yaml     # Load balancer and SSL
├── storage/                   # Persistent storage
│   └── boss-storage.yaml     # PVCs and storage classes
├── deploy.sh                 # Main deployment script
└── README.md                 # This file
```

## 🚀 Quick Start

### Prerequisites

1. **Google Cloud Platform Account**
   - GCP Project with billing enabled
   - Container Registry API enabled
   - Kubernetes Engine API enabled

2. **Local Tools**
   ```bash
   # Install Google Cloud SDK
   curl https://sdk.cloud.google.com | bash
   exec -l $SHELL
   
   # Install kubectl
   gcloud components install kubectl
   
   # Install Docker
   # Visit: https://docs.docker.com/get-docker/
   
   # Optional: Install Terraform
   # Visit: https://www.terraform.io/downloads.html
   ```

3. **Authentication**
   ```bash
   gcloud auth login
   gcloud config set project YOUR_PROJECT_ID
   ```

### Step 1: Create GKE Cluster

Choose one of the following methods:

#### Option A: Using the Setup Script (Recommended)
```bash
cd k8s/cluster
./setup-gke.sh --project-id YOUR_PROJECT_ID
```

#### Option B: Using Terraform
```bash
cd k8s/cluster
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values
terraform init
terraform plan
terraform apply
```

#### Option C: Using gcloud CLI
```bash
gcloud container clusters create boss-cluster \
    --zone=us-central1-a \
    --machine-type=e2-standard-4 \
    --num-nodes=3 \
    --enable-network-policy \
    --enable-ip-alias \
    --enable-autoscaling \
    --min-nodes=1 \
    --max-nodes=10
```

### Step 2: Deploy BOSS Application

```bash
cd k8s
./deploy.sh --project-id YOUR_PROJECT_ID --build
```

This will:
- Build the BOSS Docker image
- Push it to Google Container Registry
- Deploy all Kubernetes resources
- Set up ingress and load balancing

### Step 3: Access BOSS

Check the deployment status:
```bash
kubectl get pods -l app=boss
kubectl get services -l app=boss
kubectl get ingress -l app=boss
```

Get the external IP:
```bash
kubectl get service boss-loadbalancer
```

## 🔧 Configuration

### Environment Variables

BOSS can be configured using the following environment variables (set in `boss-configmap.yaml`):

| Variable | Description | Default |
|----------|-------------|---------|
| `SUPABASE_URL` | Supabase project URL | `https://tzetkxdzvxgmgqhrymuk.supabase.co` |
| `SUPABASE_ANON_KEY` | Supabase anonymous key | From secrets |
| `BOSS_DATA_DIR` | Data directory path | `/app/data` |
| `JAVA_OPTS` | JVM options | `-Xmx2g -Xms512m` |
| `BOSS_HEADLESS` | Run in headless mode | `false` |

### Supabase Integration

BOSS integrates with Supabase for authentication and data storage. Update the configuration in:

1. **ConfigMap** (`deployments/boss-configmap.yaml`):
   ```yaml
   data:
     supabase.url: "https://your-project.supabase.co"
   ```

2. **Secrets** (`deployments/boss-configmap.yaml`):
   ```yaml
   stringData:
     supabase.anon.key: "your-anon-key"
   ```

### SSL/TLS Configuration

The ingress configuration includes SSL/TLS termination:

1. **Google-managed certificates** (recommended):
   ```yaml
   annotations:
     ingress.gcp.kubernetes.io/managed-certificates: "boss-ssl-cert"
   ```

2. **Let's Encrypt with cert-manager**:
   ```yaml
   annotations:
     cert-manager.io/cluster-issuer: "letsencrypt-prod"
   ```

## 📊 Monitoring and Observability

### Health Checks

BOSS includes comprehensive health checks:
- **Liveness Probe**: Checks if the application is running
- **Readiness Probe**: Checks if the application is ready to serve traffic
- **Startup Probe**: Allows extra time for application startup

### Logging

View application logs:
```bash
# All BOSS pods
kubectl logs -l app=boss -f

# Specific pod
kubectl logs boss-app-<pod-id> -f

# Previous container instance
kubectl logs boss-app-<pod-id> -p
```

### Metrics

If Prometheus is installed, BOSS exposes metrics at `/metrics`:
```bash
kubectl port-forward service/boss-app-service 8080:80
curl http://localhost:8080/metrics
```

## 🔄 Scaling and Updates

### Horizontal Scaling

```bash
# Scale manually
kubectl scale deployment boss-app --replicas=5

# Auto-scaling is configured via HPA
kubectl get hpa boss-hpa
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/boss-app boss-app=gcr.io/PROJECT_ID/boss:v2.0.0

# Check rollout status
kubectl rollout status deployment/boss-app

# Rollback if needed
kubectl rollout undo deployment/boss-app
```

## 💾 Backup and Recovery

### Automated Backups

A CronJob is configured to backup BOSS data daily:
```bash
kubectl get cronjob boss-backup
kubectl logs -l job-name=boss-backup
```

### Manual Backup

```bash
# Create backup job
kubectl create job boss-manual-backup --from=cronjob/boss-backup

# Monitor backup
kubectl logs job/boss-manual-backup -f
```

### Restore from Backup

```bash
# List available backups
kubectl exec -it deployment/boss-app -- ls -la /backup

# Restore data (be careful!)
kubectl exec -it deployment/boss-app -- cp /backup/boss_backup_YYYYMMDD.tar.gz /tmp/
kubectl exec -it deployment/boss-app -- tar -xzf /tmp/boss_backup_YYYYMMDD.tar.gz -C /app/data/
```

## 🔒 Security

### Network Security

- **Network Policies**: Restrict pod-to-pod communication
- **Ingress Rules**: Control external access
- **TLS Termination**: Encrypt traffic in transit

### Pod Security

- **Security Context**: Run as non-root user
- **Resource Limits**: Prevent resource exhaustion
- **Read-only Root Filesystem**: Where possible

### Secrets Management

- **Kubernetes Secrets**: Store sensitive configuration
- **Google Secret Manager**: For enhanced security
- **Workload Identity**: Secure GCP service account access

## 🛠️ Troubleshooting

### Common Issues

1. **Pods not starting**:
   ```bash
   kubectl describe pod <pod-name>
   kubectl logs <pod-name>
   ```

2. **Image pull errors**:
   ```bash
   # Check image exists
   gcloud container images list --repository=gcr.io/PROJECT_ID
   
   # Verify authentication
   gcloud auth configure-docker
   ```

3. **Persistent volume issues**:
   ```bash
   kubectl get pv
   kubectl get pvc
   kubectl describe pvc boss-data-pvc
   ```

4. **Ingress not working**:
   ```bash
   kubectl describe ingress boss-ingress
   kubectl get events --field-selector involvedObject.kind=Ingress
   ```

### Debug Mode

Deploy BOSS with debug mode enabled:
```bash
kubectl set env deployment/boss-app JAVA_OPTS="-Xmx2g -Xms512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
kubectl port-forward deployment/boss-app 5005:5005
```

## 🧹 Cleanup

### Remove BOSS Application

```bash
# Remove application resources
kubectl delete -f deployments/
kubectl delete -f services/
kubectl delete -f ingress/

# Remove storage (careful - this deletes data!)
kubectl delete -f storage/
```

### Delete GKE Cluster

```bash
# Using gcloud
gcloud container clusters delete boss-cluster --zone us-central1-a

# Using Terraform
cd k8s/cluster
terraform destroy
```

## 📚 Additional Resources

- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [BOSS Application Repository](https://github.com/risa-labs-inc/BOSS-Kotlin)
- [Supabase Documentation](https://supabase.com/docs)

## 🆘 Support

For issues and questions:
- Create an issue in the [GitHub repository](https://github.com/risa-labs-inc/BOSS-Kotlin/issues)
- Contact support: [support@risalabs.ai](mailto:support@risalabs.ai)
- Enterprise support available for production deployments