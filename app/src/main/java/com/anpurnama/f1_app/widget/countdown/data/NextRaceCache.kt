package com.anpurnama.f1_app.widget.countdown.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anpurnama.f1_app.widget.countdown.NextRaceSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * `DataStore<Preferences>` cache for the next-race snapshot. Written by
 * [com.anpurnama.f1_app.widget.countdown.CountdownWorker] (one
 * `edit` per successful fetch), read by the [Glance widget][com.anpurnama.f1_app.widget.countdown.CountdownWidget].
 * Same `Wiring` instance — the only one.
 *
 * **Typed keys, no JSON blob.** Per the design: a serialized JSON
 * payload would couple the cache to the model shape and force a
 * migration on every rename. The typed keys ride along with the
 * snapshot fields.
 *
 * **Off-season encoding.** The off-season sentinel is
 * `startMillis == 0L` and only that key + `lastSyncedMillis` is
 * written; the other fields are removed. [read] returns a snapshot
 * whose other fields default to empty/zero, so the worker doesn't
 * need to fabricate a fake race.
 *
 * **No timestamps other than `lastSyncedMillis`.** The widget has no
 * chronometer, so a per-field "when was this set" is unnecessary.
 * The single `lastSyncedMillis` covers the worker's adaptive gate.
 *
 * **Corruption recovery.** The DataStore is constructed by
 * [com.anpurnama.f1_app.core.cache.createPreferencesDataStore] with a
 * `ReplaceFileCorruptionHandler` that returns `emptyPreferences`.
 * Parser-detected corruption (truncated protobuf, foreign payload,
 * schema drift) reads back as `null` from [snapshot] / [observe],
 * which the widget reducer maps to the documented no-data state
 * ([com.anpurnama.f1_app.widget.countdown.CountdownState.NoRaceData]).
 * The widget remains placeable; the next successful [write] /
 * [writeOffSeason] from the worker repopulates the cache and the
 * widget repaints. Ordinary `IOException`s (permission denied, full
 * disk, etc.) are not `CorruptionException`s and therefore propagate
 * — the corruption policy does not erase arbitrary I/O failures.
 */
class NextRaceCache(private val dataStore: DataStore<Preferences>) {

    /**
     * Hot stream of the cached snapshot. Emits `null` until the
     * worker has written at least once (first cold launch).
     */
    fun observe(): Flow<NextRaceSnapshot?> = dataStore.data.map { it.toSnapshot() }

    /**
     * One-shot read. The worker calls this in `doWork` to make its
     * adaptive-gate decision (cache age vs. in-race-window) without
     * collecting a long-lived stream.
     */
    suspend fun snapshot(): NextRaceSnapshot? = observe().first()

    /**
     * Atomic write of a populated snapshot. Called by the worker on a
     * successful `/current/next` fetch.
     */
    suspend fun write(snapshot: NextRaceSnapshot) {
        dataStore.edit { prefs ->
            prefs[START_MILLIS] = snapshot.startMillis
            prefs[NAME] = snapshot.raceName
            prefs[CIRCUIT_NAME] = snapshot.circuitName
            prefs[CIRCUIT_COUNTRY] = snapshot.circuitCountry.orEmpty()
            prefs[CIRCUIT_ID] = snapshot.circuitId
            prefs[SESSION_NAME] = snapshot.sessionName
            prefs[ROUND] = snapshot.round
            prefs[SEASON] = snapshot.year
            prefs[LAST_SYNCED] = snapshot.lastSyncedMillis
        }
    }

    /**
     * Atomic write of the off-season sentinel. Called by the worker
     * when `/current/next` returns an empty `race` array. Other
     * fields are cleared — the cache is in a "no race" state, not a
     * "stale race" state, so any previously-cached race metadata is
     * actively wrong.
     */
    suspend fun writeOffSeason(lastSyncedMillis: Long) {
        dataStore.edit { prefs ->
            prefs[START_MILLIS] = 0L
            prefs[LAST_SYNCED] = lastSyncedMillis
            prefs.remove(NAME)
            prefs.remove(CIRCUIT_NAME)
            prefs.remove(CIRCUIT_COUNTRY)
            prefs.remove(CIRCUIT_ID)
            prefs.remove(SESSION_NAME)
            prefs.remove(ROUND)
            prefs.remove(SEASON)
        }
    }

    private fun Preferences.toSnapshot(): NextRaceSnapshot? {
        // `START_MILLIS` is the one key the worker is guaranteed to
        // write on every successful fetch (real or off-season). Its
        // absence is the "no cache at all" signal.
        val startMillis = this[START_MILLIS] ?: return null
        val lastSynced = this[LAST_SYNCED] ?: return null
        return NextRaceSnapshot(
            year = this[SEASON] ?: 0,
            round = this[ROUND] ?: 0,
            raceName = this[NAME] ?: "",
            circuitName = this[CIRCUIT_NAME] ?: "",
            circuitCountry = this[CIRCUIT_COUNTRY]?.takeIf { it.isNotEmpty() },
            circuitId = this[CIRCUIT_ID] ?: "",
            sessionName = this[SESSION_NAME] ?: "Race",
            startMillis = startMillis,
            lastSyncedMillis = lastSynced,
        )
    }

    companion object {
        // Keys are private — callers go through the typed methods, not
        // the raw preferences. Matches the FavoritesCache convention.
        private val START_MILLIS = longPreferencesKey("next_race_start_millis")
        private val NAME = stringPreferencesKey("next_race_name")
        private val CIRCUIT_NAME = stringPreferencesKey("next_race_circuit")
        private val CIRCUIT_COUNTRY = stringPreferencesKey("next_race_circuit_country")
        private val CIRCUIT_ID = stringPreferencesKey("next_race_circuit_id")
        private val SESSION_NAME = stringPreferencesKey("next_race_session_name")
        private val ROUND = intPreferencesKey("next_race_round")
        private val SEASON = intPreferencesKey("next_race_season")
        private val LAST_SYNCED = longPreferencesKey("next_race_last_synced_millis")
    }
}
