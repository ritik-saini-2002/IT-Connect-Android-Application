# Windowscontrol Feature Roadmap — Resumable Handoff

**Purpose.** A future Claude session (or human) can resume this work cold by reading only this file plus the files it references. Every phase lists exact file paths, schema deltas, and acceptance criteria.

**Last updated:** 2026-04-17
**Working branch pattern:** `claude/<worktree-name>` → merge to `main` per sub-phase
**Authoritative files:**
- Entities/DAOs: [PcControlDatabase.kt](../app/src/main/java/com/saini/ritik/windowscontrol/data/PcControlDatabase.kt)
- Models: [PcControlModels.kt](../app/src/main/java/com/saini/ritik/windowscontrol/data/PcControlModels.kt)
- ViewModel: [PcControlViewModel.kt](../app/src/main/java/com/saini/ritik/windowscontrol/viewmodel/PcControlViewModel.kt)
- Devices UI: [PcDevicesUI.kt](../app/src/main/java/com/saini/ritik/windowscontrol/ui/screens/PcDevicesUI.kt)
- API client: [PcControlApiClient.kt](../app/src/main/java/com/saini/ritik/windowscontrol/network/PcControlApiClient.kt)
- App bootstrap: [MyApplication.kt](../app/src/main/java/com/saini/ritik/MyApplication.kt)
- Single-ton module: [PcControlMain.kt](../app/src/main/java/com/saini/ritik/windowscontrol/PcControlMain.kt)

## Environment baseline at roadmap start

- Room DB version: **5** with `PcPlan`, `PcConnectionLog`, `PcSavedDevice` entities and `MIGRATION_3_4`, `MIGRATION_4_5`.
- `PcSavedDevice` fields: `id, label, host, port, streamPort, secretKey, isMaster, pcName, addedAt, lastUsed, lastSeenOnline`.
- Agent endpoint `GET /screen/capture?q=<q>&s=<s>` returns `{"image": "<base64 jpeg>"}` — reused for thumbnails.
- Master-key-guarded endpoints already follow a clean pattern (`X-Secret-Key: <masterKey>`); see `PcControlApiClient.getConnectedUsers` for shape.
- WorkManager dep already present (`libs.androidx.work.runtime.ktx`). Coil already present.
- **Python agent source is NOT in this repo.** All server-side changes in Phase 1.4 and later phases need to land in the separate agent repo.

---

## Phase 1 — Saved-device power-user features

### 1.1 — Saved-device thumbnails ✅ planned in 1.1+1.2 combined commit

**Goal.** Cache a recent screenshot per saved device; show it on the device card.

**Schema (part of combined v5→v6 migration, see §1.2).**
Add to `PcSavedDevice`:
```kotlin
val thumbnailPath     : String? = null   // internal files-dir path, not URI
val thumbnailUpdatedAt: Long    = 0L
```

**New file.** `app/src/main/java/com/example/ritik_2/windowscontrol/network/PcThumbnailFetcher.kt`
- Method `suspend fun fetch(device: PcSavedDevice, context: Context): String?` — returns file path or null.
- Uses a transient `PcControlApiClient(PcControlSettings(device.host, device.port, device.secretKey))` to call `/screen/capture?q=20&s=6`.
- Decodes base64 to bytes, writes to `context.filesDir/pc_thumbs/<device.id>.jpg` atomically (write to `.tmp`, then `File.renameTo`).
- Swallows network exceptions, returns null.

**Repository.** Add `suspend fun updateThumbnail(id: String, path: String?, at: Long)` → DAO `UPDATE pc_saved_devices SET thumbnailPath=?, thumbnailUpdatedAt=? WHERE id=?`.

**ViewModel.** Add `fun refreshThumbnail(device: PcSavedDevice)` that runs fetcher on `viewModelScope` + updates repo. Add `fun refreshAllThumbnails()` called from `PcDevicesUI` `LaunchedEffect(Unit)` — throttled so it only runs for devices whose `thumbnailUpdatedAt < now - 5 min`.

