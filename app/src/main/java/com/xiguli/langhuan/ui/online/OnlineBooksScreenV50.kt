package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible


/* -------------------------------------------------------------------------- */
/*                              Discovery UI                                  */
/* -------------------------------------------------------------------------- */

private enum class OnlineDiscoveryGroupV50(
    val label: String,
) {
    ALL("全部"),
    RANKING("排行榜"),
    CATEGORY("分类"),
}


private data class OnlinePrototypeCategoryV50(
    val label: String,
    val ranking: Boolean = false,
)


private val OnlinePrototypeCategoriesV50 = listOf(
    OnlinePrototypeCategoryV50("悬疑"),
    OnlinePrototypeCategoryV50("仙侠"),
    OnlinePrototypeCategoryV50("武侠"),
    OnlinePrototypeCategoryV50("青春"),
    OnlinePrototypeCategoryV50("月票榜", ranking = true),
    OnlinePrototypeCategoryV50("完结榜", ranking = true),
    OnlinePrototypeCategoryV50("科幻"),
    OnlinePrototypeCategoryV50("古言"),
    OnlinePrototypeCategoryV50("历史"),
)


/* -------------------------------------------------------------------------- */
/*                                  Screen                                    */
/* -------------------------------------------------------------------------- */

/**
 * 在线书城 V50。
 *
 * 本文件只负责：书城主页、搜索、最近搜索展示、真实书源发现入口映射、
 * 推荐 / 搜索结果列表、书籍详情页、目录展示。
 *
 * 网络请求、书源解析、加入书架、离线下载等真实动作
 * 全部通过外部回调交给 OnlineBooksViewModelV36。
 */
