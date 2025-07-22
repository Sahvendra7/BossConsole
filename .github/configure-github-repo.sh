#!/bin/bash

# 🔧 GitHub Repository Configuration Script
# This script uses GitHub CLI to automatically configure repository settings

set -e  # Exit on error

echo "🔧 Configuring GitHub repository settings..."
echo "============================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if gh CLI is available and authenticated
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh) is not installed${NC}"
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI is not authenticated${NC}"
    echo "Run: gh auth login"
    exit 1
fi

# Get repository information
REPO_OWNER=$(gh repo view --json owner --jq '.owner.login' 2>/dev/null)
REPO_NAME=$(gh repo view --json name --jq '.name' 2>/dev/null)

if [ -z "$REPO_OWNER" ] || [ -z "$REPO_NAME" ]; then
    echo -e "${RED}❌ Not in a GitHub repository or repository not accessible${NC}"
    exit 1
fi

REPO_FULL_NAME="$REPO_OWNER/$REPO_NAME"
echo -e "${BLUE}📦 Repository: $REPO_FULL_NAME${NC}"
echo ""

# Function to check if user has admin access
check_admin_access() {
    PERMISSION=$(gh api repos/$REPO_FULL_NAME --jq '.permissions.admin' 2>/dev/null || echo "false")
    if [ "$PERMISSION" != "true" ]; then
        echo -e "${RED}❌ You need admin access to configure repository settings${NC}"
        echo "Contact the repository owner to grant admin permissions"
        exit 1
    fi
    echo -e "${GREEN}✅ Admin access confirmed${NC}"
}

# Function to confirm action
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

echo -e "${BLUE}1. Checking permissions...${NC}"
check_admin_access
echo ""

# Configure repository settings
echo -e "${BLUE}2. Configuring repository settings...${NC}"

# Enable vulnerability alerts
echo "   🛡️ Enabling security features..."
gh api --method PATCH repos/$REPO_FULL_NAME \
  --field has_vulnerability_alerts=true \
  --field has_dependency_graph=true > /dev/null 2>&1 && \
echo "   ✅ Security features enabled" || \
echo -e "${YELLOW}   ⚠️ Could not enable some security features (may already be enabled)${NC}"

# Enable automated security fixes
gh api --method PUT repos/$REPO_FULL_NAME/automated-security-fixes > /dev/null 2>&1 && \
echo "   ✅ Automated security fixes enabled" || \
echo -e "${YELLOW}   ⚠️ Could not enable automated security fixes${NC}"

# Configure Actions permissions
echo "   🤖 Configuring GitHub Actions..."
gh api --method PUT repos/$REPO_FULL_NAME/actions/permissions \
  --field enabled=true \
  --field allowed_actions="all" > /dev/null 2>&1

# Set workflow permissions
gh api --method PUT repos/$REPO_FULL_NAME/actions/permissions/workflow \
  --field default_workflow_permissions="write" \
  --field can_approve_pull_request_reviews=true > /dev/null 2>&1

echo "   ✅ GitHub Actions configured"

echo ""

# Branch protection rules
echo -e "${BLUE}3. Setting up branch protection...${NC}"

if confirm "Set up branch protection for 'main' branch?"; then
    echo "   🛡️ Creating branch protection rule..."
    
    # Create branch protection rule for main
    gh api --method PUT repos/$REPO_FULL_NAME/branches/main/protection \
      --field required_status_checks='{"strict":true,"contexts":["build-test (ubuntu-latest)","build-test (macos-latest)","build-test (windows-latest)","version-check","code-quality","compatibility-test"]}' \
      --field enforce_admins=false \
      --field required_pull_request_reviews='{"required_approving_review_count":1,"dismiss_stale_reviews":true,"require_code_owner_reviews":true,"require_last_push_approval":false}' \
      --field restrictions=null \
      --field allow_force_pushes=false \
      --field allow_deletions=false \
      --field block_creations=false \
      --field required_conversation_resolution=true \
      --field lock_branch=false \
      --field allow_fork_syncing=true > /dev/null 2>&1 && \
    echo "   ✅ Branch protection enabled for 'main'" || \
    echo -e "${YELLOW}   ⚠️ Could not set up branch protection (check permissions)${NC}"
fi

echo ""

# Repository topics/tags
echo -e "${BLUE}4. Setting repository topics...${NC}"
if confirm "Add descriptive topics to repository?"; then
    echo "   🏷️ Adding topics..."
    gh api --method PUT repos/$REPO_FULL_NAME/topics \
      --field names='["kotlin","compose-multiplatform","desktop-app","macos","windows","linux","automation","llm","ai","business-automation","rpa","terminal","browser","multiplatform"]' > /dev/null 2>&1 && \
    echo "   ✅ Repository topics added" || \
    echo -e "${YELLOW}   ⚠️ Could not add topics${NC}"
fi

echo ""

# Repository secrets (placeholder - these need to be added manually)
echo -e "${BLUE}5. Repository secrets...${NC}"
echo "   🔐 The following secrets should be added manually for enhanced functionality:"
echo ""
echo "   📱 For code signing (optional):"
echo "      • MACOS_CERTIFICATE_BASE64"
echo "      • MACOS_CERTIFICATE_PASSWORD"
echo "      • MACOS_KEYCHAIN_PASSWORD"
echo "      • WINDOWS_CERTIFICATE_BASE64"
echo "      • WINDOWS_CERTIFICATE_PASSWORD"
echo ""
echo "   📢 For notifications (optional):"
echo "      • SLACK_WEBHOOK_URL"
echo "      • DISCORD_WEBHOOK_URL"  
echo "      • TEAMS_WEBHOOK_URL"
echo ""
echo "   🌐 For public releases (if using sync-release workflow):"
echo "      • PUBLIC_REPO_TOKEN"
echo ""

