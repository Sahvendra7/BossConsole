# BOSS Desktop App Configuration for Self-hosted Supabase

This guide explains how to configure your BOSS desktop application to use the self-hosted Supabase instance running on GKE instead of the hosted Supabase service.

## 🔄 Configuration Changes Required

### 1. Update SupabaseConfig.kt

Replace the hosted Supabase configuration in `composeApp/src/commonMain/kotlin/ai/rever/boss/services/supabase/SupabaseConfig.kt`:

```kotlin
object SupabaseConfig {
    private var _url: String = ""
    private var _anonKey: String = ""
    
    val url: String get() = _url
    val anonKey: String get() = _anonKey
    
    fun initialize(url: String, anonKey: String) {
        _url = url
        _anonKey = anonKey
    }
    
    fun initializeFromEnvironment() {
        // Use self-hosted Supabase on GKE
        val url = "https://supabase.yourdomain.com"  // Replace with your domain
        val anonKey = "your-generated-anon-key"      // From deployment script output
        
        initialize(url, anonKey)
    }
    
    // Service endpoints for self-hosted Supabase
    object Endpoints {
        val auth: String get() = "${url}/auth/v1"
        val rest: String get() = "${url}/rest/v1"
        val realtime: String get() = "wss://${url.replace("https://", "")}/realtime/v1"
        val storage: String get() = "${url}/storage/v1"
    }
}
```

### 2. Update Environment Variables

Create or update your environment configuration:

**Option A: Environment Variables**
```bash
export SUPABASE_URL="https://supabase.yourdomain.com"
export SUPABASE_ANON_KEY="your-generated-anon-key"
export SUPABASE_SERVICE_ROLE_KEY="your-service-role-key"  # If needed
```

**Option B: Configuration File**
Create `~/.boss/supabase_config.json`:
```json
{
  "url": "https://supabase.yourdomain.com",
  "anonKey": "your-generated-anon-key",
  "serviceRoleKey": "your-service-role-key"
}
```

### 3. Update Deep Link Configuration

If using deep links for email verification, update the redirect URLs:

**In AuthService.kt**, update the deep link handling:
```kotlin
// Update email verification redirect URL
private val redirectUrl = "boss://auth/verify" // Keep the same
// The Supabase auth service will handle the redirect to your self-hosted instance
```

**In your GKE deployment**, ensure the Auth service is configured with the correct site URL:
```yaml
# In supabase-auth.yaml ConfigMap
GOTRUE_SITE_URL: "boss://auth/verify"  # For desktop app deep links
GOTRUE_URI_ALLOW_LIST: "boss://auth/verify,https://yourdomain.com"
```

## 🚀 Deployment Steps

### 1. Check GKE Cluster Status

```bash
gcloud container clusters list --project=boss-455616
```

If the cluster shows "RUNNING", proceed. If still "PROVISIONING", wait for it to complete.

### 2. Deploy Self-hosted Supabase

```bash
cd k8s

# Deploy with your domain
./deploy-supabase.sh --domain yourdomain.com

# Or deploy individual components
./deploy-supabase.sh --postgres-only
./deploy-supabase.sh --auth-only  
./deploy-supabase.sh --rest-only
```

### 3. Get Deployment Information

After deployment, the script will output:
- **Database credentials**
- **JWT secret**
- **API endpoints**
- **External IP address**

**Save these credentials securely!**

### 4. DNS Configuration

Point your domain to the external IP:
```bash
# Get the external IP
kubectl get ingress supabase-ingress

# Add DNS A records:
# supabase.yourdomain.com -> EXTERNAL_IP
# auth.supabase.yourdomain.com -> EXTERNAL_IP  
# api.supabase.yourdomain.com -> EXTERNAL_IP
# realtime.supabase.yourdomain.com -> EXTERNAL_IP
# storage.supabase.yourdomain.com -> EXTERNAL_IP
```

### 5. Update BOSS Configuration

Update your BOSS desktop app configuration:

1. **Replace Supabase URL and keys** in `SupabaseConfig.kt`
2. **Rebuild and test** the desktop app
3. **Verify authentication flow** works with self-hosted instance

