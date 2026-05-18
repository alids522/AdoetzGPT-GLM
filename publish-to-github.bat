@echo off
REM AdoetzGPT Enhanced - GitHub Publish Script (Windows)
REM This script helps you publish the Android project to GitHub

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║   AdoetzGPT Enhanced - GitHub Publish Script               ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

REM Check if git is initialized
if not exist .git (
    echo [i] Initializing Git repository...
    git init
    echo [*] Git repository initialized
    echo.
)

REM Check if remote is configured
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo [*] Please enter your GitHub repository URL:
    echo    Format: https://github.com/YOUR_USERNAME/YOUR_REPO.git
    echo.
    set /p repo_url="   Repository URL: "

    if "%repo_url%"=="" (
        echo [X] Repository URL cannot be empty
        pause
        exit /b 1
    )

    git remote add origin %repo_url%
    echo [*] Remote added: %repo_url%
    echo.
)

REM Show current remote
echo [*] Current remote:
git remote get-url origin
echo.

REM Add all files
echo [*] Staging files...
git add .
echo [*] Files staged
echo.

REM Check if there are changes to commit
git diff --cached --quiet
if errorlevel 1 (
    REM There are changes
    echo [*] Creating commit...
    git commit -m "Initial commit: AdoetzGPT Enhanced Android Client

Features:
- Persistent WebView for OpenWebUI frontend
- Background voice session support with foreground service
- Microphone stays active when app is minimized
- Configurable backend URL
- Backend switching and logout support
- WebView state preservation

Built with GitHub Actions - automatically generates APK on push"
    echo [*] Commit created
    echo.
) else (
    echo [i] No changes to commit
    echo.
)

REM Check if main branch exists
git show-ref --verify --quiet refs/heads/main
if errorlevel 1 (
    echo [*] Creating main branch...
    git branch -M main
    echo [*] Main branch created
    echo.
)

REM Push to GitHub
echo [*] Pushing to GitHub...
echo.
pause

git push -u origin main

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║   [*] Successfully published to GitHub!                   ║
echo ╚════════════════════════════════════════════════════════════╝
echo.
echo Next steps:
echo   1. Visit your repository on GitHub
echo   2. Go to Settings ^> Secrets and variables ^> Actions
echo   3. Add these secrets for signed releases:
echo      - KEYSTORE_BASE64
echo      - KEYSTORE_PASSWORD
echo      - KEY_PASSWORD
echo      - KEY_ALIAS
echo   4. Go to Actions tab and trigger a build
echo.
echo See SETUP.md for detailed instructions.
echo.
pause
