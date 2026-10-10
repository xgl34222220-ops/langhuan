package com.xiguli.langhuan.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One destination of the bottom bar. */
data class BottomTabV94(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Boolean = false,
    val badgeDescription: String? = null,
)

internal const val BOTTOM_BAR_HEIGHT_DP_V94 = 54

/**
 * V95 bottom navigation (iOS tab bar x Material hybrid).
 *
 * - Surface: the page's paper colour, a 0.5 dp hairline on top, no elevation or pill indicator.
 * - Glyphs: custom 24 dp line icons ([LanghuanTabIconsV95]); the selected tab swaps to the filled
 *   glyph with a short cross-fade and a soft spring "pop", tinted with the accent colour.
 * - Labels: 10.5 sp, medium weight when selected, regular otherwise, never wrapping.
 * - Touch: the whole column is the target (>= 54 dp tall); pressing dims the glyph slightly like
 *   iOS. Reselecting the current tab gives a light haptic tick and calls [onReselect] (pages scroll
 *   back to top). The bar pads itself for the gesture/3-button navigation bar.
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(t.background)
            .testTag("langhuan-bottom-bar"),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BOTTOM_BAR_HEIGHT_DP_V94.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.key == selectedKey
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val tint by animateColorAsState(
                    if (selected) t.primary else t.mutedForeground,
                    tween(180),
                    label = "tab-tint",
                )
                val pop by animateFloatAsState(
                    if (selected) 1f else 0.94f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "tab-pop",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onReselect(tab.key)
                                } else {
                                    onSelect(tab.key)
                                }
                            },
                        )
                        .semantics { if (tab.badge && tab.badgeDescription != null) stateDescription = tab.badgeDescription }
                        .testTag("bottom-tab-${tab.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.graphicsLayer {
                            val s = pop * if (pressed) 0.92f else 1f
                            scaleX = s
                            scaleY = s
                            alpha = if (pressed) 0.6f else 1f
                        },
                    ) {
                        Crossfade(targetState = selected, animationSpec = tween(160), label = "tab-glyph") { on ->
                            Icon(
                                imageVector = if (on) tab.selectedIcon else tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = tint,
                            )
                        }
                        if (tab.badge) FlatDotV93(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 0.3.sp,
                        ),
                        color = tint,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
