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
import androidx.compose.foundation.layout.weight
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
 * Compose-native mobile primitives derived from the component anatomy the project references:
 * HeroUI Native (BottomSheet / Tabs), coss ui (semantic states / compact controls) and tweakcn
 * (semantic theme tokens). We intentionally port the anatomy and interaction model instead of
 * importing React / React Native packages into a Jetpack Compose application.
 */
@Immutable
internal data class LanghuanComponentTokensV3(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val mutedForeground: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val destructive: Color,
)

internal fun langhuanComponentTokensV3(
    background: Color,
    foreground: Color,
    accent: Color,
): LanghuanComponentTokensV3 = LanghuanComponentTokensV3(
    background = background,
    foreground = foreground,
    surface = Color.White.copy(alpha = if (background.luminanceV3() > .55f) .76f else .09f),
    surfaceRaised = Color.White.copy(alpha = if (background.luminanceV3() > .55f) .92f else .14f),
    muted = foreground.copy(alpha = .08f),
    mutedForeground = foreground.copy(alpha = .54f),
    primary = accent,
    onPrimary = Color.White,
    outline = foreground.copy(alpha = .10f),
    destructive = Color(0xFFBA1A1A),
)

/** HeroUI-like sheet content: drag affordance -> header -> content -> safe-area. */
@Composable
internal fun LanghuanHeroSheetV3(
    tokens: LanghuanComponentTokensV3,
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
            } else {
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** HeroUI-like segmented tabs with a tonal selected surface instead of black filled pills. */
@Composable
internal fun LanghuanHeroTabsV3(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tokens: LanghuanComponentTokensV3,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = tokens.muted,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    if (selected) tokens.surfaceRaised else Color.Transparent,
                    tween(170, easing = FastOutSlowInEasing),
                    label = "tab-bg",
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
                        color = if (selected) tokens.foreground else tokens.mutedForeground,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Compact mobile action tile used by Reader's More grid. */
@Composable
internal fun LanghuanHeroActionTileV3(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tokens: LanghuanComponentTokensV3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
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

/** coss-style settings/action row with optional trailing text. */
@Composable
internal fun LanghuanHeroRowV3(
    title: String,
    tokens: LanghuanComponentTokensV3,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(13.dp),
                color = tokens.muted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (destructive) tokens.destructive else tokens.foreground.copy(alpha = .76f),
                        modifier = Modifier.size(19.dp),
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
                Text(
                    subtitle,
                    Modifier.padding(top = 3.dp),
                    color = tokens.mutedForeground,
                    fontSize = 11.sp,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(trailing, color = tokens.mutedForeground, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun LanghuanHairlineV3(tokens: LanghuanComponentTokensV3, inset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(tokens.outline),
    )
}

private fun Color.luminanceV3(): Float =
    (red * .2126f + green * .7152f + blue * .0722f)
