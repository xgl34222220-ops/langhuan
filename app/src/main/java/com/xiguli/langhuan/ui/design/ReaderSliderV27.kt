package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/** Stable 4dp track and round thumb across Material versions and OEM themes. */
@Composable
internal fun ReaderSliderV27(
    label: String, value: Float, onValueChange: (Float) -> Unit,
    tokens: LanghuanTokensV4, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val update by rememberUpdatedState(onValueChange)
    val safeValue = value.coerceIn(valueRange)
    val fraction = (safeValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    Canvas(modifier.fillMaxWidth().height(48.dp)
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(safeValue, valueRange)
            setProgress { update(it.coerceIn(valueRange)); true }
        }
        .pointerInput(valueRange) {
            detectTapGestures { point ->
                val inset = 10.dp.toPx()
                val f = ((point.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                update(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
            }
        }
        .pointerInput(valueRange) {
            detectHorizontalDragGestures { change, _ ->
                change.consume()
                val inset = 10.dp.toPx()
                val f = ((change.position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                update(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
            }
        },
    ) {
        val inset = 10.dp.toPx()
        val center = size.height / 2f
        val start = Offset(inset, center)
        val end = Offset(size.width - inset, center)
        val thumb = Offset(start.x + (end.x - start.x) * fraction, center)
        drawLine(tokens.track, start, end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        if (fraction > 0f) drawLine(tokens.primary, start, thumb, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tokens.primary, radius = 8.dp.toPx(), center = thumb)
    }
}
