package com.anpurnama.f1_app.feature.myteam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.refreshCachedSection
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
import com.anpurnama.f1_app.f1.cache.CurrentSeasonResourcesCacheRepository
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.feature.favorites.FavoritesCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

enum class DriverSlot { Driver1, Driver2 }

class MyTeamViewModel(
    private val getDriversStandings: suspend (Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (Boolean) -> Outcome<List<ConstructorStanding>>,
    private val favoritesFlow: Flow<Favorites>,
    private val setDriver1: suspend (String) -> Unit,
    private val setDriver2: suspend (String) -> Unit,
    private val setTeam: suspend (String) -> Unit,
    private val observeCachedDrivers: Flow<CachedResource<List<DriverStanding>>?>? = null,
    private val refreshCachedDrivers: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedConstructors: Flow<CachedResource<List<ConstructorStanding>>?>? = null,
    private val refreshCachedConstructors: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val refreshCachedDriverCatalog: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val refreshCachedTeamCatalog: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    data class UiState(
        val favorites: SectionUiState<Favorites>,
        val drivers: SectionUiState<List<DriverStanding>>,
        val constructors: SectionUiState<List<ConstructorStanding>>,
    )

    private val favoritesState =
        MutableStateFlow<SectionUiState<Favorites>>(SectionUiState.Loading)
    private val driversState =
        MutableStateFlow<SectionUiState<List<DriverStanding>>>(SectionUiState.Loading)
    private val constructorsState =
        MutableStateFlow<SectionUiState<List<ConstructorStanding>>>(SectionUiState.Loading)

    val uiState: StateFlow<UiState> = combine(
        favoritesState,
        driversState,
        constructorsState,
        ::UiState,
    )
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState(
                favorites = SectionUiState.Loading,
                drivers = SectionUiState.Loading,
                constructors = SectionUiState.Loading,
            ),
        )

    private fun warmUp() {
        favoritesFlow
            .onEach { favoritesState.value = SectionUiState.Content(it) }
            .launchIn(viewModelScope)
        observeCachedDrivers
            ?.onEach { cached -> if (cached != null) driversState.value = cached.toSection(nowEpochMs()) }
            ?.launchIn(viewModelScope)
        observeCachedConstructors
            ?.onEach { cached -> if (cached != null) constructorsState.value = cached.toSection(nowEpochMs()) }
            ?.launchIn(viewModelScope)
        viewModelScope.launch { loadDrivers(forceRefresh = false) }
        viewModelScope.launch { loadConstructors(forceRefresh = false) }
        viewModelScope.launch { refreshCatalogs(forceRefresh = false) }
    }

    fun refresh() {
        viewModelScope.launch { loadDrivers(forceRefresh = true) }
        viewModelScope.launch { loadConstructors(forceRefresh = true) }
        viewModelScope.launch { refreshCatalogs(forceRefresh = true) }
    }

    private suspend fun refreshCatalogs(forceRefresh: Boolean) {
        val reason = if (forceRefresh) RefreshReason.PullToRefresh else RefreshReason.StaleOpen
        refreshCachedDriverCatalog?.invoke(reason)
        refreshCachedTeamCatalog?.invoke(reason)
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        val refresh = refreshCachedDrivers
        if (observeCachedDrivers != null && refresh != null) {
            driversState.refreshCachedSection(forceRefresh, refresh)
            return
        }
        driversState.value = SectionUiState.Loading
        driversState.value = getDriversStandings(forceRefresh).toSection()
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
        val refresh = refreshCachedConstructors
        if (observeCachedConstructors != null && refresh != null) {
            constructorsState.refreshCachedSection(forceRefresh, refresh)
            return
        }
        constructorsState.value = SectionUiState.Loading
        constructorsState.value = getConstructorsStandings(forceRefresh).toSection()
    }

    fun selectDriver(slot: DriverSlot, driverId: String) {
        val favorites = (favoritesState.value as? SectionUiState.Content)?.data ?: return
        val isUsedByOtherSlot = when (slot) {
            DriverSlot.Driver1 -> favorites.driver2Id == driverId
            DriverSlot.Driver2 -> favorites.driver1Id == driverId
        }
        if (isUsedByOtherSlot) return

        viewModelScope.launch {
            when (slot) {
                DriverSlot.Driver1 -> setDriver1(driverId)
                DriverSlot.Driver2 -> setDriver2(driverId)
            }
        }
    }

    fun selectTeam(teamId: String) {
        viewModelScope.launch { setTeam(teamId) }
    }
}

fun myTeamViewModelFactory(
    getDriversStandings: GetDriversStandingsUseCase,
    getConstructorsStandings: GetConstructorsStandingsUseCase,
    favoritesCache: FavoritesCache,
    currentSeasonResourcesCacheRepository: CurrentSeasonResourcesCacheRepository? = null,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        MyTeamViewModel(
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
            favoritesFlow = favoritesCache.read(),
            setDriver1 = favoritesCache::setDriver1,
            setDriver2 = favoritesCache::setDriver2,
            setTeam = favoritesCache::setTeam,
            observeCachedDrivers = currentSeasonResourcesCacheRepository?.observeDriverStandings(),
            refreshCachedDrivers = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshDriverStandings },
            observeCachedConstructors = currentSeasonResourcesCacheRepository?.observeConstructorStandings(),
            refreshCachedConstructors = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshConstructorStandings },
            refreshCachedDriverCatalog = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshDriverCatalog },
            refreshCachedTeamCatalog = currentSeasonResourcesCacheRepository?.let { repo -> repo::refreshTeamCatalog },
        )
    }
}
