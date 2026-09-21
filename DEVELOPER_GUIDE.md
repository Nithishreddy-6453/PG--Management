# PG Manager — Developer & Architecture Setup Guide
## Android Project Foundation & Core Bootstrap

Welcome to the **PG Manager** Android codebase. This document outlines the architectural standards, directory layouts, startup sequences, dependency management, and developer workflows implemented in the project foundation.

---

## 1. Project Directory Structure

PG Manager is designed to follow **Feature-First Architecture** and **Clean Architecture** boundaries, mapped inside our primary package namespace `com.example`.

```
/app/src/main/java/com/example/
│
├── core/                           # Core shared infrastructure
│   ├── common/                     # Cross-cutting concerns & base architecture
│   │   ├── BaseArchitecture.kt     # Standard UDF components (UiState, UiEvent, BaseViewModel)
│   │   ├── PgLogger.kt             # Security and diagnostic logging abstractions
│   │   └── PgResult.kt             # Monadic result wrapper for domain exception mapping
│   │
│   ├── designsystem/               # Centralized Material Design 3 tokens
│   │   └── Theme.kt                # Custom palette, typography pairing, and spacing tokens
│   │
│   └── di/                         # Compile-time, reflection-free DI Container
│       └── Modules.kt              # Database, Preferences, Dispatchers, and AppContainer contracts
│
├── features/                       # Modular business features
│   └── startup/                    # App launch, bootstrap, onboarding screens
│       ├── SplashScreen.kt         # M3 layout with pulsing icon animations & progress monitoring
│       ├── StartupManager.kt       # Startup Coordinator managing asynchronous bootstraps
│       └── WelcomeScreen.kt        # Onboarding layout with high-contrast accessible CTAs
│
├── navigation/                     # Safe screen navigation routing
│   └── Routes.kt                   # Compile-time, type-safe screen routes
│
└── MainActivity.kt                 # Application window orchestrating modern edge-to-edge screens
```

---

## 2. Bootstrapping Sequence

During cold-starts, the application executes a highly optimized lifecycle initialization sequence to minimize main thread blocking:

```
[ PgApplication.onCreate() ]
          │
          ├── 1. Initialize Dependency Graph (AppContainerImpl)
          ├── 2. Setup Global Uncaught Exception Handler
          └── 3. Initialize StartupCoordinator (StartupManager)
                    │
                    ▼
          [ MainActivity.onCreate() ]
                    │
                    ▼
          [ SplashScreen (SCR_001) ] ── (Requests startupManager.startBootstrap())
                    │
                    ├── State.Initializing(10%) -> Logger Ready
                    ├── State.Initializing(40%) -> Verification complete
                    ├── State.Initializing(70%) -> Databases warmed up
                    └── State.Ready             -> Transitions to Welcome Screen (SCR_002)
```

---

## 3. Dependency Injection Architecture

Our DI architecture relies on an elegant, compile-time type-safe container pattern. This bypasses the bytecode weaving overhead of Dagger Hilt in sandboxed or preview environments while enforcing perfect architectural separation:

* **`AppContainer`:** Exposes sub-modules:
  * `loggerModule`: Diagnostic console logger routing.
  * `dispatcherModule`: Exposes CoroutineDispatchers (`Dispatchers.IO`, `Dispatchers.Default`, `Dispatchers.Main`).
  * `preferencesModule`: Handles secure, key-value property state queries.
  * `databaseModule`: Manages SQLite database references.
  * `repositoryModule`: Placeholders mapping data queries to entity models.

---

## 4. Base Presentation Architecture (UDF)

Our presentation layer uses **Unidirectional Data Flow (UDF)** to prevent side-effect leaks and race conditions:

1. **`UiState`:** Immutable state model rendered by Jetpack Compose.
2. **`UiEvent`:** Interactive user actions dispatched to the ViewModel.
3. **`UiEffect`:** Translucent, short-lived side-effects (snackbars, routing) dispatched via buffered `Channel` models.
4. **`BaseViewModel`:** Abstract core ViewModel standardizing flows, state collection, and background dispatcher transitions.

---

## 5. Developer Guide & Quality Verification

### Code Formatting and Lint Checks
We enforce high-quality coding standards. Before submitting pull requests, developers must run our verification checks:

```bash
# 1. Compile the complete application
gradle assembleDebug

# 2. Execute local JVM unit tests
gradle :app:testDebugUnitTest
```