@Composable
internal fun OnlineBooksScreenV50(
    state: OnlineBooksStateV36,
    query: String,
    recentSearches: List<String>,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,

    onBack: () -> Unit,
    onManageSources: () -> Unit,

    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStopSearch: () -> Unit,
    onRetrySearch: () -> Unit,

    onRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,

    onDiscover: (SourceDiscoveryV41) -> Unit,
    onLoadMore: () -> Unit,

    onOpenBook: (OnlineBookV36) -> Unit,

    onCloseDetail: () -> Unit,
    onViewSource: (String) -> Unit,
    onAddToShelf: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onChapterClick: (OnlineChapterV36) -> Unit,
) {
    val detail = state.detail

    if (detail != null) {
        OnlineBookDetailScreenV50(
            detail = detail,
            loading = state.detailLoading,
            adding = state.addingToShelf,
            download = state.download,
            onBack = onCloseDetail,
            onViewSource = { onViewSource(detail.book.sourceId) },
            onRead = onRead,
            onAdd = onAddToShelf,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onChapterClick = onChapterClick,
            modifier = modifier,
        )
        return
    }

    val t = LocalLanghuanUiTokens.current
    val searchIncomplete = state.query.isNotBlank() && state.discoverySection == null &&
        (state.searchStopped || state.searchFailures.isNotEmpty())

    var discoveryGroup by rememberSaveable {
        mutableStateOf(OnlineDiscoveryGroupV50.ALL)
    }

    val discoveries = remember(state.sources) {
        state.sources
            .filter { it.enabled }
            .flatMap { sourceDiscoveriesV41(it) }
    }

    val currentCategoryLabel = state.discoverySection
        ?.label?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        /* Header */
        OnlineStoreHeaderV50(
            embedded = embedded,
            onBack = onBack,
            onManageSources = onManageSources,
        )

        /* Search */
        OnlineSearchFieldV50(
            query = query,
            searching = state.searching,
            onQueryChange = onQueryChange,
            onSearch = {
                val value = query.trim()
                if (value.isNotBlank()) onSearch(value)
            },
            onClear = { onQueryChange("") },
        )

        /* Main content */
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space3,
                bottom = t.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space4),
        ) {
            /* Recent searches */
            if (recentSearches.isNotEmpty() && query.isBlank() && state.query.isBlank()) {
                item(key = "recent-searches") {
                    OnlineRecentSearchesV50(
                        searches = recentSearches,
                        onSearch = { value ->
                            onQueryChange(value)
                            onRecentSearch(value)
                        },
                        onClear = onClearRecentSearches,
                    )
                }
            }

            /* Discovery entry */
            item(key = "discovery-entry") {
                OnlineDiscoveryEntryV50(
                    selected = discoveryGroup,
                    onSelect = { discoveryGroup = it },
                )
            }

            /* Categories */
            item(key = "discovery-categories") {
                OnlineCategorySectionV50(
                    discoveries = discoveries,
                    selected = state.discoverySection,
                    group = discoveryGroup,
                    onDiscover = onDiscover,
                )
            }

            /* Search / discovery status */
            if (state.searching) {
                item(key = "searching") {
                    OnlineSearchingCardV50(
                        text = if (state.discoverySection != null) {
                            "正在读取「${state.discoverySection.label}」…"
                        } else {
                            val searchable = state.sources.count {
                                it.enabled &&
                                    it.searchUrl.isNotBlank() &&
                                    it.searchList.isNotBlank()
                            }
                            "正在搜索 ${state.searchedSources}/$searchable 个书源"
                        },
                        onStop = onStopSearch,
                    )
                }
            }

            /* Errors */
            if (searchIncomplete) {
                item(key = "search-recovery") {
                    val reasons = state.searchFailures.take(3).joinToString("\n") {
                        "${it.sourceName.take(48)}：${it.reason}"
                    }
                    val summary = buildString {
                        append("「${state.query.take(60)}」")
                        append(if (state.searchStopped) "已停止搜索，已完成结果保留。" else "${state.failedSources} 个书源请求失败，已完成结果保留。")
                        if (state.pendingSearchSourceIds.isNotEmpty()) append("还有 ${state.pendingSearchSourceIds.size} 个书源未完成。")
                        if (reasons.isNotEmpty()) append('\n').append(reasons)
                        if (state.searchFailures.size > 3) append("\n另有 ${state.searchFailures.size - 3} 个书源失败。")
                    }
                    val canRetry = !state.searching && (state.pendingSearchSourceIds.isNotEmpty() || state.searchFailures.isNotEmpty())
                    OnlineMessageCardV50(
                        icon = if (state.searchStopped) Icons.Rounded.Stop else Icons.Rounded.ErrorOutline,
                        title = if (state.searchStopped) "搜索已停止" else "书源搜索未完成",
                        body = summary,
                        destructive = state.searchFailures.isNotEmpty(),
                        action = if (canRetry) "重试未完成书源" else null,
                        onAction = if (canRetry) onRetrySearch else null,
                    )
                }
            }
            state.sourceStorageError?.let {
                item(key = "storage-error") {
                    OnlineMessageCardV50(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "书源暂时无法读取",
                        body = it,
                        destructive = true,
                        action = "书源管理",
                        onAction = onManageSources,
                    )
                }
            }

            state.error?.takeIf { it.isNotBlank() }?.let {
                item(key = "online-error") {
                    OnlineMessageCardV50(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "本次读取没有完成",
                        body = it,
                        destructive = true,
                    )
                }
            }

            state.discoveryPageError?.takeIf { it.isNotBlank() }?.let {
                item(key = "discovery-error") {
                    OnlineMessageCardV50(
                        icon = Icons.Rounded.Refresh,
                        title = "这一页暂时没有读完",
                        body = it,
                        action = "重试",
                        onAction = onLoadMore,
                    )
                }
            }

            /* Recommendations / Results */
            item(key = "result-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            state.query.isNotBlank() -> "搜索结果"
                            currentCategoryLabel != null -> currentCategoryLabel
                            else -> "发现好书"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.results.isNotEmpty()) {
                        Text(
                            text = "${state.results.size} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = t.mutedForeground,
                        )
                    }
                }
            }

            if (state.results.isEmpty() && !state.searching && !searchIncomplete) {
                item(key = "empty-results") {
                    OnlineStoreEmptyV50(
                        hasSources = state.sources.any { it.enabled },
                        query = state.query,
                        discoveryLabel = currentCategoryLabel,
                        onManageSources = onManageSources,
                    )
                }
            } else {
                items(
                    items = state.results,
                    key = { "${it.sourceId}:${it.bookUrl}" },
                ) { book ->
                    OnlineBookResultRowV50(
                        book = book,
                        category = currentCategoryLabel,
                        onClick = { onOpenBook(book) },
                    )
                }
            }

            /* Load more */
            if (state.discoverySection != null && state.results.isNotEmpty()) {
                item(key = "load-more") {
                    OnlineLoadMoreV50(
                        loading = state.searching,
                        hasMore = state.discoveryHasMore,
                        page = state.discoveryPage,
                        onClick = onLoadMore,
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineStoreHeaderV50(
    embedded: Boolean,
    onBack: () -> Unit,
    onManageSources: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!embedded) {
            OnlineIconButtonV50(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(t.space3))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "在线书城",
                style = MaterialTheme.typography.headlineLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "从已启用书源发现新的故事",
                modifier = Modifier.padding(top = t.space1),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
        OnlineTextButtonV50(
            icon = Icons.Rounded.Source,
            text = "书源管理",
            onClick = onManageSources,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Search                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineSearchFieldV50(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4)
            .height(50.dp)
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.width(t.space2))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isBlank()) {
                Text(
                    text = "搜索书名或作者",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = t.foreground,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(t.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank() && !searching) onSearch()
                    },
                ),
            )
        }
        if (query.isNotBlank()) {
            OnlineBareIconButtonV50(
                icon = Icons.Rounded.Close,
                contentDescription = "清空搜索",
                onClick = onClear,
            )
        }
        Spacer(Modifier.width(t.space1))
        val searchShape = RoundedCornerShape(t.radiusMd)
        val searchEnabled = query.isNotBlank() && !searching
        Box(
            modifier = Modifier
                .height(36.dp)
                .background(
                    color = if (searchEnabled) t.primary else t.border,
                    shape = searchShape,
                )
                .border(
                    width = 1.dp,
                    color = if (searchEnabled) t.primary else t.border,
                    shape = searchShape,
                )
                .clickable(enabled = searchEnabled, onClick = onSearch)
                .padding(horizontal = t.space3),
            contentAlignment = Alignment.Center,
        ) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = t.mutedForeground,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (query.isNotBlank()) t.card else t.mutedForeground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                            Recent Searches                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineRecentSearchesV50(
    searches: List<String>,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近搜索",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "清空",
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(horizontal = t.space2, vertical = t.space1),
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
        }
        Spacer(Modifier.height(t.space2))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            items(
                items = searches
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(10),
                key = { it },
            ) { value ->
                OnlineChipV50(
                    text = value,
                    selected = false,
                    onClick = { onSearch(value) },
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                            Discovery Entry                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineDiscoveryEntryV50(
    selected: OnlineDiscoveryGroupV50,
    onSelect: (OnlineDiscoveryGroupV50) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "发现",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
            OnlineDiscoveryGroupV50.entries.forEach { group ->
                OnlineDiscoveryCardV50(
                    group = group,
                    selected = group == selected,
                    onClick = { onSelect(group) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.OnlineDiscoveryCardV50(
    group: OnlineDiscoveryGroupV50,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    val icon = when (group) {
        OnlineDiscoveryGroupV50.ALL -> Icons.Rounded.AutoStories
        OnlineDiscoveryGroupV50.RANKING -> Icons.Rounded.TrendingUp
        OnlineDiscoveryGroupV50.CATEGORY -> Icons.Rounded.ManageSearch
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .background(
                color = if (selected) t.primary else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(t.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) t.card else t.primary,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.card else t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Categories                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineCategorySectionV50(
    discoveries: List<SourceDiscoveryV41>,
    selected: SourceDiscoveryV41?,
    group: OnlineDiscoveryGroupV50,
    onDiscover: (SourceDiscoveryV41) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val prototypeCategories = OnlinePrototypeCategoriesV50.filter {
        when (group) {
            OnlineDiscoveryGroupV50.ALL -> true
            OnlineDiscoveryGroupV50.RANKING -> it.ranking
            OnlineDiscoveryGroupV50.CATEGORY -> !it.ranking
        }
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (group) {
                    OnlineDiscoveryGroupV50.ALL -> "分类"
                    OnlineDiscoveryGroupV50.RANKING -> "排行榜"
                    OnlineDiscoveryGroupV50.CATEGORY -> "分类"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(t.space2))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            items(
                items = prototypeCategories,
                key = { it.label },
            ) { category ->
                val matched = onlineFindDiscoveryV50(discoveries, category)
                OnlineChipV50(
                    text = category.label,
                    selected = selected?.label == category.label,
                    onClick = {
                        val target = matched
                        if (target != null) onDiscover(target)
                    },
                )
            }
        }
        if (discoveries.isNotEmpty()) {
            Spacer(Modifier.height(t.space2))
            Text(
                text = "已启用书源的发现入口",
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
            Spacer(Modifier.height(t.space1))
            discoveries.forEach { discovery ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onDiscover(discovery) })
                        .padding(vertical = t.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (onlineIsRankingLabelV50(discovery.label)) {
                            Icons.Rounded.TrendingUp
                        } else {
                            Icons.Rounded.FolderOpen
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = t.mutedForeground,
                    )
                    Spacer(Modifier.width(t.space2))
                    Text(
                        text = discovery.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.foreground,
                    )
                    if (selected?.label == discovery.label) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = t.primary,
                        )
                    }
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Book Result                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineBookResultRowV50(
    book: OnlineBookV36,
    category: String?,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnlineCoverV50(
            coverUrl = book.cover,
            contentDescription = book.name,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(3f / 4f),
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    modifier = Modifier.padding(top = t.space1),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = t.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (category != null) {
                    OnlineLabelV50(text = category)
                    Spacer(Modifier.width(t.space1))
                }
                Text(
                    text = book.sourceName.ifBlank { book.sourceId },
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (book.intro.isNotBlank()) {
                Text(
                    text = book.intro,
                    modifier = Modifier.padding(top = t.space1),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Searching                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineSearchingCardV50(
    text: String,
    onStop: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = t.primary,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(t.space3))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
        )
        OnlineBareIconButtonV50(
            icon = Icons.Rounded.Stop,
            contentDescription = "停止",
            onClick = onStop,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Load More                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineLoadMoreV50(
    loading: Boolean,
    hasMore: Boolean,
    page: Int,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = t.primary,
                strokeWidth = 2.dp,
            )
        } else if (hasMore) {
            OnlineSecondaryButtonV50(
                text = if (page > 1) "加载第 ${page} 页" else "加载更多",
                onClick = onClick,
            )
        } else {
            Text(
                text = "已经到底了",
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Empty State                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineStoreEmptyV50(
    hasSources: Boolean,
    query: String,
    discoveryLabel: String?,
    onManageSources: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (hasSources) Icons.Rounded.MenuBook else Icons.Rounded.Source,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space3))
        Text(
            text = when {
                !hasSources -> "还没有启用书源"
                query.isNotBlank() -> "没有找到「$query」"
                discoveryLabel != null -> "「$discoveryLabel」暂时没有内容"
                else -> "试试搜索或浏览分类"
            },
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = if (!hasSources) {
                "先去书源管理启用书源，才能发现好书"
            } else {
                "换个关键词或分类试试"
            },
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            textAlign = TextAlign.Center,
        )
        if (!hasSources) {
            Spacer(Modifier.height(t.space3))
            OnlinePrimaryButtonV50(
                text = "去启用书源",
                onClick = onManageSources,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Messages                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineMessageCardV50(
    icon: ImageVector,
    title: String,
    body: String,
    destructive: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(
                width = 1.dp,
                color = if (destructive) t.primary else t.border,
                shape = shape,
            )
            .padding(t.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) t.primary else t.mutedForeground,
            )
            Spacer(Modifier.width(t.space2))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(t.space1))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(t.space2))
            OnlineTextButtonV50(text = action, onClick = onAction)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                           Book Detail Screen                               */
/* -------------------------------------------------------------------------- */

@Composable
internal fun OnlineBookDetailScreenV50(
    detail: OnlineDetailV36,
    loading: Boolean,
    adding: Boolean,
    download: OnlineDownloadV36?,
    onBack: () -> Unit,
    onViewSource: () -> Unit,
    onRead: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onChapterClick: (OnlineChapterV36) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val book = detail.book

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.space4, vertical = t.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnlineIconButtonV50(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(t.space3))
            Text(
                text = "书籍详情",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            OnlineTextButtonV50(
                icon = Icons.Rounded.Source,
                text = "书源",
                onClick = onViewSource,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space2,
                bottom = t.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space4),
        ) {
            item(key = "detail-hero") {
                OnlineDetailHeroV50(book = book, loading = loading)
            }

            if (book.intro.isNotBlank()) {
                item(key = "detail-intro") {
                    OnlineDetailSectionV50(title = "简介") {
                        Text(
                            text = book.intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.foreground,
                        )
                    }
                }
            }

            item(key = "detail-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(t.space2)) {
                    OnlinePrimaryButtonV50(text = "开始阅读", onClick = onRead)
                    Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
                        OnlineSecondaryButtonV50(
                            text = "加入书架",
                            onClick = onAdd,
                            loading = adding,
                            modifier = Modifier.weight(1f),
                        )
                        if (download != null) {
                            OnlineDownloadProgressV50(
                                download = download,
                                onCancel = onCancelDownload,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            OnlineSecondaryButtonV50(
                                text = "离线下载",
                                onClick = onDownload,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item(key = "detail-catalogue") {
                OnlineDetailSectionV50(title = "目录") {
                    if (loading) {
                        OnlineCatalogueSkeletonV50()
                    } else if (detail.chapters.isEmpty()) {
                        Text(
                            text = "暂无目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(t.space1)) {
                            Text(
                                text = "共 ${detail.chapters.size} 章",
                                style = MaterialTheme.typography.labelMedium,
                                color = t.mutedForeground,
                            )
                            detail.chapters.forEachIndexed { index, chapter ->
                                OnlineChapterRowV50(
                                    index = index,
                                    chapter = chapter,
                                    onClick = { onChapterClick(chapter) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Detail Hero                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineDetailHeroV50(
    book: OnlineBookV36,
    loading: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    Row(modifier = Modifier.fillMaxWidth()) {
        OnlineCoverV50(
            coverUrl = book.cover,
            contentDescription = book.name,
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(3f / 4f),
        )
        Spacer(Modifier.width(t.space4))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.headlineSmall,
                color = t.foreground,
                fontWeight = FontWeight.Bold,
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    modifier = Modifier.padding(top = t.space1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
            if (book.sourceName.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = t.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Source,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = t.mutedForeground,
                    )
                    Spacer(Modifier.width(t.space1))
                    Text(
                        text = book.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = t.mutedForeground,
                    )
                }
            }
            if (loading) {
                Spacer(Modifier.height(t.space2))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = t.primary,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                            Detail Section                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineDetailSectionV50(
    title: String,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        content()
    }
}


/* -------------------------------------------------------------------------- */
/*                             Chapter Row                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineChapterRowV50(
    index: Int,
    chapter: OnlineChapterV36,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedForeground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = chapter.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                           Download Progress                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineDownloadProgressV50(
    download: OnlineDownloadV36,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Column(
        modifier = modifier
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space3, vertical = t.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "下载中 ${if (download.total > 0) download.done * 100 / download.total else 0}%",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = t.foreground,
            )
            OnlineBareIconButtonV50(
                icon = Icons.Rounded.Close,
                contentDescription = "取消下载",
                onClick = onCancel,
            )
        }
        LinearProgressIndicator(
            progress = { if (download.total > 0) download.done.toFloat() / download.total else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = t.primary,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Cover                                      */
/* -------------------------------------------------------------------------- */

private val onlineCoverCacheV50 = LruCache<String, android.graphics.Bitmap>(32)

@Composable
private fun OnlineCoverV50(
    coverUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = rememberOnlineCoverV50(coverUrl)
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun rememberOnlineCoverV50(
    coverUrl: String,
): android.graphics.Bitmap? {
    var bitmap by remember(coverUrl) {
        mutableStateOf(onlineCoverCacheV50.get(coverUrl))
    }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) return@LaunchedEffect
        if (bitmap != null) return@LaunchedEffect
        val loaded = runInterruptible(Dispatchers.IO) {
            try {
                val bytes = fetchSourceBytesV36(coverUrl)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
        if (loaded != null) {
            onlineCoverCacheV50.put(coverUrl, loaded)
            bitmap = loaded
        }
    }
    return bitmap
}


/* -------------------------------------------------------------------------- */
/*                           Catalogue Skeleton                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineCatalogueSkeletonV50() {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(t.space2)) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        color = t.input,
                        shape = RoundedCornerShape(t.radiusSm),
                    ),
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                           Small Components                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun OnlineLabelV50(text: String) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Text(
        text = text,
        modifier = Modifier
            .background(color = t.input, shape = shape)
            .padding(horizontal = t.space2, vertical = t.space1),
        style = MaterialTheme.typography.labelSmall,
        color = t.mutedForeground,
    )
}

@Composable
private fun OnlineChipV50(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Box(
        modifier = Modifier
            .background(
                color = if (selected) t.primary else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space3, vertical = t.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.card else t.foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OnlineIconButtonV50(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = t.card, shape = CircleShape)
            .border(width = 1.dp, color = t.border, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = t.foreground,
        )
    }
}

@Composable
private fun OnlineBareIconButtonV50(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .padding(6.dp),
        tint = t.mutedForeground,
    )
}

@Composable
private fun OnlineTextButtonV50(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2, vertical = t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = t.primary,
            )
            Spacer(Modifier.width(t.space1))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnlinePrimaryButtonV50(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(color = t.primary, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = t.card,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnlineSecondaryButtonV50(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .height(48.dp)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = t.primary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Helpers                                     */
/* -------------------------------------------------------------------------- */

private fun onlineFindDiscoveryV50(
    discoveries: List<SourceDiscoveryV41>,
    category: OnlinePrototypeCategoryV50,
): SourceDiscoveryV41? {
    return discoveries.firstOrNull { it.label == category.label }
}

private fun onlineIsRankingLabelV50(label: String): Boolean {
    return label.contains("榜") || label.contains("排行")
}
