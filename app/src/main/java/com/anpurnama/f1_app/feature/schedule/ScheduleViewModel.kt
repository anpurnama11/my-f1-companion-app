package com.anpurnama.f1_app.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetCircuitImageUseCase
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
 * Schedule tab ViewModel — drives both the Upcoming list and the Past
 * list, plus a per-race circuit image.
 *
 *  - [GetSeasonUseCase] (ticket 01) feeds the whole tab; the season
 *    response carries the per-round past/upcoming split via
 *    `Race.winnerId`.
 *  - [GetRoundPodiumUseCase] drives per-row past-row podiums (one
 *    `/race` call per past round).
 *  - [GetCircuitImageUseCase] (homepage §3 reuse) drives a per-round
 *    OpenF1 track-layout image — pre-loaded eagerly in the VM so the
 *    tab switch from Upcoming to Past (and back) is instant with no
 *    re-fetch.
 *
 *  Section independence is the contract: every surface has its own
 *  [SectionUiState] and no composite use case. Per-row podiums and
 *  per-row circuit images are independent — a single past round's
 *  `/race` failure is a retry row, not a screen blank; a single race's
 *  OpenF1 failure leaves the image cell empty (the row still renders
 *  its other fields).
 *
 *  Init-less: first subscription triggers the warmUp; re-subscription
 *  within `WhileSubscribed(5_000)` reuses the cached state.
 *
 *  Refresh re-fires the season with `forceRefresh = true` and, in the
 *  Content branch, re-fires every past round's `/race` and every
 *  race's OpenF1 image call. This is the only path that re-loads
 *  per-row state — the screen's `LaunchedEffect(race.round)` does NOT
 *  re-fire on a same-key re-render, so the VM must own the re-fire
 *  (revision 1: fix the refresh-nukes-content bug; see
 *  `ScheduleViewModelRefreshAfterContentTest`).
 *
 *  Concurrency: per-row writes to [podiumsState] / [circuitImagesState]
 *  use atomic `MutableStateFlow.update { }` (not non-atomic RMW), so
 *  concurrent `loadPodium` / `loadCircuitImage` calls on different
 *  rounds never lose updates (revision 1: fix the RMW race; see
 *  `ScheduleViewModelRmwRaceTest`).
 */
