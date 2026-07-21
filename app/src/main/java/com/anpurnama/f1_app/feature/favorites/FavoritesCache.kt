package com.anpurnama.f1_app.feature.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Three-slot favorites store: 2 driver IDs + 1 team ID. Backs Homepage §1
 * (favorites pager) and (later) the My Team tab's management surface.
 *
 * Storage: `DataStore<Preferences>` with three typed keys, one atomic
 * `edit` per write. Mirrors the `NextRaceCache` shape from the design.
 * No `FAV_*_TS` timestamps; not needed unless a "most-recent pin" heuristic
 * lands later. 3rd-pin = explicit user replace, not auto-evict-oldest.
 *
 * `seedIfEmpty(...)` is the first-launch default seed: write the #1
 * constructor + its two drivers only when the user hasn't picked anything
 * yet. Partial seed — if the user already filled one slot, the seed fills
 * the rest, never clobbers.
 *
 * Held by `Wiring` (built from a `PreferenceDataStoreFactory.create { file }`
 * under `context.preferencesDataStoreFile("favorites")`). The test harness
 * uses a temp file via the same factory; no `Context` needed at the cache
 * surface.
 */
class FavoritesCache(private val dataStore: DataStore<Preferences>) {

    fun read(): Flow<Favorites> = dataStore.data.map { prefs ->
        Favorites(
            driver1Id = prefs[FAV_DRIVER_1],
            driver2Id = prefs[FAV_DRIVER_2],
            teamId = prefs[FAV_TEAM],
        )
    }

    suspend fun setDriver1(driverId: String) {
        dataStore.edit { it[FAV_DRIVER_1] = driverId }
    }

    suspend fun setDriver2(driverId: String) {
        dataStore.edit { it[FAV_DRIVER_2] = driverId }
    }

    suspend fun setTeam(teamId: String) {
        dataStore.edit { it[FAV_TEAM] = teamId }
    }

    /**
     * First-launch default seed. Writes the top constructor + its two
     * drivers, but only into slots the user hasn't filled yet. No-op on
     * a fully-populated cache; partial fill on a half-populated one.
     *
     * @param topTeamId     the #1 constructor's teamId (e.g. "mercedes").
     * @param topDriverIds  the two driverIds from that constructor (e.g.
     *                      `listOf("antonelli", "russell")`). Must have
     *                      exactly 2 entries; shorter lists leave the
     *                      remaining driver slot empty.
     */
    suspend fun seedIfEmpty(topTeamId: String, topDriverIds: List<String>) {
        dataStore.edit { prefs ->
            if (prefs[FAV_TEAM] == null) prefs[FAV_TEAM] = topTeamId
            val d1 = topDriverIds.getOrNull(0)
            if (prefs[FAV_DRIVER_1] == null && d1 != null) {
                prefs[FAV_DRIVER_1] = d1
            }
            val d2 = topDriverIds.getOrNull(1)
            if (prefs[FAV_DRIVER_2] == null && d2 != null) {
                prefs[FAV_DRIVER_2] = d2
            }
        }
    }

    companion object {
        // Keys are private — nothing outside this class reads them. Keeping
        // them off the public surface matches the "no FAV_*_TS" concern
        // from the spec: the cache is an opaque storage, callers go
        // through `setDriver1` / `setDriver2` / `setTeam` / `seedIfEmpty`.
        private val FAV_DRIVER_1 = stringPreferencesKey("fav_driver_1")
        private val FAV_DRIVER_2 = stringPreferencesKey("fav_driver_2")
        private val FAV_TEAM = stringPreferencesKey("fav_team")
    }
}

/**
 * Three-slot favorites snapshot. `null` means "not picked" (or "seeded
 * but the default didn't have a value to write"). `isEmpty()` is the
 * "first-launch: needs default seed" check.
 */
data class Favorites(
    val driver1Id: String?,
    val driver2Id: String?,
    val teamId: String?,
) {
    fun isEmpty(): Boolean = driver1Id == null && driver2Id == null && teamId == null
}
