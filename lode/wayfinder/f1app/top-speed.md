# Homepage §3 + Round detail — top-speed stat (GAP-A)

Research output of ticket 08. Source of truth for anyone implementing
the "Top speed" cell on the **Homepage section 3** (nearest-date GP
info) and the **Round detail** screen. Documents which free API serves
a per-circuit peak speed, the wrangling cost, and which label the stat
ships under.

## Decision (current — superseded the single-source fallback)

**Ship the real top speed (km/h) from OpenF1 `/v1/laps` `st_speed` in
the initial build.** Ticket 04 reopened to multi-source: OpenF1 is
wired alongside f1api.dev for stats f1api.dev cannot serve. The earlier
"relabel to Fastest lap" fallback was a single-source workaround and
is revoked — f1api.dev has no speed field, so a lap-time-as-speed
label would misrepresent the stat.

`lapRecord` (lap time, f1api.dev) may still ship **as well**, on a
separate "Fastest lap" cell where the design's `FastestLap =
F1Tertiary` purple token was built for it — but it does not substitute
for the top-speed km/h on Homepage §3.

## What the design actually asked for

`boxbox-club-DESIGN.md` line 169, transcribed to `ui/theme/Color.kt`:
> "Tertiary (#583ff2): Fastest lap purple — used for
> performance-highlight data."

The design names the badge color "fastest lap" and the only related
accent in the palette (`badge-fastest-lap`) is for a lap-time stat.
"Top speed" was a reframe of that cell, not a deliberate
speed-trap-reading ask. Fastest-lap is the stat the design was already
designed to host.

## Sources at a glance

| Source | Speed field? | Verdict for the stat |
|---|---|---|
| **f1api.dev** `circuit.lapRecord` | No (lap time string) | **Ships the stat as "Fastest lap" — free, all-time, already wired.** |
| **f1api.dev** race-results `fastLap` | No (universally `null` 2023-2024) | Dead field; ignore. |
| **jolpica-f1** `/ergast/f1/circuits` | No (just name + lat/lng) | Skip — subset of f1api.dev. |
| **Pit Lane F1** `/circuits/{ref}` | No (only `most_wins`, `winners_timeline`) | Parked for ticket 09 (GAP-B), not this one. |
| **OpenF1** `/v1/laps` `st_speed` | **Yes** (official speed-trap reading) | Parked for ticket-04 follow-up; 2 calls, ~200 KB. |
| **OpenF1** `/v1/car_data` peak | Yes (per-car telemetry) | 21 calls, ~60 MB; not the upgrade path. |

Full source-by-source probes, payload sizes, and the `st_speed` vs
`car_data` trade live in
[top-speed-api-wrangling.md](top-speed-api-wrangling.md).

## Cost summary (per round-detail open, cold cache)

| Approach | Calls | Payload | Top-speed value | Verdict |
|---|---|---|---|---|
| **f1api.dev `lapRecord` (relabeled "Fastest lap")** | 0 (already in `/current` inlined `circuit`) | 0 extra | Lap time `1:31:447` + driver/team/year | **Free; ships in the initial build.** |
| OpenF1 `/v1/laps` `max(st_speed)` | 2 | ~200 KB | Weekend top speed km/h + driver | Add with the OpenF1 follow-up (ticket 04). |
| OpenF1 `/v1/car_data` peak | 21 | ~60 MB | Per-car telemetry peak km/h | Don't. |

The two real options are: ship the lap-time stat for free, or pay 2
calls + ~200 KB per round-detail open to upgrade the cell to a real
km/h number. The third option (drop the cell) is also valid for
BSSN/ponytail but the round-detail page already has the data shape to
host it; the slot is already there.

## Recommendation

**Ship the "Fastest lap" cell in the initial build, sourced from
f1api.dev's `lapRecord` on the inlined `circuit` block.** No new
endpoint, no new source, no second API key, no session_key join.

When the ticket-04 OpenF1 enrichment follow-up lands (driver
headshot, weather, race-control flags), the same wiring gives a free
upgrade to a real top-speed number: the `GetRoundExtrasUseCase` (or
whatever the follow-up names it) already has the `session_key` for
the just-finished round and can call `/v1/laps` once, take
`max(st_speed)`, and surface "Top speed 306 km/h — VER" as an
optional second cell next to the lap-time one. Both cells can coexist;
the lap-time stat is canonical (all-time circuit record, what every
F1 fan recognizes), the speed stat is the freshest weekend reading.

This makes the speed figure a 1-line extension to the
already-open OpenF1 endpoint that ticket 04's reopen wires in, not
a fresh research effort. The session_key join is paid once for both
the top-speed stat and any future enrichment (headshot/weather/flags).

## Out of scope (parked elsewhere)

- **Driver headshot, weather, race-control flags on Round details** —
  ticket-04 follow-up. The `/v1/laps` call rides the same wiring.
- **GAP-B (most wins at circuit)** — ticket 09. Pit Lane F1's
  `records.most_wins` and `records.winners_timeline` are the cheap
  source; see that ticket.
- **GAP-C (full podium on Past list)** — ticket 10, closed. Lives at
  [past-list.md](past-list.md).
- **Round detail drilldown** — already in scope via ticket 03
  (`GetRoundResultsUseCase`, `GetRoundQualifyingUseCase`).
- **The "Top speed" cell as a separate widget** — out of scope;
  stat-only, on the round detail page.

## Cross-references

- Ticket 08: `lode/wayfinder/f1app/tickets/08-research-top-speed.md`
  (closed; this file is the research output).
- API wrangling detail: [top-speed-api-wrangling.md](top-speed-api-wrangling.md).
- Ticket 04: `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md`
  — reopened to multi-source; OpenF1 wired in for top speed.
- Ticket 03: `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md`
  — round detail data layer; `circuit` is already inlined on every
  race, so `lapRecord` is free.
- `lode/terminology.md` — `F1Api` extension shape, `OpenF1`, `Wiring`,
  `HttpCache` + `NO_CACHE` pull-to-refresh pattern.
- `lode/practices.md` — domain-purity invariant (the OpenF1 extension
  rides the same Ktor client, no `openf1/` package), HttpCache config.
- `lode/design-system/theme.md` — `FastestLap = F1Tertiary` color is
  the design's existing affordance for this stat. The relabel matches
  the design.

## Invariants captured

- The "section 1" Round detail stat ships as **"Top speed" (km/h)**
  from OpenF1 `/v1/laps` `max(st_speed)`, wired in the initial build
  (ticket 04 reopened to multi-source). `circuit.lapRecord` (lap time)
  ships as a separate **"Fastest lap"** cell where the design's
  `FastestLap = F1Tertiary` token was built for it — not as a
  substitute for the speed.
- The OpenF1 `/v1/car_data` peak approach is **not** the path — it's
  21 calls, ~60 MB, rate-limited, answers a worse question than
  `st_speed`. Do not implement.
- `st_speed` (the official speed-trap reading) is the "top speed"
  unit; the cell stays km/h, not mph, to match F1's published
  figures.
- The `country_name` field is the stable join key from f1api.dev's
  `circuit.country` to OpenF1's `/v1/sessions` filter, **plus year +
  race-date match** (ticket 11) — `country_name` alone is insufficient
  for US (3 circuits), Spain (2 circuits 2026+), and Italy (2 circuits
  2023–2025). The date match is the unique key, not a slug map.
- One country string diverges: f1api.dev `Great Britain` vs OpenF1
  `United Kingdom` (Silverstone). 1-entry fallback map, applied only
  when literal returns 0 results. Documented in
  [top-speed-api-wrangling.md](top-speed-api-wrangling.md).
- The "all-time circuit record" label cannot be honestly applied —
  OpenF1 data is 2023+, so an OpenF1-only scan is "4 years of data,"
  not "all-time." Ship latest Qualifying peak, labeled **"Top speed"**
  (no "record"). Ticket 11's research output.
- OpenF1 covers 2023+; pre-2023 rounds show the lap-time stat only
  and the speed cell is empty. Empty, not "—" / "N/A" — same pattern
  as f1api.dev's `winner` being null on upcoming rounds. Document the
  absence rather than falling back to `car_data`.

## Lessons learned

- **Always check the design tokens before treating a stat name as
  authoritative.** The design's "fastest-lap purple" was the
  clearest hint that the original "top speed" framing was a reframe,
  not the design's intent. The Lode's `design-system/theme.md` should
  be the first stop on any "is this stat real?" research ticket, not
  the reference doc.
- **`fastLap` in f1api.dev race results is dead code.** The field
  exists in the schema but is universally `null` across 2023 and 2024
  (440/440 rows). Don't promise it; don't surface it. The circuit's
  `lapRecord` is the only "fastest lap" f1api.dev actually serves.
- **OpenF1's `/v1/laps` is the workhorse for any "what happened on
  this lap" stat** — not just `lap_duration`, also `st_speed`,
  `i1_speed`, `i2_speed`, `segments_sector_*`. Two calls
  (`/sessions` + `/laps`) and you have the whole weekend's lap-level
  data for all 20 drivers. The temptation to reach for
  `/v1/car_data` for "peak speed" is the wrong rung — check `/laps`
  first.
- **The brute-force `/v1/car_data` path is the right reflex for
  telemetry research; it's the wrong reflex for shipping a stat.**
  Telemetry scan = debugging tool, not product feature. Stats want
  broadcast-grade, F1-published numbers; the speed trap is exactly
  that.
- **Pit Lane F1 is worth knowing about for ticket 09** — it serves
  per-circuit `most_wins` and `winners_timeline` for free
  (CC-BY-4.0 with attribution). Not a top-speed source, but a
  ticket-09 finding the rest of this research surfaced.
