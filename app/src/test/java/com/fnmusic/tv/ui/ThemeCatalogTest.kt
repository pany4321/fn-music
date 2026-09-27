package com.fnmusic.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.fnmusic.tv.core.model.AppTheme
import com.fnmusic.tv.core.model.UiScaleMode
import com.fnmusic.tv.core.model.factor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 12 套主题必须都完整：底色不透明、文字在各自底色上仍然可读、主色与名称互不重复。
 * 新加主题时如果忘了配某个 token，这里会直接失败。
 */
class ThemeCatalogTest {
    @Test fun `every theme keeps its key tokens opaque`() {
        AppTheme.entries.forEach { theme ->
            val colors = themeColors(theme)
            val tokens = listOf(
                "background" to colors.background,
                "surface" to colors.surface,
                "card" to colors.card,
                "cardBorder" to colors.cardBorder,
                "frameBorder" to colors.frameBorder,
                "container" to colors.container,
                "panel" to colors.panel,
                "control" to colors.control,
                "hairline" to colors.hairline,
                "divider" to colors.divider,
                "panelBorder" to colors.panelBorder,
                "accent" to colors.accent,
                "secondary" to colors.secondary,
                "accentSoft" to colors.accentSoft,
                "pillBackground" to colors.pillBackground,
            )
            tokens.forEach { (name, color) ->
                assertEquals("$theme.$name 必须不透明", 1f, color.alpha, 0.001f)
            }
        }
    }

    @Test fun `every theme keeps text legible on its own background`() {
        AppTheme.entries.forEach { theme ->
            val colors = themeColors(theme)
            assertTrue(
                "$theme 正文对比度不足",
                contrastRatio(colors.text, colors.background) >= 7f,
            )
            assertTrue(
                "$theme 次要文字对比度不足",
                contrastRatio(colors.muted, colors.background) >= 4.5f,
            )
        }
    }

    @Test fun `theme labels and accents are unique`() {
        val labels = AppTheme.entries.map(::themeLabel)
        assertEquals(AppTheme.entries.size, labels.toSet().size)
        assertTrue(labels.all(String::isNotBlank))

        val accents = AppTheme.entries.map { themeColors(it).accent.value }
        assertEquals("主色不能重复", AppTheme.entries.size, accents.toSet().size)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

/** 缩放档位：标准 1.35 / 较大 1.5 / 更大 1.75，自动档车机 1.25、电视与手机 1.0。 */
class UiScaleModeTest {
    @Test fun `fixed presets match the product decision`() {
        assertEquals(1.35f, UiScaleMode.Standard.factor(3.5f), 0f)
        assertEquals(1.5f, UiScaleMode.Large.factor(3.5f), 0f)
        assertEquals(1.75f, UiScaleMode.Larger.factor(3.5f), 0f)
    }

    @Test fun `auto follows device density`() {
        assertEquals(1.25f, UiScaleMode.Auto.factor(1.0f), 0f)
        assertEquals(1.25f, UiScaleMode.Auto.factor(1.33f), 0f)
        assertEquals(1.25f, UiScaleMode.Auto.factor(1.79f), 0f)
        assertEquals(1.0f, UiScaleMode.Auto.factor(1.8f), 0f)
        assertEquals(1.0f, UiScaleMode.Auto.factor(3.5f), 0f)
    }
}
