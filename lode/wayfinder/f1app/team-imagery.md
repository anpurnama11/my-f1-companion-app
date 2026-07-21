# Team & car imagery — formula1.com CDN (GAP-D)

Research output for ticket 13. Source of truth for anyone implementing
team logos / car renders on **`DriverDetail`**, **`TeamDetail`**, the
two favorited driver cards in **Homepage §1**, and the favorited team
card in **My Team**. Documents the two parallel CDN systems, slug
maps, costs, and which surface gets which format.

## Decision (current)

**Use formula1.com's own CDN, not a third-party image source.** Two
parallel systems exist — both verified live on the production CDN,
July 2026, week of the 10th GP:

| Path style | Years | Format | Resolution | Both sides? | Slug style |
|---|---|---|---|---|---|
| Legacy AEM `www.formula1.com/content/dam/fom-website/teams/{year}/{slug}.png` | 2023-2025 | PNG | 465×138 | No (one orientation) | inconsistent (`mercedes`, `aston-martin`, `Kick-Sauber`, `RB`) |
| Cloudinary `media.formula1.com/image/upload/.../common/f1/{year}/{team}/{year}{team}car{side}.webp` | 2026+ | WebP | up to 3392×746 | Yes (`carleft`, `carright`) | clean lowercase, one word (`mercedes`, `astonmartin`, `racingbulls`, `audi`, `cadillac`) |

**No live-window dependency** — these are static per-team/season
assets, no `session_key` join, no `OPENF1_BASE` round-trip, no
OpenF1 rate limit. The image is immutable for the season; CDN
returns `cache-control: max-age=31536000`.

**No API call either.** The image URL is a **constant per team/year
the app ships**, not something fetched at runtime. Slug → URL is a
compile-time string interpolation; Coil loads the image directly.
Same shape as the existing `F1API_TO_JOLPICA_CIRCUIT` map in
`F1Api.kt` (tickets 04 + 09): small `Map<TeamId+Year, String>` next
to it, with a fallback for missing entries (render the team colour
swatch — `team_colour` from OpenF1 — instead of an image).

## Slug maps (verified live on the CDN, 18 July 2026)

### 2026+ (Cloudinary `common/f1/{year}/{team}/`)

```
audi, alpine, astonmartin, cadillac, ferrari, haas, mclaren,
mercedes, racingbulls, redbullracing, williams
```

11 teams. Both `carleft.webp` and `carright.webp` exist per team.
Slugs are all lowercase, one word, no separators. Cloudinary version
is `v1740000001` (Jan 2025 epoch; the `v…` segment is a cache-bust
key — swap it for a new value when the image is re-uploaded).

### 2023-2025 (legacy AEM `teams/{year}/{slug}.png`)

The slugs are **inconsistent and not portable across years** —
rebrand = slug change. Slug map (verified by probing the CDN, ~60
candidates):

| Team | 2023 | 2024 | 2025 |
|---|---|---|---|
| Mercedes | `mercedes` | `mercedes` | `mercedes` |
| Ferrari | `ferrari` | `ferrari` | `ferrari` |
| McLaren | `mclaren` | `mclaren` | `mclaren` |
| Alpine | `alpine` | `alpine` | `alpine` |
| Williams | `williams` | `williams` | `williams` |
| Haas | `haas` | `haas` | `haas` |
| Red Bull | `red-bull` | `red-bull-racing` | `red-bull-racing` |
| Aston Martin | `aston-martin` | `aston-martin` | `aston-martin` |
| AlphaTauri (2023-2024) | `AlphaTauri` ⚠️ camelCase | `AlphaTauri` ⚠️ | — |
| RB (2024 only) | — | `RB` ⚠️ all-caps | — |
| Racing Bulls (2025-) | — | — | `racing-bulls` |
| Kick Sauber (2024-) | — | `Kick-Sauber` ⚠️ camel | `Kick-Sauber` ⚠️ |
| Stake F1 (2024 only, no asset) | — | ❌ no image | — |

The camelCase / all-caps slugs are a CMS quirk, not a typo. They
have to be matched exactly. The legacy path is also **not getting
new uploads** — when F1 migrates fully to the Cloudinary tree, this
folder goes dark. Plan for the cutover.

