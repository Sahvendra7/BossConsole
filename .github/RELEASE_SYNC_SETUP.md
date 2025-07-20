# Release Sync Setup Guide

This guide explains how to set up automatic release synchronization from this private repository to the public `risa-labs-inc/BOSS-Releases` repository.

## Overview

The GitHub Actions workflow `sync-release.yml` automatically:
1. **Detects new releases** in this private repository
2. **Downloads all release assets** (MSI, ZIP files, etc.)
3. **Creates identical release** in the public repository
4. **Updates README.md** with new version and date
5. **Maintains professional presentation** for public users

## Required Setup

### 1. Create Personal Access Token (PAT)

You need a GitHub Personal Access Token with permissions to manage the public repository:

1. **Go to GitHub Settings**:
   - Navigate to [GitHub Settings > Developer settings > Personal access tokens > Fine-grained tokens](https://github.com/settings/personal-access-tokens/fine-grained)

2. **Create New Token**:
   - **Name**: `BOSS Release Sync Token`
   - **Expiration**: 1 year (or no expiration for long-term use)
   - **Resource owner**: `risa-labs-inc`
   - **Repository access**: Select `risa-labs-inc/BOSS-Releases`

3. **Required Permissions**:
   ```
   Repository permissions:
   ✅ Contents: Read and write
   ✅ Metadata: Read
   ✅ Pull requests: Read and write
   ✅ Issues: Read and write
   ✅ Actions: Read and write
   ```

4. **Generate and Copy Token**:
   - Click "Generate token"
   - **IMPORTANT**: Copy the token immediately (you won't see it again)

### 2. Add GitHub Repository Secret

1. **Navigate to Repository Settings**:
   - Go to this repository's Settings tab
   - Click "Secrets and variables" → "Actions"

2. **Add New Secret**:
   - Click "New repository secret"
   - **Name**: `PUBLIC_REPO_TOKEN`
   - **Value**: Paste the Personal Access Token from step 1
   - Click "Add secret"

### 3. Verify Workflow Permissions

Ensure the GitHub Actions workflow has the necessary permissions:

1. **Go to Repository Settings**:
   - Navigate to "Actions" → "General"

2. **Workflow Permissions**:
   - Select "Read and write permissions"
   - Check "Allow GitHub Actions to create and approve pull requests"
   - Click "Save"

## How It Works

### Automatic Trigger
The workflow triggers automatically when you:
```bash
# Create and publish a new release
gh release create v8.9.0 --title "BOSS v8.9.0" --notes "Release notes here" *.msi *.zip
```

### What Happens Next
1. **Asset Download**: All release files are downloaded from private repo
2. **Public Release**: Identical release created in `risa-labs-inc/BOSS-Releases`
3. **README Update**: Version numbers and dates updated automatically
4. **Professional Presentation**: Public repository maintains professional appearance

### Manual Testing
To test the workflow without creating a real release:

```bash
# Test workflow with a draft release
gh release create v8.8.1-test --draft --title "Test Release" --notes "Testing sync workflow" *.msi *.zip

# Publish when ready to test sync
gh release edit v8.8.1-test --draft=false
```

## Workflow Features

### ✅ **Asset Synchronization**
- Automatically downloads all release assets
- Preserves file names and metadata
- Supports MSI, ZIP, and any other file types

### ✅ **Professional Updates**
- Updates version badges in README
- Updates download links automatically
- Maintains consistent formatting

### ✅ **Error Handling**
- Verifies downloads before upload
- Handles missing assets gracefully
- Provides detailed logging

### ✅ **Security**
- Uses secure token authentication
- No sensitive data exposed in logs
- Follows GitHub security best practices

## Troubleshooting

### Common Issues

**Issue**: Workflow fails with "Resource not accessible by integration"
**Solution**: Verify the `PUBLIC_REPO_TOKEN` secret has correct permissions

**Issue**: README not updating
**Solution**: Check that the public repository allows write access

**Issue**: Assets not uploading
**Solution**: Verify file paths and ensure assets exist in the private repository

### Debugging Steps

1. **Check Workflow Logs**:
   - Go to "Actions" tab in this repository
   - Click on the failed workflow run
   - Examine step-by-step logs

2. **Verify Token Permissions**:
   - Test token manually: `gh auth login --with-token < token.txt`
   - Try accessing public repo: `gh repo view risa-labs-inc/BOSS-Releases`

3. **Manual Sync Test**:
   ```bash
   # Test token access
   export GITHUB_TOKEN="your_token_here"
   gh release list --repo risa-labs-inc/BOSS-Releases
   ```

## Security Notes

- **Token Security**: Keep the PAT secure and rotate regularly
- **Minimal Permissions**: Only grant necessary repository permissions
- **Audit Access**: Regularly review token usage in GitHub settings
- **No Sensitive Data**: Ensure release notes don't contain secrets

---

## Quick Setup Checklist

- [ ] Create Personal Access Token with correct permissions
- [ ] Add `PUBLIC_REPO_TOKEN` secret to repository
- [ ] Verify workflow permissions are set to "Read and write"
- [ ] Test with a draft release
- [ ] Confirm automatic sync works as expected

After completing these steps, every new release in this private repository will automatically appear in the public `risa-labs-inc/BOSS-Releases` repository with professional presentation and up-to-date documentation.