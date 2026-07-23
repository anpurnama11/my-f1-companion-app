---
id: 21
title: "Edge-to-edge insets — bottom safe, top bleeds (ADR 0008)"
type: task
status: closed
blocked_by: []
owner: "pi"
---

## Question

`MainActivity` calls `enableEdgeToEdge()` (per `lode/core/navigation.md`), but `HomepageScreen.kt` and `ScheduleScreen.kt` apply no `WindowInsets.systemBars` or `safeDrawingPadding` on their root `Column`. On a Pixel 7 with gesture nav, the last Past-list row may sit under the system gesture pill; a tap on the last card can land on the bottom nav instead of opening `RoundDetail`.

This is the only real bug in the open critique list (most other minor observations are polish).

## What's in scope (now closed)

- Apply `Modifier.navigationBarsPadding()` to the root scrollable Column of:
  - `HomepageScreen.kt`
  - `ScheduleScreen.kt`
- Verify that the bottom nav still aligns correctly with the insets.
- Verify that the last row of the Past list is not obscured on a Pixel 7 emulator (or equivalent) with gesture nav.
- Add a test or smoke check if cheap; otherwise document the manual verify.

## Out of scope

- Per-screen `Scaffold` introduction (overkill for v1).
- `enableEdgeToEdge()` itself — already called from `MainActivity`.
- IME insets — no input fields in scope for these screens.
- Predictive back gesture animation — separate ticket if needed.
- `enableOnBackInvokedCallback="true"` in the manifest — separate ticket (skill checklist, API 35+).
- `isNavigationBarContrastEnforced = false` in `MainActivity` — separate ticket (skill checklist, SDK 29+).

## Resolution (closed 2027-01-15)

**Decision: `Modifier.navigationBarsPadding()` on the root `Column` of `HomepageScreen.kt` and `ScheduleScreen.kt`. Bottom-only safe area; top stays edge-to-edge so the §1 hero card bleeds to the top of the screen.**

Per ADR 0008. The `edge-to-edge` skill's PREFERRED pattern is symmetric `Modifier.padding(innerPadding)`; F1app deliberately deviates to preserve the §1 hero's magazine-cover position (the `CountdownCard` is designed to sit at the visual top of the Homepage, with the system clock floating over the card's top edge). The §3 `CircuitCard`'s 6dp brand-accent strip also relies on bleed-to-top.

M3 `NavigationBar` (in the `Scaffold`'s `bottomBar` slot in `NavShell`) handles its own `navigationBars` inset internally, so `navigationBarsPadding()` on the screen content correctly accounts for the bar's full 80dp + gesture-pill inset. `enableEdgeToEdge()` from `ComponentActivity` auto-handles status-bar icon contrast (light icons on dark surface), so bleed-to-top doesn't introduce a legibility risk on the dark theme.

### Implementation (not yet written)

```kotlin
// HomepageScreen.kt / ScheduleScreen.kt — pattern
Column(
    modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()  // ← new; the only inset modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.normal)  // visual rhythm, NOT insets
        .padding(top = Spacing.normal),
    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
) { ... }
```

Insert `.navigationBarsPadding()` after `.fillMaxSize()`, before `.verticalScroll(...)`. Keep the existing `.padding(horizontal = Spacing.normal)` and `.padding(top = Spacing.normal)` — those are visual rhythm, not insets.

### Manual verify

Pixel 7 emulator with gesture nav. Open Schedule > Past, scroll to the last row. Confirm the last card sits above the gesture pill. Tap the last card; it should open `RoundDetail`, not the bottom nav. No Compose UI test in scope (per ticket 14 testing scope: UI tests deferred).

### Lode write-back

- **ADR 0008** — `lode/decisions/0008-screen-inset-bottom-only-top-bleeds.md` — records the decision + the rejected alternatives.
- **`lode/wayfinder/f1app/homepage.md`** — bleed-to-top note added to §1.
- **`lode/core/navigation.md`** — inset contract noted; NavShell's `Scaffold` + screens' `navigationBarsPadding()`.
- **`lode/lode-map.md`** + **`lode/wayfinder/f1app/map.md`** — ticket 21 closed; ADR 0008 indexed.

## Cross-references

- `lode/decisions/0008-screen-inset-bottom-only-top-bleeds.md` — the ADR.
- `lode/core/navigation.md` — `enableEdgeToEdge()` is called from `MainActivity`; NavShell's `Scaffold` structure.
- `lode/wayfinder/f1app/homepage.md` — §1 hero bleed-to-top design.
- `lode/tmp/f1app-critique-2027-01-15.md` minor-obs #10.
- `lode/wayfinder/f1app/tickets/22-remaining-minor-observations.md` — adjacent minor-obs batch.
