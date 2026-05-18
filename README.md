# AdoetzGPT Enhanced - Android Client

Native Android client for OpenWebUI with persistent voice sessions and background microphone support.

## Features

- ✅ **Persistent WebView** - Frontend stays alive when app is minimized
- ✅ **Background Voice Sessions** - Microphone stays active even with screen off
- ✅ **Foreground Service** - Proper Android lifecycle management
- ✅ **Configurable Backend** - Connect to any OpenWebUI instance
- ✅ **Backend Switching** - Easily switch between servers
- ✅ **WebView State Preservation** - No reload on app restore

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Android App Layer                     │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌───────────────────────────┐ │
│  │   MainActivity  │◄───┤  VoiceSessionService      │ │
│  │   (WebView)     │    │  (Foreground Service)     │ │
│  └─────────────────┘    └───────────────────────────┘ │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           OpenWebUI Frontend (SvelteKit)             │   │
│  │              Running in WebView                     │   │
│  └─────────────────────────────────────────────────────┘   │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Backend API (Configurable)              │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## Building

### Prerequisites

- Android Studio Hedgehog | 2023.1.1 or later
- JDK 17
- Android SDK 35

### Build with Gradle

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### Build with Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Click Build > Build Bundle(s) / APK(s) > Build APK(s)

## Configuration

On first launch, you'll be prompted to configure your backend:

1. **Backend URL**: Your OpenWebUI instance URL (e.g., `https://your-server.com`)
2. **API Key** (optional): If your backend requires authentication

## Permissions

The app requires the following permissions:

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Load OpenWebUI frontend |
| `RECORD_AUDIO` | Microphone for voice sessions |
| `FOREGROUND_SERVICE` | Keep voice sessions alive in background |
| `POST_NOTIFICATIONS` | Voice session notification (API 33+) |
| `WAKE_LOCK` | Keep CPU running during voice sessions |

## WebView-Native Bridge

The JavaScript interface `AndroidNative` is available in the WebView:

```javascript
// Start a voice session
window.AndroidNative.startVoiceSession(sessionId);

// Stop a voice session
window.AndroidNative.stopVoiceSession();

// Check if voice session is active
const isActive = window.AndroidNative.isVoiceSessionActive();

// Get backend URL
const backendUrl = window.AndroidNative.getBackendUrl();

// Get API key
const apiKey = window.AndroidNative.getApiKey();

// Open settings
window.AndroidNative.openSettings();

// Show a toast
window.AndroidNative.showToast("Hello from native!");
```

## Troubleshooting

### WebView reloads when app is minimized

**Solution**: The app is configured to prevent this via:
- `android:configChanges` in AndroidManifest.xml
- `onPause()`/`onResume()` in MainActivity

If it still happens, check:
1. Don't call `finish()` in `onPause()`
2. Don't destroy WebView in `onPause()`
3. Make sure `launchMode="singleTask"` is set

### Voice session stops when screen is off

**Solution**: The foreground service should keep it alive. Check:
1. Foreground service is started before voice session
2. Notification is showing
3. Partial wake lock is acquired

### Microphone doesn't work in background

**Solution**: This requires a foreground service with microphone type:
1. Check `FOREGROUND_SERVICE_MICROPHONE` permission is granted
2. Ensure notification is visible when recording

## Project Structure

```
app/src/main/
├── java/com/adoetz/gpt/
│   ├── MainActivity.kt              # WebView container
│   ├── AdoetzGPTApplication.kt      # Application class
│   ├── models/                      # Data models
│   │   ├── BackendConfig.kt
│   │   └── VoiceSessionState.kt
│   ├── service/                     # Services
│   │   ├── VoiceSessionService.kt   # Foreground service
│   │   └── AudioManager.kt          # Audio focus management
│   ├── ui/                          # UI activities
│   │   └── BackendConfigActivity.kt # Settings UI
│   └── utils/                       # Utilities
│       ├── BackendConfigManager.kt  # Config persistence
│       └── NotificationHelper.kt    # Notifications
├── res/
│   ├── layout/                      # UI layouts
│   ├── values/                      # Resources
│   └── drawable/                    # Icons
└── AndroidManifest.xml              # App configuration
```

## License

This project follows the same license as OpenWebUI.
