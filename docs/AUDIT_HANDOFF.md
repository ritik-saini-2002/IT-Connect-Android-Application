# Handoff — IT-Connect Audit & Permission Hardening

**Branch:** `claude/flamboyant-gagarin-ccef2c`
**Worktree:** `.claude/worktrees/flamboyant-gagarin-ccef2c/`
**Last update:** 2026-04-18
**Status:** Phase 1 + Phase 2 + Phase 3 item 1 complete and committed on this branch.

This document is the source of truth for any follow-up agent on the audit & permission work stream. (See `HANDOFF.md` in this same folder for the unrelated windowscontrol feature roadmap — different scope, different agent.) Read this end-to-end before touching anything in the audit / permissions area — the bug list, design decisions, and the *why* behind each choice are captured here. Memory files in `~/.claude/projects/.../memory/` mirror the high-level decisions.

---

## Why this work exists

User reported (verbatim): *"there is many issue in permission management and database related somewhere profile is not loading and login takes more then a minute that is to irritating also check yesterday commits somewhere they are works"*.

A 4-agent parallel audit traced the symptoms to:
- A 60s+ worst-case login caused by sequential 30s probe of two PB superuser endpoints
- A `fallbackToDestructiveMigration()` gap that would crash on first launch after a future schema bump
- Stale-token bypass: revoked admins kept operating until the 9-min keep-alive cycle
- A read-modify-write race on `availableRoles` in role-management
- Many existing PermissionGuard helpers were defined but **never wired into UI/Activity call sites**

The audit also flagged a "wrong profile shown" bug — false positive. Caller code passes the *target* user via the `"userId"` extra; `targetUserId` is dead.

---

## What's done (Phase 1 — commit `e1de7de`)

Five files, four critical fixes + verified the existing crash handler.

| Fix | File | Change summary |
|-----|------|---------------|
| DB upgrade-crash safety net | `app/src/main/java/com/example/ritik_2/localdatabase/AppDatabase.kt` | Added `.fallbackToDestructiveMigration()`. Local DB is regenerable from PocketBase, so wipe-and-rebuild is acceptable. **Caveat:** any pending offline writes in `SyncQueueEntity` would be lost if this fires — investigate moving SyncQueue to a separate DB if the user has heavy offline workflows. |
| Login speed | `app/src/main/java/com/example/ritik_2/di/AppModule.kt` | OkHttp connect/read timeout 30→10s. Write stays 60s for uploads. |
| Login speed | `app/src/main/java/com/example/ritik_2/pocketbase/PocketBaseDataSource.kt` | (a) `checkIsPocketBaseSuperuser` now `suspend` and runs both endpoints in parallel via `coroutineScope { … async { } }`. (b) Added private `bgScope` (`Dispatchers.IO + SupervisorJob()`). (c) `backfillSystemAdminRole` call moved to `bgScope.launch { … }` so it runs after login returns. |
| Stale-token revalidation | `app/src/main/java/com/example/ritik_2/core/AdminTokenProvider.kt` | New top-level `class AuthRejectedException`. `refreshTokenInternal()` now distinguishes 401/403 (creds rejected) from network exception (transient) by tracking `sawAnyAuthFailure`/`sawAnyNetworkFailure` and only throwing `AuthRejectedException` when at least one endpoint cleanly rejected AND none was unreachable. `startKeepAlive`'s loop catches `AuthRejectedException` specifically, clears `cachedToken`, and `break`s the loop. |
| Role-mutation mutex | `app/src/main/java/com/example/ritik_2/administrator/rolemanagement/RoleManagementViewModel.kt` | New `roleMutationMutex: Mutex`. `createRole`, `deleteRole`, `changeUserRole`, `savePermissions` all wrap their `viewModelScope.launch { }` body in `roleMutationMutex.withLock { … }`. Same-session double-tap and overlapping edits no longer interleave. **Cross-process races still possible** — needs server-side ETag support in PocketBase (Phase 3+). |
| Crash handler | `app/src/main/java/com/example/ritik_2/core/GlobalCrashHandler.kt` | Already in place; installed at `MyApplication.onCreate()`. No change needed. Persists report to SharedPreferences `itconnect_crashes` keyed `last_crash` + `last_crash_time`. |

