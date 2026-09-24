package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
) {
    val mainColor = if (isActive && activeTextColor != Color.Unspecified) activeTextColor else textColor
    val mainStyle = if (isActive) textStyle.copy(fontWeight = FontWeight.Black) else textStyle
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = if (isLineRtl) Alignment.End else Alignment.Start
    ) {
        // 长歌词不自动换行：单行显示，超宽时可左右滚动
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = line.content,
                style = mainStyle,
                color = mainColor,
                textAlign = if (isLineRtl) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (showTranslation) {
            line.translation?.let {
                Text(
                    text = it,
                    color = if (isActive && activeTextColor != Color.Unspecified)
                        activeTextColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.6f),
                    textAlign = if (isLineRtl) TextAlign.End else TextAlign.Start
                )
            }
        }
    }
}

