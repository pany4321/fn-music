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

/**
 * 插画/封面占位图案的调色板：集中定义，UI 层不再出现颜色字面量。
 * 这些是内容美术（唱片、时钟、声波、心形、网格图块），不随配色主题变化。
 */
internal object FnArt {
    // 个人头像渐变
    val AvatarStart = Color(0xFFFF8A70)
    val AvatarMid = Color(0xFFD6A35E)
    val AvatarEnd = Color(0xFF70C8AF)

    // 黑胶唱片
    val VinylRing = Color(0xFF38393D)
    val VinylBody = Color(0xFF0B0C0E)
    val VinylGroove = Color(0xFF27282C)
    val VinylEdge = Color(0xFF08090A)
    val VinylLabelInk = Color(0xFFF8F2E7)

    // 时钟
    val ClockFaceStart = Color(0xFF101820)
    val ClockFaceEnd = Color(0xFF1B2733)
    val ClockHand = Color(0xFF0D1114)

    // 耳机
    val HeadphoneStart = Color(0xFFEAE6DF)
    val HeadphoneEnd = Color(0xFFC5CDCB)

    // 声波 / 心形 / 庆祝
    val WavePrimary = Color(0xFFFF4938)
    val WaveSecondary = Color(0xFFEC477E)
    val WaveTertiary = Color(0xFF7569D8)
    val WaveGlow = Color(0xFFFFA538)
    val HeartStart = Color(0xFFFF304D)
    val HeartMidStart = Color(0xFFFF563F)
    val HeartMidEnd = Color(0xFFFF9D37)
    val HeartEnd = Color(0xFFFFD45A)
    val HeartShadow = Color(0xFFD91442)
    val HeartGlow = Color(0xFFFFDA70)
    val HeartVein = Color(0xFF9E1731)
    val CelebrateStart = Color(0xFF124A3A)
    val CelebrateMid = Color(0xFF267A60)
    val Star = Color(0xFFE7C95D)
    val PlayBadge = Color(0xFF202528)

    // 歌单网格占位图块
    val GridTileStart = Color(0xFF2B1B3D)
    val GridTileSecond = Color(0xFF173F4E)
    val GridTileThird = Color(0xFF4E2317)
    val GridTileEnd = Color(0xFF1F3A2A)

    // 风格均衡器柱
    val EqualizerStart = Color(0xFFF6C445)
    val EqualizerMid = Color(0xFF4FC3F7)
    val EqualizerEnd = Color(0xFFBA68C8)
}

/** 一套主题的全部界面颜色（与 FnColors 的字段一一对应）。 */
internal data class ThemeColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val secondary: Color,
    val warning: Color,
    /** 卡片/输入框底色（默认主题下与原硬编码值一致）。 */
    val card: Color,
    /** 卡片聚焦底色。 */
    val cardFocused: Color,
    /** 顶部标签容器等次级容器底色。 */
    val container: Color,
    /** 描边色。 */
    val hairline: Color,
    /** 设置页等面板底色。 */
    val panel: Color,
    /** 面板内控件底色。 */
    val control: Color,
    /** 分隔线。 */
    val divider: Color,
    /** 面板描边。 */
    val panelBorder: Color,
    /** 选中态按钮的亮色底。 */
    val accentBright: Color,
    /** 选中态胶囊的柔和底（未聚焦/聚焦）。 */
    val accentSoft: Color,
    val accentSoftFocused: Color,
    /** 禁用态底。 */
    val disabled: Color,
    /** 勾选标记在警示色上的墨色。 */
    val inkOnWarning: Color,
    /** 设置页装饰圆。 */
    val decorA: Color,
    val decorB: Color,
    /** 次级胶囊（正在播放条）底色与描边。 */
    val pillBackground: Color,
    val pillBackgroundFocused: Color,
    val pillBackgroundPressed: Color,
    val pillBorder: Color,
    /** 封面占位底色。 */
    val artworkPlaceholder: Color,
    /** 卡片描边（让卡片区块与背景之间界限清晰）。 */
    val cardBorder: Color,
    /** 外框描边（胶囊容器、小控件、叠层封面），比卡片描边更轻。 */
    val frameBorder: Color,
    /** 错误/危险色。 */
    val danger: Color,
    /** 播放页左上角退出键底色。 */
    val controlStrong: Color,
    /** 首页四张功能卡的无封面渐变（起止）。 */
    val cardRoamStart: Color,
    val cardRoamEnd: Color,
    val cardFavoritesStart: Color,
    val cardFavoritesEnd: Color,
    val cardRecentStart: Color,
    val cardRecentEnd: Color,
    val cardCollectionStart: Color,
    val cardCollectionEnd: Color,
)

