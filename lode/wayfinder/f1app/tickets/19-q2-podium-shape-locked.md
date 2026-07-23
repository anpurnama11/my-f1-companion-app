---
id: 19
title: "Q2 podium shape lock — text-only `InlinePodium`, no red P1 background"
type: task
status: closed
blocked_by: []
owner: "pi"
---

## Question

Should the P1 podium chip on the Schedule > Past row use `F1Primary` (red) as a background, mirroring the `LIVE` chip on §1? That would create a single visual rule across the app: `red = current/active` and `red background = the winner`. Or should the text-only `InlinePodium` (shipped 2027-01-15) stay as-is, keeping the `red = current/active` rule exclusive to LIVE state?

## Resolution (closed 2027-01-15)

**Decision: NO to red P1 background. Lock the text-only `InlinePodium`.**

### Why no red P1 background

The 2027-01-15 shape-pass produced an `InlinePodium` (single inline text
line: `P1 RUS · P2 ANT · P3 LEC`) specifically because the chip *shape*
was the broken element, not the colors. The `past-list.md` "Rejected
directions" section records:

> - P1 red chip (or any coloured background) — the chip *shape* is what
>   was broken; colour does not fix it.

Re-introducing a colored background would re-introduce the chip primitive
that was just deleted, and contradict the v3 decision that locked the
shape after three iterations.

### The locked color rule

`red = current/active (LIVE only)`. Period. No other surface uses red
as a fill or background.

- §1 `LIVE` chip: `F1Primary` background, white text. The only red fill on the app.
- Past-row `P1` chip: no special color, no fill, no background. P1 dominance is implicit (left-to-right scan order).

### Lode write-back

- **ADR 0007** — `lode/decisions/0007-podium-shape-locked.md` — records the decision + the rejected alternative.
- **`lode/wayfinder/f1app/past-list.md`** "Visual treatment (locked)" section — add a one-line confirmation that the no-red-P1 rule is part of the locked treatment.

## Cross-references

- `lode/decisions/0007-podium-shape-locked.md` — the ADR.
- `lode/wayfinder/f1app/past-list.md` — the locked visual treatment.
- `lode/wayfinder/f1app/tickets/03-schedule-tab-and-round-detail.md` (in `lode/plans/f1app-build/`) — current Past-row implementation.
- `lode/tmp/f1app-critique-2027-01-15.md` — the critique document, Q2 entry.
