package com.anpurnama.f1_app.widget.countdown.data

import com.anpurnama.f1_app.core.cache.createPreferencesDataStore
import com.anpurnama.f1_app.widget.countdown.NextRaceSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Rung 1 tests for the widget's `NextRaceCache` — the
 * `DataStore<Preferences>` read model for the Countdown widget.
 *
 * Construction uses [createPreferencesDataStore] — the same internal
 * helper `Wiring` uses — with a JUnit `TemporaryFolder`. The
 * corruption-recovery and I/O-failure tests therefore exercise the
 * production construction seam directly, through the public surface
 * (`snapshot`, `write`, `writeOffSeason`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NextRaceCacheTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun newCache(): NextRaceCache = NextRaceCache(
        createPreferencesDataStore(File(tempFolder.newFolder(), "next_race.preferences_pb")),
    )

    @Test
    fun `snapshot is null before the worker has written anything`() = runTest {
        val cache = newCache()
        val snap = cache.snapshot()
        assertNull(snap)
    }

    @Test
    fun `observe emits null then the persisted snapshot after a write`() = runTest {
        val cache = newCache()
        val written = NextRaceSnapshot(
            year = 2026,
            round = 7,
            raceName = "Hungarian Grand Prix",
            circuitName = "Hungaroring",
            circuitCountry = "Hungary",
            circuitId = "hungaroring",
            sessionName = "Race",
            startMillis = 1_700_000_000_000L,
            lastSyncedMillis = 1_699_000_000_000L,
        )

        cache.write(written)

        val observed = cache.observe().first()
        assertNotNull(observed)
        assertEquals(written, observed)
    }

    @Test
    fun `writeOffSeason clears the race fields and keeps the off-season sentinel`() = runTest {
        val cache = newCache()
        cache.write(
            NextRaceSnapshot(
                year = 2026,
                round = 1,
                raceName = "Bahrain Grand Prix",
                circuitName = "Bahrain International Circuit",
                circuitCountry = "Bahrain",
                circuitId = "bahrain",
                sessionName = "Race",
                startMillis = 1_700_000_000_000L,
                lastSyncedMillis = 1_699_000_000_000L,
            ),
        )

        cache.writeOffSeason(lastSyncedMillis = 1_700_000_500_000L)

        val snap = cache.snapshot()!!
        assertEquals(0L, snap.startMillis)
        assertEquals(0, snap.year)
        assertEquals(0, snap.round)
        assertEquals("", snap.raceName)
        assertEquals("", snap.circuitName)
        assertEquals(null, snap.circuitCountry)
        assertEquals("", snap.circuitId)
        assertEquals("Race", snap.sessionName)  // reducer default; field is removed
        assertEquals(1_700_000_500_000L, snap.lastSyncedMillis)
    }

    // ---- corruption recovery (issue #72) ----

    @Test
    fun `corrupt next-race file recovers to a null snapshot`() = runTest {
        val file = File(tempFolder.newFolder(), "next_race.preferences_pb")
        // Field-1 length-delimited tag (0x0A) followed by a varint that
        // claims 4 GiB of payload and is then truncated. The proto
        // parser raises InvalidProtocolBufferException, which the JVM
        // `PreferencesMapCompat.readFrom` re-throws as
        // `CorruptionException("Unable to parse preferences proto.")`,
        // which the `ReplaceFileCorruptionHandler` catches. The widget
        // reducer then maps the null snapshot to `NoRaceData`.
        file.writeBytes(
            byteArrayOf(
                0x0A,
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0x7F,
            ),
        )

        val cache = NextRaceCache(createPreferencesDataStore(file))

        val snap = cache.snapshot()

        assertNull(
            "expected null snapshot after recovery (widget's NoRaceData trigger), got $snap",
            snap,
        )
    }

    @Test
    fun `worker repopulates a previously-corrupt cache on the next successful write`() = runTest {
        val file = File(tempFolder.newFolder(), "next_race.preferences_pb")
        file.writeBytes(
            byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
        )

        val cache = NextRaceCache(createPreferencesDataStore(file))

        // First read after corruption — recovery path; the cache is now
        // a clean empty Preferences, so `snapshot()` returns null.
        assertNull(cache.snapshot())

        // The widget reducer maps null → NoRaceData. The worker's next
        // successful tick writes a new snapshot, which the cache holds
        // and the widget repaints through the existing pipeline.
        val written = NextRaceSnapshot(
            year = 2026,
            round = 4,
            raceName = "Spanish Grand Prix",
            circuitName = "Circuit de Barcelona-Catalunya",
            circuitCountry = "Spain",
            circuitId = "catalunya",
            sessionName = "Qualifying",
            startMillis = 1_700_000_000_000L,
            lastSyncedMillis = 1_700_000_500_000L,
        )
        cache.write(written)
        assertEquals(written, cache.snapshot())
    }

    @Test
    fun `unreadable next-race file fails and does not silently recover`() = runTest {
        val file = File(tempFolder.newFolder(), "next_race.preferences_pb")
        file.writeText("anything-readable")
        // On POSIX-friendly platforms (Linux, macOS) the read is denied.
        // The `ReplaceFileCorruptionHandler` is a
        // `CorruptionHandler<Preferences>` and only fires for
        // `CorruptionException`; ordinary `IOException`s propagate.
        val denyRead = file.setReadable(false)
        file.setWritable(false)
        // Skip on filesystems where permission denial is unsupported
        // (e.g. some FAT / virtualized FS). JUnit's Assume makes the
        // test `skipped` rather than `passed`, so a true pass requires
        // the I/O path to actually run.
        assumeTrue(
            "Filesystem does not honour setReadable(false); cannot exercise I/O failure path",
            denyRead,
        )

        val cache = NextRaceCache(createPreferencesDataStore(file))

        val result = runCatching { cache.snapshot() }

        assertTrue(
            "expected snapshot() to throw on a permission-denied file, got $result",
            result.isFailure,
        )

        // Restore so JUnit's TemporaryFolder cleanup can delete the file.
        file.setReadable(true)
        file.setWritable(true)
    }
}
