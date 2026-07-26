---
id: 13
title: Wikipedia REST extension
type: task
status: ready
blocked_by: [28]
owner: ""
---

# 13 — Wikipedia REST extension

**What to build:** per the locked decision in wayfinder ticket 28,
the data layer for the "About" biography text on the redesigned
DriverDetail / TeamDetail screens. One new file at
`app/src/main/java/com/anpurnama/f1_app/f1/data/WikipediaApi.kt`
holding the `WIKIPEDIA_REST_BASE` const, the `WIKIPEDIA_USER_AGENT`
const, the `WikipediaSummary` DTO, and the
`HttpClient.getWikipediaSummary(title: String): WikipediaSummary`
extension. The extension sets the Wikipedia-required
`User-Agent` header on every call, uses the per-request base URL
pattern (the same pattern as the old OpenF1 call — see
[`core/network.md`](../../../core/network.md)), and rides on the
existing `HttpCache` plugin so re-opens don't re-fetch.

**Concretely:**

- New file: `app/src/main/java/com/anpurnama/f1_app/f1/data/WikipediaApi.kt`
  holding:
  - `const val WIKIPEDIA_REST_BASE = "https://en.wikipedia.org/api/rest_v1"`
  - `const val WIKIPEDIA_USER_AGENT = "F1app/1.0 (https://github.com/anpurnama/F1app)"`
  - `@Serializable data class WikipediaSummary(...)` with the
    five fields the screen needs (see DTO shape below).
  - `suspend fun HttpClient.getWikipediaSummary(title: String): WikipediaSummary`
    extension.
- The extension builds the URL with the Ktor `URLBuilder` `path()`
    method so the title segment is RFC 3986 path-encoded (the
    Wikipedia API expects `/page/summary/{title}` with the title
    already URL-encoded; underscores and hyphens pass through, spaces
    become `%20`). Final URL shape:
    `https://en.wikipedia.org/api/rest_v1/page/summary/{encodedTitle}`.
- The extension sets `header(HttpHeaders.UserAgent, WIKIPEDIA_USER_AGENT)`
    on every call (required by Wikipedia's API etiquette — the
    Wikimedia edge rejects unidentified clients).
- **No change to `HttpClientFactory`**: the existing `HttpCache` +
    `FileStorage` already covers the Wikipedia call. The plugin
    honors the server's `max-age` headers (Wikipedia sends ~24h for
    summary); the second call within the TTL is served from disk.
- **No change to `Wiring`** in this ticket: the extension is
    reachable from the same `HttpClient` the rest of the app uses.
- **No change to `GetDriverDetailUseCase` / `GetTeamDetailUseCase`
    in this ticket**: the use case join change (F1DB catalog +
    Wikipedia summary both arriving in one use case update) lands
    in build ticket 14 (UI rewrite / wayfinder 29). This ticket
    delivers the data layer only; the use case keeps its current
    shape. See "Scope split" below.
- New JVM unit test:
  `app/src/test/java/com/anpurnama/f1_app/f1/data/WikipediaApiTest.kt`.
  Five `@Test` methods cover the acceptance criteria
  (see "Tests" below).

**Blocked by:** 28 (wayfinder planning ticket — closed in the
resolution that produced this build ticket).

**Status:** ready

## Done when

- [x] `app/src/main/java/com/anpurnama/f1_app/f1/data/WikipediaApi.kt`
      exists with the `WIKIPEDIA_REST_BASE` const, the
      `WIKIPEDIA_USER_AGENT` const, the `WikipediaSummary` DTO, and
      the `getWikipediaSummary` extension
- [x] The DTO carries exactly five fields: `title`, `description`,
      `extract`, `contentUrl`, `thumbnail?` (thumbnail nullable
      because not every article has one)
- [x] The extension sets `User-Agent: F1app/1.0 (https://github.com/anpurnama/F1app)`
      on every call (Wikipedia etiquette)
- [x] The extension URL-encodes the title as a path segment
      (underscores and hyphens pass through, spaces become `%20`)
- [x] `WikipediaApiTest` (JVM unit) covers all four acceptance
      criteria from wayfinder ticket 28 plus a fifth cache-hit test
      (see "Tests" below)
