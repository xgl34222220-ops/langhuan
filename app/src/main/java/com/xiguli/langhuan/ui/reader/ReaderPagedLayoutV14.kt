package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reader V14 page surface.
 *
 * The first page and ordinary pages deliberately use different vertical structures:
 * - chapter page: compact chapter heading, no duplicate running header;
 * - ordinary page: quiet running chapter header;
 * - every page: one lightweight footer with time + page/book progress.
 *
 * Paragraph spacing is rendered as real dp space instead of synthetic blank text lines, so the
 * visible layout matches the measured paginator much more closely.
 */
@Composable
internal fun ReaderPagedLayoutV14(
    pageText: String,
    contentPage: Int,
    pageCount: Int,
    displayTitle: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    background: Color,
    foreground: Color,
    secondary: Color,
    overallFraction: Float,
    onToggleChrome: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .clickable(onClick = onToggleChrome)
            .padding(horizontal = sidePadding.dp)
            .padding(top = if (contentPage == 0) 14.dp else 10.dp, bottom = 8.dp),
    ) {
        if (contentPage == 0) {
            Text(
                displayTitle,
                fontSize = (fontSize + 3f).sp,
                lineHeight = (fontSize + 8f).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = family,
                color = foreground,
            )
            Spacer(Modifier.height(14.dp))
        } else {
            Text(
                displayTitle,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = family,
                color = secondary.copy(alpha = .48f),
            )
            Spacer(Modifier.height(12.dp))
        }

        ReaderPageParagraphsV14(
            text = pageText,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            firstLineIndent = firstLineIndent,
            family = family,
            color = foreground,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                style = MaterialTheme.typography.labelSmall,
                color = secondary.copy(alpha = .58f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${contentPage + 1}/${pageCount.coerceAtLeast(1)}  ·  ${(overallFraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = secondary.copy(alpha = .64f),
            )
        }
    }
}

@Composable
private fun ReaderPageParagraphsV14(
    text: String,
    modifier: Modifier,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val style = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineFactor).sp,
        fontFamily = family,
        color = color,
        textIndent = TextIndent(firstLine = if (firstLineIndent) (fontSize * 2f).sp else 0.sp),
    )
    Column(modifier.fillMaxWidth()) {
        paragraphs.forEachIndexed { index, paragraph ->
            Text(paragraph, modifier = Modifier.fillMaxWidth(), style = style)
            if (index < paragraphs.lastIndex) {
                Spacer(Modifier.height(paragraphSpacing.coerceIn(0f, 24f).dp))
            }
        }
    }
}
