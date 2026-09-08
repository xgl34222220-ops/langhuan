package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Langhuan's semantic UI tokens.
 *
 * Layering is expressed by radius + shadow + inset highlight. Visible borders are intentionally
 * not part of the hierarchy language. `track` is the only neutral 1 px separator role.
 */
@Immutable
data class LanghuanUiTokens(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val strong: Color,
    val track: Color,
    val border: Color,
    val input: Color,
    val primary: Color,
    val primaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val success: Color,
    val successForeground: Color,
    val warning: Color,
    val warningForeground: Color,
    val ring: Color,
    val warmSurface: Color,
    val radiusSm: Dp = 12.dp,
    val radiusMd: Dp = 15.dp,
    val radiusLg: Dp = 18.dp,
    val radiusXl: Dp = 24.dp,
)

val LocalLanghuanUiTokens = staticCompositionLocalOf {
    LanghuanUiTokens(
        background = Color(0xFFF4F3FA),
        foreground = Color(0xFF171923),
        card = Color(0xFFFBFAFE),
        cardForeground = Color(0xFF171923),
        muted = Color(0xFFF0EFF6),
        mutedForeground = Color(0xFF666B7A),
        strong = Color(0xFF323746),
        track = Color(0x17171923),
        border = Color(0xFFDFDEE7),
        input = Color(0xFFE9E8F0),
        primary = Color(0xFF245FD3),
        primaryForeground = Color.White,
        accent = Color(0xFFDDE8FF),
        accentForeground = Color(0xFF0C326D),
        destructive = Color(0xFFBA1A1A),
        destructiveForeground = Color.White,
        success = Color(0xFF1B8A61),
        successForeground = Color.White,
        warning = Color(0xFFC47700),
        warningForeground = Color.White,
        ring = Color(0xFF245FD3).copy(alpha = .55f),
        warmSurface = Color(0xFFF8F4ED),
    )
}

private fun depthBrush(base: Color, highlightAlpha: Float = .18f): Brush {
    val highlight = Color.White.copy(alpha = highlightAlpha).compositeOver(base)
    return Brush.verticalGradient(listOf(highlight, base, base))
}

@Composable
fun LanghuanCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    depth: Int = 1,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val radius = when (depth.coerceIn(0, 2)) {
        0 -> t.radiusSm
        2 -> t.radiusLg
        else -> t.radiusMd
    }
    val shadow = when (depth.coerceIn(0, 2)) {
        0 -> 2.dp
        2 -> 10.dp
        else -> 6.dp
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .shadow(shadow, shape, clip = false)
            .clip(shape)
            .background(depthBrush(t.card, if (depth >= 2) .22f else .16f)),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun LanghuanPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun LanghuanIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val base = if (selected) t.accent else t.card
    val foreground = if (selected) t.accentForeground else t.strong
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(depthBrush(base, .24f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = foreground)
    }
}

@Composable
fun LanghuanBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        color = if (accent) t.accent else t.muted,
        contentColor = if (accent) t.accentForeground else t.mutedForeground,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun LanghuanMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .shadow(4.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(depthBrush(t.card, .22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = t.strong)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun LanghuanSeparator(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.track),
    )
}
