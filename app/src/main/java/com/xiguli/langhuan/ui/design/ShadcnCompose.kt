package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ShadcnButtonVariant { DEFAULT, OUTLINE, SECONDARY, GHOST, DESTRUCTIVE }
enum class ShadcnButtonSize { XS, SM, DEFAULT, LG, ICON }

@Composable
fun ShadcnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.DEFAULT,
    size: ShadcnButtonSize = ShadcnButtonSize.DEFAULT,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val height = when (size) {
        ShadcnButtonSize.XS -> 30.dp
        ShadcnButtonSize.SM -> 34.dp
        ShadcnButtonSize.DEFAULT -> 38.dp
        ShadcnButtonSize.LG -> 42.dp
        ShadcnButtonSize.ICON -> 38.dp
    }
    val horizontal = when (size) {
        ShadcnButtonSize.XS -> 9.dp
        ShadcnButtonSize.SM -> 11.dp
        ShadcnButtonSize.DEFAULT -> 14.dp
        ShadcnButtonSize.LG -> 18.dp
        ShadcnButtonSize.ICON -> 0.dp
    }
    val (container, content, border) = when (variant) {
        ShadcnButtonVariant.DEFAULT -> Triple(t.primary, t.primaryForeground, t.primary)
        ShadcnButtonVariant.OUTLINE -> Triple(t.card, t.foreground, t.border)
        ShadcnButtonVariant.SECONDARY -> Triple(t.muted, t.foreground, Color.Transparent)
        ShadcnButtonVariant.GHOST -> Triple(Color.Transparent, t.foreground, Color.Transparent)
        ShadcnButtonVariant.DESTRUCTIVE -> Triple(t.destructive, t.destructiveForeground, t.destructive)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = height),
        shape = RoundedCornerShape(t.radiusMd),
        color = if (enabled) container else container.copy(alpha = .5f),
        contentColor = if (enabled) content else content.copy(alpha = .55f),
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border),
        shadowElevation = if (variant == ShadcnButtonVariant.OUTLINE) .5.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.height(height).padding(horizontal = horizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leadingIcon?.let {
                Icon(it, null, Modifier.size(if (size == ShadcnButtonSize.XS) 14.dp else 16.dp))
                if (text.isNotBlank()) Spacer(Modifier.width(7.dp))
            }
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = if (size == ShadcnButtonSize.XS) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ShadcnIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.GHOST,
    selected: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val container = when {
        selected -> t.muted
        variant == ShadcnButtonVariant.OUTLINE -> t.card
        else -> Color.Transparent
    }
    val border = if (variant == ShadcnButtonVariant.OUTLINE) BorderStroke(1.dp, t.border) else null
    Surface(
        onClick = onClick,
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(t.radiusMd),
        color = container,
        contentColor = t.foreground,
        border = border,
        shadowElevation = if (variant == ShadcnButtonVariant.OUTLINE) .5.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(18.dp), tint = t.foreground)
        }
    }
}

@Composable
fun ShadcnCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        contentColor = t.cardForeground,
        border = BorderStroke(1.dp, t.border),
        shadowElevation = 1.dp,
    ) {
        content()
    }
}

@Composable
fun ShadcnCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) {
                Text(description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
        action?.invoke()
    }
}

@Composable
fun ShadcnInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(t.radiusMd),
        color = t.card,
        border = BorderStroke(1.dp, t.input),
        shadowElevation = .5.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(it, null, Modifier.size(17.dp), tint = t.mutedForeground)
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = t.foreground),
                cursorBrush = SolidColor(t.ring),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank() && placeholder.isNotBlank()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
fun ShadcnTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(t.radiusLg))
            .background(t.muted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(t.radiusMd),
                color = if (index == selectedIndex) t.card else Color.Transparent,
                contentColor = if (index == selectedIndex) t.foreground else t.mutedForeground,
                shadowElevation = if (index == selectedIndex) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ShadcnMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (destructive) t.destructive else t.mutedForeground)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (destructive) t.destructive else t.foreground,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ShadcnSeparator(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    HorizontalDivider(modifier, thickness = 1.dp, color = t.border)
}
