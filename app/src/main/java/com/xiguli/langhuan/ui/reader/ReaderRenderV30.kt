package com.xiguli.langhuan.ui

import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius

/** Page palette plus the colours of the reading menu that floats above it. */
internal data class ReaderThemeV30(
    val key: String,
    val name: String,
    val page: Color,
    val text: Color,
    val secondary: Color,
    val dark: Boolean = false,
) {
    val sheet: Color get() = if (dark) Color(0xFF1F1F21) else Color(0xFFFFFFFF)
    val sheetText: Color get() = if (dark) Color(0xFFE8E8EA) else Color(0xFF1C1C1E)
    val sheetMuted: Color get() = if (dark) Color(0xFF8C8C92) else Color(0xFF8E8E93)
    val sheetDivider: Color get() = if (dark) Color(0xFF2E2E31) else Color(0xFFEFEFF1)
    val sheetTile: Color get() = if (dark) Color(0xFF2A2A2D) else Color(0xFFF5F5F7)
    val accent: Color get() = if (dark) Color(0xFF5B9CFF) else Color(0xFF2F7BF6)
}

internal val READER_THEMES_V30 = listOf(
    ReaderThemeV30("paper", "纸白", Color(0xFFF7F5EF), Color(0xFF1E1D1B), Color(0xFF9A968C)),
    ReaderThemeV30("tea", "番茄", Color(0xFFDDD7C1), Color(0xFF1C1A16), Color(0xFF8C8672)),
    ReaderThemeV30("sheep", "羊皮", Color(0xFFEBDDBF), Color(0xFF3A2E1F), Color(0xFF9A8763)),
    ReaderThemeV30("green", "青叶", Color(0xFFD3E4CF), Color(0xFF1D2A1C), Color(0xFF708069)),
    ReaderThemeV30("langhuan", "雾蓝", Color(0xFFE3E8EF), Color(0xFF1C2129), Color(0xFF7D8694)),
    ReaderThemeV30("pink", "樱粉", Color(0xFFF4E4E1), Color(0xFF2E2121), Color(0xFF9E8581)),
    ReaderThemeV30("white", "纯白", Color(0xFFFFFFFF), Color(0xFF151515), Color(0xFF9B9B9B)),
    ReaderThemeV30("night", "夜间", Color(0xFF151516), Color(0xFFA8A8AA), Color(0xFF5E5E62), dark = true),
)

internal fun readerThemeV30(key: String): ReaderThemeV30 =
    READER_THEMES_V30.firstOrNull { it.key == key } ?: READER_THEMES_V30.first()

/**
 * Absolute page geometry in px. Pagination uses [bodyWidth]/[bodyHeight]; the renderer uses the
 * same object, so body lines always land exactly where they were measured.
 */
internal data class ReaderGeometryV30(
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val bodyTop: Float,
    val bodyBottom: Float,
    val headerBaseline: Float,
    val footerBaseline: Float,
) {
    val bodyWidth: Float get() = right - left
    val bodyHeight: Float get() = bodyBottom - bodyTop

    companion object {
        fun of(
            width: Float,
            height: Float,
            density: Float,
            topInset: Float,
            bottomInset: Float,
            sidePaddingDp: Float,
        ): ReaderGeometryV30 {
            val side = sidePaddingDp * density
            val top = topInset.coerceAtLeast(8f * density)
            val bottom = bottomInset.coerceAtLeast(6f * density)
            return ReaderGeometryV30(
                width = width,
                height = height,
                left = side,
                right = width - side,
                headerBaseline = top + 16f * density,
                bodyTop = top + 32f * density,
                bodyBottom = height - bottom - 30f * density,
                footerBaseline = height - bottom - 10f * density,
            )
        }
    }
}

/** Info painted in the page chrome (header/footer). */
internal data class ReaderChromeInfoV30(
    val chapterTitle: String,
    val pageLabel: String,
    val progressLabel: String,
    val time: String,
    val battery: Int,
    val showTimeBattery: Boolean,
    val bookmarked: Boolean,
)

/** Paints are created once per typography/theme and reused for every frame. */
internal class ReaderPaintsV30(
    val body: TextPaint,
    val title: TextPaint,
    val chrome: TextPaint,
)

