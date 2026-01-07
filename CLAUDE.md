# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BOSS (Business Operating System Service) is a desktop application built with Kotlin Multiplatform and Compose Multiplatform. It features:
- Sophisticated WebAuthn/passkey authentication
- Integrated browser (JxBrowser) with default browser support
- Terminal integration (BossTerm library)
- Customizable keyboard shortcuts
- Workspace management
- Role-based access control (RBAC)

**Target Platforms**: Desktop only (macOS, Windows, Linux). Mobile targets disabled.

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
```

## Workflow Rules

**IMPORTANT**: Do NOT run `./gradlew run` to test the application. The user will run and test the app themselves and report back the results. Only make code changes and wait for user feedback.

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
- **BossTerm** for terminal integration

## Configuration Management

### Development Setup

Create `local.properties` file:
```properties
# JxBrowser license
jxbrowser.license.key=<your-license-key>

# Supabase configuration
SUPABASE_URL=https://api.risaboss.com
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_FUNCTION_URL=https://api.risaboss.com/functions/v1

# GitHub API token (optional, for development)
# Without: 60 req/hour | With: 5,000 req/hour
# Option 1: `gh auth login` (auto-detected)
# Option 2: Manual token from https://github.com/settings/tokens
GITHUB_TOKEN=ghp_your_token_here
```

**Configuration Priority**:
1. Environment variables (production/CI)
2. System properties
3. `local.properties` file
4. Fallback values

**Important**: The `local.properties` file is gitignored and contains sensitive keys.

### Supabase Deployment

```bash
# Deploy Edge Functions
supabase functions deploy <function-name> --project-ref pcnwqamqdnsadranufjv --no-verify-jwt

# Link to remote project (first time)
supabase link --project-ref pcnwqamqdnsadranufjv
```

**Available Edge Functions**:
- `passkey` - WebAuthn/Passkey authentication endpoints
- `redirect` - HTTP to boss:// deep link conversion

**Supabase Cloud Configuration**:
- Site URL: `boss://auth/verify`
- Redirect URLs: Must include `boss://auth/verify`
- Email Template: Use `supabase/templates/email/magic-link.html`

### Database Migrations

**Golden Rule**: Only commit migrations that work correctly. Test locally first!

```bash
# Create migration
supabase db diff -f my_feature_name

# Test locally
supabase db reset --linked

# If migration has bugs: DELETE the file, fix SQL, test again
# One feature = one clean migration file
```

**Schema Source of Truth**: `supabase/migrations/` directory

**Database Structure**:
- 13 core tables (auth, RBAC, secrets management)
- 42 database functions (RBAC, secrets, encryption, passkeys)
- 50 RLS policies for access control
- All sensitive data encrypted with AES + base64

## Key Subsystems

### Authentication System

**WebAuthn/Passkey Implementation**:
- Local biometric: Touch ID (macOS), Windows Hello (Windows)
- Cross-device: QR code generation for mobile/browser authentication
- Session management via Supabase Admin API

**Key Files**:
- `AuthService.kt` - Core authentication orchestration
- `SessionManager.kt` - Session establishment and persistence
- `DesktopPasskeyService.kt` - Desktop WebAuthn implementation
- `SupabasePasskeyService.kt` - Server-side passkey management
- `supabase/functions/passkey/` - Edge Functions for auth

**Platform-Specific**:
- macOS: Swift scripts for Touch ID, Keychain Services
- Windows: PowerShell scripts for Windows Hello, Credential Manager

### UI Architecture

**Compose Multiplatform Structure**:
- **BossAppWithAuth** - Main authentication wrapper
- **BossApp** - Main application composable
- **LoginScreen** - Handles login with passkey integration
- **Component-based UI** using Decompose navigation
- **Dark theme** with Material Design components

**Top Bar Features** (`BossTopBar.kt`):
- Project Selector with recent history
- Workspace Management (save/load layouts)
- User Display (email)
- Sign Out, Settings

