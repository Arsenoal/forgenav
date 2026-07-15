# Changelog

All notable changes to ForgeNav are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] — Nav3-functional navigation (Phase A)

### Added

- **Multi-stack / tabs** — `TabSpec`, `selectTab` / `tabBackStack` / `navigateInTab`, `TabNavHost` bottom-nav scaffold
- **Nav options** — `NavOptions` (popUpTo, singleTop, clearBackStack, save/restore flags, result request id)
- **Results** — `navigateForResult`, `setResult`, `NavResult.Ok` / `Cancelled`, `NavResultHub`
- **Interceptors** — `NavigationInterceptor` + `InterceptResult` (Proceed / Cancel / Redirect), `StartRouteProvider`
- **Deep links** — stack rebuild via `DeepLinkPattern.stackPrefix` / `DeepLink.stackRoutes`, priority matching, tab/nested graph targeting, Android `Intent.toForgeDeepLinkUri()` / `handleForgeDeepLink`
- **Adaptive UI** — `ListDetailNavHost` (compact single stack / expanded dual pane)
- **Entry saveable state** — per-`NavEntry.id` `SaveableStateHolder` in `ForgeNavHostContent`
- **Modal slots** — custom `dialog` / `bottomSheet` chrome on `ForgeNavHost`
- **Stack ops** — `setBackStack`, `popBackStack(count)`, enhanced save/restore with `selectedTabId`
- **forgenv-testing** — `testForgeNavigator`, stack assertions, result helpers (Turbine-ready)

### Changed

- `SavedNavigatorState` version **2** (adds `selectedTabId`; v1 payloads still decode)
- `rememberForgeNavigator` / `rememberSaveableForgeNavigator` accept tabs, interceptors, deep link parser

## [1.0.0] — 2026-07-15

### Published

Maven Central group **`studio.forgenav`** (domain **`forgenav.studio`**):

```text
studio.forgenav:forgenv-core:1.0.0
studio.forgenav:forgenv-compose:1.0.0
studio.forgenav:forgenv-syncforge:1.0.0
```

GitHub tag `v1.0.0` · publish + verify workflows · artifacts validated on repo1.maven.org.

### Added

- **forgenv-core** — type-safe navigation (`Route`, `ForgeNavigator`, `BackStack`), deep links (`DeepLinkParser`), multiplatform lifecycle, MVI (`ForgeViewModel`, `MviViewModel`), offline-first ports (`SyncEngine`, `Outbox`, `ConflictResolver`, `SyncAwareState`, optimistic update tracker), saved backstack state (`RouteCodec`, `saveState` / `restoreState`).
- **forgenv-compose** — `ForgeNavHost`, nested host, navigator CompositionLocal, transitions, predictive/system back, `SyncStatusIndicator`, `PendingOperationsBadge`, `OfflineBanner`, `ConflictResolutionDialog`, preview helpers, effect collectors.
- **forgenv-syncforge** — typed SyncForge adapters (`ForgeNavSync.fromSyncManager`, `SyncForgeEngineAdapter`, `SyncForgeMappers`), optional local composite SyncForge, **`LocalSyncForgeLoop`** (real SyncManager + outbox + loopback transport + conflict simulation).
- **sample-android** / **sample-desktop** / **sample-ios** — task list demo on the real sync loop.
- Docs: living README, `docs/REQUIREMENTS.md`, `docs/MAVEN_PUBLISH.md`, `docs/RELEASE.md`.

### Targets

- Android, iOS (frameworks), JVM (Desktop). wasmJs profile documented as experimental.

## [0.9.0] — Previews, testing utilities, performance hardening

See README §3 Version History.

## [0.8.0] — iOS + full CMP support

## [0.7.0] — Conflict resolution UI + pending operations

## [0.6.0] — SyncForge integration module

## [0.5.0] — Compose integration (`ForgeNavHost`)

## [0.4.0] — Offline-aware concepts

## [0.3.0] — Deep linking with serialization

## [0.2.0] — MVI base classes

## [0.1.0] — Core type-safe navigation
