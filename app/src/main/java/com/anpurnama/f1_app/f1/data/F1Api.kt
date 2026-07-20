package com.anpurnama.f1_app.f1.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders

/**
 * Base URL for the f1api.dev primary source. Held by `HttpClientFactory` /
 * `Wiring`; full URLs are built per request.
 *
 * Other sources (jolpica for all-time most-wins-at-circuit, OpenF1 for
 * top-speed) are added in tickets 04/08/09 — their base URL consts will
 * land here alongside [getCurrent] and the multi-source contract.
 */
const val F1API_BASE = "https://f1api.dev/api"

/**
 * Full-season schedule + sessions. Used by Homepage §2 aggregates and the
 * Schedule tab.
 */
suspend fun HttpClient.getCurrent(forceRefresh: Boolean = false): SeasonResponseDto {
    val response = get("$F1API_BASE/current") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}
