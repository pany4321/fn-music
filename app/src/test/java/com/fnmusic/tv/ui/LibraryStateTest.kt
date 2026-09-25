package com.fnmusic.tv.ui

import com.fnmusic.tv.core.model.Page
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.TrackGuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private fun track(id: String) = Track(
        guid = TrackGuid(id),
        title = "song-$id",
        artistName = null,
        albumName = null,
        coverId = null,
        durationMs = null,
        isCue = false,
    )

    private fun snapshotWith(vararg ids: String) = RetainedTrackCollectionSnapshot(
        tracks = ids.map(::track),
        loadedPages = listOf(
            Page(items = ids.map(::track), page = 1, pageSize = 50, total = ids.size, sort = "title"),
        ),
        page = 1,
        hasNext = false,
        expectedTotal = ids.size,
        expectedSort = "title",
        initialLoadCompleted = true,
    )

    @Test
    fun `removing a track drops it from tracks pages and totals`() {
        val snapshot = snapshotWith("a", "b", "c")

        val updated = removeTrackFromCollection(snapshot, TrackGuid("b"))

        assertEquals(listOf("a", "c"), updated.tracks.map { it.guid.value })
        assertEquals(2, updated.loadedPages.single().items.size)
        assertEquals(2, updated.loadedPages.single().total)
        assertEquals(2, updated.expectedTotal)
        assertEquals("title", updated.expectedSort)
        assertTrue(updated.initialLoadCompleted)
    }

    @Test
    fun `removing the last loaded track clears empty pages`() {
        val snapshot = snapshotWith("a")
        val withSecondPage = snapshot.copy(
            loadedPages = snapshot.loadedPages + Page(
                items = listOf(track("b")),
                page = 2,
                pageSize = 50,
                total = 2,
                sort = "title",
            ),
            tracks = listOf(track("a"), track("b")),
            page = 2,
            hasNext = true,
            expectedTotal = 2,
        )

        val updated = removeTrackFromCollection(withSecondPage, TrackGuid("b"))

        assertEquals(listOf("a"), updated.tracks.map { it.guid.value })
        assertEquals(1, updated.loadedPages.size)
        assertEquals(1, updated.expectedTotal)
    }

    @Test
    fun `removing an unknown track keeps the snapshot untouched`() {
        val snapshot = snapshotWith("a", "b")

        assertEquals(snapshot, removeTrackFromCollection(snapshot, TrackGuid("missing")))
    }

    @Test
    fun `totals never go negative`() {
        val snapshot = snapshotWith("a").copy(expectedTotal = 0)

        val updated = removeTrackFromCollection(snapshot, TrackGuid("a"))

        assertEquals(0, updated.expectedTotal)
        assertNull(updated.loadedPages.singleOrNull()?.items?.firstOrNull())
    }
}
