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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/**
 * V104 generated covers: nine designed bindings instead of flat swatches — each a palette plus a
 * typographic layout (classic frame, colour band, vertical 竖排 with a seal, oversized initial,
 * dusk gradient, modern block, circle, minimal, ribbon bookmark). Every cover gets a spine fold
 * and soft diagonal light so it reads as a printed object; type scales with the cover width so
 * shelf, list-strip and picker sizes all hold together.
 */
private data class CoverDesignV104(val paper: Color, val ink: Color, val accent: Color, val paper2: Color? = null)

private val COVER_DESIGNS_V104 = listOf(
    CoverDesignV104(Color(0xFF1F2A44), Color(0xFFF1E9DA), Color(0xFFC8A96A)), // 0 藏青 · 古典框
    CoverDesignV104(Color(0xFFF1EDE6), Color(0xFF4A2F25), Color(0xFFB4553D)), // 1 赤陶 · 色带
    CoverDesignV104(Color(0xFF2E3A4F), Color(0xFFEDE6D6), Color(0xFFB23A2E)), // 2 黛蓝 · 竖排印章
    CoverDesignV104(Color(0xFFF4F2EE), Color(0xFF23262B), Color(0xFF3D5A80)), // 3 瓷白 · 大字
    CoverDesignV104(Color(0xFF6B4A6E), Color(0xFFF3E8EE), Color(0xFFE6C9A8), Color(0xFF2C2235)), // 4 暮紫 · 渐变
    CoverDesignV104(Color(0xFFD2A04C), Color(0xFF2A2015), Color(0xFF2A2015)), // 5 赭黄 · 现代
    CoverDesignV104(Color(0xFFDCE2EA), Color(0xFF1E2B40), Color(0xFF4F6D9A)), // 6 雾蓝 · 圆
    CoverDesignV104(Color(0xFF232326), Color(0xFFECEAE5), Color(0xFFC8A96A)), // 7 墨黑 · 极简
    CoverDesignV104(Color(0xFF6E4B3A), Color(0xFFF5EBDD), Color(0xFFC4473A)), // 8 茶褐 · 书签
)

/** Default generated style for a book: stable per title so a book always keeps its look. */
fun defaultCoverStyleV100(title: String): Int = Math.floorMod(title.hashCode(), GENERATED_COVER_STYLES_V100)

/** Vertical (竖排) setting only for short CJK titles; Latin words or long titles fall back to horizontal. */
internal fun coverTitleVerticalV104(title: String): Boolean =
    title.isNotBlank() && title.length <= 9 && title.none { it.isLetterOrDigit() && it.code < 0x2E80 }

/** Title size in sp at 100 dp cover width: shorter titles set larger. */
internal fun coverTitleSizeV104(title: String): Float = when {
    title.length <= 3 -> 22f
    title.length <= 6 -> 18f
    title.length <= 10 -> 15f
    else -> 13f
}

