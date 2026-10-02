package com.xiguli.langhuan.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

@Composable
fun LanghuanCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    depth: Int = 1,
    onClick: (() -> Unit)? = null,
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
    val clickModifier = if (onClick != null) Modifier.springClickV31(pressedScale = .975f, onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .then(clickModifier)
            .shadow(shadow, shape, clip = false)
            .clip(shape)
            .background(t.card)
            .animateContentSize(LanghuanMotionV31.settle()),
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
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
    val base by animateColorAsState(if (selected) t.accent else t.card, tween(LanghuanMotionV31.MEDIUM), label = "iconButtonBg")
    val foreground by animateColorAsState(if (selected) t.accentForeground else t.strong, tween(LanghuanMotionV31.MEDIUM), label = "iconButtonFg")
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pressScaleV31(interaction, .88f)
                .size(44.dp)
                .shadow(1.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(base),
            contentAlignment = Alignment.Center,
        ) {
            // Swapping glyphs (search ⇄ close) cross-fades instead of popping.
            Crossfade(targetState = icon, animationSpec = tween(LanghuanMotionV31.FAST), label = "iconSwap") { glyph ->
                Icon(glyph, contentDescription, Modifier.size(21.dp), tint = foreground)
            }
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
    val bg by animateColorAsState(if (accent) t.accent else t.muted, tween(LanghuanMotionV31.MEDIUM), label = "badgeBg")
    val fg by animateColorAsState(if (accent) t.accentForeground else t.mutedForeground, tween(LanghuanMotionV31.MEDIUM), label = "badgeFg")
    Surface(
        modifier = modifier.animateContentSize(LanghuanMotionV31.settle()),
        color = bg,
        contentColor = fg,
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowBg by animateColorAsState(if (pressed) t.muted else Color.Transparent, tween(LanghuanMotionV31.FAST), label = "menuRowBg")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusMd))
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconShape = RoundedCornerShape(t.radiusSm)
        Box(
            modifier = Modifier
                .pressScaleV31(interaction, .9f)
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
