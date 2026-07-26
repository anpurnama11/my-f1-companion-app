package com.anpurnama.f1_app.f1.data

import java.text.Normalizer

/**
 * Build the Cloudinary formula1.com URL for a driver headshot, and the
 * underlying `driverRef` slug. The slug is a **pure function** of
 * f1api.dev `name` + `surname` plus a constant `01` suffix — no map,
 * no OpenF1 join. See
 * [lode/wayfinder/f1app/cloudinary-headshot-paths.md](../../../../../../../../wayfinder/f1app/cloudinary-headshot-paths.md)
 * for the full probe across all 22 drivers on the 2026 grid (all
 * returned `206 image/webp`).
 */
private fun Char.isCombiningMark(): Boolean =
    category == CharCategory.NON_SPACING_MARK ||
        category == CharCategory.COMBINING_SPACING_MARK

/**
 * Derive the Cloudinary `driverRef` slug from f1api.dev name fields.
 *
 * The rule is `{first 3 of name}{first 3 of last surname token}01`, lowercased.
 * The `NFKD` + strip-combining-marks step is required because
 * f1api.dev emits some surnames with diacritics (`Pérez` is the only
 * one on the 2026 grid; the slug is `serper01`). Producing a pure-
 * ASCII slug means the URL is portable across any HTTP client and
 * works for any accented surname, without depending on the CDN's
 * URL-encoding tolerance.
 *
 * For multi-word surnames the function takes the **last word** and
 * then its first 3 chars. This is required by Antonelli, the only
 * 2026 driver whose f1api.dev `surname` is a multi-word string
 * (`"Kimi Antonelli"`); `take(3)` of the full string would yield
 * `"Kim"`, but the CDN slug is `"andant01"`, which is `"and"` +
 * `"ant"` (last word `Antonelli`, first 3 chars) + `"01"`. Single-
 * word surnames are unaffected — `substringAfterLast(" ")` returns
 * the original string.
 *
 * Returns `null` when [name] or [surname] is blank — the caller then
 * falls back to the swatch with the driver's `shortName` overlaid.
 * An empty `name` would otherwise produce a 5-char slug (e.g.
 * `"per01"`) which 404s on the CDN.
 */
internal fun driverRef(name: String, surname: String): String? {
    if (name.isBlank() || surname.isBlank()) return null
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD)
            .filter { !it.isCombiningMark() }
    val n = norm(name).take(3)
    val s = norm(surname.substringAfterLast(' ')).take(3)
    if (n.isEmpty() || s.isEmpty()) return null
    return (n + s + "01").lowercase()
}

/**
 * Build the Cloudinary URL for a driver headshot, or `null` if the
 * caller has no asset for that driver/team/year.
 *
 * - Returns `null` for year < 2026 (v1 = 2026+ only — the legacy AEM
 *   path is in maintenance and is intentionally not supported).
 * - Returns `null` when [teamId] is not in [TEAM_IMAGE_SLUGS].
 * - Returns `null` when [name] or [surname] is blank (the slug math
 *   would produce a 5-char or shorter ref that 404s on the CDN).
 *
 * @throws IllegalArgumentException if [side] is not `"left"` or `"right"`.
 */
fun driverImageUrl(
    name: String,
    surname: String,
    teamId: String,
    year: Int,
    side: String = "right",
): String? {
    if (year < 2026) return null
    val teamSlug = TEAM_IMAGE_SLUGS[teamId] ?: return null
    require(side in IMAGE_SIDES) { "side must be one of $IMAGE_SIDES, was \"$side\"" }
    val ref = driverRef(name, surname) ?: return null
    return "$F1_CLOUD_BASE/$F1_CLOUD_PRESET/$F1_CLOUD_VERSION/common/f1/$year/$teamSlug/$ref/${year}${teamSlug}${ref}$side.webp"
}
