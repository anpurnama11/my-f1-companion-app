package com.anpurnama.f1_app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.anpurnama.f1_app.core.cache.CacheSyncWorker
import com.anpurnama.f1_app.core.di.Wiring
import com.anpurnama.f1_app.widget.countdown.CountdownWorker

/**
 * Application subclass that owns the [Wiring] service locator.
 * Reached from `MainActivity` and the widget as
 * `(application as F1App).wiring`. Declared in the manifest via
 * `android:name=".F1App"`.
 *
 * Also enqueues the Countdown widget's periodic refresh at process
 * start. `ExistingPeriodicWorkPolicy.UPDATE` means a fresh app
 * launch with a tuned schedule (e.g. 60-min vs 30-min cache-stale
 * threshold) takes effect immediately — no first-time cancel-and-
 * re-enqueue dance.
 *
 * Implements [SingletonImageLoader.Factory] so Coil's singleton
 * `ImageLoader` is built with the existing Ktor `HttpClient` (the
 * same one `Wiring` uses for the API) — no OkHttp dependency, no
 * duplicate engine. ticket 08.
 */
class F1App : Application(), SingletonImageLoader.Factory {

    lateinit var wiring: Wiring
        private set

    override fun onCreate() {
        super.onCreate()
        wiring = Wiring(this)
        CountdownWorker.enqueuePeriodic(this)
        CacheSyncWorker.enqueuePeriodic(this)
    }

    /**
     * Build the Coil singleton `ImageLoader` once, sharing the
     * `Wiring` Ktor `HttpClient` so network caches (HttpCache +
     * Coil's in-memory cache) don't duplicate. KMP-portable per
     * ticket 04: the same wiring works under `:shared` later.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { wiring.httpClient }))
            }
            .build()
}
