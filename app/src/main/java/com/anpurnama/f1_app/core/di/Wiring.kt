package com.anpurnama.f1_app.core.di

import android.content.Context
import com.anpurnama.f1_app.core.network.HttpClientFactory
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import io.ktor.client.HttpClient

/**
 * Manual service locator. Held by [com.anpurnama.f1_app.F1App] as
 * `app.wiring`; reached from ViewModels via
 * `viewModelFactory { initializer { ... } }`. The widget shares the
 * same instance when it lands (ticket 07) — one composition root,
 * cross-entry-point.
 *
 * Use cases expose their `HttpClient` as a method ref (`useCase::invoke`),
 * so the VM does not see the network layer.
 */
class Wiring(context: Context) {

    private val appContext: Context = context.applicationContext

    val httpClient: HttpClient = HttpClientFactory.create(appContext)

    val getSeason: GetSeasonUseCase = GetSeasonUseCase(httpClient)
}
