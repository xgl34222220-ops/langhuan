package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.net.URI


/** V94: groups come from the sources themselves (the old fixed 综合/悬疑/古风 chips matched nothing). */
internal const val SOURCE_GROUP_ALL_V94 = ""
internal const val SOURCE_GROUP_UNGROUPED_V94 = "未分组"

internal fun sourceManageGroupsV94(sources: List<BookSourceV36>): List<String> {
    val named = sources.flatMap { sourceGroupNamesV94(it) }.distinct().sorted()
    val ungrouped = sources.any { sourceGroupNamesV94(it).isEmpty() }
    return if (named.isEmpty()) emptyList() else named + if (ungrouped) listOf(SOURCE_GROUP_UNGROUPED_V94) else emptyList()
}

/** Legado groups may hold several names separated by commas/semicolons. */
internal fun sourceGroupNamesV94(source: BookSourceV36): List<String> =
    source.group.split(',', '，', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * 书源管理 V50。
 *
 * sources 直接使用真实 BookSourceV36。
 * 页面不读写 BookSourceStoreV36。
 */
@Composable
internal fun BookSourceManageScreenV50(
    sources: List<BookSourceV36>,
    sourceStorageError: String? = null,
    browserVerificationSourceIds: Set<String> = emptySet(),
    focusSourceId: String? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSource: (BookSourceV36) -> Unit,
    onToggleSource: (String) -> Unit,
    onImportSource: () -> Unit,
    onAiGenerateSource: () -> Unit,
    onCreateSource: () -> Unit = {},
    onExportSources: () -> Unit = {},
    importing: Boolean = false,
    importReport: SourceImportReportV94? = null,
    onDismissImportReport: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(SOURCE_GROUP_ALL_V94) }
    val groups = remember(sources) { sourceManageGroupsV94(sources) }

    LaunchedEffect(focusSourceId) {
        if (focusSourceId != null) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            query = ""
            group = SOURCE_GROUP_ALL_V94
        }
    }

    val filtered = remember(sources, query, group) {
        sources.filter { source ->
            sourceManageMatchesQueryV50(source = source, query = query) &&
                sourceManageMatchesGroupV50(source = source, group = group)
        }
    }
    val ordered = remember(filtered, focusSourceId) {
        sourceManageFocusOrderV74(filtered, focusSourceId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        com.xiguli.langhuan.ui.design.FlatTopBarV93(
            title = "书源管理",
            onBack = onBack,
            action = if (sources.isNotEmpty()) {
                {
                    com.xiguli.langhuan.ui.design.FlatIconButtonV93(
                        icon = androidx.compose.material.icons.Icons.Outlined.Upload,
                        contentDescription = "导出书源",
                        onClick = onExportSources,
                    )
                }
            } else null,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space3,
                bottom = t.space5,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space3),
        ) {
            item("manage-search") {
                SourceManageSearchV50(
                    query = query,
                    onQueryChange = { query = it },
                )
            }

            if (groups.isNotEmpty()) {
                item("manage-groups") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(t.space2),
                    ) {
                        items(
                            items = listOf(SOURCE_GROUP_ALL_V94) + groups,
                            key = { "group:$it" },
                        ) { item ->
                            SourceManageGroupChipV50(
                                text = item.ifEmpty { "全部" },
                                selected = item == group,
                                onClick = { group = item },
                            )
                        }
                    }
                }
            }

            if (importing) {
                item("manage-importing") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = t.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = t.primary,
                        )
                        Spacer(Modifier.width(t.space2))
                        Text("正在导入书源…", style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                    }
                }
            }

            importReport?.let { report ->
                item("manage-import-report") {
                    SourceImportReportCardV94(report = report, onDismiss = onDismissImportReport)
                }
            }

            item("manage-summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${sources.size} 个书源",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(t.space2))
                    Text(
                        text = "已启用 ${sources.count { it.enabled }} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }

            sourceStorageError?.let { error ->
                item("manage-storage-error") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                t.destructive.copy(alpha = 0.08f),
                                RoundedCornerShape(t.radiusMd),
                            )
                            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
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
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = t.destructive,
                        )
                    }
                }
            }

            if (sources.isEmpty()) {
                item("manage-empty") {
                    SourceManageEmptyV50(onCreate = onCreateSource, onImport = onImportSource)
                }
            } else if (ordered.isEmpty()) {
                item("manage-no-match") {
                    Text(
                        text = "没有匹配的书源",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = t.space5),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
            } else {
                items(
                    items = ordered,
                    key = { it.id },
                ) { source ->
                    SourceManageCardV50(
                        source = source,
                        recentlySaved = source.id == focusSourceId,
                        browserVerificationRequired =
                            source.id in browserVerificationSourceIds,
                        onToggle = { onToggleSource(source.id) },
                        onClick = { onOpenSource(source) },
                    )
                }
            }

            item("manage-note") {
                Text(
                    text = "支持阅读（Legado）静态网页书源：单个对象或数组均可，来自文本、文件或网址。依赖脚本或 JSON 接口的必需规则会写明原因并跳过，可选规则（封面、简介等）会自动忽略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = t.space4,
                    end = t.space4,
                    top = t.space2,
                    bottom = t.space3,
                ),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            SourceManageBottomButtonV50(
                icon = Icons.Rounded.Add,
                text = "新建",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onCreateSource,
            )
            SourceManageBottomButtonV50(
                icon = Icons.Rounded.FileOpen,
                text = "导入",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onImportSource,
            )
            SourceManageBottomButtonV50(
                icon = Icons.Rounded.AutoAwesome,
                text = "AI 生成书源",
                primary = true,
                modifier = Modifier.weight(1.5f),
                onClick = onAiGenerateSource,
            )
        }
    }
}

