package com.xiguli.langhuan.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * V101 · 我的 (local only).
 *
 * Header: a local avatar and name (editable, stored on this device). Rows: 阅历 (reading-time
 * statistics), 摘抄 (highlights and bookmarks of every book), 读过 (finished books) and 设置
 * (reader defaults + about). No sign-in, check-in, medals, sync or discovery.
 */

internal object LocalProfileStoreV101 {
    const val PREFS = "local_profile_v101"
    const val KEY_NAME = "name"
    const val DEFAULT_NAME = "书友"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun name(prefs: SharedPreferences): String = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
    fun saveName(prefs: SharedPreferences, name: String): Boolean {
        val clean = name.trim().take(16)
        return prefs.edit().putString(KEY_NAME, clean.ifBlank { DEFAULT_NAME }).commit()
    }
    fun avatarFile(context: Context) = File(context.filesDir, "profile_v101/avatar.jpg")
}

private enum class MinePageV101 { HOME, STATS, EXCERPTS, FINISHED, SETTINGS }

@Composable
fun LocalMinePageV101(
    books: List<LocalShelfBook>,
    shelfCount: Int,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(MinePageV101.HOME) }
    BackHandler { if (page == MinePageV101.HOME) onBack() else page = MinePageV101.HOME }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("mine_page"),
    ) {
        when (page) {
            MinePageV101.HOME -> MineHomeV101(books, shelfCount, onBack) { page = it }
            MinePageV101.STATS -> MineStatsV101 { page = MinePageV101.HOME }
            MinePageV101.EXCERPTS -> MineExcerptsV101(books, onOpenBook) { page = MinePageV101.HOME }
            MinePageV101.FINISHED -> MineFinishedV101(books, onOpenBook) { page = MinePageV101.HOME }
            MinePageV101.SETTINGS -> MineSettingsV101 { page = MinePageV101.HOME }
        }
    }
}

