---
id: 8
title: "Screen inset treatment — bottom safe, top bleeds"
status: accepted
date: 2027-01-15
---

## Context

`MainActivity` calls `enableEdgeToEdge()` (mandatory on target SDK
36+), which makes the app window draw under the system bars. Every
screen's root `Column` must opt back into the safe area to keep
critical UI tappable.

The `edge-to-edge` skill's PREFERRED pattern is
`Modifier.padding(innerPadding)` on the scrollable Column — symmetric
top and bottom safe. F1app's v1 polish spec puts the §1 hero card
flush to the top of the Homepage (bleed-to-top design): the
`CountdownCard` sits at the visual top, with the system clock floating
over the card's top edge like a watermark. The §3 `CircuitCard` has a
6dp brand-accent strip that also bleeds to the top of its container.

Symmetric padding would push the §1 hero below the status bar, demoting
it from "hero" to "first list item."

## Decision

Apply only **bottom** safe-area padding to screen root Columns:
`Modifier.navigationBarsPadding()`. Top stays edge-to-edge — the §1
hero and any future hero cards bleed to the top of the screen.

## Why

- The v1 polish spec (countdown card as magazine-cover hero) requires
  bleed-to-top. Padded-from-top contradicts the spec.
- M3 `NavigationBar` (in the `Scaffold`'s `bottomBar` slot in
  `NavShell`) handles its own `navigationBars` inset internally, so
  `navigationBarsPadding()` on the screen content correctly accounts
  for the bar's full 80dp + gesture-pill inset.
- `enableEdgeToEdge()` from `ComponentActivity` auto-handles status-bar
  icon contrast (light icons on dark surface), so bleed-to-top doesn't
  introduce a legibility risk on the dark theme.
- The trade-off is real: we deviate from the skill's PREFERRED pattern.
  But the deviation is deliberate and documented here so a future
  "just add `safeDrawingPadding`" suggestion meets this ADR.

## Considered alternatives

- **`Modifier.padding(innerPadding)` symmetric** — skill's PREFERRED.
  Loses bleed-to-top; demotes §1 hero. Rejected.
- **`Modifier.safeDrawingPadding()` symmetric** — same visual result as
  above, plus includes the display cutout (unnecessary for v1 portrait
  phones). Rejected.
- **Top `statusBarsPadding()` + bottom `navigationBarsPadding()`**
  (symmetric in practice) — equivalent to symmetric
  `padding(innerPadding)`. Rejected.
- **Hide the status bar via `WindowInsetsControllerCompat`** — wrong
  direction; the system clock is a feature, not noise. Rejected.

## Consequences

- The `HomepageScreen.kt` and `ScheduleScreen.kt` root `Column`s apply
  `.navigationBarsPadding()` after `.fillMaxSize()` and before
  `.verticalScroll(...)`. No other inset modifier is needed.
- The `.padding(horizontal = Spacing.normal)` and
  `.padding(top = Spacing.normal)` modifiers stay — those are visual
  rhythm, not insets.
- Any future screen follows the same pattern: bottom safe, top bleeds.
  A future "hero card on a different surface" gets the same treatment
  by default.
- The `edge-to-edge` skill checklist's other items (predictive back,
  `enableOnBackInvokedCallback`, `isNavigationBarContrastEnforced`)
  remain tracked separately and are not in scope for this ADR.

## Cross-references

- `lode/wayfinder/f1app/tickets/21-edge-to-edge-insets-bug.md` — the
  ticket that captures this decision.
- `lode/wayfinder/f1app/homepage.md` — §1 hero bleed-to-top design.
- `lode/core/navigation.md` — `NavShell`'s `Scaffold` structure; the
  screens' inset contract.
- `edge-to-edge` skill (Step 3) — the PREFERRED pattern we deliberately
  deviate from.
