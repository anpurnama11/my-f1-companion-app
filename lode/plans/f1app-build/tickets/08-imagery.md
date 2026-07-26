---
id: 08
title: Driver headshots + team / car imagery (Cloudinary formula1.com, 2026+)
type: task
status: shipped
blocked_by: [02, 04, 05]
owner: ""
---

# 08 — Driver headshots + team / car imagery (Cloudinary formula1.com, 2026+)

> **Recut from the original "Enrichments (driver headshots + team imagery)"
> ticket after ADR 0009 removed OpenF1 from runtime, then extended with
> driver headshots after `lode/wayfinder/f1app/cloudinary-headshot-paths.md`
> proved `driverRef` is derivable from f1api.dev data alone.** No hand-
> maintained `Map<DriverId, String>` is needed — the slug is a pure
> function of `name` + `surname` plus a constant `01` suffix.

**What to build:** two imagery enrichments on the existing screens.

- **Team / car imagery** on `TeamDetail` (hero car render), Homepage §3
  favorite-team row, My Team favorite-team card. Source:
  `media.formula1.com/image/upload/.../common/f1/{year}/{team}/{year}{team}car{side}.webp`
  (Cloudinary formula1.com tree, 2026+ only). The slug → URL is a
  compile-time constant in `f1/data/TeamImage.kt`; no API call, no
  in-memory cache, no `HttpClient` involvement. Coil loads the image
  directly. Fallback when the URL is `null` (no asset for that
  team/year, or year < 2026) is the existing `TeamColors.forId(teamId)`
  swatch — already in `ui/theme/TeamColors.kt`, already consumed by
  all five target screens, returned as `Color.Unspecified` for unknown
  team ids (honest empty state, not a fake accent).

- **Driver headshots** on `DriverDetail` (hero), Homepage §3 favorite-
  driver rows, My Team favorite-driver cards. Source: the same
  Cloudinary tree, path
  `media.formula1.com/image/upload/.../common/f1/{year}/{team}/{ref}/{year}{team}{ref}{side}.webp`,
  where `ref` is derived as `(name.take(3) + surname.take(3) + "01").lowercase()`
  with NFKD + strip combining marks for accented surnames (e.g.
  `maxver01`, `lannor01`, `nichul01`, `feralo01`, `serper01` for
  Pérez — verified live on the CDN, **all 22 drivers across all 11
  teams on the 2026 grid returned `206 image/webp`**, see
  `lode/wayfinder/f1app/cloudinary-headshot-paths.md` Pass 3). No
  map needed, no OpenF1 join needed. Compile-time pure function in
  `f1/data/DriverImage.kt`, importing the team slug map from
  `f1/data/TeamImage.kt`. Fallback when the URL is `null` is the
  `TeamColors.forId(teamId)` swatch with the driver's `shortName`
  overlaid (same treatment the cards have today).

**Blocked by:** 02 — Homepage §3 favorite rows exist; 04 —
`DriverDetail`/`TeamDetail` exist; 05 — My Team favorite cards exist.

**Status:** shipped

## Source boundary (per ADR 0009)

- No OpenF1 call. `OPENF1_BASE` is removed; no `headshot_url`, no
  `team_colour`, no `country_flag`.
- No Jolpica call. Jolpica is for results / pit stops / circuit history,
  not imagery.
- No f1api.dev call. f1api.dev returns no image field.
- Coil is **re-added** as a Compose-tree dependency — it was rolled back
  with `GetCircuitImageUseCase` per the OpenF1-removal plan. The
  dependency lives in `gradle/libs.versions.toml` + `app/build.gradle.kts`
  (KMP-portable: `io.coil-kt.coil3:coil-compose` + `io.coil-kt.coil3:coil-network-ktor3`).
  Used by both headshots and team cars.

## Slug maps + URL builders

