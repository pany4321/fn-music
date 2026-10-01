package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

@Composable
fun SyncedLineText(
    line: SyncedLine,
    isLineRtl: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    isActive: Boolean = false,
    activeTextColor: Color = Color.Unspecified,
    currentPositionMs: (() -> Int)? = null,
) {
    // 活动行使用按背景对比度算出的强调色；字重保持不变——此前的白闪与抖动
    // 来自透明度/缩放过渡与字重变化，颜色瞬时切换不会引起重排。
    val mainColor = if (isActive && activeTextColor != Color.Unspecified) activeTextColor else textColor
    val mainStyle = textStyle

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = if (isLineRtl) Alignment.End else Alignment.Start
    ) {
        if (isActive && currentPositionMs != null) {
            // 活动行：单行不换行，随演唱进度平滑向左滚动露出完整内容
            var contentWidthPx by remember { mutableIntStateOf(0) }
            var containerWidthPx by remember { mutableIntStateOf(0) }
            val overflowPx = (contentWidthPx - containerWidthPx).coerceAtLeast(0)
            val progress = remember(line, currentPositionMs) {
                derivedStateOf {
                    val t = currentPositionMs.invoke()
                    // 设计意图（真机定稿）：起始居左**固定停 3 秒** → 单次左移（约 2 秒；本行
                    // 剩余不足 2 秒时按剩余时长压缩，保证行尾一定滚到）→ 行尾完整显示并
                    // 停留到本行结束。此前按"行时长的百分比"算驻留期，短句驻留被压到
                    // 约 1 秒，首字刚显示就被左移裁掉、滚动中段看似居中。
                    val elapsed = (t - line.start).coerceAtLeast(0)
                    val span = (line.end - line.start).coerceAtLeast(1)
                    val scrollStart = 3_000f
                    val scrollDuration = (span - scrollStart).coerceIn(1_000f, 2_000f)
                    ((elapsed - scrollStart) / scrollDuration).coerceIn(0f, 1f)
                }
            }
            val lineScrollX by animateFloatAsState(
                targetValue = -overflowPx * progress.value,
                animationSpec = tween(200, easing = LinearEasing),
                label = "lyricLineHScroll",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    // 容器宽必须量在外层 Box 上：此前挂在 Text 修饰链上，量到的是文本自身宽度——
                    // 而 requiredWidth 已把该节点强制成完整内容宽，两者相等导致 overflow 恒为 0，
                    // 左移从未生效（表现为超长行静止居中、两端被裁）。
                    .onSizeChanged { containerWidthPx = it.width }
            ) {
                Text(
                    text = line.content,
                    style = mainStyle,
                    color = mainColor,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { result -> contentWidthPx = result.size.width },
                    modifier = Modifier
                        // requiredWidth(IntrinsicSize.Max)：文本按"完整内容宽"测量，
                        // 突破父容器的 max 约束——超长行的溢出量才量得出来
                        // （逐字行是自绘 Canvas 主动放宽画布，所以没这个问题）。
                        .requiredWidth(IntrinsicSize.Max)
                        .graphicsLayer { translationX = lineScrollX },
                )
            }
        } else {
            // 非活动行：单行省略
            Text(
                text = line.content,
                style = mainStyle,
                color = mainColor,
                textAlign = if (isLineRtl) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTranslation) {
            line.translation?.let {
                Text(
                    text = it,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = if (isLineRtl) TextAlign.End else TextAlign.Start
                )
            }
        }
    }
}
