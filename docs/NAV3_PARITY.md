# ForgeNav ↔ Navigation 3 functional parity backlog

Living inventory of everything we can add so ForgeNav **navigation** is as functional as **Jetpack Navigation 3 (Nav3)** for real apps — without becoming a line-by-line clone of Google’s library.

**Audience:** maintainers  
**Status:** Phase A implemented on main (post-1.0.0 unreleased)  
**Last updated:** 2026-07-15

---

## 1. How to read this document

| Term | Meaning |
|------|---------|
| **Nav3** | Jetpack Navigation 3 (Compose-first): owner-managed back stack, `NavEntry` / entry provider, `NavDisplay`, adaptive layouts, saveable back stack, decorators |
| **ForgeNav (nav)** | `forgenv-core` navigation + `forgenv-compose` host (not MVI/sync modules) |
| **Parity** | Same *capabilities* for product navigation (stacks, deep links, panes, results, back) — not identical APIs |
| **P0 / P1 / P2 / P3** | Priority: must / should / nice / platform-deep |
| **Non-goal** | Explicitly out of scope even for “Nav3-level” ambition |

**Strategic rule:** close gaps that offline-first **and** multiplatform Compose apps need. Do **not** reimplement Android-only Nav3 scenes 1:1.

---

## 2. What Nav3 is (capability model)

Nav3 (stable Compose navigation line) centers on:

1. **Back stack as state** — you own a list of keys; navigate = add/remove  
2. **Entries** — key → composable content (`NavEntry` / entry provider)  
3. **Display** — `NavDisplay` (successor mental model to `NavHost`) renders the stack  
4. **Decorators** — saveable state, view models, etc. per entry  
5. **Adaptive layouts** — list–detail / multi-pane via scenes or layout APIs  
6. **System back** — integrated with predictive back  
7. **Persistence** — remember/saveable back stack across config change / process death  
8. **Deep linking** — Android-centric entry from intents / links (often with companion tooling)

JetBrains also ships / tracks **CMP-facing Navigation 3** support for multiplatform Compose — parity work should stay **KMP-clean**.

---

## 3. What ForgeNav navigation has today (v1.0.0)

### Core (`forgenv-core`)

| Capability | API / notes | Nav3 analog |
|------------|-------------|-------------|
| Typed destinations | `Route` + app sealed `@Serializable` hierarchy | Nav keys / typed routes |
| Back stack | `BackStack` / `BackStackSnapshot` as `StateFlow` | Owner-managed back stack list |
| Navigate / replace / pop | `ForgeNavigator.navigate`, `replace`, `popBackStack` | push / pop list ops |
| Pop to / pop until | `popBackStack(routeKey)`, `popUntil` | popUpTo-ish |
| Reset | `reset` | clear + set |
| Nested stacks | `NavGraph.children`, `navigateNested`, `popNested`, `nestedBackStack` | multiple stacks (basic) |
| Entry identity | `NavEntry.id` | entry identity |
| Presentation modes | `PresentationStyle.Screen / Dialog / BottomSheet` | scenes / dialog destinations (basic) |
| Metadata | `RouteMetadata` (singleTop, clearBackStack, extras) | nav options (partial) |
| Deep link parse | `DeepLinkParser` patterns + serialization | deep link matching (common) |
| Deep link apply | `handleDeepLink` + deferred until `onStart` | intent → navigate (partial) |
| Save / restore | `RouteCodec`, `SavedNavigatorState`, `saveState` / `restoreState` | saveable back stack |
| Events | `NavEvent` shared flow | observers / analytics hooks |
| Config | `NavigatorConfig` (max stack, deep link defer, strict graph id) | — |

### Compose (`forgenv-compose`)

| Capability | API / notes | Nav3 analog |
|------------|-------------|-------------|
| Host | `ForgeNavHost` / `ForgeNavHostContent` | `NavDisplay` |
| Nested host | `NestedForgeNavHost` | multi-stack UI |
| Ambient navigator | `LocalForgeNavigator` | composition locals |
| Remember | `rememberForgeNavigator` | remember controllers |
| Saveable remember | `rememberSaveableForgeNavigator` | `rememberNavBackStack`-class APIs |
| Transitions | `NavTransitions` (slide/fade/vertical/none) | animated content |
| System / predictive back | `ForgeBackHandler` (Android PredictiveBackHandler) | system back integration |
| Typed host | `ForgeNavHostTyped` | entry provider when |

