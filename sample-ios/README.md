# ForgeNav iOS sample

Compose Multiplatform sample hosting the same offline-first tasks UI as Android/Desktop.

## Layout

| Path | Role |
|------|------|
| `sample-ios/` | KMP module producing the `ForgeNavSample` framework |
| `iosApp/` | Xcode/SwiftUI shell embedding Compose |

## Requirements

- **macOS** + **Xcode** 15+ (iOS 14.1+)
- JDK 17
- This repo’s Gradle wrapper

Linux CI only *configures* iOS targets (`kotlin.native.ignoreDisabledTargets=true`); it does not compile native binaries.

## Run on simulator / device

```bash
# From repo root (optional prebuild)
./gradlew :sample-ios:linkDebugFrameworkIosSimulatorArm64

# Open Xcode
open iosApp/iosApp.xcodeproj
```

In Xcode:

1. Select the **iosApp** scheme and an iOS Simulator (e.g. iPhone 16).
2. Set your **Team** in Signing if needed (`iosApp/Configuration/Config.xcconfig` → `TEAM_ID`).
3. **Run** (⌘R).

The Xcode *Compile Kotlin Framework* phase runs:

```bash
./gradlew :sample-ios:embedAndSignAppleFrameworkForXcode
```

## What the sample demonstrates

- Type-safe navigation (`ForgeNavHost` + transitions)
- Saveable navigator (process death APIs; iOS uses composition restore)
- Real SyncForge loop via `LocalSyncForgeLoop` (outbox, offline, conflicts)
- Sync status UI (badges, offline banner, conflict dialog)

## Deep links

Android registers `forgenav://tasks/{id}`. iOS URL types can be added to `Info.plist` later; pass URI into `SampleApp(deepLinkUri = …)` from `onOpenURL` if needed.

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Framework not found | Clean build folder in Xcode; re-run Gradle embed task |
| Signing errors | Set `TEAM_ID` in `Config.xcconfig` |
| Gradle fails on SyncForge | Ensure `../syncforge` exists (composite build) |
| Blank UI | Confirm scheme uses Debug and simulator matches arm64/x64 framework |
