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

**Database Schema** (Supabase):
- `user_passkeys` table with RLS policies
- `passkey_challenges` table for temporary challenge storage
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
```

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

## Version Management System

The project uses **centralized version management**:
- **Source**: `version.properties` file (currently v8.11.4)
- **Generation**: `gradle/version.gradle` generates `VersionConstants.kt`
- **CI Integration**: GitHub Actions automatically increment versions
- **Consistency**: All platforms use same version from single source

## Build and Deployment

### GitHub Actions Workflows
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

## Development Notes

### Current Focus Areas
- **2FA/WebAuthn authentication system** (fix/2FA branch)
- **Cross-device authentication flows**
- **GitHub Actions CI/CD improvements**
- **Code quality improvements** (manual_cleanup branch)

### Known Issues
- Issue #33: Remove hardcoded credential fallbacks after testing
- Issue #34: Use JxBrowser for login instead of system browser

### Testing Status
**Limited test coverage** - focus on build verification rather than unit/integration tests. Future development should prioritize comprehensive testing of authentication flows.

### Key Files to Understand
- `AuthService.kt` - Core authentication orchestration
- `DesktopPasskeyService.kt` - Desktop WebAuthn implementation
- `SupabaseConfig.kt` - Backend configuration and client management
- `version.properties` - Single source of truth for versioning
- `build.gradle.kts` files - Kotlin Multiplatform configuration
- `LoadingScreen.kt` - Centralized loading screen component (uses new resource API)

## Deep Link Support

The app registers `boss://` protocol for deep link handling, primarily for authentication callback flows from external browsers or mobile devices.