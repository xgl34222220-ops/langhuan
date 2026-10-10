package com.xiguli.langhuan.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** V95 custom glyphs for the four root tabs (by route key); other keys keep their own icons. */
internal fun tabGlyphV95(key: String, selected: Boolean): ImageVector? {
    val i = LanghuanTabIconsV95
    return when (key) {
        "SHELF" -> if (selected) i.ShelfFilled else i.ShelfOutline
        "ONLINE" -> if (selected) i.StoreFilled else i.StoreOutline
        "CREATE_HUB" -> if (selected) i.CreateFilled else i.CreateOutline
        "MINE" -> if (selected) i.MineFilled else i.MineOutline
        else -> null
    }
}

/** One destination of the bottom bar. */
data class BottomTabV94(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Boolean = false,
    val badgeDescription: String? = null,
)

internal const val BOTTOM_BAR_HEIGHT_DP_V94 = 56

/** A hairline that is always exactly one device pixel thick (0.5 dp rounded to whole pixels). */
@Composable
internal fun hairlineDpV96(): Dp = with(LocalDensity.current) { 0.5.dp.toPx().roundToInt().coerceAtLeast(1).toDp() }

/** Bar surface: a touch lighter than the page paper (light) / a touch lifted from the ink (dark). */
@Composable
internal fun bottomBarSurfaceV96(): Color {
    val t = LocalLanghuanUiTokens.current
    return if (t.background.luminance() < 0.5f) lerp(t.background, t.card, 0.6f) else lerp(t.background, Color.White, 0.45f)
}

/**
 * V96 bottom navigation, tuned after the iOS / Apple Books tab bar and 微信读书.
 *
 * - Surface: paper a shade lighter than the page (deep ink a shade lifted in dark mode) under a
 *   true one-pixel hairline; no elevation, no pill indicator.
 * - Glyphs: custom 24 dp icons with a 1.5 dp round stroke ([LanghuanTabIconsV95]). Selecting a tab
 *   floods the outline into its filled twin (same silhouette) with a short cross-fade, tints it with
 *   the accent and gives it a soft spring "pop" (0.86 → 1).
 * - Labels: 11 sp, medium weight + accent when selected, regular muted otherwise, slight tracking,
 *   never wrapping.
 * - Touch: the whole column (≥ 56 dp) is the target. Pressing dims the glyph, shrinks it a little
 *   and fades in a soft rounded wash behind it (no Material ripple). Reselecting the current tab
 *   gives a light haptic tick and calls [onReselect] (pages scroll back to top).
 * - Insets: the bar paints behind the gesture / 3-button navigation bar and pads its content above.
 */
@Composable
fun LanghuanBottomBarV94(
    tabs: List<BottomTabV94>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (String) -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    val haptics = LocalHapticFeedback.current
    val surface = bottomBarSurfaceV96()
    val dark = t.background.luminance() < 0.5f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surface)
            .testTag("langhuan-bottom-bar"),
    ) {
        Box(Modifier.fillMaxWidth().height(hairlineDpV96()).background(t.border.copy(alpha = if (dark) 0.95f else 0.85f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BOTTOM_BAR_HEIGHT_DP_V94.dp)
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.key == selectedKey
                BottomTabItemV96(
                    tab = tab,
                    selected = selected,
                    surface = surface,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            TabReselectBusV95.reselect(tab.key)
                            onReselect(tab.key)
                        } else {
                            onSelect(tab.key)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomTabItemV96(
    tab: BottomTabV94,
    selected: Boolean,
    surface: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val idle = if (t.background.luminance() < 0.5f) t.mutedForeground else t.mutedForeground.copy(alpha = 0.92f)
    val tint by animateColorAsState(if (selected) t.primary else idle, tween(200), label = "tab-tint")
    val wash by animateFloatAsState(
        if (pressed) 1f else 0f,
        if (pressed) tween(90) else tween(260),
        label = "tab-wash",
    )
    val press by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab-press",
    )
    // Spring "pop" only when a tab becomes selected (not on first composition).
    val pop = remember { Animatable(1f) }
    val wasSelected = remember { booleanArrayOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected[0]) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 520f))
        }
        wasSelected[0] = selected
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { if (tab.badge && tab.badgeDescription != null) stateDescription = tab.badgeDescription }
            .testTag("bottom-tab-${tab.key}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft press wash behind the glyph (iOS-like highlight instead of a ripple).
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .graphicsLayer { alpha = wash }
                    .background(t.foreground.copy(alpha = 0.07f), CircleShape),
            )
            Box(
                Modifier.graphicsLayer {
                    val s = pop.value * press
                    scaleX = s
                    scaleY = s
                    alpha = if (pressed) 0.7f else 1f
                },
            ) {
                Crossfade(targetState = selected, animationSpec = tween(180), label = "tab-glyph") { on ->
                    Icon(
                        imageVector = tabGlyphV95(tab.key, on) ?: if (on) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                    )
                }
                if (tab.badge) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(10.dp)
                            .background(surface, CircleShape)
                            .padding(1.5.dp)
                            .background(t.primary, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.2.sp,
            ),
            color = tint,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
