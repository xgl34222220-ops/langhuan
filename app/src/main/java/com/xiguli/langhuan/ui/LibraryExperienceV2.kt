package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.theme.LanghuanShape
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val LibraryV2Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 第二代书架详情/阅读体验。
 *
 * 保留原有 LibraryExperienceViewModel 作为单一书架状态源，只把章节生命周期与
 * 阅读进度放到独立控制器，避免为了阅读端功能去改动 AI 生成/长期记忆主链路。
 */
@Composable
fun ReaderFirstLibraryV2(
    viewModel: LibraryExperienceViewModel,
    onEnterWriting: (String) -> Unit,
    onCloseShelf: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions = remember { ChapterShelfActions(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var actionBusy by remember { mutableStateOf(false) }
    var progressTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    fun refreshBook(bookId: String) {
        viewModel.closeBook()
        viewModel.openBook(bookId)
    }

    fun runChapterAction(bookId: String, success: String, block: suspend () -> Unit) {
        if (actionBusy) return
        scope.launch {
            actionBusy = true
            runCatching { block() }
                .onSuccess {
                    snackbar.showSnackbar(success)
                    refreshBook(bookId)
                }
                .onFailure { error -> snackbar.showSnackbar(error.message ?: "章节操作失败") }
            actionBusy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.readingChapter != null -> {
                    val book = state.openedBook
                    if (book != null) {
                        NovelReaderV2(
                            book = book,
                            state = state,
                            onBack = viewModel::closeReader,
                            onOpenChapter = viewModel::openReader,
                            onEnterWriting = { onEnterWriting(book.id) },
                            onChapterSeen = { chapterNumber ->
                                actions.markRead(book.id, chapterNumber)
                                progressTick++
                            },
                        )
                    }
                }
                state.openedBook != null -> {
                    val book = state.openedBook!!
                    val lastRead = remember(book.id, state.chapters, progressTick) {
                        actions.lastRead(book.id)?.takeIf { number -> state.chapters.any { it.chapterNumber == number } }
                    }
                    BookDetailV2(
                        state = state,
                        lastRead = lastRead,
                        actionBusy = actionBusy,
                        onBack = viewModel::closeBook,
                        onRead = viewModel::openReader,
                        onContinueRead = {
                            val chapter = lastRead
                                ?: state.chapters.firstOrNull { it.content.isNotBlank() }?.chapterNumber
                                ?: state.chapters.firstOrNull()?.chapterNumber
                            if (chapter != null) viewModel.openReader(chapter)
                        },
                        onEnterWriting = { onEnterWriting(book.id) },
                        onRenameChapter = { chapter, title ->
                            runChapterAction(book.id, "第${chapter.chapterNumber}章已重命名") {
                                actions.renameChapter(book.id, chapter.chapterNumber, title)
                            }
                        },
                        onDeleteChapter = { chapter ->
                            runChapterAction(book.id, "章节已删除，后续章节已自动顺延") {
                                actions.deleteChapter(book.id, chapter.chapterNumber)
                            }
                        },
                        onCreateChapter = {
                            runChapterAction(book.id, "已新建空白章节") {
                                actions.createBlankChapter(book.id)
                            }
                        },
                        onDeleteBook = { viewModel.deleteBook(book.id) },
                        onSaveMetadata = viewModel::saveMetadata,
                        onGenerateIdentity = viewModel::generateIdentity,
                        onApplyIdentity = viewModel::applyIdentitySuggestion,
                        onGenerateCover = viewModel::generateCover,
                    )
                }
                else -> ReaderFirstLibrary(
                    viewModel = viewModel,
                    onEnterWorkspace = onEnterWriting,
                    onCloseShelf = onCloseShelf,
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            )
        }
    }
}

@Composable
private fun BookDetailV2(
    state: LibraryExperienceState,
    lastRead: Int?,
    actionBusy: Boolean,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onContinueRead: () -> Unit,
    onEnterWriting: () -> Unit,
    onRenameChapter: (ChapterDraft, String) -> Unit,
    onDeleteChapter: (ChapterDraft) -> Unit,
    onCreateChapter: () -> Unit,
    onDeleteBook: () -> Unit,
    onSaveMetadata: (String, String) -> Unit,
    onGenerateIdentity: () -> Unit,
    onApplyIdentity: () -> Unit,
    onGenerateCover: () -> Unit,
) {
    val book = state.openedBook ?: return
    var editingBook by remember(book.id) { mutableStateOf(false) }
    var bookTitle by remember(book.title) { mutableStateOf(book.title) }
    var premise by remember(book.premise) { mutableStateOf(book.premise) }
    var renameTarget by remember { mutableStateOf<ChapterDraft?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChapterDraft?>(null) }
    var confirmDeleteBook by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text("作品详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("阅读 · 目录 · 章节管理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ editingBook = !editingBook }) { Icon(Icons.Rounded.Edit, "编辑作品资料") }
                IconButton({ confirmDeleteBook = true }) {
                    Icon(Icons.Rounded.DeleteOutline, "删除小说", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Surface(shape = LanghuanShape.sheet, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row {
                        V2CoverImage(book.coverPath, book.title, Modifier.width(126.dp).height(180.dp))
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(book.genre, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(book.premise, maxLines = 7, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Text("${book.currentWords} / ${book.targetWords} 字", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onContinueRead,
                            modifier = Modifier.weight(1f),
                            enabled = state.chapters.isNotEmpty(),
                            shape = LanghuanShape.card,
                        ) {
                            Icon(Icons.Rounded.MenuBook, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (lastRead != null) "续读第 $lastRead 章" else "开始阅读")
                        }
                        FilledTonalButton(
                            onClick = onEnterWriting,
                            modifier = Modifier.weight(1f),
                            shape = LanghuanShape.card,
                        ) {
                            Icon(Icons.Rounded.EditNote, null)
                            Spacer(Modifier.width(6.dp))
                            Text("继续创作")
                        }
                    }
                }
            }
        }

        if (editingBook) {
            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("编辑作品资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(bookTitle, { bookTitle = it }, Modifier.fillMaxWidth(), label = { Text("书名") }, shape = LanghuanShape.card)
                        OutlinedTextField(premise, { premise = it }, Modifier.fillMaxWidth(), label = { Text("作品简介") }, minLines = 4, shape = LanghuanShape.card)
                        Button(
                            onClick = { onSaveMetadata(bookTitle, premise); editingBook = false },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bookTitle.isNotBlank() && premise.isNotBlank() && !state.isBusy,
                            shape = LanghuanShape.card,
                        ) { Text("保存资料") }
                    }
                }
            }
        }

        item {
            Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 作品包装", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("可重新生成书名、简介和本地合成封面，不改变小说核心设定。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onGenerateIdentity, Modifier.weight(1f), enabled = !state.isBusy, shape = LanghuanShape.card) { Text("AI 书名/简介") }
                        OutlinedButton(onGenerateCover, Modifier.weight(1f), enabled = !state.isBusy, shape = LanghuanShape.card) { Text("AI 封面") }
                    }
                    state.identitySuggestion?.let { suggestion ->
                        Spacer(Modifier.height(12.dp))
                        Text(suggestion.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(suggestion.premise, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onApplyIdentity, enabled = !state.isBusy, shape = LanghuanShape.card) { Text("采用这套资料") }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("共 ${state.chapters.size} 章 · 长按思路改为明确的章节菜单", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onCreateChapter, enabled = !actionBusy, shape = LanghuanShape.card) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("新章节")
                }
            }
        }

        items(state.chapters.sortedBy { it.chapterNumber }, key = { it.id }) { chapter ->
            ChapterDirectoryRow(
                chapter = chapter,
                enabled = !actionBusy,
                onRead = { onRead(chapter.chapterNumber) },
                onRename = {
                    renameTarget = chapter
                    renameText = chapter.title
                },
                onDelete = { deleteTarget = chapter },
            )
        }
    }

    renameTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名第${chapter.chapterNumber}章") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("章节标题") },
                    singleLine = true,
                    shape = LanghuanShape.card,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameChapter(chapter, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank() && !actionBusy,
                ) { Text("保存") }
            },
            dismissButton = { TextButton({ renameTarget = null }) { Text("取消") } },
        )
    }

    deleteTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除第${chapter.chapterNumber}章？") },
            text = {
                Text("会删除该章正文与版本，并把后续章节编号自动前移。人物状态、时间线和伏笔等已经确认写入的长期事实不会自动回滚。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChapter(chapter)
                        deleteTarget = null
                    },
                    enabled = state.chapters.size > 1 && !actionBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(if (state.chapters.size > 1) "删除章节" else "至少保留一章") }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } },
        )
    }

    if (confirmDeleteBook) {
        AlertDialog(
            onDismissRequest = { confirmDeleteBook = false },
            title = { Text("删除《${book.title}》？") },
            text = { Text("会删除这本小说的章节、版本、记忆和封面文件，此操作不能撤销。") },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteBook = false; onDeleteBook() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("彻底删除") }
            },
            dismissButton = { TextButton({ confirmDeleteBook = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChapterDirectoryRow(
    chapter: ChapterDraft,
    enabled: Boolean,
    onRead: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.card,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onRead).padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    chapter.chapterNumber.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(chapter.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (chapter.content.isBlank()) "暂无正文 · 可进入写作流创作" else "${chapter.content.length} 字 · v${chapter.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Rounded.MoreVert, "章节菜单") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除章节", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelReaderV2(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onEnterWriting: () -> Unit,
    onChapterSeen: (Int) -> Unit,
) {
    val chapter = state.readingChapter ?: return
    val chapters = state.chapters.sortedBy { it.chapterNumber }
    val index = chapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = chapters.getOrNull(index - 1)
    val next = chapters.getOrNull(index + 1)
    var fontSize by remember { mutableFloatStateOf(19f) }
    val scroll = rememberScrollState()

    LaunchedEffect(book.id, chapter.id) {
        onChapterSeen(chapter.chapterNumber)
        scroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回目录") }
            Column(Modifier.weight(1f)) {
                Text("《${book.title}》 · 第${chapter.chapterNumber}章", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onEnterWriting) { Icon(Icons.Rounded.EditNote, "继续创作") }
        }
        HorizontalDivider()
        SelectionContainer(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(chapter.title, fontSize = (fontSize + 7).sp, lineHeight = (fontSize + 14).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Text(
                    chapter.content.ifBlank { "这一章还没有正文。点击右上角写作按钮即可进入写作流。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.9f).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(42.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { previous?.let { onOpenChapter(it.chapterNumber) } },
                        modifier = Modifier.weight(1f),
                        enabled = previous != null,
                        shape = LanghuanShape.card,
                    ) { Text("上一章") }
                    Button(
                        onClick = { next?.let { onOpenChapter(it.chapterNumber) } },
                        modifier = Modifier.weight(1f),
                        enabled = next != null,
                        shape = LanghuanShape.card,
                    ) { Text("下一章") }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aa", fontSize = 15.sp)
                Slider(fontSize, { fontSize = it }, valueRange = 15f..26f, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
                Text("Aa", fontSize = 23.sp)
            }
        }
    }
}

@Composable
private fun V2CoverImage(path: String, title: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        path.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap, title, modifier.clip(LanghuanShape.card), contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.clip(LanghuanShape.card).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text(title.take(12), fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private class ChapterShelfActions(context: Context) {
    private val appContext = context.applicationContext
    private val db = LanghuanDatabase.get(appContext)
    private val storyDao = db.storyStateDao()
    private val chapterStateDao = db.chapterStateDao()
    private val chapterVersionDao = db.chapterVersionDao()
    private val memoryDao = db.memoryChunkDao()
    private val projects = StoryProjectManager(appContext)
    private val prefs = appContext.getSharedPreferences("langhuan_reader_progress", Context.MODE_PRIVATE)

    fun lastRead(novelId: String): Int? = prefs.getInt("chapter:$novelId", 0).takeIf { it > 0 }

    fun markRead(novelId: String, chapterNumber: Int) {
        prefs.edit().putInt("chapter:$novelId", chapterNumber).apply()
    }

    suspend fun createBlankChapter(novelId: String) {
        val loaded = projects.loadStory(novelId) ?: error("找不到这本小说")
        projects.createChapter(
            snapshot = loaded.snapshot,
            title = "",
            objective = "承接上一章结果，推动当前主线，并在章末形成新的选择或悬念。",
            conflict = "人物目标遭遇新的具体阻碍。",
            turningPoint = "章末出现足以推动下一章的新信息、代价或选择。",
        )
    }

    suspend fun renameChapter(novelId: String, chapterNumber: Int, title: String) {
        val clean = title.trim()
        require(clean.isNotBlank()) { "章节标题不能为空" }
        val row = storyDao.get(novelId) ?: error("找不到这本小说")
        val snapshot = decodeSnapshot(row)
        val entity = chapterStateDao.get(novelId, chapterNumber) ?: error("找不到这一章")
        val draft = decodeDraft(entity)
        val updatedDraft = draft.copy(title = clean)
        val full = effectiveOutline(snapshot)
        val updatedOutline = full.map { node ->
            if (node.level == OutlineLevel.CHAPTER && node.order == chapterNumber) node.copy(title = clean) else node
        }
        val updatedSnapshot = snapshot.copy(
            outline = updatedOutline,
            activeOutline = snapshot.activeOutline.map { node ->
                if (node.level == OutlineLevel.CHAPTER && node.order == chapterNumber) node.copy(title = clean) else node
            },
        )
        val now = System.currentTimeMillis()
        db.withTransaction {
            chapterStateDao.upsert(entity.copy(draftJson = encodeDraft(updatedDraft), updatedAt = now))
            storyDao.upsert(
                row.copy(
                    snapshotJson = encodeSnapshot(updatedSnapshot),
                    draftJson = if (snapshot.novel.currentChapter == chapterNumber) encodeDraft(updatedDraft) else row.draftJson,
                    updatedAt = now,
                )
            )
            memoryDao.upsert(chapterMemory(updatedDraft, now))
        }
    }

    suspend fun deleteChapter(novelId: String, chapterNumber: Int) {
        val row = storyDao.get(novelId) ?: error("找不到这本小说")
        val snapshot = decodeSnapshot(row)
        val entities = chapterStateDao.allForNovel(novelId)
        val drafts = entities.map { decodeDraft(it) }.sortedBy { it.chapterNumber }
        require(drafts.size > 1) { "一本小说至少保留一个章节" }
        val removed = drafts.firstOrNull { it.chapterNumber == chapterNumber } ?: error("找不到这一章")
        val remainingDrafts = drafts
            .filterNot { it.chapterNumber == chapterNumber }
            .map { draft -> if (draft.chapterNumber > chapterNumber) draft.copy(chapterNumber = draft.chapterNumber - 1) else draft }
            .sortedBy { it.chapterNumber }

        val full = effectiveOutline(snapshot)
        val revisedOutline = full.mapNotNull { node ->
            if (node.level != OutlineLevel.CHAPTER) return@mapNotNull node
            when {
                node.order == chapterNumber -> null
                node.order > chapterNumber -> node.copy(order = node.order - 1)
                else -> node
            }
        }
        val maxChapter = remainingDrafts.maxOf { it.chapterNumber }
        val desiredCurrent = when {
            snapshot.novel.currentChapter > chapterNumber -> snapshot.novel.currentChapter - 1
            snapshot.novel.currentChapter == chapterNumber -> chapterNumber.coerceAtMost(maxChapter)
            else -> snapshot.novel.currentChapter.coerceAtMost(maxChapter)
        }.coerceAtLeast(1)
        val selected = remainingDrafts.firstOrNull { it.chapterNumber == desiredCurrent } ?: remainingDrafts.first()
        val updatedSnapshot = snapshot.copy(
            novel = snapshot.novel.copy(
                currentChapter = selected.chapterNumber,
                currentWords = (snapshot.novel.currentWords - removed.content.length).coerceAtLeast(0),
            ),
            outline = revisedOutline,
            activeOutline = activeChain(revisedOutline, selected.chapterNumber),
            recentSummaries = snapshot.recentSummaries.filterNot { it.trimStart().startsWith("第${chapterNumber}章") },
        )
        val versions = chapterVersionDao.allForNovel(novelId)
        val now = System.currentTimeMillis()

        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DELETE FROM chapter_state WHERE novelId = ?", arrayOf(novelId))
            sql.execSQL("DELETE FROM chapter_versions WHERE novelId = ?", arrayOf(novelId))
            sql.execSQL("DELETE FROM memory_chunks WHERE novelId = ? AND sourceType = 'CHAPTER'", arrayOf(novelId))

            remainingDrafts.forEach { draft ->
                chapterStateDao.upsert(
                    ChapterStateEntity(
                        id = draft.id,
                        novelId = novelId,
                        chapterNumber = draft.chapterNumber,
                        draftJson = encodeDraft(draft),
                        updatedAt = now,
                    )
                )
                memoryDao.upsert(chapterMemory(draft, now))
            }
            versions
                .filterNot { it.chapterNumber == chapterNumber }
                .forEach { version ->
                    chapterVersionDao.upsert(
                        version.copy(chapterNumber = if (version.chapterNumber > chapterNumber) version.chapterNumber - 1 else version.chapterNumber)
                    )
                }
            storyDao.upsert(
                StoryStateEntity(
                    novelId = novelId,
                    snapshotJson = encodeSnapshot(updatedSnapshot),
                    draftJson = encodeDraft(selected),
                    updatedAt = now,
                )
            )
        }

        val oldProgress = lastRead(novelId)
        if (oldProgress != null) {
            val newProgress = when {
                oldProgress > chapterNumber -> oldProgress - 1
                oldProgress == chapterNumber -> selected.chapterNumber
                else -> oldProgress
            }.coerceAtMost(maxChapter)
            markRead(novelId, newProgress)
        }
    }

    private fun effectiveOutline(snapshot: StorySnapshot): List<OutlineNode> =
        (if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline).distinctBy { it.id }

    private fun activeChain(nodes: List<OutlineNode>, chapterNumber: Int): List<OutlineNode> {
        val chapter = nodes.firstOrNull { it.level == OutlineLevel.CHAPTER && it.order == chapterNumber } ?: return emptyList()
        val volume = nodes.firstOrNull { it.id == chapter.parentId }
        val master = volume?.parentId?.let { parent -> nodes.firstOrNull { it.id == parent } }
            ?: nodes.firstOrNull { it.level == OutlineLevel.MASTER }
        return listOfNotNull(master, volume, chapter)
    }

    private fun chapterMemory(draft: ChapterDraft, now: Long) = MemoryChunkEntity(
        id = "chapter:${draft.id}:working",
        novelId = draft.novelId,
        sourceType = "CHAPTER",
        sourceId = draft.id,
        chapterNumber = draft.chapterNumber,
        text = buildString {
            append("第${draft.chapterNumber}章 ${draft.title}。")
            if (draft.summary.isNotBlank()) append(draft.summary)
            if (draft.content.isNotBlank()) append('\n').append(draft.content.take(4_000))
        },
        updatedAt = now,
    )

    private fun decodeSnapshot(row: StoryStateEntity): StorySnapshot =
        LibraryV2Json.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)

    private fun decodeDraft(entity: ChapterStateEntity): ChapterDraft =
        LibraryV2Json.decodeFromString(ChapterDraft.serializer(), entity.draftJson)

    private fun encodeSnapshot(snapshot: StorySnapshot): String =
        LibraryV2Json.encodeToString(StorySnapshot.serializer(), snapshot)

    private fun encodeDraft(draft: ChapterDraft): String =
        LibraryV2Json.encodeToString(ChapterDraft.serializer(), draft)
}
