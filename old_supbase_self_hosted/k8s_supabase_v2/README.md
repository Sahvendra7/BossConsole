# Supabase v2 - Community Kubernetes Deployment

This directory contains the configuration and deployment files for the Supabase Community Kubernetes deployment on GKE.

## Cluster Information
- **Cluster Name**: supabase
- **Project**: boss-455616
- **Zone**: us-central1-a
- **Node Type**: e2-standard-4 (3 nodes)
- **Network**: Private nodes with Cloud NAT

## Directory Structure
```
supabase_v2/
├── README.md                  # This file
├── values.yaml               # Helm chart values
├── deploy.sh                 # Deployment script
├── apply-migrations.sh       # Database migration script
├── cluster-info.yaml         # Cluster configuration
├── charts/                   # Supabase Kubernetes Helm charts
│   └── charts/supabase/     # Main Helm chart
├── functions/                # Edge Functions
│   └── passkey-functions.ts # Passkey authentication functions
└── migrations/               # Database migrations
    ├── 20240811_create_passkey_tables.sql
    ├── 20240820_add_session_management.sql
    └── 20240830_add_completed_authentications.sql
```

## Quick Commands

### Connect to Cluster
```bash
gcloud container clusters get-credentials supabase \
  --zone us-central1-a \
  --project boss-455616
```

### Check Status
```bash
kubectl get pods -n supabase
kubectl get svc -n supabase
```

### Update Deployment
```bash
helm upgrade supabase ./charts/supabase \
  --namespace supabase \
  --values values.yaml
```

### Get Kong LoadBalancer IP
```bash
kubectl get svc supabase-supabase-kong -n supabase
```

## Services
- **Kong API Gateway**: Entry point for all services
- **PostgreSQL Database**: Main database
- **Auth (GoTrue)**: Authentication service
- **Storage**: File storage service
- **Realtime**: WebSocket connections
- **REST (PostgREST)**: REST API for database
- **Studio**: Admin dashboard
- **Functions**: Edge Functions runtime

## Next Steps
1. Fix image tags in values.yaml
2. Apply database migrations
3. Configure DNS for api.risaboss.com
4. Deploy Edge Functions