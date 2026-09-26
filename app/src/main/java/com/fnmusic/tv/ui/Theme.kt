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

internal fun themeColors(theme: AppTheme): ThemeColors = when (theme) {
    // 默认主题：与历史版本完全一致。
    AppTheme.CoralNight -> {
        val background = Color(0xFF101214)
        val surface = Color(0xFF171A1E)
        val text = Color(0xFFF4F2EC)
        val muted = Color(0xFFA9ADB4)
        val accent = Color(0xFFFF7657)
        val secondary = Color(0xFF55C5A5)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF1B201F)
        val cardFocused = Color(0xFF303634)
        val container = Color(0xFF171B1D)
        val hairline = Color(0xFF454B4D)
        val panel = Color(0xFF151A19)
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
        val background = Color(0xFF0F1416)
        val surface = Color(0xFF161B1C)
        val text = Color(0xFFF1F5F3)
        val muted = Color(0xFF9FB0AC)
        val accent = Color(0xFF4FD1C5)
        val secondary = Color(0xFF7FD1AE)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF18201F)
        val cardFocused = Color(0xFF2C3836)
        val container = Color(0xFF151C1D)
        val hairline = Color(0xFF3E4B48)
        val panel = Color(0xFF141B1A)
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
        val background = Color(0xFF0F1410)
        val surface = Color(0xFF161C17)
        val text = Color(0xFFF1F5EF)
        val muted = Color(0xFFA3B3A4)
        val accent = Color(0xFF58C070)
        val secondary = Color(0xFFE8C36A)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF182018)
        val cardFocused = Color(0xFF2C382C)
        val container = Color(0xFF151C15)
        val hairline = Color(0xFF3E4B3E)
        val panel = Color(0xFF141B15)
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
        val background = Color(0xFF121016)
        val surface = Color(0xFF1A1720)
        val text = Color(0xFFF4F1FA)
        val muted = Color(0xFFADA6BD)
        val accent = Color(0xFFA78BFA)
        val secondary = Color(0xFFF0A0D0)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF1E1A24)
        val cardFocused = Color(0xFF342C42)
        val container = Color(0xFF191521)
        val hairline = Color(0xFF48405A)
        val panel = Color(0xFF191522)
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
        val background = Color(0xFF161012)
        val surface = Color(0xFF1E171A)
        val text = Color(0xFFFAF1F3)
        val muted = Color(0xFFBDA6AC)
        val accent = Color(0xFFF58AA8)
        val secondary = Color(0xFF7FD1E8)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF221A1D)
        val cardFocused = Color(0xFF3A2C31)
        val container = Color(0xFF1C1518)
        val hairline = Color(0xFF503F45)
        val panel = Color(0xFF1C1518)
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
        val background = Color(0xFF0F1216)
        val surface = Color(0xFF161A20)
        val text = Color(0xFFF2F4F8)
        val muted = Color(0xFFA5AEBB)
        val accent = Color(0xFF5B9BF5)
        val secondary = Color(0xFF55C5A5)
        val warning = Color(0xFFE8C36A)
        val card = Color(0xFF181E26)
        val cardFocused = Color(0xFF2C3644)
        val container = Color(0xFF151A22)
        val hairline = Color(0xFF3E4854)
        val panel = Color(0xFF141922)
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
    var Card by mutableStateOf(Color(0xFF1B201F))
        private set
    var CardFocused by mutableStateOf(Color(0xFF303634))
        private set
    var Container by mutableStateOf(Color(0xFF171B1D))
        private set
    var Hairline by mutableStateOf(Color(0xFF454B4D))
        private set
    var Panel by mutableStateOf(Color(0xFF151A19))
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
        Danger = colors.danger
        ControlStrong = colors.controlStrong
        CardRoamStart = colors.cardRoamStart
        CardRoamEnd = colors.cardRoamEnd
        CardFavoritesStart = colors.cardFavoritesStart
        CardFavoritesEnd = colors.cardFavoritesEnd
        CardRecentStart = colors.cardRecentStart
        CardRecentEnd = colors.cardRecentEnd
        CardCollectionStart = colors.cardCollectionStart
        CardCollectionEnd = colors.cardCollectionEnd
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
