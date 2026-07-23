# Schedule > Past list — full podium on the list

Research output of ticket 10 (GAP-C). Source of truth for anyone implementing
the Schedule > Past tab: the list shows **full podium (P1/P2/P3)** per past
round, not just the winner. Documents which API to wrangle and how.

## Decision (closed)

**Past list shows full podium (P1, P2, P3).** The `/current` season endpoint
only serves the winner per round — there is **no bulk podium endpoint** on
f1api.dev. Full podium therefore requires fetching `/{year}/{round}/race` per
past round and taking `results[0..2]`. The round-result drilldown continues to
fetch the same `/race` call and render the full grid.

## Data confirmed against f1api.dev

Both endpoints hit live on 2026-07-16.

### `GET /current` (and `/{year}`) — full season schedule, winner only

Per-race shape (completed race, abbreviated):
```json
{
  "raceId": "australian2026", "round": 1,
  "raceName": "Formula 1 Qatar Airways Australian Grand Prix",
  "schedule": { "race": { "date": "2026-03-08", "time": "04:00:00Z" }, ... },
  "laps": 58, "circuit": { ... },
  "winner": {
    "driverId": "russell", "name": "George", "surname": "Russell",
    "country": "Great Britain", "birthday": "15/02/1998",
    "number": 63, "shortName": "RUS", "url": "..."
  },
  "teamWinner": { ... }
}
```

Key facts:
- `winner` is a **single object** keyed on P1 only. No `podium` array. No
  P2/P3 fields. **This is why the list must call `/race` to get the podium.**
- `winner` is `null` for not-yet-completed races.
- Same shape on `/current` and on the historical `/{year}` (e.g. `/2024`),
  only schema noise differs (snake_case `fast_lap_driver_id` on 2024 vs
  camelCase on current; `max_verstappen` vs `maxverstappen` IDs). Per-ticket-03
  absorbed in DTOs.
- Response size: `/current` = 28 KB, serves all 24 rounds plus season
  aggregates in one call.
- No free bulk "podiums" endpoint exists. `/current/last` returns the
  most-recent past race with the same `winner`-only shape, no `results`.
- `GET /results` returns 404 ("no seasons found"). Not an endpoint.

### `GET /{year}/{round}/race` — per-round results, the podium source

```json
{
  "races": {
    "round": "1", "raceId": "bahrein2024", "circuit": [ { ... } ],
    "results": [
      { "position": "1", "points": 26, "grid": "1", "time": "1:31:44",
        "driver": { "driverId": "maxverstappen", "name": "Max",
                    "surname": "Verstappen", "number": 33, "shortName": "VER",
                    "nationality": "Netherlands", "birthday": "1997-09-30" },
        "team":   { "teamId": "redbull", "teamName": "Red Bull Racing", ... } },
      { "position": "2", ... },
      { "position": "3", ... },
      ... 17 more ...
    ]
  }
}
```