---

## What's done (Phase 2 — this commit)

User asked for app-wide permission hardening. After auditing the existing infrastructure, the result was: **most of the framework already exists** in `core/PermissionGuard.kt` and `data/model/Permissions.kt` — what was missing was wiring at call sites.

### Design decisions (locked, do not deviate without re-confirming with user)

Memory file: `~/.claude/projects/C--Users-LBS-StudioProjects-IT-Connect-Android-Application/memory/project_permission_system_decisions.md`.

- **Edit-profile policy:** per-field. Own name/photo/phone always editable; sensitive fields (role, companyName, salary, employeeId) require explicit permission. Implemented via `PermissionGuard.editableFields()` which already existed but was unused.
- **Permission-denied UX:** hide entirely. No greyed-out cards, no tap-then-toast. Direct intent launches that bypass the home screen still get a Toast + finish, since the screen never rendered.
- **Catalog scope:** one permission key per Activity AND per main UI card AND per admin sub-screen. The catalog already existed in `Permissions.ALL_PERMISSIONS` (50+ keys); not extended in Phase 2 — windowscontrol sub-feature keys are deferred to Phase 3.
- **system_administrator** built-in role is non-editable in UI and always implicitly has all permissions; UI for granting/revoking is hidden from anyone without `manage_permissions` / `grant_revoke_any_permission`.

### Files added / changed in Phase 2

| File | Change |
|------|--------|
| `app/src/main/java/com/example/ritik_2/core/RequirePermission.kt` | **NEW.** `ComponentActivity.requirePermission(authRepository, rule, deniedMessage)` extension. Reads the session, runs the rule lambda `(role, perms, isDbAdmin) -> Boolean`, on denial Toasts + `finish()` + returns `false`. Use BEFORE `setContent { ... }`; caller `return`s on `false`. |
| `app/src/main/java/com/example/ritik_2/administrator/companysettings/CompanySettingsActivity.kt` | Added `@Inject lateinit var authRepository` + onCreate guard requiring `canAccessAdminPanel` AND (sysadmin OR `manage_companies`). |
| `app/src/main/java/com/example/ritik_2/administrator/databasemanager/DatabaseManagerActivity.kt` | onCreate guard via `PermissionGuard.canAccessDatabaseManager(role, perms, dba)`. |
| `app/src/main/java/com/example/ritik_2/administrator/departmentmanager/DepartmentActivity.kt` | onCreate guard via `canAccessAdminPanel`. |
| `app/src/main/java/com/example/ritik_2/administrator/manageuser/ManageUserActivity.kt` | onCreate guard via `canAccessAdminPanel` AND (sysadmin OR any of the user-mod permissions). |
| `app/src/main/java/com/example/ritik_2/administrator/manageuser/ManageUserViewModel.kt` | Added `editorPerms: List<String>` field, populated from `profile.permissions` (falls back to session). `canModify()` now returns false if no user-management permission is in the live list — fixes audit High #6. |
| `app/src/main/java/com/example/ritik_2/administrator/reports/ReportsActivity.kt` | onCreate guard requiring `canAccessAdminPanel` AND any of `view_reports / view_analytics / view_team_analytics / view_hr_analytics / generate_reports / export_data`. |
| `app/src/main/java/com/example/ritik_2/administrator/rolemanagement/RoleManagementActivity.kt` | onCreate guard requiring `canAccessAdminPanel` AND (sysadmin OR `manage_roles` OR `manage_permissions`). Computes `canManagePermissions` (true only for sysadmin / DB admin / `grant_revoke_any_permission`) and passes it to `RoleManagementScreen`. |
| `app/src/main/java/com/example/ritik_2/administrator/rolemanagement/RoleManagementScreen.kt` | New `canManagePermissions: Boolean = false` parameter. `RolesListView`'s `onEditPerms` is `((RoleInfo) -> Unit)?` — null when caller can't manage permissions. `RoleCard`'s `onEditPerms` likewise nullable; the lock-icon `IconButton` is wrapped in `if (onEditPerms != null) { ... }` so it disappears for non-admin users. |
| `app/src/main/java/com/example/ritik_2/profile/profilecompletion/ProfileCompletionActivity.kt` | Computes `editableFields = PermissionGuard.editableFields(...)` and passes it to `ProfileCompletionScreen`. |
| `app/src/main/java/com/example/ritik_2/profile/profilecompletion/ProfileCompletionScreen.kt` | New `editableFields: Set<String> = PermissionGuard.ALL_FIELDS` parameter. The `isAdmin && isEditing` branch in Account Info and Professional Details sections wraps each `PCField(...)` in `if ("fieldName" in editableFields) { ... }` — so a sysadmin who has `edit_profile` but had `companyName`/`role`/`salary` revoked still can't see those inputs. |

