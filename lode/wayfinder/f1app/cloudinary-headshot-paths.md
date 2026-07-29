> **Historical research — archived.** Current decisions live in
> [`decisions/`](../../decisions/) and build specs live in [`plans/`](../../plans/).

# Driver headshots on Cloudinary formula1.com — `driverRef` shape (research output)

Research output for ticket 13 / build ticket 08 (parked headshot
sub-decision). Investigates the open question: **does the Cloudinary
formula1.com tree expose a driver-headshot path that does not require
the OpenF1-derived `driverRef` slug, and if not, can `driverRef` be
derived from f1api.dev data alone?**

## TL;DR

**No** `driverId`-based path exists. Every non-`driverRef` shape
probed (`driverId`, `shortName`, car number, no-team-folder, flat
filename) returns `404`. The `driverRef` slug is **the only working
shape** — but it is a **pure function** of f1api.dev `name` and
`surname` plus a constant `01` suffix, **with NFKD + strip-combining-
marks normalization** for accented characters:

```kotlin
private fun Char.isCombiningMark(): Boolean =
    category == CharCategory.NON_SPACING_MARK ||
    category == CharCategory.COMBINING_SPACING_MARK

fun driverRef(name: String, surname: String): String {
    fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD)
            .filter { !it.isCombiningMark() }
    return (
        norm(name).take(3) +
            norm(surname.substringAfterLast(' ')).take(3) +
            "01"
        ).lowercase()
}
```

No hand-maintained `Map<DriverId, String>` is needed. No OpenF1 join
is needed. The slug was verified live on the CDN for **all 22
drivers on the 2026 grid, across all 11 teams, all returning `206
image/webp`**, plus the 11 team car images. The only accented surname
in the 2026 grid is `Pérez` → `serper01` (NFKD-stripped).

## Question

The Cloudinary `common/f1/2026/{team}/...` tree has a known headshot
path that uses `driverRef` (e.g. `/common/f1/2026/audi/nichul01/
2026audinichul01right.webp` — from `team-imagery.md`). But the
`driverRef` was previously sourced from OpenF1's
`driver_number`-keyed `headshot_url` pattern, and OpenF1 is removed
from runtime (ADR 0009). `f1api.dev /current/drivers` returns
`driverId`, `name`, `surname`, `shortName`, `number`, `teamId` — no
field matching the `driverRef` shape. The ticket 08 recut parked
driver headshots with three options:

- A — drop headshots from v1
- B — hand-maintained `Map<DriverId, String>` (~25 entries)
- C — probe Cloudinary for a different path

This research is the answer to **C** (no) and reframes the question:
if `driverRef` is derivable from `name` + `surname`, **B is also
unnecessary** and the real answer is **D: derive it**.

## Method

Three probe passes, run 2026-07-25 against `media.formula1.com`. All
probes used a `Range: bytes=0-1023` GET against
`https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/
v1740000001/common/f1/2026/...`. A `206` (Partial Content) with
`Content-Type: image/webp` and `Content-Length: 1024` is the success
signal. A `404` with `Content-Type: image/gif` and `Content-Length: 0`
is Cloudinary's standard "not found" placeholder.

### Pass 1 — is there a non-`driverRef` path?

9 candidate URL templates × 4 drivers (Verstappen, Norris, Hulkenberg,
Alonso). Full table in
[lode/tmp/probe-cloudinary-headshots.py](../../tmp/probe-cloudinary-headshots.py).

**Result:** every non-`driverRef` pattern returned `404` for every
driver. The `control-driverRef` pattern returned `206` for Verstappen
only — the other three `driverRef` guesses (`lannor04`, `nichul27`,
`feralo14`) were wrong. The team-imagery.md example
(`/common/f1/2026/audi/nichul01/...`) suggested the suffix might not
be the car number; pass 2 verified that.

### Pass 2 — is the suffix constant `01`?

7 drivers across 4 teams, probing both `{name3}{surname-token3}01`
and `{name3}{surname-token3}02` for each. Full table in
[lode/tmp/probe-driverref-suffix.py](../../tmp/probe-driverref-suffix.py).

