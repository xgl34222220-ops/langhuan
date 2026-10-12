package com.xiguli.langhuan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.rememberLanghuanCoverV30

/** V103: book covers are drawn 5:7 (measured 176 × 247 px on the reference shelf). */
const val BOOK_COVER_RATIO_V100 = 5f / 7f

/** Number of generated cover styles offered on the 封面 tab. */
const val GENERATED_COVER_STYLES_V100 = 9

private data class CoverPaletteV100(val paper: Color, val ink: Color)

/** V102: quiet solid bindings (muted, low-saturation), light and dark, like printed paperbacks. */
private val COVER_PALETTES_V100 = listOf(
    CoverPaletteV100(Color(0xFFDCE3EA), Color(0xFF2B3A4E)), // 雾蓝
    CoverPaletteV100(Color(0xFF3E4E5C), Color(0xFFEEF1F4)), // 黛青
    CoverPaletteV100(Color(0xFFEEE8DC), Color(0xFF4A3E2C)), // 米白
    CoverPaletteV100(Color(0xFFDDE5DC), Color(0xFF2F4436)), // 青瓷
    CoverPaletteV100(Color(0xFF3B4A42), Color(0xFFEEF2EE)), // 松烟
    CoverPaletteV100(Color(0xFFEADFDF), Color(0xFF5A3A40)), // 藕荷
    CoverPaletteV100(Color(0xFF8A6A55), Color(0xFFF6EFE8)), // 赭石
    CoverPaletteV100(Color(0xFFE3E2DE), Color(0xFF34353A)), // 石灰
    CoverPaletteV100(Color(0xFF2E2F33), Color(0xFFECEAE5)), // 墨
)

/** Default generated style for a book: stable per title so a book always keeps its look. */
fun defaultCoverStyleV100(title: String): Int = Math.floorMod(title.hashCode(), GENERATED_COVER_STYLES_V100)

/**
 * A text cover drawn from the title and author: a solid muted binding with a faint top light and
 * spine, the title set in a serif. Layouts: centred title over a short rule, a vertical title on
 * the right, or a top-left title block with the author beneath.
 */
@Composable
fun GeneratedCoverV100(title: String, author: String, style: Int, modifier: Modifier = Modifier) {
    val palette = COVER_PALETTES_V100[Math.floorMod(style, COVER_PALETTES_V100.size)]
    val layout = Math.floorMod(style, 3)
    val ink = palette.ink
    BoxWithConstraints(
        modifier = modifier
            .background(palette.paper)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.05f)))),
    ) {
        val small = maxWidth < 80.dp
        val titleSize = when {
            small -> 9.sp
            title.length <= 4 -> 20.sp
            title.length <= 8 -> 16.sp
            else -> 14.sp
        }
        // Spine: a soft fold a few dp in from the left edge.
        Box(
            Modifier.fillMaxHeight().width(if (small) 4.dp else 8.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f), Color.Transparent))),
        )
        when (layout) {
            0 -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (small) 6.dp else 14.dp, vertical = if (small) 8.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.9f))
                CoverTitleTextV100(title, ink, titleSize, if (small) 3 else 4)
                Spacer(Modifier.height(if (small) 5.dp else 10.dp))
                Box(Modifier.width(if (small) 10.dp else 16.dp).height(1.dp).background(ink.copy(alpha = 0.45f)))
                Spacer(Modifier.weight(1f))
                if (!small && author.isNotBlank()) CoverAuthorTextV100(author, ink)
            }
            1 -> Box(Modifier.fillMaxSize().padding(if (small) 6.dp else 14.dp)) {
                Row(Modifier.align(Alignment.TopEnd).padding(top = if (small) 2.dp else 6.dp)) {
                    Box(Modifier.padding(end = if (small) 4.dp else 8.dp, top = 2.dp).width(1.dp).height(if (small) 24.dp else 48.dp).background(ink.copy(alpha = 0.35f)))
                    Text(
                        text = title.take(12).toList().joinToString("\n"),
                        color = ink,
                        fontSize = if (small) 9.sp else 16.sp,
                        lineHeight = if (small) 11.sp else 19.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = if (small) 7 else 11,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (!small && author.isNotBlank()) {
                    Text(
                        text = author.take(10),
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp),
                        color = ink.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(start = if (small) 8.dp else 16.dp, end = if (small) 5.dp else 12.dp, top = if (small) 10.dp else 22.dp, bottom = if (small) 8.dp else 16.dp)) {
                Text(
                    text = title.take(24),
                    color = ink,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.3f,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (small) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!small && author.isNotBlank()) {
                    Text(
                        text = author.take(12),
                        modifier = Modifier.padding(top = 6.dp),
                        color = ink.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(0.4f).height(1.dp).background(ink.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
private fun CoverTitleTextV100(title: String, color: Color, size: androidx.compose.ui.unit.TextUnit, maxLines: Int) {
    Text(
        text = title.take(24),
        color = color,
        fontSize = size,
        lineHeight = size * 1.3f,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        letterSpacing = 0.5.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CoverAuthorTextV100(author: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = author.take(12),
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.72f),
        fontSize = 9.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** Which picture a shelf book shows, resolved from the shelf metadata. */
fun coverPathForV100(book: LocalShelfBook): String =
    book.meta.customCoverPath?.takeIf { it.isNotBlank() }
        ?: if (book.meta.preferGenerated) "" else book.book.coverPath

/** Shelf cover: the picked image, the imported artwork, or the generated text cover. */
@Composable
fun BookCoverV100(book: LocalShelfBook, modifier: Modifier = Modifier, targetWidthPx: Int = 360) {
    CoverImageOrGeneratedV100(
        path = coverPathForV100(book),
        title = book.title,
        author = book.author,
        style = book.meta.generatedCoverStyle ?: defaultCoverStyleV100(book.title),
        modifier = modifier,
        targetWidthPx = targetWidthPx,
    )
}

@Composable
fun CoverImageOrGeneratedV100(
    path: String,
    title: String,
    author: String,
    style: Int,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 360,
) {
    val shape = RoundedCornerShape(COVER_RADIUS_V102)
    val image = rememberLanghuanCoverV30(path, targetWidthPx)
    Box(
        modifier = modifier.clip(shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape),
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GeneratedCoverV100(title = title, author = author, style = style, modifier = Modifier.fillMaxSize())
        }
    }
}
