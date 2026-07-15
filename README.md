# ForgeNav

**Opinionated navigation + offline-first MVI for Kotlin Multiplatform** — Android, iOS, and JVM desktop.

![Kotlin](https://img.shields.io/badge/kotlin-2.1+-7F52FF?logo=kotlin&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/studio.forgenav/forgenv-core?color=0D7377)
![License](https://img.shields.io/github/license/Arsenoal/forgenav)
[![CI](https://github.com/Arsenoal/forgenav/actions/workflows/ci.yml/badge.svg)](https://github.com/Arsenoal/forgenav/actions/workflows/ci.yml)

![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/platform-iOS-000000?logo=apple&logoColor=white)
![Desktop](https://img.shields.io/badge/platform-JVM%20desktop-007396?logo=openjdk&logoColor=white)

**v1.1.0** on [Maven Central](https://central.sonatype.com/namespace/studio.forgenav) · [Release notes](CHANGELOG.md#110---2026-07-15) · tag [`v1.1.0`](https://github.com/Arsenoal/forgenav/releases/tag/v1.1.0)

Type-safe routes, multi-stack Compose navigation (tabs, list–detail, results), MVI with optimistic updates, and first-class sync UX (pending outbox, conflicts, offline banners). **v1.1.0** ships Nav3-level product navigation. Pairs cleanly with **[SyncForge](https://github.com/Arsenoal/syncforge)** — or any outbox-based engine via thin ports.

> **With SyncForge:** SyncForge owns durable outbox + push/pull. ForgeNav owns navigation and presentation state that *understands* sync (optimistic UI, badges, conflict dialogs).

### What’s new in 1.1.0

- Tabs with independent back stacks (`TabSpec` / `TabNavHost`)
- `navigateForResult` + interceptors + `NavOptions` (popUpTo, singleTop, …)
- `ListDetailNavHost`, per-entry saveable state, modal chrome slots
- Deep-link stack rebuild (`stackPrefix`) + Android Intent helpers
- New artifact: **`studio.forgenav:forgenv-testing:1.1.0`**

Full notes: [CHANGELOG.md](CHANGELOG.md#110---2026-07-15) · parity: [docs/NAV3_PARITY.md](docs/NAV3_PARITY.md)

---

## Why ForgeNav

**Type-safe navigation** — Sealed `@Serializable` routes, multiplatform backstack, deep links via kotlinx-serialization.

**Offline-first MVI** — `MviViewModel` with optimistic updates, rollback, and optional `SyncFacade` binding.

**Compose-first** — `ForgeNavHost`, tabs, list–detail, transitions, predictive/system back, Material3 sync widgets.

**Nav3-level product navigation** — multi-stack tabs, navigate-for-result, deep-link stack rebuild, interceptors, adaptive panes.

**Engine-agnostic sync ports** — `SyncEngine` / `Outbox` / `ConflictResolver` in core; optional `forgenv-syncforge` adapters.

**Process death ready** — `RouteCodec` + `rememberSaveableForgeNavigator` restore the stack.

---

## Quick start

```kotlin
// app/build.gradle.kts (or shared KMP commonMain)
dependencies {
    implementation("studio.forgenav:forgenv-core:1.1.0")
    implementation("studio.forgenav:forgenv-compose:1.1.0")
    // optional SyncForge integration:
    implementation("studio.forgenav:forgenv-syncforge:1.1.0")
    // optional unit-test helpers:
    // testImplementation("studio.forgenav:forgenv-testing:1.1.0")
}
```

```kotlin
@Serializable
sealed interface AppRoute : Route {
    @Serializable data object Home : AppRoute
    @Serializable data class Detail(val id: String) : AppRoute
}

@Composable
fun App() {
    val codec = remember {
        RouteCodec().register("AppRoute", AppRoute.serializer()) { it is AppRoute }
    }
    val nav = rememberSaveableForgeNavigator(
        startRoute = AppRoute.Home,
        routeCodec = codec,
    )

    ForgeNavHost(
        navigator = nav,
        transitionSpec = NavTransitions.SlideHorizontal,
        enableSystemBack = true,
    ) { entry ->
        when (val route = entry.route) {
            is AppRoute.Home -> HomeScreen(onOpen = { nav.navigate(AppRoute.Detail("42")) })
            is AppRoute.Detail -> DetailScreen(route.id, onBack = { nav.popBackStack() })
        }
    }
}
```

**Version catalog:**

```toml
[versions]
forgenav = "1.1.0"

[libraries]
forgenav-core = { module = "studio.forgenav:forgenv-core", version.ref = "forgenav" }
forgenav-compose = { module = "studio.forgenav:forgenv-compose", version.ref = "forgenav" }
forgenav-syncforge = { module = "studio.forgenav:forgenv-syncforge", version.ref = "forgenav" }
forgenav-testing = { module = "studio.forgenav:forgenv-testing", version.ref = "forgenav" }
```

**SyncForge (optional):**

```kotlin
val facade = ForgeNavSync.fromSyncManager(
    scope = appScope,
    syncManager = syncManager,
    outbox = outboxRepository,
    conflictStore = conflictStore,
    networkMonitor = networkMonitor,
)
// Or demo loop without a server:
val loop = ForgeNavSync.localLoop(appScope)
```

Deeper walkthrough: [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) · SyncForge side: [SyncForge docs](https://github.com/Arsenoal/syncforge/tree/main/docs)

---

## Platforms

| Platform | Entry | Sample |
|----------|--------|--------|
| **Android** | Compose + `rememberSaveableForgeNavigator` | [`:sample-android`](sample-android/) |
| **iOS** | `MainViewController()` → Compose UIKit | [`:sample-ios`](sample-ios/) + [`iosApp/`](iosApp/) |
| **JVM desktop** | Compose Desktop window | [`:sample-desktop`](sample-desktop/) |

---

## Modules

| Artifact | Role |
|----------|------|
| [`forgenv-core`](forgenv-core/) | Routes, multi-stack navigator, deep links, results, interceptors, MVI, sync ports |
| [`forgenv-compose`](forgenv-compose/) | `ForgeNavHost`, `TabNavHost`, `ListDetailNavHost`, transitions, back, sync UI |
| [`forgenv-syncforge`](forgenv-syncforge/) | SyncForge adapters + `LocalSyncForgeLoop` |
| [`forgenv-testing`](forgenv-testing/) | `testForgeNavigator`, stack assertions (no Compose UI required) |

Browse published coordinates: [Maven Central — studio.forgenav](https://repo1.maven.org/maven2/studio/forgenav/)

---

## Documentation

| | |
|---|---|
| **Correctness contract** | [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) |
| **Nav3 parity backlog** | [docs/NAV3_PARITY.md](docs/NAV3_PARITY.md) |
| **Maven publish** | [docs/MAVEN_PUBLISH.md](docs/MAVEN_PUBLISH.md) |
| **Release process** | [docs/RELEASE.md](docs/RELEASE.md) |
| **Changelog** | [CHANGELOG.md](CHANGELOG.md) |
| **Companion sync engine** | [SyncForge](https://github.com/Arsenoal/syncforge) |

### Sample apps

| Module | What it proves |
|--------|----------------|
| [`:sample-android`](sample-android/) | Saveable nav, stack deep links, Intent helper, transitions, SyncForge loop |
| [`:sample-desktop`](sample-desktop/) | Same UI on JVM + Escape back |
| [`:sample-ios`](sample-ios/) / [`iosApp`](iosApp/) | CMP framework hosted in SwiftUI |

```bash
./gradlew :sample-android:installDebug
./gradlew :sample-desktop:run
open iosApp/iosApp.xcodeproj   # macOS + Xcode
```

---

## Related: SyncForge

ForgeNav is the **navigation + UI state** layer. For durable offline sync (outbox, transports, conflict store), use:

**[SyncForge](https://github.com/Arsenoal/syncforge)** — offline-first sync for Kotlin Multiplatform (`studio.syncforge`).

| Concern | Library |
|---------|---------|
| Push / pull / outbox persistence | SyncForge |
| Navigation, MVI, optimistic UI, sync chrome | ForgeNav |

---

## Development

Want to contribute or run the repo locally? See [CONTRIBUTING.md](CONTRIBUTING.md).

```bash
git clone https://github.com/Arsenoal/forgenav.git
cd forgenav
./gradlew verifyReleaseSignOff
```

Optional local SyncForge composite: clone [syncforge](https://github.com/Arsenoal/syncforge) next to this repo (`../syncforge`); otherwise CI resolves SyncForge from Maven Central.

---

## License

[Apache License, Version 2.0](LICENSE)