/** 卡片提亮：把深色卡片底整体抬亮一档，强光下的车机屏幕也能分清卡片边界。 */
private fun Color.lifted(t: Float = 0.08f): Color = lerp(this, Color.White, t)

internal fun themeColors(theme: AppTheme): ThemeColors = when (theme) {
    // 默认主题：与历史版本完全一致。
    AppTheme.CoralNight -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF141618)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF36383A)
        val surface = Color(0xFF1B1E22)
        val text = Color(0xFFF4F2EC)
        val muted = Color(0xFFA9ADB4)
        val accent = Color(0xFFFF7657)
        val secondary = Color(0xFF55C5A5)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF292D2C)
        val cardBorder = Color(0xFF434645)
        val cardFocused = Color(0xFF303634)
        val container = Color(0xFF1B1F21)
        val hairline = Color(0xFF454B4D)
        val panel = Color(0xFF212524)
        val control = Color(0xFF292D31)
        val divider = Color(0xFF2A302F)
        val panelBorder = Color(0xFF303735)
        val accentBright = Color(0xFFFF866D)
        val accentSoft = Color(0xFF4B3936)
        val accentSoftFocused = Color(0xFF513B37)
        val disabled = Color(0xFF24282B)
        val inkOnWarning = Color(0xFF17201E)
        val decorA = Color(0xFF141719)
        val decorB = Color(0xFF131617)
        val pillBackground = Color(0xFF232827)
        val pillBackgroundFocused = Color(0xFF343A38)
        val pillBackgroundPressed = Color(0xFF3B413F)
        val pillBorder = Color(0xFF454C49)
        val artworkPlaceholder = Color(0xFF242927)
        val danger = Color(0xFFFF3B4D)
        val controlStrong = Color(0xFF0E1314)
        val cardRoamStart = Color(0xFF071D19)
        val cardRoamEnd = Color(0xFF102823)
        val cardFavoritesStart = Color(0xFF1C1110)
        val cardFavoritesEnd = Color(0xFF2A1615)
        val cardRecentStart = Color(0xFF10151C)
        val cardRecentEnd = Color(0xFF1A2330)
        val cardCollectionStart = Color(0xFF17201E)
        val cardCollectionEnd = Color(0xFF22302D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = accentBright,
            accentSoft = accentSoft,
            accentSoftFocused = accentSoftFocused,
            disabled = disabled,
            inkOnWarning = inkOnWarning,
            decorA = decorA,
            decorB = decorB,
            pillBackground = pillBackground,
            pillBackgroundFocused = pillBackgroundFocused,
            pillBackgroundPressed = pillBackgroundPressed,
            pillBorder = pillBorder,
            artworkPlaceholder = artworkPlaceholder,
            danger = danger,
            controlStrong = controlStrong,
            cardRoamStart = cardRoamStart,
            cardRoamEnd = cardRoamEnd,
            cardFavoritesStart = cardFavoritesStart,
            cardFavoritesEnd = cardFavoritesEnd,
            cardRecentStart = cardRecentStart,
            cardRecentEnd = cardRecentEnd,
            cardCollectionStart = cardCollectionStart,
            cardCollectionEnd = cardCollectionEnd,
        )
    }
    AppTheme.Jade -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF13181A)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF353A3B)
        val surface = Color(0xFF1A1F20)
        val text = Color(0xFFF1F5F3)
        val muted = Color(0xFF9FB0AC)
        val accent = Color(0xFF4FD1C5)
        val secondary = Color(0xFF7FD1AE)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF262D2C)
        val cardBorder = Color(0xFF404645)
        val cardFocused = Color(0xFF2C3836)
        val container = Color(0xFF192021)
        val hairline = Color(0xFF3E4B48)
        val panel = Color(0xFF202625)
        val control = Color(0xFF26302F)
        val divider = Color(0xFF27302E)
        val panelBorder = Color(0xFF2D3836)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    AppTheme.ForestGreen -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF131814)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF353A36)
        val surface = Color(0xFF1A201B)
        val text = Color(0xFFF1F5EF)
        val muted = Color(0xFFA3B3A4)
        val accent = Color(0xFF58C070)
        val secondary = Color(0xFFE8C36A)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF262D26)
        val cardBorder = Color(0xFF404640)
        val cardFocused = Color(0xFF2C382C)
        val container = Color(0xFF192019)
        val hairline = Color(0xFF3E4B3E)
        val panel = Color(0xFF202621)
        val control = Color(0xFF263026)
        val divider = Color(0xFF273026)
        val panelBorder = Color(0xFF2D382E)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    AppTheme.Violet -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF16141A)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF38363B)
        val surface = Color(0xFF1E1B24)
        val text = Color(0xFFF4F1FA)
        val muted = Color(0xFFADA6BD)
        val accent = Color(0xFFA78BFA)
        val secondary = Color(0xFFF0A0D0)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF2C2831)
        val cardBorder = Color(0xFF45424A)
        val cardFocused = Color(0xFF342C42)
        val container = Color(0xFF1D1925)
        val hairline = Color(0xFF48405A)
        val panel = Color(0xFF24212D)
        val control = Color(0xFF2D2838)
        val divider = Color(0xFF2E2939)
        val panelBorder = Color(0xFF363040)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    AppTheme.SakuraPink -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF1A1416)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF3B3638)
        val surface = Color(0xFF221B1E)
        val text = Color(0xFFFAF1F3)
        val muted = Color(0xFFBDA6AC)
        val accent = Color(0xFFF58AA8)
        val secondary = Color(0xFF7FD1E8)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF2F282B)
        val cardBorder = Color(0xFF484244)
        val cardFocused = Color(0xFF3A2C31)
        val container = Color(0xFF20191C)
        val hairline = Color(0xFF503F45)
        val panel = Color(0xFF272124)
        val control = Color(0xFF322A2E)
        val divider = Color(0xFF332B2F)
        val panelBorder = Color(0xFF3B3236)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    AppTheme.GraphiteBlue -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF13161A)
        // 外框描边（胶囊容器、小控件、叠层封面）——比卡片描边更轻。
        val frameBorder = Color(0xFF35383B)
        val surface = Color(0xFF1A1E24)
        val text = Color(0xFFF2F4F8)
        val muted = Color(0xFFA5AEBB)
        val accent = Color(0xFF5B9BF5)
        // 副色必须与其它主题不同：漫游等图标用副色，若与默认主题同值会看不出变化。
        val secondary = Color(0xFF9CC7F0)
        val warning = Color(0xFFE8C36A)
        // 卡片提亮：与背景拉开层次，车机强光下也能看清分区。
        val card = Color(0xFF262C33)
        val cardBorder = Color(0xFF40454B)
        val cardFocused = Color(0xFF2C3644)
        val container = Color(0xFF191E26)
        val hairline = Color(0xFF3E4854)
        val panel = Color(0xFF20242D)
        val control = Color(0xFF262E3A)
        val divider = Color(0xFF272F3B)
        val panelBorder = Color(0xFF2D3542)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    // 墨白：唯一一套“无彩”主题，所有层次靠明度表达，封面与插画成为页面里唯一的颜色。
    AppTheme.Ink -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF111213)
        val frameBorder = Color(0xFF343637)
        val surface = Color(0xFF191A1B)
        val text = Color(0xFFF2F3F4)
        val muted = Color(0xFF9AA0A5)
        val accent = Color(0xFFE8EAEC)
        val secondary = Color(0xFF8A9299)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF262829)
        val cardBorder = Color(0xFF3B3E3F)
        val cardFocused = Color(0xFF333536)
        val container = Color(0xFF1F2122)
        val hairline = Color(0xFF3A3D3E)
        val panel = Color(0xFF1E2021)
        val control = Color(0xFF2C2F31)
        val divider = Color(0xFF2A2D2E)
        val panelBorder = Color(0xFF343738)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            // 银白主色做“选中底”时不能太亮，否则文字读不出：往表面色压。
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.80f),
            accentSoftFocused = lerp(accent, surface, 0.70f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.10f),
            cardRoamEnd = lerp(background, secondary, 0.20f),
            cardFavoritesStart = lerp(background, accent, 0.06f),
            cardFavoritesEnd = lerp(background, accent, 0.14f),
            cardRecentStart = lerp(background, secondary, 0.07f),
            cardRecentEnd = lerp(background, secondary, 0.16f),
            cardCollectionStart = lerp(background, text, 0.05f),
            cardCollectionEnd = lerp(background, text, 0.12f),
        )
    }
    // 摩卡：低饱和暖棕 + 焦糖主色，与珊瑚夜的亮红、樱粉的粉刻意拉开。
    AppTheme.Mocha -> {
        // 页面底色：比纯黑抬一档，与黑色屏框 / 机身挡边分得开。
        val background = Color(0xFF171414)
        val frameBorder = Color(0xFF3C332D)
        val surface = Color(0xFF1F1B19)
        val text = Color(0xFFF4EFE9)
        val muted = Color(0xFFB3A79B)
        val accent = Color(0xFFD9A46A)
        val secondary = Color(0xFFA9B79B)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF2D2622)
        val cardBorder = Color(0xFF443A33)
        val cardFocused = Color(0xFF3A322C)
        val container = Color(0xFF251F1C)
        val hairline = Color(0xFF453B34)
        val panel = Color(0xFF231D19)
        val control = Color(0xFF332B26)
        val divider = Color(0xFF332A24)
        val panelBorder = Color(0xFF3D332C)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.14f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    // 酒红：宝石红主色，比珊瑚夜的橙红更深、更偏紫。
    AppTheme.Bordeaux -> {
        val background = Color(0xFF161013)
        val frameBorder = Color(0xFF3A232A)
        val surface = Color(0xFF1E1417)
        val text = Color(0xFFF6EFF1)
        val muted = Color(0xFFB4A3A8)
        val accent = Color(0xFFE2556E)
        val secondary = Color(0xFFC9A66B)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF2B1A1F)
        val cardBorder = Color(0xFF42282E)
        val cardFocused = Color(0xFF372227)
        val container = Color(0xFF1A1114)
        val hairline = Color(0xFF4A2E35)
        val panel = Color(0xFF211316)
        val control = Color(0xFF2F2024)
        val divider = Color(0xFF38232A)
        val panelBorder = Color(0xFF40272E)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    // 洋红：亮洋红主色，与紫罗兰（蓝紫）、樱粉（浅粉）不在同一色相段。
    AppTheme.Plum -> {
        val background = Color(0xFF160E16)
        val frameBorder = Color(0xFF3B273C)
        val surface = Color(0xFF1F151F)
        val text = Color(0xFFF6EFF6)
        val muted = Color(0xFFB3A2B3)
        val accent = Color(0xFFE07BD0)
        val secondary = Color(0xFF8FD3E8)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF2C1D2C)
        val cardBorder = Color(0xFF432C43)
        val cardFocused = Color(0xFF382639)
        val container = Color(0xFF1B111C)
        val hairline = Color(0xFF4B304B)
        val panel = Color(0xFF221622)
        val control = Color(0xFF312231)
        val divider = Color(0xFF3A243A)
        val panelBorder = Color(0xFF422B42)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    // 抹茶：黄绿主色，补上现有主题里缺的黄绿色相段。
    AppTheme.Matcha -> {
        val background = Color(0xFF13150D)
        val frameBorder = Color(0xFF353A24)
        val surface = Color(0xFF1B1E14)
        val text = Color(0xFFF3F6EC)
        val muted = Color(0xFFA8B29A)
        val accent = Color(0xFFB6D94C)
        val secondary = Color(0xFFE8C36A)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF272B1A)
        val cardBorder = Color(0xFF3D4229)
        val cardFocused = Color(0xFF333824)
        val container = Color(0xFF171A10)
        val hairline = Color(0xFF454B30)
        val panel = Color(0xFF1D2113)
        val control = Color(0xFF2A2F1C)
        val divider = Color(0xFF333823)
        val panelBorder = Color(0xFF3B4027)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            accentSoft = lerp(accent, surface, 0.72f),
            accentSoftFocused = lerp(accent, surface, 0.62f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
    // 铅灰青：低饱和冷灰青主色 + 陶土粉副色——安静，但不等于无彩（区别于墨白）。
    AppTheme.Pewter -> {
        val background = Color(0xFF101315)
        val frameBorder = Color(0xFF303637)
        val surface = Color(0xFF181C1D)
        val text = Color(0xFFEFF3F4)
        val muted = Color(0xFFA2ADB0)
        val accent = Color(0xFFA8C0C8)
        val secondary = Color(0xFFC99A90)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF232829)
        val cardBorder = Color(0xFF384041)
        val cardFocused = Color(0xFF2F3536)
        val container = Color(0xFF14181A)
        val hairline = Color(0xFF414A4C)
        val panel = Color(0xFF1A1F20)
        val control = Color(0xFF272E2F)
        val divider = Color(0xFF2F3637)
        val panelBorder = Color(0xFF363E3F)
        val danger = Color(0xFFFF3B4D)
        ThemeColors(
            background = background,
            surface = surface,
            text = text,
            muted = muted,
            accent = accent,
            secondary = secondary,
            warning = warning,
            card = card,
            cardFocused = cardFocused,
            cardBorder = cardBorder,
            frameBorder = frameBorder,
            container = container,
            hairline = hairline,
            panel = panel,
            control = control,
            divider = divider,
            panelBorder = panelBorder,
            accentBright = lerp(accent, Color.White, 0.12f),
            // 冷灰青主色偏亮：做“选中底”时往表面色压得更多，保证浅色文字读得清。
            accentSoft = lerp(accent, surface, 0.78f),
            accentSoftFocused = lerp(accent, surface, 0.68f),
            disabled = lerp(surface, background, 0.35f),
            inkOnWarning = background,
            decorA = lerp(background, surface, 0.45f),
            decorB = lerp(background, surface, 0.28f),
            pillBackground = lerp(surface, text, 0.06f),
            pillBackgroundFocused = lerp(surface, text, 0.12f),
            pillBackgroundPressed = lerp(surface, text, 0.16f),
            pillBorder = lerp(surface, text, 0.18f),
            artworkPlaceholder = lerp(surface, text, 0.05f),
            danger = danger,
            controlStrong = lerp(background, Color.Black, 0.25f),
            cardRoamStart = lerp(background, secondary, 0.08f),
            cardRoamEnd = lerp(background, secondary, 0.18f),
            cardFavoritesStart = lerp(background, accent, 0.08f),
            cardFavoritesEnd = lerp(background, accent, 0.18f),
            cardRecentStart = lerp(background, accent, 0.06f),
            cardRecentEnd = lerp(background, accent, 0.14f),
            cardCollectionStart = lerp(background, secondary, 0.10f),
            cardCollectionEnd = lerp(background, secondary, 0.22f),
        )
    }
}