### Intentionally *not* nav (keep separate)

MVI, optimistic updates, sync ports, SyncForge adapters — **do not** fold into Nav3 parity work.

---

## 4. Gap inventory (everything we can add)

Legend: **Status** = missing | partial | done

### 4.1 Back stack model & operations

| ID | Capability | Status | Notes / target API sketch | Priority |
|----|------------|--------|---------------------------|----------|
| N-BS-01 | Explicit multi back stack registry | done | Nested + tabs via `TabSpec` / `selectedTabId` | P0 |
| N-BS-02 | Tab / bottom-nav controller | done | `TabSpec` + `TabNavHost` | P0 |
| N-BS-03 | PopUpTo with inclusive + saveState | done | `NavOptions.popUpToRouteKey` / `popUpToInclusive` (+ save flags) | P0 |
| N-BS-04 | Launch single top (instance equality policy) | done | `singleTop` / `launchSingleTop` equality on route | P0 |
| N-BS-05 | Clear back stack / clear task | done | `NavOptions.clearBackStack` / metadata | P1 |
| N-BS-06 | Pop multiple / pop count | done | `popBackStack(count)` | P2 |
| N-BS-07 | Replace entire stack with list | done | `setBackStack(routes)` | P1 |
| N-BS-08 | Stack size limits & overflow policy | partial | `maxBackStackSize` trims; make policy pluggable (drop oldest / refuse) | P2 |
| N-BS-09 | Immutable public stack ops only via events | partial | Prefer single writer; document concurrency | P2 |
| N-BS-10 | Graph-scoped start destinations | done | Tab / nested stacks init with start routes | P0 |
| N-BS-11 | Floating / independent stacks (flows) | missing | Auth flow stack, modal flow stack separate from main | P1 |
| N-BS-12 | Stack snapshot diff / transactional navigate | missing | Batch navigate + single recomposition | P2 |

### 4.2 Entries, content providers, lifecycle

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-EN-01 | Entry provider DSL | partial | `when (route)` in host; add `entryProvider { entry<Home> { } }` | P1 |
| N-EN-02 | Per-entry SaveableStateHolder | done | `SaveableStateProvider(entry.id)` in host | P0 |
| N-EN-03 | Per-entry ViewModel store owner | missing | `ForgeViewModel` already manual; add entry-scoped VM store | P1 |
| N-EN-04 | Entry lifecycle callbacks | partial | Navigator lifecycle; add `ON_ENTER` / `ON_EXIT` / `ON_RETURN` per entry | P1 |
| N-EN-05 | Retained entries off-screen | missing | Keep composition of previous entry during animation; optional retain for tabs | P1 |
| N-EN-06 | Entry decorators pipeline | missing | Open `NavEntryDecorator` chain (logging, analytics, insets) | P2 |
| N-EN-07 | Content key policy | partial | `contentKey = entry.id`; allow route-based keys | P2 |
| N-EN-08 | Visible entries API | missing | `visibleEntries: StateFlow<List<NavEntry>>` for multi-pane | P0 |

### 4.3 Adaptive layouts & multi-pane (major Nav3 win)

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-AD-01 | List–detail host | done | `ListDetailNavHost` | P0 |
| N-AD-02 | Window size class integration | done | `BoxWithConstraints` breakpoint (no WSC dep) | P0 |
| N-AD-03 | Supporting pane / third pane | missing | Optional tertiary stack | P2 |
| N-AD-04 | Pane navigation rules | done | Dual pane keeps list; compact uses stack | P0 |
| N-AD-05 | Shared selection state | missing | `selectedId` synced with detail route | P1 |
| N-AD-06 | Desktop multi-window destinations | missing | Optional secondary window (JVM) | P3 |

### 4.4 Navigation results & inter-screen contracts

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-RS-01 | Navigate for result | done | `suspend navigateForResult` → `NavResult` | P0 |
| N-RS-02 | setResult + pop | done | `setResult` + `popBackStack` | P0 |
| N-RS-03 | Typed result keys | partial | Any? payload; typed unwrap in testing helpers | P1 |
| N-RS-04 | Cancelled result (back without set) | done | `NavResult.Cancelled` | P0 |
| N-RS-05 | Multi-result / channel per entry | missing | Rare; support via entry id map | P2 |

