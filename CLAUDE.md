# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BOSS (Business Operating System Service) is a desktop application built with Kotlin Multiplatform and Compose Multiplatform, featuring sophisticated WebAuthn/passkey authentication. The project uses a centralized version management system and targets desktop platforms (macOS, Windows, Linux).

## Essential Commands

### Development
```bash
./gradlew run                    # Run desktop application
./gradlew showVersion           # Display current version info
./gradlew test                  # Run tests
./gradlew build                 # Build application
```

### Platform-specific Builds
```bash
./gradlew packageDmg            # Build macOS DMG
./gradlew packageMsi            # Build Windows MSI
./gradlew packageDistributionForCurrentOS  # Linux packages (DEB/RPM)
./gradlew createExecutableJar   # Create executable JAR
```

### Version Management
```bash
./gradlew incrementVersion      # Increment patch version
./gradlew incrementMinor        # Increment minor version
./gradlew incrementMajor        # Increment major version
./gradlew generateVersionConstants  # Generate version constants
```

## Architecture Overview

### Module Structure
- **`composeApp/`** - Main Compose Multiplatform UI application
- **`server/`** - Minimal Ktor server component
- **`shared/`** - Shared business logic
- **`supabase/`** - Database migrations and Edge Functions

### Key Technologies
- **Kotlin Multiplatform** with **Compose Multiplatform** for UI
- **JxBrowser 8.8.0** for WebAuthn integration and browser functionality
- **Decompose** for component-based navigation
- **Supabase** as Backend-as-a-Service with Edge Functions
- **PTY4J** for terminal integration

### Target Platforms
Currently focused on **desktop only**: macOS, Windows, Linux. Mobile targets (Android, iOS) are disabled.

## Authentication System Architecture

### WebAuthn/Passkey Implementation
The app implements a sophisticated WebAuthn system with cross-device support:

**Core Services:**
- **`PasskeyService`** - Cross-platform interface
- **`DesktopPasskeyService`** - Desktop implementation with biometric integration
- **`SupabasePasskeyService`** - Server-side passkey management

**Authentication Flows:**
1. **Local Biometric**: Touch ID (macOS), Windows Hello (Windows)
2. **Cross-device**: QR code generation for mobile/browser authentication
3. **Session Coordination**: `sessionId` tracking across devices