internal fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.CoralNight -> "珊瑚夜"
    AppTheme.Jade -> "青碧"
    AppTheme.ForestGreen -> "森野绿"
    AppTheme.Violet -> "紫罗兰"
    AppTheme.SakuraPink -> "樱粉"
    AppTheme.GraphiteBlue -> "石墨蓝"
    AppTheme.Ink -> "墨白"
    AppTheme.Mocha -> "摩卡"
    AppTheme.Bordeaux -> "酒红"
    AppTheme.Plum -> "洋红"
    AppTheme.Matcha -> "抹茶"
    AppTheme.Pewter -> "铅灰青"
}

/**
 * 全局取色对象：字段是可观察状态，[applyTheme] 一改，所有读取处即时重组，
 * 因此无需改动 300 余处调用点即可整机换肤。
 */
object FnColors {
    var Background by mutableStateOf(Color(0xFF141618))
        private set
    var Surface by mutableStateOf(Color(0xFF1B1E22))
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
    var Card by mutableStateOf(Color(0xFF262B2D))
        private set
    var CardFocused by mutableStateOf(Color(0xFF303634))
        private set
    var Container by mutableStateOf(Color(0xFF1B1F21))
        private set
    var Hairline by mutableStateOf(Color(0xFF454B4D))
        private set
    var Panel by mutableStateOf(Color(0xFF222829))
        private set
    var Control by mutableStateOf(Color(0xFF292D31))
        private set
    var Divider by mutableStateOf(Color(0xFF2A302F))
        private set
    var PanelBorder by mutableStateOf(Color(0xFF303735))
        private set
    var AccentBright by mutableStateOf(Color(0xFFFF866D))
        private set
    var AccentSoft by mutableStateOf(Color(0xFF4B3936))
        private set
    var AccentSoftFocused by mutableStateOf(Color(0xFF513B37))
        private set
    var Disabled by mutableStateOf(Color(0xFF24282B))
        private set
    var InkOnWarning by mutableStateOf(Color(0xFF17201E))
        private set
    var DecorA by mutableStateOf(Color(0xFF141719))
        private set
    var DecorB by mutableStateOf(Color(0xFF131617))
        private set
    var PillBackground by mutableStateOf(Color(0xFF232827))
        private set
    var PillBackgroundFocused by mutableStateOf(Color(0xFF343A38))
        private set
    var PillBackgroundPressed by mutableStateOf(Color(0xFF3B413F))
        private set
    var PillBorder by mutableStateOf(Color(0xFF454C49))
        private set
    var ArtworkPlaceholder by mutableStateOf(Color(0xFF242927))
        private set
    var CardBorder by mutableStateOf(Color(0xFF434645))
        private set
    var FrameBorder by mutableStateOf(Color(0xFF36383A))
        private set
    var Danger by mutableStateOf(Color(0xFFFF3B4D))
        private set
    var ControlStrong by mutableStateOf(Color(0xFF0E1314))
        private set
    var CardRoamStart by mutableStateOf(Color(0xFF071D19))
        private set
    var CardRoamEnd by mutableStateOf(Color(0xFF102823))
        private set
    var CardFavoritesStart by mutableStateOf(Color(0xFF1C1110))
        private set
    var CardFavoritesEnd by mutableStateOf(Color(0xFF2A1615))
        private set
    var CardRecentStart by mutableStateOf(Color(0xFF10151C))
        private set
    var CardRecentEnd by mutableStateOf(Color(0xFF1A2330))
        private set
    var CardCollectionStart by mutableStateOf(Color(0xFF17201E))
        private set
    var CardCollectionEnd by mutableStateOf(Color(0xFF22302D))
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
        Card = colors.card
        CardFocused = colors.cardFocused
        Container = colors.container
        Hairline = colors.hairline
        Panel = colors.panel
        Control = colors.control
        Divider = colors.divider
        PanelBorder = colors.panelBorder
        AccentBright = colors.accentBright
        AccentSoft = colors.accentSoft
        AccentSoftFocused = colors.accentSoftFocused
        Disabled = colors.disabled
        InkOnWarning = colors.inkOnWarning
        DecorA = colors.decorA
        DecorB = colors.decorB
        PillBackground = colors.pillBackground
        PillBackgroundFocused = colors.pillBackgroundFocused
        PillBackgroundPressed = colors.pillBackgroundPressed
        PillBorder = colors.pillBorder
        ArtworkPlaceholder = colors.artworkPlaceholder
        CardBorder = colors.cardBorder
        FrameBorder = colors.frameBorder
        Danger = colors.danger
        ControlStrong = colors.controlStrong
        CardRoamStart = colors.cardRoamStart.lifted()
        CardRoamEnd = colors.cardRoamEnd.lifted()
        CardFavoritesStart = colors.cardFavoritesStart.lifted()
        CardFavoritesEnd = colors.cardFavoritesEnd.lifted()
        CardRecentStart = colors.cardRecentStart.lifted()
        CardRecentEnd = colors.cardRecentEnd.lifted()
        CardCollectionStart = colors.cardCollectionStart.lifted()
        CardCollectionEnd = colors.cardCollectionEnd.lifted()
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