**Disabled Features** (commented out with tracking issues):
- Git Integration (#90)
- Global Search (#92)
- Lanager Plugin (#93)

**Implemented Features**:
- Run/Debug Controls (#347) - Runner terminal system with run/stop/re-run

### Keyboard Shortcuts System

**Overview**: Comprehensive, customizable shortcuts with context-aware bindings, preset keymaps, and conflict detection.

**Key Components**:
- `KeymapSettingsManager.kt` - Settings persistence (`~/.boss/keymap-settings.json`)
- `GlobalKeyboardInterceptor.kt` - AWT-level interception
- `KeyboardEventBus` - Priority-based event distribution
- `BossActionHandler` - Action execution

**Contexts**: GLOBAL, BROWSER, TERMINAL, EDITOR, WORKSPACE

**Presets**: BOSS Default, VS Code, IntelliJ IDEA, Emacs

**For detailed documentation**: See [docs/KEYBOARD_SHORTCUTS.md](docs/KEYBOARD_SHORTCUTS.md)

### Threading and Coroutines

**CRITICAL RULES**:
1. Never block the UI thread - No `Thread.sleep()`, blocking I/O, or long computations
2. Use appropriate dispatchers:
   - `Dispatchers.Main` for UI updates
   - `Dispatchers.IO` for file/network/database/browser cleanup
   - `Dispatchers.Default` for CPU-bound work
3. Use `delay()` not `Thread.sleep()`
4. Always dispose resources on background threads

**Common Pattern** (Browser/Resource Disposal):
```kotlin
CoroutineScope(Dispatchers.IO).launch {
    try {
        // Dispose resources
        delay(50)  // Allow queues to drain
        // Final cleanup
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
```

**For detailed patterns and examples**: See [docs/THREADING.md](docs/THREADING.md)

### Version Management

**Centralized version management**:
- Source: `version.properties` file (currently v8.11.4)
- Generation: `gradle/version.gradle` generates `VersionConstants.kt`
- CI Integration: GitHub Actions automatically increment versions
- All platforms use same version from single source

### Default Browser Support

**Overview**: BOSS can be set as the default system browser to handle http:// and https:// URLs.

**Key Files**:
- `DefaultBrowserManager.kt` (common/desktop) - Cross-platform interface
- `MacOSDefaultBrowserHandler.kt` - macOS implementation
- `WindowsDefaultBrowserHandler.kt` - Windows implementation
- `LinuxDefaultBrowserHandler.kt` - Linux implementation
- `URLHandlerService.kt` - Handles incoming http/https URLs
- `DefaultBrowserSection.kt` - Settings UI

**Platform Behavior**:
- macOS: Automatic registration via Info.plist, Swift scripts
- Windows: Registry keys, user must manually select in Settings
- Linux: .desktop file with xdg-settings/xdg-mime

**URL Handling Flow**:
1. OS passes URL to BOSS via protocol handler
2. `DeepLinkHandler` checks protocol
3. If http/https: forwards to `URLHandlerService`
4. If boss://: processes as authentication deep link
5. Creates new browser tab in active window

### Runner Terminal System

**Overview**: Run configurations execute in terminal with run/stop/re-run controls (Issue #347).

**Key Components**:
- `RunnerTerminalService.kt` - Manages runner terminals with state tracking
- `RunnerSettingsManager.kt` - Persists runner settings (`~/.boss/runner-settings.json`)
- `RunnerTerminalEventBus.kt` - Events for opening/closing runner terminals
- `DesktopTerminalContent.kt` - Sidebar terminal with persistent state

**Terminal Targets** (configurable in Settings > Runner):
- **Sidebar Panel**: Opens in left sidebar terminal (like VS Code)
- **Main Panel**: Opens in main content area (like IntelliJ IDEA)

**Features**:
- **Run**: Execute selected configuration in terminal
- **Stop**: Send Ctrl+C (0x03) to interrupt running process (BossTerm 1.0.58+)
- **Re-run**: Stop current process, close terminal, create new one with same command

**Sidebar Terminal Integration**:
- Uses `TabbedTerminalStateRegistry` with `SIDEBAR_TERMINAL_ID` for persistent state
- `settingsOverride` with `alwaysShowTabBar = true` ensures tab bar visibility (BossTerm 1.0.59+)
- Commands sent via `sendInput()` API even before panel renders

**Key Files**:
- `DesktopRunnerTerminalService.kt` - Desktop implementation with Ctrl+C support
- `RunnerSettings.kt` (UI) - Settings UI in Settings > Runner section
- `BossTopRunBar.kt` - Run/Stop buttons in top bar

## Code Quality

### Static Analysis

```bash
detekt --input composeApp/src --report txt:detekt-report.txt --report html:detekt-report.html
```

**Common Acceptable Patterns**:
- WildcardImport for Compose UI imports
- MagicNumber for UI dimensions (8.dp, 16.sp) and common values
- SwallowedException when returning fallback values

### Resource Management

**Compose Multiplatform Resource API** (NOT Android resources):
- Location: `composeApp/src/commonMain/composeResources/`
- Generated package: `boss_kotlin.composeapp.generated.resources`
- Do NOT use: `androidx.compose.ui.res.painterResource` (deprecated)

### Code Style

- All Kotlin files must end with a newline
- Remove `printStackTrace()` calls - use `println()` for error logging
- Prefer explicit imports over wildcards (except for Compose UI)

## Build and Deployment

### GitHub Actions Workflows
- **`build.yml`** - Multi-platform testing (Ubuntu, macOS, Windows)
- **`release.yml`** - Production builds with code signing

### Code Signing
- macOS: P12 certificates, notarization via Apple Developer ID
- Windows: DigiCert KeyLocker integration
- Artifacts: DMG, MSI, DEB, RPM, JAR

### GitHub Secrets Required
- `JXBROWSER_LICENSE_KEY` - JxBrowser license
- `SUPABASE_ANON_KEY` - Supabase backend access
- Code signing certificates for macOS/Windows

## Development Notes

### Current Focus Areas
- RBAC (Role-Based Access Control) - Dynamic role and permission management
- Cross-device authentication flows
- GitHub Actions CI/CD improvements

### Resolved Issues
- ✅ Issue #75: Passkey refresh token bug (Fixed in PR #78)
- ✅ Issue #445: AI Assistant context menu moved to BossTerm library (PR #456)

### Known Issues
- Issue #33: Remove hardcoded credential fallbacks after testing
- Issue #34: Use JxBrowser for login instead of system browser

### Testing Status

**Limited test coverage** - focus on build verification rather than unit/integration tests. Future development should prioritize comprehensive testing of authentication flows.

### Key Files to Understand

**Client-side (Kotlin)**:
- `AuthService.kt` - Core authentication orchestration
- `SessionManager.kt` - Session establishment and persistence
- `DesktopPasskeyService.kt` - Desktop WebAuthn implementation
- `SupabaseConfig.kt` - Backend configuration
- `RoleService.kt` - RBAC role management
- `LoadingScreen.kt` - Centralized loading screen

**Server-side (Edge Functions)**:
- `supabase/functions/passkey/services/auth.ts` - Passkey authentication flow
- `supabase/functions/passkey/utils/jwt.ts` - Session token generation
- `supabase/functions/passkey/utils/crypto.ts` - WebAuthn signature verification

**Build & Config**:
- `version.properties` - Single source of truth for versioning
- `build.gradle.kts` files - Kotlin Multiplatform configuration

## Deep Link Support

The app registers `boss://` protocol for deep link handling, primarily for authentication callback flows from external browsers or mobile devices.

## External Dependencies

### BossTerm Library
BOSS uses [BossTerm](https://github.com/kshivang/BossTerm) for terminal integration.

**IMPORTANT**: Do NOT modify the BossTerm repository directly. Instead:
1. Create a GitHub issue using `gh issue create --repo kshivang/BossTerm`
2. Or create a PR for the issue using `gh pr create --repo kshivang/BossTerm`

This ensures proper tracking and review of changes to the shared library.

**BossTerm Features Used**:
- `TabbedTerminal` - Multi-tab terminal with splits for sidebar panel
- `EmbeddableTerminal` - Single terminal instance for embedded use
- `TabbedTerminalState` / `EmbeddableTerminalState` - State persistence across composition changes
- `OnboardingWizard` - First-launch welcome wizard for terminal setup
- `SettingsManager` - Terminal settings (stored in `~/.bossterm/settings.json`)

**AI Assistant Integration** (Issue #445):
- AI coding assistant context menu (Claude Code, GitHub Copilot, Cursor, etc.) is handled by BossTerm
- BOSS Console passes `onShowWelcomeWizard` callback to add "Welcome Wizard..." to context menu
- First-launch detection: checks `settings.onboardingCompleted` and auto-shows wizard
- Help menu also has "Welcome Wizard..." option for manual access

**Key Terminal Files**:
- `DesktopTerminalContent.kt` - Desktop terminal implementations with Welcome Wizard integration
- `Terminal.kt` - Common terminal panel component (expect/actual pattern)

## Additional Documentation

- [Keyboard Shortcuts Reference](docs/KEYBOARD_SHORTCUTS.md) - Detailed shortcuts system documentation
- [Threading Best Practices](docs/THREADING.md) - Threading patterns and common pitfalls
