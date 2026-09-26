package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mocharealm.gaze.capsule.ContinuousRoundedRectangle

@Composable
fun LyricsLineItem(
    isFocused: Boolean,
    isRightAligned: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
    blurRadius: () -> Float,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.4f,
    blendMode: BlendMode = BlendMode.SrcOver,
    isInteractive: Boolean = true,
    content: @Composable () -> Unit
) {
    // 直接取目标值，不做过渡动画：原实现让刚唱完的一行先以全白高亮、再淡出
    // 并缩放回 0.98，产生“白闪 + 抖动”。切换瞬间即与其他行完全一致。
    val scaleState = 1f
    val alphaState = if (isFocused) activeAlpha else inactiveAlpha

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scaleState
                scaleY = scaleState
                alpha = alphaState
                transformOrigin = TransformOrigin(if (isRightAligned) 1f else 0f, 1f)
                this.blendMode = blendMode
                compositingStrategy = CompositingStrategy.Offscreen

                val radius = blurRadius()
                if (radius > 0f) {
                    renderEffect = BlurEffect(
                        radiusX = radius,
                        radiusY = radius,
                        edgeTreatment = TileMode.Clamp
                    )
                }
            }
            .then(
                if (isInteractive) Modifier.clip(ContinuousRoundedRectangle(8.dp))
                    .combinedClickable(
                        onClick = onLineClicked,
                        onLongClick = onLinePressed
                    )
                else Modifier
            )

    ) {
        content()
    }
}
