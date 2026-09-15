package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Legacy spatial entry points kept for source compatibility.
 *
 * LuoShu is now the visual baseline: page backgrounds stay quiet, glass is reserved for floating
 * interaction layers, and hierarchy comes from radius/depth rather than animated decoration.
 */
@Composable
fun LanghuanAmbientBackdrop(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current
    Box(modifier.background(t.background))
}

/** Constellation decoration is intentionally neutralized on production surfaces. */
@Composable
fun LanghuanConstellationField(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    Box(modifier)
}

/** Small status accent; no drifting/glowing orb animation. */
@Composable
fun LanghuanOrb(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    size: Dp = 34.dp,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) t.accent else t.muted),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (active) 8.dp else 6.dp)
                .clip(CircleShape)
                .background(if (active) t.accentForeground else t.mutedForeground),
        )
    }
}

/**
 * Floating-panel primitive. No visible border: LuoShu uses a quiet elevated surface and soft shadow
 * instead of outlining every layer.
 */
@Composable
fun LanghuanGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = t.card.copy(alpha = .94f),
        contentColor = t.cardForeground,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

/** Calm LuoShu-style hero card used by creation/research pages. */
@Composable
fun LanghuanSpatialHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    active: Boolean = true,
    trailing: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = t.card,
        contentColor = t.cardForeground,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box {
            Box(content = trailing)
            Column(Modifier.padding(20.dp)) {
                if (!eyebrow.isNullOrBlank()) {
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = t.mutedForeground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
                content()
            }
        }
    }
}

@Composable
fun LanghuanMotionStatus(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LanghuanOrb(active = active, size = 24.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            fontWeight = FontWeight.Medium,
        )
    }
}