- [x] `./gradlew :app:compileDebugKotlin`,
      `./gradlew :app:testDebugUnitTest`,
      `./gradlew :app:assembleDebug`, and
      `./gradlew :app:assembleRelease` all green (222 tests
      pass; debug APK 17 MB, release APK 2.4 MB; R8 minified)
- [x] `THIRD_PARTY_NOTICES.md` gains a Wikipedia REST section
      crediting CC BY-SA 4.0
- [x] `lode/leaderboard/summary.md` "Planned: GAP-F detail-page
      redesign" section is updated to remove the "Wikipedia REST
      extension (tbd)" placeholder now that the data layer is
      built (use case join still pending build ticket 14)
- [x] No new runtime network calls beyond the one Wikipedia fetch
      per detail open (HttpCache covers re-opens)
- [x] No new Ktor base URL constant in `F1Api.kt` (the Wikipedia
      call is a different source with a different per-request base
      URL; lives in `WikipediaApi.kt`)
- [x] No `android.*` imports in `WikipediaApi.kt` (domain-purity
      invariant preserved)

## DTO shape

```kotlin
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
```

The wire DTO has `content_urls.desktop.page` (a nested object);
the mapper flattens to a single `contentUrl: String` because only
the desktop page URL is used (the mobile URL is the same target on
all current Android devices and the attribution link in the UI
opens in the user's default browser regardless of device form
factor). The wire DTO also has `extract_html`; the screen uses
`extract` (plain text) only — the plain-text version is safer to
render and matches the Wikipedia iOS/Android app convention.

The wire DTO carries many more fields (`type`, `displaytitle`,
`namespace`, `wikibase_item`, `pageid`, `originalimage`, `lang`,
`dir`, `extract_html`, `content_urls.mobile.page`, etc.) but the
mapper only forwards the five the screen needs. `ignoreUnknownKeys
= true` on the `HttpClient`'s JSON config means the unmapped
fields deserialise silently (no error on extra fields, the
tolerance is per [`core/network.md`](../../../core/network.md)
`HttpClientFactory`).

## Extension shape

```kotlin
suspend fun HttpClient.getWikipediaSummary(title: String): WikipediaSummary {
    val response = get {
        url {
            protocol = URLProtocol.HTTPS
            host = "en.wikipedia.org"
            path("api", "rest_v1", "page", "summary", title)
        }
        header(HttpHeaders.UserAgent, WIKIPEDIA_USER_AGENT)
    }
    return response.body()
}
```

`URLBuilder.path("api", "rest_v1", "page", "summary", title)` builds
the path as five segments and URL-encodes each segment per RFC
3986 path-segment rules. Unreserved characters (`A-Z`, `a-z`,
`0-9`, `-`, `_`, `.`, `~`) pass through unchanged; everything else
gets percent-encoded. This means:
- `Andrea_Kimi_Antonelli` → `Andrea_Kimi_Antonelli` (unchanged).
- `Mercedes-Benz_in_Formula_One` → `Mercedes-Benz_in_Formula_One` (unchanged).
- A title with a space (`Max Verstappen`) would become `Max%20Verstappen`,
  but the f1api.dev `url` field already uses underscores, so the
  mapper will only ever see underscored titles in practice.

