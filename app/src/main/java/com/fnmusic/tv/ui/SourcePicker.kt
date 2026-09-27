package com.fnmusic.tv.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.tv.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fnmusic.tv.core.data.repository.LoginHistoryEntry
import com.fnmusic.tv.core.model.ServerKind
import com.fnmusic.tv.ui.FnColors

/**
 * 音乐源（一台服务器 + 一个账号）在界面上的展示名。
 * 术语选择：**音乐源**而不是"播放源" —— 后者容易被理解成"同一首歌的直连/转码线路"，
 * 而这个概念管的是"音乐从哪台服务器来"。
 */
internal fun sourceTitle(entry: LoginHistoryEntry): String =
    entry.serverName?.takeIf(String::isNotBlank) ?: sourceKindLabel(entry.kind)

/** 源类型的中文名（给用户看）。 */
internal fun sourceKindLabel(kind: ServerKind): String = when (kind) {
    ServerKind.FnOs -> "飞牛音乐"
    ServerKind.Jellyfin -> "Jellyfin"
}

internal fun sourceSubtitle(entry: LoginHistoryEntry): String =
    "${sourceKindLabel(entry.kind)} · ${entry.server} · ${entry.username}"

/**
 * 音乐源列表的一行：`[✓] 服务器名 / 类型·地址·账号`，右侧可带删除。
 * 点整行 = 切换到它（当前源点了没反应，避免无意义重连）。
 */
@Composable
internal fun SourceRow(
    entry: LoginHistoryEntry,
    active: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteFocus: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoginActionButton(
            onClick = { if (!active) onSelect() },
            modifier = Modifier
                .weight(1f)
                .height(80.dp)
                .semantics { contentDescription = "音乐源：${sourceTitle(entry)}" }
                .focusProperties {
                    right = deleteFocus ?: FocusRequester.Cancel
                    up = upFocus ?: FocusRequester.Cancel
                    down = downFocus ?: FocusRequester.Cancel
                },
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
        if (onDelete != null) {
            LoginActionButton(
                onClick = onDelete,
                modifier = Modifier
                    .height(76.dp)
                    .width(76.dp)
                    .semantics { contentDescription = "删除音乐源：${sourceTitle(entry)}" }
                    .then(if (deleteFocus != null) Modifier.focusRequester(deleteFocus) else Modifier),
            ) {
                Text("✕", color = FnColors.Coral, fontSize = 22.sp)
            }
        }
    }
}

/**
 * 音乐源弹窗：[onSelect] 直接切换（成功前不动当前会话）、[onAdd] 去登录页添加、
 * [onDelete] 删除该源（服务端数据不受影响，只是不再记住它）。
 */
@Composable
internal fun SourcePickerDialog(
    sources: List<LoginHistoryEntry>,
    activeProfileId: String?,
    onDismiss: () -> Unit,
    onSelect: (LoginHistoryEntry) -> Unit,
    onDelete: (LoginHistoryEntry) -> Unit,
    onAdd: () -> Unit,
    switchingProfileId: String? = null,
) {
    val rowFocuses = remember(sources) { List(sources.size) { FocusRequester() } }
    val deleteFocuses = remember(sources) { List(sources.size) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
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
            Text("音乐源", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            if (sources.isEmpty()) {
                Text("还没有添加音乐源", color = FnColors.Muted, fontSize = 15.sp)
            }
            sources.forEachIndexed { index, entry ->
                SourceRow(
                    entry = entry,
                    active = entry.id == activeProfileId,
                    onSelect = { onSelect(entry) },
                    onDelete = { onDelete(entry) },
                    deleteFocus = deleteFocuses[index],
                    upFocus = rowFocuses.getOrNull(index - 1),
                    downFocus = rowFocuses.getOrNull(index + 1) ?: addFocus,
                )
                if (switchingProfileId == entry.id) {
                    Text("正在切换…", color = FnColors.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            // 添加与关闭并排：横屏电视/车机的可视高度很矮，竖着放会把"关闭"挤到屏幕外。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoginActionButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .semantics { contentDescription = "添加音乐源" }
                        .focusProperties { right = closeFocus }
                        .focusRequester(addFocus),
                ) {
                    Text("＋ 添加音乐源", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                LoginActionButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(150.dp)
                        .height(62.dp)
                        .semantics { contentDescription = "关闭" }
                        .focusProperties { left = addFocus }
                        .focusRequester(closeFocus),
                ) {
                    Text("关闭", color = FnColors.Muted, fontSize = 17.sp)
                }
            }
            }
        }
    }
}
