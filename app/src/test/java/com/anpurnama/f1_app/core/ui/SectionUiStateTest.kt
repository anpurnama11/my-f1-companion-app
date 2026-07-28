package com.anpurnama.f1_app.core.ui

import com.anpurnama.f1_app.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionUiStateTest {

    @Test
    fun `Content defaults to fresh sync status`() {
        val content = SectionUiState.Content("cached payload")

        assertEquals("cached payload", content.data)
        assertEquals(ContentSyncStatus.Fresh, content.sync)
    }

    @Test
    fun `Content can carry non destructive cache sync statuses`() {
        val stale = SectionUiState.Content("old payload", ContentSyncStatus.Stale)
        val refreshing = SectionUiState.Content("old payload", ContentSyncStatus.Refreshing)
        val failed = SectionUiState.Content(
            data = "old payload",
            sync = ContentSyncStatus.RefreshFailed("No connection"),
        )

        assertEquals("old payload", stale.data)
        assertEquals(ContentSyncStatus.Stale, stale.sync)
        assertEquals("old payload", refreshing.data)
        assertEquals(ContentSyncStatus.Refreshing, refreshing.sync)
        assertEquals("old payload", failed.data)
        assertEquals(ContentSyncStatus.RefreshFailed("No connection"), failed.sync)
    }

    @Test
    fun `Outcome success maps to fresh content`() {
        val section = Outcome.Success("network payload").toSection()

        assertTrue(section is SectionUiState.Content)
        val content = section as SectionUiState.Content
        assertEquals("network payload", content.data)
        assertEquals(ContentSyncStatus.Fresh, content.sync)
    }

    @Test
    fun `Outcome loading and failure mappings are unchanged`() {
        assertEquals(SectionUiState.Loading, (Outcome.Loading as Outcome<String>).toSection())
        assertEquals(SectionUiState.Error("boom"), Outcome.Failure("boom").toSection())
    }
}
