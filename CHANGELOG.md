# Changelog

All notable changes to ForgeNav are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-07-14

### Added

- **forgenv-core** — type-safe navigation (`Route`, `ForgeNavigator`, `BackStack`), deep links (`DeepLinkParser`), multiplatform lifecycle, MVI (`ForgeViewModel`, `MviViewModel`), offline-first ports (`SyncEngine`, `Outbox`, `ConflictResolver`, `SyncAwareState`, optimistic update tracker).
- **forgenv-compose** — `ForgeNavHost`, nested host, navigator CompositionLocal, `SyncStatusIndicator`, `PendingOperationsBadge`, `OfflineBanner`, `ConflictResolutionDialog`, preview helpers, effect collectors.
- **forgenv-syncforge** — typed SyncForge adapters (`ForgeNavSync.fromSyncManager`, `SyncForgeEngineAdapter`, `SyncForgeMappers`), composite build against sibling SyncForge, **`LocalSyncForgeLoop`** (real SyncManager + outbox + loopback transport + conflict simulation), bridge helpers for custom engines.
- **sample-android** / **sample-desktop** — task list demo on the **real sync loop** (enqueue → outbox → sync → conflict), bottom sheets, deep links.
- Full living README documenting v0.1 → v1.0 architecture decisions.
- `docs/REQUIREMENTS.md` — correctness contract and integration checklist.
- **Nav transitions** — `NavTransitions` (SlideHorizontal / Fade / SlideVertical / None) via `AnimatedContent`.
- **System / predictive back** — `ForgeBackHandler` (Android `PredictiveBackHandler`); desktop Escape / Ctrl+[ in sample.
- **iOS sample** — `sample-ios` (`ForgeNavSample` framework) + `iosApp` Xcode host; see `sample-ios/README.md`.

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
