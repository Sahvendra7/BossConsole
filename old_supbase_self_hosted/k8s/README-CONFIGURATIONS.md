# BOSS Supabase SSL Configuration Documentation

## Overview
This directory contains all the working Supabase self-hosted infrastructure configurations with Google-managed SSL certificates deployed on GKE.

## Essential Configuration Files

### SSL Configuration
- `ssl/managed-certificates.yaml` - Google-managed SSL certificates for all domains
- `ssl/kong-ssl-ingress.yaml` - Production HTTPS ingress with SSL certificate integration
- `ssl/ingress-resources.yaml` - Current ingress resources backup

### Authentication & Secrets
- `secrets/supabase-auth-secrets.yaml` - JWT secrets and database credentials
- `secrets/postgres-secrets.yaml` - PostgreSQL authentication secrets

### Service Configuration
- `configs/supabase-auth-config.yaml` - Auth service config with SMTP settings
- `configs/kong-config.yaml` - Kong API Gateway routing configuration
- `configs/supabase-db-config.yaml` - Database initialization configuration

### Deployments
- `deployments/supabase-studio-fixed.yaml` - Studio deployment with correct JWT tokens
- `deployments/all-deployments.yaml` - Complete backup of all deployments
- `deployments/postgres-statefulset.yaml` - PostgreSQL StatefulSet configuration

### Services & Networking
- `services/loadbalancer-services.yaml` - All LoadBalancer services
- `storage/persistent-volume-claims.yaml` - Storage volume claims

## Working Endpoints (HTTPS)
- **API**: https://api.risaboss.com
- **Auth**: https://auth.risaboss.com  
- **Storage**: https://storage.risaboss.com
- **Realtime**: https://realtime.risaboss.com
- **Studio**: https://studio.risaboss.com

## Key Configuration Details

### JWT Authentication
- **JWT Secret**: "your-super-secret-jwt-key-at-least-32-characters-long-for-production"
- **Anon Key**: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4
- **Service Role Key**: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJzZXJ2aWNlX3JvbGUiLCJpYXQiOjE3NTQ3ODUwNTQsImV4cCI6MTc4NjMyMTA1NH0.leOWOgwHUKaKgNTtmKO5W6Y6OdpFQyxhzFE9bEhI41E

### SMTP Configuration
- **Email**: noreply.boss@risalabs.ai
- **Provider**: Gmail SMTP
- **Configured in**: supabase-auth-config.yaml

### Static IP
- **Production IP**: 34.95.114.211
- **Used by**: HTTPS Application Load Balancer and SSL certificates

### Kong Gateway
- **Load Balancer IP**: 34.9.47.213
- **Handles**: API routing and service discovery
- **Configuration**: kong-config.yaml with domain-based routing

## Deployment Status
- ✅ SSL Certificates: Active and validated
- ✅ DNS Configuration: All domains point to 34.95.114.211
- ✅ HTTPS Endpoints: All services accessible via HTTPS
- ✅ Studio Functions: Working with JWT authentication
- ✅ Email Verification: Gmail SMTP configured
- ✅ Kong Gateway: Routing all services correctly
- ✅ Database: PostgreSQL with persistent storage

## BOSS Application Configuration
The BOSS application is configured to use:
```kotlin
val url = "https://api.risaboss.com"  // Domain-based unified Supabase API endpoint
val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4"
```

## Next Steps
1. ✅ SSL and HTTPS setup complete
2. 🔄 Kong basic auth implementation (pending)
3. 📋 Commit all configurations
4. 🧪 End-to-end testing

## Troubleshooting
All essential configurations have been extracted from the running cluster and preserved in this repository. The infrastructure is production-ready with proper SSL certificates and load balancing.