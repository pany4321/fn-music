package com.fnmusic.tv.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `tv viewport keeps historical margins and flags`() {
        val window = adaptiveWindowFor(maxWidth = 960.dp, maxHeight = 540.dp)
        assertEquals(64.dp, window.horizontalMargin)
        assertFalse(window.compact)
        assertFalse(window.shortHeight)
    }

    @Test
    fun `width tier boundaries map to the three margins`() {
        assertEquals(64.dp, adaptiveWindowFor(880.dp, 540.dp).horizontalMargin)
        assertEquals(40.dp, adaptiveWindowFor(879.dp, 540.dp).horizontalMargin)
        assertEquals(40.dp, adaptiveWindowFor(600.dp, 540.dp).horizontalMargin)
        assertEquals(24.dp, adaptiveWindowFor(599.dp, 540.dp).horizontalMargin)
    }

    @Test
    fun `compact flag follows the tv margin boundary`() {
        assertFalse(adaptiveWindowFor(880.dp, 540.dp).compact)
        assertTrue(adaptiveWindowFor(879.dp, 540.dp).compact)
    }

    @Test
    fun `short height flag flips at 520dp`() {
        assertFalse(adaptiveWindowFor(960.dp, 540.dp).shortHeight)
        assertFalse(adaptiveWindowFor(960.dp, 520.dp).shortHeight)
        assertTrue(adaptiveWindowFor(960.dp, 519.dp).shortHeight)
        assertTrue(adaptiveWindowFor(640.dp, 480.dp).shortHeight)
    }

    @Test
    fun `catalog grid keeps four columns on the tv viewport`() {
        // Both 1080p and 720p TV profiles present a ~960dp-wide viewport;
        // 960dp minus 2 x 64dp margins leaves 832dp of content.
        assertEquals(4, fittedGridColumns(availableWidth = 832.dp, maxColumns = 4))
    }

    @Test
    fun `narrow viewports shrink catalog columns`() {
        // 640dp viewport with 40dp margins.
        assertEquals(3, fittedGridColumns(availableWidth = 560.dp, maxColumns = 4))
        // 480dp viewport with 24dp margins.
        assertEquals(2, fittedGridColumns(availableWidth = 432.dp, maxColumns = 4))
    }

    @Test
    fun `ultra wide viewports never exceed the tuned column maximum`() {
        assertEquals(4, fittedGridColumns(availableWidth = 1152.dp, maxColumns = 4))
        assertEquals(3, fittedGridColumns(availableWidth = 876.dp, maxColumns = 3, minTileWidth = 165.dp, spacing = 12.dp))
    }

    @Test
    fun `tiny widths degrade to a single column`() {
        assertEquals(1, fittedGridColumns(availableWidth = 170.dp, maxColumns = 4))
        assertEquals(1, fittedGridColumns(availableWidth = 100.dp, maxColumns = 4))
        assertEquals(1, fittedGridColumns(availableWidth = 0.dp, maxColumns = 4))
    }
}
