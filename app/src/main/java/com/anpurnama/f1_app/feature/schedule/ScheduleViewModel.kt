package com.anpurnama.f1_app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetRoundPodiumUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds independently-failing season and per-round podium state. Refresh reloads rows here
 * because a same-key UI effect does not restart; concurrent row updates must stay atomic.
 */
class ScheduleViewModel(
    private val getSeason: suspend (forceRefresh: Boolean) -> Outcome<Season>,
    private val getRoundPodium: suspend (
        year: Int,
        round: Int,
        forceRefresh: Boolean,
    ) -> Outcome<RoundPodium>
) : ViewModel() {

    sealed interface UiState {
        /** A failed podium leaves the season and other rows available. */
        data class Sections(
            val season: SectionUiState<Season>,
            val year: Int,
            val podiums: Map<Int, SectionUiState<RoundPodium>>,
        ) : UiState
    }

    private val seasonState = MutableStateFlow<SectionUiState<Season>>(SectionUiState.Loading)
    private val podiumsState = MutableStateFlow<Map<Int, SectionUiState<RoundPodium>>>(emptyMap())
    private val yearState = MutableStateFlow(0)

    val uiState: StateFlow<UiState> = combine(
        seasonState,
        yearState,
        podiumsState,
    ) { season, year, podiums, ->
        UiState.Sections(
            season = season,
            year = year,
            podiums = podiums,
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(
                season = SectionUiState.Loading,
                year = 0,
                podiums = emptyMap(),
            ),
        )

    private fun warmUp() {
        viewModelScope.launch {
            loadSeason(forceRefresh = false)
        }
    }

    /** Forces the season and every past podium to reload. */
    fun refresh() {
        viewModelScope.launch {
            loadSeason(forceRefresh = true)
        }
    }

    /** Rejects a retry while the season or requested row is loading. */
    fun retryPodium(round: Int): Boolean {
        val year = yearState.value
        if (year == 0 || podiumsState.value[round] is SectionUiState.Loading) {
            return false
        }
        viewModelScope.launch {
            loadPodium(year = year, round = round, forceRefresh = true)
        }
        return true
    }

    private suspend fun loadSeason(forceRefresh: Boolean) {
        seasonState.value = SectionUiState.Loading
        val section = getSeason(forceRefresh).toSection()
        seasonState.value = section
        when (section) {
            is SectionUiState.Content -> {
                val season = section.data
                yearState.value = season.year

                val pastRounds = season.races.filter { it.winnerId != null }
                podiumsState.value = if (pastRounds.isEmpty()) emptyMap()
                    else pastRounds.associate { it.round to SectionUiState.Loading }

                pastRounds.forEach { race ->
                    viewModelScope.launch { loadPodium(year = season.year, round = race.round, forceRefresh = forceRefresh) }
                }
            }
            is SectionUiState.Error -> {
                yearState.value = 0
                podiumsState.value = emptyMap()
            }
            is SectionUiState.Loading -> Unit
        }
    }

    /** Atomic updates prevent concurrent row loads from losing state. */
    suspend fun loadPodium(year: Int, round: Int, forceRefresh: Boolean) {
        if (!forceRefresh &&
            podiumsState.value[round] is SectionUiState.Content
        ) return
        podiumsState.update { it + (round to SectionUiState.Loading) }
        podiumsState.update { it + (round to getRoundPodium(year, round, forceRefresh).toSection()) }
    }
}

fun scheduleViewModelFactory(
    getSeason: GetSeasonUseCase,
    getRoundPodium: GetRoundPodiumUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ScheduleViewModel(
            getSeason = getSeason::invoke,
            getRoundPodium = getRoundPodium::invoke,
        )
    }
}
