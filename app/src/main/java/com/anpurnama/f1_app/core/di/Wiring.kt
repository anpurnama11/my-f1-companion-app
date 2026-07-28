package com.anpurnama.f1_app.core.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.anpurnama.f1_app.core.cache.CacheState
import com.anpurnama.f1_app.core.cache.CacheStateSchemaMigration
import com.anpurnama.f1_app.core.cache.CacheStateSerializer
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.core.network.HttpClientFactory
import com.anpurnama.f1_app.feature.favorites.FavoritesCache
import com.anpurnama.f1_app.widget.countdown.data.NextRaceCache
import com.anpurnama.f1_app.f1.GetCircuitMostWinsUseCase
import com.anpurnama.f1_app.f1.GetCircuitUseCase
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriverDetailUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
import com.anpurnama.f1_app.f1.GetFastestPitstopUseCase
import com.anpurnama.f1_app.f1.GetNextRaceUseCase
import com.anpurnama.f1_app.f1.GetPracticeResultUseCase
import com.anpurnama.f1_app.f1.GetRoundPodiumUseCase
import com.anpurnama.f1_app.f1.GetRoundQualifyingUseCase
import com.anpurnama.f1_app.f1.GetRoundResultsUseCase
import com.anpurnama.f1_app.f1.GetSessionResultUseCase
import com.anpurnama.f1_app.f1.GetSprintQualifyingResultUseCase
import com.anpurnama.f1_app.f1.GetSprintResultUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.GetTeamDetailUseCase
import com.anpurnama.f1_app.f1.cache.SeasonScheduleCacheRepository
import io.ktor.client.HttpClient
import java.io.File

/**
 * Manual service locator. Held by [com.anpurnama.f1_app.F1App] as
 * `app.wiring`; reached from ViewModels via
 * `viewModelFactory { initializer { ... } }`. The widget shares the same
 * instance when it lands (ticket 07) — one composition root,
 * cross-entry-point.
 *
 * Use cases expose their `HttpClient` as a method ref (`useCase::invoke`),
 * so the VM does not see the network layer. The favorites cache is a
 * thin DataStore wrapper.
 */
class Wiring(context: Context) {

    private val appContext: Context = context.applicationContext

    val httpClient: HttpClient = HttpClientFactory.create(appContext)

    val getSeason: GetSeasonUseCase = GetSeasonUseCase(httpClient)
    val getNextRace: GetNextRaceUseCase = GetNextRaceUseCase(httpClient)
    val getDriversStandings: GetDriversStandingsUseCase = GetDriversStandingsUseCase(httpClient)
    val getConstructorsStandings: GetConstructorsStandingsUseCase = GetConstructorsStandingsUseCase(httpClient)
    val getDriverDetail: GetDriverDetailUseCase = GetDriverDetailUseCase(httpClient)
    val getTeamDetail: GetTeamDetailUseCase = GetTeamDetailUseCase(httpClient)
    val getRoundResults: GetRoundResultsUseCase = GetRoundResultsUseCase(httpClient)
    val getRoundQualifying: GetRoundQualifyingUseCase = GetRoundQualifyingUseCase(httpClient)
    val getRoundPodium: GetRoundPodiumUseCase = GetRoundPodiumUseCase(getRoundResults)
    val getPracticeResult: GetPracticeResultUseCase = GetPracticeResultUseCase(httpClient)
    val getSprintResult: GetSprintResultUseCase = GetSprintResultUseCase(httpClient)
    val getSprintQualifyingResult: GetSprintQualifyingResultUseCase = GetSprintQualifyingResultUseCase(httpClient)
    val getSessionResult: GetSessionResultUseCase = GetSessionResultUseCase(
        getRoundResults = getRoundResults,
        getRoundQualifying = getRoundQualifying,
        getPractice = getPracticeResult,
        getSprint = getSprintResult,
        getSprintQualifying = getSprintQualifyingResult,
    )
    val getFastestPitstop: GetFastestPitstopUseCase = GetFastestPitstopUseCase(httpClient)
    val getCircuit: GetCircuitUseCase = GetCircuitUseCase(httpClient)
    val getCircuitMostWins: GetCircuitMostWinsUseCase = GetCircuitMostWinsUseCase(httpClient)

    val snapshotStore: SnapshotStore = SnapshotStore(
        DataStoreFactory.create(
            serializer = CacheStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
            migrations = listOf(CacheStateSchemaMigration),
            produceFile = {
                File(File(appContext.filesDir, "datastore"), "cache-state.json").apply {
                    parentFile?.mkdirs()
                }
            },
        )
    )

    val seasonScheduleCacheRepository: SeasonScheduleCacheRepository = SeasonScheduleCacheRepository(
        store = snapshotStore,
        client = httpClient,
    )

    val favoritesCache: FavoritesCache = FavoritesCache(
        PreferenceDataStoreFactory.create {
            File(File(appContext.filesDir, "datastore"), "favorites.preferences_pb").apply {
                parentFile?.mkdirs()
            }
        }
    )

    /**
     * Typed-key DataStore for the Countdown widget. Same `Wiring`
     * instance is held by the application, the worker, and the
     * Glance widget — one DataStore, one source of truth.
     */
    val nextRaceCache: NextRaceCache = NextRaceCache(
        PreferenceDataStoreFactory.create {
            File(File(appContext.filesDir, "datastore"), "next_race.preferences_pb").apply {
                parentFile?.mkdirs()
            }
        }
    )
}