| driver         | team           | car# | slug `01`   | slug `01` status | slug `02` status |
|----------------|----------------|------|-------------|------------------|------------------|
| max_verstappen | red_bull       | 33   | `maxver01`  | 206              | 404              |
| norris         | mclaren        | 4    | `lannor01`  | 206              | 404              |
| piastri        | mclaren        | 81   | `oscpia01`  | 206              | 404              |
| hulkenberg     | audi           | 27   | `nichul01`  | 206              | 404              |
| bortoleto      | audi           | 5    | `gabbor01`  | 206              | 404              |
| alonso         | aston_martin   | 14   | `feralo01`  | 206              | 404              |
| stroll         | aston_martin   | 18   | `lanstr01`  | 206              | 404              |

**Result:** `01` works for all 7. `02` is 404 for all 7. Car number
is **not** in the slug (sanity-checked: `lannor04`, `oscpia81` both
return 404). The slug rule is:

```
{first 3 chars of f1api.dev name}{first 3 chars of f1api.dev surname}01
```

ASCII-lowercased. The Cloudinary `teamSlug` portion follows the same
map as the team-car imagery (`mercedes` → `mercedes`,
`aston_martin` → `astonmartin`, `red_bull` → `redbullracing`, etc. —
see `team-imagery.md`).

### Multi-word surname edge case

The slug rule above reads `{surname.take(3)}`. f1api.dev's `antonelli`
row has `surname = "Kimi Antonelli"` (two words); naive `take(3)` of
the full string yields `"Kim"`, producing slug `"andkim01"`, which
returns `404 image/gif` on the CDN. The actual slug is `andant01`
(8 chars: `and` + `ant` from the **last word** `Antonelli` + `01`).

The full rule, in code, is therefore:

```kotlin
norm(surname.substringAfterLast(' ')).take(3)
```

— take the last whitespace-separated token of the surname, then the
first 3 chars of that. Single-word surnames are unaffected
(`substringAfterLast(' ')` returns the original string unchanged).
Antonelli is the only 2026 driver with a multi-word surname; the
behavior is pinned by `driverRef takes the last word of a multi-word
surname — Antonelli` in
[`DriverImageTest.kt`](../../../app/src/test/java/com/anpurnama/f1_app/f1/data/DriverImageTest.kt).
Live verification: the corrected 8-char slug `andant01` (path *and*
filename portion) was observed returning `206 image/webp` against
`https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/
v1740000001/common/f1/2026/mercedes/andant01/
2026mercedesandant01right.webp`; the previously-claimed 9-char
variant (with a stray `e` between `t` and `0`, i.e. `andante01`) is
`404 image/gif` and is no longer in the table. A later 20-request
live recheck returned `206 image/webp` for the corrected URL every
time and `404 image/gif` for the mixed/stale URLs every time, so the
apparent inconsistency was probe-label/URL confusion, not evidence
that the slug rule is unstable.

> Compound surnames with a leading particle (`de Vries`,
> `van Gisbergen`) would still hit a trailing-space / leading-particle
> edge case — the rule folds to the last token regardless of whether
> it is a particle. No 2026 driver has a compound surname, so this
> stays a v2 concern; see "What this doesn't address" below.

### Pass 3 — full 2026 grid + Unicode normalization

After the ticket was written, the slug rule was re-probed against the
**entire 2026 grid** — 22 drivers, 11 teams — and the team-slug map
was re-verified end-to-end. Full table in
[lode/tmp/probe-full-grid-2026.py](../../tmp/probe-full-grid-2026.py).

**Team car images** (Section A, 11 teams, all returned `206 image/webp`):

| teamId        | teamSlug       | status |
|---------------|----------------|--------|
| `audi`        | `audi`         | 206    |
| `alpine`      | `alpine`       | 206    |
| `aston_martin`| `astonmartin`  | 206    |
| `cadillac`    | `cadillac`     | 206    |
| `ferrari`     | `ferrari`      | 206    |
| `haas`        | `haas`         | 206    |
| `mclaren`     | `mclaren`      | 206    |
| `mercedes`    | `mercedes`     | 206    |
| **`rb`**      | `racingbulls`  | 206    |
| `red_bull`    | `redbullracing`| 206    |
| `williams`    | `williams`     | 206    |

> **Note on the `rb` key.** f1api.dev uses `rb` as the canonical
> teamId for Racing Bulls (not `racing_bulls`). The original
> `team-imagery.md` slug map inherited the OpenF1-era `racing_bulls`
> key from ticket 16; the corrected map for v1 uses `rb` to match
> what f1api.dev actually returns. `TeamColors.forId(teamId)` already
> handles both keys for backwards compatibility — the slug map only
> needs `rb` to match the production data layer.

