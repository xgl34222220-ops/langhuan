package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import com.xiguli.langhuan.ui.design.LargeTitleBarV95
import com.xiguli.langhuan.ui.design.OnTabReselectV95
import com.xiguli.langhuan.ui.design.EmptyStateV95
import com.xiguli.langhuan.ui.design.LanghuanMarkV95
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.FlatListRowV93
import com.xiguli.langhuan.ui.design.FlatSectionLabelV93
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/** Same rule as the shelf's 「在写」 tab: the user's own works, not imports or followed web novels. */
internal fun isOwnWritingBookV94(book: ReaderBookUi): Boolean =
    book.genre != "导入作品" && book.sourceId.isBlank() && book.sourceBookUrl.isBlank()

private val TAB_GUTTER_V94 = 20.dp

/** V95: large title that collapses once the tab's content scrolls; reselecting the tab scrolls up. */
@Composable
private fun TabHeaderV94(title: String, scroll: ScrollState, tabKey: String) {
    OnTabReselectV95(tabKey) { scroll.animateScrollTo(0) }
    LargeTitleBarV95(title = title, collapsed = scroll.value > 0, horizontalPadding = TAB_GUTTER_V94)
}

/**
 * V94 「创作」标签：把原先散落在书架“添加”、我的页「开始创作」、运行中心和写作技能里的创作入口收拢到一处，
 * 并接上此前没有任何入口的「空白新书」（手写，不用 AI）。
 */
@Composable
internal fun CreateTabV94(
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    aiReady: Boolean,
    runActive: Boolean,
    onNewAiBook: () -> Unit,
    onNewBlankBook: (title: String, genre: String) -> Unit,
    onContinueWriting: (String) -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val writing = remember(books) { books.filter(::isOwnWritingBookV94).sortedByDescending { it.updatedAt } }
    var blankOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        val scroll = rememberScrollState()
        TabHeaderV94("创作", scroll, "CREATE_HUB")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = TAB_GUTTER_V94),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = t.space2)) {
                CreateStartCardV94(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI 开新书",
                    subtitle = if (aiReady) "聊出设定与大纲，再开写" else "先配置 AI 服务",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = onNewAiBook,
                )
                CreateStartCardV94(
                    icon = Icons.Outlined.EditNote,
                    title = "空白新书",
                    subtitle = "不用 AI，直接写第一章",
                    accent = false,
                    modifier = Modifier.weight(1f),
                    onClick = { blankOpen = true },
                )
            }
            FlatSectionLabelV93("在写的书")
            when {
                !libraryLoaded -> Text("正在载入…", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                writing.isEmpty() -> EmptyStateV95(
                    title = "还没有自己的作品",
                    message = "用上面的任一方式开一本新书吧",
                    modifier = Modifier.padding(vertical = 0.dp),
                )
                else -> writing.forEach { book ->
                    FlatListRowV93(
                        title = book.title,
                        subtitle = buildList {
                            if (book.genre.isNotBlank()) add(book.genre)
                            if (book.currentChapter > 0) add("写到第 ${book.currentChapter} 章")
                            if (book.currentWords > 0) add(if (book.currentWords >= 10_000) "%.1f 万字".format(book.currentWords / 10_000f) else "${book.currentWords} 字")
                        }.joinToString(" · ").ifBlank { "尚未开写" },
                        value = "继续写",
                        onClick = { onContinueWriting(book.id) },
                    )
                }
            }
            FlatSectionLabelV93("工具")
            FlatListRowV93(
                title = "运行中心",
                icon = Icons.Outlined.Insights,
                subtitle = if (runActive) HOME_RUN_ACTIVE_LABEL_V92 else "查看 AI 写作任务与日志",
                subtitleAccent = runActive,
                badge = runActive,
                onClick = onRunCenter,
            )
            FlatListRowV93(title = "写作技能", icon = Icons.Outlined.Psychology, subtitle = "管理创作 Skill", onClick = onSkills)
            Spacer(Modifier.height(t.space6))
        }
    }
    if (blankOpen) {
        BlankBookDialogV94(
            onDismiss = { blankOpen = false },
            onConfirm = { title, genre ->
                blankOpen = false
                onNewBlankBook(title, genre)
            },
        )
    }
}

