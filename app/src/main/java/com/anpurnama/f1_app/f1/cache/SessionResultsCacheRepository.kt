package com.anpurnama.f1_app.f1.cache

import com.anpurnama.f1_app.core.cache.BundleRefreshResult
import com.anpurnama.f1_app.core.cache.CacheResourceKey
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.f1.CarNumberTranslator
import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaAlphaResultsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaPitStopsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaQualifyingResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaRaceResultsResponseDto
import com.anpurnama.f1_app.f1.data.SeasonResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaResults
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaRoundId
import com.anpurnama.f1_app.f1.data.getJolpicaPitStops
import com.anpurnama.f1_app.f1.data.getJolpicaQualifying
import com.anpurnama.f1_app.f1.data.getJolpicaRaceResults
import com.anpurnama.f1_app.f1.model.FastestPitstop
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import com.anpurnama.f1_app.f1.model.toInstantOrNull
import com.anpurnama.f1_app.f1.toRoundQualifying
import com.anpurnama.f1_app.f1.toRoundResults
import com.anpurnama.f1_app.f1.toSeason
import com.anpurnama.f1_app.f1.toSessionResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
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
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Per-resource cache repository for session results (Race, Qualifying,
 * Sprint, Sprint Qualifying, FP1/FP2/FP3) and race-session enrichments
 * (fastest pit-stop). Each resource is keyed by (season, round, session)
 * or (season, round) for pitstops, with the active season read from
 * the snapshot store.
 *
 * Session result refreshes are gated by a "plausibly complete" check:
 * the repository looks up the scheduled start time for the round's
 * session from the cached schedule snapshot, and only makes a network
 * call if the session should have finished (start + per-session buffer).
 * Sessions in the future skip the network call and return
 * [RefreshResult.Success] without overwriting any existing cached
 * content. This avoids caching empty or partial API responses for
 * sessions that have not yet run.
 *
 * Enrichment (pitstops) is not gated — a race session may complete
 * before pitstop data is published, and the existing cached content
 * should remain visible until a valid replacement arrives.
 */
