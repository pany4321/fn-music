package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
            // 活动行：单行不换行，随演唱进度平滑向左滚动露出完整内容。
            // 绘制采用与逐字行（KaraokeLineText）相同的自绘 Canvas 方案：
            // TextMeasurer 无约束测量出完整内容宽，drawText 显式从 (0,0) 起绘——
            // 不经过 Text 的任何对齐/测量默认值，超长行首字从第一帧起必然完整可见。
            val textMeasurer = rememberTextMeasurer()
            val measuredText = remember(line.content, mainStyle) {
                textMeasurer.measure(
                    text = line.content,
                    style = mainStyle,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            val contentWidthPx = measuredText.size.width.toFloat()
            var containerWidthPx by remember { mutableIntStateOf(0) }
            val overflowPx = (contentWidthPx - containerWidthPx).coerceAtLeast(0f)
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
                    .onSizeChanged { containerWidthPx = it.width }
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { measuredText.size.height.toDp() })
                        .graphicsLayer { translationX = lineScrollX }
                ) {
                    drawText(
                        textLayoutResult = measuredText,
                        color = mainColor,
                        topLeft = Offset.Zero,
                    )
                }
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
