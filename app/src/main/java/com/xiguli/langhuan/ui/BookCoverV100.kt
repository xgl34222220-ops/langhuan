package com.xiguli.langhuan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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

/** Book covers are drawn 3:4, like printed web-novel covers. */
const val BOOK_COVER_RATIO_V100 = 0.75f

/** Number of generated cover styles offered on the 封面 tab. */
const val GENERATED_COVER_STYLES_V100 = 9

private data class CoverPaletteV100(val top: Color, val bottom: Color, val ink: Color, val accent: Color)

private val COVER_PALETTES_V100 = listOf(
    CoverPaletteV100(Color(0xFF1F3A5F), Color(0xFF0B1626), Color(0xFFF3EBDD), Color(0xFFE0B25B)), // 夜蓝
    CoverPaletteV100(Color(0xFFDCE5DD), Color(0xFFB7C9BB), Color(0xFF243B31), Color(0xFF6F8F7B)), // 青瓷
    CoverPaletteV100(Color(0xFF7A2E2E), Color(0xFF3A1214), Color(0xFFF6E7D0), Color(0xFFE9B872)), // 朱砂
    CoverPaletteV100(Color(0xFFEDE5D3), Color(0xFFD9CBAE), Color(0xFF4A3A22), Color(0xFF9C7A43)), // 宣纸
    CoverPaletteV100(Color(0xFF2E2A3F), Color(0xFF14121D), Color(0xFFE8E3F5), Color(0xFFA493D9)), // 黛紫
    CoverPaletteV100(Color(0xFFE8DCDC), Color(0xFFD1BCBE), Color(0xFF4E2E33), Color(0xFFB0646F)), // 藕荷
    CoverPaletteV100(Color(0xFF1E3B33), Color(0xFF0C1A16), Color(0xFFE6F0E9), Color(0xFF8FC1A4)), // 松烟
    CoverPaletteV100(Color(0xFFF4F1EA), Color(0xFFE4DED2), Color(0xFF2B2B2B), Color(0xFFC0392B)), // 素白
    CoverPaletteV100(Color(0xFF3D3F3A), Color(0xFF1C1D1A), Color(0xFFEDEDE6), Color(0xFFCFB27A)), // 石墨
)

/** Default generated style for a book: stable per title so a book always keeps its look. */
fun defaultCoverStyleV100(title: String): Int = Math.floorMod(title.hashCode(), GENERATED_COVER_STYLES_V100)

/**
 * A text cover drawn from the title and author. Styles differ in palette and layout (centred
 * title between rules, vertical title on the right, or a banded lower title).
 */
@Composable
fun GeneratedCoverV100(title: String, author: String, style: Int, modifier: Modifier = Modifier) {
    val palette = COVER_PALETTES_V100[Math.floorMod(style, COVER_PALETTES_V100.size)]
    val layout = Math.floorMod(style, 3)
    BoxWithConstraints(
        modifier = modifier.background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        val small = maxWidth < 80.dp
        val titleSize = when {
            small -> 10.sp
            title.length <= 4 -> 22.sp
            title.length <= 8 -> 18.sp
            else -> 15.sp
        }
        // Spine shadow on the left edge.
        Box(
            Modifier.fillMaxHeight().width(if (small) 3.dp else 6.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent))),
        )
        when (layout) {
            0 -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (small) 6.dp else 14.dp, vertical = if (small) 8.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.8f))
                Box(Modifier.width(if (small) 12.dp else 22.dp).height(1.dp).background(palette.accent))
                Spacer(Modifier.height(if (small) 4.dp else 10.dp))
                CoverTitleTextV100(title, palette.ink, titleSize, if (small) 3 else 4)
                Spacer(Modifier.height(if (small) 4.dp else 10.dp))
                Box(Modifier.width(if (small) 12.dp else 22.dp).height(1.dp).background(palette.accent))
                Spacer(Modifier.weight(1f))
                if (!small && author.isNotBlank()) CoverAuthorTextV100(author, palette.ink)
            }
            1 -> Box(Modifier.fillMaxSize().padding(if (small) 6.dp else 14.dp)) {
                Text(
                    text = title.take(12).toList().joinToString("\n"),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = if (small) 2.dp else 6.dp),
                    color = palette.ink,
                    fontSize = if (small) 9.sp else 17.sp,
                    lineHeight = if (small) 11.sp else 20.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = if (small) 7 else 12,
                    overflow = TextOverflow.Clip,
                )
                Box(
                    Modifier.align(Alignment.BottomStart).width(if (small) 10.dp else 18.dp)
                        .height(if (small) 10.dp else 18.dp).clip(RoundedCornerShape(2.dp)).background(palette.accent),
                )
                if (!small && author.isNotBlank()) {
                    Text(
                        text = author.take(10),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        color = palette.ink.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1.2f))
                Box(
                    Modifier.fillMaxWidth().background(palette.accent.copy(alpha = 0.92f))
                        .padding(horizontal = if (small) 4.dp else 10.dp, vertical = if (small) 4.dp else 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverTitleTextV100(title, palette.bottom, titleSize, if (small) 2 else 3)
                }
                Spacer(Modifier.weight(0.5f))
                if (!small && author.isNotBlank()) CoverAuthorTextV100(author, palette.ink, Modifier.padding(bottom = 12.dp))
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
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CoverAuthorTextV100(author: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = author.take(12),
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.8f),
        fontSize = 10.sp,
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
    val shape = RoundedCornerShape(4.dp)
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
