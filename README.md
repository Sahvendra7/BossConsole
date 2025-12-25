This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop, Server.

# Installation

## macOS

### Homebrew (Recommended)
```bash
brew install --cask boss
```

### Manual Installation
1. Download the latest `BOSS-x.x.x-Universal.dmg` from [Releases](https://github.com/risa-labs-inc/BOSS-Releases/releases)
2. Open the DMG and drag BOSS to Applications
3. Launch BOSS from Applications

## Windows

### MSI Installer
1. Download the latest `BOSS-x.x.x.msi` from [Releases](https://github.com/risa-labs-inc/BOSS-Releases/releases)
2. Run the installer and follow the prompts
3. Launch BOSS from the Start Menu

## Linux

### Debian/Ubuntu (DEB)
```bash
# Download and install the latest DEB package (amd64)
curl -sL $(curl -s https://api.github.com/repos/risa-labs-inc/BOSS-Releases/releases/latest | grep "browser_download_url.*amd64.deb" | cut -d '"' -f 4) -o boss.deb
sudo dpkg -i boss.deb
sudo apt-get install -f  # Fix dependencies if needed

# For ARM64:
curl -sL $(curl -s https://api.github.com/repos/risa-labs-inc/BOSS-Releases/releases/latest | grep "browser_download_url.*arm64.deb" | cut -d '"' -f 4) -o boss.deb
sudo dpkg -i boss.deb
sudo apt-get install -f
```

Or download manually from the [Releases page](https://github.com/risa-labs-inc/BOSS-Releases/releases/latest).

### Fedora/RHEL (RPM)
```bash
# Download and install the latest RPM package (x86_64)
curl -sL $(curl -s https://api.github.com/repos/risa-labs-inc/BOSS-Releases/releases/latest | grep "browser_download_url.*x86_64.rpm" | cut -d '"' -f 4) -o boss.rpm
sudo dnf install -y ./boss.rpm  # Or: sudo rpm -i boss.rpm

# For ARM64 (aarch64):
curl -sL $(curl -s https://api.github.com/repos/risa-labs-inc/BOSS-Releases/releases/latest | grep "browser_download_url.*aarch64.rpm" | cut -d '"' -f 4) -o boss.rpm
sudo dnf install -y ./boss.rpm
```

Or download manually from the [Releases page](https://github.com/risa-labs-inc/BOSS-Releases/releases/latest).

### JAR (Universal)
For distributions without DEB/RPM support or custom installations:
```bash
# Requires Java 17+
curl -sL $(curl -s https://api.github.com/repos/risa-labs-inc/BOSS-Releases/releases/latest | grep "browser_download_url.*\.jar" | head -1 | cut -d '"' -f 4) -o boss.jar
java -jar boss.jar
```

## Auto-Updates

BOSS includes automatic update support for all platforms:
- **macOS**: DMG downloads and installs automatically
- **Windows**: MSI installs silently after app quit
- **Linux**: DEB/RPM packages install with graphical sudo prompt (pkexec)

Updates are checked periodically (default: every 6 hours) and can be configured in Settings.

---

# Project Structure

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that's common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you're sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* `/server` is for the Ktor server application.

* `/shared` is for the code that will be shared between all targets in the project.
  The most important subfolder is `commonMain`. If preferred, you can add code to the platform-specific folders here too.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [GitHub](https://github.com/JetBrains/compose-multiplatform/issues).

You can open the web application by running the `:composeApp:wasmJsBrowserDevelopmentRun` Gradle task.

# JxBrowser Configuration

## License Key Configuration

The JxBrowser license key can be configured in several ways (in order of precedence):

### 1. Environment Variable (Recommended for Production)
```bash
export JXBROWSER_LICENSE_KEY="your-license-key-here"
```

### 2. System Property
```bash
java -Djxbrowser.license.key="your-license-key-here" -jar your-app.jar
```

### 3. Local Properties File (Recommended for Development)
Add to `local.properties` in the project root:
```properties
jxbrowser.license.key=your-license-key-here
```

**Note:** The `local.properties` file is already in `.gitignore` and should never be committed to version control.

### 4. Gradle Build Configuration
You can also inject the license key at build time through Gradle:

```kotlin
// In build.gradle.kts
tasks.withType<JavaExec> {
    systemProperty("jxbrowser.license.key", findProperty("jxbrowser.license.key") ?: "")
}
```

Then run with:
```bash
./gradlew run -Pjxbrowser.license.key="your-license-key-here"
```

## Security Best Practices

### JxBrowser
1. **Never commit license keys to version control**
2. **Use environment variables or secure vaults in production**
3. **Remove the hardcoded fallback key before deploying to production**
4. **Consider using a secrets management service for enterprise deployments**

### Supabase Configuration
1. **Never commit Supabase keys to version control** - they provide access to your backend
2. **Use environment variables in production** - `SUPABASE_URL` and `SUPABASE_ANON_KEY`
3. **For development**: Add keys to `local.properties` file (already in .gitignore)
4. **Configuration priority**: Environment variables → System properties → local.properties → error
5. **The anonymous key is safe to use in client applications** - it only provides public access

## Other Configuration Options

- `jxbrowser.default.url`: Set the default URL (default: "https://www.risalabs.ai")

## Example local.properties
```properties
# JxBrowser configuration
jxbrowser.license.key=YOUR_LICENSE_KEY_HERE
jxbrowser.default.url=https://www.example.com

# Supabase configuration (for development)
# IMPORTANT: Never commit these values to version control
SUPABASE_URL=https://your-supabase-url.supabase.co
SUPABASE_ANON_KEY=your-supabase-anon-key-here
SUPABASE_FUNCTION_URL=https://your-supabase-url.supabase.co/functions/v1
```

# Version Management

BOSS uses a centralized version management system to maintain consistency across all build artifacts and configurations.

## Version Configuration

All version information is stored in the [`version.properties`](./version.properties) file at the project root:

```properties
# Application Version (Semantic Versioning)
app.version.major=8
app.version.minor=8  
app.version.patch=0
app.version=8.8.0
app.bundle.version=8.8.0
```

## Version Management Commands

Use these Gradle tasks to manage versions:

```bash
# Display current version information
./gradlew showVersion

# Increment patch version (8.8.0 → 8.8.1)
./gradlew incrementVersion

# Increment minor version and reset patch (8.8.0 → 8.9.0)
./gradlew incrementMinor

# Increment major version and reset minor/patch (8.8.0 → 9.0.0)
./gradlew incrementMajor
```

## Automated Version Propagation

The centralized version automatically updates:
- JAR file names (`BOSS-8.8.0-all.jar`)
- DMG file names (`BOSS-8.8.0.dmg`)  
- MSI installer names (`BOSS-8.8.0.msi`)
- Application manifests and metadata
- Build script configurations

## Best Practices

1. **Always use the Gradle tasks** to increment versions - never edit `version.properties` manually
2. **Increment patch** for bug fixes and small improvements
3. **Increment minor** for new features and enhancements  
4. **Increment major** for breaking changes or major releases
5. **Run `./gradlew showVersion`** to verify changes before building distributions

# GitHub Actions Workflows

BOSS includes automated CI/CD workflows for building, testing, and releasing across all platforms.

## 🚀 Available Workflows

### 1. **Release Build** (`.github/workflows/release.yml`)
Automatically builds and publishes releases for all platforms.

**Triggers:**
- Push to tags matching `v*.*.*` (e.g., `v8.8.1`)
- Manual workflow dispatch with version increment options

**Outputs:**
- 🍎 **macOS DMG** (Universal: Apple Silicon + Intel)
- 🪟 **Windows MSI** (x64)
- 🐧 **Linux DEB** (amd64, arm64)
- 🐧 **Linux RPM** (x86_64, aarch64)
- 🐧 **Linux JAR** (Cross-platform fallback)
- 📦 **GitHub Release** with all artifacts

**Manual Release:**
```bash
# Via GitHub Actions UI:
# 1. Go to Actions → Release Build → Run workflow
# 2. Choose: patch/minor/major increment
# 3. Enable "Create GitHub release"
```

### 2. **Build & Test** (`.github/workflows/build.yml`)
Continuous integration for all commits and PRs.

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main`
- Manual workflow dispatch

**Features:**
- ✅ Cross-platform builds (Ubuntu, macOS, Windows)
- 🧪 Automated testing
- 🔍 Code quality checks
- 📦 Version system validation
- 🔄 Auto-update integration testing

### 3. **Version Bump** (`.github/workflows/version-bump.yml`)
Automated version management with our centralized system.

**Manual Trigger Options:**
- **Version Type**: `patch`, `minor`, `major`
- **Commit Message**: Custom message (optional)
- **Create PR**: Option to create PR instead of direct push

**Process:**
1. Increments version using centralized system
2. Generates and validates version constants
3. Tests build with new version
4. Either pushes directly or creates PR
5. Automatically triggers release build

## 🛠️ CI/CD Setup Requirements

### Repository Secrets (Optional)
For enhanced functionality, add these secrets in GitHub repository settings:

```bash
# macOS Code Signing (Optional)
MACOS_CERTIFICATE_BASE64=<base64-encoded-certificate>
CERTIFICATE_PASSWORD=<certificate-password>
KEYCHAIN_PASSWORD=<keychain-password>

# Windows Code Signing (Optional)  
WINDOWS_CERTIFICATE_BASE64=<base64-encoded-certificate>
WINDOWS_CERTIFICATE_PASSWORD=<certificate-password>
```

### Workflow Permissions
Ensure GitHub Actions has these permissions:
- ✅ **Contents**: Read and write (for version commits)
- ✅ **Pull Requests**: Write (for PR creation)
- ✅ **Actions**: Write (for workflow triggers)

## 🎯 Automated Release Process

### Option 1: Tag-Based Release
```bash
# Create and push version tag
./gradlew incrementMinor  # Updates version to 8.9.0
git add version.properties
git commit -m "🔖 Release v8.9.0"
git tag v8.9.0
git push origin main --tags

# → Automatically triggers Release Build workflow
# → Creates GitHub release with all platform builds
```

### Option 2: GitHub Actions UI
1. **Go to**: Actions → Version Bump → Run workflow
2. **Choose**: Version increment type (`patch`/`minor`/`major`)  
3. **Option**: Create PR for review, or push directly
4. **Result**: Version updated → Release build triggered → GitHub release created

### Option 3: Automated Release on PR Merge
```yaml
# In your PR description, include:
# [version:minor] - Triggers minor version bump on merge
# [version:major] - Triggers major version bump on merge  
# Default: patch increment
```

## 📊 Build Status Badges

Add these badges to show build status:

```markdown
![Build Status](https://github.com/your-username/BOSS-Kotlin/actions/workflows/build.yml/badge.svg)
![Release](https://github.com/your-username/BOSS-Kotlin/actions/workflows/release.yml/badge.svg)
```

## 🔍 Monitoring Builds

- **Build Logs**: Available in Actions tab for each workflow run
- **Artifacts**: Download builds from successful workflow runs  
- **Release Notes**: Auto-generated with download links and system requirements
- **Version Tracking**: All builds tagged with centralized version system

The CI/CD system integrates seamlessly with our centralized version management, ensuring consistent versioning across all platforms and deployment methods.

## CLI Installation

BOSS includes a powerful command-line interface for quick access to browser, files, folders, and terminal functionality.

### macOS (Homebrew) - Recommended

The BOSS CLI is automatically installed with the Homebrew Cask:

```bash
brew install --cask boss
# CLI is immediately available
boss --help
```

### macOS/Linux (Manual Installation)

For DMG installations or non-Homebrew users:

1. Open BOSS application
2. Go to **Tools** > **Install BOSS CLI**
3. Follow the installation wizard

The installer will:
- Detect if BOSS is in `/Applications/` (standard DMG installation)
- Create symlink at `/opt/homebrew/bin/boss` → app bundle (macOS)
- Or copy to `~/.local/bin/boss` for custom locations (macOS/Linux)

### Windows

1. Open BOSS application
2. Go to **Tools** > **Install BOSS CLI**
3. Follow the installation wizard
4. Restart terminal to use `boss` command

### CLI Usage

```bash
# Open URLs
boss url https://github.com
boss google.com  # Auto-detects and adds https://

# Open folders
boss folder ~/Documents
boss .  # Open current directory
boss folder  # Open home directory

# Open files
boss file README.md
boss ./path/to/file.txt

# Terminal
boss terminal
boss terminal -c "ls -la"

# Plugin access
boss plugin bookmarks
boss plugin secret-manager

# Help
boss --help
```

### Verifying Installation

```bash
which boss          # Check installation path
boss --version      # Show BOSS version
```

### Installation Paths

**Homebrew/DMG (macOS):**
- Symlink: `/opt/homebrew/bin/boss`
- Target: `/Applications/BOSS.app/Contents/Resources/boss`
- Updates automatically with app

**Manual (macOS/Linux):**
- Copy: `~/.local/bin/boss`
- Requires manual PATH configuration if not already set

**Windows:**
- Copy: `%USERPROFILE%\bin\boss.bat`
- Installer updates PATH automatically 