package com.anpurnama.f1_app.feature.leaderboard

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LeaderboardViewModel(
    private val getDriversStandings: suspend (Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (Boolean) -> Outcome<List<ConstructorStanding>>,
) : ViewModel() {

    sealed interface UiState {
        data class Sections(
            val drivers: SectionUiState<List<DriverStanding>>,
            val constructors: SectionUiState<List<ConstructorStanding>>,
        ) : UiState
    }

    private val driversState =
        MutableStateFlow<SectionUiState<List<DriverStanding>>>(SectionUiState.Loading)
    private val constructorsState =
        MutableStateFlow<SectionUiState<List<ConstructorStanding>>>(SectionUiState.Loading)

    val uiState: StateFlow<UiState> = combine(driversState, constructorsState) { drivers, constructors ->
        UiState.Sections(drivers = drivers, constructors = constructors)
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(
                drivers = SectionUiState.Loading,
                constructors = SectionUiState.Loading,
            ),
        )

    private fun warmUp() {
        viewModelScope.launch { loadDrivers(false) }
        viewModelScope.launch { loadConstructors(false) }
    }

    fun refresh() {
        viewModelScope.launch { loadDrivers(true) }
        viewModelScope.launch { loadConstructors(true) }
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        driversState.value = SectionUiState.Loading
        driversState.value = getDriversStandings(forceRefresh).toSection()
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
        constructorsState.value = SectionUiState.Loading
        constructorsState.value = getConstructorsStandings(forceRefresh).toSection()
    }
}

fun leaderboardViewModelFactory(
    getDriversStandings: GetDriversStandingsUseCase,
    getConstructorsStandings: GetConstructorsStandingsUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        LeaderboardViewModel(
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
        )
    }
}
