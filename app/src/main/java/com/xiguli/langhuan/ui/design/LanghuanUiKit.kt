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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantic UI tokens. LuoShu is the visual baseline for all non-reader-body surfaces. */
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
    val radiusSm: Dp = 11.dp,
    val radiusMd: Dp = 18.dp,
    val radiusLg: Dp = 24.dp,
    val radiusXl: Dp = 30.dp,
)

val LocalLanghuanUiTokens = staticCompositionLocalOf {
    LanghuanUiTokens(
        background = Color(0xFFF4F6FA),
        foreground = Color(0xFF171A1F),
        card = Color.White,
        cardForeground = Color(0xFF171A1F),
        muted = Color(0xFFF0F3F7),
        mutedForeground = Color(0xFF646A72),
        strong = Color(0xFF30363D),
        track = Color(0x13171A1F),
        border = Color(0xFFDDE2E8),
        input = Color(0xFFE9EDF2),
        primary = Color(0xFF315F8C),
        primaryForeground = Color.White,
        accent = Color(0xFFD7E8FA),
        accentForeground = Color(0xFF153451),
        destructive = Color(0xFFBA1A1A),
        destructiveForeground = Color.White,
        success = Color(0xFF1B8A61),
        successForeground = Color.White,
        warning = Color(0xFFC47700),
        warningForeground = Color.White,
        ring = Color(0xFF315F8C).copy(alpha = .48f),
        warmSurface = Color(0xFFF8F5EF),
    )
}

@Composable
fun LanghuanCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    depth: Int = 1,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val level = depth.coerceIn(0, 2)
    val radius = when (level) {
        0 -> t.radiusMd
        2 -> t.radiusXl
        else -> t.radiusLg
    }
    val shadow = when (level) {
        0 -> 0.dp
        2 -> 2.dp
        else -> 1.dp
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .shadow(shadow, shape, clip = false)
            .clip(shape)
            .background(t.card),
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** 48 dp touch target, 44 dp visible circle, 21 dp glyph — matching LuoShu headers. */
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
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .shadow(1.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(base),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(21.dp), tint = foreground)
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
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
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
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconShape = RoundedCornerShape(t.radiusSm)
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(1.dp, iconShape, clip = false)
                .clip(iconShape)
                .background(t.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = t.strong)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.track),
    )
}
