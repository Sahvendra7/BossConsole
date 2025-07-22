# 🛡️ Branch Protection Setup Guide

This guide helps you set up comprehensive branch protection rules for the BOSS repository.

## 🎯 Recommended Branch Protection Rules

### For `main` branch:

1. **Go to**: Repository → Settings → Branches → Add rule

2. **Branch name pattern**: `main`

3. **Protection Settings**:

#### ✅ **Restrict pushes that create files larger than 100 MB**
- Prevents accidental commits of large build artifacts

#### ✅ **Require a pull request before merging**
- **Required approvals**: 1 (minimum)
- **Dismiss stale PR approvals when new commits are pushed**: ✓
- **Require review from code owners**: ✓ (if CODEOWNERS file exists)
- **Restrict reviews to users with write access**: ✓
- **Allow specified actors to bypass required pull requests**: 
  - Add service accounts if needed

#### ✅ **Require status checks to pass before merging**
- **Require branches to be up to date before merging**: ✓
- **Status checks that are required**:
  - `build-test (ubuntu-latest)`
  - `build-test (macos-latest)`  
  - `build-test (windows-latest)`
  - `version-check`
  - `code-quality`
  - `compatibility-test`

#### ✅ **Restrict pushes that create files larger than 100 MB**
- Prevents large file commits

#### ✅ **Allow force pushes** (Optional)
- **Everyone**: ❌ (Recommended: OFF)
- **Specify who can force push**: Only repository admins

#### ✅ **Allow deletions** (Optional)
- ❌ (Recommended: OFF to prevent accidental deletion)

#### ✅ **Do not allow bypassing the above settings**
- ❌ (Keep restrictions even for admins)

### For `develop` branch:

1. **Branch name pattern**: `develop`

2. **Protection Settings** (Lighter than main):
   - ✅ Require a pull request before merging (0 approvals for development)
   - ✅ Require status checks to pass before merging
   - ✅ Allow force pushes for development team

## 📋 CODEOWNERS File

Create `.github/CODEOWNERS` file for automatic review assignments:

```bash
# Global owners
* @kshivang

# Version management
version.properties @kshivang
gradle/version.gradle @kshivang

# Build scripts
build-dmg.sh @kshivang
build-msi.bat @kshivang
*.gradle* @kshivang

# CI/CD workflows
.github/workflows/ @kshivang

# Core application
composeApp/src/commonMain/kotlin/ai/rever/boss/ @kshivang

# Platform-specific code
composeApp/src/desktopMain/ @kshivang
composeApp/src/androidMain/ @kshivang
composeApp/src/iosMain/ @kshivang

# Documentation
*.md @kshivang
```

## 🤖 PR Template

Create `.github/pull_request_template.md`:

```markdown
## 📋 Description
Brief description of changes made.

## 🔄 Type of Change
- [ ] 🐛 Bug fix (non-breaking change that fixes an issue)
- [ ] ✨ New feature (non-breaking change that adds functionality)  
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📚 Documentation update
- [ ] 🔧 Configuration change
- [ ] 🧹 Code cleanup/refactoring

## ✅ Testing Checklist
- [ ] I have tested this change locally
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] I have checked the build works on all platforms

## 📦 Version Impact
- [ ] This change requires version increment:
  - [ ] Patch (bug fix)
  - [ ] Minor (new feature)
  - [ ] Major (breaking change)

## 📱 Platform Testing
- [ ] 🍎 macOS tested
- [ ] 🪟 Windows tested  
- [ ] 🐧 Linux tested
- [ ] 📱 Mobile platforms (if applicable)

## 📸 Screenshots (if applicable)
Add screenshots or screen recordings of UI changes.

## 📝 Additional Notes
Any additional context, concerns, or considerations for reviewers.
```

## 🔒 Repository Security Settings

### 1. **General Security**
Go to: Settings → Security & analysis

Enable:
- ✅ **Dependency graph**
- ✅ **Dependabot alerts**
- ✅ **Dependabot security updates**
- ✅ **Secret scanning**
- ✅ **Push protection** (for secrets)

### 2. **Actions Security**
Go to: Settings → Actions → General

Configure:
- **Actions permissions**: Allow all actions and reusable workflows
- **Fork pull request workflows**: Require approval for first-time contributors
- **Workflow permissions**: Read repository contents and metadata (default)
- **Allow GitHub Actions to create and approve pull requests**: ✅

### 3. **Secrets Management**
Go to: Settings → Secrets and variables → Actions

Add these repository secrets for enhanced functionality:

#### 🍎 **macOS Code Signing** (Optional)
```bash
MACOS_CERTIFICATE_BASE64=<base64-encoded-certificate>
MACOS_CERTIFICATE_PASSWORD=<certificate-password>
MACOS_KEYCHAIN_PASSWORD=<keychain-password>
```

#### 🪟 **Windows Code Signing** (Optional)
```bash
WINDOWS_CERTIFICATE_BASE64=<base64-encoded-certificate>  
WINDOWS_CERTIFICATE_PASSWORD=<certificate-password>
```

#### 📡 **Release Automation**
```bash
PUBLIC_REPO_TOKEN=<GitHub token for public releases>
SLACK_WEBHOOK_URL=<Slack webhook for notifications>
DISCORD_WEBHOOK_URL=<Discord webhook for notifications>
```

## 🎯 Implementation Priority

1. **High Priority** (Do First):
   - ✅ Branch protection for main
   - ✅ Required status checks
   - ✅ PR requirements
   - ✅ Security settings

2. **Medium Priority**:
   - ✅ CODEOWNERS file
   - ✅ PR template
   - ✅ Code signing certificates

3. **Low Priority** (Nice to Have):
   - ✅ Notification webhooks
   - ✅ Advanced security scanning
   - ✅ Custom branch policies

## 🚀 Quick Setup Script

Run these commands to create the protection files:

```bash
# Create CODEOWNERS
mkdir -p .github
cat > .github/CODEOWNERS << 'EOF'
# Global owners - replace with your GitHub username
* @kshivang

# Version management
version.properties @kshivang
gradle/version.gradle @kshivang

# Build and CI/CD
*.gradle* @kshivang
.github/workflows/ @kshivang
build-dmg.sh @kshivang
build-msi.bat @kshivang
EOF

# Create PR template
cat > .github/pull_request_template.md << 'EOF'
## 📋 Description
Brief description of changes made.

## 🔄 Type of Change
- [ ] 🐛 Bug fix
- [ ] ✨ New feature  
- [ ] 💥 Breaking change
- [ ] 📚 Documentation update

## ✅ Testing Checklist
- [ ] I have tested this change locally
- [ ] New and existing tests pass
- [ ] Build works on all platforms

## 📦 Version Impact
- [ ] Requires version increment: [ ] patch [ ] minor [ ] major
EOF

echo "✅ Branch protection files created!"
echo "Next: Go to GitHub repository settings to configure branch protection rules"
```

This setup provides enterprise-grade protection and automation for your repository!