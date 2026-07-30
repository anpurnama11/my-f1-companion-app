package com.anpurnama.f1_app.f1.cache

import com.anpurnama.f1_app.core.cache.BundleRefreshResult
import com.anpurnama.f1_app.core.cache.CacheResourceKey
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshFailureClassifier
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.core.cache.failureMessageOrNull
import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.CurrentTeamsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaConstructorStandingsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaDriverStandingsResponseDto
import com.anpurnama.f1_app.f1.data.NextRaceResponseDto
import com.anpurnama.f1_app.f1.data.getDrivers
import com.anpurnama.f1_app.f1.data.getJolpicaConstructorStandings
import com.anpurnama.f1_app.f1.data.getJolpicaDriverStandings
import com.anpurnama.f1_app.f1.data.getNextRace
import com.anpurnama.f1_app.f1.data.getTeams
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.toConstructorStandings
import com.anpurnama.f1_app.f1.toDriverStandings
import com.anpurnama.f1_app.f1.toNextRace
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class CurrentSeasonResourcesCacheRepository(
    private val store: SnapshotStore,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val staleAfterMs: Long = TwelveHoursMs,
    private val refreshScheduleIfMissing: (suspend (RefreshReason) -> RefreshResult)? = null,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<RefreshResult>>()

    fun observeNextRace(): Flow<CachedResource<NextRace?>?> = observeActiveSeason(
        keyForSeason = CacheResourceKeys::nextRaceSession,
        serializer = NextRaceResponseDto.serializer(),
        mapper = { it.toNextRace() },
    )

    fun observeDriverStandings(): Flow<CachedResource<List<DriverStanding>>?> = observeActiveSeason(
        keyForSeason = CacheResourceKeys::driverStandings,
        serializer = JolpicaDriverStandingsResponseDto.serializer(),
        mapper = { it.toDriverStandings() },
    )

    fun observeConstructorStandings(): Flow<CachedResource<List<ConstructorStanding>>?> = observeActiveSeason(
        keyForSeason = CacheResourceKeys::constructorStandings,
        serializer = JolpicaConstructorStandingsResponseDto.serializer(),
        mapper = { it.toConstructorStandings() },
    )

    suspend fun refreshNextRace(reason: RefreshReason): RefreshResult = refreshActiveSeason(
        reason = reason,
        keyForSeason = CacheResourceKeys::nextRaceSession,
        serializer = NextRaceResponseDto.serializer(),
        fetch = { forceRefresh, _ -> client.getNextRace(forceRefresh) },
        validate = { dto, activeSeason -> dto.season == activeSeason },
    )

    suspend fun refreshDriverStandings(reason: RefreshReason): RefreshResult = refreshActiveSeason(
        reason = reason,
        keyForSeason = CacheResourceKeys::driverStandings,
        serializer = JolpicaDriverStandingsResponseDto.serializer(),
        fetch = { forceRefresh, season -> client.getJolpicaDriverStandings(season, forceRefresh) },
        validate = { _, _ -> true },
    )

    suspend fun refreshConstructorStandings(reason: RefreshReason): RefreshResult = refreshActiveSeason(
        reason = reason,
        keyForSeason = CacheResourceKeys::constructorStandings,
        serializer = JolpicaConstructorStandingsResponseDto.serializer(),
        fetch = { forceRefresh, season -> client.getJolpicaConstructorStandings(season, forceRefresh) },
        validate = { _, _ -> true },
    )

    suspend fun refreshDriverCatalog(reason: RefreshReason): RefreshResult = refreshActiveSeason(
        reason = reason,
        keyForSeason = CacheResourceKeys::driverCatalog,
        serializer = CurrentDriversResponseDto.serializer(),
        fetch = { forceRefresh, season -> client.getDrivers(season, forceRefresh) },
        validate = { dto, activeSeason -> dto.season == activeSeason || (dto.season == 0 && dto.drivers.isEmpty()) },
    )

    suspend fun refreshTeamCatalog(reason: RefreshReason): RefreshResult = refreshActiveSeason(
        reason = reason,
        keyForSeason = CacheResourceKeys::constructorCatalog,
        serializer = CurrentTeamsResponseDto.serializer(),
        fetch = { forceRefresh, season -> client.getTeams(season, forceRefresh) },
        validate = { dto, activeSeason -> dto.season == activeSeason || (dto.season == 0 && dto.teams.isEmpty()) },
    )

    /**
     * Best-effort bundle refresh of every current-season resource this
     * repository owns (next race, driver + constructor standings, driver +
     * team catalogs). Each per-resource refresh uses the same
     * single-flight gate as a foreground call, so overlapping
     * foreground + worker refreshes coalesce. Per-resource failures are
     * caught and recorded in the returned [BundleRefreshResult] — one
     * bad endpoint never aborts the rest of the bundle.
     *
     * Used by `CacheSyncWorker` on its 12-hour tick. Foreground screens
     * keep using the per-resource [refreshNextRace] / [refreshDriverStandings]
     * / etc. methods directly so the bundle never blocks a screen open.
     */
    suspend fun refreshCurrentSeasonBundle(): BundleRefreshResult {
        // Resolve the active season up front. No active season is a
        // legitimate off-season / pre-promotion state, not a
        // failure — return an empty bundle so the worker treats it
        // as "nothing to attempt this tick" (success) rather than
        // triggering exponential-backoff retry.
        val activeSeason = activeSeason() ?: return BundleRefreshResult.Empty
        val resources: List<Pair<String, suspend (RefreshReason) -> RefreshResult>> = listOf(
            CacheResourceKeys.nextRaceSession(activeSeason).value to ::refreshNextRace,
            CacheResourceKeys.driverStandings(activeSeason).value to ::refreshDriverStandings,
            CacheResourceKeys.constructorStandings(activeSeason).value to ::refreshConstructorStandings,
            CacheResourceKeys.driverCatalog(activeSeason).value to ::refreshDriverCatalog,
            CacheResourceKeys.constructorCatalog(activeSeason).value to ::refreshTeamCatalog,
        )
        val entries = resources.map { (key, refresh) ->
            val result = runCatching { refresh(RefreshReason.Periodic) }
                .getOrElse(RefreshFailureClassifier::classify)
            BundleRefreshResult.Entry(key = key, result = result)
        }
        return BundleRefreshResult(entries)
    }

    private fun <Dto, Domain> observeActiveSeason(
        keyForSeason: (Int) -> CacheResourceKey,
        serializer: KSerializer<Dto>,
        mapper: (Dto) -> Domain,
    ): Flow<CachedResource<Domain>?> = store.state
        .map { state ->
            val season = state.activeSeason ?: return@map null
            val key = keyForSeason(season)
            state.snapshots[key.value]?.toCachedResourceOrNull(key, serializer, mapper)
        }
        .distinctUntilChanged()

    private suspend fun <Dto> refreshActiveSeason(
        reason: RefreshReason,
        keyForSeason: (Int) -> CacheResourceKey,
        serializer: KSerializer<Dto>,
        fetch: suspend (forceRefresh: Boolean, activeSeason: Int) -> Dto,
        validate: (Dto, activeSeason: Int) -> Boolean,
    ): RefreshResult {
        var activeSeason = activeSeason()
        if (activeSeason == null) {
            val discovery = refreshScheduleIfMissing?.invoke(RefreshReason.StaleOpen)
            activeSeason = activeSeason()
            if (activeSeason == null && discovery?.failureMessageOrNull != null) return discovery
        }
        val resolvedSeason = activeSeason ?: return RefreshResult.PermanentFailure("No active season cache")
        val key = keyForSeason(resolvedSeason)
        if (reason !is RefreshReason.PullToRefresh && currentSnapshot(key)?.isStale(clock.now().toEpochMilliseconds()) == false) {
            return RefreshResult.SkippedFresh
        }
        val existing = mutex.withLock {
            inFlight[key.value]?.takeIf { it.isActive } ?: scope.async {
                runRefresh(resolvedSeason, key, serializer, reason, fetch, validate)
            }.also { deferred ->
                inFlight[key.value] = deferred
            }
        }
        return try {
            existing.await()
        } finally {
            mutex.withLock {
                if (inFlight[key.value] === existing) inFlight.remove(key.value)
            }
        }
    }

    private suspend fun <Dto> runRefresh(
        activeSeason: Int,
        key: CacheResourceKey,
        serializer: KSerializer<Dto>,
        reason: RefreshReason,
        fetch: suspend (forceRefresh: Boolean, activeSeason: Int) -> Dto,
        validate: (Dto, activeSeason: Int) -> Boolean,
    ): RefreshResult {
        val attemptedAt = clock.now().toEpochMilliseconds()
        return try {
            val dto = fetch(reason is RefreshReason.PullToRefresh, activeSeason)
            if (!validate(dto, activeSeason)) {
                return fail(
                    key,
                    attemptedAt,
                    RefreshResult.RetryableFailure("Invalid ${key.payloadKind} payload"),
                )
            }
            store.writeSnapshot(
                ResourceSnapshot(
                    key = key.value,
                    season = activeSeason,
                    payloadKind = key.payloadKind,
                    payloadVersion = PayloadVersion,
                    payloadJson = json.encodeToString(serializer, dto),
                    fetchedAtEpochMs = attemptedAt,
                    staleAfterEpochMs = attemptedAt + staleAfterMs,
                    lastAttemptEpochMs = attemptedAt,
                    lastAttemptStatus = RefreshAttemptStatus.Succeeded,
                )
            )
            RefreshResult.Refreshed
        } catch (throwable: Throwable) {
            fail(key, attemptedAt, RefreshFailureClassifier.classify(throwable))
        }
    }

    private suspend fun fail(
        key: CacheResourceKey,
        attemptedAt: Long,
        result: RefreshResult,
    ): RefreshResult {
        val message = requireNotNull(result.failureMessageOrNull)
        return try {
            recordFailure(key, attemptedAt, message)
            result
        } catch (throwable: Throwable) {
            RefreshFailureClassifier.classify(throwable)
        }
    }

    private suspend fun recordFailure(key: CacheResourceKey, attemptedAt: Long, message: String) {
        store.recordAttempt(key, attemptedAt, RefreshAttemptStatus.Failed(message))
    }

    private suspend fun activeSeason(): Int? = store.state.first().activeSeason

    private suspend fun currentSnapshot(key: CacheResourceKey): CachedResource<Unit>? =
        store.state.first().snapshots[key.value]?.let { CachedResource(Unit, it) }

    private fun <Dto, Domain> ResourceSnapshot.toCachedResourceOrNull(
        expectedKey: CacheResourceKey,
        serializer: KSerializer<Dto>,
        mapper: (Dto) -> Domain,
    ): CachedResource<Domain>? = try {
        if (key != expectedKey.value || payloadKind != expectedKey.payloadKind || season != expectedKey.season) return null
        CachedResource(mapper(json.decodeFromString(serializer, payloadJson)), this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val PayloadVersion = 1
        private const val TwelveHoursMs = 12L * 60L * 60L * 1000L
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