class ScheduleViewModel(
    private val getSeason: suspend (forceRefresh: Boolean) -> Outcome<Season>,
    private val getRoundPodium: suspend (
        year: Int,
        round: Int,
        forceRefresh: Boolean,
    ) -> Outcome<RoundPodium>,
    private val getCircuitImage: suspend (year: Int, country: String) -> Outcome<String?>,
) : ViewModel() {

    sealed interface UiState {
        /**
         * The independently-failing surfaces on the Schedule tab:
         *  - `season` — the list itself (failure here = whole list is
         *    the `OutcomeContent` failure UI; no past rounds to show).
         *  - `podiums` — per-round podium state, keyed by round
         *    (1-based). Failure on one round = retry row; the other
         *    past rounds keep their content.
         *  - `circuitImages` — per-round OpenF1 image URL state, keyed
         *    by round. Failure on one round = image cell is empty;
         *    the other races' image cells render normally.
         */
        data class Sections(
            val season: SectionUiState<Season>,
            val year: Int,
            val podiums: Map<Int, SectionUiState<RoundPodium>>,
            val circuitImages: Map<Int, SectionUiState<String?>>,
        ) : UiState
    }

    private val seasonState = MutableStateFlow<SectionUiState<Season>>(SectionUiState.Loading)
    private val podiumsState = MutableStateFlow<Map<Int, SectionUiState<RoundPodium>>>(emptyMap())
    private val circuitImagesState = MutableStateFlow<Map<Int, SectionUiState<String?>>>(emptyMap())
    private val yearState = MutableStateFlow(0)

    val uiState: StateFlow<UiState> = combine(
        seasonState,
        yearState,
        podiumsState,
        circuitImagesState,
    ) { season, year, podiums, circuitImages ->
        UiState.Sections(
            season = season,
            year = year,
            podiums = podiums,
            circuitImages = circuitImages,
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Sections(
                season = SectionUiState.Loading,
                year = 0,
                podiums = emptyMap(),
                circuitImages = emptyMap(),
            ),
        )

    private fun warmUp() {
        viewModelScope.launch {
            loadSeason(forceRefresh = false)
        }
    }

    /**
     * Public pull-to-refresh. Re-fires `/current` with
     * `forceRefresh = true` (NO_CACHE) and, in the Content branch,
     * re-fires every past round's `/race` and every race's OpenF1
     * image. Upcoming rows have no podium fetch; circuit images are
     * re-fired for all races (upcoming + past).
     */
    fun refresh() {
        viewModelScope.launch {
            loadSeason(forceRefresh = true)
        }
    }

    /**
     * Per-row retry. Re-fires a single past round's `/race` call with
     * `forceRefresh = true` (the cached value is the stale one that
     * just failed; retry expects a fresh hit).
     */
    fun retryPodium(round: Int) {
        val year = yearState.value
        if (year == 0) return  // no past rounds known yet
        viewModelScope.launch {
            loadPodium(year = year, round = round, forceRefresh = true)
        }
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
                // Seed both per-row maps to Loading so the screen
                // shows spinners immediately. Both writes are atomic
                // assignments of new maps (not RMW), so no race.
                podiumsState.value = if (pastRounds.isEmpty()) emptyMap()
                    else pastRounds.associate { it.round to SectionUiState.Loading }
                // All races seed to Loading — circuit images are
                // fetched from OpenF1 for every race (upcoming + past)
                // that has a country. Rows without a country seed to
                // Content(null) — brand accent fallback, no fetch.
                circuitImagesState.value = season.races
                    .associate { it.round to SectionUiState.Loading }

                // Fire per-row circuit image loads for all
                // country-bearing races. The VM owns the re-fire on
                // refresh (the screen's `LaunchedEffect` does NOT
                // re-fire on same-key re-render). Rows without a
                // country get the accent-only fallback immediately.
                season.races.forEach { race ->
                    val country = race.circuit.country
                    if (country != null) {
                        viewModelScope.launch { loadCircuitImage(year = season.year, round = race.round, country = country) }
                    } else {
                        circuitImagesState.update {
                            it + (race.round to SectionUiState.Content<String?>(null))
                        }
                    }
                }
                pastRounds.forEach { race ->
                    viewModelScope.launch { loadPodium(year = season.year, round = race.round, forceRefresh = forceRefresh) }
                }
            }
            is SectionUiState.Error -> {
                yearState.value = 0
                podiumsState.value = emptyMap()
                circuitImagesState.value = emptyMap()
            }
            is SectionUiState.Loading -> {
                // `getSeason` does not emit Loading in practice; the VM
                // sets Loading itself before the call. No-op.
            }
        }
    }

    /**
     * Loads a single past round's podium into the per-row map.
     * Idempotent on Content unless `forceRefresh` is true (the
     * refresh path explicitly bypasses the cache to re-fetch
     * round-trip after a pull; the screen's `LaunchedEffect` is the
     * cold-open path and gets the no-op fast path).
     *
     * Both writes use atomic [MutableStateFlow.update] so concurrent
     * calls on different rounds never lose updates.
     */
    suspend fun loadPodium(year: Int, round: Int, forceRefresh: Boolean) {
        if (!forceRefresh &&
            podiumsState.value[round] is SectionUiState.Content
        ) return
        podiumsState.update { it + (round to SectionUiState.Loading) }
        podiumsState.update { it + (round to getRoundPodium(year, round, forceRefresh).toSection()) }
    }

    /**
     * Loads a single race's OpenF1 circuit image into the per-row
     * map. Atomic [MutableStateFlow.update] writes. OpenF1 has no
     * cache layer (per spec), so the `forceRefresh` flag is recorded
     * but the underlying call always hits the network.
     */
    private suspend fun loadCircuitImage(year: Int, round: Int, country: String) {
        circuitImagesState.update { it + (round to SectionUiState.Loading) }
        circuitImagesState.update {
            it + (round to getCircuitImage(year, country).toSection())
        }
    }
}

/**
 * `viewModelFactory` builder. Mirrors the Homepage pattern — the
 * factory captures the use case instance refs from `Wiring`; the VM
 * takes `suspend (...) -> Outcome<…>` lambdas so it never sees the
 * `Wiring` types.
 */
fun scheduleViewModelFactory(
    getSeason: GetSeasonUseCase,
    getRoundPodium: GetRoundPodiumUseCase,
    getCircuitImage: GetCircuitImageUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ScheduleViewModel(
            getSeason = getSeason::invoke,
            getRoundPodium = getRoundPodium::invoke,
            getCircuitImage = getCircuitImage::invoke,
        )
    }
}
