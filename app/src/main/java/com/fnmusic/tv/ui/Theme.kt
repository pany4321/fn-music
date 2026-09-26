package com.fnmusic.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fnmusic.tv.core.model.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlin.math.pow

/** 一套主题的全部界面颜色（与 FnColors 的字段一一对应）。 */
internal data class ThemeColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val secondary: Color,
    val warning: Color,
)

internal fun themeColors(theme: AppTheme): ThemeColors = when (theme) {
    // 默认主题：与历史版本完全一致。
    AppTheme.CoralNight -> ThemeColors(
        background = Color(0xFF101214),
        surface = Color(0xFF171A1E),
        text = Color(0xFFF4F2EC),
        muted = Color(0xFFA9ADB4),
        accent = Color(0xFFFF7657),
        secondary = Color(0xFF55C5A5),
        warning = Color(0xFFE8C36A),
    )
    AppTheme.Jade -> ThemeColors(
        background = Color(0xFF0F1416),
        surface = Color(0xFF161B1C),
        text = Color(0xFFF1F5F3),
        muted = Color(0xFF9FB0AC),
        accent = Color(0xFF4FD1C5),
        secondary = Color(0xFF7FD1AE),
        warning = Color(0xFFE8C36A),
    )
    AppTheme.ForestGreen -> ThemeColors(
        background = Color(0xFF0F1410),
        surface = Color(0xFF161C17),
        text = Color(0xFFF1F5EF),
        muted = Color(0xFFA3B3A4),
        accent = Color(0xFF58C070),
        secondary = Color(0xFFE8C36A),
        warning = Color(0xFFE8C36A),
    )
    AppTheme.Violet -> ThemeColors(
        background = Color(0xFF121016),
        surface = Color(0xFF1A1720),
        text = Color(0xFFF4F1FA),
        muted = Color(0xFFADA6BD),
        accent = Color(0xFFA78BFA),
        secondary = Color(0xFFF0A0D0),
        warning = Color(0xFFE8C36A),
    )
    AppTheme.SakuraPink -> ThemeColors(
        background = Color(0xFF161012),
        surface = Color(0xFF1E171A),
        text = Color(0xFFFAF1F3),
        muted = Color(0xFFBDA6AC),
        accent = Color(0xFFF58AA8),
        secondary = Color(0xFF7FD1E8),
        warning = Color(0xFFE8C36A),
    )
    AppTheme.GraphiteBlue -> ThemeColors(
        background = Color(0xFF0F1216),
        surface = Color(0xFF161A20),
        text = Color(0xFFF2F4F8),
        muted = Color(0xFFA5AEBB),
        accent = Color(0xFF5B9BF5),
        secondary = Color(0xFF55C5A5),
        warning = Color(0xFFE8C36A),
    )
}

internal fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.CoralNight -> "珊瑚夜"
    AppTheme.Jade -> "青碧"
    AppTheme.ForestGreen -> "森野绿"
    AppTheme.Violet -> "紫罗兰"
    AppTheme.SakuraPink -> "樱粉"
    AppTheme.GraphiteBlue -> "石墨蓝"
}

/**
 * 全局取色对象：字段是可观察状态，[applyTheme] 一改，所有读取处即时重组，
 * 因此无需改动 300 余处调用点即可整机换肤。
 */
object FnColors {
    var Background by mutableStateOf(Color(0xFF101214))
        private set
    var Surface by mutableStateOf(Color(0xFF171A1E))
        private set
    var Text by mutableStateOf(Color(0xFFF4F2EC))
        private set
    var Muted by mutableStateOf(Color(0xFFA9ADB4))
        private set
    var Coral by mutableStateOf(Color(0xFFFF7657))
        private set
    var Teal by mutableStateOf(Color(0xFF55C5A5))
        private set
    var Warning by mutableStateOf(Color(0xFFE8C36A))
        private set

    val FocusFill: Color get() = lerp(Coral, Surface, 0.42f)

    internal fun applyTheme(colors: ThemeColors) {
        Background = colors.background
        Surface = colors.surface
        Text = colors.text
        Muted = colors.muted
        Coral = colors.accent
        Teal = colors.secondary
        Warning = colors.warning
    }
}

@Composable
fun FnMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FnColors.Coral,
            secondary = FnColors.Teal,
            background = FnColors.Background,
            surface = FnColors.Surface,
            onPrimary = FnColors.Background,
            onSecondary = FnColors.Background,
            onBackground = FnColors.Text,
            onSurface = FnColors.Text,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides FnColors.Text, content = content)
    }
}

// ---------------------------------------------------------------------------
// 歌词当前行配色（方案 A）：按背景对比度选取，保证任何封面/面板底色下都清晰。
// ---------------------------------------------------------------------------

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

/** 半透明底色先压到不透明背景上，避免对比度算错。 */
private fun opaqueOver(background: Color, base: Color): Color =
    if (background.alpha >= 1f) background else lerp(base, background, background.alpha)

/**
 * 当前歌词行颜色：依次尝试
 * 主题强调色 → 强调色提亮/压暗 → 封面主色提亮/压暗 → 白 → 黑，
 * 取第一个对比度 ≥ 4.5:1 的候选；都不达标时取对比度最高者。
 */
internal fun lyricAccentColor(background: Color, accent: Color, ambience: Color): Color {
    val surface = opaqueOver(background, FnColors.Background)
    val candidates = listOf(
        accent,
        lerp(accent, Color.White, 0.25f),
        lerp(accent, Color.Black, 0.25f),
        lerp(ambience, Color.White, 0.35f),
        lerp(ambience, Color.Black, 0.35f),
        Color.White,
        Color.Black,
    )
    val minimum = 4.5f
    return candidates.firstOrNull { contrastRatio(it, surface) >= minimum }
        ?: candidates.maxByOrNull { contrastRatio(it, surface) }
        ?: accent
}