Key facts:
- `results` is **ordered by finishing position**, 20 entries for a full grid.
- Top-3 = `results[0]`, `results[1]`, `results[2]`. `position` is the
  string `"1"` / `"2"` / `"3"`; see ticket 03 schema noise ("position is
  a String, `NC` for retirees").
- `circuit` here is a **one-element array**, not the inlined object on
  `/current` (per ticket 03).
- Response size: ~11 KB per round. **This is the call the Past list must make
  per past round to render P1–P3.**

### Caching

`Cache-Control: public, max-age=600` on both endpoints (10 min server-side
TTL via Vercel). Ktor `HttpCache` plugin respects this; repeated visits
within 10 min cost zero network on past rounds.

## Cost

| Surface                     | Calls per open                | Bytes              | P1–P3 served |
|-----------------------------|-------------------------------|--------------------|--------------|
| Past list (full podium)     | 1× `/current` + N× `/race`    | 28 KB + N×11 KB    | P1–P3        |
| Round drilldown (always)    | 1× `/race` (full grid)        | 11 KB              | P1–P3        |

N = past rounds at the moment of open. Mid-season ≈ 12; late season ≈ 24.

The list and the drilldown call the same `/race` endpoint, so the drilldown's
call is a cache hit when the user opens a row whose podium the list already
fetched — no double fetch per round.

## API wrangling — how the podium gets onto the list

There is no podium shortcut. Two stages:

1. **Season skeleton.** `GET /current` → list of rounds, each with `winner`
   (P1) inlined. This gives the row order, circuit, date, and the P1 cell for
   free. Upcoming rounds have `winner: null`.
2. **Podium fill.** For each past round, `GET /{year}/{round}/race` and take
   `results[0..2]`. This is the only way to get P2 and P3.

`position` is a String (`"1"`, `"2"`, `"3"`, `"NC"`); don't sort by it —
slice `results[0..2]` directly since the array is already position-ordered.

### Fetch strategy: lazy per-row, not eager-batch

- **Lazy per-row** — a small `GetRoundPodiumUseCase(year, round)` invoked
  when the Past row composes / scrolls into view. HttpCache (10 min TTL)
  absorbs re-opens. Renders P1 from `/current` instantly, P2/P3 stream in as
  each row's `/race` resolves.
- **Eager-batch** (fire all N `/race` calls on screen open) is one line more
  for no behavioural win: it front-loads all calls before any row that needs
  them is on screen, and loses the progressive-render feel. Skip until a user
  complains about scroll-in latency.

Lazy per-row is the lazy-rung. It also bounds work to rounds the user
actually scrolls past — late-season backlog of 24 past rounds only costs
24 calls if the user scrolls the whole list.

### Cold-open note

A first-time open on a fresh install mid-season fires 1 + N calls before
podiums fill. The 10-min server cache + Ktor HttpCache make re-opens free.
If cold-open cost becomes a complaint, the upgrade path is eager-batch with
a single loading shimmer — not a different API.

## Implementation contract (when this lands)

- `Race.winner: Driver?` on the `Season` model is the P1 source + row order
  from `/current`. Null on upcoming races; render "—" or hide the podium cell.
- **Order: most-recent first.** The Past tab is a "what just happened" scan
  path; `season.races.filter { it.winnerId != null }.sortedByDescending { it.round }`
  — round number is monotonic within a season, so descending round ≡
  reverse-chronological. (Upcoming keeps ascending: next race first.) The
  f1api.dev `/current` response is round-ascending, so the filter alone
  produces the wrong order; the explicit `sortedByDescending` is required.
- Past list row cells: `[round#, circuit-flag, race-name, P1/P2/P3 drivers]`.
  P1 available immediately from `/current`; P2/P3 load via the per-row use
  case. Click → `RoundDetail(year, round)`.
- `GetRoundPodiumUseCase(year, round)` returns the top-3 drivers (sliced from
  `results[0..2]`). Invoked on row-compose. Cached by HttpCache.
- Round drilldown fetches `GetRoundResultsUseCase(year, round)` (ticket 03)
  and renders the full grid P1–P20 + a top-3 podium block. Its `/race` call
  is a cache hit for rounds the list already fetched.
- `position` is a String; consumers must tolerate `"NC"`. Slice the ordered
  array rather than filtering by position.

## Visual treatment (locked 2027-01-15, `/impeccable shape`)

The past-row podium cell is the only reason a casual fan opens the Past tab;
the cell answers "who raced last, and when" in one glance. The treatment
extends — does not replace — the data contract above.

Three design iterations landed here, documented because the failed
attempts are also a record of what the row does *not* want:

1. **v1: three flat `Column` chips** — P1/P2/P3 looked identical; no
   visual hierarchy, no team name. (Original P0 #4 in the 2027-01-15
   critique.)
2. **Bolder pass: tiered chips with `F1Primary` on P1, `surfaceContainerHigh`
   on P2/P3, `titleLarge` Bold on P1, `titleMedium` on P2/P3, full team
   name underneath** — `titleLarge` made RUS/ANT as big as the GP name
   above, the row's height broke, "Mercedes For…" truncation on long
   team names added noise. Quieter pass dropped the type bump + team
   name + padding but still read as a foreign primitive in the row.
3. **Shape pass: replace chips with an inline text line** (this version).
   The row's existing visual language is text on the card surface; the
   chip shape (rounded background container) was a new primitive the
   row didn't share. Dropping the chip resolves the mismatch.

**Treatment (locked).**

- Single inline text line on the row, no container, no background, no chip:
  ```
  P1 RUS  ·  P2 ANT  ·  P3 LEC
  ```
- Position label (`P1` / `P2` / `P3`) and driver code (`RUS` / `ANT` /
  `LEC`) share `titleMedium` SemiBold (matches the GP-name weight
  above the row). Position is `onSurfaceVariant` (muted); driver code
  is `onSurface` (full). Color carries the label-vs-value hierarchy
  so the line reads as one typeface, not two mismatched sizes.
- **`start` padding `Spacing.xs` on the code Text.** Without it the
  position and code mash into one word (`P1ANT`); the brief was
  `P1 RUS` with breathing room. `Spacing.xs` matches the breathing
  room on each side of the middle dot, so the line has one consistent
  rhythm.
- Middle dot `·` with `Spacing.xs` on each side as the separator. No
  trailing separator after the last entry. The middle dot itself is
  `bodyMedium` `onSurfaceVariant` — smaller and muted, so it sits
  between the pairs without competing with them.
- P1 dominance is implicit — left-to-right scan reads P1 first; no
  special color or weight treatment on P1. The `1` in `P1` is the
  first/most-recent position.
- Position labels stay (`P1/P2/P3`) — DtS-orthodox; the race mechanic
  communicates that way to the driver.
- No team name on the row. The driver code is the F1-canonical signal;
  team is implied. If team becomes a hard requirement later, route it
  through `RoundDetail` or a future `teamShortName` field — do not
  reintroduce the chip shape.

**Rejected directions (do not revisit without a strong reason).**

- P1 red chip (or any coloured background) — the chip *shape* is what
  was broken; colour does not fix it.
- `1st` / `2nd` / `3rd` (English ordinal) — chose `P1` / `P2` / `P3` for
  DtS orthodoxy; user explicitly picked this.
- Team name on the row — long names (`Scuderia Ferrari`,
  `Mercedes-AMG Petronas`) truncate ugly inside the row's natural width;
  the team is one tap away on `RoundDetail`.
- A stepped 3-row list (vertical stack of 1/2/3 + code) — the row
  already has a vertical city/date line; stacking more text below it
  pushes the next row down without adding scanning speed.

## Out of scope (parked elsewhere)

- **GAP-A (top speed per circuit)** — ticket 08. Independent decision.
- **GAP-B (most wins at circuit)** — ticket 09. Independent decision.
- **The round-result drilldown itself** — ticket 03's
  `GetRoundResultsUseCase`; not designed here.
- **OpenF1 enrichments** — driver headshot, weather, race-control flags
  (ticket 04 follow-up). Not affected by this decision.

## Cross-references

- Ticket 10: `lode/wayfinder/f1app/tickets/10-research-past-list-podium.md`
  (closed).
- Ticket 03: `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md` —
  the data layer this decision rides on; `GetSeasonUseCase` /
  `GetRoundResultsUseCase` shape is fixed there.
- Terminology: `lode/terminology.md` — `Season aggregates`.
- Practices: `lode/practices.md` — HttpCache + NO_CACHE pull-to-refresh
  pattern; the 10-min server TTL is the cache ceiling that makes repeat
  opens free.

## Invariants captured

- Past list surface **must** render P1, P2, P3 per past round. P1 from
  `/current`; P2/P3 from the per-round `/race` call.
- Podium fill is **lazy per-row** via `GetRoundPodiumUseCase`, not an
  eager-batch on screen open. Upgrade to eager-batch only if scroll-in
  latency is a measured complaint.
- Round drilldown **must** render P1–P3 podium (data already on hand from
  its own `/race` fetch — cache-shared with the list's fill call).
- `position` is a String tolerating `"NC"`; consumers slice the ordered
  `results` array, never sort by `position`.

## Lessons learned

- The `winner`-only shape on `/current` (and `/current/last`) is by design —
  f1api.dev chose the cheaper-per-call season overview, and full results are
  on the per-round endpoint. **Pin as a contract: the season endpoint serves
  one driver per past round, not a podium array.**
- `Cache-Control: public, max-age=600` from Vercel is the real ceiling on
  cold-open cost. The list pays 1 + N on first open; re-opens within 10 min
  are free.
- The drilldown reuses the list's `/race` calls via HttpCache — design the
  fetch strategy so the two surfaces don't double-fetch the same round.
