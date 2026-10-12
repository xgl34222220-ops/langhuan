package com.xiguli.langhuan.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import com.xiguli.langhuan.ui.design.pagePushV103
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * V101 · 我的 (local only). V104: the right-hand page of the home pager.
 *
 * Header: a local avatar and name (editable, stored on this device), then a 本周阅读 card (week
 * total, streak, a seven-day bar chart and three figures). Rows: 阅历 (reading-time statistics),
 * 摘抄 (highlights and bookmarks of every book), 读过 (finished books) and 设置 (reader defaults +
 * about). No sign-in, check-in, medals, sync or discovery.
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
    visible: Boolean = true,
    onSubPageChanged: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(MinePageV101.HOME) }
    LaunchedEffect(page) { onSubPageChanged(page != MinePageV101.HOME) }
    // On HOME, back belongs to the home pager (predictive back slides 我的 off with the finger).
    BackHandler(enabled = page != MinePageV101.HOME) { page = MinePageV101.HOME }
    // Sub-pages push in full width like every other page (measured reference motion).
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = { pagePushV103(forward = targetState != MinePageV101.HOME) },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("mine_page"),
        label = "mine_page",
    ) { target ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            when (target) {
                MinePageV101.HOME -> MineHomeV101(books, shelfCount, visible, onBack) { page = it }
                MinePageV101.STATS -> MineStatsV101 { page = MinePageV101.HOME }
                MinePageV101.EXCERPTS -> MineExcerptsV101(books, onOpenBook) { page = MinePageV101.HOME }
                MinePageV101.FINISHED -> MineFinishedV101(books, onOpenBook) { page = MinePageV101.HOME }
                MinePageV101.SETTINGS -> MineSettingsV101 { page = MinePageV101.HOME }
            }
        }
    }
}

@Composable
private fun MineTopBarV101(title: String, onBack: () -> Unit) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(com.xiguli.langhuan.ui.design.ThinIconsV102.Back, contentDescription = "返回", modifier = Modifier.size(24.dp), tint = t.foreground)
        }
        Text(title, fontSize = TypeScaleV104.headline, fontWeight = FontWeight.Bold, color = t.foreground)
    }
}

/** V104 type scale shared by 我的, 书架列表 and 图书详情 (sp): one step per role, no in-betweens. */
internal object TypeScaleV104 {
    val display = 30.sp
    val title = 20.sp
    val headline = 18.sp
    val body = 15.sp
    val label = 13.sp
    val caption = 12.sp
    val micro = 11.sp
}

/** Big numerals for figures (condensed bold, like the 图书详情 progress figure). */
internal val FIGURE_FONT_V104: androidx.compose.ui.text.font.FontFamily =
    androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))

/** Splits a duration into (number, unit) runs for typographic display: 2 小时 14 分 / 35 分钟 / 20 秒. */
internal fun durationRunsV104(seconds: Long): List<Pair<String, String>> = when {
    seconds < 60 -> listOf("${seconds.coerceAtLeast(0)}" to "秒")
    seconds < 3600 -> listOf("${seconds / 60}" to "分钟")
    (seconds % 3600) / 60 == 0L -> listOf("${seconds / 3600}" to "小时")
    else -> listOf("${seconds / 3600}" to "小时", "${(seconds % 3600) / 60}" to "分")
}

@Composable
private fun DurationTextV104(seconds: Long, numberSize: androidx.compose.ui.unit.TextUnit, unitSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    val text = androidx.compose.ui.text.buildAnnotatedString {
        durationRunsV104(seconds).forEachIndexed { index, (number, unit) ->
            if (index > 0) append(" ")
            pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = numberSize, fontFamily = FIGURE_FONT_V104, color = t.foreground))
            append(number)
            pop()
            pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = unitSize, color = t.mutedForeground, fontWeight = FontWeight.Medium))
            append(" ")
            append(unit)
            pop()
        }
    }
    Text(text, modifier = modifier, maxLines = 1)
}

/**
 * Seven pill bars (oldest → today). Today is the accent, other days a light tint of it, empty days
 * a short track dot; the bars grow in once when [animate] first becomes true.
 */
