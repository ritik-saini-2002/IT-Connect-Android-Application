# IT-Connect Android Application
## Complete Project Documentation & Structure Guide

---

**Document Version:** 1.0  
**Last Updated:** 2026-05-08  
**Project Owner:** Ritik Saini  
**Repository:** ritik-saini-2002/IT-Connect-Android-Application

---

## 📋 Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Overview](#architecture-overview)
4. [Complete Project Structure](#complete-project-structure)
5. [Module Descriptions](#module-descriptions)
6. [Development Guidelines](#development-guidelines)
7. [Setup & Build Instructions](#setup--build-instructions)
8. [Testing & Quality Assurance](#testing--quality-assurance)
9. [Security Considerations](#security-considerations)
10. [Contributing Guidelines](#contributing-guidelines)

---

## 1. Project Overview

### Purpose
IT-Connect is an enterprise-grade Android application designed for IT engineers, IT support professionals, and personnel in IT-related fields. The application provides comprehensive tools for:

- **User Authentication & Management** - PocketBase-backed login with role-based access control
- **Administration Panel** - Centralized management of users, roles, departments, and companies
- **Real-time Communication** - Instant messaging with background notification services
- **Remote PC Control** - Control Windows machines over LAN with file browser and app directory
- **SMB File Sharing** - Browse and transfer files via Windows network shares
- **Infrastructure Monitoring** - Nagios integration for system and service monitoring
- **Offline-first Capabilities** - Seamless offline/online synchronization

### Key Features
- ✅ 7-tier role-based access control (System Admin to Intern)
- ✅ Real-time chat with SSE-based background notifications
- ✅ Remote Windows PC control via LAN HTTP agent
- ✅ SMB file sharing with jcifs-ng and smbj
- ✅ Nagios monitoring dashboard with alerts
- ✅ Room database caching with offline sync queue
- ✅ Hierarchical organization profile management with avatar support

### Project Metadata
- **Created:** July 3, 2025
- **Primary Language:** Kotlin (100%)
- **License:** All rights reserved
- **Repository Type:** Public
- **Min SDK:** 26
- **Target SDK:** 35
- **Compile SDK:** 35

---

## 2. Technology Stack

### UI/Frontend
| Technology | Purpose | Version |
|-----------|---------|---------|
| Jetpack Compose | Modern declarative UI toolkit | Latest |
| Material 3 | Material Design components | Latest |
| Jetpack Activity | Activity lifecycle management | Latest |
| Navigation Compose | Screen navigation | Latest |
| Constraint Layout | Complex layouts | Latest |

### Dependency Injection
| Technology | Purpose |
|-----------|---------|
| Hilt | Compile-time DI framework |
| Hilt Navigation Compose | DI for Compose navigation |

### Local Storage
| Technology | Purpose |
|-----------|---------|
| Room Database | Local data persistence with migrations |
| EncryptedSharedPreferences | Secure credential storage |
| DataStore | Configuration storage |

### Networking
| Technology | Purpose |
|-----------|---------|
| Retrofit | REST API communication |
| OkHttp | HTTP client with interceptors |
| Ktor Client | PocketBase SDK HTTP client |
| Gson | JSON serialization/deserialization |
| Kotlinx Serialization | Type-safe serialization |

### Backend Integration
| Technology | Purpose |
|-----------|---------|
| PocketBase (Kotlin SDK) | Backend database and auth |
| Ktor Client | HTTP client for PocketBase |

### File Sharing & SMB
| Technology | Purpose |
|-----------|---------|
| jcifs-ng | SMB file browsing and metadata |
| smbj | High-speed SMB file transfers |
| BouncyCastle | Cryptographic operations (included in smbj) |

### Asynchronous Programming
| Technology | Purpose |
|-----------|---------|
| Kotlin Coroutines | Lightweight concurrency |
| Flow | Reactive streams |
| Suspend functions | Non-blocking operations |

### Image & Media
| Technology | Purpose |
|-----------|---------|
| Coil | Efficient image loading with Compose |

### Logging & Monitoring
| Technology | Purpose |
|-----------|---------|
| Timber | Logging with PII safety in releases |
| Android WorkManager | Background task scheduling |

### Testing
| Technology | Purpose |
|-----------|---------|
| JUnit 4 | Unit testing framework |
| Mockito-Kotlin | Mocking for tests |
| Turbine | Flow testing |
| Espresso | UI testing |

---

## 3. Architecture Overview

### Architecture Pattern: MVVM + Clean Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                       │
│              (Screens, Composables, ViewModels)             │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              Domain Layer (Repository)                       │
│         (Business Logic, UseCase Implementations)           │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│               Data Layer (DataSources)                       │
│  (Remote API, Local Database, Encrypted Storage, Cache)    │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│         External Services & Infrastructure                   │
│  (PocketBase, Nagios, SMB/LAN, Notification Services)      │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow Pattern

```
UI (Compose) 
  ↓ (User Interaction)
ViewModel (StateFlow/StateHolder)
  ↓ (Data Request)
Repository
  ↓ (Query/Command)
DataSource (Local/Remote)
  ↓ (Fetch/Store)
Database/API/Cache
  ↓ (Data)
Flow/LiveData
  ↓ (Observe)
UI (Recompose)
```

### Key Architectural Principles

1. **Separation of Concerns** - Each layer has a single responsibility
2. **Dependency Injection** - Hilt manages all dependencies
3. **Reactive Data Flow** - Coroutines and Flow for async operations
4. **Offline-First** - Room database serves as source of truth
5. **Secure by Default** - EncryptedSharedPreferences for sensitive data
6. **Testability** - Clear interfaces allow for mocking and testing

---

## 4. Complete Project Structure

```
IT-Connect-Android-Application/
│
├── .github/                                 # GitHub configuration
│   └── workflows/                          # CI/CD workflows
│       ├── lint.yml                       # Lint checks
│       ├── unit-tests.yml                 # Unit test execution
│       ├── debug-build.yml                # Debug APK build
│       └── release-build.yml              # Release APK build
│
├── .idea/                                  # Android Studio configuration
│   └── inspectionProfiles/                # Code inspection configs
│
├── .claude/                                # Claude AI integration
│
├── app/                                    # Main Android application module
│
│   ├── src/main/java/com/example/ritik_2/ # Main source code
│   │
│   │   ├── MyApplication.kt               # Application class with Timber & Hilt setup
│   │
│   ├── auth/                              # Authentication Module
│   │   ├── AuthRepository.kt              # Auth business logic
│   │   ├── SessionManager.kt              # Session & token management
│   │   ├── EncryptedCredentialStore.kt    # Secure credential storage
│   │   └── AuthDataSource.kt              # Remote auth operations
│   │
│   ├── administrator/                     # Admin Panel Module
│   │   ├── AdminRepository.kt             # Admin operations
│   │   ├── UserManagement.kt              # User CRUD operations
│   │   ├── RoleManagement.kt              # Role configuration
│   │   ├── DepartmentManagement.kt        # Department operations
│   │   ├── CompanyManagement.kt           # Company operations
│   │   ├── ReportGenerator.kt             # Report generation
│   │   ├── AdminViewModel.kt              # Admin screen state
│   │   ├── AdminPanelScreen.kt            # Admin UI Composables
│   │   ├── UserListScreen.kt              # Users list UI
│   │   ├── UserDetailScreen.kt            # User detail UI
│   │   └── PermissionGuard.kt             # Fine-grained permissions
│   │
│   ├── chat/                              # Real-time Chat Module
│   │   ├── ChatRepository.kt              # Chat business logic
│   │   ├── MessageDataSource.kt           # Message operations
│   │   ├── NotificationService.kt         # SSE-based background service
│   │   ├── ChatViewModel.kt               # Chat state management
│   │   ├── ChatScreen.kt                  # Chat UI Composables
│   │   ├── MessageListComposable.kt       # Message list UI
│   │   ├── MessageInputComposable.kt      # Message input UI
│   │   ├── Notification.kt                # Notification models
│   │   └── NotificationManager.kt         # Notification handling
│   │
│   ├── contact/                           # Contact Management Module
│   │   ├── ContactRepository.kt           # Contact operations
│   │   ├── ContactViewModel.kt            # Contact state
│   │   ├── ContactListScreen.kt           # Contacts UI
│   │   └── ContactDetailScreen.kt         # Contact detail UI
│   │
│   ├── core/                              # Core/Shared Utilities Module
│   │   ├── AppConfig.kt                   # App-wide configuration
│   │   ├── SyncManager.kt                 # Offline/online sync orchestration
│   │   ├── PermissionGuard.kt             # Centralized permission checking
│   │   ├── AdminTokenProvider.kt          # Admin token management
│   │   ├── NetworkMonitor.kt              # Connectivity status
│   │   ├── ErrorHandler.kt                # Centralized error handling
│   │   ├── Constants.kt                   # App-wide constants
│   │   └── Extensions.kt                  # Utility extensions
│   │
│   ├── data/                              # Data Layer Module
│   │   ├── models/
│   │   │   ├── User.kt                    # User data class
│   │   │   ├── Chat.kt                    # Chat data class
│   │   │   ├── Role.kt                    # Role data class
│   │   │   ├── Department.kt              # Department data class
│   │   │   ├── Company.kt                 # Company data class
│   │   │   ├── NagiosHost.kt              # Nagios host model
│   │   │   ├── NagiosAlert.kt             # Nagios alert model
│   │   │   ├── FileEntry.kt               # File/folder model
│   │   │   └── SyncQueueItem.kt           # Offline sync queue item
│   │   │
│   │   ├── dtos/                          # Data Transfer Objects
│   │   │   ├── AuthRequest.kt
│   │   │   ├── AuthResponse.kt
│   │   │   ├── ChatMessageDTO.kt
│   │   │   ├── UserDTO.kt
│   │   │   └── RoleDTO.kt
│   │   │
│   │   └── datasources/
│   │       ├── LocalDataSource.kt         # Room database operations
│   │       ├── RemoteDataSource.kt        # API calls
│   │       ├── CacheDataSource.kt         # In-memory cache
│   │       └── SMBDataSource.kt           # SMB file operations
│   │
│   ├── di/                                # Dependency Injection Module (Hilt)
│   │   ├── AppModule.kt                   # App-level singletons
│   │   ├── DataSourceModule.kt            # DataSource bindings
│   │   ├── NetworkModule.kt               # HTTP client & API setup
│   │   ├── DatabaseModule.kt              # Room database setup
│   │   ├── RepositoryModule.kt            # Repository bindings
│   │   └── PocketBaseModule.kt            # PocketBase SDK setup
│   │
│   ├── drawer/                            # Navigation Drawer Module
│   │   ├── DrawerViewModel.kt             # Drawer state
│   │   ├── DrawerContent.kt               # Drawer UI Composables
│   │   ├── DrawerItem.kt                  # Drawer item model
│   │   └── NavigationHelper.kt            # Navigation logic
│   │
│   ├── localdatabase/                     # Room Database Module
│   │   ├── entities/
│   │   │   ├── UserEntity.kt
│   │   │   ├── ChatMessageEntity.kt
│   │   │   ├── RoleEntity.kt
│   │   │   ├── DepartmentEntity.kt
│   │   │   ├── SyncQueueEntity.kt
│   │   │   └── NagiosDataEntity.kt
│   │   │
│   │   ├── daos/
│   │   │   ├── UserDao.kt                 # User CRUD operations
│   │   │   ├── ChatMessageDao.kt          # Message operations
│   │   │   ├── RoleDao.kt                 # Role operations
│   │   │   ├── SyncQueueDao.kt            # Sync queue operations
│   │   │   └── NagiosDataDao.kt           # Nagios data operations
│   │   │
│   │   ├── AppDatabase.kt                 # Room database definition
│   │   └── DatabaseMigrations.kt          # Database version migrations
│   │
│   ├── login/                             # Login Module
│   │   ├── LoginRepository.kt             # Login logic
│   │   ├── LoginViewModel.kt              # Login state management
│   │   ├── LoginScreen.kt                 # Login UI Composables
│   │   ├── CredentialValidator.kt         # Input validation
│   │   └── LoginErrorHandler.kt           # Login error handling
│   │
│   ├── macnet/                            # MAC Address & Network Module
│   │   ├── MacAddressProvider.kt          # Get device MAC address
│   │   ├── NetworkInfoProvider.kt         # Network information
│   │   ├── DeviceIdentifier.kt            # Device identification
│   │   └── NetworkRepository.kt           # Network operations
│   │
│   ├── main/                              # Main Dashboard Module
│   │   ├── MainViewModel.kt               # Main dashboard state
│   │   ├── MainScreen.kt                  # Main dashboard UI
│   │   ├── DashboardContent.kt            # Dashboard content composable
│   │   ├── QuickStatsWidget.kt            # Stats widget
│   │   ├── RecentActivityWidget.kt        # Activity widget
│   │   └── MainNavigation.kt              # Main navigation setup
│   │
│   ├── nagios/                            # Nagios Monitoring Module
│   │   ├── NagiosRepository.kt            # Nagios business logic
│   │   ├── NagiosDataSource.kt            # Nagios API integration
│   │   ├── NagiosRetrofitClient.kt        # Retrofit setup for Nagios
│   │   ├── NagiosViewModel.kt             # Nagios state management
│   │   ├── NagiosDashboardScreen.kt       # Dashboard UI
│   │   ├── HostStatusScreen.kt            # Host status details
│   │   ├── ServiceStatusScreen.kt         # Service status details
│   │   ├── AlertsScreen.kt                # Alerts/notifications UI
│   │   ├── NagiosSettingsScreen.kt        # Nagios configuration UI
│   │   ├── BackgroundPollingService.kt    # Periodic status polling
│   │   ├── NagiosNotificationWorker.kt    # WorkManager scheduled checks
│   │   └── HealthCheckUtil.kt             # Utility functions
│   │
│   ├── notifications/                     # Notification Management Module
│   │   ├── NotificationManager.kt         # Notification dispatch
│   │   ├── NotificationRepository.kt      # Notification persistence
│   │   ├── PushNotificationHandler.kt     # FCM/Push handling
│   │   ├── NotificationChannel.kt         # Channel definitions
│   │   ├── NotificationViewModel.kt       # Notification state
│   │   └── NotificationListScreen.kt      # Notification UI
│   │
│   ├── pocketbase/                        # PocketBase Integration Module
│   │   ├── PocketBaseInitializer.kt       # SDK initialization
│   │   ├── PocketBaseDataSource.kt        # API operations
│   │   ├── PocketBaseCollections.kt       # Collection definitions
│   │   ├── AdminTokenProvider.kt          # Admin token management
│   │   ├── SessionManager.kt              # Session lifecycle
│   │   └── SyncService.kt                 # Sync orchestration
│   │
│   ├── profile/                           # User Profile Module
│   │   ├── ProfileRepository.kt           # Profile operations
│   │   ├── ProfileViewModel.kt            # Profile state management
│   │   ├── ProfileScreen.kt               # Profile UI Composables
│   │   ├── ProfileEditScreen.kt           # Edit profile UI
│   │   ├── AvatarUploader.kt              # Avatar upload logic
│   │   └── OrganizationTreeComposable.kt  # Hierarchical org display
│   │
│   ├── registration/                      # User Registration Module
│   │   ├── RegistrationRepository.kt      # Registration logic
│   │   ├── RegistrationViewModel.kt       # Registration state
│   │   ├── RegistrationScreen.kt          # Registration UI
│   │   ├── RegistrationValidator.kt       # Input validation
│   │   └── RegistrationErrorHandler.kt    # Error handling
│   │
│   ├── splash/                            # Splash Screen Module
│   │   ├── SplashScreen.kt                # Splash UI
│   │   ├── SplashViewModel.kt             # Splash logic
│   │   └── InitializationHelper.kt        # App initialization
│   │
│   ├── theme/                             # Material 3 Theme Module
│   │   ├── Color.kt                       # Color palette definitions
│   │   ├── Typography.kt                  # Text styles
│   │   ├── Shape.kt                       # Shape definitions
│   │   ├── Theme.kt                       # Theme composable
│   │   └── DarkColorScheme.kt             # Dark theme colors
│   │
│   ├── windowscontrol/                    # Windows PC Remote Control Module
│   │   ├── WindowsControlRepository.kt    # Control operations
│   │   ├── RemoteControlDataSource.kt     # HTTP agent communication
│   │   ├── WindowsControlViewModel.kt     # Control state management
│   │   ├── RemoteControlScreen.kt         # Control UI
│   │   ├── TouchpadEmulator.kt            # Touchpad simulation
│   │   ├── FileExplorer.kt                # Remote file browser
│   │   ├── AppDirectory.kt                # Running apps list
│   │   ├── ScreenCapture.kt               # Remote screen view
│   │   └── CommandExecutor.kt             # Command sender
│   │
│   └── winshare/                          # SMB File Sharing Module
│       ├── SmbRepository.kt               # SMB operations
│       ├── SmbConnectionManager.kt        # Connection lifecycle
│       ├── JcifsDataSource.kt             # jcifs-ng operations
│       ├── SmbjTransferService.kt         # smbj high-speed transfers
│       ├── SmbViewModel.kt                # SMB state management
│       ├── ShareBrowserScreen.kt          # Network share browser UI
│       ├── FileBrowserScreen.kt           # File browser UI
│       ├── TransferProgress.kt            # Transfer progress tracking
│       ├── TransferQueueManager.kt        # Queue management
│       └── CredentialCache.kt             # SMB credentials cache
│
│   ├── src/main/res/                     # Android resources
│   │   ├── drawable/                     # Vector drawables & images
│   │   ├── values/                       # String, color, dimension resources
│   │   ├── values-night/                 # Dark theme resources
│   │   ├── layout/                       # Legacy XML layouts (minimal)
│   │   ├── mipmap/                       # App icon assets
│   │   └── AndroidManifest.xml           # App manifest
│   │
│   ├── src/test/                         # Unit tests
│   │   ├── AuthRepositoryTest.kt
│   │   ├── ChatViewModelTest.kt
│   │   ├── NagiosRepositoryTest.kt
│   │   ├── SyncManagerTest.kt
│   │   └── ...
│   │
│   ├── src/androidTest/                  # Instrumented tests
│   │   ├── LoginScreenTest.kt
│   │   ├── ChatScreenTest.kt
│   │   ├── NavigationTest.kt
│   │   └── ...
│   │
│   ├── build.gradle.kts                  # App module build config
│   ├── proguard-rules.pro                # ProGuard/R8 rules
│   ├── itconnect.jks                     # App signing keystore
│   ├── .gitignore                        # App-level git ignore
│   └── debug/                            # Debug build artifacts
│
├── windows-server/                        # Kotlin JVM Windows Agent
│   ├── src/main/kotlin/com/itconnect/server/
│   │   ├── Main.kt                       # Server entry point
│   │   ├── HttpServer.kt                 # Ktor HTTP server setup
│   │   ├── routes/
│   │   │   ├── FileRoutes.kt             # File operations endpoints
│   │   │   ├── ControlRoutes.kt          # Control commands endpoints
│   │   │   ├── ScreenRoutes.kt           # Screen capture endpoints
│   │   │   └── SystemRoutes.kt           # System info endpoints
│   │   ├── handlers/
│   │   │   ├── FileHandler.kt            # File I/O operations
│   │   │   ├── MouseHandler.kt           # Mouse control
│   │   │   ├── KeyboardHandler.kt        # Keyboard control
│   │   │   ├── ScreenHandler.kt          # Screen capture
│   │   │   └── SystemHandler.kt          # System info
│   │   ├── models/
│   │   │   ├── FileInfo.kt
│   │   │   ├── MouseCommand.kt
│   │   │   ├── KeyboardCommand.kt
│   │   │   └── SystemInfo.kt
│   │   └── utils/
│   │       ├── Logger.kt
│   │       ├── PortFinder.kt
│   │       └── ConfigLoader.kt
│   │
│   ├── build.gradle.kts                  # Windows server build config
│   ├── settings.gradle.kts
│   └── README.md
│
├── gradle/                                # Gradle wrapper
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle.kts                      # Root build config
├── settings.gradle.kts                   # Project settings
├── gradle.properties                     # Global properties
├── gradlew                               # Gradle wrapper script (Unix)
├── gradlew.bat                           # Gradle wrapper script (Windows)
│
├── local.properties.example              # Example configuration file
├── README.md                             # Project README
├── Ritik_2.zip                           # Archive backup
│
└── .gitignore                            # Root-level git ignore

```

---

## 5. Module Descriptions

### 5.1 Authentication Module (`auth/`)
**Purpose:** Handles user authentication, session management, and credential security.

**Key Components:**
- `AuthRepository.kt` - Business logic for login/logout
- `SessionManager.kt` - Manages authentication tokens and session lifecycle
- `EncryptedCredentialStore.kt` - Stores credentials using EncryptedSharedPreferences
- `AuthDataSource.kt` - Communicates with PocketBase auth endpoints

**Key Responsibilities:**
- Authenticate users against PocketBase
- Manage JWT tokens securely
- Handle session expiration and refresh
- Provide role-based authorization checks

**Dependencies:** PocketBase SDK, EncryptedSharedPreferences, Coroutines

---

### 5.2 Administrator Module (`administrator/`)
**Purpose:** Provides administrative functions for system management.

**Key Submodules:**
- **User Management** - Create, read, update, delete users
- **Role Management** - Configure roles and permissions
- **Department Management** - Organize departments
- **Company Management** - Manage company profiles
- **Report Generation** - Generate analytics and reports

**Key Components:**
- `AdminRepository.kt` - Admin operations
- `PermissionGuard.kt` - Fine-grained permission checking
- `AdminPanelScreen.kt` - Admin dashboard UI
- `AdminViewModel.kt` - State management

**Permissions Required:**
- System_Administrator role access

**Dependencies:** Room, PocketBase, Hilt, Compose

---

### 5.3 Chat Module (`chat/`)
**Purpose:** Real-time messaging system with background notifications.

**Key Features:**
- One-on-one and group messaging
- Server-Sent Events (SSE) for real-time updates
- Background notification service
- Message persistence in Room database
- Offline message queuing

**Key Components:**
- `ChatRepository.kt` - Message operations
- `NotificationService.kt` - SSE-based background listener
- `ChatViewModel.kt` - Chat state with Flow
- `ChatScreen.kt` - Message UI
- `NotificationManager.kt` - Notification delivery

**Architecture:**
```
Message Send → ChatRepository → PocketBase → NotificationService (SSE)
                   ↓
            Room (local cache)
                   ↓
            ChatViewModel (Flow)
                   ↓
            ChatScreen (Compose)
```

**Dependencies:** Room, PocketBase, Coroutines/Flow, WorkManager

---

### 5.4 Core Module (`core/`)
**Purpose:** Shared utilities and core functionality used across the app.

**Key Components:**
- `AppConfig.kt` - App configuration and constants
- `SyncManager.kt` - Offline/online sync orchestration
- `PermissionGuard.kt` - Centralized permission system
- `AdminTokenProvider.kt` - Admin token provisioning
- `NetworkMonitor.kt` - Network connectivity tracking
- `ErrorHandler.kt` - Global error handling strategy

**Responsibilities:**
- Provide app-wide configuration
- Manage offline sync queue
- Check user permissions
- Monitor network status
- Log errors and exceptions

**Dependencies:** Coroutines, Room, PocketBase, Context

---

### 5.5 Data Layer Module (`data/`)
**Purpose:** Models, DTOs, and data source abstractions.

**Substructure:**
```
data/
  ├── models/         - Domain models
  ├── dtos/           - Transfer objects
  └── datasources/    - Abstract interfaces & implementations
```

**Key Responsibilities:**
- Define data structures
- Abstract data access patterns
- Handle serialization/deserialization

**Dependencies:** Kotlinx Serialization, Gson, Room

---

### 5.6 Dependency Injection Module (`di/`)
**Purpose:** Hilt configuration and dependency bindings.

**Modules:**
- `AppModule.kt` - App-level singletons (Context, Resources)
- `DataSourceModule.kt` - DataSource implementations
- `NetworkModule.kt` - HTTP clients (OkHttp, Retrofit)
- `DatabaseModule.kt` - Room database instance
- `RepositoryModule.kt` - Repository bindings
- `PocketBaseModule.kt` - PocketBase SDK initialization

**Pattern:**
```kotlin
@Provides
@Singleton
fun provideRepository(dataSource: DataSource): Repository {
    return RepositoryImpl(dataSource)
}
```

**Dependencies:** Hilt

---

### 5.7 Local Database Module (`localdatabase/`)
**Purpose:** Room database setup, entities, DAOs, and migrations.

**Structure:**
```
localdatabase/
  ├── entities/    - @Entity classes
  ├── daos/        - @Dao interfaces
  └── AppDatabase.kt - Main database class
```

**Key Entities:**
- `UserEntity` - Users table
- `ChatMessageEntity` - Messages table
- `RoleEntity` - Roles table
- `DepartmentEntity` - Departments table
- `SyncQueueEntity` - Offline operations queue
- `NagiosDataEntity` - Cached Nagios data

**Migrations:**
Handled in `DatabaseMigrations.kt` with version management.

**Dependencies:** Room, Kotlin Coroutines

---

### 5.8 Login Module (`login/`)
**Purpose:** Authentication UI and login flow.

**Components:**
- `LoginScreen.kt` - Login UI with Compose
- `LoginViewModel.kt` - Login state and events
- `LoginRepository.kt` - Login operations
- `CredentialValidator.kt` - Input validation logic

**Flow:**
```
User Input → LoginScreen → LoginViewModel → LoginRepository 
    → AuthDataSource → PocketBase → Session saved → Navigate to Main
```

**Dependencies:** Compose, Hilt, Coroutines, AuthRepository

---

### 5.9 Main Dashboard Module (`main/`)
**Purpose:** Home/dashboard screen and main navigation.

**Components:**
- `MainScreen.kt` - Dashboard layout
- `MainViewModel.kt` - Dashboard state
- `DashboardContent.kt` - Dashboard widgets
- `QuickStatsWidget.kt` - Statistics display
- `RecentActivityWidget.kt` - Activity feed
- `MainNavigation.kt` - Navigation setup

**Responsibilities:**
- Display user dashboard
- Show quick stats and updates
- Provide navigation to other features
- Handle main screen state

**Dependencies:** Compose, Hilt, Navigation, Room

---

### 5.10 Nagios Monitoring Module (`nagios/`)
**Purpose:** Integration with Nagios monitoring system.

**Key Features:**
- Real-time host/service status
- Alert notifications
- Dashboard with metrics
- Background polling service
- Settings configuration

**Key Components:**
- `NagiosRepository.kt` - Nagios API operations
- `NagiosRetrofitClient.kt` - Retrofit setup for Nagios
- `NagiosDashboardScreen.kt` - Dashboard UI
- `BackgroundPollingService.kt` - Periodic status updates
- `NagiosNotificationWorker.kt` - WorkManager scheduled checks
- `HealthCheckUtil.kt` - Utility functions

**Architecture:**
```
User opens Nagios tab
        ↓
NagiosDashboardScreen fetches data
        ↓
NagiosViewModel queries NagiosRepository
        ↓
NagiosDataSource calls NagiosRetrofitClient
        ↓
HTTP GET /nagios/api/v1/... 
        ↓
Cache in Room + display in UI
        ↓
BackgroundPollingService runs periodically (WorkManager)
        ↓
Updates cached data + sends notifications
```

**Credentials:** Stored in DataStore, read from local.properties

**Dependencies:** Retrofit, OkHttp, Room, WorkManager, Coroutines

---

### 5.11 Notifications Module (`notifications/`)
**Purpose:** Notification management and delivery.

**Components:**
- `NotificationManager.kt` - Dispatch notifications
- `NotificationRepository.kt` - Store notification history
- `PushNotificationHandler.kt` - FCM/push handling
- `NotificationChannel.kt` - Android notification channels
- `NotificationListScreen.kt` - Notification UI

**Responsibilities:**
- Create notification channels
- Dispatch notifications
- Handle notification taps
- Persist notification history
- Clean up old notifications

**Dependencies:** Android Notifications, Room, Coroutines

---

### 5.12 PocketBase Integration Module (`pocketbase/`)
**Purpose:** Backend API integration with PocketBase.

**Components:**
- `PocketBaseInitializer.kt` - SDK initialization with credentials
- `PocketBaseDataSource.kt` - CRUD operations on collections
- `PocketBaseCollections.kt` - Collection schema definitions
- `AdminTokenProvider.kt` - Provisioning admin tokens
- `SessionManager.kt` - Auth session handling
- `SyncService.kt` - Offline sync queuing

**Collections:**
- users
- messages
- roles
- departments
- companies
- notifications

**Host/Port:** Read from local.properties or BuildConfig

**Dependencies:** PocketBase Kotlin SDK, Ktor Client, Coroutines

---

### 5.13 User Profile Module (`profile/`)
**Purpose:** User profile viewing and management.

**Components:**
- `ProfileScreen.kt` - Profile view
- `ProfileEditScreen.kt` - Edit profile
- `ProfileRepository.kt` - Profile operations
- `AvatarUploader.kt` - Avatar upload logic
- `OrganizationTreeComposable.kt` - Hierarchical org display

**Features:**
- View user info and avatar
- Edit profile details
- Upload/change avatar
- View hierarchical organization structure

**Dependencies:** Compose, Room, PocketBase, Coil (image loading)

---

### 5.14 Windows Control Module (`windowscontrol/`)
**Purpose:** Remote control of Windows PCs over LAN.

**Key Features:**
- Touchpad emulation (mouse control)
- Remote file browser
- Running apps list
- Screen capture streaming
- Command execution

**Components:**
- `WindowsControlRepository.kt` - Control operations
- `RemoteControlDataSource.kt` - HTTP agent communication
- `RemoteControlScreen.kt` - Control UI
- `TouchpadEmulator.kt` - Touchpad gestures
- `FileExplorer.kt` - Remote file browser
- `AppDirectory.kt` - Running apps list
- `ScreenCapture.kt` - Screen streaming
- `CommandExecutor.kt` - Execute remote commands

**Communication:**
```
Android App → HTTP Client → Windows Agent (localhost:8080)
                 ↓
           LAN only (private IPs)
                 ↓
           Execute command/get screen
                 ↓
           Return result → Display in UI
```

**Windows Agent:** Kotlin JVM server in `windows-server/` module

**Security:**
- LAN traffic only
- No external routing
- Signed APK for distribution

**Dependencies:** Retrofit, OkHttp, Coroutines, Compose

---

### 5.15 SMB File Sharing Module (`winshare/`)
**Purpose:** Browse and transfer files over Windows SMB shares.

**Key Features:**
- Network share discovery
- File browsing with jcifs-ng
- High-speed transfers with smbj
- Transfer queue management
- Progress tracking
- Credential caching

**Components:**
- `SmbRepository.kt` - SMB operations abstraction
- `SmbConnectionManager.kt` - Connection lifecycle
- `JcifsDataSource.kt` - Uses jcifs-ng for metadata/browsing
- `SmbjTransferService.kt` - Uses smbj for file transfers
- `ShareBrowserScreen.kt` - Network share browser UI
- `FileBrowserScreen.kt` - File browser UI
- `TransferQueueManager.kt` - Queue management
- `CredentialCache.kt` - Credential storage

**Architecture:**
```
User selects share
    ↓
SmbRepository.listShares() → JcifsDataSource (metadata)
    ↓
ShareBrowserScreen displays shares
    ↓
User selects files
    ↓
TransferQueueManager.enqueue()
    ↓
SmbjTransferService processes queue (high-speed)
    ↓
TransferProgress updated
    ↓
Files downloaded to app storage
```

**Libraries:**
- `jcifs-ng` - SMB client (browsing, metadata)
- `smbj` - SMB client (high-speed transfers)
- `BouncyCastle` - Cryptography (included in smbj)

**Dependencies:** jcifs-ng, smbj, Coroutines, Room

---

### 5.16 Theme Module (`theme/`)
**Purpose:** Material 3 design system definitions.

**Components:**
- `Color.kt` - Color palette
- `Typography.kt` - Text styles
- `Shape.kt` - Shape definitions
- `Theme.kt` - Theme composition
- `DarkColorScheme.kt` - Dark theme colors

**Features:**
- Material 3 design compliance
- Light and dark themes
- Consistent typography
- Rounded shapes (Material 3 style)

**Dependencies:** Material 3, Compose

---

## 6. Development Guidelines

### Code Structure Best Practices

#### 6.1 Package Organization
```
feature/
  ├── presentation/     (UI Composables, ViewModels)
  ├── domain/          (Repositories, interfaces)
  └── data/            (DataSources, implementations)
```

#### 6.2 Naming Conventions
- **Files:** PascalCase (UserRepository.kt)
- **Classes:** PascalCase (UserViewModel)
- **Functions:** camelCase (getUserById)
- **Constants:** UPPER_SNAKE_CASE (API_BASE_URL)
- **Private members:** _prefixed (\_data)

#### 6.3 State Management
```kotlin
// ViewModel uses StateFlow for state
class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            // async operation
        }
    }
}

// UI observes state and recomposes
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    when (state) {
        is ChatUiState.Success -> { /* render messages */ }
        is ChatUiState.Loading -> { /* show loading */ }
        is ChatUiState.Error -> { /* show error */ }
    }
}
```

#### 6.4 Error Handling
```kotlin
// Use sealed classes for errors
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val exception: Exception, val message: String) : Result<T>()
    class Loading<T> : Result<T>()
}

// In repositories
fun getUsers(): Flow<Result<List<User>>> = flow {
    try {
        emit(Result.Loading())
        val users = dataSource.fetchUsers()
        emit(Result.Success(users))
    } catch (e: Exception) {
        emit(Result.Error(e, "Failed to fetch users"))
    }
}
```

#### 6.5 Dependency Injection with Hilt
```kotlin
// In AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppContext(application: Application): Context {
        return application.applicationContext
    }
    
    @Provides
    @Singleton
    fun provideUserRepository(dataSource: UserDataSource): UserRepository {
        return UserRepositoryImpl(dataSource)
    }
}

// In ViewModel
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val syncManager: SyncManager
) : ViewModel() {
    // Use injected dependencies
}
```

#### 6.6 Room Database Operations
```kotlin
// Define entity
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// DAO interface
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Query("SELECT * FROM users ORDER BY created_at DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>
}

// Usage in repository
class UserRepositoryImpl(private val userDao: UserDao) : UserRepository {
    override fun getUsers(): Flow<List<User>> {
        return userDao.observeAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
```

#### 6.7 Compose UI Patterns
```kotlin
// Stateless composable (always prefer)
@Composable
fun UserCard(user: User, onClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClicked)
            .padding(16.dp)
    ) {
        Text(user.name, style = MaterialTheme.typography.titleMedium)
        Text(user.email, style = MaterialTheme.typography.bodySmall)
    }
}

// Stateful screen composable
@Composable
fun UserListScreen(viewModel: UserListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn {
        when (uiState) {
            is UserListUiState.Success -> {
                items((uiState as UserListUiState.Success).users) { user ->
                    UserCard(
                        user = user,
                        onClicked = { viewModel.selectUser(user.id) }
                    )
                }
            }
            is UserListUiState.Error -> {
                item {
                    ErrorMessage(
                        message = (uiState as UserListUiState.Error).message,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
            is UserListUiState.Loading -> {
                item { CircularProgressIndicator() }
            }
        }
    }
}
```

#### 6.8 Testing Patterns
```kotlin
// Unit test with Mockito
@RunWith(MockitoJUnitRunner::class)
class ChatViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    lateinit var chatRepository: ChatRepository
    
    private lateinit var viewModel: ChatViewModel
    
    @Before
    fun setUp() {
        viewModel = ChatViewModel(chatRepository)
    }
    
    @Test
    fun testSendMessage() = runTest {
        val message = "Hello"
        val flow = flowOf(Result.Success(Unit))
        whenever(chatRepository.sendMessage(message)).thenReturn(flow)
        
        viewModel.sendMessage(message)
        
        verify(chatRepository).sendMessage(message)
    }
}

// UI test with Espresso
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testMessagesDisplayed() {
        composeTestRule.setContent {
            ChatScreen(
                messages = listOf(
                    Message("Hello", "user1"),
                    Message("Hi", "user2")
                ),
                onSendMessage = {}
            )
        }
        
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
```

---

### 6.9 Git Workflow

#### Branch Naming
- `feature/auth-improvements` - New features
- `bugfix/login-crash` - Bug fixes
- `hotfix/security-patch` - Urgent fixes
- `refactor/clean-up-auth` - Refactoring
- `docs/update-readme` - Documentation

#### Commit Messages
```
format: Type: Description

Example:
feat: Add password reset functionality
fix: Prevent infinite loop in sync manager
docs: Update setup instructions
refactor: Extract chat repository logic
test: Add unit tests for auth
```

#### Pull Request Process
1. Create feature branch from `develop`
2. Make atomic, focused commits
3. Create PR with clear description
4. Pass all CI checks (lint, tests, builds)
5. Get code review approval
6. Merge to `develop`, then to `main`

---

## 7. Setup & Build Instructions

### 7.1 Prerequisites
- Android Studio Ladybug or newer
- JDK 18 or later
- Android SDK 35
- Gradle 8.0+
- Git

### 7.2 Clone and Setup
```bash
# Clone the repository
git clone https://github.com/ritik-saini-2002/IT-Connect-Android-Application.git
cd IT-Connect-Android-Application

# Copy configuration file
cp local.properties.example local.properties

# Edit local.properties with your values
nano local.properties
```

### 7.3 Configure local.properties
```properties
# PocketBase configuration
pb.host=192.168.x.x
pb.port=5005
pb.path=/api/

# App signing (for release builds)
signing.storeFile=app/itconnect.jks
signing.storePassword=YOUR_PASSWORD
signing.keyAlias=itconnect
signing.keyPassword=YOUR_PASSWORD

# Nagios (optional)
nagios.url=http://192.168.x.x/nagios
nagios.username=nagios_user
nagios.password=nagios_password
```

### 7.4 Build Variants

#### Debug Build
```bash
# Assemble debug APK
./gradlew assembleDebug

# Run on connected device/emulator
./gradlew installDebugApp

# Run in Android Studio
# Menu → Run → Run 'app'
```

#### Release Build
```bash
# Requires signing config in local.properties
./gradlew assembleRelease

# Signed APK location: app/build/outputs/apk/release/
```

### 7.5 Gradle Tasks
```bash
# Build
./gradlew build                    # Full build
./gradlew assemble                 # Assemble all variants
./gradlew assembleDebug            # Debug APK
./gradlew assembleRelease          # Release APK

# Testing
./gradlew test                     # Run unit tests
./gradlew testDebugUnitTest        # Debug unit tests
./gradlew connectedAndroidTest     # Instrumented tests

# Code Quality
./gradlew lint                     # Run Lint
./gradlew detekt                   # Code analysis (if configured)

# Dependency Management
./gradlew dependencies             # Show dependency tree
./gradlew dependencyInsight        # Analyze specific dependency

# Cleaning
./gradlew clean                    # Clean build directory
./gradlew cleanBuildCache          # Clear build cache
```

---

## 8. Testing & Quality Assurance

### 8.1 Unit Testing
**Location:** `app/src/test/`

```kotlin
// Example: ChatRepositoryTest.kt
@RunWith(MockitoJUnitRunner::class)
class ChatRepositoryTest {
    @Mock private lateinit var chatDataSource: ChatDataSource
    @Mock private lateinit var chatDao: ChatMessageDao
    
    private lateinit var repository: ChatRepository
    
    @Before
    fun setUp() {
        repository = ChatRepository(chatDataSource, chatDao)
    }
    
    @Test
    fun `sendMessage should save to local database`() = runTest {
        val message = ChatMessage("Hello", "user1")
        
        repository.sendMessage(message)
        
        verify(chatDao).insertMessage(any())
    }
}
```

### 8.2 Instrumented Testing
**Location:** `app/src/androidTest/`

```kotlin
// Example: LoginScreenTest.kt
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun loginSuccessful() {
        composeTestRule.setContent {
            LoginScreen(
                viewModel = LoginViewModel(mockRepository)
            )
        }
        
        composeTestRule.onNodeWithTag("email_input").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("password_input").performTextInput("password123")
        composeTestRule.onNodeWithText("Login").performClick()
        
        composeTestRule.onNodeWithTag("main_screen").assertExists()
    }
}
```

### 8.3 Test Coverage
Target: 70%+ coverage for critical modules
- `auth/` - 90%
- `chat/` - 80%
- `nagios/` - 75%
- `localdatabase/` - 85%

Run coverage report:
```bash
./gradlew jacocoTestReport
# Report at: app/build/reports/jacoco/index.html
```

### 8.4 Lint & Code Quality
```bash
# Run lint checks
./gradlew lint

# Lint report at: app/build/reports/lint-results.html

# Check specific module
./gradlew :app:lint
```

### 8.5 Manual QA Checklist
- [ ] Login/logout works correctly
- [ ] Role-based UI elements render correctly
- [ ] Chat sends/receives messages
- [ ] Offline messages queue properly
- [ ] Nagios data refreshes periodically
- [ ] File transfer progress shows accurately
- [ ] Windows control responds quickly
- [ ] SMB shares mount correctly
- [ ] Notifications display properly
- [ ] Dark/light theme toggles work
- [ ] App doesn't crash on memory pressure
- [ ] No ANRs (Application Not Responding)
- [ ] Battery drain is acceptable
- [ ] Network usage is reasonable

---

## 9. Security Considerations

### 9.1 Credentials Management
✅ **SECURE:**
- Store in `EncryptedSharedPreferences`
- Use Android Keystore
- Never hardcode
- Read from environment variables (CI/CD)

❌ **INSECURE:**
- Hardcoded in source
- Plain SharedPreferences
- Stored in APK
- Unencrypted local files

### 9.2 Network Security
```xml
<!-- In AndroidManifest.xml or network_security_config.xml -->
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">192.168</domain>
    <domain includeSubdomains="true">10.0</domain>
    <domain includeSubdomains="true">172.16</domain>
</domain-config>
```

✅ Cleartext HTTP only on private LAN  
✅ HTTPS enforced for internet traffic  
✅ Certificate pinning for critical APIs  

### 9.3 Data Encryption
- Session tokens: EncryptedSharedPreferences
- Local database: Room with native encryption (optional)
- File transfers: Use SMB native encryption
- Sensitive logs: Filtered in release builds (Timber)

### 9.4 Backup & Data Loss Prevention
```xml
<!-- In AndroidManifest.xml -->
<application android:allowBackup="false">
    <!-- Prevents backup of sensitive data -->
</application>
```

### 9.5 Permission Model
```kotlin
// In PermissionGuard.kt
fun hasPermission(userId: String, feature: String): Boolean {
    val roles = userRepository.getUserRoles(userId)
    val requiredRole = getRequiredRole(feature)
    return roles.any { it.hasPermissionFor(feature) }
}
```

### 9.6 API Security
- Validate all inputs
- Sanitize error messages (no sensitive info)
- Use HTTPS with certificate pinning
- Implement rate limiting
- Add request signing (if needed)

### 9.7 File Security
```kotlin
// Use FileProvider for secure file access
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.example.itconnect.fileprovider"
    android:exported="false">
    <paths>
        <files-path name="downloads" path="/" />
    </paths>
</provider>
```

---

## 10. Contributing Guidelines

### 10.1 Getting Started
1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Make changes with clear commits
4. Write tests for new code
5. Run lint and tests: `./gradlew lint test`
6. Push to branch: `git push origin feature/my-feature`
7. Create Pull Request with description

### 10.2 Code Style
- Follow Kotlin style guide
- Use IDE code formatter (Ctrl+Alt+L)
- Max line length: 100 characters
- Consistent naming (see section 6.2)
- Add KDoc for public APIs

```kotlin
/**
 * Sends a chat message to the specified recipient.
 * 
 * This function handles offline queuing and background sync.
 * 
 * @param message The message content
 * @param recipientId The recipient's user ID
 * @return A [Result] indicating success or failure
 */
suspend fun sendMessage(message: String, recipientId: String): Result<Unit>
```

### 10.3 Commit Guidelines
- One logical change per commit
- Clear, descriptive messages
- Reference issues: "Closes #123"
- Test locally before pushing

```bash
git commit -m "feat: Add two-factor authentication
- Implement TOTP setup
- Add backup codes
- Update login flow
Closes #156"
```

### 10.4 Pull Request Template
```markdown
## Description
Brief explanation of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## How to Test
Steps to verify the changes

## Checklist
- [ ] Tests added/updated
- [ ] Code follows style guide
- [ ] Lint passes
- [ ] No breaking changes
- [ ] Documentation updated
```

### 10.5 Review Process
1. **Automated Checks** (GitHub Actions)
   - Lint analysis
   - Unit tests
   - APK build
   - Code coverage

2. **Manual Review** (Project Owner)
   - Code quality assessment
   - Architecture review
   - Security review
   - Performance impact

3. **Approval & Merge**
   - Minimum 1 approval
   - All checks passing
   - Squash and merge preferred

---

## Conclusion

This documentation provides a comprehensive guide to the IT-Connect Android Application project structure, architecture, and development practices. Developers and AI agents can use this document to:

1. **Understand the project structure** - Know where files are and their organization
2. **Make informed changes** - Follow established patterns and best practices
3. **Add new features** - Know how to integrate with existing modules
4. **Maintain code quality** - Follow testing and style guidelines
5. **Ensure security** - Implement security best practices from the start

---

**Document Created By:** Ritik Saini (Project Owner)  
**Repository:** https://github.com/ritik-saini-2002/IT-Connect-Android-Application  
**Last Updated:** 2026-05-08  
**Version:** 1.0

All rights reserved © Ritik Saini 2025-2026
