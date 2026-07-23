---
id: 17
title: "Lock Q1 (weekend schedule placement) + Q4 (homepage hero shape)"
type: grilling
status: closed
blocked_by: []
owner: "pi"
---

## Question

The Homepage has three sections today (§1 countdown, §2 season aggregates, §3 favorites). The 5-session weekend schedule is loaded (`weekendSchedule` atom in `HomepageViewModel.kt:73-78`) but not rendered. Two coupled questions:

- **Q1:** Where does the weekend schedule land? As a 4th/5th pager card on §1? As a new section between §1 and §2? As part of the §1 hero card?
- **Q4:** Is the countdown the unambiguous hero of the Homepage, with the rest of the page as scroll-fodder? Or do all three sections compete for attention?

These are coupled because Q1's answer depends on whether §1 is a single hero card or a multi-card pager.

## Resolution (closed 2027-01-15)

**Q1: One §1 hero card with the weekend schedule below the countdown.** Not a separate section, not a pager card.

**Q4: §1 is the unambiguous hero** — countdown on top, 5-row weekend schedule below, both inside one card. §2 and §3 are scroll-fodder.

### The final homepage section order

```
§1  Next race card (single hero, one Card)
     ├─ Top:    LIVE chip / countdown (2d 5h) / RACE COMPLETE
     │          (current behavior — `nextUpcoming(now)`)
     └─ Below:  5-row weekend schedule (FP1 / FP2 / Quali / Sprint / Race)
                local times, `SessionChip` + `formatStart` reused.
                Always visible (no expansion needed — rows are short).
§2  Season aggregates (circular gauge + 3 stat rows, unchanged)
§3  Favorites (shape decided in ticket 18)
```

### Why one §1 card, not §1 + §1b

The countdown and the 5-session schedule are both about *the next race*.
Splitting them fragments the "when" answer. The casual fan use case
("open Homepage once, see FP1 Fri 13:30 + Race Sun 15:00") is met in
one viewport. Casey/Jordan persona friction drops — no extra scroll,
no second section to discover.

The §1 card stays a single Compose `Card`; the 5 rows render as a
compact list below the countdown, not in a separate Card. The
`LIVE` pill and the `FP1` chip can sit above the same card chrome
without competing for visual weight.

### Why not a pager on §1

A pager on §1 would require multiple horizontally-swipeable cards
("countdown card" / "FP1 card" / "Quali card" / etc.), which:
- fragments the same data the user wants at a glance,
- hides the rest of the schedule behind a swipe,
- makes the Casey/Jordan "is it starting now?" question 2-3 swipes deep.

### What was considered and rejected

| Option | Rejection reason |
|---|---|
| Insert weekend schedule between §1 and §2 as a new section | Fragments the "when" answer across two sections. Adds a section count. |
| Insert between §2 and §3 | Demotes aggregates from §2 to §3; hero-energy on a non-hero element. |
| Pager on §1 (4-5 swipeable cards) | Hides the schedule behind a swipe. Casey/Jordan friction. |
| Single countdown card, no schedule (status quo) | P1 stays open. `weekendSchedule` atom remains loaded-but-unused. |
| Place after §3 (last) | Buries the "when" content below scroll-fodder. Casey-persona pain worse. |

### Persona coverage

- **Jordan (Confused First-Timer):** Opens the app, sees the §1 hero with countdown + 5 session times. Answers "when's FP1?" and "when's the race?" in one viewport. No jargon barrier — the schedule is just a list of times.
- **Casey (Distracted Mobile User):** Opens between meetings. The §1 hero is the only thing that matters; the rest of the page is scroll-fodder. Casey doesn't scroll past §1 unless curious.
- **Maya (Stats Nerd):** Sees the same §1 hero. The 5-row list answers "is Quali tomorrow?" without leaving the Homepage.

### Implementation notes (for the execution ticket)

- The `weekendSchedule` atom already exists in `HomepageViewModel.kt:73-78` — no new data fetch.
- The §1 `CountdownCard` composable extends to a `NextRaceCard` (or `NextRaceHero`) that wraps the existing countdown + the 5-row list. The list reuses `SessionChip` and `formatStart` helpers.
- The 5 rows are always visible (no expansion). Each row is a compact `Row` of `SessionChip · DAY · TIME` — same horizontal rhythm as the §1 GP-name row.
- No change to the §1 pull-to-refresh semantics, no change to the `LIVE` chip, no change to the `raceComplete` derivation.
- The §1 "below the fold" mention in `lode/wayfinder/f1app/homepage.md` needs updating to reflect "below the countdown, inside the same card."

## Cross-references

- `lode/wayfinder/f1app/homepage.md` — needs the §1 section description updated to "Next race card with weekend schedule below the countdown."
- `lode/wayfinder/f1app/tickets/18-section-3-favorites-shape.md` — §3 favorites shape, blocked by this ticket.
- `lode/wayfinder/f1app/tickets/16-team-accent-source.md` — `TeamColors.forId` for §3 accent.
- `lode/wayfinder/f1app/tickets/21-edge-to-edge-insets-bug.md` — edge-to-edge insets bug, independent of this ticket.
- `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md` — `weekendSchedule` atom source.
