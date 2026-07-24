package com.anpurnama.f1_app.feature.myteam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
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

enum class DriverSlot { Driver1, Driver2 }

class MyTeamViewModel(
    private val getDriversStandings: suspend (Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (Boolean) -> Outcome<List<ConstructorStanding>>,
    private val favoritesFlow: Flow<Favorites>,
    private val setDriver1: suspend (String) -> Unit,
    private val setDriver2: suspend (String) -> Unit,
    private val setTeam: suspend (String) -> Unit,
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
        viewModelScope.launch { loadDrivers(forceRefresh = false) }
        viewModelScope.launch { loadConstructors(forceRefresh = false) }
    }

    fun refresh() {
        viewModelScope.launch { loadDrivers(forceRefresh = true) }
        viewModelScope.launch { loadConstructors(forceRefresh = true) }
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        driversState.value = SectionUiState.Loading
        driversState.value = getDriversStandings(forceRefresh).toSection()
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
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
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        MyTeamViewModel(
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
            favoritesFlow = favoritesCache.read(),
            setDriver1 = favoritesCache::setDriver1,
            setDriver2 = favoritesCache::setDriver2,
            setTeam = favoritesCache::setTeam,
        )
    }
}