@Composable
private fun WeekBarsV104(
    week: List<Pair<String, Long>>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 64.dp,
    showMinutes: Boolean = false,
) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(animate) {
        if (animate && grow.value < 1f) grow.animateTo(1f, androidx.compose.animation.core.tween(560, easing = com.xiguli.langhuan.ui.design.PagePushV103.Easing))
    }
    val max = (week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60L)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        week.forEach { (label, seconds) ->
            val today = label == "今"
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showMinutes) {
                    Text(
                        if (seconds >= 60) "${seconds / 60}" else "",
                        fontSize = TypeScaleV104.micro,
                        color = if (today) t.primary else t.mutedForeground,
                        modifier = Modifier.padding(bottom = 4.dp),
                        maxLines = 1,
                    )
                }
                Box(Modifier.height(barHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (seconds.toFloat() / max).coerceIn(0f, 1f) * grow.value
                    if (seconds < 60) {
                        Box(Modifier.width(12.dp).height(4.dp).clip(CircleShape).background(t.track))
                    } else {
                        Box(
                            Modifier
                                .width(12.dp)
                                .height((barHeight * fraction).coerceAtLeast(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (today) t.primary else t.primary.copy(alpha = 0.26f)),
                        )
                    }
                }
                Text(
                    label,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = TypeScaleV104.micro,
                    fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (today) t.foreground else t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun MineFigureV104(value: String, label: String, modifier: Modifier = Modifier) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontFamily = FIGURE_FONT_V104, color = t.foreground, maxLines = 1)
        Text(label, modifier = Modifier.padding(top = 2.dp), fontSize = TypeScaleV104.micro, color = t.mutedForeground, maxLines = 1)
    }
}

/** Compact figure for the card footer: "2.5 h" style values stay short. */
private fun shortDurationV104(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(0)}秒"
    seconds < 3600 -> "${seconds / 60}分"
    else -> String.format(java.util.Locale.US, "%.1f时", seconds / 3600f).replace(".0时", "时")
}

@Composable
private fun MineHomeV101(books: List<LocalShelfBook>, shelfCount: Int, visible: Boolean, onBack: () -> Unit, onPage: (MinePageV101) -> Unit) {
    val context = LocalContext.current
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
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
    // Re-read whenever the page settles into view (reading time grows while 我的 sits off-screen).
    val stats = remember(visible, books) { ReaderStatsV35.snapshot(context) }
    val finished = books.count { it.progress.finished }
    val weekTotal = stats.week.sumOf { it.second }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("mine_back")) {
                Icon(com.xiguli.langhuan.ui.design.ThinIconsV102.Back, contentDescription = "返回书架", modifier = Modifier.size(24.dp), tint = t.foreground)
            }
        }
        // Profile header: 64 dp avatar with a hairline ring, 20 sp name, 12 sp sub-line.
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(t.input)
                    .border(0.5.dp, t.border, CircleShape)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .testTag("mine_avatar"),
                contentAlignment = Alignment.Center,
            ) {
                val img = avatar
                if (img != null) Image(img, contentDescription = "头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(com.xiguli.langhuan.ui.design.ThinIconsV102.Person, contentDescription = "设置头像", tint = t.mutedForeground, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editing = true }.testTag("mine_name").padding(vertical = 4.dp)) {
                Text(name, fontSize = TypeScaleV104.title, fontWeight = FontWeight.Bold, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "藏书 ${books.size} 本 · 书架 $shelfCount 个",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = TypeScaleV104.caption,
                    color = t.mutedForeground,
                    maxLines = 1,
                )
            }
            Icon(
                com.xiguli.langhuan.ui.design.ThinIconsV102.Pencil,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.mutedForeground.copy(alpha = 0.7f),
            )
        }

        // 本周阅读 card: week total + streak, seven bars, then 今日 / 累计 / 读过.
        val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.98f else 1f, label = "week_press")
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(16.dp))
                .background(t.input)
                .clickable(interactionSource = source, indication = null) { onPage(MinePageV101.STATS) }
                .testTag("mine_week_card")
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("本周阅读", fontSize = TypeScaleV104.label, color = t.mutedForeground)
                    DurationTextV104(weekTotal, TypeScaleV104.display, TypeScaleV104.label, Modifier.padding(top = 2.dp))
                }
                Text(
                    if (stats.streak > 0) "连续 ${stats.streak} 天" else "今天开始读",
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .background(t.card)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = TypeScaleV104.caption,
                    fontWeight = FontWeight.Medium,
                    color = if (stats.streak > 0) t.primary else t.mutedForeground,
                )
            }
            WeekBarsV104(stats.week, animate = visible, modifier = Modifier.padding(top = 16.dp))
            Box(Modifier.padding(top = 16.dp, bottom = 12.dp).fillMaxWidth().height(0.5.dp).background(t.border))
            Row(Modifier.fillMaxWidth()) {
                MineFigureV104(shortDurationV104(stats.today), "今日", Modifier.weight(1f))
                MineFigureV104(shortDurationV104(stats.total), "累计", Modifier.weight(1f))
                MineFigureV104("$finished", "读完", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        MineRowV101(com.xiguli.langhuan.ui.design.ThinIconsV102.Chart, "阅历", "今日 ${ReaderStatsV35.format(stats.today)}", "mine_row_stats") { onPage(MinePageV101.STATS) }
        MineHairlineV103(inset = 58.dp)
        MineRowV101(com.xiguli.langhuan.ui.design.ThinIconsV102.Quote, "摘抄", "划线与书签", "mine_row_excerpts") { onPage(MinePageV101.EXCERPTS) }
        MineHairlineV103(inset = 58.dp)
        MineRowV101(com.xiguli.langhuan.ui.design.ThinIconsV102.Done, "读过", "$finished 本", "mine_row_finished") { onPage(MinePageV101.FINISHED) }
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(8.dp).background(t.input.copy(alpha = 0.6f)))
        MineRowV101(com.xiguli.langhuan.ui.design.ThinIconsV102.Settings, "设置", "阅读默认 · 关于", "mine_row_settings") { onPage(MinePageV101.SETTINGS) }
        Text(
            "琅嬛 · 书与进度只保存在本机",
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = TypeScaleV104.micro,
            color = t.mutedForeground.copy(alpha = 0.7f),
        )
    }

    if (editing) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = t.card,
            title = { Text("编辑昵称", fontSize = TypeScaleV104.headline, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(16) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("mine_name_field"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalProfileStoreV101.saveName(prefs, draft)
                    name = LocalProfileStoreV101.name(prefs)
                    editing = false
                }, modifier = Modifier.testTag("mine_name_save")) { Text("保存", color = t.primary) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消", color = t.secondaryForeground) } },
        )
    }
}