### 4.5 Deep linking (full functional set)

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-DL-01 | Path + query arg parsing | done | `DeepLinkParser` | — |
| N-DL-02 | Build URI from route | done | `buildUri` | — |
| N-DL-03 | Deferred deep links | done | until `onStart` | — |
| N-DL-04 | Pattern priority / specificity | missing | Longest match / explicit priority | P1 |
| N-DL-05 | Multiple patterns per route | missing | aliases | P1 |
| N-DL-06 | Nested path → multi-step stack | done | `stackPrefix` / `stackRoutes` | P0 |
| N-DL-07 | Deep link into nested/tab stacks | done | `nestedGraphId` selects tab / nested | P0 |
| N-DL-08 | Android Intent helper | done | `toForgeDeepLinkUri` / `handleForgeDeepLink` | P0 |
| N-DL-09 | Android App Links docs + sample | missing | assetlinks + https patterns | P1 |
| N-DL-10 | iOS onOpenURL sample | partial | scheme in Info.plist; wire Swift → common | P0 |
| N-DL-11 | Desktop custom protocol sample | missing | | P2 |
| N-DL-12 | Deep link nav options | done | clearBackStack / singleTop / popUpTo on patterns | P0 |
| N-DL-13 | Auth gate interceptor | missing | `DeepLinkInterceptor` before navigate | P1 |
| N-DL-14 | Ambiguous match diagnostics | missing | events with candidates | P2 |
| N-DL-15 | URL encoding edge cases | partial | improve tests (UTF-8, plus, arrays) | P1 |
| N-DL-16 | Web path router (CMP web) | missing | optional module later | P3 |

### 4.6 Transitions & predictive back

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-TR-01 | Default slide/fade presets | done | `NavTransitions` | — |
| N-TR-02 | Per-route transition override | missing | metadata or entry provider | P1 |
| N-TR-03 | Shared element transitions | missing | where CMP supports | P2 |
| N-TR-04 | Predictive back progress → UI | partial | handler pops on complete; wire progress to animation | P1 |
| N-TR-05 | Predictive back for modals | partial | ensure sheet/dialog participate | P1 |
| N-TR-06 | Interruptible transitions | missing | | P2 |
| N-TR-07 | Desktop Escape / back mouse | partial | sample only; promote to API | P1 |

### 4.7 Modals, dialogs, sheets

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-MD-01 | Dialog as destination | done | Default AlertDialog + custom slot | P0 |
| N-MD-02 | Bottom sheet as destination | done | Default sheet + custom slot | P0 |
| N-MD-03 | Custom modal chrome slots | done | `dialog` / `bottomSheet` params on host | P0 |
| N-MD-04 | Multiple stacked modals | partial | list works; test + document | P1 |
| N-MD-05 | Modal vs full-screen back priority | partial | | P1 |
| N-MD-06 | Fullscreen dialog / scrim policies | missing | | P2 |

### 4.8 Arguments, type safety, validation

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-TY-01 | Serializable route args | done | kotlinx.serialization | — |
| N-TY-02 | Required vs optional args | partial | serializer defaults; document | P1 |
| N-TY-03 | Custom nav type converters | missing | enums, value classes, Instant | P1 |
| N-TY-04 | Route validation on navigate | missing | `RouteValidator` / require non-blank ids | P2 |
| N-TY-05 | Codegen / KSP for routes | missing | optional later (like Safe Args) | P3 |

### 4.9 Graph structure & conditional navigation

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-GR-01 | Nested graphs | partial | children map | P0 polish |
| N-GR-02 | Nested start destination on first entry | done | nested/tab stacks auto-init | P0 |
| N-GR-03 | Graph-level deep link base | missing | | P2 |
| N-GR-04 | Conditional start (logged in/out) | done | `StartRouteProvider` | P0 |
| N-GR-05 | Feature-module graphs | missing | include graph from modules | P1 |
| N-GR-06 | Route guards / interceptors | done | `NavigationInterceptor` chain | P0 |
| N-GR-07 | Redirect (replace with other route) | done | `InterceptResult.Redirect` | P1 |