**Driver portraits** (Section B, 22 drivers, all returned `206 image/webp`):

| driverId         | team         | car# | surname (raw) | slug         |
|------------------|--------------|------|---------------|--------------|
| `max_verstappen` | `red_bull`   | 33   | Verstappen    | `maxver01`   |
| `hadjar`         | `red_bull`   |  6   | Hadjar        | `isahad01`   |
| `norris`         | `mclaren`    |  4   | Norris        | `lannor01`   |
| `piastri`        | `mclaren`    | 81   | Piastri       | `oscpia01`   |
| `leclerc`        | `ferrari`    | 16   | Leclerc       | `chalec01`   |
| `hamilton`       | `ferrari`    | 44   | Hamilton      | `lewham01`   |
| `russell`        | `mercedes`   | 63   | Russell       | `georus01`   |
| `antonelli`      | `mercedes`   | 12   | Antonelli     | `andant01`  |
| `alonso`         | `aston_martin` | 14 | Alonso        | `feralo01`   |
| `stroll`         | `aston_martin` | 18 | Stroll        | `lanstr01`   |
| `gasly`          | `alpine`     | 10   | Gasly         | `piegas01`   |
| `colapinto`      | `alpine`     | 43   | Colapinto     | `fracol01`   |
| `lawson`         | `rb`         | 30   | Lawson        | `lialaw01`   |
| `lindblad`       | `rb`         | 36   | Lindblad      | `arvlin01`   |
| `bortoleto`      | `audi`       |  5   | Bortoleto     | `gabbor01`   |
| `hulkenberg`     | `audi`       | 27   | Hulkenberg    | `nichul01`   |
| `bearman`        | `haas`       | 87   | Bearman       | `olibea01`   |
| `ocon`           | `haas`       | 31   | Ocon          | `estoco01`   |
| `sainz`          | `williams`   | 55   | Sainz         | `carsai01`   |
| `albon`          | `williams`   | 23   | Albon         | `alealb01`   |
| `bottas`         | `cadillac`   | 77   | Bottas        | `valbot01`   |
| `perez`          | `cadillac`   | 11   | **Pérez**     | **`serper01`** |

All 22 returned `206 image/webp` with the rule
`{name.take(3) + surname.take(3) + "01"}.lowercase()`. The single
accented surname (`Pérez`) returns 206 only when the slug is the
NFKD-stripped form `serper01`. See the Unicode section below.

**Unicode / accent normalization** (Section C, all returned `206
image/webp` — but the canonical slug is the NFKD form):

| Form                       | Slug          | Status | Notes |
|----------------------------|---------------|--------|-------|
| Raw f1api.dev surname      | `serpér01`    | 206    | Works when the URL is percent-encoded (`serp%C3%A9r01`). The CDN accepts the percent-encoded form, but a Kotlin client that doesn't percent-encode would have to be configured to do so. The CDN's tolerance for percent-encoded Unicode is a quirk of the CDN's edge handler, not a contract we can rely on. |
| **NFKD + strip combining** | **`serper01`** | **206** | **Canonical form.** The slug is pure ASCII, no combining marks, lowercased. Works for any HTTP client (no URL-encoding required) and for any accented surname (the rule is general, not `Pérez`-specific). |
| NFKC + strip combining     | `serpér01`    | 206    | NFKC keeps precomposed characters as-is; `é` (U+00E9) stays `é`. This works for `Pérez` only because the CDN also accepts the percent-encoded form; for an HTTP client that doesn't percent-encode, this would 404. NFKD is the safe choice because it produces pure ASCII for *any* input. |
| 3-char surname no-norm     | `serpér01`    | 206    | Same as raw form, just makes the take(3) explicit. |
| 3-char NFKD surname        | `serper01`    | 206    | Same as NFKD form, the explicit version. |

> **Why NFKD over NFKC.** NFKD decomposes precomposed characters
> into base + combining marks (so `é` → `e` + `´`), and stripping
> combining marks gives pure ASCII. NFKC keeps precomposed
> characters (so `é` stays `é`) — for `Pérez` it happens to also be
> a valid slug after percent-encoding, but the rule should be the
> one that works for *any* accented surname (e.g. a future driver
> with `ü` or `ñ`), and that's NFKD.
>
> **What we can and can't claim from these 206s.** The CDN serves
> 206 for all five variants, so we know all of them are accepted by
> the edge. We do **not** know which is the canonical file on disk
> (the CDN could be normalizing/redirecting internally). We pick
> the NFKD form because it produces pure ASCII, which is what we
> want our Kotlin slug to be — independent of CDN normalization
> behavior, independent of HTTP-client URL-encoding, and valid for
> any future accented surname.

