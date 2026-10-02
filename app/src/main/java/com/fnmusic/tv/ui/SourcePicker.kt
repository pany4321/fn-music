package com.fnmusic.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.yield
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.yield
import androidx.tv.material3.Text
import com.fnmusic.tv.core.data.repository.LoginHistoryEntry
import com.fnmusic.tv.core.data.repository.SourceTestResult
import com.fnmusic.tv.core.model.ServerKind
import com.fnmusic.tv.ui.FnColors

/**
 * 音乐源（一台服务器 + 一个账号）在界面上的展示名。
 * 术语选择：**音乐源**而不是"播放源" —— 后者容易被理解成"同一首歌的直连/转码线路"，
 * 而这个概念管的是"音乐从哪台服务器来"。
 */
internal fun sourceTitle(entry: LoginHistoryEntry): String =
    entry.serverName
        // 旧版本把 fn 源条目存成了简写 "fn"：显示时归一化为正式名称
        ?.takeIf { it.isNotBlank() && !it.equals("fn", ignoreCase = true) }
        ?: sourceKindLabel(entry.kind)

/** 源类型的中文名（给用户看）。 */
internal fun sourceKindLabel(kind: ServerKind): String = when (kind) {
    ServerKind.FnOs -> "飞牛音乐"
    ServerKind.Jellyfin -> "Jellyfin"
}

internal fun sourceSubtitle(entry: LoginHistoryEntry): String =
    "${sourceKindLabel(entry.kind)} · ${entry.server} · ${entry.username}"

/** 源行下面那行状态：切换中 / 测试中 / 测试结果。 */
internal data class SourceRowStatus(val text: String, val tone: Tone) {
    enum class Tone { Neutral, Positive, Negative }

    companion object {
        fun switching() = SourceRowStatus("正在切换…", Tone.Neutral)
        fun testing() = SourceRowStatus("正在测试…", Tone.Neutral)
    }
}

/** 一行的三个焦点锚点：源行本体 / 测试 / 删除。三列各自纵向相连，遥控器上下键才顺手。 */
internal class SourceRowFocuses(
    val body: FocusRequester,
    val test: FocusRequester,
    val delete: FocusRequester,
)

/** 测试结果 → 状态行（失败原因复用设置页/弹窗统一的 errorMessage）。 */
internal fun sourceTestStatus(result: SourceTestResult?): SourceRowStatus = when (result) {
    null -> SourceRowStatus("测试没有完成，请再试一次", SourceRowStatus.Tone.Negative)
    is SourceTestResult.Ok -> SourceRowStatus(
        text = if (result.renewedCredentials) {
            "连接正常（${result.elapsedMillis} ms，已用保存的密码续期）"
        } else {
            "连接正常（${result.elapsedMillis} ms）"
        },
        tone = SourceRowStatus.Tone.Positive,
    )
    is SourceTestResult.CredentialsExpired -> SourceRowStatus(
        text = "服务器可达，但登录凭据已失效，请重新登录",
        tone = SourceRowStatus.Tone.Negative,
    )
    is SourceTestResult.Failed -> SourceRowStatus(
        text = "无法连接：${errorMessage(result.error)}",
        tone = SourceRowStatus.Tone.Negative,
    )
}

/**
 * 音乐源列表的一行：`[✓] 服务器名 / 类型·地址·账号`，右侧可带「测试」与删除。
 * 点整行 = 切换到它（当前源点了没反应，避免无意义重连）。
 */
