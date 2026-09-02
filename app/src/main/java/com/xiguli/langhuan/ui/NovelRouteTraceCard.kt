package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.NovelRouteDecision
import com.xiguli.langhuan.engine.NovelRouteStatus

/** Compact, inspectable execution trace for the latest natural-language creation turn. */
@Composable
internal fun NovelRouteTraceCardV4(route: NovelRouteDecision) {
    var expanded by remember(route.summary, route.status) { mutableStateOf(false) }
    val statusText = when (route.status) {
        NovelRouteStatus.SELECTED -> "已选择"
        NovelRouteStatus.RUNNING -> "执行中"
        NovelRouteStatus.SUCCESS -> "已完成"
        NovelRouteStatus.FAILED -> "执行失败"
    }
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .36f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    "${route.intent.label} · ${route.capabilities.size} 个能力",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (route.status) {
                        NovelRouteStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起执行详情" else "查看执行详情",
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!expanded && route.capabilities.isNotEmpty()) {
            Text(
                route.capabilities.take(4).joinToString(" · ") { it.label } +
                    if (route.capabilities.size > 4) " · +${route.capabilities.size - 4}" else "",
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            if (route.capabilities.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    route.capabilities.forEach { capability ->
                        Text(
                            capability.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = .8f))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            route.capabilities.forEach { capability ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(capability.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Text(
                            capability.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (route.reasons.isNotEmpty()) {
                Text(
                    route.reasons.joinToString(" · "),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
