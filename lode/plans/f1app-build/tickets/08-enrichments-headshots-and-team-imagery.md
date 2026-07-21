---
id: 08
title: Enrichments (driver headshots + team imagery)
type: task
status: ready-for-agent
blocked_by: [02, 04, 05]
owner: ""
---

# 08 — Enrichments (driver headshots + team imagery)

**What to build:** the two imagery enrichments locked as Tier 1. Driver headshots render on `DriverDetail`, Homepage §1 favorite-driver cards, and My Team favorite-driver cards — source OpenF1 `/v1/drivers?driver_number=<n>&session_key=<latest>` `headshot_url`, cached in-memory `Map<driverId, String>`, loaded with Coil, with fallback chain OpenF1 `headshot_url` → Cloudinary `common/f1/{year}/{team}/{driverRef}/` portrait → `team_colour` swatch. Team / car imagery renders on `TeamDetail` (hero car render), Homepage §1 favorite-team card, My Team favorite-team card — source formula1.com Cloudinary `media.formula1.com/.../common/f1/{year}/{team}/...webp` (2026+ legacy path dropped for v1), where the slug → URL is a compile-time constant `teamImageUrl()` in `f1/data/` (no API call), fallback `team_colour` swatch. Weather + race-control flags remain out of scope for v1.

**Blocked by:** 02 — Homepage §1 favorite cards exist; 04 — `DriverDetail`/`TeamDetail` exist; 05 — My Team favorite cards exist.

**Status:** ready-for-agent

## Done when

- [ ] OpenF1 `/v1/drivers?driver_number=<n>&session_key=<latest>` extension on `F1Api.kt`; in-memory `Map<driverId, String>` headshot cache in `Wiring`
- [ ] Coil wired into the Compose tree for image load
- [ ] Headshot fallback chain: OpenF1 `headshot_url` → Cloudinary `common/f1/{year}/{team}/{driverRef}/` portrait → `team_colour` swatch
- [ ] `teamImageUrl()` compile-time constant in `f1/data/` (Cloudinary formula1.com, 2026+ slugs); fallback `team_colour` swatch
- [ ] Driver headshots on `DriverDetail` + Homepage §1 favorite-driver cards + My Team favorite-driver cards (replacing the text/swatch fallback from 04/02/05)
- [ ] Team car imagery on `TeamDetail` hero + Homepage §1 favorite-team card + My Team favorite-team card (replacing the swatch fallback from 04/02/05)
- [ ] No weather / race-control flags in v1 (out of scope)

Spec cross-ref: `lode/specs/f1app.md` (Enrichments), `lode/wayfinder/f1app/team-imagery.md`, `lode/wayfinder/f1app/top-speed-api-wrangling.md`.