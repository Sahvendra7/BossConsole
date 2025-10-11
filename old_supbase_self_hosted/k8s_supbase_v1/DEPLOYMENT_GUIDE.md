# BOSS Passkey Authentication - Deployment Guide

## 🚀 Quick Deployment to Self-Hosted Supabase

### Step 1: Apply Database Migration

Connect to your PostgreSQL instance and run the migration:

```bash
# Using kubectl to connect to your database pod
kubectl exec -it postgres-0 -- psql -U postgres -d postgres -f /app/migrations/20240811_create_passkey_tables.sql

# Or if you have direct database access
psql -h your-db-host -U postgres -d boss_db -f supabase/migrations/20240811_create_passkey_tables.sql
```

### Step 2: Deploy Consolidated Edge Function

```bash
cd supabase/
./deploy-functions.sh
```

This deploys a single consolidated function that handles all passkey operations via query parameters.

### Step 3: Verify Deployment

Check that the function is running:

```bash
kubectl get pods -l app=supabase,component=functions
kubectl logs -l app=supabase,component=functions
```

### Step 4: Test the Implementation

1. Build and run BOSS desktop app
2. Navigate to settings and set up Touch ID passkey
3. Log out and try "Sign in with Touch ID"

## 🎯 Current Implementation Status

✅ **Real Cryptographic Implementation**
- **No mock data**: All WebAuthn operations use real cryptography
- **ECDSA P-256**: Proper elliptic curve signatures with SHA-256
- **DER-to-Raw conversion**: Java signature format converted to Web Crypto API format
- **Key storage**: Real key pairs stored in macOS keychain and filesystem

✅ **Backend Architecture** 
- **Consolidated function**: Single `main.ts` handles all operations
- **Query parameter routing**: `/functions/v1/passkey-functions?op=auth-complete`
- **Real signature verification**: Server verifies ECDSA signatures properly
- **Database integration**: Proper storage and retrieval of public keys

## 📋 API Endpoints

The consolidated function provides these endpoints via query parameters:

```bash
# Registration flow
POST /functions/v1/passkey-functions?op=reg-challenge
POST /functions/v1/passkey-functions?op=reg-complete

# Authentication flow  
POST /functions/v1/passkey-functions?op=auth-challenge
POST /functions/v1/passkey-functions?op=auth-complete

# Management operations
POST /functions/v1/passkey-functions?op=list
POST /functions/v1/passkey-functions?op=delete
```

## 🔄 Scalable Deployment Process

### Current Structure
```
supabase/functions/
├── passkey-functions/     # Passkey authentication functions
│   └── index.ts
├── hello/                 # Example function
│   └── index.ts
└── [future-functions]/    # Add more functions here
    └── index.ts
```

### Deployment Commands

**Deploy all functions:**
```bash
cd supabase/
./deploy-all-functions.sh
```

**Update specific function:**
```bash
./update-functions.sh passkey-functions  # Update only passkey functions
./update-functions.sh hello             # Update only hello function
./update-functions.sh                   # Update all functions
```

**Legacy deployment (passkey-functions only):**
```bash
./deploy-functions.sh  # Backwards compatible
```

### Adding New Functions

1. **Create function directory:**
   ```bash
   mkdir functions/my-new-function
   ```

2. **Add index.ts:**
   ```typescript
   // functions/my-new-function/index.ts
   import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
   
   serve(async (req) => {
     return new Response(JSON.stringify({ message: "My new function!" }))
   })
   ```

3. **Deploy:**
   ```bash
   ./deploy-all-functions.sh
   ```

### Verify Deployment
```bash
kubectl rollout status deployment/supabase-functions
kubectl logs -l app=supabase,component=functions --tail=50
```

## 🧪 Testing Complete Flow

The implementation is production-ready with real cryptography:

1. **Touch ID Registration**: Users can register biometric credentials
2. **Real signatures**: ECDSA P-256 signatures with proper DER-to-raw conversion
3. **Secure verification**: Server validates signatures using Web Crypto API
4. **Key management**: Proper storage/retrieval of cryptographic key pairs

This provides enterprise-grade passwordless authentication leveraging platform biometrics! 🔐