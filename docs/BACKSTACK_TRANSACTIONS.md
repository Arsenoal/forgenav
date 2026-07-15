# Back stack writes: full stack read, op/diff write (N-BS-12)

**Status:** done (shipped in **v1.2.0**)  
**Priority:** P2  
**Parity ID:** [N-BS-12](NAV3_PARITY.md)  
**Source:** community feedback — *“full backstack, diffs for writes; snapshots get ugly fast”*  
**Last updated:** 2026-07-15

---

## 1. Principle

| Layer | Model | Why |
|-------|--------|-----|
| **Read / UI / save** | Full `BackStackSnapshot` (`List<NavEntry>`) | Hosts, collectors, and process-death restore need the whole stack as truth |
| **Write** | Ops / diffs applied in one transaction | Avoid intermediate emissions, identity thrash, and multi-frame recomposition |
| **History / undo** | Do **not** keep a ring of full snapshots | Snapshots-as-write-protocol and snapshot-logs get ugly fast |

Nav3-aligned mental model:

> **The back stack is state. Navigation is mutation of that list — not a stream of replacement snapshots.**

Public API stays op-based (`navigate`, `popBackStack`, `setBackStack`, …). Internally multi-step mutations become **one** `StateFlow` emission and **one** coherent `NavEvent`.

---

## 2. What was wrong before (v1.1.0)

| Call path | Problem |
|-----------|---------|
| `setBackStack(routes)` | `reset` + N× `push` → **N+1** snapshot updates |
| Deep link `stackRoutes` rebuild | Same multi-emit pattern |
| `popBackStack(count)` | N× `pop` + N× `NavEvent.Popped` |
| `applyNavigate` with `popUpTo` then `push` | Two mutations before collectors settle |
| `trimIfNeeded` after navigate | Extra `restore` emission after the “real” navigate |

---

## 3. Implementation (v1.2.0)

### 3.1 Ops

```kotlin
sealed interface BackStackOp {
    data class Push(val route: Route, val metadata: RouteMetadata = RouteMetadata()) : BackStackOp
    data class ReplaceTop(val route: Route, val metadata: RouteMetadata = RouteMetadata()) : BackStackOp
    data class Pop(val count: Int = 1) : BackStackOp
    data class PopTo(val routeKey: String, val inclusive: Boolean = false) : BackStackOp
    data class PopUntil(val predicate: (NavEntry) -> Boolean) : BackStackOp
    data class Reset(val routes: List<Route>, val metadata: RouteMetadata = RouteMetadata()) : BackStackOp
    data class TrimToMaxSize(val maxSize: Int) : BackStackOp
}
```

### 3.2 Single apply

`BackStack.apply(ops)` folds ops over the entry list inside one `MutableStateFlow.update` and returns `BackStackApplyResult` (`previousTop`, `newTop`, `removed`, `added`, `didChange`).

Convenience methods (`push`, `pop`, `replace`, `reset`, `popTo`, `popUntil`) are single-op wrappers.

### 3.3 Navigator rewiring

| Public call | Transaction |
|-------------|-------------|
| `setBackStack` | `Reset` + `TrimToMaxSize` |
| Deep link stack rebuild | `Reset` + `TrimToMaxSize` |
| `popBackStack(count)` | `Pop(count)` → one `NavEvent.Popped` |
| `navigate` (+ popUpTo / clear) | `PopTo?` + `Push`/`Reset` + `TrimToMaxSize` |
| `replace` / `reset` | single op |

Events: keep existing shapes; **one** event per public call (approach A). No op-log persistence; save/restore still uses full `SavedNavigatorState`.

### 3.4 Explicit non-goals (still)

1. Snapshot assignment as primary public write API  
2. Undo stack of full `BackStackSnapshot`s  
3. CRDT / multi-writer patch logs  
4. UI subscribed only to diffs  

---

## 4. Acceptance criteria (met)

1. `setBackStack(listOf(A, B, C))` → **one** `backStack` emission (beyond current value)  
2. Deep link stack rebuild → **one** emission on the target stack  
3. `popBackStack(count = k)` → **one** emission and **one** `Popped` when k entries removed  
4. `navigate` with `popUpTo` + push does not publish the intermediate post-pop stack  
5. Public simple `navigate` / `popBackStack()` API unchanged for apps  
6. Save/restore still encodes full stacks  

Tests: `BackStackTest`, `ForgeNavigatorNav3Test` (Turbine single-emission cases).

---

## 5. Optional follow-ups

- [ ] Public `navigateInTransaction { }` DSL if apps need multi-step batches beyond existing APIs  
- [ ] Pluggable overflow policy inside apply (**N-BS-08**)  
- [ ] Optional `NavEvent.Transaction` for analytics fidelity  

---

## 6. Related

- [NAV3_PARITY.md](NAV3_PARITY.md) — N-BS-12, N-BS-08, N-BS-09  
- `forgenv-core/.../BackStack.kt`  
- `forgenv-core/.../ForgeNavigator.kt`  
