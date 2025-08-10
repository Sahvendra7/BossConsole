#!/usr/bin/env python3
import jwt
import json
from datetime import datetime, timedelta

# JWT secret from our deployment
JWT_SECRET = "your-super-secret-jwt-key-at-least-32-characters-long-for-production"

# Generate anon key (public, read-only access)
anon_payload = {
    "iss": "supabase",
    "ref": "boss-self-hosted",
    "role": "anon",
    "iat": int(datetime.utcnow().timestamp()),
    "exp": int((datetime.utcnow() + timedelta(days=3650)).timestamp())  # 10 years
}

# Generate service role key (admin access)
service_payload = {
    "iss": "supabase", 
    "ref": "boss-self-hosted",
    "role": "service_role",
    "iat": int(datetime.utcnow().timestamp()),
    "exp": int((datetime.utcnow() + timedelta(days=3650)).timestamp())  # 10 years
}

# Generate tokens
anon_token = jwt.encode(anon_payload, JWT_SECRET, algorithm="HS256")
service_token = jwt.encode(service_payload, JWT_SECRET, algorithm="HS256")

print("=== BOSS Self-hosted Supabase JWT Keys ===")
print(f"ANON_KEY: {anon_token}")
print(f"SERVICE_ROLE_KEY: {service_token}")
print()
print("=== Configuration for SupabaseConfig.kt ===")
print(f'val url = "http://34.9.246.150"  // Auth service endpoint')
print(f'val anonKey = "{anon_token}"')