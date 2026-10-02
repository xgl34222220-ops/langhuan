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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.net.URI


private val SourceBrowsePrototypeLabelsV50 = listOf(
    "悬疑",
    "仙侠",
    "武侠",
    "青春",
    "月票榜",
    "完结榜",
)


/**
 * 单个书源浏览页。
 *
 * 只展示 BookSourceV36 / SourceDiscoveryV41 / OnlineBookV36
 * 已经存在的真实数据。
 *
 * 启用、编辑、复制、删除、发现请求全部交给上层。
 */
@Composable
internal fun BookSourceBrowseScreenV50(
    source: BookSourceV36,
    discoveries: List<SourceDiscoveryV41>,
    previewBooks: List<OnlineBookV36>,
    selectedDiscovery: SourceDiscoveryV41? = null,
    loading: Boolean = false,
    browserVerificationRequired: Boolean = false,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEditRules: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenDiscovery: (SourceDiscoveryV41) -> Unit,
    onOpenBook: (OnlineBookV36) -> Unit,
    onCopySourceJson: () -> Unit,
    onDeleteSource: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        SourceBrowseHeaderV50(
            source = source,
            onBack = onBack,
            onEditRules = onEditRules,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space3,
                bottom = t.space5,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space4),
        ) {
            item("source-state") {
                SourceBrowseStateCardV50(
                    source = source,
                    browserVerificationRequired = browserVerificationRequired,
                    onEnabledChange = onEnabledChange,
                )
            }

            item("source-categories") {
                SourceBrowseCategoriesV50(
                    discoveries = discoveries,
                    selected = selectedDiscovery,
                    onSelect = onOpenDiscovery,
                )
            }

            item("source-books-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedDiscovery?.label ?: "书源发现",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (previewBooks.isNotEmpty()) {
                        Text(
                            text = "${previewBooks.size} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = t.mutedForeground,
                        )
                    }
                }
            }

            if (loading) {
                item("source-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = t.space5),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = t.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(t.space2))
                        Text(
                            text = "正在读取网站实际内容…",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }
            } else if (previewBooks.isEmpty()) {
                item("source-empty") {
                    SourceBrowseEmptyV50(
                        text = if (discoveries.isEmpty()) {
                            "这个书源暂时没有可浏览的发现入口。可编辑规则后回书城实测。"
                        } else {
                            "这个分类暂时没有读取到书籍。"
                        },
                    )
                }
            } else {
                items(
                    items = previewBooks,
                    key = { "${it.sourceId}:${it.bookUrl}" },
                ) { book ->
                    SourceBrowseBookCardV50(
                        book = book,
                        category = selectedDiscovery?.label,
                        onClick = { onOpenBook(book) },
                    )
                }
            }

            item("source-note") {
                Text(
                    text = "书源浏览只展示网站实际解析结果。分类、榜单和书籍不会由琅嬛补造。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }

        SourceBrowseBottomActionsV50(
            onEditRules = onEditRules,
            onCopySourceJson = onCopySourceJson,
            onDeleteSource = onDeleteSource,
        )
    }
}


@Composable
private fun SourceBrowseHeaderV50(
    source: BookSourceV36,
    onBack: () -> Unit,
    onEditRules: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBrowseIconButtonV50(
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
                text = sourceHostV50(source.baseUrl),
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
                .padding(horizontal = t.space2, vertical = t.space2),
            style = MaterialTheme.typography.labelLarge,
            color = t.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
private fun SourceBrowseStateCardV50(
    source: BookSourceV36,
    browserVerificationRequired: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (source.enabled) t.accent else t.input,
                        RoundedCornerShape(t.radiusMd),
                    )
                    .border(1.dp, t.border, RoundedCornerShape(t.radiusMd)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Source,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (source.enabled) {
                        t.accentForeground
                    } else {
                        t.secondaryForeground
                    },
                )
            }
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (source.enabled) "已启用" else "已停用",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = "可在书城实测搜索与发现",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            SourceBrowseSwitchV50(
                checked = source.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
        if (browserVerificationRequired) {
            Spacer(Modifier.height(t.space3))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
    }
}


@Composable
private fun SourceBrowseCategoriesV50(
    discoveries: List<SourceDiscoveryV41>,
    selected: SourceDiscoveryV41?,
    onSelect: (SourceDiscoveryV41) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column {
        Text(
            text = "发现",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            items(
                items = SourceBrowsePrototypeLabelsV50,
                key = { it },
            ) { label ->
                val section = sourceDiscoveryForLabelV50(
                    label = label,
                    discoveries = discoveries,
                )
                SourceBrowseChipV50(
                    text = label,
                    selected = section != null && section == selected,
                    enabled = section != null,
                    onClick = { section?.let(onSelect) },
                )
            }
        }
        val extras = discoveries.filter { discovery ->
            SourceBrowsePrototypeLabelsV50.none { label ->
                sourceDiscoveryForLabelV50(
                    label = label,
                    discoveries = listOf(discovery),
                ) != null
            }
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(t.space2))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                items(
                    items = extras,
                    key = { "${it.sourceId}:${it.template}" },
                ) { section ->
                    SourceBrowseChipV50(
                        text = section.label,
                        selected = section == selected,
                        enabled = true,
                        onClick = { onSelect(section) },
                    )
                }
            }
        }
    }
}


