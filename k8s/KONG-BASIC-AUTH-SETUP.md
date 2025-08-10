# Kong Basic Auth Setup for Studio Protection

## Overview
Studio dashboard (`https://studio.risaboss.com`) is now protected with HTTP Basic Authentication via Kong Gateway's built-in `basic-auth` plugin.

## Configuration

### Kong Consumer & Credentials
```yaml
consumers:
  - username: admin
    basicauth_credentials:
      - username: admin
        password: StudioAdmin2025!
```

### Service Protection
```yaml
- name: studio-service
  url: http://supabase-studio:3000
  plugins:
    - name: basic-auth
      config:
        hide_credentials: true
```

## Access Credentials

**Studio Dashboard Access:**
- **URL**: https://studio.risaboss.com
- **Username**: `admin`
- **Password**: `StudioAdmin2025!`

## Testing Results

✅ **Without credentials**: Returns `401 Unauthorized`
```bash
curl -s https://studio.risaboss.com/
# Returns: 401 Unauthorized
```

✅ **With correct credentials**: Returns `200 OK` 
```bash
curl -u admin:StudioAdmin2025! https://studio.risaboss.com/
# Returns: 200 OK (with Studio content)
```

✅ **With wrong credentials**: Returns `401 Unauthorized`
```bash
curl -u admin:WrongPassword https://studio.risaboss.com/
# Returns: 401 Unauthorized
```

✅ **Other services unaffected**: API services work without authentication
```bash
curl https://api.risaboss.com/auth/v1/health
# Returns: 200 OK (no auth required)
```

## Security Features

### Kong Basic Auth Plugin Benefits:
- **Native Integration**: Uses Kong's built-in authentication
- **Credential Hiding**: `hide_credentials: true` removes auth headers from upstream requests
- **No Additional Infrastructure**: No extra nginx proxies needed
- **Selective Protection**: Only Studio is protected, API services remain open
- **HTTPS Enforced**: All traffic encrypted via SSL certificates

### Browser Experience:
When accessing https://studio.risaboss.com in a browser:
1. Browser shows HTTP Basic Auth dialog
2. Enter username: `admin`, password: `StudioAdmin2025!`
3. Access granted to Studio dashboard
4. Credentials cached by browser for session

## Architecture

```
User Request → HTTPS Load Balancer → Kong Gateway → Basic Auth Plugin → Studio Service
           (SSL Termination)      (Authentication)              (Dashboard)
```

## Maintenance

### To Update Password:
1. Edit `k8s/configs/kong-config.yaml`
2. Change password in `basicauth_credentials`
3. Apply: `kubectl apply -f k8s/configs/kong-config.yaml`
4. Restart: `kubectl rollout restart deployment supabase-kong`

### To Add More Users:
```yaml
consumers:
  - username: admin
    basicauth_credentials:
      - username: admin
        password: StudioAdmin2025!
  - username: developer
    basicauth_credentials:
      - username: developer
        password: AnotherSecurePassword456
```

### To Remove Authentication:
Remove the `plugins` section from `studio-service` in kong-config.yaml

## Security Recommendations

1. **Change Default Password**: Update password from default value
2. **Use Strong Passwords**: Minimum 16 characters with mixed case, numbers, symbols
3. **Regular Rotation**: Change passwords quarterly
4. **Monitor Access**: Check Kong logs for unauthorized attempts
5. **HTTPS Only**: Never access over HTTP (already enforced by SSL redirect)

The Studio is now secured while maintaining the seamless API access for applications.