@Composable
private fun CreateStartCardV94(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier
            .background(
                if (accent) t.primary.copy(alpha = 0.08f) else t.foreground.copy(alpha = 0.04f),
                RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (accent) t.primary else t.foreground.copy(alpha = 0.78f))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * V95: 「空白新书」 is a bottom sheet in the reference reader's style — grab handle, centred title,
 * stacked fields, one full-width primary action and a quiet 「取消」 pill underneath.
 */
@Composable
private fun BlankBookDialogV94(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    var title by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(t.foreground.copy(alpha = 0.16f), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(
                "空白新书",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text("书名", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            Spacer(Modifier.height(6.dp))
            SourcePlainFieldV94(value = title, onValueChange = { title = it }, hint = "未命名小说", modifier = Modifier.fillMaxWidth(), description = "书名")
            Spacer(Modifier.height(14.dp))
            Text("类型（可选）", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            Spacer(Modifier.height(6.dp))
            SourcePlainFieldV94(value = genre, onValueChange = { genre = it }, hint = "例如：悬疑、言情", modifier = Modifier.fillMaxWidth(), description = "类型")
            Spacer(Modifier.height(22.dp))
            SheetPillButtonV95("创建并开写", primary = true) { onConfirm(title, genre) }
            Spacer(Modifier.height(10.dp))
            SheetPillButtonV95("取消", primary = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun SheetPillButtonV95(text: String, primary: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (primary) t.primary else t.foreground.copy(alpha = 0.05f), RoundedCornerShape(23.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) t.primaryForeground else t.secondaryForeground,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/**
 * V94 「我的」标签（原先藏在书架右上角「更多功能」里的页面）。只保留不在其它标签出现的入口：
 * 书源管理、导入本地书籍、AI 与模型；书架排序/显示统一在书架的「整理」面板里。
 */
@Composable
internal fun MineTabV94(
    bookCount: Int?,
    finishedCount: Int?,
    sourceCount: Int,
    enabledSourceCount: Int,
    aiLabel: String?,
    onSources: () -> Unit,
    onImportLocal: () -> Unit,
    onAiSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        val scroll = rememberScrollState()
        TabHeaderV94("我的", scroll, "MINE")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = TAB_GUTTER_V94),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = t.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).background(t.foreground.copy(alpha = 0.045f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    LanghuanMarkV95(size = 38.dp)
                }
                Spacer(Modifier.width(t.space4))
                Column {
                    Text("琅嬛读者", style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (bookCount == null) "正在载入书架…" else "书架 $bookCount 本 · 已读完 ${finishedCount ?: 0} 本",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
            FlatSectionLabelV93("阅读")
            FlatListRowV93(
                title = "书源管理",
                icon = Icons.Outlined.Source,
                subtitle = "新建、导入、导出与启用书源",
                value = if (sourceCount == 0) "未添加" else "$enabledSourceCount/$sourceCount 已启用",
                onClick = onSources,
            )
            FlatListRowV93(title = "导入本地书籍", icon = Icons.Outlined.FileOpen, value = "TXT · EPUB", onClick = onImportLocal)
            FlatSectionLabelV93("AI")
            FlatListRowV93(
                title = "AI 与模型",
                icon = Icons.Outlined.SettingsSuggest,
                subtitle = aiLabel ?: "服务商、模型与任务路由",
                value = if (aiLabel == null) "未配置" else null,
                onClick = onAiSetup,
            )
            FlatSectionLabelV93("关于")
            FlatListRowV93(
                title = "琅嬛",
                icon = Icons.Outlined.Info,
                subtitle = "阅读与 AI 写作",
                value = version.ifBlank { null },
                onClick = null,
            )
            Spacer(Modifier.height(t.space6))
        }
    }
}
