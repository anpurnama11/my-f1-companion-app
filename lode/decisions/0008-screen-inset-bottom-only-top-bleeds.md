---
id: 8
title: "Screen inset treatment — top safe at rest, scroll-under bleed"
status: accepted
date: 2027-01-15
---

## Context

`MainActivity` calls `enableEdgeToEdge()` (mandatory on target SDK
36+), which makes the app window draw under the system bars. Every
screen's root `Column` must opt back into the safe area to keep
critical UI tappable.

F1app's definition of "bleed" is scroll-under: at rest, content should clear
the status bar; after the user scrolls upward, content may pass behind the
status icons. Fixed viewport top padding prevents that scroll-under behavior,
while no top inset makes first content feel cramped against the status bar.

## Decision

Apply **bottom** safe-area padding at the shell/content boundary, and apply the
**top** status-bar inset as scrollable content. `NavShell` uses
`Scaffold(contentWindowInsets = WindowInsets(0.dp))`, then pads `NavDisplay`
with Scaffold's `innerPadding` so the bottom bar is respected while the top does
not get fixed viewport padding. Scrollable `Column` screens apply
`verticalScroll(...).statusBarsPadding()` before visual padding; LazyColumn
screens add a status-bar-height spacer as the first item.

```kotlin
Modifier
    .fillMaxSize()
    .verticalScroll(rememberScrollState())
    .statusBarsPadding() // scrolls away; not fixed viewport padding
    .padding(horizontal = Spacing.normal, top = Spacing.normal)
```

Subpages use a shared floating back button, also owned by `NavShell`. It appears
only when the active tab back stack has more than one entry **and** the active
page content has scrolled upward past a small threshold. It calls the same
`navigator.goBack()` path as system Back, and applies `statusBarsPadding()` to
the button itself so the tappable control is below status icons without pushing
the entire page down.

## Why

- The user's design definition is "safe at rest, bleed when scrolled." Putting
  the top inset inside the scroll content matches that definition.
- Fixed top padding at `NavShell`/`Scaffold` would keep every screen below the
  status bar forever and remove the scroll-under effect.
- M3 `NavigationBar` (in the `Scaffold`'s `bottomBar` slot in
  `NavShell`) handles its own `navigationBars` inset internally, so
  Scaffold `innerPadding` correctly accounts for the bar's visual height.
- A subpage back affordance is critical UI, but making every detail screen a
  padded `TopAppBar` would contradict the bleed-to-top direction. A floating
  button keeps both requirements true.
- `enableEdgeToEdge()` from `ComponentActivity` auto-handles status-bar
  icon contrast (light icons on dark surface), so bleed-to-top doesn't
  introduce a legibility risk on the dark theme.
- The trade-off is real: we still deviate from the skill's fixed symmetric
  Scaffold padding. But the deviation is deliberate and documented here so a
  future "just add `safeDrawingPadding`" suggestion meets this ADR.

## Considered alternatives

- **`Modifier.padding(innerPadding)` symmetric** — skill's PREFERRED. Makes the
  top inset fixed, so content cannot scroll under the status bar. Rejected.
- **`Modifier.safeDrawingPadding()` symmetric** — same visual result as
  above, plus includes the display cutout (unnecessary for v1 portrait
  phones). Rejected.
- **Fixed top `statusBarsPadding()` before `verticalScroll`** — clears the
  status bar at rest but never bleeds while scrolling. Rejected.
- **Hide the status bar via `WindowInsetsControllerCompat`** — wrong
  direction; the system clock is a feature, not noise. Rejected.

## Consequences

- `NavShell` owns bottom safe-area padding through Scaffold `innerPadding`; do
  not add full `safeDrawingPadding()` or fixed top padding at the shell.
- Scrollable screen roots own their top status inset as scrollable content:
  `statusBarsPadding()` after `verticalScroll`, or a LazyColumn first-item
  `windowInsetsTopHeight(WindowInsets.statusBars)` spacer.
- The `.padding(horizontal = Spacing.normal)` and
  `.padding(top = Spacing.normal)` modifiers stay — those are visual
  rhythm, not insets.
- Any future screen follows the same pattern: top safe at rest, scroll-under
  allowed after the user scrolls.
- Detail routes rely on the shared `BleedingBackButton`; do not add screen-local
  top app bars unless a route has a separate load-bearing reason.
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
