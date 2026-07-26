# 0013 — DriverDetail Compare card = "vs teammate"

**Status: accepted**

The "Compare" card on the redesigned `DriverDetail` screen
(wayfinder ticket 29 / build ticket 14) shows the driver compared
against their **teammate** — the other driver on the same
constructor. Rejected: "vs another driver" (would require a driver
picker/selection flow, which is the dropped Driver Comparison
screen per the map's Out of scope section).

The card surfaces one teammate stat — current championship position
and points (e.g. Antonelli's page shows "George Russell — P2 · 198
pts"). It uses data already on the joined
`/current/drivers-championship` payload (the championship entry's
`teamId` keys to a second `driverId` in the same championship
list). No new screens, no new data sources, no new use case joins.

The card is **driver-only**: `TeamDetail` does not get a Compare
card per ticket 29's scope. Team vs team comparison is a different
product decision (compare against championship neighbor, historical
rival, no teammate-equivalent exists) and is a follow-up if
desired.

## Why vs teammate (and not vs another driver)

- **Resurrects nothing.** "Vs another driver" requires a driver
  picker / selection flow. The Driver Comparison screen was
  explicitly dropped by the user (map's Out of scope). Wiring
  the picker as a card on the detail page would re-introduce the
  same UX, just with a different entry point.
- **No new data sources.** The teammate's current standing is
  already on the joined `/current/drivers-championship` payload.
  Every row has `driverId` + `teamId`; filtering the list to the
  same `teamId` and excluding the current `driverId` gives the
  teammate in one map step.
- **Natural F1 mental model.** The most common driver comparison
  in F1 discourse is "your driver vs their teammate" — same car,
  same garage, same race weekends. It's the apples-to-apples
  comparison.
- **No selection state.** The card is a static row, not a target
  of user input. No rememberSaveable for the picked driver, no
  deep-link target, no analytics.

## Edge cases (handled in build ticket 14)

- **One-car team** (defensive — never in F1 in practice): the
  card hides. The screen's content is unaffected.
- **Reserve / substitute driver** (mid-season swap, one-off
  third driver): if the joined championship payload has exactly
  one row for the constructor (the current driver only — the
  swap is too new to reflect), the card hides. The current
  driver is the only data point.
- **Missing teammate standing** (championship entry has no
  `position` / `points` for the teammate): the card shows the
  teammate's name and a dash for the stat line ("George
  Russell — —"), not a thrown exception.
- **No constructor match** (driver's `teamId` does not match any
  other row's `teamId` — a stale cache edge case): the card
  hides. The screen's content is unaffected.

The card is never the source of a page-level error. A missing or
ambiguous teammate degrades the card, not the screen.

## Considered options

- **Vs teammate (chosen)** — driver-detail → joined
  `/current/drivers-championship` payload → filter by
  `teamId` → exclude self → one row → static card. Zero new
  surface, zero new data source.
- **Vs another driver (rejected)** — driver picker / selection
  flow / deep-link target / rememberSaveable state / analytics.
  Resurrects the dropped Driver Comparison feature. The user's
  earlier drop decision applies.
- **Vs championship leader (rejected)** — compares to P1, not
  to a specific driver. Less informative (the leader changes
  week to week) and doesn't match the F1 fan mental model.
- **No Compare card at all (rejected)** — the screenshot
  shows the card. Removing it would be a design regression.
- **Compare card on TeamDetail (rejected, out of scope for this
  ADR)** — team vs team is a different product decision
  (championship neighbor? historical rival? no teammate
  equivalent). Tracked as a follow-up if desired.

## Cross-references

- [Wayfinder ticket 29](../wayfinder/f1app/tickets/29-driver-team-detail-ui-rewrite.md) —
  planning ticket; closed in the resolution that produced build
  ticket 14 and this ADR.
- [Build ticket 14](../plans/f1app-build/tickets/14-driver-team-detail-ui-rewrite.md) —
  implementation contract; the use case join that resolves the
  teammate row lands here.
- [ADR 0012](0012-gap-f-detail-page-data-sources.md) — the data
  source split (F1DB build-time + Wikipedia REST + f1api.dev).
  The Compare card uses f1api.dev data only; no impact on the
  F1DB / Wikipedia join.
- Map's Out of scope section: the Driver Comparison screen is
  dropped; this ADR's lock is consistent with that drop.
