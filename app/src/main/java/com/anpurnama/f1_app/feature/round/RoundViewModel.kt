package com.anpurnama.f1_app.feature.round

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
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetRoundQualifyingUseCase
import com.anpurnama.f1_app.f1.GetRoundResultsUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.cache.SeasonScheduleCacheRepository
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.RoundQualifying
import com.anpurnama.f1_app.f1.model.RoundResults
import com.anpurnama.f1_app.f1.model.RoundMode
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.roundMode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

/**
 * Round detail ViewModel — drives the race results and the
 * qualifying results as two independently-failing sections.
 *
 *  - **Init-less**: first subscription triggers [warmUp]; the cold
 *    stream is `stateIn(Lazily)` so the first load fires once and
 *    subsequent subscribers read the hot `StateFlow` without
 *    re-firing. Re-fire is via [refresh] (pull-to-refresh) only.
 *  - **Pull-to-refresh** re-fires both with `forceRefresh = true`.
 *
 * `year`/`round` are constructor params (not state) — they are
 * forwarded to both use cases on every fire.
 */
class RoundViewModel(
    val year: Int,
    val round: Int,
    private val getRoundResults: suspend (Int, Int, Boolean) -> Outcome<RoundResults>,
    private val getRoundQualifying: suspend (Int, Int, Boolean) -> Outcome<RoundQualifying>,
    private val getSeason: (suspend (Int, Boolean) -> Outcome<Season>)? = null,
    private val observeCachedSeason: Flow<CachedResource<Season>?>? = null,
    private val refreshCachedSeason: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    sealed interface UiState {
        /**
         * The two independently-failing surfaces on the Round detail
         * page: each `SectionUiState` rises or falls on its own call.
         */
        data class Sections(
            val results: SectionUiState<RoundResults>,
            val qualifying: SectionUiState<RoundQualifying>,
            val season: SectionUiState<Season> = SectionUiState.Loading,
            val race: Race? = null,
            val mode: RoundMode? = null,
        ) : UiState
    }

    private val resultsState = MutableStateFlow<SectionUiState<RoundResults>>(SectionUiState.Loading)
    private val qualifyingState = MutableStateFlow<SectionUiState<RoundQualifying>>(SectionUiState.Loading)
    private val seasonState = MutableStateFlow<SectionUiState<Season>>(
        if (getSeason == null) SectionUiState.Content(
            Season(0, emptyList(), 0, 0.0, 0, 0f)
        ) else SectionUiState.Loading
    )
    val uiState: StateFlow<UiState> = combine(
        resultsState, qualifyingState, seasonState,
    ) { results, qualifying, season ->
        val race = (season as? SectionUiState.Content)?.data?.races?.firstOrNull { it.round == round }
        UiState.Sections(
            results = results,
            qualifying = qualifying,
            season = season,
            race = race,
            mode = race?.let { roundMode(it, now()) },
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(
                results = SectionUiState.Loading,
                qualifying = SectionUiState.Loading,
                season = seasonState.value,
            ),
        )

    /**
     * First-load warm-up: fires both use cases in parallel with
     * `forceRefresh = false`. Each writes to its own [MutableStateFlow]
     * so a failure on one never blanks the other.
     */
    private fun warmUp() {
        observeCachedSeason
            ?.onEach { cached ->
                if (cached != null && cached.data.year == year) seasonState.value = cached.toSection(now().toEpochMilliseconds())
            }
            ?.launchIn(viewModelScope)
        viewModelScope.launch { loadResults(forceRefresh = false) }
        viewModelScope.launch { loadQualifying(forceRefresh = false) }
        if (getSeason != null) viewModelScope.launch { loadSeason(forceRefresh = false) }
    }

    /**
     * Public pull-to-refresh. Re-fires both use cases with
     * `forceRefresh = true` (NO_CACHE).
     */
    fun refresh() {
        viewModelScope.launch { loadResults(forceRefresh = true) }
        viewModelScope.launch { loadQualifying(forceRefresh = true) }
        if (getSeason != null) viewModelScope.launch { loadSeason(forceRefresh = true) }
    }

    private suspend fun loadResults(forceRefresh: Boolean) {
        resultsState.value = SectionUiState.Loading
        resultsState.value = getRoundResults(year, round, forceRefresh).toSection()
    }

    private suspend fun loadQualifying(forceRefresh: Boolean) {
        qualifyingState.value = SectionUiState.Loading
        qualifyingState.value = getRoundQualifying(year, round, forceRefresh).toSection()
    }

    private suspend fun loadSeason(forceRefresh: Boolean) {
        val refresh = refreshCachedSeason
        val cached = observeCachedSeason?.first()
        if (refresh != null && cached?.data?.year == year) {
            seasonState.value = cached.toSection(now().toEpochMilliseconds())
            if (seasonState.value is SectionUiState.Content) {
                seasonState.value = (seasonState.value as SectionUiState.Content<Season>).copy(sync = ContentSyncStatus.Refreshing)
            } else {
                seasonState.value = SectionUiState.Loading
            }
            val result = refresh(if (forceRefresh) RefreshReason.PullToRefresh else RefreshReason.StaleOpen)
            if (result is RefreshResult.Success) {
                val refreshed = observeCachedSeason.first()
                if (refreshed?.data?.year == year) {
                    seasonState.value = refreshed.toSection(now().toEpochMilliseconds())
                } else {
                    loadYearSpecificSeason(forceRefresh)
                }
            }
            if (result is RefreshResult.Failure) {
                val current = seasonState.value
                seasonState.value = if (current is SectionUiState.Content) {
                    current.copy(sync = ContentSyncStatus.RefreshFailed(result.message))
                } else {
                    SectionUiState.Error(result.message)
                }
            }
            return
        }
        loadYearSpecificSeason(forceRefresh)
    }

    private suspend fun loadYearSpecificSeason(forceRefresh: Boolean) {
        val useCase = getSeason ?: return
        val existing = seasonState.value
        if (existing !is SectionUiState.Content) seasonState.value = SectionUiState.Loading
        val outcome = useCase(year, forceRefresh)
        seasonState.value = when {
            outcome is Outcome.Failure && existing is SectionUiState.Content ->
                existing.copy(sync = ContentSyncStatus.RefreshFailed(outcome.errorMessage))
            else -> outcome.toSection()
        }
    }
}

/**
 * `viewModelFactory` builder. Mirrors the Schedule pattern — the
 * factory captures the use case instance refs; the VM takes
 * `suspend (Int, Int, Boolean) -> Outcome<…>` lambdas so it never
 * sees the `Wiring` types.
 */
fun roundViewModelFactory(
    year: Int,
    round: Int,
    getRoundResults: GetRoundResultsUseCase,
    getRoundQualifying: GetRoundQualifyingUseCase,
    getSeason: GetSeasonUseCase? = null,
    seasonScheduleCacheRepository: SeasonScheduleCacheRepository? = null,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        RoundViewModel(
            year = year,
            round = round,
            getRoundResults = getRoundResults::invoke,
            getRoundQualifying = getRoundQualifying::invoke,
            getSeason = getSeason?.let { { requestedYear, forceRefresh -> it(requestedYear, forceRefresh) } },
            observeCachedSeason = seasonScheduleCacheRepository?.observeCurrentSeason(),
            refreshCachedSeason = seasonScheduleCacheRepository?.let { repo -> repo::refreshCurrentSeason },
        )
    }
}
