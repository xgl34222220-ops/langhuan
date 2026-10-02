package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.net.URI


/**
 * 单书源发现页。
 *
 * sections / books 都由真实书源引擎提供：
 * SourceDiscoveryV41 + OnlineBookV36。
 */
@Composable
internal fun BookSourceDiscoveryScreenV50(
    source: BookSourceV36,
    sections: List<SourceDiscoveryV41>,
    selectedSection: SourceDiscoveryV41?,
    books: List<OnlineBookV36>,
    loading: Boolean,
    hasMore: Boolean,
    pageError: String? = null,
    browserVerificationRequired: Boolean = false,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEditRules: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSelectSection: (SourceDiscoveryV41) -> Unit,
    onOpenBook: (OnlineBookV36) -> Unit,
    onLoadMore: () -> Unit,
    onStop: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.space4, vertical = t.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceDiscoveryIconButtonV50(
                icon = Icons.Rounded.ArrowBack,
                description = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = sourceDiscoveryHostV50(source.baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "编辑规则",
                modifier = Modifier
                    .clickable(onClick = onEditRules)
                    .padding(t.space2),
                style = MaterialTheme.typography.labelLarge,
                color = t.primary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.space4)
                .background(
                    if (source.enabled) t.accent else t.input,
                    RoundedCornerShape(t.radiusMd),
                )
                .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
                .padding(t.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Source,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (source.enabled) t.accentForeground else t.secondaryForeground,
            )
            Spacer(Modifier.width(t.space2))
            Text(
                text = if (source.enabled) {
                    "已启用 · 可在书城实测搜索与发现"
                } else {
                    "已停用"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (source.enabled) t.accentForeground else t.mutedForeground,
            )
            SourceDiscoverySwitchV50(
                checked = source.enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        if (browserVerificationRequired) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = t.space4,
                        end = t.space4,
                        top = t.space2,
                    )
                    .background(
                        t.goldContainer,
                        RoundedCornerShape(t.radiusMd),
                    )
                    .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
                    .padding(t.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = t.goldForeground,
                )
                Spacer(Modifier.width(t.space2))
                Text(
                    text = "需浏览器验证",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.goldForeground,
                )
            }
        }

        if (sections.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(top = t.space3),
                contentPadding = PaddingValues(horizontal = t.space4),
                horizontalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                items(
                    items = sourceDiscoveryOrderV50(sections),
                    key = { "${it.sourceId}:${it.template}" },
                ) { section ->
                    SourceDiscoveryChipV50(
                        text = section.label,
                        selected = section == selectedSection,
                        onClick = { onSelectSection(section) },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space4,
                bottom = t.space5,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space3),
        ) {
            item("discovery-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedSection?.label ?: "书源发现",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (books.isNotEmpty()) {
                        Text(
                            text = "${books.size} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = t.mutedForeground,
                        )
                    }
                }
            }

            if (pageError != null) {
                item("discovery-error") {
                    SourceDiscoveryErrorV50(
                        message = pageError,
                        onRetry = onLoadMore,
                    )
                }
            }

            if (loading) {
                item("discovery-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = t.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = t.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(t.space2))
                        Text(
                            text = "正在读取发现页面…",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                        Text(
                            text = "停止",
                            modifier = Modifier
                                .clickable(onClick = onStop)
                                .padding(t.space2),
                            style = MaterialTheme.typography.labelMedium,
                            color = t.primary,
                        )
                    }
                }
            }

            if (!loading && books.isEmpty()) {
                item("discovery-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(t.input, RoundedCornerShape(t.radiusMd))
                            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
                            .padding(t.space5),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (selectedSection == null) {
                                "选择一个网站真实提供的分类或榜单。"
                            } else {
                                "这个入口暂时没有读取到书籍。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.mutedForeground,
                        )
                    }
                }
            }

            items(
                items = books,
                key = { "${it.sourceId}:${it.bookUrl}" },
            ) { book ->
                SourceDiscoveryBookRowV50(
                    book = book,
                    category = selectedSection?.label,
                    onClick = { onOpenBook(book) },
                )
            }

            if (books.isNotEmpty() && !loading) {
                item("discovery-more") {
                    val shape = RoundedCornerShape(t.radiusMd)
                    val actionable = hasMore || pageError != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                if (actionable) t.card else t.input,
                                shape,
                            )
                            .border(1.dp, t.border, shape)
                            .clickable(
                                enabled = actionable,
                                onClick = onLoadMore,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                pageError != null -> "重试本页"
                                hasMore -> "加载更多"
                                else -> "已经到底了"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (actionable) t.primary else t.mutedForeground,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun SourceDiscoveryBookRowV50(
    book: OnlineBookV36,
    category: String?,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .clickable(onClick = onClick)
            .padding(t.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.name.ifBlank { "未命名书籍" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                category?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.width(t.space2))
                    Text(
                        text = it,
                        modifier = Modifier
                            .background(
                                if (sourceDiscoveryRankingV50(it)) {
                                    t.goldContainer
                                } else {
                                    t.accent
                                },
                                RoundedCornerShape(t.radiusSm),
                            )
                            .border(1.dp, t.border, RoundedCornerShape(t.radiusSm))
                            .padding(
                                horizontal = t.space2,
                                vertical = t.space1,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sourceDiscoveryRankingV50(it)) {
                            t.goldForeground
                        } else {
                            t.accentForeground
                        },
                    )
                }
            }
            Spacer(Modifier.height(t.space2))
            Text(
                text = book.intro.trim().ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = "${book.author.ifBlank { "佚名" }} · ${book.sourceName}",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(t.space2))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.mutedForeground,
        )
    }
}


