package com.anpurnama.f1_app.feature.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.feature.favorites.FavoritesCache
import com.anpurnama.f1_app.f1.GetCircuitImageUseCase
import com.anpurnama.f1_app.f1.GetCircuitTopSpeedUseCase
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
import com.anpurnama.f1_app.f1.GetNextRaceUseCase
import com.anpurnama.f1_app.f1.GetRaceWeekendScheduleUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.TopSpeed
import com.anpurnama.f1_app.f1.model.WeekendSchedule
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
 * Homepage ViewModel — combines 5 use cases + reads the favorites cache.
 *
 *  - §1 favorite pager — `Favorites` cache + the constructors' standing
 *    (to resolve the picked team) + the drivers' standing (to resolve the
 *    two picked drivers) + the next race (for the GP card).
 *  - §2 season aggregates — `GetSeasonUseCase`.
 *  - §3 nearest GP info — `GetNextRaceUseCase` + `GetCircuitTopSpeedUseCase`.
 *
 * **Section independence:** every section has its own [SectionUiState] and
 * each use case runs in isolation. A 4xx/5xx on the drivers endpoint does NOT
 * blank the screen; the drivers section shows the shared `OutcomeContent`
 * failure UI and the other sections stay in their state. No composite
 * "get homepage data" use case. Use cases return [Outcome] (data-layer result);
 * the VM maps each to [SectionUiState] (UI transport) at the assignment site —
 * the composable never imports [Outcome].
 *
 * **First-launch seed:** when the favorites cache is empty AND the
 * constructors + drivers standings have loaded, write the #1 constructor
 * + its two drivers. The seed is a one-shot side effect; if the user has
 * already filled any slot, the seed leaves it alone (see `FavoritesCache.seedIfEmpty`).
 *
 * **Pull-to-refresh:** `refresh()` re-fires every use case with
 * `forceRefresh = true` so each adds `Cache-Control: no-cache` (OpenF1
 * doesn't send cache headers so its request is fresh either way). The
 * favorites cache is not re-read on refresh — it's local and already fresh.
 *
 * Init-less: first subscription to `uiState` triggers the loads via
 * `Flow.onStart { ... }`. Re-subscription within `WhileSubscribed(5_000)`
 * gets the cached state without re-firing.
 */
class HomepageViewModel(
    private val getSeason: suspend (forceRefresh: Boolean) -> Outcome<Season>,
    private val getNextRace: suspend (forceRefresh: Boolean) -> Outcome<NextRace?>,
    private val getRaceWeekendSchedule: suspend (year: Int, country: String) -> Outcome<WeekendSchedule?>,
    private val getDriversStandings: suspend (forceRefresh: Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (forceRefresh: Boolean) -> Outcome<List<ConstructorStanding>>,
    private val getCircuitTopSpeed: suspend (
        circuitId: String,
        country: String,
        year: Int,
        qualyDate: String,
    ) -> Outcome<TopSpeed?>,
    private val getCircuitImage: suspend (year: Int, country: String) -> Outcome<String?>,
    private val favoritesFlow: Flow<Favorites>,
    private val seedIfEmpty: suspend (topTeamId: String, topDriverIds: List<String>) -> Unit,
) : ViewModel() {

    sealed interface UiState {
        /**
         * Composite of the sections (7 atoms: favorites + 6 use case
         * states). The screen renders each via the shared `OutcomeContent`
         * family — section independence is the contract.
         */
        data class Sections(
            val favorites: SectionUiState<Favorites>,
            val season: SectionUiState<Season>,
            val nextRace: SectionUiState<NextRace?>,
            val drivers: SectionUiState<List<DriverStanding>>,
            val constructors: SectionUiState<List<ConstructorStanding>>,
            val topSpeed: SectionUiState<TopSpeed?>,
            val weekendSchedule: SectionUiState<WeekendSchedule?>,
            val circuitImage: SectionUiState<String?>,
        ) : UiState
    }

    private val favorites = MutableStateFlow<SectionUiState<Favorites>>(SectionUiState.Loading)
    private val season = MutableStateFlow<SectionUiState<Season>>(SectionUiState.Loading)
    private val nextRace = MutableStateFlow<SectionUiState<NextRace?>>(SectionUiState.Loading)
    private val drivers = MutableStateFlow<SectionUiState<List<DriverStanding>>>(SectionUiState.Loading)
    private val constructors = MutableStateFlow<SectionUiState<List<ConstructorStanding>>>(SectionUiState.Loading)
    private val topSpeed = MutableStateFlow<SectionUiState<TopSpeed?>>(SectionUiState.Loading)
    private val weekendSchedule = MutableStateFlow<SectionUiState<WeekendSchedule?>>(SectionUiState.Loading)
    private val circuitImage = MutableStateFlow<SectionUiState<String?>>(SectionUiState.Loading)

    /**
     * Intermediate shapes for the 5-arg + 2-arg combine. The 7 atoms
     * fold in via two typed combines — no vararg `Array<Any?>` cast.
     * `private` so the public `UiState` surface is the only thing callers see.
     */
    private data class Sections5(
        val favorites: SectionUiState<Favorites>,
        val season: SectionUiState<Season>,
        val nextRace: SectionUiState<NextRace?>,
        val drivers: SectionUiState<List<DriverStanding>>,
        val constructors: SectionUiState<List<ConstructorStanding>>,
    )

    private data class Sections2(
        val topSpeed: SectionUiState<TopSpeed?>,
        val weekendSchedule: SectionUiState<WeekendSchedule?>,
        val circuitImage: SectionUiState<String?>,
    )

    val uiState: StateFlow<UiState> = combine(
        combine(favorites, season, nextRace, drivers, constructors) { f, s, n, d, c ->
            Sections5(f, s, n, d, c)
        },
        combine(topSpeed, weekendSchedule, circuitImage) { ts, ws, ci -> Sections2(ts, ws, ci) },
    ) { five, two ->
        UiState.Sections(
            favorites = five.favorites,
            season = five.season,
            nextRace = five.nextRace,
            drivers = five.drivers,
            constructors = five.constructors,
            topSpeed = two.topSpeed,
            weekendSchedule = two.weekendSchedule,
            circuitImage = two.circuitImage,
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            // Loading sentinel for the first frame so the screen has
            // something to show. The first real emission arrives when
            // `warmUp()` finishes.
            initialValue = UiState.Sections(
                favorites = SectionUiState.Loading,
                season = SectionUiState.Loading,
                nextRace = SectionUiState.Loading,
                drivers = SectionUiState.Loading,
                constructors = SectionUiState.Loading,
                topSpeed = SectionUiState.Loading,
                weekendSchedule = SectionUiState.Loading,
                circuitImage = SectionUiState.Loading,
            ),
        )

    /**
     * Subscribes to the favorites cache and fires the use case loads.
     * Each use case writes to its own atom; section independence is
     * enforced by the per-atom [SectionUiState] (no composite use case).
     */
    private fun warmUp() {
        // Reactive favorites read — emits empty on first launch, populated
        // values after the seed runs.
        favoritesFlow
            .onEach { favorites.value = SectionUiState.Content(it) }
            .launchIn(viewModelScope)

        // One-shot first load (no forceRefresh). Each section is independent;
        // topSpeed + weekendSchedule + circuitImage are derived from the
        // next-race atom so they load AFTER loadNextRace completes. The seed
        // runs LAST so the standings atoms are populated when the check fires;
        // it waits for the first favorites emission to read the current cache
        // state.
        viewModelScope.launch {
            loadSeason(forceRefresh = false)
            loadNextRace(forceRefresh = false)
            loadRaceDerivedSections(forceRefresh = false)
            loadDrivers(forceRefresh = false)
            loadConstructors(forceRefresh = false)
            seedIfCacheEmpty()
        }
    }

    /**
     * Public pull-to-refresh. Re-fires every use case with
     * `forceRefresh = true` so each adds `Cache-Control: no-cache`. The
     * top-speed cell + weekend schedule refresh too if a next race is
     * known.
     */
    fun refresh() {
        viewModelScope.launch {
            loadSeason(forceRefresh = true)
            loadNextRace(forceRefresh = true)
            loadRaceDerivedSections(forceRefresh = true)
            loadDrivers(forceRefresh = true)
            loadConstructors(forceRefresh = true)
        }
    }

    private suspend fun loadSeason(forceRefresh: Boolean) {
        season.value = SectionUiState.Loading
        season.value = getSeason(forceRefresh).toSection()
    }

    private suspend fun loadNextRace(forceRefresh: Boolean) {
        nextRace.value = SectionUiState.Loading
        nextRace.value = getNextRace(forceRefresh).toSection()
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        drivers.value = SectionUiState.Loading
        drivers.value = getDriversStandings(forceRefresh).toSection()
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
        constructors.value = SectionUiState.Loading
        constructors.value = getConstructorsStandings(forceRefresh).toSection()
    }

    /**
     * Loads the three sections that are derived from the current `nextRace`
     * atom: top speed, weekend schedule, and circuit image. If the season is
     * over (next race is `null`), the atoms are cleared to `Content(null)` so
     * the UI shows an empty state instead of stale data.
     *
     * This is called immediately after every `loadNextRace()` — in `warmUp()`
     * and `refresh()` — which is the only path that writes the `nextRace`
     * atom. No reactive observer is needed because the atom has no other
     * writer.
     */
    private suspend fun loadRaceDerivedSections(forceRefresh: Boolean) {
        val next = (nextRace.value as? SectionUiState.Content)?.data
        if (next == null) {
            topSpeed.value = SectionUiState.Content(null)
            weekendSchedule.value = SectionUiState.Content(null)
            circuitImage.value = SectionUiState.Content(null)
            return
        }
        loadTopSpeed(forceRefresh)
        loadWeekendSchedule()
        loadCircuitImage()
    }

    /**
     * Loads §3's top-speed cell from the current `nextRace` atom. Skips
     * if the next race hasn't loaded, the season is over (race = null),
     * or the inlined race is missing the country / Qualifying date the
     * OpenF1 join needs. Each `?:` early-out keeps this a simple
     * happy-path read; the use case handles the actual session_key join.
     */
    private suspend fun loadTopSpeed(forceRefresh: Boolean) {
        val next = (nextRace.value as? SectionUiState.Content)?.data ?: return
        val circuit = next.circuit
        val qualyDate = next.qualyDate ?: next.raceDate ?: return
        val country = circuit.country ?: return
        topSpeed.value = SectionUiState.Loading
        topSpeed.value = getCircuitTopSpeed(circuit.id, country, next.year, qualyDate).toSection()
    }

    /**
     * Loads §1's race-weekend schedule from the current `nextRace` atom.
     * Skips for the same reasons `loadTopSpeed` does — no next race, or
     * the inlined race is missing the (year, country) the OpenF1 lookup
     * needs. The use case handles the country-divergence fallback
     * (Silverstone) and the empty-session case (`Success(null)`).
     */
    private suspend fun loadWeekendSchedule() {
        val next = (nextRace.value as? SectionUiState.Content)?.data ?: return
        val country = next.circuit.country ?: return
        weekendSchedule.value = SectionUiState.Loading
        weekendSchedule.value = getRaceWeekendSchedule(next.year, country).toSection()
    }

    /**
     * Loads §1's circuit track-layout image from the current `nextRace`
     * atom. Skips if no next race or the country is missing. The use case
     * handles the country-divergence fallback and returns `Success(null)`
     * when OpenF1 has no image for this circuit.
     */
    private suspend fun loadCircuitImage() {
        val next = (nextRace.value as? SectionUiState.Content)?.data ?: return
        val country = next.circuit.country ?: return
        circuitImage.value = SectionUiState.Loading
        circuitImage.value = getCircuitImage(next.year, country).toSection()
    }

    /**
     * First-launch default seed. Reads the current `favorites.value` +
     * `constructors.value` + `drivers.value` and writes the top
     * constructor + its two drivers into the cache if the cache is
     * empty. No-op when the cache is already populated (the user
     * picked something, or the seed already ran). The DataStore write
     * is partial-fill safe — slots the user has already filled are
     * preserved.
     *
     * The `favoritesFlow.first()` is needed so the check sees the
     * current cache state rather than racing against the
     * `favoritesFlow.onEach` collector.
     */
    private suspend fun seedIfCacheEmpty() {
        val favs = favoritesFlow.first()
        if (!favs.isEmpty()) return
        val con = (constructors.value as? SectionUiState.Content)?.data ?: return
        val drv = (drivers.value as? SectionUiState.Content)?.data ?: return
        val topTeamId = con.firstOrNull()?.teamId ?: return
        val topDriverIds = drv.filter { it.teamId == topTeamId }.take(2).map { it.driverId }
        if (topDriverIds.size == 2) {
            seedIfEmpty(topTeamId, topDriverIds)
        }
    }
}

/**
 * `viewModelFactory` builder. The factory captures everything from
 * `Wiring`; the VM takes function references (use cases), a `Flow`, and
 * the cache's `seedIfEmpty` function ref — keeping the `Wiring` types
 * off the public VM API.
 */
fun homepageViewModelFactory(
    getSeason: GetSeasonUseCase,
    getNextRace: GetNextRaceUseCase,
    getRaceWeekendSchedule: GetRaceWeekendScheduleUseCase,
    getDriversStandings: GetDriversStandingsUseCase,
    getConstructorsStandings: GetConstructorsStandingsUseCase,
    getCircuitTopSpeed: GetCircuitTopSpeedUseCase,
    getCircuitImage: GetCircuitImageUseCase,
    favoritesCache: FavoritesCache,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomepageViewModel(
            getSeason = getSeason::invoke,
            getNextRace = getNextRace::invoke,
            getRaceWeekendSchedule = getRaceWeekendSchedule::invoke,
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
            getCircuitTopSpeed = getCircuitTopSpeed::invoke,
            getCircuitImage = getCircuitImage::invoke,
            favoritesFlow = favoritesCache.read(),
            seedIfEmpty = favoritesCache::seedIfEmpty,
        )
    }
}
