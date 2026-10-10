package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One destination of the V94 bottom bar. */
data class BottomTabV94(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Boolean = false,
    val badgeDescription: String? = null,
)

internal const val BOTTOM_BAR_HEIGHT_DP_V94 = 56

/**
 * V94 flat bottom navigation: a hairline on top, thin outline glyphs, small labels, and the accent
 * colour only for the selected destination. No pill indicator, no elevation — it matches the V93
 * flat kit. The bar pads itself for the gesture/navigation bar.
 */
@Composable
fun LanghuanBottomBarV94(
    tabs: List<BottomTabV94>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(t.background)
            .testTag("langhuan-bottom-bar"),
    ) {
        FlatHairlineV93()
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
                val tint = if (selected) t.primary else t.mutedForeground
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.key) })
                        .semantics { if (tab.badge && tab.badgeDescription != null) stateDescription = tab.badgeDescription }
                        .testTag("bottom-tab-${tab.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                            tint = tint,
                        )
                        if (tab.badge) FlatDotV93(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = tint,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
