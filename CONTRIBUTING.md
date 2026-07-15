# Contributing to ForgeNav

Thank you for helping make offline-first KMP navigation better.

## Development setup

- JDK 17+
- Android SDK 35 (for `sample-android` / Android targets)
- Optional: Xcode for iOS framework builds (macOS)

```bash
./gradlew :forgenv-core:jvmTest
./gradlew :forgenv-testing:jvmTest
./gradlew :forgenv-compose:compileKotlinJvm
./gradlew verifyReleaseSignOff
./gradlew :sample-desktop:run
```

Current library version is pinned in `gradle.properties` as `forgenav.version` (**1.2.0**).

## Project layout

| Module | Responsibility |
|--------|----------------|
| `forgenv-core` | Routes, multi-stack navigator, deep links, results, interceptors, MVI, sync ports |
| `forgenv-compose` | `ForgeNavHost`, `TabNavHost`, `ListDetailNavHost`, transitions, sync UI |
| `forgenv-syncforge` | SyncForge / outbox adapters + `LocalSyncForgeLoop` |
| `forgenv-testing` | Unit-test helpers (`testForgeNavigator`, stack assertions) |
| `sample-android` | Android demo |
| `sample-desktop` | Desktop demo |
| `sample-ios` + `iosApp/` | iOS CMP demo |

## Guidelines

1. **KMP-first** — new APIs land in `commonMain` unless they are truly platform-specific.
2. **No hard engine coupling** — sync engines plug in via `SyncEngine` / `Outbox` / `ConflictResolver`.
3. **Binary compatibility** — treat public API as stable on `main` after 1.0; prefer deprecations. Nav surface expanded in **1.1.0** (additive); **1.2.0** is transactional write-path behavior (same public navigate API).
4. **Tests** — unit-test navigators (prefer `forgenv-testing`), reducers, deep links, and optimistic rollback paths.
5. **Style** — Kotlin official code style; meaningful names over abbreviations.
6. **Nav3 backlog** — product navigation gaps live in [docs/NAV3_PARITY.md](docs/NAV3_PARITY.md) (Phase A done in 1.1.0; N-BS-12 in 1.2.0; Phase B+ open).

## Pull requests

- Keep PRs focused (one concern per PR when practical).
- Include tests for bug fixes.
- Update `README.md` / `CHANGELOG.md` / API docs when behavior changes.
- Do not force-push shared branches without coordination.

## Reporting issues

Include:

- ForgeNav version / commit (e.g. `1.2.0` or SHA)
- Targets affected (Android / iOS / JVM / Web)
- Minimal repro (route hierarchy, intent sequence, sync status)
- Whether SyncForge (or another engine) is in the stack

## Releases

See [docs/RELEASE.md](docs/RELEASE.md). Tagging `vX.Y.Z` triggers Maven Central publish.

## Code of conduct

Be respectful. Assume good intent. Harassment is not tolerated.
