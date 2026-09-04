package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val LibraryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

data class ReaderBookUi(
    val id: String,
    val title: String,
    val genre: String,
    val premise: String,
    val theme: String,
    val coverPath: String,
    val currentWords: Int,
    val targetWords: Int,
    val currentChapter: Int,
    val updatedAt: Long,
)

data class BookIdentitySuggestion(
    val title: String,
    val premise: String,
    val coverBrief: String,
)

data class LibraryExperienceState(
    val stories: List<ReaderBookUi> = emptyList(),
    val openedBook: ReaderBookUi? = null,
    val chapters: List<ChapterDraft> = emptyList(),
    val readingChapter: ChapterDraft? = null,
    val identitySuggestion: BookIdentitySuggestion? = null,
    val isBusy: Boolean = false,
    val workspaceStoryId: String? = null,
    val requestActivityReload: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val libraryLoaded: Boolean = false,
)

class LibraryExperienceViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LanghuanDatabase.get(application)
    private val storyDao = db.storyStateDao()
    private val projects = StoryProjectManager(application)
    private val repository = PersistentStoryRepository(application)
    private val projectPrefs = application.getSharedPreferences("langhuan_project_state", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(LibraryExperienceState())
    val state: StateFlow<LibraryExperienceState> = _state.asStateFlow()
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            storyDao.observeAll().collect { rows ->
                val books = rows.mapNotNull { row ->
                    runCatching {
                        val snapshot = LibraryJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                        ReaderBookUi(
                            id = snapshot.novel.id,
                            title = snapshot.novel.title,
                            genre = snapshot.novel.genre,
                            premise = snapshot.novel.premise,
                            theme = snapshot.novel.theme,
                            coverPath = snapshot.novel.coverPath,
                            currentWords = snapshot.novel.currentWords,
                            targetWords = snapshot.novel.targetWords,
                            currentChapter = snapshot.novel.currentChapter,
                            updatedAt = row.updatedAt,
                        )
                    }.getOrNull()
                }
                _state.update { current ->
                    val opened = current.openedBook?.id?.let { id -> books.firstOrNull { it.id == id } }
                    current.copy(stories = books, openedBook = opened, libraryLoaded = true)
                }
            }
        }
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                activeProviderId = providers.firstOrNull { it.isDefault }?.id ?: providers.firstOrNull()?.id
            }
        }
    }

    fun openBook(id: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null, identitySuggestion = null) }
            runCatching {
                val book = _state.value.stories.firstOrNull { it.id == id } ?: error("找不到这本小说")
                val chapters = projects.chapterDrafts(id)
                book to chapters
            }.onSuccess { (book, chapters) ->
                _state.update { it.copy(openedBook = book, chapters = chapters, readingChapter = null, isBusy = false, workspaceStoryId = id) }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message ?: "打开作品失败") }
            }
        }
    }

    fun closeBook() = _state.update { it.copy(openedBook = null, chapters = emptyList(), readingChapter = null, identitySuggestion = null) }

    fun openReader(chapterNumber: Int) {
        val chapter = _state.value.chapters.firstOrNull { it.chapterNumber == chapterNumber } ?: return
        _state.update { it.copy(readingChapter = chapter) }
    }

    fun closeReader() = _state.update { it.copy(readingChapter = null) }

    fun readPrevious() {
        val current = _state.value.readingChapter ?: return
        _state.value.chapters.firstOrNull { it.chapterNumber == current.chapterNumber - 1 }?.let { chapter ->
            _state.update { it.copy(readingChapter = chapter) }
        }
    }

    fun readNext() {
        val current = _state.value.readingChapter ?: return
        _state.value.chapters.firstOrNull { it.chapterNumber == current.chapterNumber + 1 }?.let { chapter ->
            _state.update { it.copy(readingChapter = chapter) }
        }
    }

    fun createStory(title: String, genre: String, premise: String, theme: String, targetWords: Int) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching { projects.createStory(NewStoryRequest(title, genre, premise, theme, targetWords)) }
                .onSuccess { created ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            workspaceStoryId = created.snapshot.novel.id,
                            message = "已创建《${created.snapshot.novel.title}》",
                        )
                    }
                    openBook(created.snapshot.novel.id)
                }.onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "创建小说失败") } }
        }
    }

    fun deleteBook(id: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching {
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    sql.execSQL("DELETE FROM chapter_versions WHERE novelId = ?", arrayOf(id))
                    sql.execSQL("DELETE FROM chapter_state WHERE novelId = ?", arrayOf(id))
                    sql.execSQL("DELETE FROM memory_chunks WHERE novelId = ?", arrayOf(id))
                    sql.execSQL("DELETE FROM story_state WHERE novelId = ?", arrayOf(id))
                    if (id == DEMO_ID) {
                        storyDao.upsert(
                            StoryStateEntity(
                                novelId = DEMO_ID,
                                snapshotJson = "__deleted_demo__",
                                draftJson = "__deleted_demo__",
                                updatedAt = 0L,
                            )
                        )
                    }
                }
                if (projectPrefs.getString(KEY_ACTIVE_STORY, null) == id) {
                    projectPrefs.edit().remove(KEY_ACTIVE_STORY).apply()
                }
                runCatching { File(getApplication<Application>().filesDir, "covers/$id.png").delete() }
                _state.value.stories.firstOrNull { it.id != id }
            }.onSuccess { next ->
                _state.update {
                    it.copy(
                        openedBook = null,
                        chapters = emptyList(),
                        readingChapter = null,
                        isBusy = false,
                        workspaceStoryId = next?.id,
                        message = "小说已删除",
                    )
                }
            }.onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "删除失败") } }
        }
    }

    fun saveMetadata(title: String, premise: String) {
        val book = _state.value.openedBook ?: return
        if (title.isBlank() || premise.isBlank() || _state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching { updateNovel(book.id) { snapshot ->
                snapshot.copy(novel = snapshot.novel.copy(title = title.trim(), premise = premise.trim()))
            } }.onSuccess {
                refreshOpenedBook(book.id)
                _state.update { it.copy(isBusy = false, requestActivityReload = true, message = "书名和简介已保存") }
            }.onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "保存作品资料失败") } }
        }
    }

    fun generateIdentity() {
        val book = _state.value.openedBook ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isBusy = true, error = null, identitySuggestion = null) }
            runCatching { BookIdentityAi(gateway).suggest(book) }
                .onSuccess { suggestion -> _state.update { it.copy(isBusy = false, identitySuggestion = suggestion) } }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "AI 生成书名和简介失败") } }
        }
    }

    fun applyIdentitySuggestion() {
        val suggestion = _state.value.identitySuggestion ?: return
        val book = _state.value.openedBook ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching {
                updateNovel(book.id) { snapshot ->
                    snapshot.copy(novel = snapshot.novel.copy(title = suggestion.title.trim(), premise = suggestion.premise.trim()))
                }
            }.onSuccess {
                refreshOpenedBook(book.id)
                _state.update { it.copy(isBusy = false, identitySuggestion = null, requestActivityReload = true, message = "已采用 AI 书名和简介") }
            }.onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "应用 AI 建议失败") } }
        }
    }

    fun generateCover() {
        val book = _state.value.openedBook ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "AI 生成封面需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching {
                val brief = _state.value.identitySuggestion?.coverBrief
                    ?: BookIdentityAi(gateway).coverBrief(book)
                val path = CoverComposer.create(getApplication(), book, brief)
                updateNovel(book.id) { snapshot -> snapshot.copy(novel = snapshot.novel.copy(coverPath = path)) }
                path
            }.onSuccess {
                refreshOpenedBook(book.id)
                _state.update { it.copy(isBusy = false, message = "AI 封面已生成") }
            }.onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message ?: "AI 封面生成失败") } }
        }
    }

    fun consumeWorkspaceStory() = _state.update { it.copy(workspaceStoryId = null) }
    fun consumeActivityReload() = _state.update { it.copy(requestActivityReload = false) }
    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private suspend fun refreshOpenedBook(id: String) {
        val row = storyDao.get(id) ?: return
        val snapshot = runCatching { LibraryJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson) }.getOrNull() ?: return
        val book = ReaderBookUi(
            id = snapshot.novel.id,
            title = snapshot.novel.title,
            genre = snapshot.novel.genre,
            premise = snapshot.novel.premise,
            theme = snapshot.novel.theme,
            coverPath = snapshot.novel.coverPath,
            currentWords = snapshot.novel.currentWords,
            targetWords = snapshot.novel.targetWords,
            currentChapter = snapshot.novel.currentChapter,
            updatedAt = row.updatedAt,
        )
        _state.update { it.copy(openedBook = book) }
    }

    private suspend fun updateNovel(id: String, transform: (StorySnapshot) -> StorySnapshot) {
        val row = storyDao.get(id) ?: error("找不到小说")
        val snapshot = LibraryJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
        val updated = transform(snapshot)
        storyDao.upsert(row.copy(snapshotJson = LibraryJson.encodeToString(StorySnapshot.serializer(), updated), updatedAt = System.currentTimeMillis()))
    }

    private suspend fun activeGateway(): AiGateway? {
        val id = activeProviderId ?: return null
        return repository.providerConfig(id)?.let(::UniversalAiGateway)
    }

    companion object {
        private const val DEMO_ID = "novel-1"
        private const val KEY_ACTIVE_STORY = "active_story_id"
    }
}