### 4.10 State retention & process death

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-SV-01 | Stack save/restore with RouteCodec | done | | — |
| N-SV-02 | Per-entry saveable state map | done | `NavEntry.savedState` + Compose holder | P0 |
| N-SV-03 | SaveableStateHolder per entry | done | see N-EN-02 | P0 |
| N-SV-04 | Nested stacks in restore | done | | — |
| N-SV-05 | Pending deep links restore | done | | — |
| N-SV-06 | Versioned migrations of saved nav state | partial | version field; migration hooks | P2 |
| N-SV-07 | Android SavedStateRegistry without Compose | missing | Activity-level helper | P2 |
| N-SV-08 | iOS state restoration hooks | missing | | P2 |

### 4.11 System integration (Android-heavy)

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-SY-01 | Predictive / system back | done (basic) | | P1 polish |
| N-SY-02 | OnBackInvoked / edge-to-edge samples | partial | sample Activity | P2 |
| N-SY-03 | Multiple activities / task affinity | missing | rare for CMP; document “single Activity” | P3 |
| N-SY-04 | Shortcuts / pin deep links | missing | docs | P3 |
| N-SY-05 | Notifications → deep link | missing | helper + docs | P1 |

### 4.12 Compose host ergonomics

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-UI-01 | ForgeNavHost | done | | — |
| N-UI-02 | AnimatedContent transitions | done | | polish P1 |
| N-UI-03 | NavDisplay-like scene API | missing | optional advanced host | P2 |
| N-UI-04 | Scaffold / inset awareness | missing | content windows | P2 |
| N-UI-05 | BottomBar + tabs scaffold | done | `TabNavHost` | P0 |
| N-UI-06 | Animated visibility of chrome by route | missing | | P2 |
| N-UI-07 | Preview helpers | partial | `PreviewForgeNavHost` | P1 |

### 4.13 Testing & observability

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-TS-01 | Unit tests back stack / deep link / save | done | Nav3 suite + existing tests | P0 |
| N-TS-02 | TestNavigator / Turbine helpers | done | `forgenv-testing` module | P0 |
| N-TS-03 | Compose UI tests sample | missing | | P1 |
| N-TS-04 | Navigation event logging | partial | `NavEvent`; structured logger | P1 |
| N-TS-05 | Debug overlay (stack inspector) | missing | like SyncForge SF overlay | P2 |

### 4.14 Multiplatform completeness

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-MP-01 | Android host | done | | — |
| N-MP-02 | JVM desktop host | done | Escape in sample | P1 promote |
| N-MP-03 | iOS CMP host | done (sample) | polish deep links | P1 |
| N-MP-04 | wasmJs / web router | missing | experimental | P3 |
| N-MP-05 | expect/actual only at edges | done | keep | — |

### 4.15 Interop & migration

| ID | Capability | Status | Notes | Priority |
|----|------------|--------|-------|----------|
| N-IX-01 | Side-by-side with Nav3 | missing | docs: hybrid Activity | P2 |
| N-IX-02 | Migration guide from Navigation 2/3 | missing | | P1 |
| N-IX-03 | Bridge Nav3 back stack ↔ ForgeNavigator | missing | optional adapter | P3 |
| N-IX-04 | Fragment / View interop | missing | **non-goal** for KMP core | — |

---

## 5. Mapping: “Nav3 feature” → ForgeNav work items

| Nav3-style feature | Work item IDs |
|--------------------|---------------|
| Owner-managed back stack | Mostly done; N-BS-07, N-BS-12 |
| Multiple back stacks / tabs | N-BS-01, N-BS-02, N-BS-10, N-UI-05 |
| Entry provider | N-EN-01 |
| Saveable entries / decorators | N-EN-02, N-EN-03, N-EN-06, N-SV-02 |
| Adaptive list–detail | N-AD-01…N-AD-05 |
| System / predictive back | N-SY-01, N-TR-04, N-TR-05 |
| Deep linking | N-DL-04…N-DL-15 |
| Animated transitions | N-TR-02, N-TR-03 |
| Results between screens | N-RS-01…N-RS-04 |
| Scene-based layouts | N-UI-03, N-AD-* |

---

