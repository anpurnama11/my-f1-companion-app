package com.anpurnama.f1_app.f1.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WikipediaApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
    }

    // MockEngine must receive a String; pre-wrapping it breaks ContentNegotiation.
    private fun MockRequestHandleScope.jsonOk(
        body: String,
        cacheControl: String = "public, max-age=86400",
    ) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headers {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            append(HttpHeaders.CacheControl, cacheControl)
        },
    )

    @Test
    fun `getWikipediaSummary for Antonelli returns canonical title and sets User-Agent`() = runTest {
        var capturedPath: String? = null
        var capturedUserAgent: String? = null
        val client = mockClient { req ->
            capturedPath = req.url.fullPath
            capturedUserAgent = req.headers[HttpHeaders.UserAgent]
            jsonOk(
                """
                {
                  "title": "Kimi Antonelli",
                  "description": "Italian racing driver (born 2006)",
                  "extract": "Andrea Kimi Antonelli is an Italian racing driver who competes in Formula One for Mercedes.",
                  "content_urls": {
                    "desktop": { "page": "https://en.wikipedia.org/wiki/Kimi_Antonelli" },
                    "mobile":  { "page": "https://en.m.wikipedia.org/wiki/Kimi_Antonelli" }
                  },
                  "wikibase_item": "Q131702790",
                  "pageid": 81842305
                }
                """.trimIndent(),
            )
        }

        val summary = client.getWikipediaSummary("Andrea_Kimi_Antonelli")

        assertEquals("Kimi Antonelli", summary.title)
        assertEquals(
            "https://en.wikipedia.org/wiki/Kimi_Antonelli",
            summary.contentUrl,
        )
        assertEquals("Italian racing driver (born 2006)", summary.description)
        assertTrue(
            "extract should be non-empty, was: '${summary.extract}'",
            summary.extract.isNotEmpty(),
        )
        assertEquals("/api/rest_v1/page/summary/Andrea_Kimi_Antonelli", capturedPath)
        assertTrue(
            "expected User-Agent header starting with F1app/1.0, was: $capturedUserAgent",
            capturedUserAgent?.startsWith("F1app/1.0") == true,
        )
    }

    @Test
    fun `getWikipediaSummary for Mercedes in F1 returns canonical hyphenated title`() = runTest {
        val client = mockClient {
            jsonOk(
                """
                {
                  "title": "Mercedes-Benz in Formula One",
                  "description": "Formula One activities of Mercedes-Benz",
                  "extract": "Mercedes-Benz, a German automotive brand of the Mercedes-Benz Group, has been involved in Formula One as both team owner and engine manufacturer for various periods since 1954.",
                  "content_urls": {
                    "desktop": { "page": "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One" }
                  }
                }
                """.trimIndent(),
            )
        }

        val summary = client.getWikipediaSummary("Mercedes-Benz_in_Formula_One")

        assertEquals("Mercedes-Benz in Formula One", summary.title)
        assertEquals(
            "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One",
            summary.contentUrl,
        )
    }

    @Test
    fun `getWikipediaSummary passes through underscores and hyphens in the title segment`() = runTest {
        var capturedPath: String? = null
        val client = mockClient { req ->
            capturedPath = req.url.fullPath
            jsonOk(
                """
                {
                  "title": "Mercedes-Benz in Formula One",
                  "extract": "...",
                  "content_urls": { "desktop": { "page":
                    "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One" } }
                }
                """.trimIndent(),
            )
        }

        client.getWikipediaSummary("Mercedes-Benz_in_Formula_One")

        assertEquals(
            "/api/rest_v1/page/summary/Mercedes-Benz_in_Formula_One",
            capturedPath,
        )
    }

    @Test
    fun `getWikipediaSummary tolerates articles with no thumbnail and no description`() = runTest {
        val client = mockClient {
            jsonOk(
                """
                {
                  "title": "Kimi Antonelli",
                  "extract": "Andrea Kimi Antonelli is an Italian racing driver.",
                  "content_urls": { "desktop": { "page":
                    "https://en.wikipedia.org/wiki/Kimi_Antonelli" } }
                }
                """.trimIndent(),
            )
        }

        val summary = client.getWikipediaSummary("Kimi_Antonelli")

        assertNull(summary.thumbnail)
        assertNull(summary.description)
        assertEquals("Kimi Antonelli", summary.title)
        assertTrue(summary.extract.isNotEmpty())
    }

    @Test
    fun `getWikipediaSummary decodes thumbnail when present`() = runTest {
        val client = mockClient {
            jsonOk(
                """
                {
                  "title": "Kimi Antonelli",
                  "description": "Italian racing driver (born 2006)",
                  "extract": "...",
                  "content_urls": { "desktop": { "page":
                    "https://en.wikipedia.org/wiki/Kimi_Antonelli" } },
                  "thumbnail": {
                    "source": "https://upload.wikimedia.org/wikipedia/thumb/2/2e/Antonelli_2024.jpg/220px-Antonelli_2024.jpg",
                    "width": 220,
                    "height": 293
                  }
                }
                """.trimIndent(),
            )
        }

        val summary = client.getWikipediaSummary("Kimi_Antonelli")

        assertNotNull("expected thumbnail in summary", summary.thumbnail)
        val thumb: WikipediaThumbnail = requireNotNull(summary.thumbnail)
        assertEquals(220, thumb.width)
        assertEquals(293, thumb.height)
        assertTrue(
            "thumbnail source should be a Wikipedia upload URL, was: ${thumb.source}",
            thumb.source.startsWith("https://upload.wikimedia.org/"),
        )
    }

    @Test
    fun `getWikipediaSummary cache-hits on the second call within the TTL`() = runTest {
        val callCount = AtomicInteger(0)
        // Use real disk storage to cover the configured cache path.
        val cacheDir = kotlin.io.path.createTempDirectory("f1app-wiki-cache-").toFile()
        try {
            val client = HttpClient(
                MockEngine { _ ->
                    callCount.incrementAndGet()
                    jsonOk(
                        """
                        {
                          "title": "Kimi Antonelli",
                          "extract": "Andrea Kimi Antonelli is an Italian racing driver.",
                          "content_urls": { "desktop": { "page":
                            "https://en.wikipedia.org/wiki/Kimi_Antonelli" } }
                        }
                        """.trimIndent(),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) { json(json) }
                install(HttpCache) { publicStorage(FileStorage(cacheDir)) }
            }

            val first = client.getWikipediaSummary("Kimi_Antonelli")
            val second = client.getWikipediaSummary("Kimi_Antonelli")

            assertEquals(1, callCount.get())
            assertEquals(first.title, second.title)
            assertEquals(first.extract, second.extract)
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
