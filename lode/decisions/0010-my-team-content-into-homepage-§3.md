---
id: 10
title: "Favorites management lives on Homepage §3 — no separate My Team tab"
status: accepted
date: 2026-07-25
---

## Context

F1app v1 ships a 4-tab bottom nav: Homepage, Schedule, Leaderboard, My
Team. The My Team tab is a settings surface — three favorite slots
(Driver 1, Driver 2, Constructor) and a `ModalBottomSheet` picker —
used a handful of times in the app's lifetime (configure once, tweak
occasionally when drivers change teams or the user changes who they
follow). Homepage §3 already renders a read-only favorites summary
using the same `FavoritesCache`, so the data is on both surfaces.

The casual-fan product principle (PRODUCT.md) is "widget-first, then
app-as-backing-surface." The widget surfaces "when is the next
session" without an app open; the app exists to back the widget and
answer "what happened." For this audience, the My Team tab is an
over-built settings surface for a configuration the user performs
rarely. The prototype in
`lode/wayfinder/f1app/my-team-on-homepage.md` explored three variants
for folding My Team into Homepage §3.

## Decision

**Variant A wins.** §3 is the existing compact preview card (one
`Card` containing three rows). Each row is tappable; tapping a row
opens the picker (the same `ModalBottomSheet` listing current
standings, with the "Already selected" disabled state) for that slot
directly. The "Change" label on the right of each row is the
affordance. No mode toggle.

Concretely, the 4th tab is gone:

- The `Route.MyTeam` `data object` is removed from `Routes.kt`.
- The `My Team` `NavigationBarItem` is removed from `NavShell.kt`.
- `Route.homepageTabs` becomes a 3-entry list (Homepage, Schedule,
  Leaderboard).
- `MyTeamScreen.kt`, `MyTeamViewModel.kt`, the `myTeamViewModelFactory`
  function, the picker composable (moved to the Homepage feature), and
  any `feature/myteam/` tests are deleted.
- The `onPickFavorites: () -> Unit` parameter on `HomepageScreen` is
  removed; the Homepage no longer navigates to a separate management
  surface.
- The "Pick favorites" `Button` CTA in the empty state is removed.
  The empty state is three placeholder rows inside one card, each
  tappable to pick that slot.
- `FavoritesCache` (DataStore + atomic `edit`) is unchanged — it
  remains the source of truth. The picker writes to it via the
  existing `setDriver1` / `setDriver2` / `setTeam` methods.
- The prototype file `feature/homepage/prototype/MyTeamInlinePrototype.kt`
  and the `NavShell.kt` one-line swap are deleted at build time.
- The two `private` → `internal` visibility changes on
  `Section1Countdown` and `Section2Season` in `HomepageScreen.kt`
  stay — they're an unrelated improvement and keep §1+§2 reuseable
  from other surfaces (and tests).

## Why

- **A keeps §3 in the *results* family** with §1 (countdown) and
  §2 (season aggregates). The page-center-of-gravity stays on the §1
  hero, and the homepage reads as one coherent "what's happening"
  surface, not a settings page that happens to also have a hero.
- **The "Change" label is enough affordance.** A's per-row
  "Choose" / "Change" label is the same primary-color treatment used
  elsewhere in the app for tappable actions; it's discoverable
  without being intrusive. The empty state's three placeholder rows
  are themselves the prompt — there's no need for a `Button` CTA
  to teach the affordance.
- **A is minimal.** No new mode state, no `rememberSaveable` toggle,
  no second visual treatment that the user has to learn. The user
  taps what they see; the picker that opens is the same one My Team
  used.
- **Variant C would fight the §1 hero.** C is "always editable,
  three separate cards" — it promotes §3 to the *settings* family
  and pushes the page-center-of-gravity away from the hero. ADR 0008
  commits the app to bleed-to-top + magazine-cover §1; C contradicts
  that. C only works if you also rethink the homepage composition,
  which is a different product than v1 describes.
- **Variant B is a hybrid that costs more than it gives.** B is
  "edit-mode toggle" — explicit `Edit` / `Done` button top-right,
  default state is the read-only preview. For a surface the user
  touches a handful of times per season, the mode-switch cost
  (extra tap for every change, state to remember) is high relative
  to the benefit (slightly lighter default visual). B is the
  natural fallback if A's discoverability fails in field testing,
  but A is the right starting point.

## Considered alternatives

- **Variant A — Inline tap-to-pick** (chosen). Compact preview
  rows; tap to pick; "Change" label as affordance.
- **Variant B — Edit-mode toggle** (rejected). Mode-switch cost
  for a low-use surface. Natural fallback if A's discoverability
  fails.
- **Variant C — Always editable** (rejected). Three separate
  cards promote §3 to the settings family; fights §1 hero
  treatment. Only viable if the whole homepage composition is
  rethought.
- **Keep the 4-tab shape** (rejected). Status quo. The My Team
  tab doesn't earn its slot for a casual fan who configures
  favorites once; the 4th tab is overhead for an already-bursty
  usage pattern.

## Consequences

- The bottom nav has 3 tabs, not 4. The widget-first product
  principle strengthens: the app exists to back the widget and
  answer "what happened" — three tabs (Homepage, Schedule,
  Leaderboard) cover that surface cleanly.
- The `Route.MyTeam` route and its `entry<Route.MyTeam>` block in
  `NavShell.kt` are removed. ADR 0004 (multi-backstack) still
  applies to the remaining 3 tabs.
- The picker composable moves from `feature/myteam/` to
  `feature/homepage/` (or a shared `feature/picker/` location if
  other surfaces ever need it). The picker continues to write to
  `FavoritesCache` directly; no new `ViewModel` is required for
  the write path because the picker is invoked from a composable
  that already has access to the cache via `Wiring`.
- The Homepage `HomepageViewModel` does not need to expose
  write methods — the picker is rendered outside the VM tree
  and writes directly. This keeps `SectionUiState` → VM
  (ADR 0002) one-directional.
- The empty state for §3 changes: from "No favorites selected" +
  "Pick favorites" `Button` to three placeholder rows inside one
  card, each tappable. This is a small visual change that removes
  one `Button` from the homepage and replaces it with three
  rows — net lighter, not heavier.
- `lode/my-team/summary.md` is marked superseded and points to
  this ADR + the wayfinder + build tickets.
- `lode/summary.md` may want a one-line note that the app is
  3-tab, not 4-tab. This is a follow-up, not in scope for this
  ADR.
- The throwaway prototype `MyTeamInlinePrototype.kt` and the
  `NavShell.kt` one-line swap are deleted at build time. Until
  then, the prototype is the running app.

## Cross-references

- Research: `lode/wayfinder/f1app/my-team-on-homepage.md` — the
  prototype, the three variants, the A-vs-C frame insight.
- Wayfinder ticket: `lode/wayfinder/f1app/tickets/24-favorites-on-homepage.md`
  — the closed planning decision.
- Build ticket: `lode/plans/f1app-build/tickets/11-favorites-on-homepage.md`
  — the actual code work, status `ready`.
- Related ADRs: ADR 0008 (bleed-to-top — what C would fight),
  ADR 0004 (multi-backstack — still applies to 3 tabs).
- Related tickets: 12 (favorites picker UX + storage — the
  picker shape), 18 (§3 favorites shape — the locked destination
  that this ADR doesn't change in shape, only in tappability).
- Supersedes build: 05 (`my-team-tab-and-favorites-picker`,
  shipped) — the in-tab My Team management that 05 built. The
  `FavoritesCache` storage layer from 05 is unchanged.
- Superseded topical file: `lode/my-team/summary.md` — marked
  superseded at build time.