**No `forceRefresh` parameter** on this extension. Wikipedia
content is effectively immutable within a day; a pull-to-refresh
on the detail screen doesn't need a force-refresh escape hatch.
The cache TTL (~24h) is the user's "refresh window" — re-opening
the screen within a day serves the cached body. If a future
ticket needs force-refresh, add the `Cache-Control: no-cache`
header the same way the f1api.dev extensions do
(see [`f1/data/F1Api.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/f1/data/F1Api.kt)
for the `forceRefresh` shape).

## Tests

One JVM unit test file at
`app/src/test/java/com/anpurnama/f1_app/f1/data/WikipediaApiTest.kt`,
mirroring the existing `F1ApiTest` MockEngine shape. Five
`@Test` methods, one per acceptance criterion plus a fifth
cache-hit verification:

```kotlin
class WikipediaApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
    }

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType, ContentType.Application.Json.toString(),
            // Wikipedia sends these on real responses — emulate so the
            // HttpCache plugin caches the response.
            HttpHeaders.CacheControl, "public, max-age=86400",
        ),
    )

    @Test
    fun `getWikipediaSummary for Antonelli returns canonical title and sets User-Agent`() = runTest {
        var capturedPath: String? = null
        var capturedUserAgent: String? = null
        val client = mockClient { req ->
            capturedPath = req.url.fullPath
            capturedUserAgent = req.headers[HttpHeaders.UserAgent]
            jsonOk("""
                {
                  "title": "Kimi Antonelli",
                  "description": "Italian racing driver (born 2006)",
                  "extract": "Andrea Kimi Antonelli is an Italian racing driver...",
                  "content_urls": { "desktop": { "page":
                    "https://en.wikipedia.org/wiki/Kimi_Antonelli" } }
                }
            """.trimIndent())
        }

        val summary = client.getWikipediaSummary("Andrea_Kimi_Antonelli")

        assertEquals("Kimi Antonelli", summary.title)
        assertEquals("https://en.wikipedia.org/wiki/Kimi_Antonelli", summary.contentUrl)
        assertEquals("Italian racing driver (born 2006)", summary.description)
        assertEquals("/api/rest_v1/page/summary/Andrea_Kimi_Antonelli", capturedPath)
        assertTrue(
            "expected User-Agent header, was: $capturedUserAgent",
            capturedUserAgent?.startsWith("F1app/1.0") == true,
        )
    }

    @Test
    fun `getWikipediaSummary for Mercedes in F1 returns canonical hyphenated title`() = runTest {
        val client = mockClient {
            jsonOk("""
                {
                  "title": "Mercedes-Benz in Formula One",
                  "description": "Formula One activities of Mercedes-Benz",
                  "extract": "Mercedes-Benz, a German automotive brand...",
                  "content_urls": { "desktop": { "page":
                    "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One" } }
                }
            """.trimIndent())
        }

        val summary = client.getWikipediaSummary("Mercedes-Benz_in_Formula_One")

        assertEquals("Mercedes-Benz in Formula One", summary.title)
        // Hyphen survives path-segment encoding (hyphen is unreserved).
        assertEquals("https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One",
                     summary.contentUrl)
    }

    @Test
    fun `getWikipediaSummary passes through underscores in the title segment`() = runTest {
        var capturedPath: String? = null
        val client = mockClient { req ->
            capturedPath = req.url.fullPath
            jsonOk("""
                { "title": "Kimi Antonelli",
                  "extract": "...",
                  "content_urls": { "desktop": { "page": "..." } } }
            """.trimIndent())
        }

        client.getWikipediaSummary("Andrea_Kimi_Antonelli")

        // Underscores survive path-segment encoding (sub-delim, allowed).
        assertEquals("/api/rest_v1/page/summary/Andrea_Kimi_Antonelli", capturedPath)
    }

    @Test
    fun `getWikipediaSummary tolerates articles with no thumbnail`() = runTest {
        val client = mockClient {
            jsonOk("""
                { "title": "Kimi Antonelli",
                  "extract": "...",
                  "content_urls": { "desktop": { "page": "..." } } }
            """.trimIndent())
        }

        val summary = client.getWikipediaSummary("Kimi_Antonelli")

        assertNull(summary.thumbnail)
        assertNull(summary.description)
    }

    @Test
    fun `getWikipediaSummary cache-hits on the second call within TTL`() = runTest {
        val callCount = AtomicInteger(0)
        val cacheDir = kotlin.io.path.createTempDirectory("f1app-wiki-cache-").toFile()
        try {
            val client = HttpClient(MockEngine { _ ->
                callCount.incrementAndGet()
                jsonOk("""
                    { "title": "Kimi Antonelli",
                      "extract": "...",
                      "content_urls": { "desktop": { "page": "..." } } }
                """.trimIndent())
            }) {
                expectSuccess = true
                install(ContentNegotiation) { json(json) }
                install(HttpCache) { publicStorage(FileStorage(cacheDir)) }
            }

            // First call: hits the engine.
            client.getWikipediaSummary("Kimi_Antonelli")
            // Second call: served from the disk cache; engine is NOT called again.
            client.getWikipediaSummary("Kimi_Antonelli")

            assertEquals("expected HttpCache to serve the second call", 1, callCount.get())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
```

The fifth test is the HttpCache hit verification. It builds a
`HttpClient` with `HttpCache` + `FileStorage` in a temp dir plus
the `MockEngine`, makes two calls to the same URL, and asserts the
`MockEngine` was only invoked once. The temp dir is cleaned up in
the `finally` block. This pattern is a fresh shape (the existing
`F1ApiTest` doesn't install `HttpCache` because the f1api.dev
extensions don't need to verify the cache plugin in isolation) —
documented here for any future per-source cache tests.

## Scope split — use case join change rides with the UI rewrite

Wayfinder ticket 28 mentions "one line in the `GetDriverDetailUseCase` /
`GetTeamDetailUseCase` join — pass the f1api.dev `url` slug
(auto-redirects to canonical title) as the input." That change is
deferred to build ticket 14 (the UI rewrite ticket for wayfinder 29)
so both data sources (F1DB catalog from build ticket 12 + Wikipedia
from this ticket) land in one use case update. The reasoning:

- The F1DB catalog (build ticket 12) changes the use case's
  constructor signature (gains a `DriverCatalog` / `ConstructorCatalog` /
  `TeamSeasonalFacts` parameter). Adding the Wikipedia call in the
  same edit would couple two unrelated runtime data flows.
- The UI rewrite (build ticket 14) already touches the use case
  seam because the new `DriverDetail` / `TeamDetail` model gains
  ~12 new fields per the locked ADR 0012. The Wikipedia summary
  field is one of those new fields, so the use case update is
  naturally bundled with the model update.
- This ticket (build 13) ships the data layer in a clean, isolated
  unit. The extension + DTO + cache + User-Agent are independently
  testable without involving the use case at all.

**This ticket does not change the use case.** The use case
contracts (`Outcome<DriverDetail>` / `Outcome<TeamDetail>`) are
unchanged. The `DriverDetail` / `TeamDetail` model classes are
unchanged. Only the new `WikipediaApi.kt` file lands in `:app`.

## Wikipedia API etiquette

- **User-Agent header**: required per the Wikimedia User-Agent
  policy. The Wikimedia edge identifies clients by their
  `User-Agent`; an empty or generic UA (`okhttp/4.x`,
  `Java/17`) is treated as bot traffic and may be rate-limited or
  rejected. The header value is a `const val` at the top of
  `WikipediaApi.kt` so the contact URL is easy to update in one
  place if the project ever moves repos.
- **No API key**: Wikipedia REST is free, no key, no rate-limit
  beyond the standard Wikimedia 200 req/s edge limit (well under
  the app's needs).
- **CC BY-SA 4.0**: the `extract` text is reused under CC BY-SA
  4.0. The attribution line ("From Wikipedia, the free
  encyclopedia, under CC BY-SA 4.0" + link to
  `summary.contentUrl`) is rendered in the UI by build ticket 14
  — not in this ticket.
- **Atom feed / language wikis**: out of scope (wayfinder ticket
  28 §"Out of scope"). English only for v1.

## Invariants

- The extension is pure-Kotlin and Android-free: no `android.*`
  imports (domain-purity invariant, preserved per
  [`practices.md`](../../../practices.md) §"Domain-purity invariant
  (hard)").
- The extension rides on the existing `HttpCache` plugin — no
  custom cache logic, no per-source file storage. The
  `FileStorage` under `cacheDir/http_cache` (per
  [`core/network.md`](../../../core/network.md) `HttpClientFactory`)
  is shared with the f1api.dev and Jolpica responses; the
  10 MB cap is well within the app's needs across all sources.
- The `WIKIPEDIA_USER_AGENT` constant is a `const val` and is the
  only place the contact URL lives. Future maintainers update
  this in one place if the project moves.
- The DTO matches the wire shape (via the @SerialName mapping for
  `content_urls.desktop.page` → `contentUrl`) and tolerates extra
  fields via the `ignoreUnknownKeys = true` JSON config.
- No force-refresh flag on this extension (see "Extension shape"
  above). Pull-to-refresh on the detail screen re-runs the use
  case, which calls this extension; the cache hit covers the
  common case (re-open within a day), and the day-old content is
  still effectively correct for biographical text.

## Out of scope for this ticket

- The use case join change (`GetDriverDetailUseCase` /
  `GetTeamDetailUseCase`). Lands in build ticket 14 (UI rewrite /
  wayfinder 29) with the model field additions. See "Scope split"
  above.
- The UI attribution line ("From Wikipedia, the free
  encyclopedia, under CC BY-SA 4.0" + link to `contentUrl`).
  Lands in build ticket 14.
- The `WikipediaSummary` model field on `DriverDetail` /
  `TeamDetail`. Lands in build ticket 14.
- Atom feed support (Wikipedia REST is JSON only for the summary
  endpoint; English only for v1; both per wayfinder ticket 28
  §"Out of scope").
- Force-refresh flag. The `~24h` cache TTL is the user's
  refresh window. Add the `forceRefresh` flag if a future ticket
  needs it (the same shape as the f1api.dev extensions).
- Multiple language wikis. English only for v1.

## Cross-references

- Wayfinder 28:
  [`lode/wayfinder/f1app/tickets/28-wikipedia-rest-extension.md`](../../../wayfinder/f1app/tickets/28-wikipedia-rest-extension.md)
  — planning decision; closed in the resolution that produced this
  build ticket.
- Wayfinder 26:
  [`lode/wayfinder/f1app/tickets/26-research-gap-f-detail-redesign.md`](../../../wayfinder/f1app/tickets/26-research-gap-f-detail-redesign.md)
  — parent research; closed.
- Research:
  [`lode/wayfinder/f1app/driver-team-detail.md`](../../../wayfinder/f1app/driver-team-detail.md)
  + [`lode/wayfinder/f1app/driver-team-detail-api-wrangling.md`](../../../wayfinder/f1app/driver-team-detail-api-wrangling.md)
  — field inventory + per-source payload shapes + computed
  Antonelli/Mercedes checks.
- ADR 0012:
  [`lode/decisions/0012-gap-f-detail-page-data-sources.md`](../../../decisions/0012-gap-f-detail-page-data-sources.md)
  — the source split (F1DB build-time + Wikipedia REST + f1api.dev
  runtime).
- ADR 0009:
  [`lode/decisions/0009-remove-openf1-runtime-dependency.md`](../../../decisions/0009-remove-openf1-runtime-dependency.md)
  — no new runtime network for stats; Wikipedia is the one new
  runtime source for the "About" text only.
- Network pattern: [`lode/core/network.md`](../../../core/network.md)
  — `HttpClient` factory + per-request base URL pattern.
- Pattern reference:
  [`app/src/main/java/com/anpurnama/f1_app/f1/data/F1Api.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/f1/data/F1Api.kt)
  — sibling `HttpClient` extension pattern (per-source
  `*_BASE` const + per-endpoint extension + `forceRefresh`
  convention; this extension deliberately omits the `forceRefresh`
  flag per the "Invariants" section).
- Pattern reference:
  [`app/src/main/java/com/anpurnama/f1_app/f1/data/Dtos.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/f1/data/Dtos.kt)
  — sibling DTO shape (`@Serializable` + `ignoreUnknownKeys`
  tolerance + nested DTOs via inner classes; this extension puts
  the DTO next to the extension because the DTO is a Wikipedia-only
  shape, not a shared envelope).
- Pattern reference:
  [`app/src/test/java/com/anpurnama/f1_app/f1/F1ApiTest.kt`](../../../../app/src/test/java/com/anpurnama/f1_app/f1/F1ApiTest.kt)
  — `MockEngine` + `respond(String)` + `expectSuccess = true` test
  shape.
- Sister build ticket 12:
  [`12-f1db-driver-constructor-catalog-import.md`](12-f1db-driver-constructor-catalog-import.md)
  — F1DB build-time catalog import. Both this ticket and ticket
  12 are "data layer only" — the use case join change lands in
  build ticket 14.
- Follow-up build ticket 14 (wayfinder 29): DriverDetail /
  TeamDetail UI rewrite. Consumes both the F1DB catalog (build
  12) and the Wikipedia extension (this ticket) in one use case
  update.
- THIRD_PARTY_NOTICES:
  [`THIRD_PARTY_NOTICES.md`](../../../../THIRD_PARTY_NOTICES.md)
  — to extend with a Wikipedia REST section.
