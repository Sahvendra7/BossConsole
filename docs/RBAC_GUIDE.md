# BOSS RBAC System - Role-Based Access Control

## Overview

BOSS implements a comprehensive Role-Based Access Control (RBAC) system using Supabase's native Custom Claims and Auth Hooks. This system provides secure, scalable role management with minimal overhead and maximum flexibility for plugin extensions.

## Architecture

### Components

1. **Database Layer** - PostgreSQL tables, enums, RLS policies, and functions
2. **Auth Hooks** - JWT claim injection for Supabase native auth (magic link, OAuth)
3. **Edge Functions** - Custom JWT generation for passkey authentication with RBAC claims
4. **Kotlin Services** - Client-side role management and checking
5. **JWT Integration** - Role claims embedded in access tokens (both auth methods)

### Data Flow

```
User Signs Up
    ↓
handle_new_user() trigger → Assigns 'user' role
    ↓
User Authenticates
    ↓
custom_access_token_hook() → Injects role claims into JWT
    ↓
Client receives JWT with claims
    ↓
RoleService parses claims → Available to application
    ↓
RLS policies enforce permissions at database level
```

### Authentication Methods & RBAC

BOSS supports multiple authentication methods, and RBAC role claims are consistently included across all of them:

#### 1. Magic Link Authentication (Supabase Native)
- User requests magic link via email
- Supabase Auth validates the link
- **Auth Hook** (`custom_access_token_hook`) runs before JWT issuance
- Hook fetches user roles from database
- JWT issued with `user_role`, `user_roles`, and `is_admin` claims

#### 2. Passkey Authentication (Custom)
- User authenticates with Touch ID/Windows Hello/Security Key
- Edge Function (`/functions/v1/passkey`) verifies signature
- Edge Function **fetches user roles** from `user_roles` table
- Custom JWT generated with RBAC claims included
- Client imports session with full role information

**Key Point:** Both methods produce identical JWT claim structures, ensuring consistent RBAC behavior regardless of authentication method.

**JWT Claims Structure (Both Methods):**
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "authenticated",
  "user_role": "user",           // Primary role
  "user_roles": ["user", "admin"], // All roles
  "is_admin": true                // Admin flag
}
```

## Database Schema

### Tables

#### `user_roles`
Maps users to their assigned roles (many-to-many relationship).

```sql
CREATE TABLE public.user_roles (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id),
    role app_role NOT NULL,
    assigned_by UUID REFERENCES auth.users(id),
    assigned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    UNIQUE (user_id, role)
);
```

**Fields:**
- `id` - Unique identifier
- `user_id` - Reference to user in auth.users
- `role` - The assigned role (from app_role enum)
- `assigned_by` - Admin who assigned the role (audit trail)
- `assigned_at` - When the role was assigned (audit trail)

#### `role_permissions`
Maps roles to specific permissions for fine-grained access control.

```sql
CREATE TABLE public.role_permissions (
    id UUID PRIMARY KEY,
    role app_role NOT NULL,
    permission app_permission NOT NULL,
    created_at TIMESTAMPTZ,
    UNIQUE (role, permission)
);
```

### Enums

#### `app_role`
Defines available roles in the system.

```sql
CREATE TYPE public.app_role AS ENUM ('user', 'admin');
```

**System Roles:**
- `user` - Default role for all users
- `admin` - Administrative role with full permissions

**Extensibility:** Add plugin-specific roles with:
```sql
ALTER TYPE public.app_role ADD VALUE 'plugin_xyz_manager';
```

#### `app_permission`
Defines granular permissions.

```sql
CREATE TYPE public.app_permission AS ENUM (
    'users.read',
    'users.write',
    'workspaces.read',
    'workspaces.write',
    'workspaces.delete',
    'plugins.install',
    'plugins.manage',
    'admin.access'
);
```

## Kotlin Integration

### Models

#### `AppRole` Enum
```kotlin
enum class AppRole(val value: String) {
    USER("user"),
    ADMIN("admin");