**UI.** In `SavedDeviceCard`, replace the 38dp circular icon when `thumbnailPath != null`:
```kotlin
AsyncImage(model = File(device.thumbnailPath),
    contentDescription = null,
    modifier = Modifier.size(38.dp).clip(CircleShape),
    contentScale = ContentScale.Crop)
```
Fall back to current icon otherwise.

**Acceptance.**
- Open Devices screen → thumbnails populate for online devices within ~2s.
- Kill network → still renders cached thumbnails.
- Delete device → thumbnail file cleaned up in `deleteDevice` repo method.

### 1.2 — Wake-on-LAN

**Schema (combined v5→v6 migration with §1.1).**
Add to `PcSavedDevice`:
```kotlin
val macAddress      : String? = null    // "AA:BB:CC:DD:EE:FF"
val broadcastAddress: String? = null    // e.g. "192.168.1.255"; null = auto-derive
val wolPort         : Int     = 9
```

**Migration v5→v6 SQL (6 new columns, all nullable / defaults).**
```sql
ALTER TABLE pc_saved_devices ADD COLUMN thumbnailPath TEXT;
ALTER TABLE pc_saved_devices ADD COLUMN thumbnailUpdatedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pc_saved_devices ADD COLUMN macAddress TEXT;
ALTER TABLE pc_saved_devices ADD COLUMN broadcastAddress TEXT;
ALTER TABLE pc_saved_devices ADD COLUMN wolPort INTEGER NOT NULL DEFAULT 9;
```

Bump `@Database(..., version = 6)` and `.addMigrations(..., MIGRATION_5_6)`.

**New file.** `app/src/main/java/com/example/ritik_2/windowscontrol/network/WakeOnLan.kt`
- `object WakeOnLan { suspend fun wake(mac: String, broadcast: String?, port: Int = 9): Boolean }`
- Builds the 6×`0xFF` preamble + 16× MAC bytes = 102-byte magic packet.
- Broadcast: use given addr or compute from active network interface's IPv4 + /24 assumption (fallback: `"255.255.255.255"`).
- UDP: `DatagramSocket().apply { broadcast = true }.send(DatagramPacket(...))`. Wrap in `withContext(Dispatchers.IO)` + try/catch → return bool.
- MAC parsing tolerant of `:`, `-`, or no separators (uppercase/lowercase).

**ViewModel.** `fun wakePc(device: PcSavedDevice)` → launches, updates `_uiState` to Success("Wake packet sent to <label>") or Error.

**UI.**
- `SavedDeviceCard`: add a small `FilledTonalIconButton` with `Icons.Default.PowerSettingsNew` tinted amber, visible only when `device.macAddress != null`. Calls `onWake`.
- `EditDeviceDialog`: add two fields (MAC address, broadcast override optional) at the bottom of the dialog, under an `AnimatedVisibility` gated by a "Show advanced" toggle. Port is hard-coded to 9 for MVP.
- Validate MAC regex `^[0-9A-Fa-f]{2}([:-]?[0-9A-Fa-f]{2}){5}$` before enabling save.

**Acceptance.**
- Device with MAC shows power button; tap → Toast "Wake packet sent".
- Device without MAC has no button.
- Editing MAC persists and survives app restart.

### 1.3 — Power scheduler (Room + WorkManager)

**New entity + DAO** in `PcControlDatabase.kt`:
```kotlin
@Entity(tableName = "pc_schedules")
data class PcSchedule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String,            // FK to pc_saved_devices.id (no hard FK; handled in code)
    val action: String,              // "WOL" | "SHUTDOWN" | "SLEEP" | "LOCK" | "EXECUTE_PLAN"
    val planId: String? = null,      // only if action == EXECUTE_PLAN
    val hour: Int,                   // 0..23 local time
    val minute: Int,                 // 0..59
    val daysMask: Int,               // bits 0..6 = Sun..Sat; 0x7F = daily
    val enabled: Boolean = true,
    val lastFiredAt: Long = 0L,      // de-dupe — don't fire twice within 90s window
    val createdAt: Long = System.currentTimeMillis()
)

@Dao interface PcScheduleDao {
    @Query("SELECT * FROM pc_schedules ORDER BY hour, minute") fun getAll(): Flow<List<PcSchedule>>
    @Query("SELECT * FROM pc_schedules WHERE deviceId = :id") fun getForDevice(id: String): Flow<List<PcSchedule>>
    @Query("SELECT * FROM pc_schedules WHERE enabled = 1") suspend fun getEnabledSync(): List<PcSchedule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: PcSchedule)
    @Delete suspend fun delete(s: PcSchedule)
    @Query("UPDATE pc_schedules SET lastFiredAt=:ts WHERE id=:id") suspend fun markFired(id: String, ts: Long)
}
```

