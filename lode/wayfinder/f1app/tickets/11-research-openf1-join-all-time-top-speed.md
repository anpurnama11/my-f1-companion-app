---
id: 11
title: "Research: OpenF1 session_key join + all-time-vs-latest top speed (GAP-A.2)"
type: research
status: closed
blocked_by: [04]
owner: ""
closed_by: "Join = country_name + year + race-date match (one /v1/sessions call, no static map needed for the common case; 1-entry country fallback for 'Great Britain' → 'United Kingdom'). Stat ships as latest Qualifying peak relabeled 'Top speed' — 2 calls + ~200 KB per open, same as ticket 08 baseline. All-time OpenF1 scan is feasible (8 calls + ~800 KB / circuit) but 'all-time' is misleading because OpenF1 starts 2023+. Pre-2023 cells empty."
---

## Question

Ticket 08 closed on "ship real top speed (km/h) from OpenF1 `/v1/laps`
`st_speed`" after ticket 04 reopened to multi-source. Two load-bearing
sub-questions were flagged open and now need answers before implementation:

1. **session_key join strategy.** OpenF1 `/v1/laps` requires a `session_key`.
   What's the cheapest correct way to resolve a `session_key` for a given
   f1api.dev circuit + year? Is `country_name` the stable join key (ticket 08
   claimed), or does it diverge (Monaco vs Monte-Carlo) in ways that need a
   fallback? How many calls does the join add per Homepage §3 open?

2. **All-time record vs latest-session peak.** Homepage §3 says "top speed
   **record**" — implying the all-time circuit peak. But OpenF1 `/v1/laps`
   serves 2023+ only, and `max(st_speed)` is per-session. Can OpenF1 serve
   an all-time circuit peak directly, or is it a multi-season `/v1/laps`
   scan (one call per historical session at that circuit)? If multi-season
   scan, what's the cost (call count, bytes, rate-limit feel) vs just
   showing the latest-session peak relabeled "Top speed"?

## Context

- Ticket 04 reopened to multi-source: OpenF1 wired alongside f1api.dev for
  stats f1api.dev cannot serve. One `HttpClient`, per-request base URLs
  (`OPENF1_BASE`), repository methods in `f1/` (no `openf1/` package).
- Ticket 08 is closed; `lode/wayfinder/f1app/top-speed.md` documents the
  `/v1/laps` `st_speed` finding + the `car_data` (NOT the path) finding.
  Ticket 08 explicitly punted "all-time vs latest" to this follow-up.
- Homepage §3 (`lode/wayfinder/f1app/homepage.md`) shows "top speed record"
  on the nearest-date GP card.
- OpenF1 rate-limit feel from ticket 08: ~3 req/s free tier comfortable;
  `/v1/laps` `st_speed` call was ~200 KB. Multi-season scan cost unknown.
- OpenF1 coverage is 2023+ (ticket 08). Pre-2023 rounds can't serve the
  stat at all — what does the cell show?

## Resolution needed

- **Join strategy locked.** `country_name` vs `circuit.name` vs a slug map.
  One documented approach, with the fallback for divergence cases.
- **"Record" semantics locked.** Ship latest-session peak (relabeled "Top
  speed" without "record"), all-time OpenF1-served peak (if feasible), or
  mix (latest + "record" badge if all-time is also fetched)? State the
  call cost of each.
- **Pre-2023 handling.** Empty cell? Placeholder? The cell must not lie.

## Out of scope

- GAP-A primary (which API serves top speed) — ticket 08, closed. OpenF1.
- GAP-B (most wins at circuit) — ticket 09.
- GAP-C (full podium on Past list) — ticket 10, closed.
- OpenF1 additive enrichments (headshot/weather/flags) — parked follow-up.

## Default resolution if not investigated

Ship **latest-session peak** via one `session_key`-resolved `/v1/laps` call,
labeled "Top speed" (drop "record"). Multi-season scan parked as a
follow-up. Pre-2023 rounds show an empty speed cell with no placeholder.

---

## Resolution (research output)

Full details in [top-speed.md](../top-speed.md) and
[top-speed-api-wrangling.md](../top-speed-api-wrangling.md). Summary:

### Q1 — session_key join strategy

**Join is `country_name + year + race-date match`. One API call.**

`country_name` alone is not enough — three countries host multiple F1
circuits and would each return N sessions for the same year:

| Country | OpenF1 `circuit_short_name` values | Affected years |
|---|---|---|
| `United States` | Austin, Las Vegas, Miami | 2023+ (3 circuits) |
| `Spain` | Catalunya, Madring | 2026+ (2 circuits) |
| `Italy` | Imola, Monza | 2023–2025 (2 circuits) |

f1api.dev's `circuit.country` gives us the country string, and
f1api.dev's `race.schedule.race.date` (`YYYY-MM-DD`) is the **exact
race date**. OpenF1's `date_start` is ISO with time, but the date
portion matches f1api.dev's race date for every circuit tested (Miami
2024-05-05, Austin 2024-10-20, Vegas 2024-11-24, Monza 2024-09-01,
etc.). One `date_start.toLocalDate() == race.date` comparison is unique
per (year, country) — verified live.