@Composable
private fun MineHairlineV103(inset: androidx.compose.ui.unit.Dp) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(t.border))
}

@Composable
private fun MineRowV101(icon: ImageVector, label: String, hint: String, tag: String, onClick: () -> Unit) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(start = 20.dp, end = 16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = t.foreground)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = TypeScaleV104.body, fontWeight = FontWeight.Medium, color = t.foreground, modifier = Modifier.weight(1f))
        Text(hint, fontSize = TypeScaleV104.caption, color = t.mutedForeground, maxLines = 1)
        Icon(
            com.xiguli.langhuan.ui.design.ThinIconsV102.Chevron,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
            tint = t.mutedForeground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MineStatsV101(onBack: () -> Unit) {
    val context = LocalContext.current
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    val stats = remember { ReaderStatsV35.snapshot(context) }
    MineTopBarV101("阅历", onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("mine_stats")) {
        Column(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 24.dp)) {
            Text("今日阅读", fontSize = TypeScaleV104.label, color = t.mutedForeground)
            DurationTextV104(stats.today, 40.sp, TypeScaleV104.body, Modifier.padding(top = 2.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.input)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("近 7 天", fontSize = TypeScaleV104.label, color = t.mutedForeground, modifier = Modifier.weight(1f))
                Text("单位：分钟", fontSize = TypeScaleV104.micro, color = t.mutedForeground.copy(alpha = 0.8f))
            }
            WeekBarsV104(stats.week, animate = true, modifier = Modifier.padding(top = 12.dp), barHeight = 120.dp, showMinutes = true)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.input).padding(vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth()) {
                MineFigureV104(shortDurationV104(stats.total), "累计时长", Modifier.weight(1f))
                MineFigureV104("${stats.streak} 天", "连续阅读", Modifier.weight(1f))
            }
            Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(0.5.dp).background(t.border))
            Row(Modifier.fillMaxWidth()) {
                MineFigureV104("${stats.pages}", "翻页", Modifier.weight(1f))
                MineFigureV104("${stats.chapters}", "读完章节", Modifier.weight(1f))
            }
        }
        Text(
            "阅读时长只在本机统计，满 1 分钟的日子计入连续阅读。",
            fontSize = TypeScaleV104.caption,
            color = t.mutedForeground,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 32.dp),
        )
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
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    MineTopBarV101("摘抄", onBack)
    if (items.isEmpty()) {
        com.xiguli.langhuan.ui.design.EmptyStateV104(
            art = com.xiguli.langhuan.ui.design.EmptyArtV104.NOTES,
            title = "还没有摘抄",
            body = "阅读时长按文字可划线，点书签图标可加书签，都会收在这里。",
            modifier = Modifier.fillMaxSize().padding(bottom = 56.dp).testTag("mine_excerpts_empty"),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mine_excerpts"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
        items(items) { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenBook(item.bookId) }
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier.padding(top = 2.dp).width(2.dp).height(if (item.text.isNotBlank()) 40.dp else 16.dp)
                        .clip(CircleShape).background(t.primary.copy(alpha = if (item.text.isNotBlank()) 0.55f else 0.25f)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (item.text.isNotBlank()) {
                        Text(item.text, fontSize = TypeScaleV104.body, lineHeight = 24.sp, color = t.foreground, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "《${item.bookTitle}》 · ${item.title}",
                        modifier = Modifier.padding(top = if (item.text.isNotBlank()) 8.dp else 0.dp),
                        fontSize = TypeScaleV104.caption,
                        color = t.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MineHairlineV103(inset = 36.dp)
        }
    }
}

@Composable
private fun MineFinishedV101(books: List<LocalShelfBook>, onOpenBook: (String) -> Unit, onBack: () -> Unit) {
    val finished = books.filter { it.progress.finished }.sortedByDescending { it.lastReadAt }
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    MineTopBarV101("读过", onBack)
    if (finished.isEmpty()) {
        com.xiguli.langhuan.ui.design.EmptyStateV104(
            art = com.xiguli.langhuan.ui.design.EmptyArtV104.FINISHED,
            title = "还没有读完的书",
            body = "读完最后一章的书会出现在这里。",
            modifier = Modifier.fillMaxSize().padding(bottom = 56.dp).testTag("mine_finished_empty"),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("mine_finished"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
        items(finished, key = { it.id }) { book ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCoverV100(book, Modifier.width(44.dp).aspectRatio(BOOK_COVER_RATIO_V100), targetWidthPx = 160)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, fontSize = TypeScaleV104.body, fontWeight = FontWeight.Medium, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        book.author.ifBlank { "佚名" },
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = TypeScaleV104.caption,
                        color = t.mutedForeground,
                        maxLines = 1,
                    )
                }
                Text("已读完", fontSize = TypeScaleV104.caption, color = t.primary)
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
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    MineTopBarV101("设置", onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("mine_settings")) {
        MineSectionLabelV104("阅读默认")
        MineStepperRowV101("字号", "${settings.fontSize.roundToInt()}",
            onMinus = { settings.fontSize = (settings.fontSize - 1f).coerceAtLeast(ReaderSettingsV30.MIN_FONT) },
            onPlus = { settings.fontSize = (settings.fontSize + 1f).coerceAtMost(ReaderSettingsV30.MAX_FONT) })
        MineHairlineV103(inset = 20.dp)
        MineStepperRowV101("行间距", String.format(java.util.Locale.US, "%.1f", settings.lineFactor),
            onMinus = { settings.lineFactor = (settings.lineFactor - 0.1f).coerceAtLeast(ReaderSettingsV30.MIN_LINE) },
            onPlus = { settings.lineFactor = (settings.lineFactor + 0.1f).coerceAtMost(ReaderSettingsV30.MAX_LINE) })
        MineHairlineV103(inset = 20.dp)
        MineSwitchRowV101("上下滚动翻页", settings.turnMode == ReaderTurnModeV30.SCROLL) {
            settings.selectTurnMode(if (it) ReaderTurnModeV30.SCROLL else settings.lastPagedMode)
        }
        MineHairlineV103(inset = 20.dp)
        MineSwitchRowV101("夜间模式跟随系统", settings.followSystemNight) { settings.followSystemNight = it }
        MineSectionLabelV104("操作与屏幕")
        MineSwitchRowV101("音量键翻页", settings.volumeTurn) { settings.volumeTurn = it }
        MineHairlineV103(inset = 20.dp)
        MineSwitchRowV101("阅读时屏幕常亮", settings.keepScreen) { settings.keepScreen = it }
        MineHairlineV103(inset = 20.dp)
        MineSwitchRowV101("锁定竖屏", settings.lockPortrait) { settings.lockPortrait = it }
        MineSectionLabelV104("关于")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("琅嬛 $version", fontSize = TypeScaleV104.body, fontWeight = FontWeight.SemiBold, color = t.foreground)
            Text(
                "本地阅读器：支持 TXT / Markdown / EPUB 导入，书籍、进度、主题和统计都只保存在本机，不联网、不需要账号。",
                fontSize = TypeScaleV104.label, lineHeight = 20.sp, color = t.mutedForeground, modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun MineSectionLabelV104(text: String) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Text(
        text,
        fontSize = TypeScaleV104.caption,
        fontWeight = FontWeight.Medium,
        color = t.mutedForeground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun MineStepperRowV101(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TypeScaleV104.body, color = t.foreground, modifier = Modifier.weight(1f))
        MineStepButtonV104("－", "减小$label", onMinus)
        Text(value, fontSize = TypeScaleV104.body, fontFamily = FIGURE_FONT_V104, color = t.foreground, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(44.dp), maxLines = 1)
        MineStepButtonV104("＋", "增大$label", onPlus)
    }
}

@Composable
private fun MineStepButtonV104(symbol: String, description: String, onClick: () -> Unit) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(t.input).clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 16.sp, color = t.foreground) }
}

@Composable
private fun MineSwitchRowV101(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onChange(!checked) }.padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = TypeScaleV104.body, color = t.foreground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = t.primary,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = t.track,
                uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}
