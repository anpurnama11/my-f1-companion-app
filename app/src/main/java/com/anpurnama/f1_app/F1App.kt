package com.anpurnama.f1_app

import android.app.Application
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
 */
class F1App : Application() {

    lateinit var wiring: Wiring
        private set

    override fun onCreate() {
        super.onCreate()
        wiring = Wiring(this)
        CountdownWorker.enqueuePeriodic(this)
    }
}
