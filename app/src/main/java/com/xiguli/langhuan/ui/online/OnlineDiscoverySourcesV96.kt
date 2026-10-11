package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/** One enabled source's discovery categories, in the order the source declares them. */
internal data class OnlineDiscoverySourceV96(
    val sourceId: String,
    val name: String,
    val sections: List<SourceDiscoveryV41>,
)

/** An enabled source that contributes no category, with the reason shown to the user. */
internal data class OnlineDiscoveryIssueV96(val sourceName: String, val reason: String)

internal data class OnlineDiscoveryCatalogV96(
    val sources: List<OnlineDiscoverySourceV96>,
    val issues: List<OnlineDiscoveryIssueV96>,
)

/** 书城「发现」: categories grouped per enabled source; sources without usable categories are reported, not dropped. */
internal fun onlineDiscoveryCatalogV96(sources: List<BookSourceV36>): OnlineDiscoveryCatalogV96 {
    val groups = ArrayList<OnlineDiscoverySourceV96>()
    val issues = ArrayList<OnlineDiscoveryIssueV96>()
    sources.filter { it.enabled }.forEach { source ->
        val name = source.name.ifBlank { source.id }
        when {
            !source.enabledExplore -> issues += OnlineDiscoveryIssueV96(name, "已关闭发现")
            source.exploreUrl.isBlank() -> issues += OnlineDiscoveryIssueV96(name, "未配置发现分类")
            else -> {
                val catalog = runCatching { sourceDiscoveryCatalogV41(source) }
                    .getOrElse { SourceDiscoveryCatalogV41(emptyList(), listOf("发现分类解析失败：${it.message.orEmpty().take(80)}")) }
                if (catalog.sections.isEmpty()) {
                    issues += OnlineDiscoveryIssueV96(name, catalog.issues.firstOrNull() ?: "没有可用的发现分类")
                } else {
                    groups += OnlineDiscoverySourceV96(source.id, name, catalog.sections)
                }
            }
        }
    }
    return OnlineDiscoveryCatalogV96(groups, issues)
}

/** 「全部书源」: round-robin so the first visible chips come from every source instead of the first one. */
internal fun interleaveDiscoveriesV96(groups: List<OnlineDiscoverySourceV96>): List<SourceDiscoveryV41> {
    val result = ArrayList<SourceDiscoveryV41>(groups.sumOf { it.sections.size })
    val longest = groups.maxOfOrNull { it.sections.size } ?: 0
    for (index in 0 until longest) groups.forEach { group -> group.sections.getOrNull(index)?.let(result::add) }
    return result
}

/** Chips for the selected source, or all sources interleaved when none (or a vanished one) is selected. */
internal fun visibleDiscoveriesV96(groups: List<OnlineDiscoverySourceV96>, selectedSourceId: String?): List<SourceDiscoveryV41> =
    groups.firstOrNull { it.sourceId == selectedSourceId }?.sections ?: interleaveDiscoveriesV96(groups)

/** Two sources can both call a category 「玄幻」: keep both and name the source on each duplicate. */
internal fun discoveryChipLabelsV96(visible: List<SourceDiscoveryV41>, groups: List<OnlineDiscoverySourceV96>): List<String> {
    val names = groups.associate { it.sourceId to it.name }
    val duplicateLabels = visible.groupBy { it.label }.filterValues { list -> list.map { it.sourceId }.distinct().size > 1 }.keys
    return visible.map { discovery ->
        if (discovery.label in duplicateLabels) "${discovery.label}·${(names[discovery.sourceId] ?: discovery.sourceId).take(8)}" else discovery.label
    }
}

/** 「N 个书源暂无发现」, collapsed by default; expands to one line per source with its reason. */
@Composable
internal fun OnlineDiscoveryIssuesV96(issues: List<OnlineDiscoveryIssueV96>, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) return
    val t = LocalLanghuanUiTokens.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = "${issues.size} 个书源暂无发现 ${if (expanded) "▴" else "▾"}",
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = t.space1),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
        )
        if (expanded) {
            issues.forEach { issue ->
                Text(
                    text = "${issue.sourceName.take(24)}：${issue.reason}",
                    modifier = Modifier.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
