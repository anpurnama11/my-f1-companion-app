package com.anpurnama.f1_app.core.network

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single Ktor [HttpClient] for the app, built once at [com.anpurnama.f1_app.F1App]
 * startup and held by `Wiring`.
 *
 * Plugins:
 *  - [HttpTimeout] — 15s request / 10s connect budget.
 *  - [ContentNegotiation] with `kotlinx.serialization` JSON,
 *    `ignoreUnknownKeys = true` (forward-compat with API additions) and
 *    `coerceInputValues = true` (tolerate `null` for non-null fields).
 *  - [HttpCache] with a 10 MB [FileStorage] under `cacheDir/http_cache`.
 *    Honors server `max-age`/`max-stale` headers — f1api.dev sends
 *    `max-age=600` so cold offline launches serve from cache; sources
 *    without cache headers (OpenF1, added later) bypass the plugin.
 *  - [Logging] at `LogLevel.BODY` (request/response method + URL + headers +
 *    full body — the Ktor equivalent of OkHttp `HttpLoggingInterceptor.Level.BODY`).
 *    A custom [Logger] routes to `android.util.Log` under the `F1api` tag.
 *    `ponytail:` no redaction — fine while every source is public race data;
 *    gate or drop to `LogLevel.INFO` before wiring an authenticated source.
 *
 * `expectSuccess = true` is required so 4xx/5xx throw
 * [io.ktor.client.plugins.ClientRequestException] /
 * [io.ktor.client.plugins.ServerResponseException] before body
 * deserialization — the use case's 4xx/5xx catch branches depend on it.
 *
 * No default base URL — per ticket 04, per-request endpoints build full
 * URLs from the source's `*_BASE` const in `f1/data/F1Api.kt`.
 */
object HttpClientFactory {

    private const val CACHE_DIR = "http_cache"

    fun create(context: Context): HttpClient = HttpClient(CIO) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }

        install(HttpCache) {
            val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            publicStorage(FileStorage(cacheDir))
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }

        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) { Log.i("F1api", message) }
            }
        }
    }
}
