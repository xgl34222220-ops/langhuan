package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Muted cloth-binding tones for books without a cover image: (paper, ink) per mode. */
private val COVER_TONES_LIGHT_V96 = listOf(
    Color(0xFFDCE5DD) to Color(0xFF2F4A40), // 青瓷 celadon
    Color(0xFFEDE5D3) to Color(0xFF5A4A2E), // 米 rice
    Color(0xFFD9DFE8) to Color(0xFF2E3D55), // 黛蓝 ink blue
    Color(0xFFE8DCDC) to Color(0xFF5A3438), // 藕荷 plum
    Color(0xFFE2E1DA) to Color(0xFF3D3F3A), // 石 slate
)
private val COVER_TONES_DARK_V96 = listOf(
    Color(0xFF27312C) to Color(0xFFB9CFC3),
    Color(0xFF332D23) to Color(0xFFE0CFAE),
    Color(0xFF262C36) to Color(0xFFB8C6DC),
    Color(0xFF33292A) to Color(0xFFDDBFC2),
    Color(0xFF2D2D2A) to Color(0xFFCFCFC6),
)

/**
 * V96 generated cover for a book without artwork: a cloth-bound look (tone picked from the title so
 * a book always keeps its colour), a soft spine shadow on the left, and the title set between two
 * short rules. Scales its type down for small list thumbnails.
 */
@Composable
fun CoverPlaceholderV96(title: String, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val dark = t.background.luminance() < 0.5f
    val tones = if (dark) COVER_TONES_DARK_V96 else COVER_TONES_LIGHT_V96
    val (paper, ink) = tones[Math.floorMod(title.hashCode(), tones.size)]
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(paper, Color.White, if (dark) 0.04f else 0.22f), paper)),
        ),
    ) {
        val small = maxWidth < 72.dp
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (small) 3.dp else 6.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (small) 5.dp else 12.dp, vertical = if (small) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.width(if (small) 10.dp else 18.dp).height(1.dp).background(ink.copy(alpha = 0.45f)))
            Text(
                text = title.take(16),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (small) 9.sp else 14.sp,
                    lineHeight = if (small) 12.sp else 19.sp,
                    letterSpacing = if (small) 0.sp else 0.6.sp,
                ),
                color = ink,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = if (small) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.width(if (small) 10.dp else 18.dp).height(1.dp).background(ink.copy(alpha = 0.45f)))
            Spacer(Modifier.height(if (small) 2.dp else 6.dp))
        }
    }
}
