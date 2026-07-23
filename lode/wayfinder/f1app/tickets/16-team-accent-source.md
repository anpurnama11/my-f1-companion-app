---
id: 16
title: "Team-accent source — `TeamColors.forId` for v1"
type: research
status: closed
blocked_by: []
owner: "pi"
---

## Question

The §3 favorites cards need a team color accent (each `DriverStanding`'s
team color, each `ConstructorStanding`'s own color). The current
`DriverStanding` / `ConstructorStanding` models (f1api.dev data) carry no
color field. Which API / source provides the team accent for v1, and
what's the path forward when a real data source stabilizes?

## Resolution (closed 2027-01-15)

### Live API probe (2027-01-15)

| API | Has team color? | Field | Shape | Coverage |
|---|---|---|---|---|
| **f1api.dev** | ❌ NO | — | Standings endpoints (`/api/current/drivers`, `/api/current/teams`, `/api/current/drivers-championship`) carry no color field. Confirmed live. |
| **OpenF1** (`/v1/drivers`) | ✅ YES | `team_colour` | `String` (hex, no `#` prefix, e.g. `"3671C6"`) | Per driver, per `session_key`. **30-day live window — empty in off-season.** |
| **Jolpica alpha** (`/alpha/core/teams/?year={year}`) | ✅ YES | `primary_color` | `String` (hex, with `#` prefix, e.g. `"#00D7B6"`) | Per team, year-round. **Alpha endpoint — Jolpica is still finalizing the `alpha/core/*` tree** (issue #304). |
| **Jolpica ergast-compatible** (`/ergast/f1/{year}/constructorstandings`) | ❌ NO | — | Constructor object: `{constructorId, url, name, nationality}`. No color. Ergast endpoints explicitly frozen. |

### Decision: hardcoded `TeamColors.forId` for v1

**Why hardcoded, not OpenF1 join:** OpenF1's 30-day rolling window means
the favorites page loses accent colors in winter — the exact moment
casual fans open the app. The lode claim that `team_colour` is "already
wired" is true for the headshot/imagery fallback (which has a session
context) but **misleading for the §3 standings path** which has no
session.

**Why hardcoded, not Jolpica alpha now:** Jolpica themselves say alpha is
unfinished. f1api.dev `teamId` (`"mercedes"`) ≠ Jolpica `name`
(`"Mercedes"`) — needs a small map. Would require a new
`GetTeamColorsUseCase` + in-memory cache + a third HTTP path. Saves
maintenance of a 10-line file but introduces real complexity for the
same data.

**Why hardcoded works:** ~10 liveries/year, ~10-line Kotlin `object`,
same precedent as `Circuits.forId(...)` (23 entries, locked),
`F1API_TO_JOLPICA_CIRCUIT`, and `LEGACY_TEAM_SLUGS`. Stable per season;
touch once a year when liveries change.

### Future migration: Jolpica alpha `primary_color`

When the Jolpica alpha tree stabilizes (per issue #304 they're working
on a community-driven update process for color changes), a one-shot
fetch can replace the hardcoded map. Same data shape (`#RRGGBB` hex),
same `Map<teamId, Color>` in `Wiring`. The hardcoded `TeamColors.forId`
is the v1 answer; the alpha fetch is the v1.x answer.

### Implementation

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

(Values are from the Jolpica alpha live response — same numbers, no `#`,
ARGB Compose `Color`.)

### Hex → Compose `Color` mapping

| Source format | Example | Compose `Color` |
|---|---|---|
| OpenF1 `team_colour` | `"3671C6"` (no `#`) | `Color(0xFF3671C6)` (prepend `FF` for ARGB alpha) |
| Jolpica `primary_color` | `"#00D7B6"` (with `#`) | `Color(0xFF00D7B6)` (strip `#`, prepend `FF`) |

### Invariants captured

- **v1 = hardcoded `TeamColors.forId`.** No API call. No live-window risk. No alpha-endpoint risk. Same shape precedent as `Circuits.forId`, `F1API_TO_JOLPICA_CIRCUIT`, `LEGACY_TEAM_SLUGS`.
- **OpenF1 `team_colour` is rejected for standings path** because of the 30-day live window. Stay correct: it IS appropriate for the headshot/imagery fallback (which has a session context).
- **Jolpica alpha is the future source.** Track issue #304. When the alpha tree stabilizes, replace the hardcoded map with a one-shot fetch.
- **The 10-line file replaces 3 alternative paths** (OpenF1 join, Jolpica alpha now, or a hybrid). Each alternative was more complex for the same data.
- **Unknown teamIds return `Color.Unspecified`.** This is the honest unknown state — no swatch, no fake fallback color.
- **The accent goes on a 6dp surface strip** (mirrors the §3 top-speed 6dp `Circuits.forId(...)` pattern), not on text. The `Color.kt` contract — accent backgrounds on dark, never text — is respected.

## Cross-references

- `lode/wayfinder/f1app/team-accent.md` — research detail + live API responses + Jolpica alpha hex values.
- `lode/wayfinder/f1app/team-imagery.md` — sister file (tier-1 enrichment, formula1.com CDN). Shares the same `team_colour` swatch fallback when no image is available.
- `lode/wayfinder/f1app/tickets/13-additive-ui-enrichments.md` — `team_colour` swatch as the headshot/imagery empty state.
- `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md` — `OPENF1_BASE` per-request base URL; OpenF1 wiring is paid; OpenF1 driver data is session-keyed, not season-keyed.
- Jolpica issue #304: https://github.com/jolpica/jolpica-f1/issues/304 (color field request, status: in progress).