private class BookIdentityAi(private val gateway: AiGateway) {
    suspend fun suggest(book: ReaderBookUi): BookIdentitySuggestion {
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是专业中文网络小说责编与装帧策划。根据已有题材、核心故事和主题，重新给出更有辨识度、不过度俗套的书名与简介。
                    必须输出 GeneratedChapter JSON：title=建议书名；content=120-220字作品简介；summary=封面视觉简报，描述构图、气质、核心意象、字体氛围，不要解释；stateChanges=[]；touchedForeshadowingIds=[]。
                    不得改变小说核心设定，不要用营销套话堆砌。
                """.trimIndent(),
                user = """
                    当前书名：${book.title}
                    类型：${book.genre}
                    当前简介：${book.premise}
                    主题：${book.theme}
                    请给出一套可以直接使用的书名、简介和封面视觉简报。
                """.trimIndent(),
            )
        )
        return BookIdentitySuggestion(
            title = output.title.trim().ifBlank { book.title },
            premise = output.content.trim().ifBlank { book.premise },
            coverBrief = output.summary.trim().ifBlank { "围绕《${book.title}》与${book.genre}主题形成克制、有辨识度的竖版小说封面" },
        )
    }

    suspend fun coverBrief(book: ReaderBookUi): String = gateway.generate(
        PromptBundle(
            system = """
                你是小说封面美术指导。输出 GeneratedChapter JSON。title 固定 cover；content 只写一段不超过180字的竖版小说封面视觉简报，包含气质、构图、核心意象和留白方向；summary 留空；stateChanges=[]；touchedForeshadowingIds=[]。
            """.trimIndent(),
            user = "书名：${book.title}\n类型：${book.genre}\n简介：${book.premise}\n主题：${book.theme}",
        )
    ).content.trim()
}

private object CoverComposer {
    fun create(application: Application, book: ReaderBookUi, brief: String): String {
        val width = 900
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val seed = (book.title + brief).hashCode().absoluteValue
        val hue1 = (seed % 360).toFloat()
        val hue2 = ((seed / 7 + 55) % 360).toFloat()
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hue1, .48f, .34f))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf(hue2, .58f, .14f))
        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(34, 255, 255, 255) }
        repeat(9) { index ->
            val x = ((seed * (index + 13L)) % width).toFloat()
            val y = ((seed * (index + 29L)) % height).toFloat()
            canvas.drawCircle(x, y, 80f + (index * 27f), haze)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(205, 255, 255, 255)
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(book.genre.take(18), 78f, 110f, labelPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = when {
                book.title.length <= 4 -> 118f
                book.title.length <= 8 -> 98f
                else -> 80f
            }
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        drawWrapped(canvas, book.title, titlePaint, 78f, 390f, width - 156f, 1.22f)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(150, 255, 255, 255); strokeWidth = 3f }
        canvas.drawLine(80f, 810f, 310f, 810f, line)

        val briefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 255, 255, 255)
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        drawWrapped(canvas, brief.replace('\n', ' ').take(150), briefPaint, 80f, 875f, width - 160f, 1.55f, maxLines = 5)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(150, 255, 255, 255)
            textSize = 24f
        }
        canvas.drawText("琅嬛 · AI COVER", 80f, 1190f, footer)

        val dir = File(application.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "${book.id}.png")
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 96, output) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun drawWrapped(canvas: Canvas, text: String, paint: Paint, x: Float, y: Float, maxWidth: Float, lineScale: Float, maxLines: Int = 4) {
        if (text.isBlank()) return
        val lines = mutableListOf<String>()
        var current = ""
        text.forEach { char ->
            val candidate = current + char
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current
                current = char.toString()
            } else current = candidate
        }
        if (current.isNotEmpty()) lines += current
        lines.take(maxLines).forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * paint.textSize * lineScale, paint)
        }
    }
}

@Composable
fun ReaderFirstLibrary(
    viewModel: LibraryExperienceViewModel,
    onEnterWorkspace: (String) -> Unit,
    onCloseShelf: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.readingChapter != null -> NovelReader(
                    state = state,
                    onBack = viewModel::closeReader,
                    onPrevious = viewModel::readPrevious,
                    onNext = viewModel::readNext,
                )
                state.openedBook != null -> BookDetail(
                    state = state,
                    onBack = viewModel::closeBook,
                    onRead = viewModel::openReader,
                    onDelete = { viewModel.deleteBook(state.openedBook!!.id) },
                    onSaveMetadata = viewModel::saveMetadata,
                    onGenerateIdentity = viewModel::generateIdentity,
                    onApplyIdentity = viewModel::applyIdentitySuggestion,
                    onGenerateCover = viewModel::generateCover,
                    onEnterWorkspace = { onEnterWorkspace(state.openedBook!!.id) },
                )
                else -> EnhancedShelf(
                    state = state,
                    onOpen = viewModel::openBook,
                    onCreate = { showCreate = true },
                    onClose = onCloseShelf,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
        }
    }

    if (showCreate) CreateBookDialog(
        busy = state.isBusy,
        onDismiss = { showCreate = false },
        onCreate = { title, genre, premise, theme, target ->
            viewModel.createStory(title, genre, premise, theme, target)
            showCreate = false
        },
    )
}

@Composable
private fun EnhancedShelf(state: LibraryExperienceState, onOpen: (String) -> Unit, onCreate: () -> Unit, onClose: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛书架", style = MaterialTheme.typography.displaySmall)
                    Text("像小说 App 一样打开作品、目录与正文", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.stories.isNotEmpty()) IconButton(onClose) { Icon(Icons.Rounded.Close, "进入工作台") }
            }
        }
        item {
            Button(onCreate, Modifier.fillMaxWidth().height(52.dp), enabled = !state.isBusy, shape = LanghuanShape.card) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("新建小说")
            }
        }
        if (state.stories.isEmpty()) {
            item {
                Surface(shape = LanghuanShape.sheet, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(14.dp))
                        Text("书架是空的", style = MaterialTheme.typography.headlineSmall)
                        Text("默认演示小说已经可以彻底删除。新建一本，或回到工作台导入稿件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(state.stories, key = { it.id }) { book -> BookShelfCard(book) { onOpen(book.id) } }
    }
}

@Composable
private fun BookShelfCard(book: ReaderBookUi, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = LanghuanShape.sheet,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(book.coverPath, book.title, Modifier.width(88.dp).height(126.dp))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(book.genre, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(book.premise, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(9.dp))
                Text("${book.currentWords} 字 · 写到第 ${book.currentChapter} 章", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun BookDetail(
    state: LibraryExperienceState,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onDelete: () -> Unit,
    onSaveMetadata: (String, String) -> Unit,
    onGenerateIdentity: () -> Unit,
    onApplyIdentity: () -> Unit,
    onGenerateCover: () -> Unit,
    onEnterWorkspace: () -> Unit,
) {
    val book = state.openedBook ?: return
    var editing by remember(book.id) { mutableStateOf(false) }
    var title by remember(book.title) { mutableStateOf(book.title) }
    var premise by remember(book.premise) { mutableStateOf(book.premise) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("作品详情", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ editing = !editing }) { Icon(Icons.Rounded.Edit, "编辑作品资料") }
                IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "删除小说", tint = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Surface(shape = LanghuanShape.sheet, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row {
                        CoverImage(book.coverPath, book.title, Modifier.width(126.dp).height(180.dp))
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(book.genre, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(9.dp))
                            Text(book.premise, maxLines = 7, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Text("${book.currentWords} / ${book.targetWords} 字", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onEnterWorkspace, Modifier.fillMaxWidth(), shape = LanghuanShape.card) {
                        Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(7.dp)); Text("进入创作工作台")
                    }
                }
            }
        }
        item {
            Surface(shape = LanghuanShape.sheet, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("AI 作品包装", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("书名、简介和封面都可以由当前 AI 辅助生成；封面在手机本地合成，不会把你的正文图片上传。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onGenerateIdentity, Modifier.weight(1f), enabled = !state.isBusy, shape = LanghuanShape.card) { Text("AI 书名/简介") }
                        OutlinedButton(onGenerateCover, Modifier.weight(1f), enabled = !state.isBusy, shape = LanghuanShape.card) { Text("AI 生成封面") }
                    }
                    state.identitySuggestion?.let { suggestion ->
                        Spacer(Modifier.height(12.dp))
                        Text(suggestion.title, style = MaterialTheme.typography.titleLarge)
                        Text(suggestion.premise, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onApplyIdentity, enabled = !state.isBusy, shape = LanghuanShape.card) { Text("采用这套书名和简介") }
                    }
                }
            }
        }
        if (editing) {
            item {
                Surface(shape = LanghuanShape.sheet, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("编辑作品资料", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("书名") }, shape = LanghuanShape.card)
                        OutlinedTextField(premise, { premise = it }, Modifier.fillMaxWidth(), label = { Text("作品简介") }, minLines = 4, shape = LanghuanShape.card)
                        Button({ onSaveMetadata(title, premise); editing = false }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && premise.isNotBlank() && !state.isBusy, shape = LanghuanShape.card) { Text("保存资料") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("共 ${state.chapters.size} 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.chapters, key = { it.chapterNumber }) { chapter ->
            Surface(
                Modifier.fillMaxWidth().clickable { onRead(chapter.chapterNumber) },
                shape = LanghuanShape.card,
                tonalElevation = 1.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chapter.chapterNumber.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold)
                        Text(if (chapter.content.isBlank()) "暂无正文" else "${chapter.content.length} 字", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除《${book.title}》？") },
            text = { Text("会删除这本小说的章节、版本、记忆和封面文件。此操作不能撤销。") },
            confirmButton = { Button({ confirmDelete = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("彻底删除") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NovelReader(state: LibraryExperienceState, onBack: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val chapter = state.readingChapter ?: return
    var fontSize by remember { mutableFloatStateOf(19f) }
    val hasPrevious = state.chapters.any { it.chapterNumber == chapter.chapterNumber - 1 }
    val hasNext = state.chapters.any { it.chapterNumber == chapter.chapterNumber + 1 }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回目录") }
            Column(Modifier.weight(1f)) {
                Text("第 ${chapter.chapterNumber} 章", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
        SelectionContainer(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text(chapter.title, fontSize = (fontSize + 7).sp, lineHeight = (fontSize + 14).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Text(
                    chapter.content.ifBlank { "这一章还没有正文。可以返回创作工作台继续写作。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.9f).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(40.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onPrevious, Modifier.weight(1f), enabled = hasPrevious, shape = LanghuanShape.card) { Text("上一章") }
                    Button(onNext, Modifier.weight(1f), enabled = hasNext, shape = LanghuanShape.card) { Text("下一章") }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", fontSize = 15.sp)
                Slider(fontSize, { fontSize = it }, valueRange = 15f..26f, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
                Text("Aa", fontSize = 23.sp)
            }
        }
    }
}

@Composable
private fun CoverImage(path: String, title: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { path.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
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

@Composable
private fun CreateBookDialog(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("200000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建小说") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("书名") })
                OutlinedTextField(genre, { genre = it }, label = { Text("类型") })
                OutlinedTextField(premise, { premise = it }, label = { Text("核心故事 / 简介") }, minLines = 3)
                OutlinedTextField(theme, { theme = it }, label = { Text("主题") })
                OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("目标字数") })
            }
        },
        confirmButton = { Button({ onCreate(title, genre, premise, theme, target.toIntOrNull() ?: 200000) }, enabled = title.isNotBlank() && !busy) { Text("创建") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