```kotlin
// f1/data/TeamImage.kt
private const val F1_CLOUD_BASE    = "https://media.formula1.com/image/upload"
private const val F1_CLOUD_VERSION = "v1740000001"
private const val F1_CLOUD_PRESET  = "c_lfill,w_1320,q_auto"

/**
 * Maps f1api.dev teamId → Cloudinary team folder slug. The keys MUST
 * match what f1api.dev actually returns in /current/drivers and
 * /current/teams — in particular, `rb` (not `racing_bulls`) is the
 * canonical teamId for Racing Bulls. `TeamColors.forId()` already
 * handles both keys for backwards compatibility; the slug map is
 * tighter because the data layer only emits one of them.
 */
internal val TEAM_IMAGE_SLUGS: Map<String, String> = mapOf(
    "audi"          to "audi",
    "alpine"        to "alpine",
    "aston_martin"  to "astonmartin",
    "cadillac"      to "cadillac",
    "ferrari"       to "ferrari",
    "haas"          to "haas",
    "mclaren"       to "mclaren",
    "mercedes"      to "mercedes",
    "rb"            to "racingbulls",
    "red_bull"      to "redbullracing",
    "williams"      to "williams",
)

fun teamImageUrl(teamId: String, year: Int, side: String = "right"): String? {
    if (year < 2026) return null
    val slug = TEAM_IMAGE_SLUGS[teamId] ?: return null
    require(side in setOf("left", "right"))
    return "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$slug/${year}${slug}car$side.webp"
}
```

```kotlin
// f1/data/DriverImage.kt — imports TEAM_IMAGE_SLUGS from TeamImage.kt
import com.anpurnama.f1_app.f1.data.TEAM_IMAGE_SLUGS
import java.text.Normalizer

private fun Char.isCombiningMark(): Boolean =
    category == CharCategory.NON_SPACING_MARK ||
    category == CharCategory.COMBINING_SPACING_MARK

/**
 * Derives the Cloudinary `driverRef` slug from f1api.dev name fields.
 * The rule is `{first 3 of name}{first 3 of last surname token}01`, ASCII-
 * lowercased. The NFKD + strip combining marks step is required
 * because f1api.dev emits some surnames with diacritics (Pérez in
 * 2026); producing a pure-ASCII slug means the URL is portable
 * across HTTP clients and works for any accented surname, without
 * depending on the CDN's URL-encoding tolerance. Verified live on
 * all 22 drivers of the 2026 grid, all returning 206 image/webp —
 * see lode/wayfinder/f1app/cloudinary-headshot-paths.md (Pass 3).
 */
internal fun driverRef(name: String, surname: String): String {
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD)
            .filter { !it.isCombiningMark() }
    return (
        norm(name).take(3) +
            norm(surname.substringAfterLast(' ')).take(3) +
            "01"
        ).lowercase()
}

fun driverImageUrl(
    name: String,
    surname: String,
    teamId: String,
    year: Int,
    side: String = "right",
): String? {
    if (year < 2026) return null
    val teamSlug = TEAM_IMAGE_SLUGS[teamId] ?: return null
    require(side in setOf("left", "right"))
    val ref = driverRef(name, surname)
    return "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$teamSlug/$ref/${year}${teamSlug}${ref}$side.webp"
}
```

`LEGACY_TEAM_SLUGS` is **not shipped** — v1 = 2026+ only, the legacy
AEM tree is in maintenance and the cutover is a single `year >= 2026`
codepath. Per
[lode/wayfinder/f1app/team-imagery.md](../../wayfinder/f1app/team-imagery.md)
and
[lode/wayfinder/f1app/cloudinary-headshot-paths.md](../../wayfinder/f1app/cloudinary-headshot-paths.md).

## Fallback contract

- Caller asks `teamImageUrl(teamId, year)` or
  `driverImageUrl(name, surname, teamId, year)`. If non-null → Coil
  `AsyncImage(model = url)`. If null → render the existing
  `TeamColors.forId(teamId)` swatch, with the team name (or driver
  `shortName`) overlaid.
- Year comes from the call site: `TeamDetail` and `DriverDetail` use
  the current season; `Homepage §3` and `My Team` use the current
  season (no per-row year — all rows render the same year).