**Result:** the slug rule is `{name3}{last-surname-token3}01`
lowercased, after NFKD + strip combining marks. All 22 drivers on the
2026 grid return 206. No driver returns 404, including the one
accented surname (`Pérez` → `serper01`) and the multi-word surname
(`Kimi Antonelli` → `andant01`).

## What this rules out

- **C (probe Cloudinary for a different path)** — no such path exists.
  Every variant of `driverId`, `shortName`, car number, no-team-folder,
  or flat filename returned 404 across the entire probe surface.
- **B (hand-maintained `Map<DriverId, String>`)** — the slug is
  derivable from data already in the response, so the map is
  unnecessary maintenance.
- **OpenF1 join** — `driverRef` is derivable from f1api.dev alone; no
  second API call needed.

## What this rules in

A pure function in `f1/data/`, parallel to the existing
`teamImageUrl()`:

```kotlin
// f1/data/DriverImage.kt — next to TeamImage.kt
private fun Char.isCombiningMark(): Boolean =
    category == CharCategory.NON_SPACING_MARK ||
    category == CharCategory.COMBINING_SPACING_MARK

private fun driverRef(name: String, surname: String): String {
    fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD)
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
    return "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$teamSlug/$ref/${year}${teamSlug}${ref}${side}.webp"
}
```

`TEAM_IMAGE_SLUGS` keys the f1api.dev `teamId` directly: `rb` (not
`racing_bulls`) for Racing Bulls, matching what f1api.dev actually
returns. See the team-slug map in the ticket (`08-imagery.md`).

Caller: `DriverDetail` passes the driver's `name` + `surname` from
`CurrentDriversResponseDto`; `Homepage §3` and `My Team` favorite-driver
cards pass the same from the f1api.dev current-drivers lookup. No new
API call. No map. Same Coil path as `teamImageUrl()`.

## What this doesn't address

- **Unicode normalization** — **resolved in pass 3**. The full 2026
  grid probe confirmed NFKD + strip combining marks is the canonical
  normalization. `Pérez` → `serper01` is the only accented surname
  on the 2026 grid, and it works. f1api.dev normalizes some names
  (Hülkenberg → Hulkenberg) but not all (Pérez stays accented), so
  the normalization must happen at the slug-derivation step, not be
  trusted to the source.
- **The 2026 grid specifically** — **resolved in pass 3**. All 22
  drivers, all 11 teams probed. The slug rule
  `{name3}{last-surname-token3}01` lowercased, with NFKD + strip
  combining marks, returns `206 image/webp` for every driver.
- **Compound surnames** (e.g. `de Vries`). The current rule takes the
  last surname token, so `de Vries` would produce `vri`. That may or
  may not match the CDN's treatment of surname particles. The current
  2026 grid has no such driver, so particle-aware handling stays out
  of v1 scope.
- **Suffix `01` may not be stable across seasons.** Pass 2 proved it
  for 2026 (all 7 second-suffix probes returned 404). If F1 starts
  publishing `02` for second drivers (e.g. when one team's lead
  driver gets a new headshot), the rule needs a fallback or a
  per-season `Map<TeamId, Suffix>`. For v1, `01` is hardcoded.
- **The legacy 2023-2025 AEM path.** The Cloudinary tree is 2026+
  only (per `team-imagery.md`). For historical driver headshots
  (2023-2025), the legacy AEM `content/dam/fom-website/teams/...` path
  is required — and that path uses different slugs (`AlphaTauri`,
  `Kick-Sauber` camelCase). Out of v1 scope; the year guard in
  `driverImageUrl()` returns `null` for year < 2026 and the caller
  falls back to the accent + initials treatment.

## What this is NOT

- **Not an OpenF1 `headshot_url` substitute.** OpenF1 was the primary
  source in the original ticket 08. The Cloudinary path is the
  *fallback* shape that was always there, but it required the
  `driverRef` slug, which OpenF1 used to provide. Without OpenF1,
  the slug is derived from f1api.dev data, but the underlying
  asset is the same Cloudinary file.
- **Not a different CDN.** This is still `media.formula1.com` — the
  same Cloudinary tree the team-car imagery uses (`team-imagery.md`).
