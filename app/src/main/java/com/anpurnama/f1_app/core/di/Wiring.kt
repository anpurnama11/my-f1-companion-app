package com.anpurnama.f1_app.core.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.lifecycle.ViewModelProvider
import com.anpurnama.f1_app.core.cache.BundleRefreshResult
import com.anpurnama.f1_app.core.cache.RefreshFailureClassifier
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.feature.circuit.circuitViewModelFactory as circuitFactory
import com.anpurnama.f1_app.feature.driver.driverViewModelFactory as driverFactory
import com.anpurnama.f1_app.feature.homepage.homepageViewModelFactory as homepageFactory
import com.anpurnama.f1_app.feature.leaderboard.leaderboardViewModelFactory as leaderboardFactory
import com.anpurnama.f1_app.feature.myteam.myTeamViewModelFactory as myTeamFactory
import com.anpurnama.f1_app.feature.round.roundViewModelFactory as roundFactory
import com.anpurnama.f1_app.feature.schedule.scheduleViewModelFactory as scheduleFactory
import com.anpurnama.f1_app.feature.sessionresult.sessionResultViewModelFactory as sessionResultFactory
import com.anpurnama.f1_app.feature.team.teamViewModelFactory as teamFactory
import com.anpurnama.f1_app.core.cache.CacheState
import com.anpurnama.f1_app.core.cache.CacheStateSchemaMigration
import com.anpurnama.f1_app.core.cache.CacheStateSerializer
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.core.cache.createPreferencesDataStore
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
import com.anpurnama.f1_app.f1.cache.CurrentSeasonResourcesCacheRepository
import com.anpurnama.f1_app.f1.cache.NonSeasonResourcesCacheRepository
import com.anpurnama.f1_app.f1.cache.SeasonScheduleCacheRepository
import com.anpurnama.f1_app.f1.cache.SessionResultsCacheRepository
import io.ktor.client.HttpClient
import kotlinx.datetime.Instant
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

    internal val httpClient: HttpClient by lazy { HttpClientFactory.create(appContext) }

    private val getSeasonUseCase: GetSeasonUseCase by lazy { GetSeasonUseCase(httpClient) }
    private val getNextRaceUseCase: GetNextRaceUseCase by lazy { GetNextRaceUseCase(httpClient) }
    private val getDriversStandings: GetDriversStandingsUseCase by lazy { GetDriversStandingsUseCase(httpClient) }
    private val getConstructorsStandings: GetConstructorsStandingsUseCase by lazy { GetConstructorsStandingsUseCase(httpClient) }
    private val getDriverDetail: GetDriverDetailUseCase by lazy { GetDriverDetailUseCase(httpClient) }
    private val getTeamDetail: GetTeamDetailUseCase by lazy { GetTeamDetailUseCase(httpClient) }
    private val getRoundResults: GetRoundResultsUseCase by lazy { GetRoundResultsUseCase(httpClient) }
    private val getRoundQualifying: GetRoundQualifyingUseCase by lazy { GetRoundQualifyingUseCase(httpClient) }
    private val getRoundPodium: GetRoundPodiumUseCase by lazy { GetRoundPodiumUseCase(getRoundResults) }
    private val getPracticeResult: GetPracticeResultUseCase by lazy { GetPracticeResultUseCase(httpClient) }
    private val getSprintResult: GetSprintResultUseCase by lazy { GetSprintResultUseCase(httpClient) }
    private val getSprintQualifyingResult: GetSprintQualifyingResultUseCase by lazy { GetSprintQualifyingResultUseCase(httpClient) }
    private val getSessionResult: GetSessionResultUseCase by lazy {
        GetSessionResultUseCase(
            getRoundResults = getRoundResults,
            getRoundQualifying = getRoundQualifying,
            getPractice = getPracticeResult,
            getSprint = getSprintResult,
            getSprintQualifying = getSprintQualifyingResult,
        )
    }
    private val getFastestPitstop: GetFastestPitstopUseCase by lazy { GetFastestPitstopUseCase(httpClient) }
    private val getCircuit: GetCircuitUseCase by lazy { GetCircuitUseCase(httpClient) }
    private val getCircuitMostWins: GetCircuitMostWinsUseCase by lazy { GetCircuitMostWinsUseCase(httpClient) }

    private val snapshotStore: SnapshotStore by lazy {
        SnapshotStore(
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
    }

    private val seasonScheduleCacheRepository: SeasonScheduleCacheRepository by lazy {
        SeasonScheduleCacheRepository(
            store = snapshotStore,
            client = httpClient,
        )
    }

    private val currentSeasonResourcesCacheRepository: CurrentSeasonResourcesCacheRepository by lazy {
        CurrentSeasonResourcesCacheRepository(
            store = snapshotStore,
            client = httpClient,
            refreshScheduleIfMissing = seasonScheduleCacheRepository::refreshCurrentSeason,
        )
    }

    private val sessionResultsCacheRepository: SessionResultsCacheRepository by lazy {
        SessionResultsCacheRepository(
            store = snapshotStore,
            client = httpClient,
        )
    }

    private val nonSeasonResourcesCacheRepository: NonSeasonResourcesCacheRepository by lazy {
        NonSeasonResourcesCacheRepository(
            store = snapshotStore,
            client = httpClient,
        )
    }

    private val favoritesCache: FavoritesCache by lazy {
        FavoritesCache(
            createPreferencesDataStore(
                File(File(appContext.filesDir, "datastore"), "favorites.preferences_pb").apply {
                    parentFile?.mkdirs()
                }
            )
        )
    }

    /**
     * Typed-key DataStore for the Countdown widget. Same `Wiring`
     * instance is held by the application, the worker, and the
     * Glance widget — one DataStore, one source of truth.
     */
    internal val nextRaceCache: NextRaceCache by lazy {
        NextRaceCache(
            createPreferencesDataStore(
                File(File(appContext.filesDir, "datastore"), "next_race.preferences_pb").apply {
                    parentFile?.mkdirs()
                }
            )
        )
    }


    fun homepageViewModelFactory(): ViewModelProvider.Factory = homepageFactory(
        getSeason = getSeasonUseCase,
        getNextRace = getNextRaceUseCase,
        getDriversStandings = getDriversStandings,
        getConstructorsStandings = getConstructorsStandings,
        favoritesCache = favoritesCache,
        seasonScheduleCacheRepository = seasonScheduleCacheRepository,
        currentSeasonResourcesCacheRepository = currentSeasonResourcesCacheRepository,
    )

    fun scheduleViewModelFactory(): ViewModelProvider.Factory = scheduleFactory(
        getSeason = getSeasonUseCase,
        getRoundPodium = getRoundPodium,
        seasonScheduleCacheRepository = seasonScheduleCacheRepository,
        sessionResultsCacheRepository = sessionResultsCacheRepository,
    )

    fun leaderboardViewModelFactory(): ViewModelProvider.Factory = leaderboardFactory(
        getDriversStandings = getDriversStandings,
        getConstructorsStandings = getConstructorsStandings,
        currentSeasonResourcesCacheRepository = currentSeasonResourcesCacheRepository,
    )

    fun myTeamViewModelFactory(): ViewModelProvider.Factory = myTeamFactory(
        getDriversStandings = getDriversStandings,
        getConstructorsStandings = getConstructorsStandings,
        favoritesCache = favoritesCache,
        currentSeasonResourcesCacheRepository = currentSeasonResourcesCacheRepository,
    )

    fun roundViewModelFactory(year: Int, round: Int): ViewModelProvider.Factory = roundFactory(
        year = year,
        round = round,
        getRoundResults = getRoundResults,
        getRoundQualifying = getRoundQualifying,
        getSeason = getSeasonUseCase,
        seasonScheduleCacheRepository = seasonScheduleCacheRepository,
    )

    fun sessionResultViewModelFactory(
        year: Int,
        round: Int,
        session: com.anpurnama.f1_app.f1.model.SessionType,
    ): ViewModelProvider.Factory = sessionResultFactory(
        year = year,
        round = round,
        session = session,
        getSessionResult = getSessionResult,
        getFastestPitstop = getFastestPitstop,
        sessionResultsCacheRepository = sessionResultsCacheRepository,
    )

    fun circuitViewModelFactory(circuitId: String): ViewModelProvider.Factory = circuitFactory(
        circuitId = circuitId,
        getCircuit = getCircuit,
        getCircuitMostWins = getCircuitMostWins,
        nonSeasonResourcesCacheRepository = nonSeasonResourcesCacheRepository,
    )

    fun driverViewModelFactory(driverId: String): ViewModelProvider.Factory = driverFactory(
        driverId = driverId,
        getDriverDetail = getDriverDetail,
    )

    fun teamViewModelFactory(teamId: String): ViewModelProvider.Factory = teamFactory(
        teamId = teamId,
        getTeamDetail = getTeamDetail,
    )

    internal suspend fun loadNextRaceForCountdown(forceRefresh: Boolean) = getNextRaceUseCase.invoke(forceRefresh)

    internal suspend fun loadSeasonForCountdown(forceRefresh: Boolean) = getSeasonUseCase.invoke(forceRefresh)

    internal suspend fun refreshCurrentSeasonScheduleForPeriodic(): RefreshResult = runCatching {
        seasonScheduleCacheRepository.refreshCurrentSeason(RefreshReason.Periodic)
    }.getOrElse(RefreshFailureClassifier::classify)

    internal suspend fun refreshCurrentSeasonResourcesBundleForPeriodicSync(): BundleRefreshResult = runCatching {
        currentSeasonResourcesCacheRepository.refreshCurrentSeasonBundle()
    }.getOrElse { e ->
        BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry(
                    key = "current-season-resources-bundle",
                    result = RefreshFailureClassifier.classify(e),
                ),
            ),
        )
    }

    internal suspend fun refreshCurrentSeasonSessionsBundleForPeriodicSync(now: Instant): BundleRefreshResult = runCatching {
        sessionResultsCacheRepository.refreshCurrentSeasonBundle(now)
    }.getOrElse { e ->
        BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry(
                    key = "current-season-sessions-bundle",
                    result = RefreshFailureClassifier.classify(e),
                ),
            ),
        )
    }

}
