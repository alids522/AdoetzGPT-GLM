#!/bin/bash

# AdoetzGPT Enhanced - GitHub Publish Script
# This script helps you publish the Android project to GitHub

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║   AdoetzGPT Enhanced - GitHub Publish Script               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if git is initialized
if [ ! -d .git ]; then
    echo "📦 Initializing Git repository..."
    git init
    echo "✓ Git repository initialized"
    echo ""
fi

# Check if remote is configured
if ! git remote get-url origin &>/dev/null; then
    echo "🔗 Please enter your GitHub repository URL:"
    echo "   Format: https://github.com/YOUR_USERNAME/YOUR_REPO.git"
    echo ""
    read -p "   Repository URL: " repo_url

    if [ -z "$repo_url" ]; then
        echo "❌ Repository URL cannot be empty"
        exit 1
    fi

    git remote add origin "$repo_url"
    echo "✓ Remote added: $repo_url"
    echo ""
fi

# Show current remote
echo "📍 Current remote:"
git remote get-url origin
echo ""

# Add all files
echo "📋 Staging files..."
git add .
echo "✓ Files staged"
echo ""

# Check if there are changes to commit
if git diff --cached --quiet; then
    echo "ℹ️  No changes to commit"
else
    # Commit
    echo "💾 Creating commit..."
    git commit -m "Initial commit: AdoetzGPT Enhanced Android Client

Features:
- Persistent WebView for OpenWebUI frontend
- Background voice session support with foreground service
- Microphone stays active when app is minimized
- Configurable backend URL
- Backend switching and logout support
- WebView state preservation

Built with GitHub Actions - automatically generates APK on push"
    echo "✓ Commit created"
    echo ""
fi

# Check if main branch exists, otherwise create it
if ! git show-ref --verify --quiet refs/heads/main; then
    echo "🌿 Creating main branch..."
    git branch -M main
    echo "✓ Main branch created"
    echo ""
fi

# Push to GitHub
echo "🚀 Pushing to GitHub..."
echo ""
read -p "Press Enter to push to GitHub (or Ctrl+C to cancel)..."
echo ""

git push -u origin main

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║   ✓ Successfully published to GitHub!                    ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo "  1. 🌐 Visit your repository on GitHub"
echo "  2. ⚙️  Go to Settings > Secrets and variables > Actions"
echo "  3. 🔐 Add these secrets for signed releases:"
echo "     - KEYSTORE_BASE64"
echo "     - KEYSTORE_PASSWORD"
echo "     - KEY_PASSWORD"
echo "     - KEY_ALIAS"
echo "  4. ▶️  Go to Actions tab and trigger a build"
echo ""
echo "See SETUP.md for detailed instructions."
echo ""