**Already-guarded Activities** (touched in earlier commits, still correct):
- `AdministratorPanelActivity.kt` (lines 105, 131, 216 use `canAccessAdminPanel` / `canAccessDatabaseManager`)
- `CreateUserActivity.kt`
- `MainActivity.kt:181` blocks card click via `canAccessFeature`
- `MainScreen.kt:156, 404` hides cards/drawer entries via `canAccessFeature`
- `DatabaseManagerViewModel.kt:87` defends VM logic
- `ProfileActivity.kt` already passes `canManagePermissions = canEdit && sessionId != userId` and uses `canEditProfile`

---

## What is NOT done (deferred to Phase 3)

Tracked in priority order. Each can ship independently.

### High value
1. ~~**Per-sub-feature permission keys for Windows Control.**~~ **DONE.** Added `windows_control_touchpad`, `windows_control_file_browser`, `windows_control_app_directory`, `windows_control_admin_settings`, `windows_control_add_step` to `Permissions.ALL_PERMISSIONS` + `forRole(ROLE_ADMIN)`. Added `PermissionGuard.canAccessWindowsControlSub()`. Wired `@AndroidEntryPoint` + `requirePermission` into all 6 windowscontrol Activities (including parent `PcControlActivity`).
2. **Permission key constants instead of literal strings.** Audit Medium: `Permissions.kt` defines string keys but call sites in `RoleManagementViewModel`, `PermissionGuard`, etc. use bare literals. A single typo anywhere breaks the gate silently. Add `const val PERM_X = "x"` for every key in `Permissions.kt` and replace literals project-wide.
3. **Cross-process ETag/optimistic concurrency** for role mutations. Phase 1's mutex only covers same-session. Two admins on different devices can still clobber each other's role changes. Either implement client-side compare-and-set (re-fetch + diff before PATCH) or add server-side `updatedAt` validation in PocketBase rules.

### Medium value
4. **PocketBase collection-name constants in one place.** `PocketBaseDataSource.kt:26-29` defines `COL_USERS`, `COL_COMPANIES`, etc., but `DatabaseManagerViewModel.kt:276-278` (and others) hardcode the same strings. Single source of truth needed.
5. **`PcControlDatabase` migration chain validation.** Audit High #2 — version 8 is declared but it's unclear whether all v3→v7 intermediate migrations are registered. Same pattern as Phase 1's `AppDatabase` fix may be safe (cache-only DB → destructive fallback), but verify whether `PcLog` / `PcDevice` / `PcSchedule` carry user-meaningful state that would be painful to lose.
6. **Optional DAO null-safety in `PcControlRepository`.** Audit High #5 — DAOs are nullable in the constructor but used as if non-null. Either inject as non-null at init or wrap each call site in `?.let { }`.
7. **Built-in role write-protect.** Audit Medium #5 — saving permissions for a built-in role like "Administrator" persists to `role_definitions` but a factory re-seed via `Permissions.forRole()` would silently restore defaults. Either forbid edits in the UI (`if (role.isBuiltIn) disable savePermissions`) or make the seed defer to existing custom values.
8. **Profile-completion error surfacing.** `ProfileCompletionViewModel.kt:58` swallows admin-token fetch errors via `catch (_: Exception) {}`. Subsequent admin saves silently fail. At minimum log; ideally surface a one-time UI warning so the admin knows their save was a no-op.
9. **Snapshot-listener cleanup.** `notifications/NotificationServices.kt` has commented-out `addSnapshotListener` calls that, if re-enabled, would leak. Document the Activity-lifecycle cleanup pattern before reactivating.