### 2026+ driver portraits (bonus find)

The same Cloudinary `common/f1/{year}/{team}/` tree also holds
per-driver portraits — useful if OpenF1's `headshot_url` is ever
missing (Colapinto was the openf1 issue #224 case):

```
/common/f1/2026/{team}/{driverRef}/{year}{team}{driverRef}{side}.webp
e.g. /common/f1/2026/audi/nichul01/2026audinichul01right.webp
```

Driver refs match the OpenF1 `driver_number`-keyed headshot URL
pattern (`MAXVER01`, `LEWHAM01` …), so the slug is already in the
data layer.

## URL builders (for the implementer)

```kotlin
// f1/data/TeamImage.kt — next to the existing F1API_TO_JOLPICA_CIRCUIT map
private const val F1_LEGACY_BASE   = "https://www.formula1.com/content/dam/fom-website/teams"
private const val F1_CLOUD_BASE    = "https://media.formula1.com/image/upload"
private const val F1_CLOUD_VERSION = "v1740000001"
private const val F1_CLOUD_PRESET  = "c_lfill,w_1320,q_auto"

fun teamImageUrl(teamSlug: String, year: Int, side: String = "right"): String? =
    if (year >= 2026)
        "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$teamSlug/${year}${teamSlug}car$side.webp"
    else
        LEGACY_TEAM_SLUGS[year]?.get(teamSlug)
            ?.let { "$F1_LEGACY_BASE/$year/$it.png" }

// Caller falls back to a `team_colour` swatch when the URL is null
// (no asset for that year/team — Stake F1 2024, retired constructors, etc.)
```

`LEGACY_TEAM_SLUGS` is a `Map<Int, Map<String, String>>` — one entry
per team per year, ~30 rows total. Compile-time constant. Add a row
when a new season uploads, delete a row when a constructor retires.

## Cost summary

| Operation | Calls | Bytes | Notes |
|---|---|---|---|
| Build the URL | 0 | 0 | String interpolation, no network. |
| Coil image load (first time) | 1 | 200-300 KB | WebP, transparent background, CDN `max-age=31536000`. |
| Coil image load (warm cache) | 0 | 0 | In-memory after first load. |

**No session_key, no API key, no rate limit.** This is a static
asset, not a query. The image is immutable per season; the same
URL serves the same car all year.

## What this is NOT

- **Not a car photo (in-race action).** This is a clean side-profile
  studio render of the season's car (F1-commissioned, not
  Getty-licensed). In-race action photos exist on the same CDN too
  (e.g. `fom-website/2026/Belgium/GettyImages-2286379204.webp` —
  the `GettyImages-` prefix in the filename is the giveaway) but
  the underlying image rights belong to Getty (F1's official photo
  partner) and the URLs are per-event. Not safe to ship as a curated set.
- **Not the team logo (wordmark).** The 465×138 PNG **is** the car,
  not a logo — see the Mercedes image fetched in research
  (livery + Petronas + IWC + CrowdStrike, all on the car body). If
  you want a wordmark, it's at
  `media.formula1.com/.../common/f1/{year}/{team}/{year}{team}logowhite.webp`
  (also verified live, same tree). Slug map matches the car slugs.
- **Not the only way to get a car image.** Ergast/Jolpica and Pit
  Lane F1 have no car imagery (per the prior research). Wikipedia
  has car images but URLs are page-bound, not stable per season.
  Formula1.com CDN is the only stable, free, no-rate-limit source.

## Out of scope (parked)

- **Weather, race-control flags** — still separate enrichments
  under ticket 13 (live-window-only, OpenF1).
- **Driver headshots** — first item on the ticket's list; the
  Cloudinary `common/f1/{year}/{team}/{driverRef}/` tree is a
  fallback only when OpenF1 `headshot_url` is null.
- **Country flags** — available via OpenF1's `country_flag` field
  (not yet consumed).
- **Circuit images** — shipped via OpenF1's `meetings.circuit_image`
  field. Loaded with Coil `AsyncImage` on the Homepage §1 countdown
  card; `GetCircuitImageUseCase(year, country)` wraps the
  `/v1/meetings` lookup and reuses the `F1API_TO_OPENF1_COUNTRY`
  Silverstone fallback. The image is best-effort: missing images
  render the card without the decorative track layout.
