package com.anpurnama.f1_app.f1.data

/**
 * Build the Cloudinary formula1.com URL for a team's car / hero image.
 *
 * The asset is season-static (no per-round URL change) and lives on
 * F1's own Cloudinary tree, which serves `cache-control: max-age=31536000`
 * — Coil's in-memory cache plus the CDN cache together cover a season
 * with a single fetch. See
 * [lode/wayfinder/f1app/team-imagery.md](../../../../../../../../wayfinder/f1app/team-imagery.md)
 * for the full research. v1 = 2026+ only; year < 2026 returns null so
 * the caller falls back to a [com.anpurnama.f1_app.ui.theme.TeamColors] swatch.
 */
internal const val F1_CLOUD_BASE = "https://media.formula1.com/image/upload"
internal const val F1_CLOUD_VERSION = "v1740000001"
internal const val F1_CLOUD_PRESET = "c_lfill,w_1320,q_auto"

/**
 * f1api.dev `teamId` → Cloudinary folder slug for the 2026+ tree.
 *
 * Keys are exactly the `teamId` values f1api.dev returns from
 * `/current/drivers` and `/current/teams` — in particular `rb` (not
 * `racing_bulls`) for Racing Bulls. `TeamColors.forId()` already
 * accepts both for backwards compatibility; this map is tighter
 * because the data layer only emits one of them.
 *
 * Verified live on the CDN for all 11 teams on the 2026 grid —
 * see [lode/wayfinder/f1app/cloudinary-headshot-paths.md](../../../../../../../../wayfinder/f1app/cloudinary-headshot-paths.md)
 * (Pass 3, Section A).
 */
internal val TEAM_IMAGE_SLUGS: Map<String, String> = mapOf(
    "audi" to "audi",
    "alpine" to "alpine",
    "aston_martin" to "astonmartin",
    "cadillac" to "cadillac",
    "ferrari" to "ferrari",
    "haas" to "haas",
    "mclaren" to "mclaren",
    "mercedes" to "mercedes",
    "rb" to "racingbulls",
    "red_bull" to "redbullracing",
    "williams" to "williams",
)

/** Valid `side` values for [teamImageUrl] / [driverImageUrl]. */
internal val IMAGE_SIDES: Set<String> = setOf("left", "right")

/**
 * Build the Cloudinary URL for a team-car render, or `null` if the
 * caller has no asset for that team/year. The 2026+ path exposes two
 * orientations per team (`carleft.webp`, `carright.webp`); the
 * pre-2026 legacy AEM tree is intentionally **not** supported in v1.
 *
 * @throws IllegalArgumentException if [side] is not `"left"` or `"right"`.
 */
fun teamImageUrl(teamId: String, year: Int, side: String = "right"): String? {
    if (year < 2026) return null
    val slug = TEAM_IMAGE_SLUGS[teamId] ?: return null
    require(side in IMAGE_SIDES) { "side must be one of $IMAGE_SIDES, was \"$side\"" }
    return "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$slug/${year}${slug}car$side.webp"
}