@Composable
private fun MineTopBarV101(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MineHomeV101(books: List<LocalShelfBook>, shelfCount: Int, onBack: () -> Unit, onPage: (MinePageV101) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LocalProfileStoreV101.prefs(context) }
    var name by remember { mutableStateOf(LocalProfileStoreV101.name(prefs)) }
    var editing by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableStateOf(0) }
    val avatar by produceState<ImageBitmap?>(null, avatarVersion) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = LocalProfileStoreV101.avatarFile(context)
                if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (importReaderBackdropImageV92(context, uri, maxEdgePx = 512, target = LocalProfileStoreV101.avatarFile(context))) {
                avatarVersion++
            }
        }
    }
    val stats = remember { ReaderStatsV35.snapshot(context) }
    val finished = books.count { it.progress.finished }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .testTag("mine_avatar"),
                contentAlignment = Alignment.Center,
            ) {
                val img = avatar
                if (img != null) Image(img, contentDescription = "头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.Person, contentDescription = "设置头像", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable { editing = true }.testTag("mine_name")) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("编辑个人资料 · 仅保存在本机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MineFigureV101(ReaderStatsV35.format(stats.total), "累计阅读")
            MineFigureV101("${stats.streak} 天", "连续阅读")
            MineFigureV101("${books.size}", "藏书 · $shelfCount 个书架")
            MineFigureV101("$finished", "读过")
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        MineRowV101(Icons.Outlined.BarChart, "阅历", "今日 ${ReaderStatsV35.format(stats.today)}", "mine_row_stats") { onPage(MinePageV101.STATS) }
        MineRowV101(Icons.Outlined.FormatQuote, "摘抄", "划线与书签", "mine_row_excerpts") { onPage(MinePageV101.EXCERPTS) }
        MineRowV101(Icons.Outlined.TaskAlt, "读过", "$finished 本", "mine_row_finished") { onPage(MinePageV101.FINISHED) }
        Spacer(Modifier.height(8.dp))
        MineRowV101(Icons.Outlined.Settings, "设置", "阅读默认 · 关于", "mine_row_settings") { onPage(MinePageV101.SETTINGS) }
    }

    if (editing) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑昵称") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(16) },
                    singleLine = true,
                    modifier = Modifier.testTag("mine_name_field"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalProfileStoreV101.saveName(prefs, draft)
                    name = LocalProfileStoreV101.name(prefs)
                    editing = false
                }, modifier = Modifier.testTag("mine_name_save")) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MineFigureV101(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MineRowV101(icon: ImageVector, label: String, hint: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MineStatsV101(onBack: () -> Unit) {
    val context = LocalContext.current
    val stats = remember { ReaderStatsV35.snapshot(context) }
    MineTopBarV101("阅历", onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("mine_stats")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MineFigureV101(ReaderStatsV35.format(stats.today), "今日")
            MineFigureV101(ReaderStatsV35.format(stats.total), "累计")
            MineFigureV101("${stats.streak} 天", "连续")
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MineFigureV101("${stats.pages}", "翻页")
            MineFigureV101("${stats.chapters}", "读完章节")
        }
        Text("近 7 天", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        val max = (stats.week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60L)
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            stats.week.forEach { (label, seconds) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${seconds / 60}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .padding(vertical = 4.dp)
                            .width(16.dp)
                            .height((100f * seconds / max).roundToInt().coerceAtLeast(2).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(label, fontSize = 11.sp)
                }
            }
        }
        Text("单位：分钟。阅读时长只在本机统计。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

private data class MineExcerptV101(val bookId: String, val bookTitle: String, val title: String, val text: String, val time: Long)

@Composable
private fun MineExcerptsV101(books: List<LocalShelfBook>, onOpenBook: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by produceState(emptyList<MineExcerptV101>(), books) {
        value = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE)
            books.flatMap { book ->
                val highlights = ReaderParagraphHighlightStoreV50.load(prefs, book.id).getOrDefault(emptyList()).map {
                    MineExcerptV101(book.id, book.title, it.chapterTitle.ifBlank { "第${it.chapterIndex + 1}章" }, it.excerpt, it.updatedAt)
                }
                val marks = ReaderBookmarkStoreV49.load(prefs, book.id).getOrDefault(emptySet())
                    .mapNotNull { it.toIntOrNull() }.sorted().map { number ->
                        MineExcerptV101(book.id, book.title, "书签 · 第${number}章", "", 0L)
                    }
                highlights.sortedByDescending { it.time } + marks
            }
        }
    }
    MineTopBarV101("摘抄", onBack)
    if (items.isEmpty()) {
        Text(
            "还没有划线或书签。阅读时长按文字可划线，下拉页面或点书签图标可加书签。",
            modifier = Modifier.padding(20.dp).testTag("mine_excerpts_empty"),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mine_excerpts")) {
        items(items) { item ->
            Column(Modifier.fillMaxWidth().clickable { onOpenBook(item.bookId) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("《${item.bookTitle}》 · ${item.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.text.isNotBlank()) Text(item.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MineFinishedV101(books: List<LocalShelfBook>, onOpenBook: (String) -> Unit, onBack: () -> Unit) {
    val finished = books.filter { it.progress.finished }.sortedByDescending { it.lastReadAt }
    MineTopBarV101("读过", onBack)
    if (finished.isEmpty()) {
        Text("读完最后一章的书会出现在这里。", modifier = Modifier.padding(20.dp).testTag("mine_finished_empty"),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mine_finished")) {
        items(finished, key = { it.id }) { book ->
            Row(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("已读完", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MineSettingsV101(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ReaderSettingsV30(context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE)) }
    LaunchedEffect(settings.snapshot()) { settings.save() }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    MineTopBarV101("设置", onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("mine_settings")) {
        Text("阅读默认", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        MineStepperRowV101("字号", "${settings.fontSize.roundToInt()}",
            onMinus = { settings.fontSize = (settings.fontSize - 1f).coerceAtLeast(ReaderSettingsV30.MIN_FONT) },
            onPlus = { settings.fontSize = (settings.fontSize + 1f).coerceAtMost(ReaderSettingsV30.MAX_FONT) })
        MineStepperRowV101("行间距", String.format(java.util.Locale.US, "%.1f", settings.lineFactor),
            onMinus = { settings.lineFactor = (settings.lineFactor - 0.1f).coerceAtLeast(ReaderSettingsV30.MIN_LINE) },
            onPlus = { settings.lineFactor = (settings.lineFactor + 0.1f).coerceAtMost(ReaderSettingsV30.MAX_LINE) })
        MineSwitchRowV101("上下滚动翻页", settings.turnMode == ReaderTurnModeV30.SCROLL) {
            settings.selectTurnMode(if (it) ReaderTurnModeV30.SCROLL else settings.lastPagedMode)
        }
        MineSwitchRowV101("夜间模式跟随系统", settings.followSystemNight) { settings.followSystemNight = it }
        MineSwitchRowV101("音量键翻页", settings.volumeTurn) { settings.volumeTurn = it }
        MineSwitchRowV101("阅读时屏幕常亮", settings.keepScreen) { settings.keepScreen = it }
        MineSwitchRowV101("锁定竖屏", settings.lockPortrait) { settings.lockPortrait = it }
        Text("关于", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text("琅嬛 $version", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "本地阅读器：支持 TXT / Markdown / EPUB 导入，书籍、进度、主题和统计都只保存在本机，不联网、不需要账号。",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun MineStepperRowV101(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text("－", fontSize = 18.sp) }
        Text(value, fontSize = 15.sp, modifier = Modifier.width(44.dp), maxLines = 1)
        TextButton(onClick = onPlus) { Text("＋", fontSize = 18.sp) }
    }
}

@Composable
private fun MineSwitchRowV101(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
