package com.anpurnama.f1_app.f1.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wikipedia REST API base URL. Used per-request (the existing
 * `HttpClient` has no default base URL; this follows the same
 * per-request base URL pattern as the f1api.dev and Jolpica
 * extensions in `F1Api.kt`.
 */
const val WIKIPEDIA_REST_BASE = "https://en.wikipedia.org/api/rest_v1"

/**
 * Wikipedia `User-Agent` header value, required by the Wikimedia
 * edge policy. The Wikimedia edge identifies clients by their
 * `User-Agent`; an empty or generic UA (e.g. `okhttp/4.x`) is
 * treated as bot traffic and may be rate-limited or rejected.
 *
 * Update the contact URL in one place if the project ever moves
 * repos. The `HttpClient`'s `HttpCache` plugin covers re-opens
 * (Wikipedia sends `max-age` on real responses; the
 * `MockEngine` tests emulate the header).
 */
const val WIKIPEDIA_USER_AGENT = "F1app/1.0 (https://github.com/anpurnama/F1app)"

/**
 * Editorial summary for a Wikipedia article. Returned by
 * `GET /api/rest_v1/page/summary/{title}`. Reused under CC BY-SA 4.0;
 * attribution is rendered in the "About" section of the
 * DriverDetail / TeamDetail screens (build ticket 14).
 *
 * Wire fields not forwarded (the mapper only needs the five
 * below; `ignoreUnknownKeys = true` on the `HttpClient`'s JSON
 * config tolerates the rest): `type`, `displaytitle`,
 * `namespace`, `wikibase_item`, `pageid`, `originalimage`,
 * `lang`, `dir`, `extract_html`, `content_urls.mobile.page`,
 * `content_urls.desktop.{format,revisions,edit,thumbnail}`,
 * `thumbnail.{original,mime}`.
 *
 * @property title Canonical article title (the Wikipedia REST
 *   endpoint auto-redirects non-canonical slugs, so the input
 *   title from f1api.dev's `url` field may be normalized here —
 *   e.g. `Andrea_Kimi_Antonelli` → `Kimi Antonelli`).
 * @property description One-line article summary from Wikipedia
 *   (e.g. `"Italian racing driver (born 2006)"`). Optional; not
 *   every article populates this field.
 * @property extract Plain-text editorial summary (typically
 *   ~500-800 chars). The "About" body on the detail screen.
 *   Plain text only — the wire also carries `extract_html`, which
 *   we deliberately ignore (HTML rendering in Compose is a
 *   separate, larger surface; the plain-text extract is safer
 *   and matches the Wikipedia iOS/Android app convention).
 * @property contentUrl Canonical article URL on Wikipedia
 *   (`https://en.wikipedia.org/wiki/{canonical_title}`). Used
 *   as the attribution link target in the "About" section
 *   (CC BY-SA 4.0 requires a credit + license link; the article
 *   URL satisfies the credit).
 * @property thumbnail Article main image, when present. The
 *   detail screen may render it as a hero above the "About"
 *   text (build ticket 14 decision; not in this ticket).
 */
@Serializable
data class WikipediaSummary(
    val title: String = "",
    val description: String? = null,
    val extract: String = "",
    val contentUrl: String = "",
    val thumbnail: WikipediaThumbnail? = null,
)

@Serializable
data class WikipediaThumbnail(
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

// Internal wire DTO for the nested `content_urls.desktop.page`
// path. The mapper below flattens it to `WikipediaSummary.contentUrl`
// — only the desktop page URL is forwarded (the mobile URL is the
// same target on all current Android devices, and the attribution
// link in the UI opens in the user's default browser regardless
// of device form factor).
@Serializable
private data class WikipediaContentUrlsDto(
    val desktop: WikipediaContentUrlTargetDto = WikipediaContentUrlTargetDto(),
)

@Serializable
private data class WikipediaContentUrlTargetDto(
    val page: String = "",
)

// Wire DTO. Differs from [WikipediaSummary] only in the nested
// `content_urls.desktop.page` shape — the mapper flattens that to
// the public `contentUrl` field.
@Serializable
private data class WikipediaSummaryDto(
    val title: String = "",
    val description: String? = null,
    val extract: String = "",
    @SerialName("content_urls") val contentUrls: WikipediaContentUrlsDto = WikipediaContentUrlsDto(),
    val thumbnail: WikipediaThumbnail? = null,
)

private fun WikipediaSummaryDto.toSummary(): WikipediaSummary = WikipediaSummary(
    title = title,
    description = description,
    extract = extract,
    contentUrl = contentUrls.desktop.page,
    thumbnail = thumbnail,
)

/**
 * Fetch the Wikipedia REST editorial summary for the given
 * article title. The title is the slug form (underscores, not
 * spaces — matches the f1api.dev `url` field); the endpoint
 * auto-redirects non-canonical slugs to the canonical title and
 * the `WikipediaSummary.title` field carries the canonical form
 * (e.g. `"Kimi Antonelli"`, not the input `"Andrea_Kimi_Antonelli"`).
 *
 * When [forceRefresh] is true, the request carries
 * `Cache-Control: no-cache` to bypass Ktor's HttpCache, matching
 * the convention used in [F1Api.kt]. Pull-to-refresh on the
 * detail screen calls with `forceRefresh = true`; stale-open
 * refreshes call with the default `false`.
 *
 * The URL is built with `URLBuilder.path()` so the title segment
 * is RFC 3986 path-segment encoded. Unreserved characters
 * (`A-Z`, `a-z`, `0-9`, `-`, `_`, `.`, `~`) pass through
 * unchanged; everything else gets percent-encoded. f1api.dev's
 * `url` field uses underscores (e.g.
 * `Andrea_Kimi_Antonelli`) and hyphens (e.g.
 * `Mercedes-Benz_in_Formula_One`); both pass through unchanged.
 */
suspend fun HttpClient.getWikipediaSummary(
    title: String,
    forceRefresh: Boolean = false,
): WikipediaSummary {
    val response = get {
        url {
            protocol = URLProtocol.HTTPS
            host = "en.wikipedia.org"
            // Path is built as five segments so `title` is encoded
            // as a single path segment (RFC 3986). This matches
            // Wikipedia's `/page/summary/{title}` contract: a
            // single title slug with underscores preserved.
            path("api", "rest_v1", "page", "summary", title)
        }
        header(HttpHeaders.UserAgent, WIKIPEDIA_USER_AGENT)
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body<WikipediaSummaryDto>().toSummary()
}
