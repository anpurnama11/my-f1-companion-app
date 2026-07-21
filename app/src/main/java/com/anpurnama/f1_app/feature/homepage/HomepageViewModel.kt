package com.anpurnama.f1_app.feature.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.feature.favorites.FavoritesCache
import com.anpurnama.f1_app.f1.GetCircuitTopSpeedUseCase
import com.anpurnama.f1_app.f1.GetConstructorsStandingsUseCase
import com.anpurnama.f1_app.f1.GetDriversStandingsUseCase
import com.anpurnama.f1_app.f1.GetNextRaceUseCase
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.TopSpeed
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
 * **Section independence:** every section has its own `Outcome` and each
 * use case runs in isolation. A 4xx/5xx on the drivers endpoint does NOT
 * blank the screen; the drivers section shows the shared `OutcomeContent`
 * failure UI and the other 5 sections stay in their state. No composite
 * "get homepage data" use case.
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
    private val getDriversStandings: suspend (forceRefresh: Boolean) -> Outcome<List<DriverStanding>>,
    private val getConstructorsStandings: suspend (forceRefresh: Boolean) -> Outcome<List<ConstructorStanding>>,
    private val getCircuitTopSpeed: suspend (
        circuitId: String,
        country: String,
        year: Int,
        qualyDate: String,
    ) -> Outcome<TopSpeed?>,
    private val favoritesFlow: Flow<Favorites>,
    private val seedIfEmpty: suspend (topTeamId: String, topDriverIds: List<String>) -> Unit,
) : ViewModel() {

    sealed interface UiState {
        /**
         * Composite of the 5 sections (6 atoms: favorites + 5 use case
         * outcomes). The screen renders each via the shared
         * `OutcomeContent` family — section independence is the contract.
         */
        data class Sections(
            val favorites: Outcome<Favorites>,
            val season: Outcome<Season>,
            val nextRace: Outcome<NextRace?>,
            val drivers: Outcome<List<DriverStanding>>,
            val constructors: Outcome<List<ConstructorStanding>>,
            val topSpeed: Outcome<TopSpeed?>,
        ) : UiState
    }

    private val favorites = MutableStateFlow<Outcome<Favorites>>(Outcome.Loading)
    private val season = MutableStateFlow<Outcome<Season>>(Outcome.Loading)
    private val nextRace = MutableStateFlow<Outcome<NextRace?>>(Outcome.Loading)
    private val drivers = MutableStateFlow<Outcome<List<DriverStanding>>>(Outcome.Loading)
    private val constructors = MutableStateFlow<Outcome<List<ConstructorStanding>>>(Outcome.Loading)
    private val topSpeed = MutableStateFlow<Outcome<TopSpeed?>>(Outcome.Loading)

    /**
     * Intermediate shape for the 5-arg combine. The 6th atom (topSpeed)
     * folds in via a typed 2-arg combine — no vararg `Array<Any?>` cast.
     * `private` so the public `UiState` surface is the only thing callers see.
     */
    private data class Sections5(
        val favorites: Outcome<Favorites>,
        val season: Outcome<Season>,
        val nextRace: Outcome<NextRace?>,
        val drivers: Outcome<List<DriverStanding>>,
        val constructors: Outcome<List<ConstructorStanding>>,
    )

    val uiState: StateFlow<UiState> = combine(
        combine(favorites, season, nextRace, drivers, constructors) { f, s, n, d, c ->
            Sections5(f, s, n, d, c)
        },
        topSpeed,
    ) { five, ts ->
        UiState.Sections(
            favorites = five.favorites,
            season = five.season,
            nextRace = five.nextRace,
            drivers = five.drivers,
            constructors = five.constructors,
            topSpeed = ts,
        )
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Loading sentinel for the first frame so the screen has
            // something to show. The first real emission arrives when
            // `warmUp()` finishes.
            initialValue = UiState.Sections(
                favorites = Outcome.Loading,
                season = Outcome.Loading,
                nextRace = Outcome.Loading,
                drivers = Outcome.Loading,
                constructors = Outcome.Loading,
                topSpeed = Outcome.Loading,
            ),
        )

    /**
     * Subscribes to the favorites cache and fires the 5 use case loads.
     * Each use case writes to its own atom; section independence is
     * enforced by the per-atom `Outcome` mapping (no composite use case).
     */
    private fun warmUp() {
        // Reactive favorites read — emits empty on first launch, populated
        // values after the seed runs.
        favoritesFlow
            .onEach { favorites.value = Outcome.Success(it) }
            .launchIn(viewModelScope)

        // One-shot first load (no forceRefresh). Each section is independent;
        // topSpeed is derived from the next-race atom so it loads AFTER
        // loadNextRace completes in this coroutine. The seed runs LAST so
        // the standings atoms are populated when the check fires; it waits
        // for the first favorites emission to read the current cache state.
        viewModelScope.launch {
            loadSeason(forceRefresh = false)
            loadNextRace(forceRefresh = false)
            loadTopSpeed(forceRefresh = false)
            loadDrivers(forceRefresh = false)
            loadConstructors(forceRefresh = false)
            seedIfCacheEmpty()
        }
    }

    /**
     * Public pull-to-refresh. Re-fires every use case with
     * `forceRefresh = true` so each adds `Cache-Control: no-cache`. The
     * top-speed cell refreshes too if a next race is known.
     */
    fun refresh() {
        viewModelScope.launch {
            loadSeason(forceRefresh = true)
            loadNextRace(forceRefresh = true)
            loadTopSpeed(forceRefresh = true)
            loadDrivers(forceRefresh = true)
            loadConstructors(forceRefresh = true)
        }
    }

    private suspend fun loadSeason(forceRefresh: Boolean) {
        season.value = Outcome.Loading
        season.value = getSeason(forceRefresh)
    }

    private suspend fun loadNextRace(forceRefresh: Boolean) {
        nextRace.value = Outcome.Loading
        nextRace.value = getNextRace(forceRefresh)
    }

    private suspend fun loadDrivers(forceRefresh: Boolean) {
        drivers.value = Outcome.Loading
        drivers.value = getDriversStandings(forceRefresh)
    }

    private suspend fun loadConstructors(forceRefresh: Boolean) {
        constructors.value = Outcome.Loading
        constructors.value = getConstructorsStandings(forceRefresh)
    }

    /**
     * Loads §3's top-speed cell from the current `nextRace` atom. Skips
     * if the next race hasn't loaded, the season is over (race = null),
     * or the inlined race is missing the country / Qualifying date the
     * OpenF1 join needs. Each `?:` early-out keeps this a simple
     * happy-path read; the use case handles the actual session_key join.
     */
    private suspend fun loadTopSpeed(forceRefresh: Boolean) {
        val next = (nextRace.value as? Outcome.Success)?.data ?: return
        val circuit = next.circuit
        val qualyDate = next.qualyDate ?: next.raceDate ?: return
        val country = circuit.country ?: return
        topSpeed.value = Outcome.Loading
        topSpeed.value = getCircuitTopSpeed(circuit.id, country, next.year, qualyDate)
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
        val con = (constructors.value as? Outcome.Success)?.data ?: return
        val drv = (drivers.value as? Outcome.Success)?.data ?: return
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
    getDriversStandings: GetDriversStandingsUseCase,
    getConstructorsStandings: GetConstructorsStandingsUseCase,
    getCircuitTopSpeed: GetCircuitTopSpeedUseCase,
    favoritesCache: FavoritesCache,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomepageViewModel(
            getSeason = getSeason::invoke,
            getNextRace = getNextRace::invoke,
            getDriversStandings = getDriversStandings::invoke,
            getConstructorsStandings = getConstructorsStandings::invoke,
            getCircuitTopSpeed = getCircuitTopSpeed::invoke,
            favoritesFlow = favoritesCache.read(),
            seedIfEmpty = favoritesCache::seedIfEmpty,
        )
    }
}
