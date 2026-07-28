package com.anpurnama.f1_app.feature.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.ui.ContentSyncStatus
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.refreshCachedSection
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.feature.favorites.FavoritesCache
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
import com.anpurnama.f1_app.f1.GetNextRaceUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.cache.CurrentSeasonResourcesCacheRepository
import com.anpurnama.f1_app.f1.cache.SeasonScheduleCacheRepository
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.WeekendSchedule
import com.anpurnama.f1_app.f1.model.toWeekendSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Homepage state holder. Each screen section owns an independent
 * [SectionUiState]; the countdown schedule is derived from the two primary
 * f1api.dev payloads rather than making a second network request.
 */
class HomepageViewModel(
    private val getSeason: suspend (forceRefresh: Boolean) -> Outcome<Season>,
    private val getNextRace: suspend (forceRefresh: Boolean) -> Outcome<NextRace?>,
    private val getDriversStandings: suspend (forceRefresh: Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (forceRefresh: Boolean) -> Outcome<List<ConstructorStanding>>,
    private val favoritesFlow: Flow<Favorites>,
    private val seedIfEmpty: suspend (topTeamId: String, topDriverIds: List<String>) -> Unit,
    private val observeCachedSeason: Flow<CachedResource<Season>?>? = null,
    private val refreshCachedSeason: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedNextRace: Flow<CachedResource<NextRace?>?>? = null,
    private val refreshCachedNextRace: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedDrivers: Flow<CachedResource<List<DriverStanding>>?>? = null,
    private val refreshCachedDrivers: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedConstructors: Flow<CachedResource<List<ConstructorStanding>>?>? = null,
    private val refreshCachedConstructors: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    sealed interface UiState {
        data class Sections(
            val favorites: SectionUiState<Favorites>,
            val season: SectionUiState<Season>,
            val nextRace: SectionUiState<NextRace?>,
            val drivers: SectionUiState<List<DriverStanding>>,
            val constructors: SectionUiState<List<ConstructorStanding>>,
            val weekendSchedule: SectionUiState<WeekendSchedule?>,
        ) : UiState
    }

    private val favorites = MutableStateFlow<SectionUiState<Favorites>>(SectionUiState.Loading)
    private val season = MutableStateFlow<SectionUiState<Season>>(SectionUiState.Loading)
    private val nextRace = MutableStateFlow<SectionUiState<NextRace?>>(SectionUiState.Loading)
    private val drivers = MutableStateFlow<SectionUiState<List<DriverStanding>>>(SectionUiState.Loading)
    private val constructors = MutableStateFlow<SectionUiState<List<ConstructorStanding>>>(SectionUiState.Loading)
    private val weekendSchedule = MutableStateFlow<SectionUiState<WeekendSchedule?>>(SectionUiState.Loading)

    private data class PrimarySections(
        val favorites: SectionUiState<Favorites>,
        val season: SectionUiState<Season>,
        val nextRace: SectionUiState<NextRace?>,
        val drivers: SectionUiState<List<DriverStanding>>,
        val constructors: SectionUiState<List<ConstructorStanding>>,
    )

    val uiState: StateFlow<UiState> = combine(
        combine(favorites, season, nextRace, drivers, constructors) { f, s, n, d, c ->
            PrimarySections(f, s, n, d, c)
        },
        weekendSchedule,
    ) { primary, schedule ->
        UiState.Sections(
            favorites = primary.favorites,
            season = primary.season,
            nextRace = primary.nextRace,
            drivers = primary.drivers,
            constructors = primary.constructors,
            weekendSchedule = schedule,
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(
                favorites = SectionUiState.Loading,
                season = SectionUiState.Loading,
                nextRace = SectionUiState.Loading,
                drivers = SectionUiState.Loading,
                constructors = SectionUiState.Loading,
                weekendSchedule = SectionUiState.Loading,
            ),
        )

    private fun warmUp() {
        favoritesFlow
            .onEach { favorites.value = SectionUiState.Content(it) }
            .launchIn(viewModelScope)

        observeCachedSeason
            ?.onEach { cached ->
                if (cached != null) {
                    season.value = cached.toSection(nowEpochMs())
                    deriveWeekendSchedule()
                }
            }
            ?.launchIn(viewModelScope)

        observeCachedNextRace
            ?.onEach { cached ->
                if (cached != null) {
                    nextRace.value = cached.toSection(nowEpochMs())
                    deriveWeekendSchedule()
                }
            }
            ?.launchIn(viewModelScope)

        observeCachedDrivers
            ?.onEach { cached ->
                if (cached != null) drivers.value = cached.toSection(nowEpochMs())
            }
            ?.launchIn(viewModelScope)

        observeCachedConstructors
            ?.onEach { cached ->
                if (cached != null) constructors.value = cached.toSection(nowEpochMs())
            }
            ?.launchIn(viewModelScope)

        viewModelScope.launch {
            loadSeason(false)
            loadNextRace(false)
            deriveWeekendSchedule()
            loadDrivers(false)
            loadConstructors(false)
            seedIfCacheEmpty()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadSeason(true)
            loadNextRace(true)
            deriveWeekendSchedule()
            loadDrivers(true)
            loadConstructors(true)
        }
    }

    private suspend fun loadSeason(forceRefresh: Boolean) {
        val refresh = refreshCachedSeason
        if (observeCachedSeason != null && refresh != null) {
            if (season.value is SectionUiState.Content) {
                season.value = (season.value as SectionUiState.Content<Season>).copy(sync = ContentSyncStatus.Refreshing)
            } else {
                season.value = SectionUiState.Loading
            }
            val result = refresh(if (forceRefresh) RefreshReason.PullToRefresh else RefreshReason.StaleOpen)
            if (result is RefreshResult.Success && season.value is SectionUiState.Content) {
                season.value = (season.value as SectionUiState.Content<Season>).copy(sync = ContentSyncStatus.Fresh)
            }
            if (result is RefreshResult.Failure) {
                val current = season.value
                season.value = if (current is SectionUiState.Content) {
                    current.copy(sync = ContentSyncStatus.RefreshFailed(result.message))
                } else {
                    SectionUiState.Error(result.message)
                }
            }
            return
        }
        season.value = SectionUiState.Loading
        season.value = getSeason(forceRefresh).toSection()
    }

    private suspend fun loadNextRace(forceRefresh: Boolean) {
        val refresh = refreshCachedNextRace
        if (observeCachedNextRace != null && refresh != null) {
            nextRace.refreshCachedSection(forceRefresh, refresh)
            return
        }
        nextRace.value = SectionUiState.Loading
        nextRace.value = getNextRace(forceRefresh).toSection()
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        val refresh = refreshCachedDrivers
        if (observeCachedDrivers != null && refresh != null) {
            drivers.refreshCachedSection(forceRefresh, refresh)
            return
        }
        drivers.value = SectionUiState.Loading
        drivers.value = getDriversStandings(forceRefresh).toSection()
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
        val refresh = refreshCachedConstructors
        if (observeCachedConstructors != null && refresh != null) {
            constructors.refreshCachedSection(forceRefresh, refresh)
            return
        }
        constructors.value = SectionUiState.Loading
        constructors.value = getConstructorsStandings(forceRefresh).toSection()
    }

    private fun deriveWeekendSchedule() {
        val next = (nextRace.value as? SectionUiState.Content)?.data
        val currentSeason = (season.value as? SectionUiState.Content)?.data
        val schedule = next?.let { race ->
            currentSeason?.races
                ?.firstOrNull { it.round == race.round }
                ?.schedule
                ?.toWeekendSchedule()
        }
        weekendSchedule.value = SectionUiState.Content(schedule)
    }

    private suspend fun seedIfCacheEmpty() {
        val favs = favoritesFlow.first()
        if (!favs.isEmpty()) return
        val con = (constructors.value as? SectionUiState.Content)?.data ?: return
        val drv = (drivers.value as? SectionUiState.Content)?.data ?: return
        val topTeamId = con.firstOrNull()?.teamId ?: return
        val topDriverIds = drv.filter { it.teamId == topTeamId }.take(2).map { it.driverId }
        if (topDriverIds.size == 2) seedIfEmpty(topTeamId, topDriverIds)
    }
}

fun homepageViewModelFactory(
    getSeason: GetSeasonUseCase,
    getNextRace: GetNextRaceUseCase,
    getDriversStandings: GetDriversStandingsUseCase,
    getConstructorsStandings: GetConstructorsStandingsUseCase,
    favoritesCache: FavoritesCache,
    seasonScheduleCacheRepository: SeasonScheduleCacheRepository? = null,
    currentSeasonResourcesCacheRepository: CurrentSeasonResourcesCacheRepository? = null,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomepageViewModel(
            getSeason = getSeason::invoke,
            getNextRace = getNextRace::invoke,
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
            favoritesFlow = favoritesCache.read(),
            seedIfEmpty = favoritesCache::seedIfEmpty,
            observeCachedSeason = seasonScheduleCacheRepository?.observeCurrentSeason(),
            refreshCachedSeason = seasonScheduleCacheRepository?.let { repo -> repo::refreshCurrentSeason },
            observeCachedNextRace = currentSeasonResourcesCacheRepository?.observeNextRace(),
            refreshCachedNextRace = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshNextRace },
            observeCachedDrivers = currentSeasonResourcesCacheRepository?.observeDriverStandings(),
            refreshCachedDrivers = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshDriverStandings },
            observeCachedConstructors = currentSeasonResourcesCacheRepository?.observeConstructorStandings(),
            refreshCachedConstructors = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshConstructorStandings },
        )
    }
}