internal fun readerPaintsV30(
    spec: ReaderTypeSpecV30,
    theme: ReaderThemeV30,
    chromeSizePx: Float,
): ReaderPaintsV30 {
    val body = readerTextPaintV30(spec.fontSizePx, spec.fontKey, spec.weight, spec.letterSpacingEm).apply {
        color = theme.text.toArgb()
    }
    val title = readerTextPaintV30(spec.titleSizePx, spec.fontKey, 700, spec.letterSpacingEm).apply {
        color = theme.text.toArgb()
    }
    val chrome = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = chromeSizePx
        color = theme.secondary.toArgb()
        isSubpixelText = true
    }
    return ReaderPaintsV30(body, title, chrome)
}

/** Draws body lines only, relative to the given origin. Shared by paged and scroll modes. */
internal fun DrawScope.drawReaderLinesV30(
    lines: List<ReaderLineV30>,
    originX: Float,
    originY: Float,
    paints: ReaderPaintsV30,
) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        for (line in lines) {
            val paint = if (line.title) paints.title else paints.body
            val y = originY + line.baseline
            val units = line.units
            val xs = line.xs
            if (units != null && xs != null) {
                for (k in units.indices) native.drawText(units[k], originX + xs[k], y, paint)
            } else {
                native.drawText(line.text, originX, y, paint)
            }
        }
    }
}

/** Full page: background, header, body lines and footer. */
internal fun DrawScope.drawReaderPageV30(
    page: ReaderPageV30?,
    geometry: ReaderGeometryV30,
    theme: ReaderThemeV30,
    paints: ReaderPaintsV30,
    info: ReaderChromeInfoV30,
    placeholder: String? = null,
) {
    drawRect(theme.page)
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val chrome = paints.chrome
        // Header: chapter title, ellipsized by hand to keep it on one line.
        val maxHeader = geometry.bodyWidth - if (info.bookmarked) 24f * density else 0f
        native.drawText(ellipsizeV30(info.chapterTitle, chrome, maxHeader), geometry.left, geometry.headerBaseline, chrome)
        // Footer: time + battery on the left, page + progress on the right.
        if (info.showTimeBattery) {
            native.drawText(info.time, geometry.left, geometry.footerBaseline, chrome)
        }
        val rightLabel = if (info.pageLabel.isBlank()) info.progressLabel else "${info.pageLabel}   ${info.progressLabel}"
        native.drawText(rightLabel, geometry.right - chrome.measureText(rightLabel), geometry.footerBaseline, chrome)
        if (info.showTimeBattery) {
            val timeWidth = chrome.measureText(info.time)
            drawBatteryV30(geometry.left + timeWidth + 6f * density, geometry.footerBaseline, chrome.textSize, info.battery, theme.secondary)
        }
    }
    if (info.bookmarked) {
        val w = 12f * density
        val h = 20f * density
        val x = geometry.right - w
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x, 0f)
            lineTo(x + w, 0f)
            lineTo(x + w, h)
            lineTo(x + w / 2f, h - 5f * density)
            lineTo(x, h)
            close()
        }
        drawPath(path, theme.text.copy(alpha = .28f))
    }
    if (page != null) {
        drawReaderLinesV30(page.lines, geometry.left, geometry.bodyTop, paints)
    } else if (placeholder != null) {
        drawIntoCanvas { canvas ->
            val paint = paints.chrome
            val w = paint.measureText(placeholder)
            canvas.nativeCanvas.drawText(placeholder, (geometry.width - w) / 2f, geometry.height / 2f, paint)
        }
    }
}

