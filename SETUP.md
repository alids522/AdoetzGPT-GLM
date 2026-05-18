# GitHub Actions Setup Guide

## Step 1: Create a New GitHub Repository

I **cannot create repositories for you** - you must do this yourself:

### Option A: Via GitHub Website
1. Go to https://github.com/new
2. Repository name: `AdoetzGPT-Enhanced` (or any name you prefer)
3. Description: `OpenWebUI Android Client with Persistent Voice Sessions`
4. Visibility: **Private** or **Public** (your choice)
5. **DO NOT** initialize with README, .gitignore, or license
6. Click **Create repository**

### Option B: Via GitHub CLI (if installed)
```bash
gh repo create AdoetzGPT-Enhanced --private --description "OpenWebUI Android Client"
```

---

## Step 2: Push Code to GitHub

```bash
cd "/Volumes/NAS-1/root/ai project/openwebui2/AdoetzGPT GLM"

# Initialize git
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit: AdoetzGPT Enhanced Android Client

Features:
- Persistent WebView for OpenWebUI frontend
- Background voice session support
- Foreground service for microphone
- Configurable backend URL
- Backend switching support"

# Add remote (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/AdoetzGPT-Enhanced.git

# Push
git push -u origin main
```

---

## Step 3: Configure GitHub Secrets (For Signed Releases)

For signed APK releases, you need to set up repository secrets:

### Generate a Keystore (One Time)

```bash
# On your local machine
keytool -genkey -v -keystore adoetzgpt-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias adoetzgpt
```

### Encode Keystore to Base64

```bash
# macOS
base64 -i adoetzgpt-release.jks | pbcopy

# Linux
base64 -w 0 adoetzgpt-release.jks | xclip

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("adoetzgpt-release.jks")) | Set-Clipboard
```

### Add Secrets to GitHub

Go to: `https://github.com/YOUR_USERNAME/AdoetzGPT-Enhanced/settings/secrets/actions`

Click **New repository secret** and add:

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | Paste the base64 encoded keystore |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password (usually same as keystore) |
| `KEY_ALIAS` | `adoetzgpt` |

---

## Step 4: Trigger a Build

### Automatic Build
Push to `main` branch:
```bash
git push origin main
```

### Manual Build
1. Go to: `https://github.com/YOUR_USERNAME/AdoetzGPT-Enhanced/actions`
2. Click **"Build Android APK"** workflow
3. Click **"Run workflow"**
4. Select `debug` or `release`
5. Click **"Run workflow"**

---

## Step 5: Download the APK

### From Workflow Run
1. Go to the Actions tab
2. Click on the workflow run
3. Scroll to **Artifacts** section
4. Download the APK

### From Release (for release builds)
1. Go to: `https://github.com/YOUR_USERNAME/AdoetzGPT-Enhanced/releases`
2. Download the APK from the latest release

---

## Build Workflows

### Workflow 1: Build APK (`.github/workflows/build.yml`)

**Triggers:**
- Push to `main` or `develop` branch
- Pull request to `main` or `develop`
- Manual trigger

**Builds:**
- Debug APK (unsigned)
- Release APK (unsigned, no keystore required)

**Artifacts:**
- Retained for 30 days (debug)
- Retained for 90 days (release)

### Workflow 2: Build Signed Release (`.github/workflows/release.yml`)

**Triggers:**
- Push tag (e.g., `git tag v1.0.0 && git push origin v1.0.0`)
- Manual trigger

**Builds:**
- Signed release APK

**Requirements:**
- Keystore secrets must be configured

**Creates:**
- GitHub Release with APK attached

---

## Quick Reference

### Push a new version
```bash
# Make changes
git add .
git commit -m "Your commit message"

# Push (triggers build)
git push origin main
```

### Create a release
```bash
# Tag the commit
git tag v1.0.0

# Push the tag (triggers signed build)
git push origin v1.0.0
```

### Download APK from command line (using gh CLI)
```bash
# List artifacts
gh run list --repo YOUR_USERNAME/AdoetzGPT-Enhanced

# Download from latest run
gh run download --repo YOUR_USERNAME/AdoetzGPT-Enhanced
```

---

## Troubleshooting

### Build fails with "SDK location not found"
- The GitHub Actions uses its own Android SDK
- This error should not occur on GitHub Actions
- If it occurs, check the workflow file

### Build fails with "Could not resolve dependencies"
- This is a temporary issue
- Re-run the workflow

### Signed build fails with "Keystore password incorrect"
- Check your secrets in GitHub repository settings
- Make sure all 4 secrets are set correctly

### APK is too large
- Check the build summary for APK size
- Enable ProGuard/R8 shrinking (already enabled)
- Check for unnecessary resources

---

## Next Steps

1. ✅ Create repository on GitHub
2. ✅ Push code to repository
3. ✅ Configure secrets (for signed releases)
4. ✅ Trigger first build
5. ✅ Download and test APK
6. ✅ Release on GitHub (optional)
7. ✅ Submit to Google Play (requires additional setup)