## 6. Phased delivery plan (recommended)

### Phase A — “As functional as Nav3 for standard apps” (P0)

Goal: honest answer to “why not Nav3?” for most product apps.

1. **Multi-stack / tabs** — N-BS-01, N-BS-02, N-BS-10, N-UI-05  
2. **Results** — N-RS-01…N-RS-04  
3. **List–detail adaptive** — N-AD-01, N-AD-02, N-AD-04  
4. **Per-entry SaveableStateHolder** — N-EN-02, N-SV-02  
5. **Deep link stack building + platform helpers** — N-DL-06, N-DL-07, N-DL-08, N-DL-10, N-DL-12  
6. **Modal host slots** — N-MD-01…N-MD-03  
7. **Interceptors / conditional start** — N-GR-04, N-GR-06  
8. **Testing module** — N-TS-01, N-TS-02  

### Phase B — “Nav3-comfortable power users” (P1)

- PopUpTo/saveState polish, floating flows, entry lifecycle, predictive back progress, App Links docs, per-route transitions, result typing, feature graphs, migration guide  

### Phase C — Polish & platform depth (P2–P3)

- Shared elements, decorators pipeline, debug overlay, web router, multi-window, KSP Safe Args-like  

---

## 7. Explicit non-goals (even for “Nav3 parity”)

1. **Clone Nav3 class names / scenes API 1:1**  
2. **Fragment / XML navigation graphs** as primary model  
3. **Replace Google Navigation inside Google samples**  
4. **Guarantee day-1 parity with every future Nav3 release**  
5. **Put sync/outbox semantics inside the navigator** (stay in MVI/sync modules)  
6. **iOS UIKit navigation controllers as core** (CMP host is enough)  

---

## 8. Success criteria (“as functional as Nav3”)

We call navigation **Nav3-functional** when all of the following are true:

- [x] Tabs with independent back stacks + restore  
- [x] List–detail on wide layout, single stack on compact  
- [x] Typed navigate-for-result  
- [x] Deep link can rebuild a multi-entry stack and land in a tab  
- [x] Android Intent helpers documented and sampled (iOS URL open partial — sample scheme)  
- [x] Per-entry saveable UI state survives rotation/process death  
- [x] Dialog/sheet destinations with app-owned content slots  
- [x] Predictive back + transitions don’t fight modals  
- [x] `forgenav-testing` can assert stack sequences without Compose UI  
- [x] Docs: Nav3 parity backlog + checklist (this file)  

Not required for the badge: shared elements, web router, Fragment interop.

---

## 9. Current vs target (scorecard)

| Area | Today (post Phase A) | Notes |
|------|----------------------|-------|
| Basic stack ops | Strong | |
| Multi-stack / tabs | Strong | `TabSpec` + `TabNavHost` |
| Adaptive panes | Strong | `ListDetailNavHost` |
| Results | Strong | `navigateForResult` |
| Deep links (common parse) | Strong | |
| Deep links (platform + nested stack) | Strong | stackPrefix + Android Intent |
| Entry saveable state | Strong | per-entry holder |
| Transitions / back | Good | predictive back basic |
| Testing | Strong | `forgenv-testing` |
| Offline MVI (non-nav) | Differentiator | Keep |

---

## 10. Suggested issue labels

Use GitHub labels when filing:

- `nav/backstack`
- `nav/multistack`
- `nav/adaptive`
- `nav/deeplink`
- `nav/results`
- `nav/modal`
- `nav/transitions`
- `nav/testing`
- `nav/parity-nav3`

Reference IDs from this doc (`N-BS-02`, `N-DL-06`, …) in issue titles.

---

## 11. Related docs

- [REQUIREMENTS.md](REQUIREMENTS.md) — overall library correctness  
- [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md) — distribution  
- [SyncForge](https://github.com/Arsenoal/syncforge) — sync engine companion  
- Android Nav3 overview: [Announcing Navigation 3](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html) · [navigation3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3)

---

## 12. Changelog for this document

| Date | Change |
|------|--------|
| 2026-07-15 | Initial backlog from v1.0.0 vs Nav3 capability model |
| 2026-07-15 | Phase A implemented: tabs, results, list-detail, saveable entries, deep-link stacks, interceptors, testing module |
