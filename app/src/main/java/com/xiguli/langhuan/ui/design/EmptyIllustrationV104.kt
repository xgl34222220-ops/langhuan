package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What an empty page is about; picks the drawing. */
enum class EmptyArtV104 { SHELF, NOTES, FINISHED }

/**
 * V104 empty-state art: a small hairline drawing in the page's own greys with one accent touch
 * (a book on a shelf, an open book with a quote mark, a closed book with a tick). Flat, no
 * shadows, adapts to light and dark through the UI tokens.
 */
@Composable
fun EmptyIllustrationV104(art: EmptyArtV104, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val line = t.mutedForeground.copy(alpha = 0.55f)
    val fill = t.input
    val accent = t.primary
    Canvas(modifier.size(width = 132.dp, height = 104.dp)) {
        val u = size.width / 132f
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Soft ground disc shared by every drawing.
        drawOval(fill, topLeft = Offset(14f * u, 84f * u), size = Size(104f * u, 14f * u))
        when (art) {
            EmptyArtV104.SHELF -> drawShelf(u, stroke, line, fill, accent)
            EmptyArtV104.NOTES -> drawNotes(u, stroke, line, fill, accent)
            EmptyArtV104.FINISHED -> drawFinished(u, stroke, line, fill, accent)
        }
    }
}

private fun DrawScope.book(x: Float, y: Float, w: Float, h: Float, u: Float, stroke: Stroke, line: Color, body: Color) {
    drawRoundRect(body, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(2f * u))
    drawRoundRect(line, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(2f * u), style = stroke)
    // Two spine bands.
    drawLine(line, Offset(x + 3f * u, y + 8f * u), Offset(x + w - 3f * u, y + 8f * u), stroke.width)
    drawLine(line, Offset(x + 3f * u, y + h - 8f * u), Offset(x + w - 3f * u, y + h - 8f * u), stroke.width)
}

private fun DrawScope.drawShelf(u: Float, stroke: Stroke, line: Color, fill: Color, accent: Color) {
    val base = 88f * u
    book(30f * u, base - 52f * u, 16f * u, 52f * u, u, stroke, line, fill)
    book(48f * u, base - 62f * u, 18f * u, 62f * u, u, stroke, accent, accent.copy(alpha = 0.14f))
    book(68f * u, base - 46f * u, 14f * u, 46f * u, u, stroke, line, fill)
    // One book leaning on the stack.
    rotate(degrees = 18f, pivot = Offset(84f * u, base)) {
        book(84f * u, base - 50f * u, 15f * u, 50f * u, u, stroke, line, fill)
    }
    drawLine(line, Offset(22f * u, base), Offset(110f * u, base), stroke.width, cap = StrokeCap.Round)
    // A small sparkle above the stack.
    val c = Offset(104f * u, 22f * u)
    drawLine(accent, Offset(c.x, c.y - 5f * u), Offset(c.x, c.y + 5f * u), stroke.width, cap = StrokeCap.Round)
    drawLine(accent, Offset(c.x - 5f * u, c.y), Offset(c.x + 5f * u, c.y), stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawNotes(u: Float, stroke: Stroke, line: Color, fill: Color, accent: Color) {
    // An open book: two pages meeting at a spine.
    val left = Path().apply {
        moveTo(66f * u, 34f * u)
        cubicTo(54f * u, 26f * u, 38f * u, 26f * u, 24f * u, 30f * u)
        lineTo(24f * u, 82f * u)
        cubicTo(38f * u, 78f * u, 54f * u, 78f * u, 66f * u, 86f * u)
        close()
    }
    val right = Path().apply {
        moveTo(66f * u, 34f * u)
        cubicTo(78f * u, 26f * u, 94f * u, 26f * u, 108f * u, 30f * u)
        lineTo(108f * u, 82f * u)
        cubicTo(94f * u, 78f * u, 78f * u, 78f * u, 66f * u, 86f * u)
        close()
    }
    drawPath(left, fill)
    drawPath(right, fill)
    drawPath(left, line, style = stroke)
    drawPath(right, line, style = stroke)
    // Text lines on the left page, a highlighted line on the right.
    listOf(42f, 52f, 62f).forEach { y -> drawLine(line, Offset(32f * u, y * u), Offset(58f * u, y * u), stroke.width, cap = StrokeCap.Round) }
    drawLine(accent.copy(alpha = 0.30f), Offset(74f * u, 52f * u), Offset(100f * u, 52f * u), 5f * u, cap = StrokeCap.Round)
    drawLine(line, Offset(74f * u, 62f * u), Offset(96f * u, 62f * u), stroke.width, cap = StrokeCap.Round)
    // Quote mark above.
    drawCircle(accent, radius = 3f * u, center = Offset(60f * u, 14f * u))
    drawCircle(accent, radius = 3f * u, center = Offset(70f * u, 14f * u))
    drawLine(accent, Offset(58f * u, 15f * u), Offset(56f * u, 21f * u), stroke.width, cap = StrokeCap.Round)
    drawLine(accent, Offset(68f * u, 15f * u), Offset(66f * u, 21f * u), stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawFinished(u: Float, stroke: Stroke, line: Color, fill: Color, accent: Color) {
    // A closed book standing slightly turned, with a tick badge.
    book(42f * u, 22f * u, 44f * u, 64f * u, u, stroke, line, fill)
    drawLine(line, Offset(50f * u, 22f * u), Offset(50f * u, 86f * u), stroke.width)
    drawLine(line, Offset(58f * u, 44f * u), Offset(78f * u, 44f * u), stroke.width, cap = StrokeCap.Round)
    drawLine(line, Offset(58f * u, 52f * u), Offset(72f * u, 52f * u), stroke.width, cap = StrokeCap.Round)
    val c = Offset(88f * u, 26f * u)
    drawCircle(accent, radius = 11f * u, center = c)
    val tick = Path().apply {
        moveTo(c.x - 5f * u, c.y)
        lineTo(c.x - 1.5f * u, c.y + 3.5f * u)
        lineTo(c.x + 5f * u, c.y - 3.5f * u)
    }
    drawPath(tick, Color.White, style = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Illustration, a title and a short line, centred. */
@Composable
fun EmptyStateV104(
    art: EmptyArtV104,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustrationV104(art)
        Text(
            title,
            modifier = Modifier.padding(top = 20.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = t.foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = t.mutedForeground,
            textAlign = TextAlign.Center,
        )
        action?.let {
            androidx.compose.foundation.layout.Box(Modifier.padding(top = 24.dp)) { it() }
        }
    }
}
