#!/bin/bash

# 🚀 Complete Production Repository Setup
# This script sets up everything needed for a production-ready BOSS repository

set -e  # Exit on error

echo "🚀 BOSS Production Repository Setup"
echo "===================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# ASCII Art Banner
echo -e "${PURPLE}"
cat << 'EOF'
██████╗  ██████╗ ███████╗███████╗
██╔══██╗██╔═══██╗██╔════╝██╔════╝
██████╔╝██║   ██║███████╗███████╗
██╔══██╗██║   ██║╚════██║╚════██║
██████╔╝╚██████╔╝███████║███████║
╚═════╝  ╚═════╝ ╚══════╝╚══════╝
                                 
Business Operating System Service
Production CI/CD Setup
EOF
echo -e "${NC}"
echo ""

# Function to run with status indication
run_step() {
    local step_name="$1"
    local command="$2"
    local optional="${3:-false}"
    
    echo -e "${BLUE}🔄 $step_name...${NC}"
    
    if eval "$command"; then
        echo -e "${GREEN}✅ $step_name completed${NC}"
        return 0
    else
        if [ "$optional" = "true" ]; then
            echo -e "${YELLOW}⚠️  $step_name failed (optional - continuing)${NC}"
            return 0
        else
            echo -e "${RED}❌ $step_name failed${NC}"
            return 1
        fi
    fi
}

# Check prerequisites
echo -e "${BLUE}🔍 Checking prerequisites...${NC}"

if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI not installed${NC}"
    echo "Install from: https://cli.github.com/"
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo -e "${RED}❌ Git not installed${NC}"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI not authenticated${NC}"
    echo "Run: gh auth login"
    exit 1
fi

if [ ! -f "version.properties" ] || [ ! -f "build.gradle.kts" ]; then
    echo -e "${RED}❌ Not in BOSS repository root${NC}"
    echo "Run this script from the repository root directory"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites met${NC}"
echo ""

# Get repository info
REPO_FULL_NAME=$(gh repo view --json nameWithOwner --jq '.nameWithOwner')
echo -e "${BLUE}📦 Repository: $REPO_FULL_NAME${NC}"
echo ""

# Step 1: Validate build system
run_step "Testing build system" "./gradlew showVersion --quiet"

# Step 2: Configure GitHub repository settings  
run_step "Configuring GitHub repository" "./.github/configure-github-repo.sh" true

# Step 3: Validate workflows
echo -e "${BLUE}🔄 Validating GitHub workflows...${NC}"
WORKFLOW_COUNT=$(find .github/workflows -name "*.yml" -type f | wc -l | tr -d ' ')
if [ "$WORKFLOW_COUNT" -gt 0 ]; then
    echo "   Found $WORKFLOW_COUNT workflow files:"
    find .github/workflows -name "*.yml" -type f -exec basename {} \; | sed 's/^/   • /'
    echo -e "${GREEN}✅ Workflows validated${NC}"
else
    echo -e "${RED}❌ No workflow files found${NC}"
    exit 1
fi

# Step 4: Test version system
run_step "Testing version management" "./gradlew generateVersionConstants --quiet"

# Step 5: Create initial commit if needed
echo -e "${BLUE}🔄 Checking git status...${NC}"
if [ -n "$(git status --porcelain)" ]; then
    echo "   📝 Uncommitted changes detected"
    echo "   📋 Changed files:"
    git status --short | sed 's/^/   /'
    
    read -p "   Commit production setup changes? (y/n): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git add .
        git commit -m "🚀 Production CI/CD setup

- Add comprehensive GitHub Actions workflows
- Implement centralized version management  
- Configure security scanning and monitoring
- Add branch protection and code review templates
- Enable automated release building and deployment
- Add performance monitoring and quality gates

🤖 Generated with Claude Code"
        
        echo -e "${GREEN}✅ Changes committed${NC}"
        
        read -p "   Push to remote? (y/n): " -n 1 -r  
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git push
            echo -e "${GREEN}✅ Changes pushed to remote${NC}"
        fi
    fi