**One country name diverges**: f1api.dev's `circuit.country = "Great
Britain"` for Silverstone, OpenF1's `country_name = "United Kingdom"`.
This is the **only** string divergence in the current 24-circuit
schedule (verified by enumerating all f1api.dev countries against
OpenF1's `country_name` set — all 23 others match exactly). 1-entry
fallback map, applied only when the literal returns 0 results — same
shape as the 5-entry `F1API_TO_JOLPICA_CIRCUIT` map already in
`F1Api.kt` (ticket 04):

```kotlin
private val F1API_TO_OPENF1_COUNTRY = mapOf(
    "Great Britain" to "United Kingdom",
)
```

**No `circuit_short_name` is ever used as a join key.** Monaco vs
Monte-Carlo and Spa vs Spa-Francorchamps divergences are sidestepped
by date match. `circuit_key` (OpenF1's internal ID) is stable but not
portable to f1api.dev — also never used.

### Q2 — All-time vs latest

**Ship latest Qualifying peak, label "Top speed" (no "record").** The
default resolution holds; this is the cost table that locks it.

| Approach | Calls/cell | Bytes/cell | "All-time" claim? | Verdict |
|---|---|---|---|---|
| **Latest Qualifying peak** (recommended) | 2 | ~200 KB | No | Ship. Same as ticket 08. |
| Latest + 1 prior year | 4 | ~400 KB | No, marginally better | Skip — diminishing returns. |
| All-time OpenF1 scan (2023+) | 8 | ~800 KB | **Misleading** (4 yrs ≠ all-time) | Don't. |
| "True" all-time (2004+) | n/a | n/a | Yes | Not possible on any free API. |

**The "record" label cannot be honestly applied.** OpenF1 starts 2023.
Showing `max(2023–2026 st_speed)` as "all-time circuit record"
misrepresents the stat — the real all-time record was almost certainly
set pre-2023 (different aero regs, different engine era). "Top speed"
with no qualifier is honest: it's the latest speed-trap reading,
broadcast-grade, what F1 publishes for the current car era.

**Pre-2023 handling**: empty cell, no placeholder. Empty = no data,
which is the truth. A "—" or "N/A" lies (it says "we know there's no
data" when really we know there's no data *because of our source*).
Same pattern as f1api.dev's `winner` being null on upcoming rounds
(ticket 03).

**All-time OpenF1 scan is feasible, just not the right shape.** ~4
Qualifying sessions per circuit across 2023–2026 (one per year per
circuit) = 4 + 4 = 8 calls and ~800 KB per circuit. Cacheable per
circuit (the value doesn't change between page opens), but the 8-call
cost on every cold open is unnecessary when the "record" label is
dishonest anyway. Latest peak is the same stat in 99% of cases (top
speed is dominated by car era, not track). **Parked.**

### Decisions (locked)

- **Join strategy**: `country_name` + `year` + race-date match
  (1 `/v1/sessions` call). 1-entry fallback map
  (`"Great Britain" → "United Kingdom"`) when literal returns 0.
- **Session**: `session_name=Qualifying`. Per ticket 08 wrangling:
  low-fuel push laps produce the weekend's actual top speed.
- **Label**: **"Top speed"** — no "record". Latest Qualifying peak.
- **Pre-2023 rounds**: empty cell. No placeholder. No fake "—".
- **All-time OpenF1 scan**: not shipped. Parked.
- **`is_cancelled` filter**: not applied. Even cancelled weekends
  (2023 Imola) recorded Qualifying laps; date match handles
  disambiguation.

### Cost (Homepage §3 cold open, Round detail cold open)

Same as ticket 08 baseline — **2 calls + ~200 KB + ~0.6 s** (3 calls
with the UK fallback fired). No cost increase. The use-case is paid
once for both Homepage §3 and Round detail — both call sites already
have `country` + `year` + `raceDate` from the inlined f1api.dev
race/circuit object.

### Implementation shape (for the next implementation ticket, not now)

```kotlin
// f1/data/F1Api.kt — next to the OpenF1 extensions
private val F1API_TO_OPENF1_COUNTRY = mapOf("Great Britain" to "United Kingdom")

// f1/GetCircuitTopSpeedUseCase.kt — body sketch (~15 lines)
suspend fun getTopSpeed(country: String, year: Int, raceDate: String): Int? {
    val key = sessionKeyFor(country, year, raceDate)
        ?: F1API_TO_OPENF1_COUNTRY[country]?.let { sessionKeyFor(it, year, raceDate) }
        ?: return null
    return openF1.getLaps(key).maxOf { it.stSpeed }
}

private suspend fun sessionKeyFor(country: String, year: Int, raceDate: String) =
    openF1.getSessions(country, year, "Qualifying")
        .firstOrNull { it.dateStart.toLocalDate().toString() == raceDate }
        ?.sessionKey
```

Lives in `F1Api.kt` next to the OpenF1 extensions, same pattern as
`F1API_TO_JOLPICA_CIRCUIT`. Per [top-speed.md](../top-speed.md)
decision and [top-speed-api-wrangling.md](../top-speed-api-wrangling.md)
endpoint choice.

### Invariants captured

- **OpenF1 join key is `country_name + year + race-date match`**, not
  `country_name` alone. Multi-circuit countries (US, Spain, Italy)
  would otherwise return N sessions; the date match is unique.
- **`country_name` has exactly one string divergence between
  f1api.dev and OpenF1**: `Great Britain` vs `United Kingdom`
  (Silverstone). 1-entry fallback map, applied only on 0 results.
- **`circuit_short_name` is never used as a join key.** Monaco /
  Monte-Carlo and Spa / Spa-Francorchamps divergences are sidestepped
  by date match.
- **OpenF1 starts 2023.** "Top speed" without qualifier is the cell;
  "record" cannot be honestly applied.
- **Pre-2023 cells are empty**, not "—" or "N/A".
- **Latest Qualifying peak is the right cell**, per ticket 08.
- **The session_key join is paid once** for both Homepage §3 and
  Round detail (both have `country` + `year` + `raceDate` already).
- **All-time OpenF1 scan is parked**, not "TODO".

### Lessons learned

- **Cross-source key strings diverge in unguessable ways.** The
  Silverstone `Great Britain` vs `United Kingdom` divergence was not
  flagged by ticket 08 or ticket 04 — the only way to find it was a
  full enumerate-and-compare. Whenever two APIs claim a shared
  "country" concept, **enumerate every value pair before shipping
  the join**. A 24-row check catches it.
- **The 3 multi-circuit countries (US, Spain, Italy) shape the join,
  not the 21 single-circuit countries.** Probing the *worst* country,
  not the average, is the right reflex.
- **"All-time" is a label, not just a scan.** A 2023–2026 scan is
  "4 years of OpenF1 data," not "all-time circuit record." The
  honest label is the smaller of the two truths.
- **Date is a universal tiebreaker.** Whenever a join returns N
  candidates and you have one extra piece of timestamped context
  (the f1api.dev race date), prefer the timestamp over a static
  slug map. The f1api.dev `race.schedule.race.date` field is the
  cheapest, most stable disambiguator we already have.

### Cross-references

- [top-speed.md](../top-speed.md) — top-speed decision, recommendation,
  invariants (this ticket's findings are folded in).
- [top-speed-api-wrangling.md](../top-speed-api-wrangling.md) —
  endpoint + payload + cost details; the `Session_key join` section
  is the implementation hook (the country fallback map lives in
  `F1Api.kt` next to the OpenF1 extensions).
- [tickets/04-api-client-and-enrichment-scope.md](04-api-client-and-enrichment-scope.md) —
  multi-source wiring; jolpica's 5-entry `circuitId` map is the
  precedent for the 1-entry country fallback here.
- [tickets/08-research-top-speed.md](08-research-top-speed.md) —
  closed; this ticket picks up the join + all-time questions 08
  punted.
