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
 * Frozen reader page surface. V16 keeps the historical function name to avoid another route/version
 * wrapper, but measurement and rendering now share the same Compose styles and spacing constants.
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
    indentFirstParagraph: Boolean,
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
            .padding(top = if (contentPage == 0) 10.dp else 8.dp, bottom = 4.dp),
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
            Spacer(Modifier.height(10.dp))
        } else {
            Text(
                displayTitle,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = family,
                color = secondary.copy(alpha = .44f),
            )
            Spacer(Modifier.height(10.dp))
        }

        ReaderPageParagraphsV16(
            text = pageText,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            firstLineIndent = firstLineIndent,
            indentFirstParagraph = indentFirstParagraph,
            family = family,
            color = foreground,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = secondary.copy(alpha = .54f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${contentPage + 1}/${pageCount.coerceAtLeast(1)}  ·  ${(overallFraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
                textAlign = TextAlign.End,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = secondary.copy(alpha = .58f),
            )
        }
    }
}

@Composable
private fun ReaderPageParagraphsV16(
    text: String,
    modifier: Modifier,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    Column(modifier.fillMaxWidth()) {
        paragraphs.forEachIndexed { index, paragraph ->
            val shouldIndent = firstLineIndent && (index > 0 || indentFirstParagraph)
            Text(
                paragraph,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                    fontFamily = family,
                    color = color,
                    textIndent = TextIndent(firstLine = if (shouldIndent) (fontSize * 2f).sp else 0.sp),
                ),
            )
            if (index < paragraphs.lastIndex) {
                Spacer(Modifier.height(paragraphSpacing.coerceIn(0f, 24f).dp))
            }
        }
    }
}
