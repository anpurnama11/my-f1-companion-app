package com.anpurnama.f1_app

import android.app.Application
import com.anpurnama.f1_app.core.di.Wiring

/**
 * Application subclass that owns the [Wiring] service locator.
 * Reached from `MainActivity` and (later) the widget as
 * `(application as F1App).wiring`. Declared in the manifest via
 * `android:name=".F1App"`.
 */
class F1App : Application() {

    lateinit var wiring: Wiring
        private set

    override fun onCreate() {
        super.onCreate()
        wiring = Wiring(this)
    }
}
