# Team accent — `TeamColors.forId` for v1 (GAP-E)

Research output for ticket 16. Documents the four candidate APIs for
team color, why f1api.dev and Jolpica ergast are not options, why OpenF1
is rejected for the standings path, and why the hardcoded map is the v1
answer. Future migration target: Jolpica alpha `primary_color`.

## Decision (current)

**Use a hardcoded `TeamColors.forId(teamId: String): Color` object for v1.**
Compose `Color(0xFF...)` values transcribed from the Jolpica alpha
`primary_color` field. ~10 lines of Kotlin. No API call. No live-window
risk. No alpha-endpoint risk.

Same shape precedent as `Circuits.forId(...)` (23 entries), the
`F1API_TO_JOLPICA_CIRCUIT` slug map, and `LEGACY_TEAM_SLUGS` (~30
entries) — three existing `Map<X, Y>` objects in the data layer. Adding
a fourth is the same shape.

## Why not the alternatives

### f1api.dev — no color field

`/api/current/drivers` returns:

```json
{"driverId":"antonelli","name":"Andrea","surname":"Kimi Antonelli",
 "nationality":"Italy","birthday":"2006-08-25","number":12,
 "shortName":"ANT","url":"...","teamId":"mercedes"}
```

`/api/current/teams` returns:

```json
{"teamId":"mercedes","teamName":"Mercedes Formula 1 Team",
 "teamNationality":"Germany","firstAppeareance":1954,
 "constructorsChampionships":8,"driversChampionships":9,"url":"..."}
```

`/api/current/drivers-championship` adds a `team{...}` block with
`{teamId, teamName, country, firstAppereance, constructorsChampionships, driversChampionships, url}`.

**No color field on any of these.** f1api.dev does not expose team color.

### OpenF1 — 30-day live window

OpenF1 `/v1/drivers?driver_number=1&session_key=9158` returns `team_colour`:

```json
{"driver_number":1,"full_name":"Max VERSTAPPEN","name_acronym":"VER",
 "team_name":"Red Bull Racing","team_colour":"3671C6",...}
```

Format: hex `RRGGBB`, no `#` prefix. **But** the data is per-driver,
per-`session_key`, and OpenF1 has a 30-day rolling window. In winter
(off-season) `session_key=latest` may return empty arrays or stale data.
The §3 favorites surface shows year-round, so an OpenF1 join would lose
the accent in exactly the season where the casual fan use case is
strongest.

The lode claim that `team_colour` is "already wired" is true for the
**headshot/imagery fallback** chain (which has a session context — it
can ask "what was the latest session") but **misleading for the §3
standings path** which has no session context.

### Jolpica alpha — unfinished endpoint

Jolpica alpha `/alpha/core/teams/?year=2025` returns `primary_color` as
`#RRGGBB` with the `#` prefix. **All 10+ constructors have a value.**

```json
{"id":"team_tdiMmtQx","name":"Mercedes","primary_color":"#00D7B6",...}
{"id":"team_LjEBz7Xq","name":"Ferrari","primary_color":"#ED1131",...}
{"id":"team_SYB8m2Vp","name":"Red Bull","primary_color":"#4781D7",...}
{"id":"team_jJ6cuwXz","name":"McLaren","primary_color":"#F47600",...}
{"id":"team_LJ6hqyXM","name":"Alpine F1 Team","primary_color":"#00A1E8",...}
```

