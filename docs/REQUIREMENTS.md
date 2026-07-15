# ForgeNav — Requirements for Correct Operation

This document is the **contract** for ForgeNav to function correctly: what the library guarantees, what consumers must provide, what must be true at runtime, and what we still need to harden for production.

**Status:** v1.0.0 published on Maven Central (`studio.forgenav`) and verified  
**Last updated:** 2026-07-15

---

## 1. Recommended next step (do this first)

### Goal
Turn the sample’s *demo* sync path into a **real offline-first loop** so the library is proven with production-shaped data flow.

### Next step status — **DONE (local real loop)**

Composite SyncForge + typed adapters + sample real loop are implemented:

1. ~~**Composite / peer SyncForge**~~ → `settings.gradle.kts` `includeBuild("../syncforge")`
2. ~~**Typed adapters**~~ → `ForgeNavSync.fromSyncManager`, `SyncForgeMappers`, `SyncForgeEngineAdapter`
3. ~~**Feature screen loop**~~ → `LocalSyncForgeLoop` + sample `TaskViewModel`  
   (`enqueueChange` → outbox → loopback push/pull → `deferToUser` conflicts)
4. **Device smoke test** — run `sample-android` / `sample-desktop` manually
5. ~~Saved backstack state~~ → `RouteCodec` + `saveState`/`restoreState` + `rememberSaveableForgeNavigator`
6. ~~Transitions + predictive back~~ → `NavTransitions` + `ForgeBackHandler` / `PredictiveBackHandler`
7. ~~iOS sample~~ → `sample-ios` + `iosApp` (Xcode host; run on macOS)
8. ~~Maven Central publish~~ → `studio.forgenav` 1.0.0 + Publish / Verify Actions
9. Still later: device smoke, umbrella `forgenav-android` artifact  
10. Navigation depth: [NAV3_PARITY.md](NAV3_PARITY.md) (tabs, list–detail, results, deep links)

### Success criteria

