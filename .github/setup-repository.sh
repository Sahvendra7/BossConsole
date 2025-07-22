#!/bin/bash

# 🚀 BOSS Repository Setup Script
# This script helps set up the repository with all production-ready configurations

set -e  # Exit on error

echo "🚀 Setting up BOSS repository for production..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the right directory
if [ ! -f "version.properties" ] || [ ! -f "build.gradle.kts" ]; then
    echo -e "${RED}❌ Error: This script must be run from the BOSS repository root${NC}"
    echo "Expected files: version.properties, build.gradle.kts"
    exit 1
fi

echo -e "${BLUE}📋 Repository Setup Checklist${NC}"
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to prompt for yes/no
confirm() {
    while true; do
        read -p "$1 (y/n): " yn
        case $yn in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

# 1. Verify Git configuration
echo -e "${BLUE}1. Git Configuration${NC}"
if git config --get user.name > /dev/null && git config --get user.email > /dev/null; then
    echo -e "   ✅ Git user configured: $(git config --get user.name) <$(git config --get user.email)>"
else
    echo -e "   ${YELLOW}⚠️  Git user not configured${NC}"
    if confirm "   Configure Git user now?"; then
        read -p "   Enter your name: " git_name
        read -p "   Enter your email: " git_email
        git config --global user.name "$git_name"
        git config --global user.email "$git_email"
        echo -e "   ✅ Git user configured"
    fi
fi

# 2. Check required tools
echo -e "${BLUE}2. Required Tools${NC}"
TOOLS=("java" "gradle" "git" "gh")
MISSING_TOOLS=()

for tool in "${TOOLS[@]}"; do
    if command_exists "$tool"; then
        if [ "$tool" = "java" ]; then
            JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2)
            echo -e "   ✅ $tool ($JAVA_VERSION)"
        elif [ "$tool" = "gradle" ]; then
            GRADLE_VERSION=$(gradle --version | grep "Gradle" | cut -d' ' -f2)
            echo -e "   ✅ $tool ($GRADLE_VERSION)"
        elif [ "$tool" = "gh" ]; then
            GH_VERSION=$(gh --version | head -n 1 | cut -d' ' -f3)
            echo -e "   ✅ $tool ($GH_VERSION)"
        else
            echo -e "   ✅ $tool"
        fi
    else
        echo -e "   ❌ $tool (not found)"
        MISSING_TOOLS+=("$tool")
    fi
done

if [ ${#MISSING_TOOLS[@]} -ne 0 ]; then
    echo -e "   ${YELLOW}⚠️  Missing tools: ${MISSING_TOOLS[*]}${NC}"
    echo "   Please install missing tools and run this script again"
    echo ""
    echo "   Installation instructions:"
    for tool in "${MISSING_TOOLS[@]}"; do
        case $tool in
            "java")
                echo "   - Java 17+: https://adoptium.net/"
                ;;
            "gradle")
                echo "   - Gradle: https://gradle.org/install/ (or use wrapper: ./gradlew)"
                ;;
            "gh")
                echo "   - GitHub CLI: https://cli.github.com/"
                ;;
        esac
    done
fi

# 3. Test build system
echo -e "${BLUE}3. Build System${NC}"
if ./gradlew showVersion --quiet > /dev/null 2>&1; then
    VERSION=$(grep "^app.version=" version.properties | cut -d'=' -f2)
    echo -e "   ✅ Build system working (version $VERSION)"
    
    # Test version increment
    if ./gradlew help --quiet > /dev/null 2>&1; then
        echo -e "   ✅ Gradle tasks available"
    else
        echo -e "   ${YELLOW}⚠️  Gradle tasks may have issues${NC}"
    fi
else
    echo -e "   ${RED}❌ Build system has issues${NC}"
    echo "   Please fix build errors before continuing"
fi

