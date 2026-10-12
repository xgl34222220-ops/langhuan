package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.util.Locale
import kotlin.math.roundToInt

/** V101 「字号」/「行段」: the menu closes and pill sliders float over the live page. */
internal enum class ReaderQuickAdjustV101 { SIZE, SPACING }

/** Rounds [raw] to [step] inside [range]. */
internal fun readerSnapV101(raw: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    val clamped = raw.coerceIn(range.start, range.endInclusive)
    if (step <= 0f) return clamped
    val steps = ((clamped - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}

@Composable
internal fun ReaderQuickAdjustOverlayV101(
    adjust: ReaderQuickAdjustV101,
    settings: ReaderSettingsV30,
    theme: ReaderThemeV30,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping the page outside the pills closes them; the page stays visible behind.
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .testTag("reader_quick_adjust"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 36.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (adjust) {
                ReaderQuickAdjustV101.SIZE -> {
                    ReaderPillSliderV101(
                        label = "字号 ${settings.fontSize.roundToInt()}",
                        value = settings.fontSize,
                        range = ReaderSettingsV30.MIN_FONT..ReaderSettingsV30.MAX_FONT,
                        step = 1f,
                        theme = theme,
                        onValue = { settings.fontSize = it },
                    )
                    ReaderPillSliderV101(
                        label = "字距 " + String.format(Locale.US, "%.2f", settings.letterSpacing),
                        value = settings.letterSpacing,
                        range = ReaderSettingsV30.MIN_LETTER_SPACING..ReaderSettingsV30.MAX_LETTER_SPACING,
                        step = 0.01f,
                        theme = theme,
                        onValue = { settings.letterSpacing = it },
                    )
                }
                ReaderQuickAdjustV101.SPACING -> {
                    ReaderPillSliderV101(
                        label = "行间距 " + String.format(Locale.US, "%.1f", settings.lineFactor),
                        value = settings.lineFactor,
                        range = ReaderSettingsV30.MIN_LINE..ReaderSettingsV30.MAX_LINE,
                        step = 0.05f,
                        theme = theme,
                        onValue = { settings.lineFactor = it },
                    )
                    ReaderPillSliderV101(
                        label = "段间距 ${settings.paragraphSpacing.roundToInt()}",
                        value = settings.paragraphSpacing,
                        range = ReaderSettingsV30.MIN_PARAGRAPH..ReaderSettingsV30.MAX_PARAGRAPH,
                        step = 1f,
                        theme = theme,
                        onValue = { settings.paragraphSpacing = it },
                    )
                }
            }
        }
    }
}

/** A full-width pill whose filled part is the value, with its label inside (reference style). */
@Composable
internal fun ReaderPillSliderV101(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    theme: ReaderThemeV30,
    onValue: (Float) -> Unit,
) {
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val track = if (theme.dark) Color(0xFF2A2A2D) else Color(0xFFF1F1F3)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(track)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target -> onValue(readerSnapV101(target, range, step)); true }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullWidth = maxWidth
        fun valueAt(x: Float) = readerSnapV101(range.start + (x / widthPx) * (range.endInclusive - range.start), range, step)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(range, step, widthPx) {
                    detectTapGestures(onTap = { onValue(valueAt(it.x)) })
                }
                .pointerInput(range, step, widthPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onValue(valueAt(change.position.x))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fullWidth * fraction.coerceAtLeast(0.30f))
                    .clip(CircleShape)
                    .background(theme.accent),
            )
            Text(
                text = label,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                  定位                                       */
/* -------------------------------------------------------------------------- */

/** Chapter index and in-chapter fraction for a whole-book [percent] (0..1). */
internal fun readerLocateTargetV101(percent: Float, chapterCount: Int): Pair<Int, Float> {
    if (chapterCount <= 0) return 0 to 0f
    val position = percent.coerceIn(0f, 1f) * chapterCount
    val index = position.toInt().coerceIn(0, chapterCount - 1)
    return index to (position - index).coerceIn(0f, 1f)
}

@Composable
internal fun ReaderLocatePanelV101(
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int,
    onLocate: (Int, Float) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val inside = if (pageCount > 0) pageIndex.toFloat() / pageCount.toFloat() else 0f
    val start = if (chapters.isEmpty()) 0f else ((chapterIndex + inside) / chapters.size).coerceIn(0f, 1f)
    var percent by remember(chapterIndex, pageIndex) { mutableFloatStateOf(start) }
    var chapterInput by remember { mutableStateOf("") }
    val (targetIndex, targetFraction) = readerLocateTargetV101(percent, chapters.size)
    val target = chapters.getOrNull(targetIndex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4)
            .padding(bottom = t.space4)
            .testTag("reader_locate_panel"),
    ) {
        ReaderSubPageHeaderV101(title = "定位", onBack = onBack)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.US, "%.1f", percent * 100f),
                style = MaterialTheme.typography.displaySmall,
                color = t.foreground,
                fontWeight = FontWeight.Bold,
            )
            Text("%", modifier = Modifier.padding(start = 2.dp, bottom = 6.dp), color = t.mutedForeground)
        }
        Text(
            text = target?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) } ?: "暂无章节",
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
            maxLines = 1,
        )
        Slider(
            value = percent,
            onValueChange = { percent = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "全书位置" },
            colors = SliderDefaults.colors(
                thumbColor = t.primary,
                activeTrackColor = t.primary,
                inactiveTrackColor = t.foreground.copy(alpha = 0.10f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = chapters.isNotEmpty(),
                onClick = { onLocate(targetIndex, targetFraction) },
            ) { Text("跳到 ${String.format(Locale.US, "%.1f", percent * 100f)}%") }
        }
        Spacer(Modifier.height(t.space2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = chapterInput,
                onValueChange = { input -> chapterInput = input.filter(Char::isDigit).take(6) },
                modifier = Modifier.weight(1f).testTag("reader_locate_chapter"),
                singleLine = true,
                label = { Text("章节序号（1 - ${chapters.size}）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(t.radiusMd),
            )
            Spacer(Modifier.width(t.space2))
            val number = chapterInput.toIntOrNull()
            TextButton(
                enabled = number != null && number in 1..chapters.size,
                onClick = { number?.let { onLocate(it - 1, 0f) } },
            ) { Text("跳转") }
        }
    }
}

/** Sub-page header used by V101 settings pages: back chevron, centred title, optional action. */
@Composable
internal fun ReaderSubPageHeaderV101(
    title: String,
    onBack: () -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回" }
                .padding(10.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                contentDescription = null,
                modifier = Modifier.width(20.dp),
                tint = LocalLanghuanUiTokens.current.foreground,
            )
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            if (titleContent != null) titleContent()
            else Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalLanghuanUiTokens.current.foreground,
            )
        }
        if (action != null) Box(modifier = Modifier.align(Alignment.CenterEnd)) { action() }
    }
}
