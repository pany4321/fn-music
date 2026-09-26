package com.fnmusic.tv.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Landscape viewport adaptation shared by TV and sideloaded car head units.
 * TV viewports (~960dp wide) keep the historical 64dp margins and four-column
 * grids; narrower or shorter car screens shrink margins and column counts so
 * fixed-size content still fits instead of clipping.
 */
data class AdaptiveWindow(
    val horizontalMargin: Dp,
    val compact: Boolean,
    val shortHeight: Boolean,
    /** 当前界面缩放倍数（1 倍为原始尺寸），封面据此选择更大的位图变体。 */
    val uiScale: Float = 1f,
)

val LocalAdaptiveWindow = staticCompositionLocalOf {
    AdaptiveWindow(horizontalMargin = 64.dp, compact = false, shortHeight = false)
}

internal fun adaptiveWindowFor(maxWidth: Dp, maxHeight: Dp, uiScale: Float = 1f): AdaptiveWindow {
    val horizontalMargin = when {
        maxWidth >= 880.dp -> 64.dp
        maxWidth >= 600.dp -> 40.dp
        else -> 24.dp
    }
    return AdaptiveWindow(
        horizontalMargin = horizontalMargin,
        compact = maxWidth < 880.dp,
        // 16:9 TVs are 960x540dp; only shorter car viewports (e.g. 1920x720
        // at 1.5x density = 480dp tall) count as short.
        shortHeight = maxHeight < 520.dp,
        uiScale = uiScale,
    )
}

/**
 * Catalog grids keep fixed-width tiles; derive how many columns fit so narrow
 * viewports shrink the column count instead of overflowing. `maxColumns`
 * preserves the tuned TV layout: a ~960dp TV viewport still renders exactly
 * four fixed catalog columns.
 */
internal fun fittedGridColumns(
    availableWidth: Dp,
    maxColumns: Int,
    minTileWidth: Dp = 170.dp,
    spacing: Dp = 14.dp,
): Int {
    if (availableWidth < minTileWidth) return 1
    val fitted = ((availableWidth.value + spacing.value) / (minTileWidth.value + spacing.value)).toInt()
    return fitted.coerceIn(1, maxColumns)
}