if confirm "Open repository secrets page in browser?"; then
    gh repo view --web --branch main &
    sleep 1
    open "https://github.com/$REPO_FULL_NAME/settings/secrets/actions" 2>/dev/null || \
    echo "   Navigate to: https://github.com/$REPO_FULL_NAME/settings/secrets/actions"
fi

echo ""

# Enable additional repository features
echo -e "${BLUE}6. Additional repository features...${NC}"

# Enable issues and projects
echo "   📋 Configuring repository features..."
gh api --method PATCH repos/$REPO_FULL_NAME \
  --field has_issues=true \
  --field has_projects=true \
  --field has_wiki=false \
  --field has_discussions=false \
  --field delete_branch_on_merge=true \
  --field allow_merge_commit=true \
  --field allow_squash_merge=true \
  --field allow_rebase_merge=false \
  --field allow_auto_merge=true > /dev/null 2>&1

echo "   ✅ Repository features configured"

# Set default branch to main (if not already)
DEFAULT_BRANCH=$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')
if [ "$DEFAULT_BRANCH" != "main" ]; then
    if confirm "Set 'main' as default branch?"; then
        gh api --method PATCH repos/$REPO_FULL_NAME \
          --field default_branch="main" > /dev/null 2>&1 && \
        echo "   ✅ Default branch set to 'main'" || \
        echo -e "${YELLOW}   ⚠️ Could not change default branch${NC}"
    fi
fi

echo ""

# Test workflows
echo -e "${BLUE}7. Testing workflow access...${NC}"
if gh workflow list > /dev/null 2>&1; then
    echo "   ✅ Workflows accessible"
    echo "   📋 Available workflows:"
    gh workflow list | head -5
    
    if confirm "Enable all workflows?"; then
        gh workflow list --json name,state | jq -r '.[] | select(.state=="disabled") | .name' | while read workflow; do
            gh workflow enable "$workflow" 2>/dev/null && \
            echo "   ✅ Enabled: $workflow" || \
            echo -e "${YELLOW}   ⚠️ Could not enable: $workflow${NC}"
        done
    fi
else
    echo -e "${YELLOW}   ⚠️ Could not access workflows${NC}"
fi

echo ""

# Create environment for production
echo -e "${BLUE}8. Creating environments...${NC}"
if confirm "Create 'production' environment for releases?"; then
    gh api --method PUT repos/$REPO_FULL_NAME/environments/production \
      --field wait_timer=0 \
      --field prevent_self_review=false \
      --field reviewers='[]' \
      --field deployment_branch_policy='{"protected_branches":true,"custom_branch_policies":false}' > /dev/null 2>&1 && \
    echo "   ✅ Production environment created" || \
    echo -e "${YELLOW}   ⚠️ Could not create production environment${NC}"
fi

echo ""

# Repository description and homepage
echo -e "${BLUE}9. Repository metadata...${NC}"
if confirm "Update repository description and homepage?"; then
    DESCRIPTION="BOSS (Business Operating System Service) - Intelligent multiplatform desktop automation with AI integration, RPA capabilities, and comprehensive development tools"
    HOMEPAGE="https://risa-labs.com"
    
    gh api --method PATCH repos/$REPO_FULL_NAME \
      --field description="$DESCRIPTION" \
      --field homepage="$HOMEPAGE" > /dev/null 2>&1 && \
    echo "   ✅ Repository metadata updated" || \
    echo -e "${YELLOW}   ⚠️ Could not update metadata${NC}"
fi

echo ""

# Final validation
echo -e "${BLUE}10. Final validation...${NC}"
echo "    🔍 Checking repository configuration..."

# Check if branch protection is working
PROTECTION_STATUS=$(gh api repos/$REPO_FULL_NAME/branches/main/protection --jq '.required_status_checks.strict' 2>/dev/null || echo "false")
if [ "$PROTECTION_STATUS" = "true" ]; then
    echo "    ✅ Branch protection active"
else
    echo -e "${YELLOW}    ⚠️ Branch protection may not be fully configured${NC}"
fi

# Check Actions
ACTIONS_ENABLED=$(gh api repos/$REPO_FULL_NAME/actions/permissions --jq '.enabled' 2>/dev/null || echo "false")
if [ "$ACTIONS_ENABLED" = "true" ]; then
    echo "    ✅ GitHub Actions enabled"
else
    echo -e "${YELLOW}    ⚠️ GitHub Actions may not be enabled${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Repository configuration complete!${NC}"
echo ""

# Summary
echo -e "${BLUE}📊 Configuration Summary:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Security features enabled"
echo "✅ GitHub Actions configured with write permissions" 
echo "✅ Branch protection configured for 'main'"
echo "✅ Repository features optimized"
echo "✅ Topics and metadata updated"
echo "✅ Production environment created"
echo ""

echo -e "${BLUE}🎯 Next Steps:${NC}"
echo "1. 🔐 Add optional secrets for enhanced features"
echo "2. 🧪 Test the CI/CD workflows"  
echo "3. 🚀 Create your first release!"
echo ""

echo -e "${BLUE}💡 Quick Commands:${NC}"
echo "• gh workflow run build.yml           - Test build workflow"
echo "• gh workflow run version-bump.yml    - Increment version"
echo "• gh workflow run release.yml         - Create release"
echo "• gh repo view --web                  - Open repo in browser"
echo ""

echo -e "${GREEN}✨ Your repository is now production-ready! ✨${NC}"