@Composable
fun GeneratedCoverV100(title: String, author: String, style: Int, modifier: Modifier = Modifier) {
    val d = COVER_DESIGNS_V104[Math.floorMod(style, COVER_DESIGNS_V104.size)]
    val layout = Math.floorMod(style, COVER_DESIGNS_V104.size)
    val cleanTitle = title.trim().ifBlank { "未命名" }
    val cleanAuthor = author.trim()
    BoxWithConstraints(
        // The cover's type is decoration: the shelf item already carries the title for a11y/tests.
        modifier = modifier.clearAndSetSemantics { }.background(
            if (d.paper2 != null) Brush.verticalGradient(listOf(d.paper, d.paper2)) else Brush.verticalGradient(listOf(d.paper, d.paper)),
        ),
    ) {
        val k = (maxWidth.value / 100f).coerceIn(0.3f, 2.5f)
        fun sp(v: Float) = (v * k).sp
        fun dp(v: Float) = (v * k).dp
        val tiny = maxWidth < 56.dp
        val titleSp = coverTitleSizeV104(cleanTitle)
        val vertical = coverTitleVerticalV104(cleanTitle)
        when (layout) {
            0 -> { // Classic double frame, centred serif title, diamond ornament.
                Box(Modifier.fillMaxSize().padding(dp(7f)).border(dp(0.8f), d.accent.copy(alpha = 0.75f)))
                Box(Modifier.fillMaxSize().padding(dp(10f)).border(dp(0.4f), d.accent.copy(alpha = 0.45f)))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = dp(16f), vertical = dp(18f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(0.8f))
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp), TextAlign.Center, maxLines = 4)
                    Box(Modifier.padding(vertical = dp(8f)).size(dp(5f)).rotate(45f).background(d.accent))
                    Spacer(Modifier.weight(1f))
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Center)
                }
            }
            1 -> { // Terracotta band holding the title, author on the paper below.
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(0.6f).background(d.accent).padding(start = dp(14f), end = dp(10f), bottom = dp(10f))) {
                        CoverTitleV104(cleanTitle, Color(0xFFFBF6F0), sp(titleSp), TextAlign.Start, maxLines = 3, modifier = Modifier.align(Alignment.BottomStart))
                    }
                    Column(Modifier.fillMaxWidth().weight(0.4f).padding(start = dp(14f), end = dp(10f), top = dp(10f))) {
                        Box(Modifier.width(dp(18f)).height(dp(1.2f)).background(d.accent))
                        if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Start, Modifier.padding(top = dp(7f)))
                    }
                }
            }
            2, 8 -> if (vertical) { // 竖排: vertical title, a seal (2) or a ribbon bookmark (8).
                Box(Modifier.fillMaxSize()) {
                    if (layout == 8) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(end = dp(14f)).width(dp(9f)).height(dp(34f))
                                .background(d.accent, GenericShape { size, _ ->
                                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                                    lineTo(size.width / 2f, size.height * 0.82f); lineTo(0f, size.height); close()
                                }),
                        )
                    }
                    Row(
                        Modifier.align(if (layout == 8) Alignment.Center else Alignment.TopEnd)
                            .padding(top = dp(if (layout == 8) 0f else 14f), end = dp(if (layout == 8) 0f else 14f)),
                    ) {
                        if (layout == 2) Box(Modifier.padding(end = dp(6f), top = dp(2f)).width(dp(0.6f)).height(dp(60f)).background(d.ink.copy(alpha = 0.35f)))
                        Text(
                            text = cleanTitle.toList().joinToString("\n"),
                            color = d.ink,
                            fontSize = sp(if (cleanTitle.length <= 5) 17f else 14f),
                            lineHeight = sp(if (cleanTitle.length <= 5) 20f else 16.5f),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (layout == 2) {
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(start = dp(14f), bottom = dp(14f)).size(dp(15f))
                                .background(d.accent, RoundedCornerShape(dp(1.5f))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (cleanAuthor.firstOrNull() ?: '书').toString(),
                                color = Color(0xFFF7EEE4),
                                fontSize = sp(9f),
                                lineHeight = sp(10f),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (!tiny && cleanAuthor.isNotEmpty()) {
                        CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Center, Modifier.align(Alignment.BottomCenter).padding(bottom = dp(12f)))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(start = dp(16f), end = dp(12f), top = dp(18f), bottom = dp(14f))) {
                    Box(Modifier.width(dp(20f)).height(dp(2f)).background(d.accent))
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp), TextAlign.Start, maxLines = 4, modifier = Modifier.padding(top = dp(10f)))
                    Spacer(Modifier.weight(1f))
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Start)
                }
            }
            3 -> { // Oversized initial in a tint, title block top-left.
                Box(Modifier.fillMaxSize()) {
                    Text(
                        cleanTitle.first().toString(),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = dp(4f)),
                        color = d.accent.copy(alpha = 0.16f),
                        fontSize = sp(92f),
                        lineHeight = sp(96f),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Column(Modifier.fillMaxSize().padding(start = dp(14f), end = dp(12f), top = dp(16f))) {
                        CoverTitleV104(cleanTitle, d.ink, sp(titleSp * 0.92f), TextAlign.Start, maxLines = 4, serif = false)
                        Box(Modifier.padding(top = dp(8f)).width(dp(14f)).height(dp(2f)).background(d.accent))
                        if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Start, Modifier.padding(top = dp(6f)))
                    }
                }
            }
            4 -> { // Dusk gradient, title between dotted ornament rules.
                Column(
                    Modifier.fillMaxSize().padding(horizontal = dp(14f), vertical = dp(16f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    CoverOrnamentV104(d.accent, k)
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp), TextAlign.Center, maxLines = 4, modifier = Modifier.padding(vertical = dp(9f)))
                    CoverOrnamentV104(d.accent, k)
                    Spacer(Modifier.weight(1f))
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Center)
                }
            }
            5 -> { // Modern: thick top bar, heavy sans title low on the cover.
                Column(Modifier.fillMaxSize().padding(start = dp(13f), end = dp(11f), top = dp(14f), bottom = dp(14f))) {
                    Box(Modifier.fillMaxWidth().height(dp(4f)).background(d.accent))
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Start, Modifier.padding(top = dp(6f)))
                    Spacer(Modifier.weight(1f))
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp * 1.05f), TextAlign.Start, maxLines = 4, serif = false, weight = FontWeight.Black)
                }
            }
            6 -> { // A large circle with the title beneath.
                Column(
                    Modifier.fillMaxSize().padding(horizontal = dp(12f), vertical = dp(14f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.padding(top = dp(6f)).size(dp(48f)).background(d.accent, CircleShape))
                    Spacer(Modifier.weight(1f))
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp * 0.92f), TextAlign.Center, maxLines = 3)
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Center, Modifier.padding(top = dp(6f)))
                }
            }
            else -> { // 7 (and 8 without a vertical title): minimal, spaced title between hairlines.
                Column(
                    Modifier.fillMaxSize().padding(horizontal = dp(14f), vertical = dp(16f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(dp(26f)).height(dp(0.7f)).background(d.ink.copy(alpha = 0.5f)))
                    CoverTitleV104(cleanTitle, d.ink, sp(titleSp * 0.95f), TextAlign.Center, maxLines = 4, spacing = 1.2f, modifier = Modifier.padding(vertical = dp(10f)))
                    Box(Modifier.width(dp(26f)).height(dp(0.7f)).background(d.ink.copy(alpha = 0.5f)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(dp(4f)).background(d.accent, CircleShape))
                    if (!tiny && cleanAuthor.isNotEmpty()) CoverAuthorV104(cleanAuthor, d.ink, sp(8.5f), TextAlign.Center, Modifier.padding(top = dp(6f)))
                }
            }
        }
        // Spine fold and soft diagonal light, drawn over every design.
        Box(
            Modifier.fillMaxHeight().width(dp(7f))
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.White.copy(alpha = 0.10f), Color.Transparent))),
        )
        Box(Modifier.padding(start = dp(7f)).fillMaxHeight().width(dp(0.6f)).background(Color.Black.copy(alpha = 0.08f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.10f))),
            ),
        )
    }
}

@Composable
private fun CoverTitleV104(
    title: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    align: TextAlign,
    maxLines: Int,
    modifier: Modifier = Modifier,
    serif: Boolean = true,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: Float = 0.4f,
) {
    Text(
        text = title.take(28),
        modifier = modifier,
        color = color,
        fontSize = size,
        lineHeight = size * 1.28f,
        fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
        fontWeight = weight,
        textAlign = align,
        letterSpacing = (size.value * spacing / 10f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CoverAuthorV104(author: String, color: Color, size: androidx.compose.ui.unit.TextUnit, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text = author.take(12),
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.72f),
        fontSize = size,
        lineHeight = size * 1.3f,
        letterSpacing = (size.value * 0.08f).sp,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A short rule with a dot in the middle: —— · ——. */
@Composable
private fun CoverOrnamentV104(color: Color, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((14 * k).dp).height((0.7f * k).dp).background(color.copy(alpha = 0.8f)))
        Box(Modifier.padding(horizontal = (4 * k).dp).size((3 * k).dp).background(color, CircleShape))
        Box(Modifier.width((14 * k).dp).height((0.7f * k).dp).background(color.copy(alpha = 0.8f)))
    }
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