## 🔧 Monitoring and Management

### View Logs
```bash
# All Supabase services
kubectl logs -l app=supabase -f

# Specific service
kubectl logs deployment/supabase-auth -f
kubectl logs deployment/supabase-rest -f
kubectl logs deployment/postgres -f
```

### Database Access
```bash
# Connect to PostgreSQL
kubectl exec -it deployment/postgres -- psql -U supabase_admin postgres

# Port forward for local access
kubectl port-forward service/postgres 5432:5432
```

### Scale Services
```bash
# Scale REST API for more load
kubectl scale deployment supabase-rest --replicas=5

# Scale Auth service
kubectl scale deployment supabase-auth --replicas=3
```

### Backup Database
```bash
# Create database backup
kubectl exec deployment/postgres -- pg_dump -U supabase_admin postgres > supabase_backup.sql

# Restore from backup
kubectl exec -i deployment/postgres -- psql -U supabase_admin postgres < supabase_backup.sql
```

## 🔒 Security Considerations

### 1. Change Default Passwords
The deployment script generates secure passwords, but you should rotate them periodically:

```bash
# Generate new password
NEW_PASSWORD=$(openssl rand -base64 32)

# Update secret
kubectl patch secret postgres-secrets -p='{"stringData":{"POSTGRES_PASSWORD":"'$NEW_PASSWORD'"}}'

# Restart PostgreSQL
kubectl rollout restart statefulset postgres
```

### 2. JWT Secret Rotation
```bash
# Generate new JWT secret
NEW_JWT=$(openssl rand -base64 64)

# Update all services that use JWT
kubectl patch secret postgres-secrets -p='{"stringData":{"JWT_SECRET":"'$NEW_JWT'"}}'
kubectl patch secret supabase-auth-secrets -p='{"stringData":{"GOTRUE_JWT_SECRET":"'$NEW_JWT'"}}'

# Restart services
kubectl rollout restart deployment supabase-auth
kubectl rollout restart deployment supabase-rest
```

### 3. Network Security
- The deployment uses private GKE cluster
- All inter-service communication is within the cluster
- Only the ingress exposes services externally
- Use TLS/SSL certificates for HTTPS

### 4. Database Security
- PostgreSQL runs with non-root user
- Row Level Security (RLS) should be configured for your tables
- Regular backups are essential

## 🚨 Troubleshooting

### Common Issues

1. **Services not starting**
   ```bash
   kubectl describe pods -l app=supabase
   kubectl logs deployment/supabase-auth
   ```

2. **Database connection issues**
   ```bash
   kubectl exec deployment/postgres -- pg_isready -U supabase_admin
   ```

3. **External IP not assigned**
   ```bash
   kubectl describe ingress supabase-ingress
   ```

4. **SSL/TLS certificate issues**
   ```bash
   kubectl describe managedcertificate supabase-ssl-cert
   ```

### BOSS Desktop App Issues

1. **Authentication failures**
   - Check if the Supabase URL is reachable
   - Verify API keys are correct
   - Check network connectivity

2. **Deep link verification not working**
   - Ensure Auth service `GOTRUE_SITE_URL` is configured correctly
   - Check deep link handler in desktop app

## 📈 Performance Optimization

### Resource Scaling
- **PostgreSQL**: Increase memory for better caching
- **REST API**: Scale replicas for high query load  
- **Auth**: Scale during high sign-up periods
- **Realtime**: Scale for concurrent connections

### Storage Optimization
- Use SSD storage for PostgreSQL
- Regular VACUUM and ANALYZE on database
- Monitor storage usage and scale PVCs as needed

## 🎯 Next Steps

1. **Deploy Supabase to GKE** using the provided scripts
2. **Update BOSS desktop app** configuration  
3. **Test authentication flow** end-to-end
4. **Set up monitoring** and alerting
5. **Configure backups** and disaster recovery
6. **Implement CI/CD** for updates

Your self-hosted Supabase instance will provide the same functionality as the hosted service but with full control over your data and infrastructure!