**Migration v6→v7.**
```sql
CREATE TABLE IF NOT EXISTS pc_schedules (
  id TEXT NOT NULL PRIMARY KEY,
  deviceId TEXT NOT NULL,
  action TEXT NOT NULL,
  planId TEXT,
  hour INTEGER NOT NULL,
  minute INTEGER NOT NULL,
  daysMask INTEGER NOT NULL,
  enabled INTEGER NOT NULL,
  lastFiredAt INTEGER NOT NULL DEFAULT 0,
  createdAt INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pc_schedules_device ON pc_schedules(deviceId);
```

Bump `@Database(..., version = 7)`, expose `abstract fun scheduleDao()`, pass to repo.

**Repository extensions.** `allSchedules: Flow<List<PcSchedule>>`, `schedulesForDevice(id)`, `upsertSchedule`, `deleteSchedule`, `dueSchedulesNow(): List<PcSchedule>` (filters by current day+hour+minute±3 min and `lastFiredAt < now - 90s`).

**New file.** `app/src/main/java/com/example/ritik_2/windowscontrol/scheduler/PcScheduleWorker.kt`
- `class PcScheduleWorker(ctx, params) : CoroutineWorker(ctx, params)`
- `override suspend fun doWork(): Result = withContext(Dispatchers.IO) { ... }`
- Logic:
  1. `val due = repo.dueSchedulesNow()` — may be empty.
  2. For each: resolve device via `repo.findById(deviceId)`; skip if missing.
  3. Dispatch:
     - `WOL` → `WakeOnLan.wake(device.macAddress, device.broadcastAddress, device.wolPort)`
     - `SHUTDOWN`/`SLEEP`/`LOCK` → transient `PcControlApiClient` `.executeQuickStep(PcStep("SYSTEM_CMD", action))`
     - `EXECUTE_PLAN` → load `PcPlan` by `planId`, `.executePlan(plan)`
  4. `repo.markScheduleFired(schedule.id, now)` regardless of success (prevents retry storm).
- Return `Result.success()` — individual failures should not fail the worker.

**Worker registration.** In `MyApplication.onCreate` after `PcControlMain.init`:
```kotlin
val req = PeriodicWorkRequestBuilder<PcScheduleWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "pc_schedule_tick", ExistingPeriodicWorkPolicy.KEEP, req
)
```

**ViewModel.** Expose `schedulesFor(deviceId): StateFlow<List<PcSchedule>>`, `addSchedule(...)`, `toggleSchedule(s)`, `deleteSchedule(s)`.

**UI.**
- Add `Icons.Default.Schedule` button to `SavedDeviceCard` → opens dialog.
- New file `app/src/main/java/com/example/ritik_2/windowscontrol/ui/screens/PcScheduleDialog.kt`
- Full-height bottom-sheet or `AlertDialog` listing existing schedules + "Add" button.
- Per schedule row: time (hh:mm) • days chips (S M T W T F S) • action label • enable toggle • delete.
- Add dialog: `TimePicker` (material3), day-of-week `FilterChip`s, action dropdown (WOL/SHUTDOWN/SLEEP/LOCK/EXECUTE_PLAN; last opens plan picker).

**Acceptance.**
- Schedule "Wake at 08:30 weekdays" → with the app backgrounded, at 08:30 on Mon–Fri the worker fires a magic packet within the 15-min scheduler window.
- `lastFiredAt` prevents double-fire if the worker runs twice within 90s.
- Disable toggle stops future fires immediately.

### 1.4 — Agent self-update endpoints (⚠️ REQUIRES EXTERNAL AGENT REPO)