private fun DrawScope.drawBatteryV30(x: Float, baseline: Float, textSize: Float, level: Int, color: Color) {
    val h = textSize * .72f
    val w = h * 1.9f
    val top = baseline - h - textSize * .04f
    val stroke = 1.1f * density
    drawRoundRect(
        color = color,
        topLeft = Offset(x, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(h * .22f),
        style = Stroke(stroke),
    )
    val fill = ((w - stroke * 4f) * (level.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(x + stroke * 2f, top + stroke * 2f),
        size = Size(fill, h - stroke * 4f),
        cornerRadius = CornerRadius(h * .1f),
    )
    drawRect(color, topLeft = Offset(x + w + stroke * .5f, top + h * .3f), size = Size(stroke * 1.6f, h * .4f))
}

private fun ellipsizeV30(text: String, paint: TextPaint, max: Float): String {
    if (paint.measureText(text) <= max) return text
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > max) end--
    return text.substring(0, end) + "…"
}

/**
 * Draws one frame of a page turn.
 *
 * [top] is the page being turned away (to the left), [bottom] the page being revealed and
 * [progress] runs from 0 (top fully visible) to 1 (turn finished). Forward turns pass
 * current/next with the finger progress; backward turns pass previous/current with 1 - progress,
 * so every mode only has to implement one direction.
 */
internal fun DrawScope.drawReaderTurnV30(
    mode: ReaderTurnModeV30,
    progress: Float,
    drawTop: DrawScope.() -> Unit,
    drawBottom: DrawScope.() -> Unit,
    theme: ReaderThemeV30,
) {
    val w = size.width
    val h = size.height
    val q = progress.coerceIn(0f, 1f)
    when (mode) {
        ReaderTurnModeV30.SLIDE, ReaderTurnModeV30.NONE, ReaderTurnModeV30.SCROLL -> {
            translate(left = -q * w) { drawTop() }
            translate(left = w - q * w) { drawBottom() }
        }

        ReaderTurnModeV30.COVER -> {
            drawBottom()
            val edge = w - q * w
            translate(left = -q * w) { drawTop() }
            if (q > 0f && q < 1f) {
                val shadow = 18f * density
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .20f * (1f - q * .6f)), Color.Transparent),
                        startX = edge,
                        endX = edge + shadow,
                    ),
                    topLeft = Offset(edge, 0f),
                    size = Size(shadow, h),
                )
            }
        }

        ReaderTurnModeV30.SIMULATION -> {
            // A vertical paper fold. The lifted part of the top page [fold, w] is mirrored onto
            // [flap, fold], exactly like a sheet folded along x = fold.
            val fold = w * (1f - q)
            val flap = w * (1f - 2f * q)
            clipRect(left = fold, top = 0f, right = w, bottom = h) { drawBottom() }
            // Shadow cast by the flap onto the revealed page.
            val cast = (26f * density) * (1f - q * .5f)
            if (q > 0f && q < 1f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .26f), Color.Transparent),
                        startX = fold,
                        endX = fold + cast,
                    ),
                    topLeft = Offset(fold, 0f),
                    size = Size(cast, h),
                )
            }
            clipRect(left = 0f, top = 0f, right = fold, bottom = h) { drawTop() }
            if (q > 0f && q < 1f) {
                val flapLeft = flap.coerceAtLeast(0f)
                // Soft shadow of the flap edge on the top page.
                val edgeShadow = 14f * density
                if (flap > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .16f)),
                            startX = flap - edgeShadow,
                            endX = flap,
                        ),
                        topLeft = Offset(flap - edgeShadow, 0f),
                        size = Size(edgeShadow, h),
                    )
                }
                clipRect(left = flapLeft, top = 0f, right = fold, bottom = h) {
                    // Back of the paper: page colour, faint mirrored text showing through.
                    drawRect(lerp(theme.page, theme.text, if (theme.dark) .10f else .06f))
                    withTransform({ scale(scaleX = -1f, scaleY = 1f, pivot = Offset(fold, h / 2f)) }) {
                        drawContextAlphaV30(.16f) { drawTop() }
                    }
                    val flapWidth = (fold - flap).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = if (theme.dark) .02f else .18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = .14f),
                            ),
                            startX = flap,
                            endX = flap + flapWidth,
                        ),
                        topLeft = Offset(flapLeft, 0f),
                        size = Size(fold - flapLeft, h),
                    )
                }
            }
        }
    }
}

/**
 * Draws content with reduced opacity. A layer keeps the mirrored text faint without having to
 * recolour every paint.
 */
private fun DrawScope.drawContextAlphaV30(alpha: Float, block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        block()
        canvas.restore()
    }
}

/** Maps theme keys written by older reader versions onto the V30 palette. */
internal fun readerThemeKeyV30(key: String): String = when (key) {
    in READER_THEMES_V30.map { it.key } -> key
    "tomato" -> "tea"
    "mint" -> "green"
    "mist", "ink" -> "langhuan"
    "cream" -> "sheep"
    "dark", "black", "ink_night", "nightBlue" -> "night"
    else -> if (key.contains("night", ignoreCase = true)) "night" else "paper"
}
