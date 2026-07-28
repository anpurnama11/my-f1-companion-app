package com.anpurnama.f1_app.f1.cache

import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.f1.data.SeasonResponseDto
import com.anpurnama.f1_app.f1.data.getCurrent
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.toSeason
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SeasonScheduleCacheRepository(
    private val store: SnapshotStore,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val staleAfterMs: Long = TwelveHoursMs,
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<RefreshResult>? = null

    fun observeCurrentSeason(): Flow<CachedResource<Season>?> = store.state
        .map { state ->
            val activeSeason = state.activeSeason ?: return@map null
            val key = CacheResourceKeys.currentSeasonSchedule(activeSeason).value
            state.snapshots[key]?.toCachedSeasonOrNull()
        }
        .distinctUntilChanged()

    suspend fun refreshCurrentSeason(reason: RefreshReason): RefreshResult {
        if (reason is RefreshReason.StaleOpen && currentCachedSeason()?.isStale(clock.now().toEpochMilliseconds()) == false) {
            return RefreshResult.Success
        }
        val existing = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async {
                runRefresh(reason)
            }.also { deferred ->
                inFlight = deferred
            }
        }
        return try {
            existing.await()
        } finally {
            mutex.withLock {
                if (inFlight === existing) inFlight = null
            }
        }
    }

    private suspend fun runRefresh(reason: RefreshReason): RefreshResult {
        val attemptedAt = clock.now().toEpochMilliseconds()
        return try {
            val dto = client.getCurrent(forceRefresh = reason is RefreshReason.PullToRefresh)
            val season = dto.toSeason()
            if (!season.isValidCurrentSchedule()) {
                val message = "Invalid current-season schedule"
                recordFailure(attemptedAt, message)
                return RefreshResult.Failure(message)
            }
            val key = CacheResourceKeys.currentSeasonSchedule(season.year)
            val snapshot = ResourceSnapshot(
                key = key.value,
                season = season.year,
                payloadKind = key.payloadKind,
                payloadVersion = PayloadVersion,
                payloadJson = json.encodeToString(SeasonResponseDto.serializer(), dto),
                fetchedAtEpochMs = attemptedAt,
                staleAfterEpochMs = attemptedAt + staleAfterMs,
                lastAttemptEpochMs = attemptedAt,
                lastAttemptStatus = RefreshAttemptStatus.Succeeded,
            )
            store.promoteActiveSeason(season.year, snapshot)
            RefreshResult.Success
        } catch (e: ClientRequestException) {
            fail(attemptedAt, "Request failed (${e.response.status.value})")
        } catch (e: ServerResponseException) {
            fail(attemptedAt, "Server error (${e.response.status.value})")
        } catch (e: Exception) {
            fail(attemptedAt, e.message ?: "Network error")
        }
    }

    private suspend fun fail(attemptedAt: Long, message: String): RefreshResult.Failure {
        recordFailure(attemptedAt, message)
        return RefreshResult.Failure(message)
    }

    private suspend fun recordFailure(attemptedAt: Long, message: String) {
        val activeSeason = store.stateValue().activeSeason ?: return
        store.recordAttempt(
            key = CacheResourceKeys.currentSeasonSchedule(activeSeason),
            attemptedAtEpochMs = attemptedAt,
            status = RefreshAttemptStatus.Failed(message),
        )
    }

    private suspend fun SnapshotStore.stateValue() = state.first()

    private suspend fun currentCachedSeason(): CachedResource<Season>? {
        val state = store.stateValue()
        val activeSeason = state.activeSeason ?: return null
        val key = CacheResourceKeys.currentSeasonSchedule(activeSeason).value
        return state.snapshots[key]?.toCachedSeasonOrNull()
    }

    private fun ResourceSnapshot.toCachedSeasonOrNull(): CachedResource<Season>? = try {
        if (payloadKind != CacheResourceKeys.currentSeasonSchedule(season ?: return null).payloadKind) return null
        val dto = json.decodeFromString(SeasonResponseDto.serializer(), payloadJson)
        val season = dto.toSeason()
        if (!season.isValidCurrentSchedule()) null else CachedResource(season, this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun Season.isValidCurrentSchedule(): Boolean = year > 0 && races.isNotEmpty()

    companion object {
        const val PayloadVersion = 1
        private const val TwelveHoursMs = 12L * 60L * 60L * 1000L
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
