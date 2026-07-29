# 0007 — Podium shape locked: text-only `InlinePodium`, no red P1 background

**Status:** accepted

## Context

The Past-row podium on Schedule underwent three shape iterations in the
2027-01-15 polish pass:

1. **v1 flat chips** — three equal `Column`s of `Text` on a `RoundedCornerShape(8.dp)` clip. P1 looked exactly like P3.
2. **v2 bolder chips** — `titleLarge` Bold on P1 + full `teamName` line. Oversized chips, ugly "Mercedes For…" truncations, height asymmetry that broke the row.
3. **v3 inline text line** — single line, no container, no background, no chip shape: `P1 RUS · P2 ANT · P3 LEC`. P1 dominance implicit (left-to-right scan order).

The critique (`lode/tmp/f1app-critique-2027-01-15.md`) then asked: should
P1 use `F1Primary` (red) as a background, mirroring the `LIVE` chip on
§1? That would create a single visual rule: `red = current/active` and
`red background = the winner`.

## Decision

**No red P1 background. The text-only `InlinePodium` (v3) is final.**

The locked color rule across the app is:

> **`red = current/active (LIVE only)`.** No other surface uses red as a
> fill or background.

- §1 `LIVE` chip: `F1Primary` background, white text. The only red fill on the app.
- Past-row `P1`: no special color, no fill, no background. P1 dominance is implicit (left-to-right scan order).

## Why

1. **The chip *shape* was the broken element, not the colors.** The v1
   and v2 iterations both failed because of the chip primitive (foreign
   shape in a text-language row), not because the colors were wrong.
   Adding a colored background to v3 would re-introduce the chip
   primitive that was just deleted.
2. **Three iterations to land v3 is the receipt for cost.** Re-litigating
   shape now (by adding a red P1 background that implies a chip) costs
   another iteration cycle. The shape is locked; color does not fix it.
3. **The single visual rule `red = current/active` is cleaner than
   `red = current/active OR winner`.** Adding "winner" to the rule makes
   the rule harder to remember and harder to apply consistently.
4. **P1 dominance works without color.** The position label (`P1`)
   leading the line, the three-letter driver code (`RUS`) in full-weight
   `onSurface`, and the left-to-right scan order together carry the
   hierarchy. Adding red would compete with the `LIVE` chip's
   `F1Primary` for the same color budget.

## Considered options (rejected)

- **P1 `F1Primary` background, mirroring `LIVE` chip** — rejected.
  Reverses the v3 shape decision. Adds a second rule for the same color
  budget. Re-introduces the chip primitive.
- **P1 `F1Primary` text color only** — rejected. The `Color.kt` contract
  reserves `F1Primary` for backgrounds on dark, not text. Text uses
  `onSurface` / `onSurfaceVariant` only.
- **P1 `F1Primary` 6dp leading strip** — rejected. Implies a chip
  container that doesn't exist; the row has no leading edge.

## Consequences

- The `InlinePodium` composable in `ScheduleScreen.kt` stays
  text-only.
- The `past-list.md` "Visual treatment (locked)" section gets a
  one-line confirmation that the no-red-P1 rule is part of the locked
  treatment.
- Any future surface that wants to call out a winner uses a different
  shape (iconography, weight, position), not red. The `red =
  current/active` rule is reserved for the `LIVE` chip.

## Cross-references

- [Past-list research history](https://github.com/anpurnama11/my-f1-companion-app/issues/40) — the locked visual treatment.
- `https://github.com/anpurnama11/my-f1-companion-app/issues/49` — the
  ticket that locks this decision.
- `lode/tmp/f1app-critique-2027-01-15.md` — the critique document, Q2
  entry.
- `lode/design-system/theme.md` — the `F1Primary` color token rule.