    companion object {
        fun fromString(value: String): AppRole?
        fun fromStringOrDefault(value: String?, default: AppRole = USER): AppRole
    }
}
```

#### `RoleClaims` Data Class
```kotlin
data class RoleClaims(
    val userRole: AppRole,        // Primary role
    val userRoles: List<AppRole>,  // All roles
    val isAdmin: Boolean           // Quick admin check
) {
    fun hasRole(role: AppRole): Boolean
    fun hasAnyRole(vararg roles: AppRole): Boolean
    fun hasAllRoles(vararg roles: AppRole): Boolean
}
```

#### Enhanced `UserInfo`
```kotlin
data class UserInfo(
    val id: String,
    val email: String,
    val createdAt: String,
    val roleClaims: RoleClaims? = null
) {
    val primaryRole: AppRole
    val roles: List<AppRole>
    val isAdmin: Boolean
    fun hasRole(role: AppRole): Boolean
}
```

### Services

#### `RoleService`
Core service for role management operations.

```kotlin
object RoleService {
    // Parse role claims from JWT session
    fun parseRoleClaimsFromSession(session: UserSession?): RoleClaims?

    // Get user roles
    suspend fun getUserRoles(userId: String): Result<List<UserRole>>

    // Check if user has role
    suspend fun userHasRole(userId: String, role: AppRole): Result<Boolean>
    suspend fun isUserAdmin(userId: String): Result<Boolean>

    // Assign/remove roles (admin only)
    suspend fun assignRole(targetUserId: String, role: AppRole): Result<Unit>
    suspend fun removeRole(targetUserId: String, role: AppRole): Result<Unit>

    // Permission checking
    suspend fun getRolePermissions(role: AppRole): Result<List<RolePermission>>
    suspend fun canPerformAction(userId: String, permission: AppPermission): Result<Boolean>
}
```

#### `AuthService` Extensions
```kotlin
object AuthService {
    // Current user role checking
    fun getCurrentUserRoleClaims(): RoleClaims?
    fun isCurrentUserAdmin(): Boolean
    fun currentUserHasRole(role: AppRole): Boolean

    // Role management (proxies to RoleService)
    suspend fun assignRole(targetUserId: String, role: AppRole): Result<Unit>
    suspend fun removeRole(targetUserId: String, role: AppRole): Result<Unit>
    suspend fun getUserRoles(userId: String): Result<List<UserRole>>
    suspend fun userHasPermission(userId: String, permission: AppPermission): Result<Boolean>
}
```

## Usage Examples

### Check Current User's Role

```kotlin
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.models.AppRole

// Simple admin check
if (AuthService.isCurrentUserAdmin()) {
    println("User is an admin")
}

// Check specific role
if (AuthService.currentUserHasRole(AppRole.ADMIN)) {
    println("User has admin role")
}

// Get all role claims
val claims = AuthService.getCurrentUserRoleClaims()
claims?.let {
    println("Primary role: ${it.userRole}")
    println("All roles: ${it.userRoles}")
    println("Is admin: ${it.isAdmin}")
}
```

### Observe Current User's Roles

```kotlin
import ai.rever.boss.services.supabase.AuthService
import kotlinx.coroutines.flow.collectLatest

// Observe user changes
AuthService.currentUser.collectLatest { user ->
    user?.let {
        println("User: ${it.email}")
        println("Primary role: ${it.primaryRole}")
        println("Is admin: ${it.isAdmin}")

        if (it.hasRole(AppRole.ADMIN)) {
            // Show admin UI
        }
    }
}
```

### Assign Role (Admin Only)

```kotlin
import ai.rever.boss.services.supabase.AuthService
import ai.rever.boss.services.supabase.models.AppRole

// Must be called by an admin
val result = AuthService.assignRole(
    targetUserId = "user-uuid",
    role = AppRole.ADMIN
)

result.fold(
    onSuccess = { println("Role assigned successfully") },
    onFailure = { error -> println("Failed to assign role: ${error.message}") }
)
```

### Remove Role (Admin Only)

```kotlin
val result = AuthService.removeRole(
    targetUserId = "user-uuid",
    role = AppRole.ADMIN
)

result.fold(
    onSuccess = { println("Role removed successfully") },
    onFailure = { error -> println("Failed to remove role: ${error.message}") }
)
```

### Check Permissions

```kotlin
import ai.rever.boss.services.supabase.models.AppPermission

val canManagePlugins = AuthService.userHasPermission(
    userId = "user-uuid",
    permission = AppPermission.PLUGINS_MANAGE
)

