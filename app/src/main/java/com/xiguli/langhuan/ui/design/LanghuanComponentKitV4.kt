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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native Compose mobile primitives for the app shell and reader settings.
 * Hierarchy comes from depth, radius and inset highlight; neutral borders are not used as walls.
 */
@Immutable
internal data class LanghuanTokensV4(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val mutedForeground: Color,
    val strong: Color,
    val primary: Color,
    val track: Color,
    val destructive: Color,
)

internal fun langhuanTokensV4(background: Color, foreground: Color, accent: Color): LanghuanTokensV4 {
    val light = background.red * .2126f + background.green * .7152f + background.blue * .0722f > .55f
    return LanghuanTokensV4(
        background = background,
        foreground = foreground,
        surface = Color.White.copy(alpha = if (light) .78f else .08f),
        surfaceRaised = Color.White.copy(alpha = if (light) .97f else .09f).compositeOver(background),
        muted = foreground.copy(alpha = .075f),
        mutedForeground = foreground.copy(alpha = .54f),
        strong = foreground.copy(alpha = .82f),
        primary = accent,
        track = foreground.copy(alpha = if (light) .075f else .12f),
        destructive = Color(0xFFBA1A1A),
    )
}

private fun v4DepthBrush(base: Color, highlight: Float = .20f): Brush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = highlight).compositeOver(base), base, base),
)

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
        shadowElevation = 8.dp,
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
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(tokens.track),
            )
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    Modifier.padding(top = 14.dp, bottom = 12.dp),
                    color = tokens.foreground,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .6.sp,
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            val bg by animateColorAsState(
                targetValue = if (active) tokens.surfaceRaised else tokens.surface,
                animationSpec = tween(170, easing = FastOutSlowInEasing),
                label = "langhuan-tab",
            )
            Box(
                Modifier
                    .weight(1f)
                    .shadow(if (active) 2.dp else 0.dp, RoundedCornerShape(15.dp), clip = false)
                    .clip(RoundedCornerShape(15.dp))
                    .background(bg)
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Keep the requested inset highlight as a shallow 6dp top sheen. The old
                // full-height white gradient crossed the label area and rendered as a white bar
                // on light reader themes.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(.72f)
                        .height(6.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (active) .16f else .10f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Text(
                    label,
                    color = if (active) tokens.foreground else tokens.mutedForeground,
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = .3.sp,
                )
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
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val base = if (selected) tokens.primary.copy(alpha = .14f).compositeOver(tokens.surfaceRaised) else tokens.surfaceRaised
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(v4DepthBrush(base, .24f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = if (selected) tokens.primary else tokens.strong,
            )
        }
        Text(
            label,
            Modifier.padding(top = 7.dp),
            color = if (selected) tokens.primary else tokens.mutedForeground,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = .25.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
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
            Box(
                Modifier
                    .size(38.dp)
                    .shadow(4.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(v4DepthBrush(tokens.surfaceRaised, .22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (destructive) tokens.destructive else tokens.strong,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) tokens.destructive else tokens.foreground,
                fontSize = 14.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = .3.sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    Modifier.padding(top = 3.dp),
                    color = tokens.mutedForeground,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    letterSpacing = .25.sp,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                color = tokens.mutedForeground,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                letterSpacing = .25.sp,
            )
        }
    }
}

@Composable
internal fun LanghuanDividerV4(tokens: LanghuanTokensV4, inset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(tokens.track),
    )
}

