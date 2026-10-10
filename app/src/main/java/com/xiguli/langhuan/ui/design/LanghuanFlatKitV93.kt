package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable

/*
 * V93 flat kit: the visual language of the reference reader (plain light surfaces, thin outline
 * icons, no chip/card borders, one accent colour, generous spacing). Purely presentational; every
 * caller keeps its own state and callbacks.
 */

/** Plain 44 dp icon button: no fill, no border. Selected tints the glyph with the accent. */
@Composable
fun FlatIconButtonV93(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null,
    iconSize: Int = 22,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize.dp).graphicsLayer { alpha = if (pressed) 0.55f else 1f },
            tint = tint ?: if (selected) t.primary else t.foreground.copy(alpha = 0.84f),
        )
    }
}

/** Sub-page header: back arrow, centred title, optional trailing action. */
@Composable
fun FlatTopBarV93(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backDescription: String = "返回",
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = modifier.fillMaxWidth().height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 56.dp),
            // V95 type scale: sub-page titles 17 sp semibold (iOS nav-bar title size).
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // V96: an iOS-style chevron reads lighter than the Material arrow.
            FlatIconButtonV93(
                icon = Icons.AutoMirrored.Outlined.ArrowBackIos,
                contentDescription = backDescription,
                onClick = onBack,
                iconSize = 20,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/** Small grey group label above a run of rows. */
@Composable
fun FlatSectionLabelV93(text: String, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = text,
        modifier = modifier.padding(top = 24.dp, bottom = 4.dp),
        // V96: 12 sp medium, light tracking — a quiet caption, not a heading.
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        fontWeight = FontWeight.Medium,
        color = t.mutedForeground.copy(alpha = 0.9f),
    )
}

/**
 * Flat list row: thin outline icon, title with optional grey subtitle, trailing value / chevron.
 * No card, no border; rows are separated only by whitespace.
 */
@Composable
fun FlatListRowV93(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
    badge: Boolean = false,
    subtitleAccent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val content = when {
        !enabled -> t.mutedForeground.copy(alpha = 0.5f)
        destructive -> t.destructive
        else -> t.foreground
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (destructive) t.destructive else content.copy(alpha = 0.78f),
                )
                if (badge) FlatDotV93(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
            }
            Spacer(Modifier.width(t.space4))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = if (subtitleAccent) t.primary else t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(t.space2))
            Text(
                text = value,
                modifier = Modifier.widthIn(max = 140.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
        when {
            trailing != null -> { Spacer(Modifier.width(t.space2)); trailing() }
            chevron && onClick != null -> {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = t.mutedForeground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** Settings row: title + grey subtitle, switch on the right. The whole row toggles. */
@Composable
fun FlatSwitchRowV93(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = t.foreground.copy(alpha = 0.78f),
            )
            Spacer(Modifier.width(t.space4))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp), color = t.foreground)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = t.mutedForeground)
            }
        }
        Spacer(Modifier.width(t.space3))
        FlatSwitchV93(checked = checked)
    }
}

/** Visual-only switch track; semantics live on the owning row. */
@Composable
fun FlatSwitchV93(checked: Boolean) {
    val t = LocalLanghuanUiTokens.current
    // V96: iOS proportions (46 x 28, 24 dp thumb), spring-driven thumb, cross-faded track and a
    // soft shadow under the thumb.
    val x by animateDpAsState(
        if (checked) 20.dp else 2.dp,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "switch-thumb",
    )
    val track by animateColorAsState(
        if (checked) t.primary else t.foreground.copy(alpha = 0.13f),
        tween(180),
        label = "switch-track",
    )
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .background(color = track, shape = CircleShape),
    ) {
        Box(
            modifier = Modifier
                .offset(x = x, y = 2.dp)
                .size(24.dp)
                .shadow(elevation = 1.5.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.25f))
                .background(color = Color.White, shape = CircleShape),
        )
    }
}

/**
 * Plain-text tab: no chip. The selected tab is bold foreground with a short accent underline; an
 * optional count sits beside the label in small grey.
 */
@Composable
fun FlatTextTabV93(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accentText: Boolean = false,
    semanticsModifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        // V94: the tab used to accept onClick without ever wiring it.
        modifier = modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = semanticsModifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = when {
                    selected && accentText -> t.primary
                    selected -> t.foreground
                    else -> t.mutedForeground
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            if (count != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = t.mutedForeground,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val bar by animateColorAsState(if (selected && !accentText) t.primary else Color.Transparent, tween(180), label = "tab-bar")
        val barW by animateDpAsState(if (selected) 16.dp else 6.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "tab-bar-w")
        Box(
            Modifier
                .width(barW)
                .height(2.5.dp)
                .background(color = bar, shape = CircleShape),
        )
    }
}

/** Icon-grid cell used by the reader 「更多」 tab: a soft round well with an outline glyph. */
@Composable
fun FlatIconCellV93(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    toggle: Boolean? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val glyph = when {
        !enabled -> t.mutedForeground.copy(alpha = 0.4f)
        destructive -> t.destructive
        active -> t.primary
        else -> t.foreground.copy(alpha = 0.78f)
    }
    val interactive = if (toggle != null) {
        Modifier.toggleable(value = toggle, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(interactive)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    color = if (active) t.primary.copy(alpha = 0.10f) else t.foreground.copy(alpha = 0.045f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = glyph)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = when {
                !enabled -> t.mutedForeground.copy(alpha = 0.5f)
                destructive -> t.destructive
                active -> t.primary
                else -> t.mutedForeground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small accent dot (run badge, unread marker). Visual only. */
@Composable
fun FlatDotV93(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Box(modifier.size(7.dp).background(t.primary, CircleShape))
}

/** Hairline used sparingly between a page header and content. */
@Composable
fun FlatHairlineV93(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Box(modifier.fillMaxWidth().height(hairlineDpV96()).background(t.border.copy(alpha = 0.8f)))
}