canManagePlugins.fold(
    onSuccess = { hasPermission ->
        if (hasPermission) {
            // Allow plugin management
        }
    },
    onFailure = { /* Handle error */ }
)
```

### UI Example: Conditional Rendering

```kotlin
@Composable
fun AdminPanel() {
    val currentUser by AuthService.currentUser.collectAsState()

    if (currentUser?.isAdmin == true) {
        Column {
            Text("Admin Panel")
            Button(onClick = { /* Admin action */ }) {
                Text("Manage Users")
            }
        }
    } else {
        Text("Access Denied")
    }
}
```

## Database Functions

### Admin Functions

#### `assign_role_to_user(target_user_id, target_role)`
Assigns a role to a user. Only callable by admins.

```sql
SELECT public.assign_role_to_user(
    'user-uuid'::uuid,
    'admin'::public.app_role
);
```

#### `remove_role_from_user(target_user_id, target_role)`
Removes a role from a user. Only callable by admins. Cannot remove own admin role.

```sql
SELECT public.remove_role_from_user(
    'user-uuid'::uuid,
    'admin'::public.app_role
);
```

### Query Functions

#### `user_has_role(check_user_id, check_role)`
Check if a user has a specific role.

```sql
SELECT public.user_has_role(
    'user-uuid'::uuid,
    'admin'::public.app_role
);
```

#### `is_user_admin(check_user_id)`
Quick check if a user is an admin.

```sql
SELECT public.is_user_admin('user-uuid'::uuid);
```

#### `get_user_roles(check_user_id)`
Get all roles for a user.

```sql
SELECT * FROM public.get_user_roles('user-uuid'::uuid);
```

#### `authorize(requested_permission)`
Check if current user (from JWT) has a permission. Used in RLS policies.

```sql
-- In RLS policy
CREATE POLICY "Admins can delete" ON workspaces
    FOR DELETE
    USING (public.authorize('workspaces.delete'));
```

## Row Level Security (RLS)

### Policy Examples

#### User Roles Table

**Users can view their own roles:**
```sql
CREATE POLICY "Users can view their own roles"
    ON public.user_roles
    FOR SELECT
    USING (auth.uid() = user_id);
```

**Admins can view all roles:**
```sql
CREATE POLICY "Admins can view all roles"
    ON public.user_roles
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );
```

**Only admins can assign roles:**
```sql
CREATE POLICY "Admins can assign roles"
    ON public.user_roles
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );
```

### Using Permissions in Custom Policies

```sql
-- Example: Only users with 'workspaces.delete' permission can delete
CREATE POLICY "Authorized users can delete workspaces"
    ON public.workspaces
    FOR DELETE
    USING (public.authorize('workspaces.delete'));
```

## Setup Instructions

### 1. Deploy Migrations

```bash
# Navigate to project root
cd /path/to/boss-main

# Deploy migrations using Supabase CLI
supabase db push

# Or apply migrations manually in Supabase Dashboard
# SQL Editor → New Query → Paste migration content → Run
```

### 2. Enable Auth Hook

1. Go to Supabase Dashboard
2. Navigate to **Authentication → Hooks**
3. Enable **Custom Access Token Hook**
4. Select: `public.custom_access_token_hook`
5. Save changes

### 3. Create First Admin (Manual)

Since the first admin must be created manually (chicken-and-egg problem):

```sql
-- Find your user ID
SELECT id, email FROM auth.users WHERE email = 'your-email@example.com';

-- Assign admin role
INSERT INTO public.user_roles (user_id, role)
VALUES ('your-user-uuid', 'admin');
```

After this, the admin can assign roles to other users via the application.

### 4. Test the System

```kotlin
// In your app initialization
suspend fun testRBAC() {
    // Sign in
    AuthService.sendMagicLink("test@example.com")

    // After authentication
    val claims = AuthService.getCurrentUserRoleClaims()
    println("Role: ${claims?.userRole}") // Should print: Role: user or Role: admin

    // For admins
    if (AuthService.isCurrentUserAdmin()) {
        val result = AuthService.assignRole("other-user-id", AppRole.ADMIN)
        println("Assign result: $result")
    }
}
```

## Plugin Integration

### Adding Plugin-Specific Roles

1. **Extend the enum** (via migration):
```sql
ALTER TYPE public.app_role ADD VALUE 'plugin_analytics_viewer';
ALTER TYPE public.app_role ADD VALUE 'plugin_analytics_manager';
```

2. **Add Kotlin enum values**:
```kotlin
enum class AppRole(val value: String) {
    USER("user"),
    ADMIN("admin"),
    PLUGIN_ANALYTICS_VIEWER("plugin_analytics_viewer"),
    PLUGIN_ANALYTICS_MANAGER("plugin_analytics_manager");
}
```

3. **Add plugin permissions** (optional):
```sql
ALTER TYPE public.app_permission ADD VALUE 'plugin.analytics.view';
ALTER TYPE public.app_permission ADD VALUE 'plugin.analytics.manage';

