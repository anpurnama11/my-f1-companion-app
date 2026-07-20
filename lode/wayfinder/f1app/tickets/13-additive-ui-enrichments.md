---
id: 13
title: "Additive UI enrichments (headshots, weather, race-control flags, team imagery)"
type: grilling
status: closed
blocked_by: [04]
owner: ""
---

> **RE-TYPED as grilling** — "which ship, and on which surfaces" is a human
> decision, not a task. The body's default-resolution is the agent's prior take
> (headshots-first), to grill against, not the answer.

## Question

Four additive UI enrichments were parked as bounded follow-ups when
ticket 04 (API scope) closed (and again when the formula1.com CDN
research landed — see [team-imagery.md](../team-imagery.md)). Within
wayfinder, "closed" means the *planning* decision is locked — it does
not mean the code is built. The OpenF1 plumbing (`session_key` join,
`OPENF1_BASE`, `HttpClient` in `Wiring`) is **contractually** scoped
by tickets 04 + 11; the formula1.com CDN wiring is just string
interpolation + Coil (no new endpoint, no new `HttpClient`). Landing
these is cheaper than a greenfield fetch once built, but shipping
them is a separate prioritization. Which ship, and on which surfaces?

## The four enrichments

1. **Driver headshots** — on `DriverDetail` (and the two favorited driver
   cards in Homepage §1 / My Team). Source: OpenF1 `/v1/drivers?driver_number=<n>&session_key=<latest>`
   (`headshot_url` field, a CDN URL). One call per driver; cache the URL
   in memory (the URL is stable per season — `session_key` changes but
   `headshot_url` repeats). Coil for the actual image load.
2. **Weather** — on `RoundDetail`'s circuit block (and Homepage §3 nearest-GP
   card). Source: OpenF1 `/v1/weather?session_key=<k>` (`air temp`,
   `track temp`, `humidity`, `pressure`, `wind direction/speed`, `rainfall`).
   Live session only — no weather for future/scheduled rounds. Empty cell
   outside a live window, same honest-empty rule as ticket 11.
3. **Race-control flags** — on `RoundDetail` live view (yellow/red flag
   periods). Source: OpenF1 `/v1/race_control?session_key=<k>` (`flag`,
   `scope`, `sector`, `message`). Live-window only.
4. **Team & car imagery** — on `DriverDetail` (small car render below
   the driver), `TeamDetail` (hero car + wordmark), and the favorited
   team card in My Team. Source: **formula1.com CDN** (NOT a free API),
   two parallel systems: legacy AEM `www.formula1.com/content/dam/fom-website/teams/{year}/{slug}.png`
   (2023-2025) and Cloudinary `media.formula1.com/image/upload/.../common/f1/{year}/{team}/{year}{team}car{side}.webp`
   (2026+). Slug → URL is a **compile-time constant** (no API call, no
   session_key join, no live-window). Coil loads the image directly.
   The image is season-stable (`max-age=31536000`); same URL all year.
   Empty state = `team_colour` swatch from OpenF1 (already wired in
   ticket 04). Full research in
   [team-imagery.md](../team-imagery.md).

## Decision needed

- **Scope of the initial cut:** all four, or a subset? Ponytail lean:
  ship in **two tiers** —
  - **Tier 1 (no live-window):** headshots + team/car imagery. Both
    are stable per season, both are URL strings (no API call for
    imagery), both show on every screen in every state. Cheapest to
    ship, highest-traffic surfaces.
  - **Tier 2 (live-window):** weather + race-control. Both gated on
    "is there a live session now?", same fan-out pattern from the
    session_key join (ticket 11), both empty outside the live window
    (same honest-empty rule as ticket 11).
- **Cache shape:**
  - Headshot URL — stable per driver/season. In-memory
    `Map<driverId, String>` on the driver repo, or DataStore-persisted
    if we want it to survive cold start. Ponytail lean: in-memory
    first; the OpenF1 call is cheap, re-fetch on cache miss.
  - Team imagery URL — **compile-time constant per (team, year)**,
    no cache needed. Lives in a `Map<Int, Map<String, String>>` next
    to `F1API_TO_JOLPICA_CIRCUIT` in the data layer. Coil handles
    image caching.
- **Live-window reuse:** weather + race-control both need "is there a
  live session now?" — same window logic as the Countdown widget's
  `[FP1_start, race_start + 3h]` (ticket 07). Factor a shared helper or
  duplicate the check? BSSN: duplicate the 3-line check in
  `GetRoundDetailWeatherUseCase` / `GetRaceControlUseCase` first; factor
  if a third caller appears.

## Out of scope

- Telemetry surfaces (RPM/speed/DRS, per-lap times, pit stops) — remain
  out per the map; no free API need for an in-scope screen.
