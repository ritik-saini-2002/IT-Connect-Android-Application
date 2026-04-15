# IT-Connect Android Application

Enterprise IT management and employee engagement platform for Android.

## Features

- **Authentication** — PocketBase-backed login with role-based access control (7 roles: System Admin to Intern)
- **Admin Panel** — User, role, department, and company management with fine-grained permissions
- **Real-time Chat** — Messaging with background notification service (SSE-based)
- **Windows PC Control** — Remote control, file browser, touchpad, app directory via LAN HTTP agent
- **SMB File Sharing** — Browse and transfer files over Windows network shares (jcifs-ng + smbj)
- **Nagios Monitoring** — Dashboard, host/service status, alerts, background polling with notifications
- **Offline-first** — Room database caching with sync queue for offline writes
- **Profile Management** — Hierarchical organization data with avatar support

## Architecture

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Local DB | Room (with proper migrations) |
| Networking | OkHttp + Retrofit (Nagios) + Ktor (PocketBase SDK) |
| Backend | PocketBase (self-hosted) |
| Auth | PocketBase auth + EncryptedSharedPreferences |
| Async | Kotlin Coroutines + Flow |
| Image Loading | Coil |
| Logging | Timber (PII-safe in release builds) |

## Setup

1. Clone the repo
2. Copy `local.properties.example` to `local.properties` and fill in your values:
   ```properties
   pb.host=192.168.x.x
   pb.port=5005
   signing.storeFile=app/itconnect.jks
   signing.storePassword=YOUR_PASSWORD
   signing.keyAlias=itconnect
   signing.keyPassword=YOUR_PASSWORD
   ```
3. Place your signing keystore at `app/itconnect.jks`
4. Open in Android Studio and sync Gradle
5. Run on a device or emulator (minSdk 26)

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config in local.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lint
```

## CI/CD

GitHub Actions pipeline runs on push to `main`/`develop` and PRs to `main`:
- Lint check
- Unit tests
- Debug APK build
- Release APK build (main branch only, uses GitHub Secrets for signing)

## Security

- Signing credentials read from `local.properties` or environment variables (never hardcoded)
- Admin credentials stored in EncryptedSharedPreferences (not in APK)
- Cleartext HTTP restricted to private LAN IPs only
- EncryptedSharedPreferences for session tokens (no plaintext fallback)
- Backup disabled to protect sensitive local data
- FileProvider restricted to specific directories

## Project Structure

```
app/src/main/java/com/example/ritik_2/
  auth/           — Authentication repository and session management
  administrator/  — Admin panel (users, roles, departments, companies, reports)
  chat/           — Real-time messaging with notification service
  core/           — AppConfig, SyncManager, PermissionGuard, AdminTokenProvider
  data/           — Models, DTOs, data sources
  di/             — Hilt dependency injection modules
  localdatabase/  — Room entities, DAOs, database
  login/          — Login screen
  main/           — Main dashboard and navigation
  nagios/         — Nagios monitoring (dashboard, hosts, alerts, settings)
  notifications/  — Notification management
  pocketbase/     — PocketBase integration (initializer, data source, session)
  profile/        — Profile viewing and completion
  windowscontrol/ — PC remote control (touchpad, file browser, app directory)
  winshare/       — SMB/Windows file transfer
```

## License

All rights reserved.