But: Jolpica themselves say the alpha tree is unfinished (issue #304).
Mapping f1api.dev `teamId` (`"mercedes"`) to Jolpica `name` (`"Mercedes"`)
or `id` (`"team_tdiMmtQx"`) needs a small map. Adopting alpha now means
depending on a moving target, a third HTTP client path, and a new
`GetTeamColorsUseCase`. **Saves maintenance of a 10-line file at the
cost of all that.**

### Jolpica ergast-compatible — no color field

Confirmed live:

```json
{"MRData":{"StandingsTable":{"StandingsLists":[{"ConstructorStandings":[
  {"position":"1","positionText":"1","points":"833","wins":"14",
   "Constructor":{"constructorId":"mclaren","url":"...","name":"McLaren",
                  "nationality":"British"}}]}]}}}
```

No color. Jolpica explicitly says they don't plan to add it to the
ergast endpoints ("currently do not plan to add this info to the ergast
endpoints as we ideally want people to move away from them").

## Cost summary

| Path | Calls | Bytes | Maintenance | Risk |
|---|---|---|---|---|
| Hardcoded `TeamColors.forId` | 0 | 0 | ~10 liveries/year | None |
| OpenF1 join in standings use case | +1 per refresh | ~1KB | Off-season empty | Live window |
| Jolpica alpha fetch | +1 per season (or on new teamId) | ~5KB | None (community-driven) | Alpha endpoint |
| Jolpica ergast | N/A | N/A | N/A | Color field never coming |

## v1 implementation

```kotlin
// f1/data/TeamColors.kt — next to Circuits.forId(...)
// Stable per season; touch once a year when liveries change.
// Future source: Jolpica alpha /alpha/core/teams/?year={year} primary_color.
object TeamColors {
    fun forId(teamId: String): Color = when (teamId) {
        "ferrari"   -> Color(0xFFED1131)
        "mercedes"  -> Color(0xFF00D7B6)
        "red_bull"  -> Color(0xFF4781D7)
        "mclaren"   -> Color(0xFFF47600)
        "aston_martin" -> Color(0xFF229971)
        "alpine"    -> Color(0xFF00A1E8)
        "williams"  -> Color(0xFF1868DB)
        "rb", "racing_bulls" -> Color(0xFF6C98FF)
        "sauber", "kick_sauber", "audi" -> Color(0xFF01C00E)
        "haas"      -> Color(0xFF9C9FA2)
        "cadillac"  -> Color(0xFF000000)  // TBD when liveries confirmed
        else        -> Color.Unspecified
    }
}
```

Compose `Color(0xFF...)` is the ARGB form. The 6-digit hex from the API
(`"00D7B6"`) becomes `0xFF00D7B6` (prepend `FF` for opaque alpha).
The `#` is stripped.

## Future migration path

When Jolpica alpha tree stabilizes (per issue #304 — community-driven
update process for color changes):

1. Add `GetTeamColorsUseCase` in `f1/data/` next to the use-case family
   (or extend an existing use case with the team-color fetch).
2. In-memory `Map<teamId, Color>` cache in `Wiring`, populated once per
   season.
3. `TeamColors.forId(teamId)` becomes `cache[teamId] ?: Color.Unspecified`
   with the hardcoded map as the **fallback** (so the app keeps working
   if Jolpica is down).

The hardcoded `TeamColors.forId` is the v1 answer; the alpha fetch is
the v1.x answer. Same `Map<teamId, Color>` shape, same call site.

## What this is NOT

- **Not a `team_colour` from OpenF1.** OpenF1's `team_colour` is for the
  headshot/imagery fallback chain (where session context is available).
  This is a separate, year-round source for the §3 favorites accent.
- **Not a manual color picker.** The values come from Jolpica alpha's
  community-maintained color data, not from a hand-tuned palette.
- **Not a "live" color.** Liveries change once a year; the hardcoded map
  is updated once a year. There is no "real-time" team color in F1.

## Out of scope (parked elsewhere)

- **Weather, race-control flags** — still separate enrichments under
  ticket 13 (live-window-only, OpenF1).
- **Driver headshots** — first item on the ticket 13 list; OpenF1
  `headshot_url` with `team_colour` swatch fallback.
- **Country flags** — OpenF1 `country_flag`, not yet consumed.

## Cross-references

- Ticket 16: `lode/wayfinder/f1app/tickets/16-team-accent-source.md` —
  source-of-truth decision.
- Ticket 13: `lode/wayfinder/f1app/tickets/13-additive-ui-enrichments.md`
  — the `team_colour` swatch as the headshot/imagery empty state.
- Ticket 18: `lode/wayfinder/f1app/tickets/18-section-3-favorites-shape.md`
  — §3 favorites shape; the accent lives on whichever shape is chosen.
- Ticket 04: `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md`
  — `OPENF1_BASE` per-request base URL; OpenF1 wiring is paid.
- Jolpica issue #304: https://github.com/jolpica/jolpica-f1/issues/304
  (color field request, status: in progress).

## Invariants captured

- **f1api.dev and Jolpica ergast are not team-color sources.** Neither
  endpoint carries a color field; both are out.
- **OpenF1 `team_colour` is rejected for the §3 standings path** because
  of the 30-day live window. The headshot/imagery fallback (with session
  context) keeps using it.
- **Jolpica alpha `primary_color` is the future source** when the alpha
  tree stabilizes. Track issue #304.
- **The v1 hardcoded map is the cheapest answer that meets the brief.**
  ~10 lines, no API call, no live-window risk, no alpha-endpoint risk.
  Same shape as the three existing `Map<X, Y>` objects in the codebase.
- **Unknown teamIds return `Color.Unspecified`.** Honest unknown state,
  not a fake fallback color.
- **The accent goes on a surface strip, never on text.** The `Color.kt`
  contract — accent backgrounds on dark, never text — is respected
  everywhere. (The circuit color usage in §3 is the precedent.)
- **The hardcoded map is a fallback when Jolpica alpha lands**, not the
  other way around. The shape `Map<teamId, Color>` doesn't change.

## Lessons learned

- **"Already wired" is a lode phrase that needs the data path to be
  re-checked.** `team_colour` is wired for headshots (where session
  context exists) but not for standings (where it doesn't). The
  30-day window is the disambiguator. Always check the actual call
  site before assuming a lode claim transfers.
- **Three existing `Map<X, Y>` objects in the codebase is a strong
  precedent for a fourth.** When the alternative is a new HTTP client
  path, a new use case, a new in-memory cache, and a third API
  dependency, a 10-line hardcoded object is the right call.
- **Alpha endpoints are not production endpoints.** Jolpica alpha is
  useful for *knowing the future data shape* (the `primary_color` field
  gives us the format we'll consume later) but not for *consuming now*.
  Hardcode the values from the alpha response, plan the migration.
- **Compose `Color(0xFF...)` ARGB is not the same as the API's
  `#RRGGBB` hex.** Strip the `#`, prepend `FF` for alpha. The hex value
  itself is identical; only the encoding differs.