-- Map permissions to roles
INSERT INTO public.role_permissions (role, permission)
VALUES
    ('plugin_analytics_viewer', 'plugin.analytics.view'),
    ('plugin_analytics_manager', 'plugin.analytics.view'),
    ('plugin_analytics_manager', 'plugin.analytics.manage');
```

4. **Use in plugin code**:
```kotlin
// Check if user can access plugin
if (AuthService.currentUserHasRole(AppRole.PLUGIN_ANALYTICS_VIEWER)) {
    // Show analytics dashboard
}

// Or check permission
val canManage = AuthService.userHasPermission(
    userId = currentUserId,
    permission = AppPermission.fromString("plugin.analytics.manage")!!
)
```

### Dynamic Role Management (Alternative)

For plugins that need to create roles dynamically without migrations:

1. **Add a custom_roles column** (future enhancement):
```sql
ALTER TABLE public.user_roles ADD COLUMN custom_role TEXT;
```

2. **Plugins can manage roles via API** using the custom_role field for plugin-specific roles while keeping system roles in the enum.

## Security Considerations

### ⚠️ CRITICAL: Client-Side JWT Parsing Security Model

**The client does NOT verify JWT signatures.** This is intentional and follows industry best practices:

#### Why No Client-Side Signature Verification?

1. **JWT Already Verified Server-Side**: Supabase Auth verifies signatures when issuing tokens
2. **Client Cannot Be Trusted**: Any code running on the client can be modified/debugged
3. **Performance**: Signature verification is expensive and unnecessary on client
4. **Security Happens Server-Side**: RLS policies enforce all authorization

#### What Client-Side Role Checks Are For

✅ **Safe Uses:**
- Showing/hiding UI elements (buttons, menu items)
- Displaying role badges and user info
- Optimistic UI updates
- Reducing unnecessary API calls

❌ **NEVER Use For:**
- Granting access to sensitive data
- Bypassing server-side authorization
- Making security decisions
- Skipping database permission checks

#### Security Architecture

```
Client Side                          Server Side
┌──────────────────┐                ┌──────────────────────┐
│ Parse JWT Claims │                │ Verify JWT Signature │
│ (informational)  │   →  API  →    │ Check RLS Policies   │
│                  │      Call       │ Execute if Authorized│
└──────────────────┘                └──────────────────────┘
     UI Only                         Actual Security
```

**Example: Admin Role Check**

```kotlin
// Client side (UI convenience)
if (AuthService.isCurrentUserAdmin()) {
    // ✅ Show "Delete User" button
    // ❌ DO NOT skip server call
}

// Server side (actual security)
// RLS Policy on user_roles table:
CREATE POLICY "Only admins can delete users" ON auth.users
  FOR DELETE USING (
    public.is_user_admin(auth.uid())  -- Verifies JWT claims server-side
  );
