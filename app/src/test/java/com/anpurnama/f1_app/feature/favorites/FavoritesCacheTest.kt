package com.anpurnama.f1_app.feature.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Rung 1 for the favorites store. The cache wraps a `DataStore<Preferences>`
 * with typed keys (`FAV_DRIVER_1` / `FAV_DRIVER_2` / `FAV_TEAM`) and a
 * one-shot "seed if empty" call that writes the default picks.
 *
 * Tests use `PreferenceDataStoreFactory.create { tempFile }` with a JUnit
 * `TemporaryFolder` — pure JVM, no Robolectric, no `android.*` imports
 * in the test body. The production class does import `Context` (held by
 * `Wiring`); the test only exercises the cache logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesCacheTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun newCache(): Pair<FavoritesCache, DataStore<Preferences>> {
        val file = File(tempFolder.newFolder(), "favorites.preferences_pb")
        val ds = PreferenceDataStoreFactory.create { file }
        return FavoritesCache(ds) to ds
    }

    @Test
    fun `read returns the empty Favorites when nothing has been written`() = runTest {
        val (cache, _) = newCache()
        val fav = cache.read().first()
        assertNull(fav.driver1Id)
        assertNull(fav.driver2Id)
        assertNull(fav.teamId)
        assertTrue(fav.isEmpty())
    }

    @Test
    fun `setDriver1 then setDriver2 then setTeam round-trips through the store`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver1("antonelli")
        cache.setDriver2("hamilton")
        cache.setTeam("ferrari")

        val fav = cache.read().first()
        assertEquals("antonelli", fav.driver1Id)
        assertEquals("hamilton", fav.driver2Id)
        assertEquals("ferrari", fav.teamId)
        assertFalse(fav.isEmpty())
    }

    @Test
    fun `overwriting a slot replaces only that slot`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver1("a")
        cache.setDriver2("b")
        cache.setTeam("c")
        cache.setTeam("d")  // replace team only

        val fav = cache.read().first()
        assertEquals("a", fav.driver1Id)
        assertEquals("b", fav.driver2Id)
        assertEquals("d", fav.teamId)
    }

    @Test
    fun `a driver cannot occupy both slots`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver1("antonelli")

        cache.setDriver2("antonelli")

        val fav = cache.read().first()
        assertEquals("antonelli", fav.driver1Id)
        assertNull(fav.driver2Id)
    }

    @Test
    fun `duplicate rejection works when driver 2 was selected first`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver2("russell")

        cache.setDriver1("russell")

        val fav = cache.read().first()
        assertNull(fav.driver1Id)
        assertEquals("russell", fav.driver2Id)
    }

    @Test
    fun `concurrent writes cannot put the same driver in both slots`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver1("antonelli")
        cache.setDriver2("russell")

        coroutineScope {
            launch { cache.setDriver1("hamilton") }
            launch { cache.setDriver2("hamilton") }
        }

        val fav = cache.read().first()
        assertEquals(1, listOf(fav.driver1Id, fav.driver2Id).count { it == "hamilton" })
        assertTrue(fav.driver1Id != fav.driver2Id)
    }

    @Test
    fun `seedIfEmpty writes defaults when the cache is empty`() = runTest {
        val (cache, _) = newCache()
        val drivers = listOf("antonelli", "russell")
        cache.seedIfEmpty(topTeamId = "mercedes", topDriverIds = drivers)

        val fav = cache.read().first()
        assertEquals("mercedes", fav.teamId)
        assertEquals("antonelli", fav.driver1Id)
        assertEquals("russell", fav.driver2Id)
    }

    @Test
    fun `seedIfEmpty is a no-op when the cache is already populated`() = runTest {
        val (cache, _) = newCache()
        cache.setDriver1("user-pick-1")
        cache.setDriver2("user-pick-2")
        cache.setTeam("user-team")

        // Second seed attempt with very different defaults must not clobber
        // the user's picks.
        cache.seedIfEmpty(topTeamId = "ferrari", topDriverIds = listOf("leclerc", "sainz"))

        val fav = cache.read().first()
        assertEquals("user-pick-1", fav.driver1Id)
        assertEquals("user-pick-2", fav.driver2Id)
        assertEquals("user-team", fav.teamId)
    }

    @Test
    fun `seedIfEmpty is partial when only some slots are empty`() = runTest {
        // If the user already picked a driver but nothing else, the seed
        // fills the remaining slots only — never overwrites the user's pick.
        // driver1 is preserved; driver2 gets the second top driver; team
        // gets the top constructor.
        val (cache, _) = newCache()
        cache.setDriver1("user-driver-1")

        cache.seedIfEmpty(topTeamId = "ferrari", topDriverIds = listOf("leclerc", "sainz"))

        val fav = cache.read().first()
        assertEquals("user-driver-1", fav.driver1Id)  // preserved
        assertEquals("sainz", fav.driver2Id)           // seeded (topDriverIds[1])
        assertEquals("ferrari", fav.teamId)            // seeded
    }
}
