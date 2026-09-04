package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.BorderStroke
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
 * Semantic design tokens for the Langhuan UI layer.
 *
 * This mirrors shadcn/ui's role model: background/foreground/card/muted/border/input/primary/
 * accent/destructive/ring. Accent is intentionally neutral; explicit brand color lives in the
 * Material tertiary role instead of leaking into every selected or hover state.
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
    val radiusSm: Dp = 6.dp,
    val radiusMd: Dp = 8.dp,
    val radiusLg: Dp = 10.dp,
    val radiusXl: Dp = 12.dp,
)

val LocalLanghuanUiTokens = staticCompositionLocalOf {
    LanghuanUiTokens(
        background = Color(0xFFFAFAFA),
        foreground = Color(0xFF18181B),
        card = Color(0xFFFFFFFF),
        cardForeground = Color(0xFF18181B),
        muted = Color(0xFFF4F4F5),
        mutedForeground = Color(0xFF71717A),
        border = Color(0xFFE4E4E7),
        input = Color(0xFFE4E4E7),
        primary = Color(0xFF18181B),
        primaryForeground = Color(0xFFFAFAFA),
        accent = Color(0xFFEFEFF0),
        accentForeground = Color(0xFF18181B),
        destructive = Color(0xFFDC2626),
        destructiveForeground = Color.White,
        success = Color(0xFF15815D),
        successForeground = Color.White,
        warning = Color(0xFFB76A0A),
        warningForeground = Color.White,
        ring = Color(0xFF71717A),
        warmSurface = Color(0xFFFAFAFA),
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
        border = BorderStroke(1.dp, t.border),
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
    val container = if (selected) t.foreground else t.card
    val foreground = if (selected) t.primaryForeground else t.foreground
    Surface(
        modifier = modifier.size(40.dp),
        color = container,
        contentColor = foreground,
        shape = RoundedCornerShape(t.radiusSm),
        border = BorderStroke(1.dp, if (selected) t.foreground else t.border),
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
        border = BorderStroke(1.dp, t.border),
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
            color = t.muted,
            contentColor = t.foreground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = t.foreground)
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
    HorizontalDivider(modifier = modifier, color = t.border, thickness = 1.dp)
}