- **Pre-2023 seasons** — the legacy AEM tree was 2023 onwards.
  Older constructors/years show the team-colour swatch fallback.

## Cross-references

- Ticket 13: `lode/wayfinder/f1app/tickets/13-additive-ui-enrichments.md`
  — the open grilling ticket this research feeds; team imagery
  is the 4th candidate after headshots, weather, race-control.
- Ticket 04: `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md`
  — `F1API_TO_JOLPICA_CIRCUIT` map is the precedent for the
  slug map shape; same file (`F1Api.kt`-area, no new package).
- Ticket 09: `lode/wayfinder/f1app/tickets/09-research-most-wins-at-circuit.md`
  — multi-source precedent; this research adds a **third
  source domain** (formula1.com CDN), but still no new package —
  helpers stay in `f1/`.
- `lode/terminology.md` — `F1Api` extension shape, `Wiring`,
  `HttpCache`. Coil is the image loader; see `lode/practices.md`.

## Invariants captured

- **formula1.com CDN is the source of truth for car + team imagery.**
  No scraping, no third-party host, no free-API alternative exists.
- **Two CDN systems run in parallel.** Legacy AEM
  (`www.formula1.com/content/dam/...`) for 2023-2025, Cloudinary
  (`media.formula1.com/image/upload/.../common/f1/`) for 2026+.
  The implementer must handle both; a `year >= 2026` branch is the
  right cutover (no team-name overlap on either side).
- **2026 Cloudinary slugs are clean** (lowercase, one word, no
  separators). 2023-2025 AEM slugs are inconsistent
  (`AlphaTauri` camelCase, `RB` all-caps, `Kick-Sauber` camel).
  Use the map; don't derive slugs at runtime.
- **Both sides exist on the 2026+ path** (`carleft`, `carright`).
  The legacy path is single-orientation only — pick a default
  (right) and accept the asymmetry on 2023-2025.
- **The image is a season-stable constant** — same URL all year.
  No re-fetch, no cache invalidation, `max-age=31536000` already
  on the CDN response.
- **No live-window dependency.** Unlike weather / race-control
  flags (also under ticket 13), team imagery shows on every
  screen, every state, every round.
- **A team-colour swatch is the honest empty state** — the
  `team_colour` hex from OpenF1 (already wired in ticket 04's
  OpenF1 extension) renders as a coloured surface when no image
  URL is available. No "—" / "N/A" placeholders.
- **The legacy AEM path is in maintenance, not active development.**
  When F1 fully migrates to the Cloudinary tree, the
  `LEGACY_TEAM_SLUGS` map goes away. Plan for a single
  `year >= 2026` codepath becoming the only codepath.

## Lessons learned

- **The 2026 path was a find, not a probe.** The user's "10th GP
  and the image isn't uploaded" was the right instinct but the
  wrong tree — F1 uploaded 2026 cars to a new Cloudinary-backed
  CDN path (`common/f1/2026/`), not the legacy AEM
  `teams/2026/` path. **Always check F1's own site source** for
  the current asset path, not the path that worked last season.
- **CDN URL patterns break on rebrand, not on tech.**
  `AlphaTauri → RB` (2024) and `VisaCashAppRB → Racing Bulls`
  (2025) and `Kick Sauber → Audi` (2026) all changed slugs. The
  legacy slug map is a rebrand-history table, not a translation
  table.
- **The Cloudinary `image/upload/{preset}/v{version}/path` shape is
  informative** — `c_lfill,w_1320,q_auto` is a real Cloudinary
  fetch URL transform. The `v1740000001` is a version segment,
  bumped when the asset is re-uploaded. Treat it as part of the
  URL, not a cache buster you can rotate.
- **Always probe a public CDN for the "missing" image you assume
  doesn't exist.** The 2026 assets are public, on F1's own CDN,
  with predictable structure. The research cost was 5 minutes
  of URL probes once we knew the team page existed.
- **"The image is the data"** — for a static-per-season asset,
  the URL is the data. Compile-time `Map<TeamId+Year, String>` is
  the right shape; do not fetch a manifest, do not parse the
  formula1.com HTML at runtime, do not introduce a `team_images`
  endpoint on a free API that doesn't have one.