**Session Generation** (Fixed in PR #78):
- Uses **Supabase Admin API** (`admin.generateLink()` + `verifyOtp()`)
- Generates proper sessions with unique string refresh tokens
- Tokens stored in `auth.sessions` table for automatic rotation
- Auth hooks inject RBAC claims during token generation
- **No manual JWT generation** - Supabase handles all token signing

**Database Schema** (Supabase):
- `user_passkeys` table with RLS policies
- `passkey_challenges` table for temporary challenge storage
- `auth.sessions` table for refresh token tracking
- Edge Functions at `/functions/v1/passkey`

### Platform-Specific Integration
- **macOS**: Swift scripts for Touch ID, Keychain Services integration
- **Windows**: PowerShell scripts for Windows Hello, Credential Manager
- **Cryptography**: ECDSA P-256 signatures, proper WebAuthn client data handling

## Configuration Management

### Development Setup
Create `local.properties` file:
```properties
# JxBrowser license
jxbrowser.license.key=<your-license-key>

# Supabase configuration (using Supabase Cloud)
SUPABASE_URL=https://api.risaboss.com
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_FUNCTION_URL=https://api.risaboss.com/functions/v1

# GitHub API token (optional, recommended for development)
# Without token: 60 requests/hour (unauthenticated)
# With token: 5,000 requests/hour (authenticated)
# Option 1: Run `gh auth login` (auto-detected, no config needed)
# Option 2: Manual token from https://github.com/settings/tokens
GITHUB_TOKEN=ghp_your_token_here
```

### GitHub API Rate Limits
The update checker uses GitHub API to fetch release information. Rate limits apply:

**Unauthenticated (no token):**
- 60 requests per hour per IP address
- Suitable for production use
- May hit limits during rapid development/testing

**Authenticated (with token):**
- 5,000 requests per hour
- Recommended for development
- Prevents rate limit issues when frequently restarting app

**Setting up GitHub Authentication:**

**Option 1 (Easiest) - GitHub CLI:**
```bash
gh auth login
```
The app will automatically use `gh auth token` to get your token. No manual configuration needed!

**Option 2 - Manual Token:**
1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Give it a descriptive name (e.g., "BOSS Update Checker")
4. **No scopes needed** - public repo access only
5. Copy token and add to `local.properties`: `GITHUB_TOKEN=ghp_...`
6. Restart app to apply

**Priority Order:**
1. Environment variable: `GITHUB_TOKEN`
2. System property: `GITHUB_TOKEN`
3. `local.properties` file
4. GitHub CLI (`gh auth token`)
5. Unauthenticated (fallback)

### Supabase Deployment
The project uses **Supabase CLI** for all function deployments and database migrations:

```bash
# Deploy Edge Functions
supabase functions deploy <function-name> --project-ref pcnwqamqdnsadranufjv --no-verify-jwt

# Deploy all functions
supabase functions deploy --project-ref pcnwqamqdnsadranufjv --no-verify-jwt

# Link to remote project (first time)
supabase link --project-ref pcnwqamqdnsadranufjv
```

**Available Edge Functions:**
- `passkey` - WebAuthn/Passkey authentication endpoints
- `redirect` - HTTP to boss:// deep link conversion for magic links

### Supabase Cloud Configuration
The project uses **Supabase Cloud** (not self-hosted). Important dashboard configurations:

**Authentication → URL Configuration:**
- **Site URL**: `boss://auth/verify` (enables direct deep link magic links)
- **Redirect URLs**: Must include `boss://auth/verify`

**Authentication → Email Templates:**
- **Magic Link Template**: Use `supabase/templates/email/magic-link.html`
- Ensures magic links use proper deep link format instead of HTTPS redirects

### Configuration Priority
1. Environment variables (production/CI)
2. System properties
3. `local.properties` file
4. Fallback values (temporary - see Issue #33)

**Important**: The `local.properties` file is gitignored and contains sensitive keys.

### Migration Development Best Practices

**Golden Rule**: Only commit migrations that work correctly. Test locally first!

#### Development Workflow

1. **Create migration**:
   ```bash
   supabase db diff -f my_feature_name
   ```

2. **Test locally**:
   ```bash
   supabase db reset --linked
   # Verify the migration works
   ```

3. **If migration has bugs**:
   - ❌ DON'T create a fix migration
   - ✅ DELETE the migration file
   - ✅ Fix the SQL
   - ✅ Run `supabase db reset --linked` again
   - Repeat until it works

4. **Only commit when working**:
   - One feature = one clean migration file
   - No iterative fix files during development

#### After Production Deployment

- Migrations become **immutable**
- Never delete deployed migrations
- Add new migration to fix issues
- Periodically consolidate with `supabase db pull`

#### Periodic Consolidation

Every few months, consolidate migrations:
```bash
# Pull current production schema
supabase db pull --linked

# Delete old migrations
rm supabase/migrations/202510*.sql

# Rename new file to baseline
mv supabase/migrations/[new-file].sql supabase/migrations/[date]_baseline_schema.sql

# Commit
git add supabase/migrations/
git commit -m "chore: Consolidate migrations into baseline"
```

This keeps the migration history clean and manageable.

### Database Schema Structure

The BOSS database schema consists of **13 core tables** organized into functional areas:

#### Authentication & Passkeys
- `user_passkeys` - WebAuthn credential storage
- `passkey_challenges` - Temporary authentication challenges
- `completed_authentications` - Cross-device auth coordination

#### RBAC (Role-Based Access Control)
- `roles` - System and custom roles
- `permissions` - Granular permissions (format: `resource.action`)
- `role_permissions` - Many-to-many role-permission mapping
- `user_roles` - User role assignments
- `users` - User profile data synced with `auth.users`

#### Secrets Management
- `secrets` - Encrypted credential storage (website, username, password)
- `secret_metadata` - 2FA configuration (TOTP, recovery codes)
- `secret_tags` - Tags for organizing secrets
- `secret_shares` - User/role-based secret sharing
- `secret_access_log` - Audit trail for secret operations

#### Key Database Functions (42 total)

**RBAC Functions** (11):
- `assign_role_to_user`, `remove_role_from_user`
- `create_new_role`, `delete_role`, `get_all_roles`
- `create_new_permission`, `delete_permission`, `get_all_permissions`
- `assign_permission_to_role`, `remove_permission_from_role`
- `authorize` - Permission check for RLS policies

**Secret Functions** (9):
- `create_secret`, `update_secret`, `delete_secret`
- `get_user_secrets` - User's owned secrets
- `get_user_secrets_with_shared` - Owned + shared secrets (handles duplicates with DISTINCT ON)
- `search_user_secrets` - Search by website/username
- `share_secret`, `unshare_secret`, `get_secret_shares`

**Encryption Helpers** (4):
- `encrypt_text`, `decrypt_text` - AES encryption with base64 encoding
- `get_encryption_key` - Retrieves key from Supabase Vault
- `safe_decrypt_recovery_codes` - Safe decryption with error handling

**Passkey Functions** (4):
- `create_mobile_registration_session`, `get_session_status`
- `clean_expired_passkey_challenges`, `cleanup_expired_completed_authentications`

**Schema Notes**:
- All sensitive data encrypted with **AES + base64** (NOT PGP)
- 50 RLS policies enforce access control
- 35 indexes for query optimization
- 5 PostgreSQL extensions enabled (pgcrypto, uuid-ossp, supabase_vault, etc.)
- Recovery codes stored as encrypted JSON arrays in `secret_metadata.recovery_codes_encrypted`

## Version Management System

The project uses **centralized version management**:
- **Source**: `version.properties` file (currently v8.11.4)
- **Generation**: `gradle/version.gradle` generates `VersionConstants.kt`
- **CI Integration**: GitHub Actions automatically increment versions
- **Consistency**: All platforms use same version from single source

## Build and Deployment

### Running Workflows
- **`build.yml`** - Multi-platform testing (Ubuntu, macOS, Windows)
- **`release.yml`** - Production builds with code signing

### Code Signing
- **macOS**: P12 certificates, notarization via Apple Developer ID
- **Windows**: DigiCert KeyLocker integration
- **Artifacts**: DMG, MSI, DEB, RPM, JAR packages

### GitHub Secrets Required
- `JXBROWSER_LICENSE_KEY` - JxBrowser license for production builds
- `SUPABASE_ANON_KEY` - Supabase backend access
- Code signing certificates for macOS/Windows

## UI Architecture

### Compose Multiplatform Structure
- **BossAppWithAuth** - Main authentication wrapper
- **LoginScreen** - Handles login with passkey integration
- **Component-based UI** using Decompose navigation
- **Dark theme** with Material Design components

### Navigation
Uses **Decompose** for component lifecycle and navigation management rather than traditional Android Navigation.

### Top Bar Components
The application's top bar (`BossTopBar.kt`) provides core UI navigation and controls.

**Currently Implemented:**
- **Project Selector** - Switch between projects with recent project history
- **Workspace Management** - Save/load UI workspace layouts (if configured)
- **User Display** - Shows logged-in user's email
- **Sign Out** - Logout with confirmation dialog
- **Settings** - Access application settings

**Disabled Features** (commented out with tracking issues):
- **Git Integration** (#90) - Branch selector and git operations
- **Run/Debug Controls** (#91) - Lanager controls, Run, Debug, Stop buttons
- **Global Search** (#92) - Search for files, commands, or actions
- **Lanager Plugin** (#93) - AI agent swarm management (under discussion)

These features are commented out in the code with TODO references and will be implemented according to their respective GitHub issues.

### Keyboard Shortcuts System

The application features a comprehensive, customizable keyboard shortcuts system (Issue #201) with:

**Core Features:**
- **Context-aware shortcuts** - Different key bindings for GLOBAL, BROWSER, TERMINAL, EDITOR, and WORKSPACE contexts
- **Conflict detection** - Visual warnings when multiple shortcuts use the same key combination
- **Preset keymaps** - Pre-configured schemes: BOSS Default, VS Code, IntelliJ IDEA, Emacs
- **Import/Export** - Backup and share keymap configurations via JSON
- **UI Editor** - Visual interface for capturing and editing shortcuts
- **JSON Editing** - Direct file editing at `~/.boss/keymap-settings.json`

**Architecture:**

*Data Models* (composeApp/src/commonMain/kotlin/ai/rever/boss/keymap/model/):
- **`ShortcutContext.kt`** - Enum defining where shortcuts are active (GLOBAL, BROWSER, etc.)
- **`KeyBinding.kt`** - Individual shortcut with key, modifiers, context, category, description
- **`KeymapSettings.kt`** - Container for all shortcuts with preset tracking
- **`KeymapActions.kt`** - Registry of 14+ action IDs with metadata

*Handler System* (composeApp/src/commonMain/kotlin/ai/rever/boss/keymap/handler/):
- **`KeymapMatcher.kt`** - Matches keyboard events to configured bindings
- **`KeymapValidator.kt`** - Detects conflicts and validates shortcuts
- **`KeymapHandler.kt`** - Context-aware event dispatcher (used in BossApp.kt)

*Presets* (composeApp/src/commonMain/kotlin/ai/rever/boss/keymap/presets/):
- **`KeymapPresets.kt`** - BOSS Default, VS Code, IntelliJ IDEA presets
- **`PresetDefinitions.kt`** - Emacs preset with Ctrl-based shortcuts

*UI Components* (composeApp/src/commonMain/kotlin/ai/rever/boss/components/settings/keymap/):
- **`EditableKeymapSettings.kt`** - Main settings UI with search/filter
- **`KeyCaptureDialog.kt`** - Modal for capturing key presses
- **`ConflictWarningBadge.kt`** - Visual conflict indicators
- **`PresetSelector.kt`** - Preset switcher with customization badges
- **`KeymapImportExport.kt`** - JSON import/export dialogs

*Settings Manager* (composeApp/src/commonMain/kotlin/ai/rever/boss/keymap/):
- **`KeymapSettingsManager.kt`** - Expect/actual pattern for platform-specific persistence
- Desktop implementation saves to `~/.boss/keymap-settings.json`
- Reactive StateFlow for settings updates

**Preset Keymaps:**

1. **BOSS Default** - Browser-style with Cmd-based bindings
   - Cmd+N: New window, Cmd+W: Close tab, Cmd+T: New browser tab

2. **VS Code** - Visual Studio Code inspired
   - Cmd+P: Quick file switcher, Cmd+Shift+E: Project explorer, Cmd+Alt+Arrow: Split navigation

3. **IntelliJ IDEA** - JetBrains IDE inspired
   - Cmd+E: Recent files, Cmd+1: Project window, Cmd+Alt+Arrow: Navigate splits

4. **Emacs** - Ctrl-based shortcuts
   - Ctrl+F: New file, Ctrl+K: Close tab, Alt+X: Quick switcher

**Integration:**
- Settings accessible via Settings > Keyboard Shortcuts
- Shortcuts handled in `BossApp.kt` via `KeymapHandler`
- Context detection based on active tab type (browser/terminal/editor)
- Platform-aware display (⌘ on macOS, Ctrl on Windows/Linux)

**JSON Format:**
```json
{
  "shortcuts": {
    "window.new": {
      "actionId": "window.new",
      "key": "N",
      "modifiers": ["Cmd"],
      "context": "GLOBAL",
      "category": "Window Management",
      "description": "Open New Window",
      "enabled": true
    }
  },
  "presetName": "BOSS Default",
  "customized": false,
  "version": 1
}
```

## Code Quality

### Static Analysis
The project uses **detekt** (CLI version) for Kotlin static code analysis:

```bash
# Run detekt analysis
detekt --input composeApp/src --report txt:detekt-report.txt --report html:detekt-report.html

# Note: detekt is not integrated into Gradle - use CLI directly
```

**Common Acceptable Patterns:**
- **WildcardImport**: Acceptable for Compose UI imports (`androidx.compose.material.*`)
- **MagicNumber**: Acceptable for UI dimensions (`8.dp`, `16.sp`) and common values (0, 1, 2)
- **SwallowedException**: Acceptable when returning fallback values (parsing, file ops)

### Resource Management
Uses **Compose Multiplatform Resource API** (not Android resources):

- **Resources location**: `composeApp/src/commonMain/composeResources/`
- **Generated package**: `boss_kotlin.composeapp.generated.resources`
- **Import pattern**:
  ```kotlin
  import org.jetbrains.compose.resources.painterResource
  import boss_kotlin.composeapp.generated.resources.Res
  import boss_kotlin.composeapp.generated.resources.your_resource
  ```
- **Do NOT use**: `androidx.compose.ui.res.painterResource` (deprecated)

### Code Style
- All Kotlin files must end with a newline
- Remove `printStackTrace()` calls - use `println()` for error logging
- Prefer explicit imports over wildcards (except for Compose UI)

### Threading and Coroutines

**CRITICAL**: Proper threading is essential for responsive UI and preventing freezes. This section documents threading best practices learned from production issues.

#### UI Thread Rules

**NEVER do these on the UI thread:**
- ❌ `Thread.sleep()` - Blocks UI thread, causes freezes
- ❌ Blocking I/O operations (file read/write, network calls)
- ❌ Long computations (>16ms drops frames, >100ms feels laggy)
- ❌ Resource cleanup that takes time (browser disposal, database cleanup)

**ONLY do these on the UI thread:**
- ✅ Quick UI updates and state changes
- ✅ Composable recomposition
- ✅ Layout and drawing operations
- ✅ Fast synchronous operations (<16ms)

#### Dispatcher Usage Guide

**`Dispatchers.Main`** - UI operations only:
- UI state updates
- Composable recomposition
- Quick operations (<16ms)
- Launching coroutines that switch to background threads

**`Dispatchers.IO`** - I/O-bound operations:
- File operations (read/write)
- Network calls and HTTP requests
- Database queries
- **Browser cleanup and disposal**
- Resource cleanup with delays

**`Dispatchers.Default`** - CPU-bound operations:
- Heavy computations
- Data processing and transformations
- Parsing large data structures
- Image processing

#### Common Patterns

**✅ CORRECT - Background Disposal:**
```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        // Dispose on background thread to avoid blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                browserViewState?.let { disposeBrowserViewState(it) }

                // Non-blocking coroutine delay
                delay(50)  // Allow RPC queue to drain

                browser?.let { disposeBrowser(it) }
            } catch (e: Exception) {
                println("Error disposing browser: ${e.message}")
            }
        }
    }
}
```

**❌ INCORRECT - Blocking UI Thread:**
```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        try {
            browserViewState?.let { disposeBrowserViewState(it) }

            // ❌ BLOCKS UI THREAD - causes 50ms freeze!
            Thread.sleep(50)

            browser?.let { disposeBrowser(it) }
        } catch (e: Exception) {
            println("Error disposing browser: ${e.message}")
        }
    }
}
```

#### Disposal and Cleanup Best Practices

1. **Use coroutines for resource cleanup:**
   ```kotlin
   CoroutineScope(Dispatchers.IO).launch {
       // Heavy cleanup work here
   }
   ```

2. **Use `delay()` instead of `Thread.sleep()`:**
   ```kotlin
   delay(50)              // ✅ Non-blocking
   Thread.sleep(50)       // ❌ Blocks thread
   ```

3. **Handle exceptions in cleanup:**
   ```kotlin
   try {
       cleanup()
   } catch (e: Exception) {
       println("Cleanup error: ${e.message}")
   }
   ```

4. **Document why delays are needed:**
   ```kotlin
   // Wait 50ms for JxBrowser's RPC queue to drain
   // This prevents race condition in SharedMemoryTransport
   delay(50)
   ```

5. **Don't wait for async cleanup to complete:**
   - `dispose()` should return immediately
   - Let cleanup happen in background
   - UI stays responsive

#### JxBrowser-Specific Patterns

JxBrowser requires careful threading due to internal RPC (Remote Procedure Call) architecture:

**Browser Disposal Pattern:**
```kotlin
CoroutineScope(Dispatchers.IO).launch {
    // Dispose BrowserViewState first
    browserViewState?.let { disposeBrowserViewState(it) }

    // Wait for RPC message queue to drain
    // Without this, browser.close() tears down RPC while messages are pending
    delay(50)

    // Now safe to close browser
    browser?.let { disposeBrowser(it) }
}
```

**Why the delay?**
- `browser.close()` immediately tears down RPC connections
- JxBrowser's `SharedMemoryTransport` may have queued RPC messages
- If RPC observer becomes null before messages process → NullPointerException
- 50ms delay allows queue to drain gracefully

**Reference:** See `Fluck.kt:336-357` for complete implementation

#### Real-World Case Study: Fluck.kt Browser Disposal

**Problem (commit 31c6ea3):**
```kotlin
fun dispose() {
    browserViewState?.let { disposeBrowserViewState(it) }
    Thread.sleep(50)  // ❌ BLOCKED UI THREAD
    browser?.let { disposeBrowser(it) }
}
```

**Impact:**
- 50ms UI freeze every time a tab closed
- Multiple rapid tab closures = multiple 50ms freezes
- Poor user experience, especially on slower systems
- User perception: "App feels sluggish"

**Solution (commit 40bf0b2):**
```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        CoroutineScope(Dispatchers.IO).launch {  // ✅ Background thread
            try {
                browserViewState?.let { disposeBrowserViewState(it) }
                delay(50)  // ✅ Non-blocking coroutine delay
                browser?.let { disposeBrowser(it) }
            } catch (e: Exception) {
                println("Error disposing browser: ${e.message}")
            }
        }
    }
}
```

**Results:**
- ✅ UI thread never blocks during tab closure
- ✅ `dispose()` returns immediately (microseconds)
- ✅ Browser cleanup happens asynchronously
- ✅ Smooth, responsive UI even when closing many tabs

**Lesson:** Always profile UI responsiveness when adding cleanup code.

#### Testing for Threading Issues

**Manual Testing:**
1. Close multiple tabs rapidly - should be instantaneous, no lag
2. Monitor UI responsiveness during heavy operations
3. Watch for frame drops or stuttering
4. Test on slower hardware if possible

**Code Review Checklist:**
- [ ] No `Thread.sleep()` calls in UI-accessible code
- [ ] No blocking I/O on main thread
- [ ] Resource cleanup uses `Dispatchers.IO`
- [ ] Delays use `delay()` not `Thread.sleep()`
- [ ] Long operations happen in background coroutines

**Search Patterns:**
```bash
# Find potential threading issues
git grep "Thread.sleep"        # Should be rare/zero
git grep "\.sleep("            # Catch variations
git grep "blockingGet"         # Blocking calls
```

#### IntelliJ IDEA Inspections

Enable these inspections to catch threading issues:

1. **"Inappropriate blocking method call"**
   - Detects blocking calls on coroutine dispatchers
   - Catches `Thread.sleep()` in suspend functions

2. **"Possibly blocking call in non-blocking context"**
   - Warns about blocking I/O in coroutines
   - Suggests `Dispatchers.IO` for I/O operations

3. **"Slow operations on UI thread"** (Android)
   - While this is for Android, the principle applies
   - Watch for file I/O, network, and long computations

#### Quick Reference

| Operation | Dispatcher | Pattern |
|-----------|-----------|---------|
| UI update | `Dispatchers.Main` | Direct call or `withContext` |
| File I/O | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Network | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Database | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Browser cleanup | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Heavy compute | `Dispatchers.Default` | `CoroutineScope(Dispatchers.Default).launch {}` |
| Delay | Any | `delay(ms)` never `Thread.sleep(ms)` |

**Remember:** When in doubt, move work off the UI thread. It's easier to optimize later than to debug UI freezes.

## Development Notes

### Current Focus Areas
- **RBAC (Role-Based Access Control)** - Dynamic role and permission management
- **Cross-device authentication flows**
- **GitHub Actions CI/CD improvements**

### Resolved Issues
- ✅ Issue #75: Passkey refresh token bug - Users were logged out after 1 hour (Fixed in PR #78)

### Known Issues
- Issue #33: Remove hardcoded credential fallbacks after testing
- Issue #34: Use JxBrowser for login instead of system browser

### Testing Status
**Limited test coverage** - focus on build verification rather than unit/integration tests. Future development should prioritize comprehensive testing of authentication flows.

### Key Files to Understand

**Client-side (Kotlin):**
- `AuthService.kt` - Core authentication orchestration
- `SessionManager.kt` - Session establishment and persistence
- `DesktopPasskeyService.kt` - Desktop WebAuthn implementation
- `SupabaseConfig.kt` - Backend configuration and client management
- `RoleService.kt` - RBAC role management
- `LoadingScreen.kt` - Centralized loading screen component (uses new resource API)

**Server-side (Edge Functions):**
- `supabase/functions/passkey/services/auth.ts` - Passkey authentication flow
- `supabase/functions/passkey/utils/jwt.ts` - Session token generation (Admin API)
- `supabase/functions/passkey/utils/crypto.ts` - WebAuthn signature verification

**Build & Config:**
- `version.properties` - Single source of truth for versioning
- `build.gradle.kts` files - Kotlin Multiplatform configuration

## Deep Link Support

The app registers `boss://` protocol for deep link handling, primarily for authentication callback flows from external browsers or mobile devices.

## Default Browser Support

BOSS Console can be set as the default system browser to handle http:// and https:// URLs. When set as default, clicking web links in other applications will open them in BOSS's Fluck browser.

### Platform Behavior

**macOS:**
- Automatic registration via Info.plist CFBundleURLTypes (http, https schemes)
- Programmatic setting via Swift scripts using LSSetDefaultHandlerForURLScheme
- Falls back to opening System Preferences if programmatic setting fails
- User can verify/change in System Preferences > General > Default web browser

**Windows:**
- Registry keys created in `HKEY_CURRENT_USER\SOFTWARE\Clients\StartMenuInternet\BOSS`
- Includes Capabilities, URLAssociations for http/https, and file type associations
- Windows 10+ requires user to manually select default in Settings (Microsoft security restriction)
- App automatically opens Windows Settings (ms-settings:defaultapps) for user selection
- Cannot be set programmatically due to hash algorithm protection

**Linux:**
- Creates `~/.local/share/applications/boss.desktop` file with proper MIME types
- Uses xdg-settings and xdg-mime for registration
- Supports x-scheme-handler/http, x-scheme-handler/https, and text/html MIME types
- May require desktop session restart for changes to take effect
- Compatible with GNOME, KDE, XFCE, and other XDG-compliant desktop environments

### Implementation

**Key Files:**
- `DefaultBrowserManager.kt` (common) - Cross-platform interface (80 lines)
- `DefaultBrowserManager.kt` (desktop) - Desktop platform dispatcher (100 lines)
- `MacOSDefaultBrowserHandler.kt` - macOS implementation (200 lines)
- `WindowsDefaultBrowserHandler.kt` - Windows implementation (250 lines)
- `LinuxDefaultBrowserHandler.kt` - Linux implementation (220 lines)
- `URLHandlerService.kt` - Handles incoming http/https URLs (120 lines)
- `DefaultBrowserSection.kt` - Settings UI component (220 lines)
- `ProfileManagementSection.kt` - Extracted profile management (190 lines)
- `boss.desktop.template` - Linux .desktop file template (30 lines)

**URL Handling Flow:**
1. User clicks http/https link in external application
2. OS passes URL to BOSS via registered protocol handler
3. `DeepLinkHandler` receives URL and checks protocol
4. If http/https: forwards to `URLHandlerService`
5. If boss://: processes as authentication deep link
6. `URLHandlerService` validates URL and extracts domain
7. Creates new Fluck browser tab with URL
8. Tab displayed in active window (or creates new window if none exist)

**File Size Management:**
All implementation files kept under 300 lines for maintainability:
- `FluckBrowserSettings.kt` reduced from 420 to 229 lines
- Profile management extracted to separate component
- Platform handlers separated by OS

### Settings UI

**Location:** Settings > Fluck Browser > Default Browser

**Features:**
- Real-time status indicator (✓ Default / × Not Default)
- "Set as Default Browser" button with loading states
- Automatic status refresh
- Platform-specific instructions and messaging
- Error handling with user-friendly messages
- Success/instructions dialogs based on platform

**Status Display:**
- Green checkmark if BOSS is default
- Gray X if BOSS is not default
- Loading spinner while checking/setting
- Error icon with message if operation fails

**Platform-Specific Behavior:**
- macOS: Shows "Set as Default Browser" button, attempts automatic setting
- Windows: Shows warning that Settings will open, guides user through manual selection
- Linux: Shows XDG information, sets automatically via xdg-settings

### URL Validation

`URLHandlerService` validates incoming URLs for security:
- Only accepts http:// and https:// protocols
- Rejects malformed URLs (missing domain, invalid format)
- Extracts domain name for tab title display
- Removes "www." prefix for cleaner titles
- Example: "https://www.github.com/user/repo" → tab title "github.com"

### Integration Points

**Deep Link Handler:**
- Extended to handle http/https URLs alongside boss:// deep links
- Routes http/https to URLHandlerService
- Routes boss:// to authentication flow
- Works across all platforms (macOS, Windows, Linux)

**Window Management:**
- URLHandlerService integrates with WindowManager
- Adds tabs to first available window
- Creates new window if no windows exist
- Maintains multi-window support

**Browser Profiles:**
- Default browser setting works independently of browser profiles
- Profile selection affects cookie/cache storage
- Profile changes require application restart