@Composable
private fun SourceBrowseBookCardV50(
    book: OnlineBookV36,
    category: String?,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .clickable(onClick = onClick)
            .padding(t.space4),
    ) {
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
                SourceBrowseLabelV50(
                    text = it,
                    gold = isRankingSourceLabelV50(it),
                )
            }
        }
        Spacer(Modifier.height(t.space2))
        Text(
            text = book.intro.trim().ifBlank {
                book.latest.takeIf { it.isNotBlank() }
                    ?.let { "最新：$it" }
                    ?: "暂无简介"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(t.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.author.ifBlank { "佚名" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.sourceName.ifBlank { "当前书源" },
                style = MaterialTheme.typography.labelSmall,
                color = t.primary,
                maxLines = 1,
            )
        }
    }
}


@Composable
private fun SourceBrowseBottomActionsV50(
    onEditRules: () -> Unit,
    onCopySourceJson: () -> Unit,
    onDeleteSource: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = t.space4,
                end = t.space4,
                top = t.space2,
                bottom = t.space3,
            ),
        verticalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        SourceBrowseOperationV50(
            icon = Icons.Rounded.Edit,
            title = "编辑规则",
            subtitle = "保存只校验格式，可用性请回书城实测",
            onClick = onEditRules,
        )
        SourceBrowseOperationV50(
            icon = Icons.Rounded.ContentCopy,
            title = "复制书源 JSON",
            subtitle = "自定义请求头也会包含在内",
            onClick = onCopySourceJson,
        )
        SourceBrowseOperationV50(
            icon = Icons.Rounded.DeleteOutline,
            title = "删除书源",
            subtitle = "已加入书架的书不受影响",
            destructive = true,
            onClick = onDeleteSource,
        )
    }
}


@Composable
private fun SourceBrowseOperationV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (destructive) t.destructive.copy(alpha = 0.08f) else t.card,
                shape,
            )
            .border(1.dp, t.border, shape)
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (destructive) t.destructive else t.secondaryForeground,
        )
        Spacer(Modifier.width(t.space3))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) t.destructive else t.foreground,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}


@Composable
private fun SourceBrowseEmptyV50(text: String) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.input, RoundedCornerShape(t.radiusMd))
            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
            .padding(t.space5),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = t.mutedForeground,
        )
    }
}


@Composable
private fun SourceBrowseIconButtonV50(
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
private fun SourceBrowseSwitchV50(
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


@Composable
private fun SourceBrowseChipV50(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .height(38.dp)
            .background(
                when {
                    selected -> t.accent
                    enabled -> t.card
                    else -> t.input
                },
                shape,
            )
            .border(
                1.dp,
                if (selected) t.primary else t.border,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = t.space3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                selected -> t.accentForeground
                enabled -> t.secondaryForeground
                else -> t.mutedForeground.copy(alpha = 0.42f)
            },
        )
    }
}


@Composable
private fun SourceBrowseLabelV50(
    text: String,
    gold: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Text(
        text = text,
        modifier = Modifier
            .background(if (gold) t.goldContainer else t.accent, shape)
            .border(1.dp, t.border, shape)
            .padding(horizontal = t.space2, vertical = t.space1),
        style = MaterialTheme.typography.labelSmall,
        color = if (gold) t.goldForeground else t.accentForeground,
    )
}


private fun sourceHostV50(url: String): String =
    runCatching { URI(url).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: url


private fun sourceDiscoveryForLabelV50(
    label: String,
    discoveries: List<SourceDiscoveryV41>,
): SourceDiscoveryV41? {
    val aliases = when (label) {
        "悬疑" -> listOf("悬疑", "推理", "灵异")
        "仙侠" -> listOf("仙侠", "修仙", "仙道")
        "武侠" -> listOf("武侠", "武道")
        "青春" -> listOf("青春", "校园")
        "月票榜" -> listOf("月票", "月榜")
        "完结榜" -> listOf("完结榜", "完本榜", "完结", "完本")
        else -> listOf(label)
    }
    return discoveries.firstOrNull { section ->
        aliases.any { keyword ->
            section.label.contains(keyword, ignoreCase = true)
        }
    }
}


private fun isRankingSourceLabelV50(label: String): Boolean =
    label.contains("榜") ||
        label.contains("排行") ||
        label.contains("月票")
