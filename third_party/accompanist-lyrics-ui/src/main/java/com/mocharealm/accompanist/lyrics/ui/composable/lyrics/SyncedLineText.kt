package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    // 活动行不换色也不加粗：切换行时若颜色/字重变化，会先出现白光再加
    // 文本重排抖动；这里让所有行颜色、字重完全一致。
    val mainColor = textColor
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
                    val span = (line.end - line.start).coerceAtLeast(1)
                    val elapsed = ((t - line.start).toFloat() / span).coerceIn(0f, 1f)
                    // 前半段保持行首不滚动（保证开头可读），后半段再平滑滚到行尾。
                    ((elapsed - 0.5f) / 0.5f).coerceIn(0f, 1f)
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
                        .onSizeChanged { containerWidthPx = it.width }
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