class SessionResultsCacheRepository(
    private val store: SnapshotStore,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val staleAfterMs: Long = TwelveHoursMs,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<RefreshResult>>()

    // ── Observers ──────────────────────────────────────────────────────

    /** Observe cached [SessionResult] for a specific (season, round, session). */
    fun observeSessionResult(
        season: Int,
        round: Int,
        session: SessionType,
    ): Flow<CachedResource<SessionResult>?> = store.state
        .map { state ->
            val key = CacheResourceKeys.sessionResults(season, round, session)
            state.snapshots[key.value]?.toSessionResultOrNull(
                expectedKey = key,
                session = session,
                season = season,
                round = round,
                translator = state.snapshots.alphaTranslatorForSeason(season),
            )
        }
        .distinctUntilChanged()

    /** Observe cached [FastestPitstop] for a specific (season, round). */
    fun observePitstops(
        season: Int,
        round: Int,
    ): Flow<CachedResource<FastestPitstop?>?> = store.state
        .map { state ->
            val key = CacheResourceKeys.pitstops(season, round)
            state.snapshots[key.value]?.toPitstopOrNull(key)
        }
        .distinctUntilChanged()

    // ── Refresh: session results ───────────────────────────────────────

    suspend fun refreshSessionResult(
        season: Int,
        round: Int,
        session: SessionType,
        reason: RefreshReason,
    ): RefreshResult {
        val key = CacheResourceKeys.sessionResults(season, round, session)
        // Restrict to the active season — session results are current-season only.
        // When no active season is set (e.g. before first schedule load), also reject
        // the write so the ViewModel's explicit fallback handles the direct request.
        val active = store.state.first().activeSeason
        if (active == null || season != active) {
            return RefreshResult.Failure(
                if (active == null) "No active season" else "Not the active season"
            )
        }
        if (reason !is RefreshReason.PullToRefresh && currentSnapshot(key)?.isStale(
                clock.now().toEpochMilliseconds()
            ) == false
        ) {
            return RefreshResult.Success
        }
        // Session plausibly-complete gate: skip network for future sessions.
        if (!isSessionPlausiblyComplete(season, round, session)) {
            // If we already have cached content, keep it. If not, report the
            // gated state to callers so they do not bypass the completion gate
            // through a direct fallback network path.
            val existing = currentSnapshot(key)
            if (existing != null) {
                return RefreshResult.Success
            }
            // No cached content and session not yet complete: record the
            // attempt and surface a non-network failure/empty state.
            val message = "Session not yet complete"
            store.recordAttempt(key, clock.now().toEpochMilliseconds(),
                RefreshAttemptStatus.Failed(message))
            return RefreshResult.Failure(message)
        }
        return singleFlightRefresh(key.value) {
            runSessionResultRefresh(season, round, session, key, reason)
        }
    }

    // ── Refresh: pitstops ──────────────────────────────────────────────

    suspend fun refreshPitstops(
        season: Int,
        round: Int,
        reason: RefreshReason,
    ): RefreshResult {
        val key = CacheResourceKeys.pitstops(season, round)
        // Restrict to the active season — pitstops are current-season only.
        // When no active season is set (e.g. before first schedule load), also reject
        // the write so the ViewModel's explicit fallback handles the direct request.
        val active = store.state.first().activeSeason
        if (active == null || season != active) {
            return RefreshResult.Failure(
                if (active == null) "No active season" else "Not the active season"
            )
        }
        if (reason !is RefreshReason.PullToRefresh && currentSnapshot(key)?.isStale(
                clock.now().toEpochMilliseconds()
            ) == false
        ) {
            return RefreshResult.Success
        }
        return singleFlightRefresh(key.value) {
            runPitstopRefresh(season, round, key, reason)
        }
    }

    // ── Bundle refresh (periodic worker) ──────────────────────────────

    /**
     * Best-effort current-season bundle refresh for session results and
     * race enrichments. Walks the cached schedule (read from the
     * snapshot store) and refreshes only the sessions whose scheduled
     * start falls within [BundleWindowBefore] / [BundleWindowAfter] of
     * [now] AND that the plausibly-complete gate considers complete.
     *
     * Pitstops are refreshed for completed Race sessions in the same
     * window; they are enrichment, not result, but a per-round key fits
     * the same bundle.
     *
     * Per-resource failures are caught and recorded in the returned
     * [BundleRefreshResult] — the gate's "Session not yet complete" is
     * a normal `RefreshResult.Failure` and is **not** an exception, so
     * a future session surfaces as a `failed` entry, not a missing one.
     *
     * Used by `CacheSyncWorker` on its 12-hour tick. The per-resource
     * single-flight gate still applies, so an overlapping foreground
     * refresh joins the worker's in-flight call rather than starting a
     * duplicate.
     */
    suspend fun refreshCurrentSeasonBundle(now: Instant): BundleRefreshResult {
        // No active season OR no cached schedule is a legitimate
        // off-season / pre-promotion state, not a failure — return
        // an empty bundle so the worker treats it as "nothing to
        // attempt this tick" (success) rather than triggering
        // exponential-backoff retry. The next 12h tick re-evaluates.
        val activeSeason = store.state.first().activeSeason ?: return BundleRefreshResult.Empty
        val schedule = readCachedSchedule(activeSeason) ?: return BundleRefreshResult.Empty
        val candidates = eligibleBundleCandidates(now, schedule)
        if (candidates.isEmpty()) {
            return BundleRefreshResult.Empty
        }
        val entries = candidates.map { candidate ->
            val key = CacheResourceKeys.sessionResults(activeSeason, candidate.round, candidate.session).value
            val result = runCatching {
                refreshSessionResult(
                    season = activeSeason,
                    round = candidate.round,
                    session = candidate.session,
                    reason = RefreshReason.Periodic,
                )
            }.getOrElse { e -> RefreshResult.Failure(e.message ?: "Bundle refresh error") }
            BundleRefreshResult.Entry(key = key, result = result)
        } + candidates.filter { it.session == SessionType.Race }.map { candidate ->
            val key = CacheResourceKeys.pitstops(activeSeason, candidate.round).value
            val result = runCatching {
                refreshPitstops(
                    season = activeSeason,
                    round = candidate.round,
                    reason = RefreshReason.Periodic,
                )
            }.getOrElse { e -> RefreshResult.Failure(e.message ?: "Bundle refresh error") }
            BundleRefreshResult.Entry(key = key, result = result)
        }
        return BundleRefreshResult(entries)
    }

    /**
     * Read the cached schedule snapshot and map it to the domain
     * [Season]. Returns `null` if the snapshot is missing or the
     * payload fails to deserialize — the caller should treat that as
     * "no schedule, no eligible sessions" rather than aborting the
     * bundle.
     */
    private suspend fun readCachedSchedule(season: Int): Season? {
        val snapshot = store.state.first()
            .snapshots[CacheResourceKeys.currentSeasonSchedule(season).value]
            ?: return null
        return try {
            json.decodeFromString(SeasonResponseDto.serializer(), snapshot.payloadJson).toSeason()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Filter the cached season's [ScheduledSession] list to the
     * sessions eligible for a periodic bundle refresh. The window
     * bounds (now ± [BundleWindowBefore] / [BundleWindowAfter]) are a
     * coarse discovery hint; the per-session plausibly-complete gate
     * is the authoritative filter, so a future session inside
     * +48h is still excluded.
     */
    private fun eligibleBundleCandidates(
        now: Instant,
        season: Season,
    ): List<BundleCandidate> {
        val nowMs = now.toEpochMilliseconds()
        val beforeMs = nowMs - BundleWindowBefore.inWholeMilliseconds
        val afterMs = nowMs + BundleWindowAfter.inWholeMilliseconds
        return season.races
            .flatMap { race ->
                race.schedule?.activeSessions().orEmpty().mapNotNull { session ->
                    val sessionMs = session.slot.toInstantOrNull()?.toEpochMilliseconds()
                        ?: return@mapNotNull null
                    if (sessionMs < beforeMs || sessionMs > afterMs) return@mapNotNull null
                    BundleCandidate(round = race.round, session = session.type, sessionMs = sessionMs)
                }
            }
            .filter { candidate ->
                // Authoritative filter: the repository's existing
                // plausibly-complete gate (start + per-session buffer).
                // Inline the buffer math so the bundle does not need to
                // re-walk the schedule once per session through the
                // store; the gate value matches [completionBufferMs].
                nowMs >= candidate.sessionMs + candidate.session.completionBufferMs()
            }
    }

    private data class BundleCandidate(
        val round: Int,
        val session: SessionType,
        val sessionMs: Long,
    )

    // ── Internal: session result refresh ──────────────────────────────

    private suspend fun runSessionResultRefresh(
        season: Int,
        round: Int,
        session: SessionType,
        key: CacheResourceKey,
        reason: RefreshReason,
    ): RefreshResult {
        val attemptedAt = clock.now().toEpochMilliseconds()
        return try {
            when (session) {
                SessionType.Race -> {
                    val dto = client.getJolpicaRaceResults(season, round,
                        forceRefresh = reason is RefreshReason.PullToRefresh)
                    store.writeSnapshot(buildSnapshot(key, attemptedAt,
                        JolpicaRaceResultsResponseDto.serializer(), dto))
                    RefreshResult.Success
                }
                SessionType.Quali -> {
                    val dto = client.getJolpicaQualifying(season, round,
                        forceRefresh = reason is RefreshReason.PullToRefresh)
                    store.writeSnapshot(buildSnapshot(key, attemptedAt,
                        JolpicaQualifyingResponseDto.serializer(), dto))
                    RefreshResult.Success
                }
                SessionType.FP1, SessionType.FP2, SessionType.FP3,
                SessionType.Sprint, SessionType.SprintQuali -> {
                    val roundId = client.getJolpicaAlphaRoundId(season, round,
                        forceRefresh = reason is RefreshReason.PullToRefresh)
                        ?: return fail(key, attemptedAt, "Session is unavailable")
                    val filter = session.toAlphaFilter()
                    val dto = client.getJolpicaAlphaResults(roundId, filter,
                        forceRefresh = reason is RefreshReason.PullToRefresh)
                    store.writeSnapshot(buildSnapshot(key, attemptedAt,
                        JolpicaAlphaResultsResponseDto.serializer(), dto))
                    RefreshResult.Success
                }
            }
        } catch (e: IllegalArgumentException) {
            // Alpha filter guard throws for unsupported filters.
            if (e.message == "Invalid session filter") {
                fail(key, attemptedAt, "Session is unavailable")
            } else {
                fail(key, attemptedAt, e.message ?: "Deserialization error")
            }
        } catch (e: ClientRequestException) {
            fail(key, attemptedAt, "Request failed (${e.response.status.value})")
        } catch (e: ServerResponseException) {
            fail(key, attemptedAt, "Server error (${e.response.status.value})")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(key, attemptedAt, e.message ?: "Network error")
        }
    }

    // ── Internal: pitstop refresh ──────────────────────────────────────

    private suspend fun runPitstopRefresh(
        season: Int,
        round: Int,
        key: CacheResourceKey,
        reason: RefreshReason,
    ): RefreshResult {
        val attemptedAt = clock.now().toEpochMilliseconds()
        return try {
            val dto = client.getJolpicaPitStops(season, round,
                forceRefresh = reason is RefreshReason.PullToRefresh)
            store.writeSnapshot(buildSnapshot(key, attemptedAt,
                JolpicaPitStopsResponseDto.serializer(), dto))
            RefreshResult.Success
        } catch (e: ClientRequestException) {
            fail(key, attemptedAt, "Request failed (${e.response.status.value})")
        } catch (e: ServerResponseException) {
            fail(key, attemptedAt, "Server error (${e.response.status.value})")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(key, attemptedAt, e.message ?: "Network error")
        }
    }

    // ── Plausibly-complete gate ────────────────────────────────────────

    /**
     * Checks whether a session is plausibly complete, based on the cached
     * schedule. Returns `true` if:
     *   - No schedule snapshot exists (conservatively allow the fetch —
     *     the API may return a sensible response).
     *   - The schedule does not contain the requested round (allow fetch).
     *   - The session's slot is not defined (allow fetch).
     *   - The session's start time + per-session buffer has passed.
     *
     * Returns `false` when the session's start + buffer is still in the
     * future — no network call should be made.
     *
     * Buffer defaults (hours): Race 4h, Qualifying 2h, Sprint 2h,
     * Sprint Qualifying 1.5h, FP sessions 1.5h.
     */
    private suspend fun isSessionPlausiblyComplete(
        season: Int,
        round: Int,
        session: SessionType,
    ): Boolean {
        val state = store.state.first()
        val scheduleKey = CacheResourceKeys.currentSeasonSchedule(season).value
        val scheduleSnapshot = state.snapshots[scheduleKey] ?: return true
        val dto = try {
            json.decodeFromString(SeasonResponseDto.serializer(), scheduleSnapshot.payloadJson)
        } catch (_: SerializationException) {
            return true
        }
        val race = dto.races.firstOrNull { it.round == round } ?: return true
        val slot = when (session) {
            SessionType.FP1 -> race.schedule.fp1
            SessionType.FP2 -> race.schedule.fp2
            SessionType.FP3 -> race.schedule.fp3
            SessionType.SprintQuali -> race.schedule.sprintQualy
            SessionType.Sprint -> race.schedule.sprintRace
            SessionType.Quali -> race.schedule.qualy
            SessionType.Race -> race.schedule.race
        } ?: return true
        val sessionStart = slot.toInstantEpochMs() ?: return true
        val bufferMs = session.completionBufferMs()
        return clock.now().toEpochMilliseconds() >= sessionStart + bufferMs
    }

    // ── Snapshot helpers ───────────────────────────────────────────────

    private suspend fun fail(
        key: CacheResourceKey,
        attemptedAt: Long,
        message: String,
    ): RefreshResult.Failure {
        store.recordAttempt(key, attemptedAt, RefreshAttemptStatus.Failed(message))
        return RefreshResult.Failure(message)
    }

    private suspend fun currentSnapshot(key: CacheResourceKey): CachedResource<Unit>? =
        store.state.first().snapshots[key.value]?.let { CachedResource(Unit, it) }

    /** Atomic lookup-or-create for the given [keyValue]. */
    private suspend fun singleFlightRefresh(
        keyValue: String,
        block: suspend () -> RefreshResult,
    ): RefreshResult {
        val deferred = mutex.withLock {
            inFlight[keyValue]?.takeIf { it.isActive }
                ?: scope.async { block() }
                    .also { inFlight[keyValue] = it }
        }
        return try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight[keyValue] === deferred) inFlight.remove(keyValue)
            }
        }
    }

    private fun <Dto> buildSnapshot(
        key: CacheResourceKey,
        now: Long,
        serializer: KSerializer<Dto>,
        dto: Dto,
    ): ResourceSnapshot = ResourceSnapshot(
        key = key.value,
        season = key.season,
        payloadKind = key.payloadKind,
        payloadVersion = PayloadVersion,
        payloadJson = json.encodeToString(serializer, dto),
        fetchedAtEpochMs = now,
        staleAfterEpochMs = now + staleAfterMs,
        lastAttemptEpochMs = now,
        lastAttemptStatus = RefreshAttemptStatus.Succeeded,
    )

    // ── Cache-read deserializers ───────────────────────────────────────

    private fun ResourceSnapshot.toSessionResultOrNull(
        expectedKey: CacheResourceKey,
        session: SessionType,
        season: Int,
        round: Int,
        translator: CarNumberTranslator,
    ): CachedResource<SessionResult>? = try {
        if (key != expectedKey.value || payloadKind != expectedKey.payloadKind) return null
        val result = when (session) {
            SessionType.Race -> {
                val dto = json.decodeFromString(JolpicaRaceResultsResponseDto.serializer(), payloadJson)
                val rr = dto.toRoundResults()
                SessionResult(
                    year = rr.year, round = rr.round, raceName = rr.raceName,
                    circuit = rr.circuit, session = session,
                    raceResults = rr.results,
                    fastestLap = rr.results.mapNotNull { r ->
                        r.fastLap?.let { time ->
                            com.anpurnama.f1_app.f1.model.FastestLap(
                                driverNumber = r.driverNumber,
                                driverName = r.driverName,
                                driverShortName = r.driverShortName,
                                time = time,
                            )
                        }
                    }.minByOrNull { it.time },
                )
            }
            SessionType.Quali -> {
                val dto = json.decodeFromString(JolpicaQualifyingResponseDto.serializer(), payloadJson)
                val rq = dto.toRoundQualifying()
                SessionResult(
                    year = rq.year, round = rq.round, raceName = rq.raceName,
                    circuit = rq.circuit, session = session,
                    qualifyingResults = rq.results,
                )
            }
            SessionType.FP1, SessionType.FP2, SessionType.FP3,
            SessionType.Sprint, SessionType.SprintQuali -> {
                val dto = json.decodeFromString(JolpicaAlphaResultsResponseDto.serializer(), payloadJson)
                dto.toSessionResult(season, round, session, translator)
            }
        }
        CachedResource(result, this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun Map<String, ResourceSnapshot>.alphaTranslatorForSeason(season: Int): CarNumberTranslator {
        val key = CacheResourceKeys.driverCatalog(season)
        val snapshot = this[key.value] ?: return CarNumberTranslator.EMPTY
        if (snapshot.payloadKind != key.payloadKind || snapshot.season != season) return CarNumberTranslator.EMPTY
        return try {
            CarNumberTranslator.from(
                json.decodeFromString(CurrentDriversResponseDto.serializer(), snapshot.payloadJson)
            )
        } catch (_: SerializationException) {
            CarNumberTranslator.EMPTY
        } catch (_: IllegalArgumentException) {
            CarNumberTranslator.EMPTY
        }
    }

    private fun ResourceSnapshot.toPitstopOrNull(
        expectedKey: CacheResourceKey,
    ): CachedResource<FastestPitstop?>? = try {
        if (key != expectedKey.value || payloadKind != expectedKey.payloadKind) return null
        val dto = json.decodeFromString(JolpicaPitStopsResponseDto.serializer(), payloadJson)
        val stops = dto.mrData.raceTable.races
            .flatMap { it.pitStops }
            .mapNotNull { stop ->
                val driverId = stop.driverId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val duration = stop.duration?.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?: return@mapNotNull null
                FastestPitstop(driverId, duration)
            }
        CachedResource(stops.minByOrNull { it.durationSeconds }, this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val PayloadVersion = 1
        private const val TwelveHoursMs = 12L * 60L * 60L * 1000L
        // Bundle window: per the wayfinder 05 decision, only consider
        // session resources whose scheduled start is within ±48h of now.
        // The plausibly-complete gate then re-filters on per-session
        // buffer; the window is a discovery hint, not the gate.
        private val BundleWindowBefore: kotlin.time.Duration = kotlin.time.Duration.parse("48h")
        private val BundleWindowAfter: kotlin.time.Duration = kotlin.time.Duration.parse("48h")
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }
    }
}

/** @return alpha session filter string for the given [SessionType]. */
internal fun SessionType.toAlphaFilter(): String = when (this) {
    SessionType.FP1 -> "FP1"
    SessionType.FP2 -> "FP2"
    SessionType.FP3 -> "FP3"
    SessionType.Sprint -> "SR"
    SessionType.SprintQuali -> "SQ"
    // Race and Quali never reach the alpha path, but provide a value for exhaustiveness.
    SessionType.Race -> error("Race uses Jolpica standard, not alpha")
    SessionType.Quali -> error("Qualifying uses Jolpica standard, not alpha")
}

/**
 * Per-session type completion buffer. Returns a conservative duration in
 * milliseconds that a session should be allowed to run beyond its scheduled
 * start time before we consider it plausible that results are available.
 */
internal fun SessionType.completionBufferMs(): Long = when (this) {
    SessionType.FP1, SessionType.FP2, SessionType.FP3 -> 90L * 60L * 1000L       // 1.5h
    SessionType.SprintQuali -> 90L * 60L * 1000L                                  // 1.5h
    SessionType.Sprint -> 120L * 60L * 1000L                                      // 2h
    SessionType.Quali -> 120L * 60L * 1000L                                       // 2h
    SessionType.Race -> 240L * 60L * 1000L                                        // 4h
}

/**
 * Parse the `date` + `time` fields from a [com.anpurnama.f1_app.f1.data.SessionDto]
 * into an epoch-millisecond [Long]. Returns `null` if either field is blank
 * or unparseable.
 */
internal fun com.anpurnama.f1_app.f1.data.SessionDto.toInstantEpochMs(): Long? {
    val d = date?.takeIf { it.isNotBlank() } ?: return null
    val t = time?.takeIf { it.isNotBlank() } ?: return null
    // SessionDto time is "HH:MM:SSZ" — zoned. f1api.dev times are UTC.
    // Concatenate as "YYYY-MM-DDTHH:MM:SSZ" for kotlinx.datetime parsing.
    return try {
        kotlinx.datetime.Instant.parse("${d}T${t}")
            .toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}
