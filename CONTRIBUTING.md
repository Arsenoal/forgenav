# Contributing to ForgeNav

Thank you for helping make offline-first KMP navigation better.

## Development setup

- JDK 17+
- Android SDK 35 (for `sample-android` / Android targets)
- Optional: Xcode for iOS framework builds (macOS)

```bash
./gradlew :forgenv-core:jvmTest
./gradlew :forgenv-compose:compileKotlinJvm
./gradlew :sample-desktop:run
```

## Project layout

| Module | Responsibility |
|--------|----------------|
| `forgenv-core` | Routes, navigator, MVI, sync ports |
| `forgenv-compose` | `ForgeNavHost`, status UI, previews |
| `forgenv-syncforge` | SyncForge / outbox adapters |
| `sample-android` | Android demo |
| `sample-desktop` | Desktop demo |

## Guidelines

1. **KMP-first** — new APIs land in `commonMain` unless they are truly platform-specific.
2. **No hard engine coupling** — sync engines plug in via `SyncEngine` / `Outbox` / `ConflictResolver`.
3. **Binary compatibility** — treat public API as stable on `main` after 1.0; prefer deprecations.
4. **Tests** — unit-test navigators, reducers, deep links, and optimistic rollback paths.
5. **Style** — Kotlin official code style; meaningful names over abbreviations.

## Pull requests

- Keep PRs focused (one concern per PR when practical).
- Include tests for bug fixes.
- Update `README.md` / API docs when behavior changes.
- Do not force-push shared branches without coordination.

## Reporting issues

Include:

- ForgeNav version / commit
- Targets affected (Android / iOS / JVM / Web)
- Minimal repro (route hierarchy, intent sequence, sync status)
- Whether SyncForge (or another engine) is in the stack

## Code of conduct

Be respectful. Assume good intent. Harassment is not tolerated.