**Where the agent lives.** Not in this repo. The CLI search `find -iname "agent_v*.py"` returned nothing in this tree. The Kotlin `PcControlApiClient` talks to whatever Flask app runs on the PC. Ask the user for the agent repo path before making server-side changes.

**Kotlin client (in-repo, safe to land now).**
Append to `PcControlApiClient.kt`:
```kotlin
suspend fun uploadAgentCode(masterKey: String, code: ByteArray): PcNetworkResult<String>
suspend fun rollbackAgent(masterKey: String): PcNetworkResult<String>
suspend fun getAgentVersion(masterKey: String): PcNetworkResult<String>
```
All use a 60-second OkHttp client (agent file-swap + restart can take ~20s). Body shape:
```json
{ "code_b64": "<base64>", "sha256": "<hex>" }
```
Client computes SHA-256 of `code` locally and sends both — server verifies before writing.

**Server spec (hand to the agent repo maintainer).**
```python
# All three routes: require _is_master(request.headers.get("X-Secret-Key"))
# Use hmac.compare_digest for the digest check. Never trust content alone.

# POST /agent/update
# Body: {"code_b64": str, "sha256": str}
# 1. data = base64.b64decode(code_b64)
# 2. if len(data) < 4096 or len(data) > 5_000_000: reject
# 3. if hashlib.sha256(data).hexdigest() != sha256: reject (use compare_digest)
# 4. if b"Flask" not in data[:20000]: reject (sanity marker — adjust to match your agent)
# 5. Ring-buffer backups: rename agent.py → agent.py.bak.1, .bak.1 → .bak.2, keep 3 deep; drop .bak.3
# 6. Write data to agent.py.new, then os.replace(agent.py.new, agent.py) — atomic
# 7. Respond {"ok": true}, then in a background thread: time.sleep(0.5); os._exit(42)
#    NSSM (or your service wrapper) should be configured to relaunch on exit.

# POST /agent/rollback
# Swap agent.py.bak.1 → agent.py (via temp file + os.replace), then restart as above.

# GET /agent/version
# Return {"version": "<current-version-constant>", "modified_at": <int-epoch>}
```

**Acceptance (once agent ships).**
- Upload a malformed file → 400, no restart.
- Upload valid file → 200 within 1s, new version reported within 30s.
- Rollback → prior version active, older backup now at `.bak.1`.
- Non-master key → 403 on all three endpoints.

---

## Phase 2 — Transport security

### 2.1 — Cert pinning (HTTPS agent)

**Prereq.** Agent must first serve HTTPS. Options discussed:
- Self-signed cert generated on first agent launch (stored next to `agent.py`). Fingerprint shown in agent UI.
- Mobile scans fingerprint via QR or typed entry.

**Kotlin.** Store the SHA-256 cert fingerprint per saved device in a new `certFingerprint TEXT` column (migration vN→vN+1). `PcBaseClient` builds OkHttpClient with `CertificatePinner.Builder().add(host, "sha256/<b64>").build()` when fingerprint is set. When unset: current cleartext behavior preserved (but log a warning).

**Files to touch.**
- [PcControlDatabase.kt](../app/src/main/java/com/saini/ritik/windowscontrol/data/PcControlDatabase.kt): add column + migration
- [PcControlApiClient.kt](../app/src/main/java/com/saini/ritik/windowscontrol/network/PcControlApiClient.kt): conditional pinner in `buildHttp()`
- `EditDeviceDialog`: add fingerprint field + "Scan QR" action
- New file: `network/PcCertFingerprintScanner.kt` for the QR flow (CameraX + ML Kit already in project? check)

**Open question.** Does the existing PocketBase backend already have a real cert via reverse proxy? If yes, its OkHttp client should migrate off `usesCleartextTraffic` independently — track in §2.2 if scope creeps.

### 2.2 — Remove cleartext traffic allowance

Currently `android:usesCleartextTraffic="true"` in `AndroidManifest.xml` because agent IPs are raw. Once §2.1 is in, flip it to `false` with a `network_security_config.xml` that explicitly whitelists LAN ranges (`10.*`, `172.16.*–172.31.*`, `192.168.*`) for pre-fingerprint flows only. Document the trade-off.

