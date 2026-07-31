package com.anpurnama.f1_app.f1.cache

import com.anpurnama.f1_app.core.cache.CacheResourceKey
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshFailureClassifier
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.core.cache.failureMessageOrNull
import com.anpurnama.f1_app.f1.data.CircuitDetailResponseDto
import com.anpurnama.f1_app.f1.data.CircuitWinnersResponseDto
import com.anpurnama.f1_app.f1.data.WikipediaSummary
import com.anpurnama.f1_app.f1.data.getCircuit
import com.anpurnama.f1_app.f1.data.getCircuitWinners
import com.anpurnama.f1_app.f1.data.getWikipediaSummary
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import com.anpurnama.f1_app.f1.toCircuitDetail
import com.anpurnama.f1_app.f1.toCircuitMostWins
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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

/**
 * Cache repository for non-season resources: circuit metadata, circuit
 * most-wins, and Wikipedia summaries. These resources have `season = null` in
 * their cache keys, so they survive season promotion/pruning in the snapshot
 * store (SnapshotStore.promoteActiveSeason retains snapshots whose
 * `season == null`).
 *
 * Each resource is identified by a stable key (circuitId, page title) rather
 * than by active-season, so this repository does not depend on the active
 * season from the store.
 */
class NonSeasonResourcesCacheRepository(
    private val store: SnapshotStore,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val staleAfterMs: Long = TwentyFourHoursMs,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, RefreshFlight>()

    // ── Circuit metadata ───────────────────────────────────────────────

    fun observeCircuitMetadata(circuitId: String): Flow<CachedResource<CircuitDetail>?> =
        store.state
            .map { state ->
                val key = CacheResourceKeys.circuitMetadata(circuitId)
                val snapshot = state.snapshots[key.value] ?: return@map null
                snapshot.toCircuitDetailOrNull(key)
            }
            .distinctUntilChanged()

    suspend fun refreshCircuitMetadata(
        f1apiCircuitId: String,
        reason: RefreshReason,
    ): RefreshResult {
        val key = CacheResourceKeys.circuitMetadata(f1apiCircuitId)
        return refresh(key, reason, { forceRefresh ->
            client.getCircuit(f1apiCircuitId, forceRefresh)
        }) { attemptedAt, dto ->
            if (dto.circuit.isEmpty()) {
                RefreshResult.PermanentFailure("Circuit $f1apiCircuitId not found")
            } else {
                store.writeSnapshot(buildSnapshot(key, attemptedAt,
                    CircuitDetailResponseDto.serializer(), dto))
                RefreshResult.Refreshed
            }
        }
    }

    // ── Circuit most-wins ──────────────────────────────────────────────

    fun observeCircuitMostWins(circuitId: String): Flow<CachedResource<CircuitMostWins>?> =
        observeGlobal(CacheResourceKeys.circuitMostWins(circuitId),
            CircuitWinnersResponseDto.serializer(),
            CircuitWinnersResponseDto::toCircuitMostWins)

    suspend fun refreshCircuitMostWins(
        f1apiCircuitId: String,
        reason: RefreshReason,
    ): RefreshResult {
        val key = CacheResourceKeys.circuitMostWins(f1apiCircuitId)
        return refresh(key, reason, { forceRefresh ->
            client.getCircuitWinners(f1apiCircuitId, forceRefresh)
        }) { attemptedAt, dto ->
            store.writeSnapshot(buildSnapshot(key, attemptedAt,
                CircuitWinnersResponseDto.serializer(), dto))
            RefreshResult.Refreshed
        }
    }

    // ── Wikipedia summaries ────────────────────────────────────────────

    fun observeWikipediaSummary(pageTitle: String): Flow<CachedResource<WikipediaSummary>?> =
        observeGlobal(CacheResourceKeys.wikipediaSummary(pageTitle),
            WikipediaSummary.serializer(),
            { summary -> summary })

    suspend fun refreshWikipediaSummary(
        pageTitle: String,
        reason: RefreshReason,
    ): RefreshResult {
        val key = CacheResourceKeys.wikipediaSummary(pageTitle)
        return refresh(key, reason, { forceRefresh ->
            client.getWikipediaSummary(pageTitle, forceRefresh)
        }) { attemptedAt, summary ->
            store.writeSnapshot(buildSnapshot(key, attemptedAt,
                WikipediaSummary.serializer(), summary))
            RefreshResult.Refreshed
        }
    }

    private suspend fun <Dto> refresh(
        key: CacheResourceKey,
        reason: RefreshReason,
        fetch: suspend (Boolean) -> Dto,
        persist: suspend (Long, Dto) -> RefreshResult,
    ): RefreshResult {
        if (reason !is RefreshReason.PullToRefresh && currentSnapshot(key)?.isStale(
                clock.now().toEpochMilliseconds()
            ) == false
        ) {
            return RefreshResult.SkippedFresh
        }
        return singleFlightRefresh(key.value, reason is RefreshReason.PullToRefresh) { forceRefresh ->
            val attemptedAt = clock.now().toEpochMilliseconds()
            try {
                val result = persist(
                    attemptedAt,
                    fetch(forceRefresh),
                )
                if (result.failureMessageOrNull != null) {
                    fail(key, attemptedAt, result)
                } else {
                    result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                fail(key, attemptedAt, RefreshFailureClassifier.classify(throwable))
            }
        }
    }

    // ── Generic observer pattern (non-season, no active-season dependency) ────

    private fun <Dto, Domain> observeGlobal(
        key: CacheResourceKey,
        serializer: KSerializer<Dto>,
        mapper: (Dto) -> Domain,
    ): Flow<CachedResource<Domain>?> = store.state
        .map { state ->
            state.snapshots[key.value]?.toCachedResourceOrNull(key, serializer, mapper)
        }
        .distinctUntilChanged()

    // ── Helpers ────────────────────────────────────────────────────────

    private suspend fun fail(
        key: CacheResourceKey,
        attemptedAt: Long,
        result: RefreshResult,
    ): RefreshResult {
        val message = requireNotNull(result.failureMessageOrNull)
        return try {
            store.recordAttempt(key, attemptedAt, RefreshAttemptStatus.Failed(message))
            result
        } catch (throwable: Throwable) {
            RefreshFailureClassifier.classify(throwable)
        }
    }

    private suspend fun currentSnapshot(key: CacheResourceKey): CachedResource<Unit>? =
        store.state.first().snapshots[key.value]?.let { CachedResource(Unit, it) }

    /** Atomic lookup-or-create for the given [keyValue]. */
    private suspend fun singleFlightRefresh(
        keyValue: String,
        forceRefresh: Boolean,
        block: suspend (Boolean) -> RefreshResult,
    ): RefreshResult {
        val flight = mutex.withLock {
            inFlight[keyValue]?.takeIf { it.deferred.isActive }?.also { existing ->
                if (forceRefresh && !existing.forceRefresh) {
                    existing.forceFollowUpRequested = true
                }
            } ?: startFlightLocked(keyValue, forceRefresh, block)
        }

        return flight.deferred.await()
    }

    /** Creates and publishes a flight while [mutex] is held. */
    private fun startFlightLocked(
        keyValue: String,
        forceRefresh: Boolean,
        block: suspend (Boolean) -> RefreshResult,
    ): RefreshFlight {
        val flight = RefreshFlight(forceRefresh)
        flight.deferred = scope.async(start = CoroutineStart.LAZY) {
            runFlight(keyValue, flight, block)
        }
        inFlight[keyValue] = flight
        flight.deferred.start()
        return flight
    }

    private suspend fun runFlight(
        keyValue: String,
        flight: RefreshFlight,
        block: suspend (Boolean) -> RefreshResult,
    ): RefreshResult = try {
        val result = block(flight.forceRefresh)
        val followUp = mutex.withLock {
            if (inFlight[keyValue] !== flight) {
                null
            } else if (!flight.forceRefresh && flight.forceFollowUpRequested) {
                startFlightLocked(keyValue, forceRefresh = true, block)
            } else {
                inFlight.remove(keyValue)
                null
            }
        }
        followUp?.deferred?.await() ?: result
    } finally {
        // Cleanup belongs to the producer so a cancelled waiter cannot clear
        // a still-running shared request or its forced successor.
        mutex.withLock {
            if (inFlight[keyValue] === flight) inFlight.remove(keyValue)
        }
    }

    private class RefreshFlight(
        val forceRefresh: Boolean,
    ) {
        var forceFollowUpRequested: Boolean = false
        lateinit var deferred: Deferred<RefreshResult>
    }

    private fun <Dto> buildSnapshot(
        key: CacheResourceKey,
        now: Long,
        serializer: KSerializer<Dto>,
        dto: Dto,
    ): ResourceSnapshot = ResourceSnapshot(
        key = key.value,
        season = key.season, // null for non-season resources
        payloadKind = key.payloadKind,
        payloadVersion = PayloadVersion,
        payloadJson = json.encodeToString(serializer, dto),
        fetchedAtEpochMs = now,
        staleAfterEpochMs = now + staleAfterMs,
        lastAttemptEpochMs = now,
        lastAttemptStatus = RefreshAttemptStatus.Succeeded,
    )

    private fun ResourceSnapshot.toCircuitDetailOrNull(
        expectedKey: CacheResourceKey,
    ): CachedResource<CircuitDetail>? = try {
        if (key != expectedKey.value || payloadKind != expectedKey.payloadKind) return null
        val dto = json.decodeFromString(CircuitDetailResponseDto.serializer(), payloadJson)
        val detail = dto.toCircuitDetail() ?: return null
        CachedResource(detail, this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun <Dto, Domain> ResourceSnapshot.toCachedResourceOrNull(
        expectedKey: CacheResourceKey,
        serializer: KSerializer<Dto>,
        mapper: (Dto) -> Domain,
    ): CachedResource<Domain>? = try {
        if (key != expectedKey.value || payloadKind != expectedKey.payloadKind) return null
        CachedResource(mapper(json.decodeFromString(serializer, payloadJson)), this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val PayloadVersion = 1
        private const val TwentyFourHoursMs = 24L * 60L * 60L * 1000L
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