@Composable
private fun SourceDiscoveryErrorV50(
    message: String,
    onRetry: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.destructive.copy(alpha = 0.08f), shape)
            .border(1.dp, t.border, shape)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.destructive,
        )
        Spacer(Modifier.width(t.space2))
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = t.destructive,
        )
        Text(
            text = "重试",
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(t.space2),
            style = MaterialTheme.typography.labelMedium,
            color = t.primary,
        )
    }
}


@Composable
private fun SourceDiscoveryChipV50(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .height(38.dp)
            .background(if (selected) t.accent else t.card, shape)
            .border(1.dp, if (selected) t.primary else t.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = t.space3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}


@Composable
private fun SourceDiscoveryIconButtonV50(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = t.secondaryForeground,
        )
    }
}


@Composable
private fun SourceDiscoverySwitchV50(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusXl)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .background(if (checked) t.primary else t.input, shape)
            .border(1.dp, if (checked) t.primary else t.border, shape)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = if (checked) 20.dp else 2.dp,
                    top = 2.dp,
                )
                .size(20.dp)
                .background(
                    if (checked) t.card else t.mutedForeground,
                    RoundedCornerShape(t.radiusSm),
                ),
        )
    }
}


private fun sourceDiscoveryOrderV50(
    sections: List<SourceDiscoveryV41>,
): List<SourceDiscoveryV41> {
    val preferred = listOf(
        "悬疑",
        "仙侠",
        "武侠",
        "青春",
        "月票榜",
        "完结榜",
    )
    val result = ArrayList<SourceDiscoveryV41>()
    preferred.forEach { wanted ->
        sections.firstOrNull { section ->
            sourceDiscoveryMatchesV50(
                wanted = wanted,
                actual = section.label,
            )
        }?.let { if (it !in result) result += it }
    }
    sections.forEach {
        if (it !in result) result += it
    }
    return result
}


private fun sourceDiscoveryMatchesV50(
    wanted: String,
    actual: String,
): Boolean {
    val aliases = when (wanted) {
        "悬疑" -> listOf("悬疑", "推理", "灵异")
        "仙侠" -> listOf("仙侠", "修仙", "仙道")
        "武侠" -> listOf("武侠", "武道")
        "青春" -> listOf("青春", "校园")
        "月票榜" -> listOf("月票", "月榜")
        "完结榜" -> listOf("完结榜", "完本榜", "完结", "完本")
        else -> listOf(wanted)
    }
    return aliases.any { actual.contains(it, ignoreCase = true) }
}


private fun sourceDiscoveryRankingV50(label: String): Boolean =
    label.contains("榜") ||
        label.contains("排行") ||
        label.contains("月票")


private fun sourceDiscoveryHostV50(url: String): String =
    runCatching { URI(url).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: url