- For year < 2026 (e.g., a 2025 historical lookup, which the v1 build
  doesn't currently surface but the data layer supports) the swatch
  fallback is the answer; no warning, no error.

## Done when

- [x] `f1/data/TeamImage.kt` added with `teamImageUrl(teamId, year, side)`
      and the 11-entry `TEAM_IMAGE_SLUGS` map
- [x] `f1/data/DriverImage.kt` added with `driverRef(name, surname)` and
      `driverImageUrl(name, surname, teamId, year, side)`
- [x] Coil 3 (`io.coil-kt.coil3:coil-compose` + `coil-network-ktor3`) added
      to `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [x] `TeamDetail` hero: Coil `AsyncImage` when `teamImageUrl()` is
      non-null, swatch fallback otherwise
- [x] Homepage §3 favorite-team row: Coil `AsyncImage` when
      `teamImageUrl()` is non-null, swatch fallback otherwise
      (replacing the swatch fallback from ticket 02)
- [x] My Team favorite-team card: Coil `AsyncImage` when
      `teamImageUrl()` is non-null, swatch fallback otherwise
      (replacing the swatch fallback from ticket 05)
- [x] `DriverDetail` hero: Coil `AsyncImage` when `driverImageUrl()` is
      non-null, swatch + `shortName` fallback otherwise
- [x] Homepage §3 favorite-driver row: Coil `AsyncImage` when
      `driverImageUrl()` is non-null, swatch + `shortName` fallback
      otherwise (replacing the swatch fallback from ticket 02)
- [x] My Team favorite-driver card: Coil `AsyncImage` when
      `driverImageUrl()` is non-null, swatch + `shortName` fallback
      otherwise (replacing the swatch fallback from ticket 05)
- [x] No `teamImageUrl` / `driverImageUrl` calls for year < 2026
      surface in v1; the `year >= 2026` guard is unit-tested against
      the 2024 / 2025 cases (returns null, no exception)
- [x] No weather / race-control flags in v1 (still out of scope)

## Verify before shipping

These are research-doc follow-ups, not blockers, but should be
confirmed before declaring the ticket done:

- [x] **Full 2026 grid probe.** Done in
      [lode/tmp/probe-full-grid-2026.py](../../tmp/probe-full-grid-2026.py)
      (Pass 3 of the headshot research). All 22 drivers across all 11
      teams returned 206 image/webp for the derived slug. The
      team-slug map is also verified end-to-end (all 11 team car
      images returned 206). One bug caught: the slug map originally
      keyed `racing_bulls` for Racing Bulls; f1api.dev emits `rb`.
      Fixed in the map above — `TEAM_IMAGE_SLUGS["rb"] = "racingbulls"`.
      `TeamColors.forId()` already handles both keys for backwards
      compatibility.
- [x] **Unicode normalization.** Done in the same Pass 3 probe.
      `Pérez` is the only accented surname on the 2026 grid; the
      canonical slug is `serper01` (NFKD + strip combining marks).
      The CDN also accepts the percent-encoded form `serp%C3%A9r01`
      (a CDN edge quirk), but we don't depend on it — the NFKD form
      is pure ASCII, works for any HTTP client, and works for any
      accented surname. The `norm()` helper in the `driverRef()`
      snippet above is the one-liner that handles this. A unit test
      asserting `driverRef("Sergio", "Pérez") == "serper01"` and
      `driverImageUrl("Sergio", "Pérez", "cadillac", 2026) ==
      "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/
      v1740000001/common/f1/2026/cadillac/serper01/
      2026cadillacserper01right.webp"` is cheap insurance.
- **Suffix `01` is a v1 assumption.** Re-verify the rule at the next
  off-season. If a `02` appears, the rule needs a per-team suffix map.

Spec cross-ref: `lode/specs/f1app.md` (Enrichments),
`lode/wayfinder/f1app/team-imagery.md`,
`lode/wayfinder/f1app/team-accent.md` (fallback source),
`lode/wayfinder/f1app/cloudinary-headshot-paths.md` (driverRef rule).
