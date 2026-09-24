# Brick Kiln ERP — Android App

A native Android app (Kotlin + Jetpack Compose) for field workers to enter
production data and sync it to the desktop ERP server.

## Key Features

- **Offline-first**: All production entries are saved locally to a Room
  SQLite database first, then auto-synced when network is available.
- **Wi-Fi + Cloud**: Connects to the desktop ERP server on the same LAN
  (fast, free), and optionally to a cloud relay (Supabase) for out-of-Wi-Fi
  usage. Auto-fails over from LAN to cloud.
- **Existing users**: Mobile app uses the SAME username/password as the
  desktop ERP. No new accounts needed.
- **Three production stages**:
  1. Raw Brick Making (کچی اینٹ بنانا)
  2. Transport + Loading (بھٹے تک لانا + بھٹے میں جوڑنا)
  3. Baked Brick Unloading (پکی اینٹ نکالنا)
- **Worker lookup**: Search workers by name or code, see their rate/balance.
- **Sync status**: See how many entries are pending, last sync time, today's totals.

## How it works

```
   Mobile App (Android)
       │
       │ HTTP POST /rpc {channel, args}
       │
       ├─── LAN (Wi-Fi): ───────►  Desktop ERP (Server mode, port 8765)
       │                                │
       │                                └── SQLite (master DB)
       │
       └─── Cloud relay: ────────►  Supabase Edge Function
                                       │
                                       └── forwards to desktop (via tunnel)
```

When the user logs in:
1. App POSTs `auth:login` to desktop server (LAN preferred, cloud fallback)
2. App POSTs `mobile:context` to get user info + workers list + work types
3. All data is cached in Room (offline SQLite)

When the user creates a production entry:
1. Entry is saved locally with status=PENDING
2. WorkManager SyncWorker picks it up within 15 minutes (or immediately
   on manual "Sync Now" tap)
3. SyncWorker POSTs `mobile:submit-production` for each pending entry
4. On success, status=SYNCED. On failure, status=FAILED (with error)
5. Failed entries are retried on next sync cycle

## Building the app

### Prerequisites

- Android Studio Hedgehog or newer (or just JDK 17 + Android SDK 34)
- Android SDK 34 (Android 14)
- Min SDK 24 (Android 7.0)

### Steps

1. Open this folder in Android Studio: `File → Open → select this directory`
2. Let Gradle sync complete (downloads dependencies, ~2 minutes)
3. Generate launcher icons:
   - Right-click `app/src/main/res` → `New → Image Asset`
   - Icon type: Launcher Icons
   - Name: ic_launcher
   - Use a brick/kiln themed image or vector
4. Connect your Android phone (or start an emulator)
5. Click the green ▶ Run button (or `Shift+F10`)
6. The app installs and opens

### Generating a signed APK for distribution

1. In Android Studio: `Build → Generate Signed Bundle / APK → APK`
2. Create a new keystore (or use existing) — **save the keystore + password
   safely**, you'll need them for every update
3. Select `release` build variant
4. Click Finish — APK is at `app/build/outputs/apk/release/app-release.apk`
5. Transfer the APK to your phone and install (enable "Install unknown apps"
   in phone settings if needed)

## Setup (for end users)

After installing the app on a phone:

1. Open the app — you'll see the Login screen
2. Tap the ⚙ Settings icon (top right)
3. Enter the desktop server's IP address (visible on the desktop ERP's
   Network Settings page, e.g., `192.168.1.10`)
4. Port: `8765` (default)
5. Access code: enter the code shown on the desktop ERP's Network Settings
   (leave empty if no code was set)
6. Tap "Test LAN Connection" — you should see ✓
7. (Optional) Enter cloud relay URL for out-of-Wi-Fi usage
8. Save → go back to Login → enter your ERP username + password
9. Login — your workers list + work types will sync automatically
10. Tap + to create a production entry, even offline!

## Architecture

```
app/src/main/java/com/brickkiln/erp/
├── BrickKilnApp.kt          # Application class, inits WorkManager
├── MainActivity.kt          # Single-activity host for Compose
├── data/
│   ├── local/                # Room offline cache
│   │   ├── AppDatabase.kt
│   │   ├── dao/              # Data Access Objects
│   │   └── entity/           # SQLite row schemas
│   ├── remote/               # Network layer
│   │   ├── ApiClient.kt      # Retrofit factory + LAN/cloud fallback
│   │   ├── ApiService.kt    # RPC interface
│   │   └── Dtos.kt           # Request/Response DTOs
│   └── repository/           # Business logic
│       ├── AuthRepository.kt
│       ├── ProductionRepository.kt
│       └── SettingsRepository.kt
├── sync/
│   └── SyncWorker.kt         # WorkManager periodic sync
├── ui/
│   ├── theme/Theme.kt        # Material 3 color scheme
│   ├── navigation/AppNavGraph.kt
│   ├── login/                # Login screen
│   ├── home/                 # Dashboard with today's stats
│   ├── production/           # Entry form + history
│   ├── worker/               # Worker lookup
│   └── settings/             # Server config
└── util/
    └── NetworkMonitor.kt     # Connectivity checks
```

## Dependencies

- Jetpack Compose (UI)
- Room (offline SQLite)
- Retrofit + OkHttp (HTTP)
- WorkManager (background sync)
- DataStore (settings)
- CameraX + ML Kit (barcode scan — future)
- Material 3

## Desktop ERP compatibility

Requires desktop ERP v2.9.0 or newer. The desktop must be in Server mode
(Network Settings → Server → Apply). See the main repo:
https://github.com/mohsin-rasheedbaga/brick-kiln-erp

## License

MIT