- [x] Composite resolves `studio.syncforge:syncforge` to sibling project
- [x] Sample uses SyncManager outbox path (not ControllableSyncEngine)
- [x] Saved backstack API + unit tests (`SavedNavStateTest`)
- [ ] Device smoke: offline queue, sync, conflict dialog (manual)
- [ ] Device smoke: process death restore (Don't keep activities)

---

## 2. What “function correctly” means

ForgeNav functions correctly when **all** of the following hold:

| Layer | Correct behavior |
|-------|------------------|
| **Navigation** | Stack mutations are consistent; current destination matches top of backstack; pop never drops below root unless app resets intentionally |
| **Deep links** | Registered URI patterns decode to the intended `Route` and navigate with the intended metadata |
| **MVI** | Every `dispatch` eventually reduces state; effects are delivered once; cleared VMs cancel work |
| **Optimistic UI** | Tracked updates can be committed or rolled back to a known previous state |
| **Sync ports** | Status / pending / conflicts observed by VMs and UI match the bound engine |
| **Compose host** | `ForgeNavHost` renders the top screen (and modals); dismiss of dialog/sheet pops stack |
| **Lifecycle** | Deep links deferred until started are flushed on `onStart`; dispose cancels navigator resources |

If any row fails, the library (or the consumer integration) is **not** operating correctly.

---

## 3. Repository / toolchain requirements

### Required to build libraries + JVM tests

| Requirement | Minimum / notes |
|-------------|-----------------|
| JDK | **17+** |
| Gradle | Wrapper **8.14+** (included) |
| Kotlin | **2.1.x** (catalog: 2.1.10) |
| Network (first build) | To resolve Maven deps |

```bash
./gradlew :forgenv-core:jvmTest
./gradlew :forgenv-compose:compileKotlinJvm
./gradlew :forgenv-syncforge:compileKotlinJvm
```

### Required for Android sample / Android targets

| Requirement | Notes |
|-------------|--------|
| Android SDK | **compileSdk 35**, **minSdk 24** |
| `local.properties` | `sdk.dir=...` (Android Studio creates this) |
| Device/emulator | For runtime smoke, not for unit tests |

```bash
./gradlew :sample-android:assembleDebug
```

### Required for Desktop sample

| Requirement | Notes |
|-------------|--------|
| JVM 17+ | Same JDK |
| Display / desktop env | For `./gradlew :sample-desktop:run` |

### Required for iOS library compilation

| Requirement | Notes |
|-------------|--------|
| macOS + Xcode | Native targets |
| `kotlin.native.ignoreDisabledTargets=true` | Allows Linux CI without iOS compile |

### Optional peer (production offline-first)

| Requirement | Notes |
|-------------|--------|
| SyncForge **2.x** (or custom outbox engine) | Peer dependency; not required to *compile* `forgenv-core` / `forgenv-compose` |
| `forgenv-syncforge` | Required only when using adapters |

---

## 4. Module dependency graph (must stay valid)

```text
sample-*  →  forgenv-compose  →  forgenv-core
sample-*  →  forgenv-syncforge →  forgenv-core
forgenv-core     →  coroutines, serialization   (no Compose, no SyncForge)
forgenv-compose  →  CMP + forgenv-core
forgenv-syncforge→  forgenv-core (+ peer engine at app level)
```

**Invariants:**

1. `forgenv-core` must **never** depend on Compose or a concrete sync SDK.
2. Apps may use **core only** (headless / custom UI).
3. Apps that use Compose navigation must depend on **compose** (and transitively core).
4. SyncForge types must only appear at the **app** or **forgenv-syncforge adapter** boundary, not in core public API.

---

## 5. Consumer requirements (app must provide)

### 5.1 Navigation (always)

| # | Requirement | Why |
|---|-------------|-----|
| N1 | Define app routes as types implementing `Route` | Navigator is type-based |
| N2 | Prefer `@Serializable` sealed hierarchies for deep-linked routes | Parser uses KSerializer |
| N3 | Create **one** root `ForgeNavigator` (or nest deliberately) | Multiple roots diverge stacks |
| N4 | Render destinations with `ForgeNavHost` (or equivalent collector of `backStack`) | Otherwise navigation has no UI |
| N5 | Call `onStart` / `onStop` (or use `rememberForgeNavigator` lifecycle binding) | Deep-link deferral + lifecycle |
| N6 | Register deep-link patterns **before** handling cold-start URIs | Unregistered → failed link |
| N7 | Treat `routeKey` carefully if you rename classes | Default key = simple class name |
| N8 | Keep route payloads small (ids, not huge graphs) | Stack + recomposition cost |

### 5.2 MVI / ViewModels (when using MviViewModel)

| # | Requirement | Why |
|---|-------------|-----|
| V1 | Implement pure `reduce` | Deterministic state |
| V2 | Put IO/sync in `handleSideEffect` | Avoid blocking reduce |
| V3 | `clear()` ViewModels when leaving composition (or use `rememberForgeViewModel`) | Avoid leaks / zombie collectors |
| V4 | Collect `effects` with a single long-lived collector (`CollectEffects`) | One-shot delivery |
| V5 | Use `Dispatchers.Main` (or Main.immediate) for UI-bound VMs on Android/Desktop | Threading correctness |
| V6 | If using optimistic APIs, always **commit or rollback** every optimistic id | No orphan tracker entries |

### 5.3 Sync integration (when offline-first)

| # | Requirement | Why |
|---|-------------|-----|
| S1 | Provide a `SyncFacade` (or bind engine/outbox/resolver) | Status flows into VM/UI |
| S2 | Map engine status → ForgeNav `SyncStatus` | UI widgets only know ForgeNav types |
| S3 | Map outbox rows → `PendingOperation` | Badges / counts |
| S4 | Map conflicts → `ConflictInfo` and implement `apply(decision)` | Dialogs are useless without apply |
| S5 | Enqueue outbox ops in the same user action as optimistic UI | Consistency |
| S6 | On remote win / permanent failure: rollback optimistic state | Avoid lying UI |
| S7 | Implement `isOnline()` honestly | Offline banner / offline status |

### 5.4 Compose UI (when using forgenv-compose)

| # | Requirement | Why |
|---|-------------|-----|
| C1 | Compose Multiplatform / Material3 on the app classpath | Widgets need it |
| C2 | Provide content for **every** navigable route in `ForgeNavHost` | Missing branch = blank/unknown |
| C3 | Handle modal routes (`Dialog` / `BottomSheet`) content | Host only provides chrome |
| C4 | Wire conflict dialog decisions back to VM/engine | UI alone does not resolve storage |

### 5.5 Platform deep links

| Platform | Requirement |
|----------|-------------|
| Android | Intent-filter + pass `intent.data` into `handleDeepLink` |
| iOS | URL open callback → same URI string |
| Desktop | CLI / custom protocol → URI string |
| Web | Path/hash → parser pattern |

---

## 6. Library internal invariants (must not break)

These are **implementation rules** for maintainers. Breaking them breaks correctness.

### Navigation

| ID | Invariant |
|----|-----------|
| INV-N1 | Root backstack always has ≥ 1 entry after init (unless explicitly disposed/reset into invalid state — avoid empty) |
| INV-N2 | `pop()` returns false and does not empty the stack past root |
| INV-N3 | Each `NavEntry.id` is unique per push |
| INV-N4 | `singleTop` compares route equality + `routeKey` as implemented |
| INV-N5 | Nested graph ids used in `navigateNested` must exist in `NavGraph.children` or be created consistently |
| INV-N6 | Deep-link parse failure must not corrupt the stack |

### MVI

| ID | Invariant |
|----|-----------|
| INV-V1 | `dispatch` never blocks the caller on IO |
| INV-V2 | `clear()` is idempotent; after clear, no new work should be scheduled |
| INV-V3 | Effect channel consumers must not assume replay |
| INV-V4 | Optimistic rollback restores **previousState** for that update id |

### Sync ports

| ID | Invariant |
|----|-----------|
| INV-S1 | Core types are engine-agnostic |
| INV-S2 | `NoOp*` implementations are safe defaults for previews |
| INV-S3 | Adapters must not drop conflict apply failures silently without status update (app policy) |

### Compose

| ID | Invariant |
|----|-----------|
| INV-C1 | Host keys destinations by `entry.id` |
| INV-C2 | Modal dismiss calls pop (stack remains source of truth) |
| INV-C3 | `LocalForgeNavigator` only used under a provider / host |

---

## 7. Runtime configuration checklist (app launch)

Use this before calling a build “integration complete”:

### Cold start

- [ ] Navigator created with correct `startRoute`
- [ ] Lifecycle → `onStart` before or when UI is interactive
- [ ] Pending deep link from process start applied once
- [ ] ViewModels created with intended `SyncFacade` (not accidental `demo()`)

### Steady state

- [ ] Back / system back maps to `popBackStack()` where appropriate
- [ ] Sync status indicator reflects engine (not stuck on `Synced`)
- [ ] Pending badge matches outbox count
- [ ] Offline banner only when actually offline / engine says offline

### Failure paths

- [ ] Sync error surfaces as `SyncStatus.Error` (or app equivalent messaging)
- [ ] Conflict list non-empty ⇒ dialog or dedicated UI
- [ ] Retry path calls `syncNow` / `retryFailed`

### Teardown

- [ ] Activity/window destroy → navigator `dispose` / VM `clear`
- [ ] No leaked coroutines (strict mode / leak canary optional)

---

## 8. Correctness test matrix (minimum gate)

### Must pass on every PR (CI)

| Suite | Command / scope |
|-------|-----------------|
| Backstack ops | `BackStackTest` |
| Deep link parse/build | `DeepLinkParserTest` |
| MVI reduce + optimistic | `MviViewModelTest` |
| Optimistic tracker | `OptimisticUpdateTrackerTest` |
| Compile compose + syncforge JVM | Gradle compile tasks |

```bash
./gradlew :forgenv-core:jvmTest \
  :forgenv-compose:compileKotlinJvm \
  :forgenv-syncforge:compileKotlinJvm
```

### Must pass before calling “production-ready for our apps”

| Gate | What to verify |
|------|----------------|
| Android instrumentation / manual | Navigation, deep link intent, back |
| Offline loop | Queue → online → synced |
| Conflict loop | Simulate conflict → both decisions |
| Optimistic rollback | Force failure after optimistic edit |
| Process death (when implemented) | Restore stack + current screen |
| iOS (when shipping iOS) | Same flows on simulator |

---

## 9. Feature completeness vs correctness

| Capability | Required for *correct* nav/MVI? | Required for *correct* offline-first product? | v1.0 status |
|------------|----------------------------------|-----------------------------------------------|-------------|
| Type-safe navigate/pop | Yes | Yes | Done |
| Compose host | Yes (if CMP UI) | Yes | Done |
| Deep links | If app uses them | Often yes | Done (patterns registered by app) |
| MVI base | If using library VMs | Yes | Done |
| Optimistic tracker | If using optimistic APIs | Yes | Done |
| Sync ports + UI widgets | No | Yes | Done (ports + UI) |
| Real SyncForge typed adapter | No | **Yes for SyncForge apps** | Bridge only — **next step** |
| Saved state / process death | Strongly recommended Android | Yes for mobile QA | Done (`RouteCodec` + `rememberSaveableForgeNavigator`) |
| Animations | No | No | Not done |
| iOS sample app | No | If shipping iOS | Targets only |
| Maven publish | No | For external consumers | **Done** — `studio.forgenav:*:1.0.0` on Central |

**Correctness of the library core ≠ completeness of a product integration.**

---

## 10. Definition of Done — library “functions correctly” for a consumer app

A consumer app integration is **correct** when:

1. **Build:** app compiles with `forgenv-core` (+ compose/syncforge as used).  
2. **Navigate:** every user-visible screen is a registered route rendered by the host.  
3. **Back:** system/app back matches stack pop semantics.  
4. **Deep link (if any):** cold start and warm start both land on the right screen with args.  
5. **State:** intents reduce predictably; effects not lost/duplicated for snackbars.  
6. **Sync (if any):** status, pending count, conflicts match engine; optimistic UI never permanently diverges from store without a resolution path.  
7. **Lifecycle:** no crashes on rotate/background (and eventually process death).  
8. **Tests:** core CI green; app has at least one automated or scripted smoke path for the above.

---

## 11. Suggested development backlog (after next step)

1. Real SyncForge E2E (current next step)  
2. Android saved backstack state  
3. Predictive back + transitions  
4. iOS sample  
5. Publish + binary compatibility  
6. wasm sample (if web matters)  
7. DI helpers  

---

## 12. Quick reference — “what do I need installed / configured?”

**Developer machine (library work):**

- JDK 17  
- Android Studio (optional but recommended)  
- Android SDK 35 for Android modules  
- Git clone of this repo  

**App using ForgeNav offline-first:**

- KMP/CMP project  
- Dependencies: `forgenv-core`, usually `forgenv-compose`, optionally `forgenv-syncforge`  
- Route sealed hierarchy  
- Navigator + NavHost  
- Sync engine binding (SyncForge or custom implementing ports)  
- Platform deep link plumbing  

**CI:**

- JDK 17  
- `./gradlew :forgenv-core:jvmTest` (+ compile other modules)  
- Optional Android emulator job for sample  

---

## 13. Document ownership

| Audience | Use this doc for |
|----------|------------------|
| Maintainers | Invariants, CI gates, backlog order |
| App developers | Sections 5, 7, 10 |
| Reviewers | Section 8 + 6 before merging API changes |

When behavior changes, update **this file** and the root **README** version history.
