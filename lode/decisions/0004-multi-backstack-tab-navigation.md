# 0004 — Multi-backstack tab navigation (revision 2)

**Status:** accepted  
**Date:** 2027-01-15  
**Supersedes:** revision 1 (single `NavBackStack` with `clear()` on tab switch)

## Context

The original NavShell used one shared `NavBackStack` and called `backStack.clear()` +
`backStack.add(dest.route)` on tab switch. This destroyed the previous tab's Nav3
entry, which cleared its `ViewModelStoreOwner`. Each tab switch caused a full data
re-fetch because ViewModels were recreated from scratch.

The user reported: "whenever I change tab from home to schedule back to home, it
seems both tab re-request data."

## Decision

Replace the single shared backstack with one persistent `NavBackStack<NavKey>` per
top-level tab (Homepage, Schedule, Leaderboard, MyTeam). Switching tabs only changes
which stack `NavDisplay` renders. Each tab's entries are decorated with both:

- `rememberSaveableStateHolderNavEntryDecorator` — scopes `rememberSaveable`
  composable state per entry per tab.
- `rememberViewModelStoreNavEntryDecorator` — scopes `ViewModel` instances per
  entry per tab, so ViewModels survive tab switches.

The new classes:
- `NavigationState` — holds `Map<Route, NavBackStack<NavKey>>` + current tab.
- `rememberNavigationState()` — composable factory for `NavigationState`.
- `Navigator` — `navigate(route)` dispatches tab switch vs within-stack push;
  `goBack()` handles exit-through-home (back on non-start-tab root → switch to
  start tab; back on start-tab root → exit app).

## Why

- Tab switch data re-fetch is a correctness bug (wasteful network calls, visible
  loading spinners), not a cosmetic issue.
- The multi-backstack pattern is the official Navigation 3 recipe (see
  `android docs search "Navigation 3 multiple backstack"`).
- Minimal surface change: `NavShell.kt` rewritten, new `NavigationState.kt` added,
  one dependency added (`lifecycle-viewmodel-navigation3:2.11.0`).

## Considered alternatives

- **Keep single backstack, use WhileSubscribed(5_000) only** — doesn't help because
  the ViewModel is destroyed, not just unsubscribed.
- **Keep entries in the backstack permanently** (never clear, just add/move) —
  breaks detail route push/pop because routes from different tabs would interleave.
- **Cache data at the Wiring layer** — works but leaks the caching concern into the
  composition root; each tab should own its ViewModel lifecycle.
- **Retain ViewModels at Activity scope** — never clears ViewModels, leaks memory
  for detail pages the user left hours ago.

## Known interaction: `Lazily` upstream × multi-backstack

Multi-backstack preserves the `ViewModelStore` of a tab's root entry
across transient pushes (e.g. pushing `RoundDetail` onto the Schedule
tab's stack). The Schedule `ViewModel` instance survives the push,
so its `StateFlow` stays alive — but **only if its cold upstream is
also alive**. The data-layer `SharingStarted` choice controls this:

- `WhileSubscribed(5_000)` cancels the upstream 5s after the last
  subscriber leaves; re-subscribe after that grace fires a fresh
  `onStart { warmUp() }` → another `/current` call. This is the
  "back from Round detail re-fires Schedule" bug (Round reads
  routinely exceed 5s).
- `Lazily` keeps the upstream alive for the entire `viewModelScope`
  lifetime, so re-subscribe always reads the cached `StateFlow`
  value. **F1app's policy (Jan 2027):** all three screen VMs
  (`HomepageViewModel`, `ScheduleViewModel`, `RoundViewModel`) use
  `Lazily` for their `stateIn`. Safe because the data layer is
  server-cached — see
  [lode/practices.md §"`SharingStarted` policy"](practices.md) and
  the [terminology.md "Init-less ViewModel" entry](terminology.md).
  Regression coverage in
  `ScheduleViewModelBackFromDetailTest` and
  `RoundViewModelResubscribeAfterTimeoutTest`.

## Key detail: decorator order

`rememberSaveableStateHolderNavEntryDecorator` must come **before**
`rememberViewModelStoreNavEntryDecorator` in the decorator list so the saveable-state
holder wraps the VM store. Swapping them silently breaks `SavedStateHandle` / UI state
restoration on process death.

## Tests

- Build passes (`assembleDebug`).
- All 24 JVM unit tests pass (`testDebugUnitTest`).
- No instrumentation test yet for the multi-backstack behavior (e.g. asserting
  ViewModel instance identity across tab switches).