### Low value / polish
10. Pre-existing icon deprecation warnings (Icons.Filled.* → Icons.AutoMirrored.Filled.*). 30+ instances across the project. Mechanical fix.
11. **Per-field profile gating sweep.** Phase 2 wired `editableFields` only into the `isAdmin && isEditing` branch (highest-stakes fields). The `isManager && isEditing` and self-edit branches still use the legacy hardcoded layout. If finer control is desired, repeat the `if ("field" in editableFields)` pattern in those branches too.

---

## How to verify Phase 1 + Phase 2 manually

After installing the new APK on a device:

1. **Login speed.** Time it. Should be 1-3s on a healthy LAN, ≤10s worst case (one endpoint timeout). Previous worst case was 60s+.
2. **DB upgrade safety.** No way to test directly without a future schema bump; the safety net is dormant.
3. **Stale-token revalidation.** Log in as System_Administrator on device A. On device B (PocketBase admin UI) change the SA user's password. Wait 9-10 min on device A. The next admin operation should fail cleanly with "Admin credentials rejected by server" rather than silently using the old token.
4. **Role-list mutex.** Open Role Management. Double-tap "Create Role" or rapidly toggle permissions on two roles in quick succession. No duplicate roles or lost edits.
5. **Activity guards.** Sign in as `Employee`. Try `adb shell am start -n com.example.ritik_2/.administrator.databasemanager.DatabaseManagerActivity`. Should Toast "Database Manager — System Administrator only" and finish. Repeat for the 5 other admin Activities.
6. **Hide grant/revoke UI.** Sign in as `Administrator` (NOT System_Administrator). Open Role Management. The lock/security icon next to each role should be absent. Sign in as System_Administrator — the icon should appear.
7. **`canModify` live perm check.** Sign in as Admin who has `modify_user`. Open Manage Users, edit a user — works. In another window (PB admin), revoke `modify_user`. In the app, force-refresh Manage Users (back-and-reopen). Edit attempt should now show "You don't have permission to modify this user".
8. **Per-field profile gating.** Sign in as System_Administrator. Edit another user's profile. All fields visible (`ALL_FIELDS`). Sign in as a Manager — only designation/department editable. Sign in as the user themselves — only `SELF_EDITABLE_FIELDS` (address, phone, experience, emergency contacts, image) editable.

---

## Useful commands

```bash
# Compile (worktree)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileDebugKotlin --console=plain

# View Phase 1 + Phase 2 commits
git log --oneline -3

# View just this branch's changes vs main
git log main..HEAD --oneline
git diff main...HEAD --stat
```

---

## Memory files (read these for context, not strict spec)

- `user_role.md` — Ritik is the solo developer; informal bug reports; wants triage not perfect repros
- `project_overview.md` — Kotlin/Compose, PocketBase + Firestore + Room, Windows-control agent
- `project_permission_system_decisions.md` — locked-in design choices for the permission overhaul

---

## TL;DR for the next agent

The bug fixes and the wiring layer are done. The infrastructure (`PermissionGuard`, `Permissions`, `requirePermission`) is solid and reusable. If the user asks for "Phase 3", the most-asked items are likely (1) windowscontrol sub-feature keys, (2) const-ifying the permission strings, (3) making the per-field gating sweep through the manager and self-edit branches too. **Do not start any of these without an explicit ask** — surface them as recommendations and wait for sign-off, same as the previous phases.
