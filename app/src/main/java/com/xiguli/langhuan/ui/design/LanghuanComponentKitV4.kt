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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reader/shell primitives following the same calm geometry as LuoShu. */
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
        surface = Color.White.copy(alpha = if (light) .72f else .07f).compositeOver(background),
        surfaceRaised = Color.White.copy(alpha = if (light) .96f else .09f).compositeOver(background),
        muted = foreground.copy(alpha = .075f),
        mutedForeground = foreground.copy(alpha = .54f),
        strong = foreground.copy(alpha = .86f),
        primary = accent,
        track = foreground.copy(alpha = if (light) .075f else .12f),
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
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = tokens.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
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
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.45).sp,
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
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .weight(1f)
                    .shadow(if (active) 1.dp else 0.dp, shape, clip = false)
                    .clip(shape)
                    .background(bg)
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
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
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(1.dp, shape, clip = false)
                .clip(shape)
                .background(if (selected) tokens.primary.copy(alpha = .13f).compositeOver(tokens.surfaceRaised) else tokens.surfaceRaised),
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
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
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
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val iconShape = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(1.dp, iconShape, clip = false)
                    .clip(iconShape)
                    .background(tokens.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (destructive) tokens.destructive else tokens.strong,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) tokens.destructive else tokens.foreground,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = .2.sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    Modifier.padding(top = 2.dp),
                    color = tokens.mutedForeground,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    letterSpacing = .2.sp,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                color = tokens.mutedForeground,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                letterSpacing = .2.sp,
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