- Headshot alternatives (Wikipedia / driver social) — OpenF1 is the
  free source, and it's already wired. The formula1.com Cloudinary
  `common/f1/{year}/{team}/{driverRef}/` tree is a **fallback** when
  OpenF1's `headshot_url` is null (e.g. the Colapinto case in
  openf1 issue #224), not a primary source.
- Historical weather / past race-control — live window only on first
  cut; historical OpenF1 data exists back to 2023 if needed later.
- In-race action photos (Getty Images content) — formula1.com serves
  these too (e.g. `fom-website/2026/Belgium/GettyImages-2286379204.webp`)
  but the underlying image rights belong to Getty (F1's official photo
  partner) and the URLs are per-event, so they rot between seasons.
  Not safe to ship as a curated image set. The static side-profile
  render is the right curated asset.
- Scraper / HTML parser for formula1.com team pages — the CDN paths
  are public, the slug map is the data, no scraping needed. The
  static-asset CDN endpoints we use (`www.formula1.com/content/dam/...`,
  `media.formula1.com/image/upload/...`) return `access-control-allow-origin: *`
  and are designed for public consumption; Coil GETs are fine. A
  third-party scraper README warns about IP bans for high-volume
  requests, but that applies to HTML scraping, not to CDN asset fetches.

## Resolution

**Scope (Tier 1 only):** Driver headshots + team/car imagery ship on all relevant surfaces. Weather and race-control flags are out of scope for this ticket.

**Surfaces:**
- Headshots: `DriverDetail`, Homepage §1 favorite-driver cards, My Team favorite-driver cards.
- Team/car imagery: `TeamDetail` (hero car render), Homepage §1 favorite-team card, My Team favorite-team card.

**Sources:**
- Headshots: OpenF1 `/v1/drivers` `headshot_url`.
- Team/car imagery: formula1.com Cloudinary `media.formula1.com/.../common/f1/{year}/{team}/...` (2026+ only). Legacy AEM path dropped for v1.

**Fallback chain (headshots):**
1. OpenF1 `headshot_url`
2. Cloudinary `common/f1/{year}/{team}/{driverRef}/` portrait (compile-time URL)
3. `team_colour` swatch from OpenF1

**Team imagery fallback:** `team_colour` swatch when no Cloudinary asset.

**Cache:** Headshot URLs cached in-memory (`Map<driverId, String>`). Team imagery URLs are compile-time constants — no cache needed. Coil handles image caching.

**Live-window enrichments (weather, flags):** Out of scope. If ever needed, they graduate as a fresh ticket with their own `session_key` fan-out scope.

## Default resolution if not decided

Ship in two tiers:

**Tier 1** — headshots + team/car imagery together. Both are
season-stable URL strings, both surface on the highest-traffic
screens (`DriverDetail`, `TeamDetail`, the favorited driver cards in
Homepage §1, the favorited team card in My Team). The
`GetDriverHeadshotUseCase(driverId)` lands over the existing
`HttpClient` (`OPENF1_BASE`); the team-imagery URL is a compile-time
constant. Both Coil-loaded. The team-imagery helper (`teamImageUrl`)
lives next to the existing `F1API_TO_JOLPICA_CIRCUIT` map in
`F1Api.kt`-area (no new package, per ticket 04's domain-purity rule).

**Tier 2** — weather + race-control together. Both live-window-only,
same session_key fan-out, same honest-empty rule. Defer to a
follow-up under this same ticket (or split into 13a/13b if Tier 1
lands cleanly and the live-window pair needs its own scope).

## Cross-references

- Ticket 04: `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md`
  — multi-source wiring; one `HttpClient`, `OPENF1_BASE` paid.
  Domain-purity invariant: no `openf1/` or `team-images/` package —
  `teamImageUrl()` lives in `f1/data/`.
- Ticket 11: `lode/wayfinder/f1app/tickets/11-research-openf1-join-all-time-top-speed.md`
  — `session_key` join via `country_name + year + race-date`, 1-entry
  country map (`Great Britain → United Kingdom`).
- Ticket 07: `lode/wayfinder/f1app/tickets/07-countdown-widget-specifics.md`
  — live-window definition `[FP1_start, race_start + 3h]`.
- [team-imagery.md](../team-imagery.md) — research output for
  enrichment #4. Two CDN systems, slug maps, Cloudinary
  `common/f1/{year}/` tree + legacy AEM `teams/{year}/` tree, both
  verified live on the CDN. 2026 cars ARE uploaded — the upload
  went to a new path, not the legacy one.
- Homepage §1: `lode/wayfinder/f1app/homepage.md` — two favorited driver
  cards (headshot) and the favorited team card (car render) that
  would show the images.
- `next` implementer: see the `~15-line` top-speed use-case sketch in
  ticket 11 for the OpenF1-session-key helper pattern to reuse, and
  the `teamImageUrl()` URL-builder sketch in
  [team-imagery.md](../team-imagery.md) for the formula1.com CDN
  shape.
