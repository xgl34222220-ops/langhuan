package com.xiguli.langhuan.ui.design

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic design tokens for Langhuan's native mobile UI.
 *
 * The role names stay compatible with the earlier shadcn-inspired layer, but the defaults now
 * follow Langhuan's Material You / MIUIx hierarchy: tonal selection, soft surfaces and semantic
 * state colors instead of black selected controls and outlined card walls.
 */
@Immutable
data class LanghuanUiTokens(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
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
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 20.dp,
    val radiusXl: Dp = 26.dp,
)

val LocalLanghuanUiTokens = staticCompositionLocalOf {
    LanghuanUiTokens(
        background = Color(0xFFF4F3FA),
        foreground = Color(0xFF171923),
        card = Color(0xFFFBFAFE),
        cardForeground = Color(0xFF171923),
        muted = Color(0xFFF0EFF6),
        mutedForeground = Color(0xFF666B7A),
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

@Composable
fun LanghuanCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        color = t.card,
        contentColor = t.cardForeground,
        shape = RoundedCornerShape(t.radiusMd),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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
                    style = MaterialTheme.typography.labelMedium,
                    color = t.mutedForeground,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
    val container = if (selected) t.accent else Color.Transparent
    val foreground = if (selected) t.accentForeground else t.foreground
    Surface(
        modifier = modifier.size(40.dp),
        color = container,
        contentColor = foreground,
        shape = RoundedCornerShape(t.radiusMd),
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(20.dp), tint = foreground)
        }
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
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
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
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(t.radiusSm),
            color = t.accent,
            contentColor = t.accentForeground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = t.accentForeground)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
    HorizontalDivider(modifier = modifier, color = t.border.copy(alpha = .56f), thickness = 1.dp)
}