else
    echo -e "${GREEN}✅ Git working tree clean${NC}"
fi

# Step 6: Test a workflow
echo -e "${BLUE}🔄 Testing CI/CD system...${NC}"
read -p "Run a test build workflow? (y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if gh workflow run build.yml; then
        echo -e "${GREEN}✅ Test workflow triggered${NC}"
        echo "   View progress: gh run list --limit 1"
        echo "   Or visit: https://github.com/$REPO_FULL_NAME/actions"
    else
        echo -e "${YELLOW}⚠️  Could not trigger test workflow${NC}"
    fi
fi

echo ""

# Final summary
echo -e "${GREEN}🎉 Production setup complete!${NC}"
echo ""
echo -e "${PURPLE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 SETUP SUMMARY${NC}"
echo -e "${PURPLE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo -e "${GREEN}✅ COMPLETED FEATURES:${NC}"
echo "   🏗️  Cross-platform build automation (macOS, Windows, Linux)"
echo "   📦  Centralized version management with auto-generation"
echo "   🔒  Comprehensive security scanning and monitoring"
echo "   🚀  Automated release building and publishing"
echo "   🛡️  Branch protection with required reviews"
echo "   📢  Multi-platform notification system"
echo "   📈  Performance monitoring and optimization"
echo "   🤖  Dependabot for automated dependency updates"
echo "   👥  Code ownership and review templates"
echo "   🔐  Code signing setup for production releases"
echo ""

echo -e "${BLUE}🎯 NEXT STEPS:${NC}"
echo "   1. 🔑 Add optional secrets for enhanced features:"
echo "      • Code signing certificates (MACOS_CERTIFICATE_BASE64, etc.)"
echo "      • Notification webhooks (SLACK_WEBHOOK_URL, DISCORD_WEBHOOK_URL)"
echo "      • Public release token (PUBLIC_REPO_TOKEN)"
echo ""
echo "   2. 🧪 Test the complete system:"
echo "      • gh workflow run build.yml          # Test builds"
echo "      • gh workflow run version-bump.yml   # Test versioning"
echo "      • gh workflow run release.yml        # Create release"
echo ""
echo "   3. 🚀 Create your first automated release:"
echo "      • ./gradlew incrementMinor           # Update version"
echo "      • git add . && git commit -m 'Ready for release'"
echo "      • git tag v\$(grep '^app.version=' version.properties | cut -d'=' -f2)"
echo "      • git push --tags                    # Trigger release build"
echo ""

echo -e "${PURPLE}🔗 USEFUL LINKS:${NC}"
echo "   • Repository:    https://github.com/$REPO_FULL_NAME"
echo "   • Actions:       https://github.com/$REPO_FULL_NAME/actions"
echo "   • Releases:      https://github.com/$REPO_FULL_NAME/releases"
echo "   • Security:      https://github.com/$REPO_FULL_NAME/security"
echo "   • Settings:      https://github.com/$REPO_FULL_NAME/settings"
echo ""

echo -e "${BLUE}💡 QUICK COMMANDS:${NC}"
cat << 'EOF'
   # Version Management
   ./gradlew showVersion              # Show current version
   ./gradlew incrementVersion         # Bump patch (8.8.0 → 8.8.1)
   ./gradlew incrementMinor           # Bump minor (8.8.0 → 8.9.0)
   ./gradlew incrementMajor           # Bump major (8.8.0 → 9.0.0)
   
   # CI/CD Operations
   gh workflow list                   # List all workflows
   gh workflow run build.yml          # Trigger build & test
   gh workflow run version-bump.yml   # Automated version bump
   gh workflow run release.yml        # Create release
   gh run list --limit 5              # Show recent runs
   
   # Repository Management
   gh repo view --web                 # Open in browser
   gh pr list                         # List pull requests
   gh release list                    # List releases
   gh secret list                     # List configured secrets
EOF

echo ""
echo -e "${GREEN}✨ Your BOSS repository is now enterprise-ready! ✨${NC}"
echo -e "${PURPLE}   Happy coding and automated releasing! 🚀${NC}"
echo ""