/** Keeps a confirmed write visible without changing the durable source order. */
internal fun sourceManageFocusOrderV74(
    sources: List<BookSourceV36>,
    focusSourceId: String?,
): List<BookSourceV36> = if (focusSourceId == null) {
    sources
} else {
    sources.sortedByDescending { it.id == focusSourceId }
}


@Composable
private fun SourceManageSearchV50(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(t.input, shape)
            .border(1.dp, t.border, shape)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.width(t.space2))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isBlank()) {
                Text(
                    text = "按名称、分组或网站搜索",
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
                cursorBrush = SolidColor(t.primary),
            )
        }
    }
}


@Composable
private fun SourceManageCardV50(
    source: BookSourceV36,
    browserVerificationRequired: Boolean,
    recentlySaved: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    val catalog = remember(source) {
        sourceDiscoveryCatalogV41(
            source.copy(
                enabled = true,
                enabledExplore = true,
            ),
        )
    }

    val capabilities = remember(source, catalog) {
        buildList {
            if (source.searchUrl.isNotBlank() && source.searchList.isNotBlank()) {
                add("搜索")
            }
            if (catalog.sections.any { !sourceManageRankingV50(it.label) }) {
                add("发现")
            }
            if (catalog.sections.any { sourceManageRankingV50(it.label) }) {
                add("榜单")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .clickable(onClick = onClick)
            .padding(t.space4),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(t.input, RoundedCornerShape(t.radiusMd))
                    .border(1.dp, t.border, RoundedCornerShape(t.radiusMd)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Source,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = t.primary,
                )
            }
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (recentlySaved) {
                        Spacer(Modifier.width(t.space2))
                        Text(
                            text = "刚保存",
                            modifier = Modifier
                                .background(t.accent, RoundedCornerShape(t.radiusSm))
                                .border(1.dp, t.primary, RoundedCornerShape(t.radiusSm))
                                .padding(horizontal = t.space2, vertical = t.space1),
                            style = MaterialTheme.typography.labelSmall,
                            color = t.accentForeground,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(t.space1))
                Text(
                    text = buildString {
                        if (source.group.isNotBlank()) {
                            append(source.group)
                            append(" · ")
                        }
                        append(sourceManageHostV50(source.baseUrl))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(t.space2))
            SourceManageSwitchV50(
                checked = source.enabled,
                onClick = onToggle,
            )
        }

        if (capabilities.isNotEmpty()) {
            Spacer(Modifier.height(t.space3))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                items(capabilities) { capability ->
                    Text(
                        text = capability,
                        modifier = Modifier
                            .background(t.accent, RoundedCornerShape(t.radiusSm))
                            .border(1.dp, t.border, RoundedCornerShape(t.radiusSm))
                            .padding(
                                horizontal = t.space2,
                                vertical = t.space1,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = t.accentForeground,
                    )
                }
            }
        }

        val warning = when {
            browserVerificationRequired -> "需浏览器验证"
            catalog.issues.isNotEmpty() -> catalog.issues.first()
            !bookSourceSupportedV36(source) -> "存在当前引擎未支持的规则"
            else -> null
        }

        Spacer(Modifier.height(t.space3))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (warning != null) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (browserVerificationRequired) t.gold else t.destructive,
                )
                Spacer(Modifier.width(t.space1))
            }
            Text(
                text = warning ?: if (source.enabled) {
                    "已启用 · 可在书城实测"
                } else {
                    "已停用"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    browserVerificationRequired -> t.goldForeground
                    warning != null -> t.destructive
                    else -> t.mutedForeground
                },
                maxLines = 2,
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
}


@Composable
private fun SourceManageGroupChipV50(
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
private fun SourceManageSwitchV50(
    checked: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusXl)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .background(if (checked) t.primary else t.input, shape)
            .border(1.dp, if (checked) t.primary else t.border, shape)
            .clickable(onClick = onClick),
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
private fun SourceManageBottomButtonV50(
    icon: ImageVector,
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = modifier
            .height(48.dp)
            .background(if (primary) t.primary else t.card, shape)
            .border(1.dp, if (primary) t.primary else t.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = t.space3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = if (primary) t.card else t.primary,
        )
        Spacer(Modifier.width(t.space2))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) t.card else t.secondaryForeground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}


@Composable
private fun SourceManageEmptyV50(onCreate: () -> Unit, onImport: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Outlined.Source,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space3))
        Text(
            text = "还没有书源",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = "导入阅读（Legado）书源 JSON，或手动新建一个。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space4))
        Row(horizontalArrangement = Arrangement.spacedBy(t.space5)) {
            Text(
                text = "导入书源",
                modifier = Modifier.clickable(onClick = onImport).padding(t.space2),
                style = MaterialTheme.typography.labelLarge,
                color = t.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "新建书源",
                modifier = Modifier.clickable(onClick = onCreate).padding(t.space2),
                style = MaterialTheme.typography.labelLarge,
                color = t.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Inline import summary (not a modal): counts, then each skipped source with its reason. */
@Composable
internal fun SourceImportReportCardV94(report: SourceImportReportV94, onDismiss: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val failed = report.error != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (failed) t.destructive.copy(alpha = 0.06f) else t.primary.copy(alpha = 0.06f),
                RoundedCornerShape(t.radiusMd),
            )
            .padding(t.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    failed -> "导入失败"
                    report.added.isEmpty() && report.skipped.isNotEmpty() -> "没有导入新书源"
                    else -> "已导入 ${report.added.size} 个书源"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (failed) t.destructive else t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "知道了",
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = t.space2, vertical = t.space1),
                style = MaterialTheme.typography.labelLarge,
                color = t.primary,
            )
        }
        report.error?.let {
            Spacer(Modifier.height(t.space1))
            Text(it, style = MaterialTheme.typography.bodySmall, color = t.destructive)
        }
        if (report.skipped.isNotEmpty()) {
            Spacer(Modifier.height(t.space2))
            Text("跳过 ${report.skipped.size} 个：", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            report.skipped.take(8).forEach {
                Text("· $it", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (report.skipped.size > 8) Text("另有 ${report.skipped.size - 8} 个", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        if (report.warnings.isNotEmpty()) {
            Spacer(Modifier.height(t.space2))
            Text("已导入但有提示：", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            report.warnings.take(6).forEach {
                Text("· $it", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (report.warnings.size > 6) Text("另有 ${report.warnings.size - 6} 条提示", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}


private fun sourceManageMatchesQueryV50(
    source: BookSourceV36,
    query: String,
): Boolean {
    val value = query.trim()
    if (value.isBlank()) return true
    return source.name.contains(value, true) ||
        source.group.contains(value, true) ||
        source.baseUrl.contains(value, true)
}


private fun sourceManageMatchesGroupV50(
    source: BookSourceV36,
    group: String,
): Boolean = when (group) {
    SOURCE_GROUP_ALL_V94 -> true
    SOURCE_GROUP_UNGROUPED_V94 -> sourceGroupNamesV94(source).isEmpty()
    else -> group in sourceGroupNamesV94(source)
}


private fun sourceManageRankingV50(label: String): Boolean =
    label.contains("榜") ||
        label.contains("排行") ||
        label.contains("月票")


private fun sourceManageHostV50(url: String): String =
    runCatching { URI(url).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: url
