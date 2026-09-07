package com.xiguli.langhuan.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native Compose primitives ported from the anatomy of HeroUI Native / coss / tweakcn references.
 * This is deliberately a Compose implementation rather than a React/React-Native dependency.
 */
@Immutable
internal data class LanghuanTokensV4(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val mutedForeground: Color,
    val primary: Color,
    val outline: Color,
    val destructive: Color,
)

internal fun langhuanTokensV4(background: Color, foreground: Color, accent: Color): LanghuanTokensV4 {
    val light = background.red * .2126f + background.green * .7152f + background.blue * .0722f > .55f
    return LanghuanTokensV4(
        background = background,
        foreground = foreground,
        surface = Color.White.copy(alpha = if (light) .78f else .08f),
        surfaceRaised = Color.White.copy(alpha = if (light) .94f else .13f),
        muted = foreground.copy(alpha = .075f),
        mutedForeground = foreground.copy(alpha = .54f),
        primary = accent,
        outline = foreground.copy(alpha = .10f),
        destructive = Color(0xFFBA1A1A),
    )
}

@Composable
internal fun LanghuanSheetV4(
    tokens: LanghuanTokensV4,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = tokens.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(tokens.foreground.copy(alpha = .16f)),
            )
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    Modifier.padding(top = 14.dp, bottom = 12.dp),
                    color = tokens.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun LanghuanTabsV4(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    tokens: LanghuanTokensV4,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = tokens.muted,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val bg by animateColorAsState(
                    targetValue = if (active) tokens.surfaceRaised else Color.Transparent,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                    label = "reader-tab",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(bg)
                        .clickable { onSelected(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (active) tokens.foreground else tokens.mutedForeground,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LanghuanActionTileV4(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tokens: LanghuanTokensV4,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(15.dp),
            color = if (selected) tokens.primary.copy(alpha = .14f) else tokens.muted,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(21.dp),
                    tint = if (selected) tokens.primary else tokens.foreground.copy(alpha = .78f),
                )
            }
        }
        Text(
            label,
            Modifier.padding(top = 7.dp),
            color = if (selected) tokens.primary else tokens.mutedForeground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun LanghuanRowV4(
    title: String,
    tokens: LanghuanTokensV4,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Surface(Modifier.size(38.dp), RoundedCornerShape(13.dp), color = tokens.muted) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (destructive) tokens.destructive else tokens.foreground.copy(alpha = .76f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) tokens.destructive else tokens.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, Modifier.padding(top = 3.dp), color = tokens.mutedForeground, fontSize = 11.sp)
            }
        }
        if (!trailing.isNullOrBlank()) Text(trailing, color = tokens.mutedForeground, fontSize = 12.sp)
    }
}

@Composable
internal fun LanghuanDividerV4(tokens: LanghuanTokensV4, inset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(tokens.outline),
    )
}