# 4. GitHub integration
echo -e "${BLUE}4. GitHub Integration${NC}"
if command_exists "gh"; then
    if gh auth status > /dev/null 2>&1; then
        GITHUB_USER=$(gh api user --jq '.login' 2>/dev/null || echo "unknown")
        echo -e "   ✅ GitHub CLI authenticated ($GITHUB_USER)"
        
        # Check repository
        if gh repo view > /dev/null 2>&1; then
            REPO_NAME=$(gh repo view --json nameWithOwner --jq '.nameWithOwner')
            echo -e "   ✅ Repository accessible ($REPO_NAME)"
        else
            echo -e "   ${YELLOW}⚠️  Repository not accessible via GitHub CLI${NC}"
        fi
    else
        echo -e "   ${YELLOW}⚠️  GitHub CLI not authenticated${NC}"
        if confirm "   Authenticate with GitHub now?"; then
            gh auth login
        fi
    fi
else
    echo -e "   ❌ GitHub CLI not available"
fi

# 5. Workflow files validation
echo -e "${BLUE}5. Workflow Files${NC}"
WORKFLOWS=(".github/workflows/build.yml" ".github/workflows/release.yml" ".github/workflows/version-bump.yml")
for workflow in "${WORKFLOWS[@]}"; do
    if [ -f "$workflow" ]; then
        echo -e "   ✅ $(basename "$workflow")"
    else
        echo -e "   ❌ $(basename "$workflow") (missing)"
    fi
done

# 6. Repository configuration files
echo -e "${BLUE}6. Repository Configuration${NC}"
CONFIG_FILES=(".github/CODEOWNERS" ".github/pull_request_template.md" ".github/SECURITY.md" ".github/dependabot.yml")
for config in "${CONFIG_FILES[@]}"; do
    if [ -f "$config" ]; then
        echo -e "   ✅ $(basename "$config")"
    else
        echo -e "   ❌ $(basename "$config") (missing)"
    fi
done

echo ""
echo -e "${GREEN}✨ Setup Complete!${NC}"
echo ""

# 7. Next steps
echo -e "${BLUE}🎯 Next Steps:${NC}"
echo ""

echo -e "${YELLOW}Required (Do Now):${NC}"
echo "1. 🔧 Configure GitHub repository settings:"
echo "   • Go to: Repository → Settings → Actions → General"
echo "   • Set 'Workflow permissions' to 'Read and write permissions'"
echo "   • Enable 'Allow GitHub Actions to create and approve pull requests'"
echo ""
echo "2. 🛡️ Set up branch protection:"
echo "   • Go to: Repository → Settings → Branches"
echo "   • Add protection rule for 'main' branch"
echo "   • Require pull requests, status checks, and up-to-date branches"
echo "   • See .github/branch-protection-setup.md for detailed instructions"
echo ""

echo -e "${BLUE}Optional (Enhance Later):${NC}"
echo "3. 🔐 Add code signing certificates (for signed releases):"
echo "   • Go to: Repository → Settings → Secrets and variables → Actions"
echo "   • Add MACOS_CERTIFICATE_BASE64, WINDOWS_CERTIFICATE_BASE64, etc."
echo ""
echo "4. 📢 Set up notifications (for team communication):"
echo "   • Add SLACK_WEBHOOK_URL, DISCORD_WEBHOOK_URL secrets"
echo "   • Configure team communication channels"
echo ""
echo "5. 🔒 Enable security features:"
echo "   • Go to: Repository → Settings → Security & analysis"
echo "   • Enable Dependabot alerts and security updates"
echo "   • Enable secret scanning and push protection"
echo ""

echo -e "${GREEN}🚀 Your repository is ready for production CI/CD!${NC}"
echo ""

# Test the version system
echo -e "${BLUE}🧪 Quick Test:${NC}"
if confirm "Run a quick version system test?"; then
    echo "Current version info:"
    ./gradlew showVersion --quiet
    echo ""
    echo -e "${GREEN}✅ Version system working correctly!${NC}"
fi

echo ""
echo -e "${BLUE}📚 Useful Commands:${NC}"
echo "• ./gradlew showVersion          - Show current version"
echo "• ./gradlew incrementVersion     - Increment patch version"  
echo "• ./gradlew incrementMinor       - Increment minor version"
echo "• ./gradlew incrementMajor       - Increment major version"
echo "• gh workflow run release.yml    - Trigger release build"
echo "• gh workflow run version-bump.yml - Trigger version bump"
echo ""

echo -e "${GREEN}🎉 Setup complete! Happy coding! 🎉${NC}"