@Composable
internal fun SourceRow(
    entry: LoginHistoryEntry,
    active: Boolean,
    focuses: SourceRowFocuses,
    modifier: Modifier = Modifier,
    above: SourceRowFocuses? = null,
    below: SourceRowFocuses? = null,
    /** 最后一行的"下一项"：弹窗底部的按钮（添加/清空/关闭）。 */
    bottomFocus: FocusRequester? = null,
    onSelect: () -> Unit,
    onTest: (() -> Unit)? = null,
    testing: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoginActionButton(
            onClick = { if (!active) onSelect() },
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .semantics { contentDescription = "音乐源：${sourceTitle(entry)}" }
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = if (onTest != null) focuses.test else focuses.delete
                    up = above?.body ?: FocusRequester.Cancel
                    down = below?.body ?: bottomFocus ?: FocusRequester.Cancel
                }
                .focusRequester(focuses.body),
            selected = active,
        ) {
            // 胶囊按钮（50% 圆角）左右两端是弧线：文字必须留内边距，
            // 否则首字压在弧上、看着"跑出按钮"（真机反馈）。
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) {
                        Text("▶", color = FnColors.Coral, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        sourceTitle(entry),
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Text("当前", color = FnColors.Coral, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    sourceSubtitle(entry),
                    color = FnColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onTest != null) {
            LoginActionButton(
                // 测试期间保持可聚焦（禁用会把焦点从图里摘掉、跳回第一行），
                // 重入由调用点的"已有测试在跑就不受理"挡住。
                onClick = { if (!testing) onTest() },
                modifier = Modifier
                    .width(112.dp)
                    .height(68.dp)
                    .semantics { contentDescription = "测试音乐源：${sourceTitle(entry)}" }
                    .focusProperties {
                        left = focuses.body
                        right = focuses.delete
                        up = above?.test ?: FocusRequester.Cancel
                        down = below?.test ?: bottomFocus ?: FocusRequester.Cancel
                    }
                    .focusRequester(focuses.test),
            ) {
                Text(
                    if (testing) "测试中…" else "测试",
                    color = if (testing) FnColors.Muted else FnColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (onDelete != null) {
            LoginActionButton(
                onClick = onDelete,
                modifier = Modifier
                    .width(76.dp)
                    .height(68.dp)
                    .semantics { contentDescription = "删除音乐源：${sourceTitle(entry)}" }
                    .focusProperties {
                        left = if (onTest != null) focuses.test else focuses.body
                        right = FocusRequester.Cancel
                        up = above?.delete ?: FocusRequester.Cancel
                        down = below?.delete ?: bottomFocus ?: FocusRequester.Cancel
                    }
                    .focusRequester(focuses.delete),
            ) {
                Text("✕", color = FnColors.Coral, fontSize = 22.sp)
            }
        }
    }
}

/**
 * 音乐源弹窗。两个入口共用：
 * - 「我的」页 / 设置页的**音乐源管理**：能选、能测、能删、能添加（[title] = "音乐源"）；
 * - 登录页的**已有音乐源**（会话过期时一键重登）：只列已有的，不给添加入口
 *   （[onAdd] = null），[title] = "已有音乐源"。
 */
@Composable
internal fun SourcePickerDialog(
    sources: List<LoginHistoryEntry>,
    activeProfileId: String?,
    onDismiss: () -> Unit,
    onSelect: (LoginHistoryEntry) -> Unit,
    title: String = "音乐源",
    statuses: Map<String, SourceRowStatus> = emptyMap(),
    testingProfileId: String? = null,
    onTest: ((LoginHistoryEntry) -> Unit)? = null,
    onDelete: ((LoginHistoryEntry) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    /**
     * 1.4.x 遗留的"最近用过的地址"（只有地址、没有凭据，也没被"保持登录"记成档案）。
     * 点一条只是把地址填回表单，所以这里给它们留一个次级列表，别让老用户白丢这个入口。
     */
    recentServers: List<String> = emptyList(),
    onRecentSelect: ((String) -> Unit)? = null,
) {
    // 删除音乐源二次确认：✕ 只装填待删项，确认后才真正调 onDelete。
    var pendingDelete by remember { mutableStateOf<LoginHistoryEntry?>(null) }
    val confirmCancelFocus = remember { FocusRequester() }
    val rowFocuses = remember(sources) {
        List(sources.size) { SourceRowFocuses(FocusRequester(), FocusRequester(), FocusRequester()) }
    }
    val recentFocuses = remember(recentServers) { List(recentServers.size) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    // 最后一行往下走：先去弹窗底部最左侧那个按钮，再横着走。
    val bottomFocus = when {
        onAdd != null -> addFocus
        onClearAll != null -> clearFocus
        else -> closeFocus
    }
    val legacyVisible = recentServers.isNotEmpty() && onRecentSelect != null
    Dialog(
        onDismissRequest = onDismiss,
        // 平台对话框窗口默认会限制宽度（真机上只有屏宽的四成左右），关掉它自己定宽。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 源列表要放下"服务器名 / 类型 · 地址 · 账号"，给宽一些：屏宽的 0.86，最多 1000dp。
            val dialogWidth = minOf(maxWidth * 0.86f, 1000.dp)
            Column(
                Modifier
                    .width(dialogWidth)
                    // 横屏电视/车机的可视高度很矮，内容必须能滚，否则「关闭」会被切在屏幕外。
                    .heightIn(max = maxHeight * 0.92f)
                    .verticalScroll(rememberScrollState())
                    .background(FnColors.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                if (sources.isEmpty()) {
                    Text(
                        if (legacyVisible) "还没有保存的音乐源" else "还没有添加音乐源",
                        color = FnColors.Muted,
                        fontSize = 15.sp,
                    )
                }
                sources.forEachIndexed { index, entry ->
                    SourceRow(
                        entry = entry,
                        active = entry.id == activeProfileId,
                        focuses = rowFocuses[index],
                        above = rowFocuses.getOrNull(index - 1),
                        below = rowFocuses.getOrNull(index + 1),
                        bottomFocus = if (sources.lastIndex == index) {
                            recentFocuses.firstOrNull() ?: bottomFocus
                        } else {
                            null
                        },
                        onSelect = { onSelect(entry) },
                        onTest = onTest?.let { test -> { test(entry) } },
                        testing = entry.id == testingProfileId,
                        onDelete = onDelete?.let { delete -> { pendingDelete = entry } },
                    )
                    statuses[entry.id]?.let { status -> SourceStatusLine(status) }
                }
                if (legacyVisible && onRecentSelect != null) {
                    Text("最近用过的地址", color = FnColors.Muted, fontSize = 14.sp)
                    recentServers.forEachIndexed { index, address ->
                        LoginActionButton(
                            onClick = { onRecentSelect(address) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics { contentDescription = "最近用过的地址：$address" }
                                .focusProperties {
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    up = rowFocuses.lastOrNull()?.body
                                        ?: recentFocuses.getOrNull(index - 1)
                                        ?: FocusRequester.Cancel
                                    down = recentFocuses.getOrNull(index + 1) ?: bottomFocus
                                }
                                .focusRequester(recentFocuses[index]),
                        ) {
                            Text(
                                address,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                // 底部按钮并排：横屏电视/车机的可视高度很矮，竖着放会把"关闭"挤到屏幕外。
                // 几何与上方 SourceRow 完全对齐（间距同为 8dp，高度与源行主体一致 72dp）：
                //   添加音乐源 = 歌单行的主体列（weight 1f）；关闭 = 测试(112) + 间距(8) + 删除(68) = 188dp。
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAdd != null) {
                        LoginActionButton(
                            onClick = onAdd,
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .semantics { contentDescription = "添加音乐源" }
                                .focusProperties { right = if (onClearAll != null) clearFocus else closeFocus }
                                .focusRequester(addFocus),
                        ) {
                            Text("＋ 添加音乐源", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onClearAll != null) {
                        LoginActionButton(
                            onClick = onClearAll,
                            modifier = Modifier
                                .width(170.dp)
                                .height(72.dp)
                                .semantics { contentDescription = "清空全部音乐源" }
                                .focusProperties {
                                    left = if (onAdd != null) addFocus else FocusRequester.Cancel
                                    right = closeFocus
                                }
                                .focusRequester(clearFocus),
                        ) {
                            Text("清空", color = FnColors.Coral, fontSize = 17.sp)
                        }
                    }
                    LoginActionButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(188.dp)
                            .height(72.dp)
                            .semantics { contentDescription = "关闭" }
                            .focusProperties {
                                left = when {
                                    onClearAll != null -> clearFocus
                                    onAdd != null -> addFocus
                                    else -> FocusRequester.Cancel
                                }
                            }
                            .focusRequester(closeFocus),
                    ) {
                        Text("关闭", color = FnColors.Muted, fontSize = 17.sp)
                    }                }
            }
        }
        // 删除音乐源二次确认（与清空收藏同一套自绘 Dialog 风格：取消键初始聚焦，确认键 Coral）
        pendingDelete?.let { target ->
            Dialog(onDismissRequest = { pendingDelete = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                val cancelFocus = confirmCancelFocus
                LaunchedEffect(target) {
                    yield()
                    runCatching { cancelFocus.requestFocus() }
                }
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(0.9f)
                        .background(FnColors.Surface, RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("删除音乐源", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "将删除「${sourceTitle(target)}」及其登录信息，此操作不可撤销。",
                        color = FnColors.Text,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        LoginActionButton(
                            onClick = { pendingDelete = null },
                            modifier = Modifier
                                .width(132.dp)
                                .height(46.dp)
                                .focusRequester(cancelFocus),
                        ) {
                            Text("取消", fontSize = 15.sp)
                        }
                        LoginActionButton(
                            onClick = {
                                val entry = target
                                pendingDelete = null
                                onDelete?.invoke(entry)
                            },
                            modifier = Modifier
                                .width(132.dp)
                                .height(46.dp),
                            selected = true,
                        ) {
                            Text("删除", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceStatusLine(status: SourceRowStatus) {
    val color = when (status.tone) {
        SourceRowStatus.Tone.Neutral -> FnColors.Muted
        SourceRowStatus.Tone.Positive -> FnColors.Teal
        SourceRowStatus.Tone.Negative -> FnColors.Warning
    }
    Text(
        status.text,
        color = color,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}
