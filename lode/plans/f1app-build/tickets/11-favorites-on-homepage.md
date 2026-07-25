---
id: 11
title: Favorites on Homepage §3 (remove My Team tab)
type: task
status: ready
blocked_by: [24]
owner: ""
---

# 11 — Favorites on Homepage §3 (remove My Team tab)

**What to build:** fold favorites management into Homepage §3 per the
locked decision in wayfinder ticket 24. The §3 favorites card (variant
A: one card, three tappable rows) is the management surface — tapping
any row opens the picker (the existing `ModalBottomSheet` listing
current standings with the "Already selected" disabled state) for
that slot. The "Change" label on the right of each row is the
affordance.

Concretely:

- Remove `Route.MyTeam` from `Routes.kt`; update `Route.homepageTabs`
  to a 3-entry list (Homepage, Schedule, Leaderboard).
- Remove the `My Team` `NavigationBarItem` from `NavShell.kt` (the
  `TopLevelDestination` enum loses the `MyTeam` entry; the bottom nav
  has 3 tabs).
- Delete `feature/myteam/MyTeamScreen.kt`,
  `feature/myteam/MyTeamViewModel.kt`, the `myTeamViewModelFactory`
  function, and the `feature/myteam/` test files. The directory is
  removed.
- Move the picker composable from `feature/myteam/MyTeamScreen.kt`
  (the private `PickerContent` / `PickerList` / `PickerRow` / `PickerSlot`
  helpers and the `DriverSlot` enum) into `feature/homepage/`
  (recommended: `feature/homepage/FavoritesPicker.kt`). Expose
  what's needed for tests; private internals stay private.
- Update `HomepageScreen` §3: rows are tappable; tap opens the picker
  for that slot; empty state is three placeholder rows inside one
  card (no `Button` CTA). Remove the `onPickFavorites: () -> Unit`
  parameter and the `FavoritesSection` "Pick favorites" `Button`
  branch.
- The picker writes to `FavoritesCache` directly via the existing
  `setDriver1` / `setDriver2` / `setTeam` methods (now invoked from
  the picker, not from `MyTeamViewModel`).
- Revert the `NavShell.kt` one-line prototype swap back to
  `HomepageScreen(onPickFavorites = ...)` call (which is itself
  removed — `HomepageScreen` takes no parameters after this).
- Delete `feature/homepage/prototype/MyTeamInlinePrototype.kt` and
  the `prototype/` directory.
- The two `private` → `internal` visibility changes on
  `Section1Countdown` and `Section2Season` in `HomepageScreen.kt`
  are reverted (not preserved) — they were only there to enable
  the prototype's reuse of the real §1+§2 as backdrop.

**Blocked by:** 24 — planning decision locked (variant A wins).

**Status:** ready

## Done when

- [x] `MyTeamInlinePrototype.kt` and the `prototype/` directory
      deleted (2026-07-25, ahead of build — at prototype-teardown
      time)
- [ ] `NavShell.kt` reverted: no prototype swap; the `My Team`
      `TopLevelDestination` removed
- [ ] `Route.MyTeam` removed from `Routes.kt`;
      `Route.homepageTabs` is a 3-entry list
- [ ] `feature/myteam/MyTeamScreen.kt`,
      `feature/myteam/MyTeamViewModel.kt`,
      `myTeamViewModelFactory` function, and the `feature/myteam/`
      test files deleted; the directory removed
- [ ] Picker composable moved into
      `feature/homepage/FavoritesPicker.kt` (or similar); writes to
      `FavoritesCache` via the existing `setDriver1` / `setDriver2` /
      `setTeam` methods
- [ ] `HomepageScreen` §3: rows are tappable; tap opens the picker
      for that slot; empty state is three placeholder rows (no
      `Button` CTA); `onPickFavorites` parameter removed
- [ ] `lode/summary.md` updated to reflect 3-tab shape (one-line
      edit)
- [ ] `lode/my-team/summary.md` marked superseded with a pointer
      to ADR 0010 + this ticket
- [ ] `FavoritesCache` tests pass; `HomepageViewModel` tests pass;
      new picker tests added (per ticket 14's testing scope)
- [ ] `./gradlew :app:compileDebugKotlin` and
      `./gradlew :app:testDebugUnitTest` both green

## Scope fix vs the original ticket text

This ticket supersedes the build-side scope of wayfinder ticket 24
and replaces the in-tab My Team management that build ticket 05
(shipped) built. The `FavoritesCache` DataStore (ticket 05's
storage layer) is unchanged — only the surface that exposes it
changes.

The two `private` → `internal` visibility changes on
`Section1Countdown` and `Section2Season` were made for the
prototype and reverted at prototype-teardown time (2026-07-25).
This ticket does not re-introduce them; if a future surface needs
internal access to §1 or §2, it can re-expose them at that time.

## Cross-references

- ADR 0010: `lode/decisions/0010-my-team-content-into-homepage-§3.md`
- Wayfinder 24: `lode/wayfinder/f1app/tickets/24-favorites-on-homepage.md`
- Research: `lode/wayfinder/f1app/my-team-on-homepage.md`
- Supersedes build: 05 (`my-team-tab-and-favorites-picker`, shipped)
- Related: ADR 0008 (bleed-to-top), ADR 0004 (multi-backstack),
  ticket 12 (favorites picker UX + storage), ticket 18 (§3
  favorites shape), ticket 14 (testing scope)