### 2.3 — Biometric re-auth for master-key actions

**Trigger.** Any call using `masterKey` in `PcControlApiClient` (`getConnectedUsers`, `kickUser`, `changeSecretKey`, `uploadAgentCode`, `rollbackAgent`).

**Approach.** Wrap master-key calls through a small `MasterActionGate` that:
1. Checks `BiometricManager.canAuthenticate(STRONG or DEVICE_CREDENTIAL)`.
2. On screens that use these endpoints, show a `BiometricPrompt` before the call fires; cache an OK for 5 minutes.
3. Falls back to "Enter master key again" text dialog when biometrics unavailable.

**New file.** `app/src/main/java/com/example/ritik_2/windowscontrol/security/MasterActionGate.kt`. Wire into `PcControlViewModel` — every master-key entry point goes through `gate.require { ... }`.

---

## Phase 3 — Real-time + push

### 3.1 — WebSocket replaces polling

**Current state.** The app polls `/ping`, `/screen/capture`, `/connections` on timers. Add a `/ws` endpoint on the agent that pushes events (user connected/disconnected, screenshot-ready, status-change). Client: OkHttp `WebSocket` with backoff reconnect.

**Kotlin.** New file `network/PcAgentSocket.kt`. ViewModel subscribes and folds events into existing flows. Keep polling as fallback when socket fails.

**Server.** Add Flask-Sock or similar; master-key still gates connection upgrade.

### 3.2 — FCM push for offline notifications

**Prereq.** `google-services.json` must land in `app/`. Ask the user to provision the Firebase project.

**Scope.**
- Agent POSTs to a tiny relay (could be a PocketBase collection) when it comes online/offline.
- Relay pushes FCM message to registered device tokens.
- Android `FirebaseMessagingService` delivers a local notification.

**Defer until.** `google-services.json` is in place — any sooner and we waste effort on mocks.

---

## Phase 4 — Audit + offline + tests

### 4.1 — Audit trail collection in PocketBase

New collection `audit_events`: `{user, actor_device, target_pc, action, masked_payload, at}`. Client writes on every master-key call, plan execution, device delete. Admin screen lists last 500.

### 4.2 — Offline queue UI

`PcCommandQueue` — Room-backed outbox for plan executions attempted while offline. When `PcConnectionStatus.ONLINE` fires, drain the queue in order. Already-connected users should see a "Pending (N)" chip.

### 4.3 — Instrumentation tests

- Room migration tests: `MigrationTestHelper` for v5→v6, v6→v7, eventual v7→v8.
- WoL magic-packet shape (unit test with a loopback UDP listener).
- ViewModel state-flow tests using `TurbineTestRule` — already a test dep.

---

## Phase 5 — Deferred (intentionally)

Not scoped here:
- End-to-end encryption of per-plan secrets
- Multi-user RBAC on PocketBase beyond current role fields
- In-app agent installer/downloader
- Remote desktop streaming over WebRTC

These require architectural decisions that should not be fait accompli.

---

## Commit & merge discipline

Per sub-phase: one commit on `claude/<worktree>`, then fast-forward merge to `main`, then `git push origin main`.

```bash
# on the worktree branch
git add -A && git commit -m "feat(windowscontrol): <phase> — <short summary>"

# fold into main
git checkout main
git pull --ff-only origin main
git merge --ff-only claude/<worktree>
git push origin main
git checkout claude/<worktree>
```

If `--ff-only` fails (main advanced since), rebase the worktree branch onto main first. Do **not** force-push to main under any circumstance.

## Resume checklist for a fresh session

1. `git log --oneline -10` → identify the last `feat(windowscontrol)` commit to know where we stopped.
2. Read this file top-to-bottom.
3. `git status` — verify working tree clean. If not, diff first before touching anything.
4. Pick the first unchecked phase. Re-verify its "before" state by grepping for the new entity/column names — if they already exist, move to the next phase.
5. Run a build after each phase: `./gradlew :app:compileDebugKotlin` is fast enough for a sanity check; full `:app:assembleDebug` before merging.