- **Not a hand-maintained map.** The `driverRef` is a pure function
  of `name + surname + "01"`, no `Map<DriverId, String>`.

## Implications for ticket 08

The recut parked driver headshots with options A / B / C. This
research eliminates C (no `driverId`-based path) and reframes B (a
map is not needed; the slug is derivable). The real shape of the
recut is:

| Original option | Status after research | Notes |
|---|---|---|
| A — drop headshots | Still available | Cheapest, no code |
| B — hand-maintained `Map<DriverId, String>` | **Subsumed by D** | The map is unnecessary maintenance |
| C — probe Cloudinary | **Dead** | No `driverId`-based path exists |
| **D — derive `driverRef` from `name`+`surname`** | **New option, recommended** | One pure function, no map, same Coil wiring as `teamImageUrl()` |

If the user picks D, ticket 08 grows by ~30 lines of Kotlin (the
`DriverImage.kt` file + three "Done when" lines for the headshot
surfaces on `DriverDetail`, `Homepage §3`, and `My Team`). If the
user picks A, ticket 08 stays as the team/car-only recut and the
parked section is deleted.

## Open follow-ups

- [x] Probe all 22 2026 drivers to confirm the slug rule across the
  full grid — **done in pass 3, all 22 returned 206.** See
  [lode/tmp/probe-full-grid-2026.py](../../tmp/probe-full-grid-2026.py)
  and the "Pass 3" table above.
- [x] Confirm Unicode normalization handles `Pérez` (the only accented
  surname in 2026) — **done in pass 3, NFKD + strip combining marks
  returns 206 for the ASCII form `serper01`.** The percent-encoded
  raw form `serp%C3%A9r01` also returns 206 (CDN edge accepts it),
  but we don't claim it's the canonical file on disk — we choose
  NFKD because it produces pure ASCII, which is the right Kotlin
  slug regardless of CDN normalization. Hülkenberg was probed in
  pass 2 as ASCII (f1api.dev normalizes that one to `Hulkenberg`);
  no accented form exists in the data to probe.
- [ ] Watch F1's Cloudinary uploads over the 2026/2027 off-season for
  any `02`/`03` suffix additions.

## Cross-references

- Ticket 08 (build): `lode/plans/f1app-build/tickets/08-imagery.md` —
  the recut ticket (team/car imagery + driver headshots via derived
  `driverRef`).
- Ticket 13 (wayfinder): `lode/wayfinder/f1app/tickets/13-additive-ui-enrichments.md` —
  the planning ticket, closed.
- `team-imagery.md` — the Cloudinary CDN research, same tree, same
  team-slug map.
- `team-accent.md` — `TeamColors.forId()` swatch fallback (used when
  no image URL is available).
- ADR 0009 — `lode/decisions/0009-remove-openf1-runtime-dependency.md` —
  why `driverRef` is no longer in the runtime data layer.
- `openf1-removal.md` — the source-boundary plan.

## Invariants captured

- **The Cloudinary `common/f1/{year}/` tree requires `driverRef`.**
  No `driverId`-based path, no `shortName`-based path, no car-number
  path. Every other shape is `404 image/gif` (Cloudinary placeholder).
- **`driverRef` is `{first 3 of name}{first 3 of last surname token}01`,
  ASCII-lowercased, with NFKD + strip combining marks before
  `take(3)`.** Probed across all 22 drivers on the 2026 grid, all 11
  teams, all returning 206. Car number is not in the slug. The
  NFKD normalization produces pure ASCII (e.g. `Pérez` → `Perez` →
  `per`), which is the form we use for the slug. The percent-encoded
  Unicode form also works at the CDN, but we don't depend on it —
  the NFKD form works for any HTTP client and any accented input.
- **The team-slug map keys the f1api.dev `teamId`** (e.g. `rb` for
  Racing Bulls, not `racing_bulls`). This matches what f1api.dev
  actually returns in /current/drivers and /current/teams.
- **The rule is pure** — no map, no API call, no OpenF1 dependency.
  The data needed (`name`, `surname`) is already in
  `CurrentDriversResponseDto`.
- **Year guard.** The Cloudinary tree is 2026+; year < 2026 returns
  `null` and the caller falls back to the accent + initials treatment.
- **Suffix `01` is a v1 assumption, not a 2027 promise.** Re-verify
  the slug rule at the next off-season. If a `02` appears, the rule
  needs a per-team suffix map.
