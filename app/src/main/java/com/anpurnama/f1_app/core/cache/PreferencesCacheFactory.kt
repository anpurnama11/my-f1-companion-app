package com.anpurnama.f1_app.core.cache

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

/**
 * Production seam for the two Preferences DataStore caches
 * ([com.anpurnama.f1_app.feature.favorites.FavoritesCache],
 * [com.anpurnama.f1_app.widget.countdown.data.NextRaceCache]).
 *
 * One function, one construction: the same `DataStore<Preferences>` shape
 * is built for both — a single Preferences `*.preferences_pb` file with
 * a [ReplaceFileCorruptionHandler] that recovers parser-detected
 * corruption to [emptyPreferences]. `Wiring` and the JVM tests both
 * call this function against the same temp file, so a future change to
 * the recovery policy (e.g. a migration step or a per-cache sentinel)
 * lands in one place and is covered by the JVM tests that already
 * exercise this seam.
 *
 * Visible to tests (Kotlin `internal`); not part of the public surface.
 * Per `lode/practices.md`, the DataStore itself is private to `Wiring`
 * at the production layer — this helper is the only call site.
 */
internal fun createPreferencesDataStore(file: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    ) { file }