```

**Key Principle:** Client-side checks are **optimistic assumptions**. Server-side checks are **enforced guarantees**.

### 1. Admin Protection
- Users cannot remove their own admin role (prevents lockout)
- First admin must be created manually via SQL
- All role changes are audited (assigned_by, assigned_at)

### 2. JWT Security
- **Role claims are signed** in JWT by Supabase (server-side)
- **Signature verification** happens server-side on every API request
- **Client parsing is informational only** - does NOT verify signature
- Claims refresh automatically on token renewal (every hour)
- Forged JWTs are rejected by Supabase API (signature mismatch)
- Server-side RLS policies check `auth.jwt()` claims (verified)

### 3. RLS Policies
- All tables with roles/permissions have RLS enabled
- Service role bypasses RLS (for Edge Functions only)
- Users can only see their own roles
- Admins can view/modify all roles (verified server-side)
- RLS policies use `auth.jwt()` which contains verified claims

### 4. Permission Checking
- Always use `authorize()` function in RLS policies
- **Never trust client-side role checks alone**
- Database enforces permissions regardless of client claims
- All mutations protected by RLS + database functions
- Edge Functions use service role to bypass RLS (trusted environment)

## Troubleshooting

### Roles not appearing in JWT

**For Magic Link Authentication:**

1. **Check auth hook is enabled:**
   - Dashboard → Authentication → Hooks → Custom Access Token Hook

2. **Verify role assignment:**
   ```sql
   SELECT * FROM public.user_roles WHERE user_id = 'your-user-uuid';
   ```

3. **Test hook function directly:**
   ```sql
   SELECT public.custom_access_token_hook(
       jsonb_build_object(
           'user_id', 'your-user-uuid',
           'claims', '{}'::jsonb
       )
   );
   ```

**For Passkey Authentication:**

1. **Verify Edge Function is deployed:**
   ```bash
   supabase functions deploy passkey --project-ref YOUR_PROJECT_REF
   ```

2. **Check Edge Function has JWT_SECRET configured:**
   - Dashboard → Edge Functions → passkey → Settings
   - Ensure `JWT_SECRET` environment variable is set

3. **Verify role assignment:**
   ```sql
   SELECT * FROM public.user_roles WHERE user_id = 'your-user-uuid';
   ```

**For Both Methods:**

4. **Re-authenticate:** Log out and log back in to get a fresh JWT with updated claims.

5. **Check JWT payload in logs:** Look for debug output showing parsed claims:
   ```
   🔍 [RBAC DEBUG] JWT Claims parsed:
     user_role: user
     user_roles: [user, admin]
     is_admin: true
   ```

### "Only admins can assign roles" error

- Ensure the user making the call is an admin
- Check: `SELECT * FROM public.user_roles WHERE user_id = auth.uid() AND role = 'admin';`
- First admin must be created manually via SQL

### RLS policies blocking access

- Service role bypasses RLS - use for admin operations
- Check if user has required role: `SELECT public.user_has_role(auth.uid(), 'admin');`
- Verify RLS policies allow the operation

### JWT decode errors in Kotlin

- Ensure `java.util.Base64` is available (should be standard in JVM)
- Check JWT format (should be three dot-separated parts)
- Verify access token is not expired

## Performance Considerations

### Caching
- Role claims are cached in JWT (no database lookup on every request)
- JWT refresh updates role claims automatically
- RLS policies use efficient indexes on user_id and role columns

### Indexes
All tables have appropriate indexes:
```sql
CREATE INDEX idx_user_roles_user_id ON public.user_roles(user_id);
CREATE INDEX idx_user_roles_role ON public.user_roles(role);
CREATE INDEX idx_role_permissions_role ON public.role_permissions(role);
```

### Query Optimization
- Use `user_has_role()` function for simple checks
- Use `authorize()` in RLS policies (efficient with proper indexes)
- Batch role checks when possible

## Future Enhancements

### Potential Additions
1. **Role Hierarchy** - Roles that inherit permissions from other roles
2. **Temporary Roles** - Time-limited role assignments
3. **Role Groups** - Collections of roles for easy assignment
4. **Audit Log UI** - View who assigned what role and when
5. **Permission Builder** - UI for creating custom permissions
6. **Multi-Tenant Roles** - Roles scoped to specific workspaces/organizations

## References

- [Supabase RBAC Documentation](https://supabase.com/docs/guides/database/postgres/custom-claims-and-role-based-access-control-rbac)
- [PostgreSQL Row Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [JWT Claims](https://datatracker.ietf.org/doc/html/rfc7519#section-4)
- [Issue #54](https://github.com/risa-labs-inc/BOSS-Kotlin/issues/54) - Original requirement

## Support

For questions or issues with the RBAC system:
1. Check this documentation
2. Review the code comments in migration files
3. Open an issue on GitHub
4. Contact the development team

---

**Version:** 1.2.0
**Last Updated:** 2025-10-18
**Author:** BOSS Development Team

**Changelog:**
- v1.2.0 (2025-10-18): Added critical security model documentation (JWT signature verification)
- v1.1.0 (2025-10-18): Added passkey authentication RBAC integration documentation
- v1.0.0 (2025-01-18): Initial RBAC system release
