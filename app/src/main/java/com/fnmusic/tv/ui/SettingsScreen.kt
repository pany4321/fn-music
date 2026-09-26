package com.fnmusic.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.fnmusic.tv.AuthenticatedAppDependencies
import com.fnmusic.tv.BuildConfig
import com.fnmusic.tv.R
import com.fnmusic.tv.core.model.AppTheme
import com.fnmusic.tv.core.model.PlayerStyle
import com.fnmusic.tv.core.model.UiScaleMode
import com.fnmusic.tv.core.model.preferences.CacheBudget
import com.fnmusic.tv.core.model.preferences.CacheUsage
import com.fnmusic.tv.update.UpdateCheckSource
import com.fnmusic.tv.update.UpdateUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

// 面板/控件配色改为主题 token（默认主题下与原硬编码值一致，其它主题随主题变化）。
private val SettingsPanel: Color get() = FnColors.Panel
private val SettingsControl: Color get() = FnColors.Control
private val SettingsDividerColor: Color get() = FnColors.Divider
private val SettingsBorderColor: Color get() = FnColors.PanelBorder

/** 缩放档位的界面标签；自动档按设备密度决定实际倍数。 */
private fun uiScaleLabel(mode: UiScaleMode): String = when (mode) {
    UiScaleMode.Auto -> "自动"
    UiScaleMode.Standard -> "标准"
    UiScaleMode.Large -> "较大"
    UiScaleMode.Larger -> "更大"
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsScreen(container: AuthenticatedAppDependencies, onBack: () -> Unit) {
    val preferences by container.appPreferences.state.collectAsStateWithLifecycle()
    val updateState by container.updateController.state.collectAsStateWithLifecycle()
    val scope = LocalLibraryRetainedState.current.scope
    val window = LocalAdaptiveWindow.current
    val backgroundBackExit by container.appPreferences.backgroundBackExit.collectAsStateWithLifecycle()
    val preferencesTheme by container.appPreferences.theme.collectAsStateWithLifecycle()
    val preferencesUiScale by container.appPreferences.uiScale.collectAsStateWithLifecycle()
    val coverStyleFocus = remember { FocusRequester() }
    val posterStyleFocus = remember { FocusRequester() }
    val backgroundExitFocus = remember { FocusRequester() }
    val onlineLyricsFocus = remember { FocusRequester() }
    val cacheFocuses = remember { List(CacheBudget.entries.size) { FocusRequester() } }
    val clearCacheFocus = remember { FocusRequester() }
    val themeFocuses = remember { List(AppTheme.entries.size) { FocusRequester() } }
    val scaleFocuses = remember { List(UiScaleMode.entries.size) { FocusRequester() } }
    val updateFocus = remember { FocusRequester() }
    var usage by remember { mutableStateOf(CacheUsage(artworkBytes = 0, indexBytes = 0)) }

    suspend fun refreshUsage() {
        usage = container.musicRepository.cacheUsage()
    }

    LaunchedEffect(Unit) {
        container.musicRepository.applyArtworkBudget()
        refreshUsage()
    }
    LaunchedEffect(Unit) {
        yield()
        runCatching { coverStyleFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(FnColors.Background)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 84.dp, y = (-132).dp)
                .size(280.dp)
                .background(FnColors.DecorA, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 104.dp)
                .size(230.dp)
                .background(FnColors.DecorB, CircleShape),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = minOf(window.horizontalMargin, 40.dp),
                    vertical = if (window.shortHeight) 12.dp else 28.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailBackButton(onClick = onBack)
                Spacer(Modifier.width(16.dp))
                Text("设置", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("播放与歌词", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SettingsPanel, RoundedCornerShape(9.dp))
                    .border(0.75.dp, SettingsBorderColor, RoundedCornerShape(9.dp))
                    .padding(horizontal = 22.dp),
            ) {
                SettingsRow(label = "播放界面", height = 65.dp) {
                    SettingsChoiceButton(
                        label = "CD 模式",
                        selected = preferences.playerStyle == PlayerStyle.Cover,
                        onClick = { container.appPreferences.setPlayerStyle(PlayerStyle.Cover) },
                        modifier = Modifier
                            .width(123.dp)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = posterStyleFocus
                                down = backgroundExitFocus
                            }
                            .focusRequester(coverStyleFocus),
                    )
                    SettingsChoiceButton(
                        label = "海报模式",
                        selected = preferences.playerStyle == PlayerStyle.Poster,
                        onClick = { container.appPreferences.setPlayerStyle(PlayerStyle.Poster) },
                        modifier = Modifier
                            .width(123.dp)
                            .focusProperties {
                                left = coverStyleFocus
                                right = FocusRequester.Cancel
                                down = backgroundExitFocus
                            }
                            .focusRequester(posterStyleFocus),
                    )
                }
                SettingsDivider()
                SettingsRow(label = "车载", height = 65.dp) {
                SettingsCheckbox(
                    label = "返回键后台运行",
                    selected = backgroundBackExit,
                    onClick = {
                        container.appPreferences.setBackgroundBackExitEnabled(!backgroundBackExit)
                    },
                    modifier = Modifier
                        .focusProperties {
                            up = coverStyleFocus
                            down = onlineLyricsFocus
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .focusRequester(backgroundExitFocus),
                )
                }
                SettingsDivider()
                SettingsRow(label = "歌词", height = 65.dp) {
                    SettingsCheckbox(
                        label = "在线歌词匹配",
                        selected = preferences.onlineLyricsMatchingEnabled,
                        onClick = {
                            container.appPreferences.setOnlineLyricsMatchingEnabled(
                                !preferences.onlineLyricsMatchingEnabled,
                            )
                        },
                        modifier = Modifier
                            .focusProperties {
                                up = backgroundExitFocus
                                down = cacheFocuses.first()
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .focusRequester(onlineLyricsFocus),
                    )
                }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "图片磁盘缓存上限",
                        color = FnColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(144.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        // FlowRow lets the four budget pills wrap onto extra lines
                        // on narrow car screens instead of clipping.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            CacheBudget.entries.forEachIndexed { index, budget ->
                                SettingsChoiceButton(
                                    label = "${budget.megabytes} MB",
                                    selected = preferences.cacheBudget == budget,
                                    onClick = {
                                        scope.launch {
                                            container.appPreferences.setCacheBudget(budget)
                                            container.musicRepository.applyArtworkBudget()
                                            refreshUsage()
                                        }
                                    },
                                    modifier = Modifier
                                        .width(104.dp)
                                        .focusProperties {
                                            up = onlineLyricsFocus
                                            // 下方是主题行（新增），不能再跳过它直达更新按钮。
                                            down = themeFocuses.first()
                                            left = cacheFocuses.getOrNull(index - 1)
                                                ?: FocusRequester.Cancel
                                            right = cacheFocuses.getOrNull(index + 1)
                                                ?: clearCacheFocus
                                        }
                                        .focusRequester(cacheFocuses[index]),
                                )
                            }
                        }
                        Text(
                            "当前 ${formatBytes(usage.totalBytes)}（图片 ${formatBytes(usage.artworkBytes)} / 资料 ${formatBytes(usage.indexBytes)}）",
                            color = FnColors.Muted,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SettingsActionButton(
                        label = "清除图片和资料缓存",
                        onClick = {
                            scope.launch {
                                container.authenticatedActions.clearAllEvictableCaches()
                                refreshUsage()
                            }
                        },
                        modifier = Modifier
                            .width(166.dp)
                            .focusProperties {
                                up = onlineLyricsFocus
                                // 下方是主题行。
                                down = themeFocuses.first()
                                left = cacheFocuses.last()
                                right = FocusRequester.Cancel
                            }
                            .focusRequester(clearCacheFocus),
                    )
                }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "主题",
                        color = FnColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(144.dp),
                    )
                    // 六个主题：窄屏自动换行；左右键行内移动，行首/行尾取消，上下接缓存行与更新按钮。
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AppTheme.entries.forEachIndexed { index, theme ->
                            SettingsChoiceButton(
                                label = themeLabel(theme),
                                selected = preferencesTheme == theme,
                                onClick = { container.appPreferences.setTheme(theme) },
                                modifier = Modifier
                                    .width(104.dp)
                                    .focusProperties {
                                        up = clearCacheFocus
                                        // 下方是界面缩放行（新增），再往下才是更新按钮。
                                        down = scaleFocuses.first()
                                        left = themeFocuses.getOrNull(index - 1)
                                            ?: FocusRequester.Cancel
                                        right = themeFocuses.getOrNull(index + 1)
                                            ?: FocusRequester.Cancel
                                    }
                                    .focusRequester(themeFocuses[index]),
                            )
                        }
                    }
                }
                SettingsDivider()
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("界面缩放", color = FnColors.Muted, fontSize = 12.sp)
                    // 四个档位固定在单行（总宽约 437dp，最短的横屏也放得下），
                    // 不换行意味着上下键不会跳格：上行接主题、下行接更新按钮。
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        UiScaleMode.entries.forEachIndexed { index, mode ->
                            SettingsChoiceButton(
                                label = uiScaleLabel(mode),
                                selected = preferencesUiScale == mode,
                                onClick = { container.appPreferences.setUiScale(mode) },
                                modifier = Modifier
                                    // 等分可用宽度并封顶 104dp：窄屏（手机开 1.5 倍）也不会把
                                    // 第四个档位挤出边界，宽屏仍与主题行胶囊等宽。
                                    .weight(1f, fill = false)
                                    .widthIn(max = 104.dp)
                                    .focusProperties {
                                        up = themeFocuses.last()
                                        down = if (container.updateController.enabled) {
                                            updateFocus
                                        } else {
                                            FocusRequester.Cancel
                                        }
                                        left = scaleFocuses.getOrNull(index - 1)
                                            ?: FocusRequester.Cancel
                                        right = scaleFocuses.getOrNull(index + 1)
                                            ?: FocusRequester.Cancel
                                    }
                                    .focusRequester(scaleFocuses[index]),
                            )
                        }
                    }
                    Text(
                        "自动按屏幕密度选择：电视/手机 1 倍，1080P 车机 1.5 倍。车机建议「标准」。",
                        color = FnColors.Muted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("关于", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(114.dp)
                    .background(SettingsPanel, RoundedCornerShape(9.dp))
                    .border(0.75.dp, SettingsBorderColor, RoundedCornerShape(9.dp))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.width(238.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("飞牛音乐", fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Android TV / 车机 飞牛音乐第三方客户端",
                            color = FnColors.Muted,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AboutDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutValue("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                    AboutValue("作者", "大南瓜")
                    AboutValue("GitHub", "github.com/pany4321/fn-music")
                }
                if (container.updateController.enabled) {
                    AboutDivider()
                    Column(
                        Modifier.width(175.dp).padding(start = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("软件更新", color = FnColors.Muted, fontSize = 12.sp, lineHeight = 14.sp)
                        SettingsActionButton(
                            label = updateButtonLabel(updateState),
                            leading = "↓",
                            enabled = (updateState as? UpdateUiState.Checking)?.source != UpdateCheckSource.Manual,
                            onClick = container.updateController::checkManually,
                                    modifier = Modifier
                                        .width(128.dp)
                                        .height(48.dp)
                                .focusProperties {
                                    up = scaleFocuses.last()
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                                .focusRequester(updateFocus),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    height: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FnColors.Muted, fontSize = 12.sp, modifier = Modifier.width(144.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SettingsDividerColor))
}

@Composable
private fun AboutDivider() {
    Box(Modifier.width(0.5.dp).height(82.dp).background(SettingsDividerColor))
}

@Composable
private fun AboutValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FnColors.Muted, fontSize = 12.sp, modifier = Modifier.width(63.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            // 与设置页其它控件同一套描边策略：
            //   未聚焦 = 控件底 + 0.5dp 亮细线（无论是否选中）
            //   聚焦   = 提亮底 + 同样粗细的亮线（未选中不加粗）；胶囊的主色只用来表示“选中”，
            //            所以未选中项聚焦时不用主色描边（否则比选中项还抢眼）
            //   选中   = 主色底 + 1.5dp 主色线 + 加粗
            containerColor = if (selected) FnColors.AccentSoft else SettingsControl,
            contentColor = FnColors.Text,
            focusedContainerColor = if (selected) FnColors.AccentSoftFocused else FnColors.CardFocused,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = if (selected) FnColors.AccentSoftFocused else FnColors.CardFocused,
            pressedContentColor = FnColors.Text,
        ),
        border = ButtonDefaults.border(
            border = Border(
                BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) FnColors.Coral else FnColors.Hairline),
                shape = shape,
            ),
            // 未选中项聚焦时线宽保持与其它按钮一致的 0.5dp（只把线调亮 + 底色提亮），
            // 线加粗会显得比旁边的按钮“更粗一档”。
            focusedBorder = Border(
                BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) FnColors.Coral else FnColors.Muted),
                shape = shape,
            ),
            pressedBorder = Border(
                BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) FnColors.Coral else FnColors.Muted),
                shape = shape,
            ),
        ),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 选中态靠“底色 + 加粗”区分，焦点态靠描边，两者不再混淆。
            Text(
                label,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            containerColor = SettingsControl,
            contentColor = FnColors.Text,
            focusedContainerColor = FnColors.Coral,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = FnColors.Coral,
            pressedContentColor = FnColors.Text,
            disabledContainerColor = FnColors.Disabled,
            disabledContentColor = FnColors.Muted,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.5.dp, FnColors.Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) Text(leading, fontSize = 16.sp, lineHeight = 16.sp)
                Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun SettingsCheckbox(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .width(166.dp)
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(SettingsControl, RoundedCornerShape(5.dp))
            .border(
                if (focused) 1.5.dp else 0.5.dp,
                if (focused) FnColors.Coral else FnColors.Hairline,
                RoundedCornerShape(5.dp),
            )
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(if (selected) FnColors.Warning else Color.Transparent, RoundedCornerShape(2.dp))
                .border(1.5.dp, if (selected) FnColors.Warning else FnColors.Muted, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", color = FnColors.InkOnWarning, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun updateButtonLabel(state: UpdateUiState): String = when (state) {
    is UpdateUiState.Checking -> "正在检查…"
    is UpdateUiState.UpToDate -> "已是最新版本"
    is UpdateUiState.Error -> if (state.manifest == null) "检查失败，重试" else "检查更新"
    else -> "检查更新"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f KB".format(bytes / 1024.0)
}
