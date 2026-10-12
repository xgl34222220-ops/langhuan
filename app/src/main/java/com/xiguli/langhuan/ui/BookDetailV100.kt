package com.xiguli.langhuan.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.ThinIconsV102
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.data.shelf.BookFacts
import kotlinx.coroutines.launch

private enum class DetailTabV100 { DETAILS, COVER }

/**
 * 图书详情: cover and editable 书名/作者 at the top, then 详情 (progress and file facts) and
 * 封面 (pick a generated cover, the imported artwork or a gallery picture). ✓ saves.
 */
@Composable
fun BookDetailPageV100(
    book: LocalShelfBook,
    groupName: String,
    detail: LocalBookDetail?,
    onBack: () -> Unit,
    onSave: (title: String, author: String, cover: CoverChoice?) -> Unit,
    onImportCover: suspend (android.net.Uri) -> String?,
    initialCoverTab: Boolean = false,
) {
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
    var tab by rememberSaveable(book.id) { mutableStateOf(if (initialCoverTab) DetailTabV100.COVER else DetailTabV100.DETAILS) }
    // Pending cover choice, encoded so it survives recreation: "o" original, "g:<n>", "c:<path>".
    var coverKey by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    val choice: CoverChoice? = coverKey?.let { key ->
        when {
            key == "o" -> CoverChoice.Original
            key.startsWith("g:") -> CoverChoice.Generated(key.removePrefix("g:").toIntOrNull() ?: 0)
            key.startsWith("c:") -> CoverChoice.Custom(key.removePrefix("c:"))
            else -> null
        }
    }
    val previewPath = when (choice) {
        null -> coverPathForV100(book)
        CoverChoice.Original -> book.book.coverPath
        is CoverChoice.Generated -> ""
        is CoverChoice.Custom -> choice.path
    }
    val previewStyle = (choice as? CoverChoice.Generated)?.style
        ?: book.meta.generatedCoverStyle ?: defaultCoverStyleV100(book.title)
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onImportCover(uri)?.let { coverKey = "c:$it" } }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().testTag("book_detail")) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(ThinIconsV102.Back, contentDescription = "返回", modifier = Modifier.size(24.dp)) }
            Text("图书详情", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { onSave(title, author, choice) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("detail_save"),
            ) { Icon(ThinIconsV102.Check, contentDescription = "保存", modifier = Modifier.size(24.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            CoverImageOrGeneratedV100(
                path = previewPath,
                title = title.ifBlank { book.title },
                author = author,
                style = previewStyle,
                modifier = Modifier.width(84.dp).aspectRatio(BOOK_COVER_RATIO_V100)
                    .shadow(3.dp, RoundedCornerShape(COVER_RADIUS_V102), ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.2f)),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                EditableFieldV100("书名", title, { title = it }, "detail_title")
                Spacer(Modifier.height(10.dp))
                EditableFieldV100("作者", author, { author = it }, "detail_author", placeholder = "未知作者")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            DetailTabLabelV100("详情", tab == DetailTabV100.DETAILS, "detail_tab_details") { tab = DetailTabV100.DETAILS }
            Spacer(Modifier.width(24.dp))
            DetailTabLabelV100("封面", tab == DetailTabV100.COVER, "detail_tab_cover") { tab = DetailTabV100.COVER }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        when (tab) {
            DetailTabV100.DETAILS -> DetailsTabV100(book, groupName, detail)
            DetailTabV100.COVER -> CoverTabV100(
                book = book,
                title = title.ifBlank { book.title },
                author = author,
                selectedKey = coverKey ?: currentCoverKeyV100(book),
                onPick = { coverKey = it },
                onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

private fun currentCoverKeyV100(book: LocalShelfBook): String = when {
    !book.meta.customCoverPath.isNullOrBlank() -> "c:${book.meta.customCoverPath}"
    book.meta.preferGenerated || book.book.coverPath.isBlank() -> "g:${book.meta.generatedCoverStyle ?: defaultCoverStyleV100(book.title)}"
    else -> "o"
}

@Composable
private fun EditableFieldV100(label: String, value: String, onChange: (String) -> Unit, tag: String, placeholder: String = "") {
    val t = LocalLanghuanUiTokens.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val line by animateColorAsState(if (focused) t.primary else t.border, animationSpec = tween(200), label = "underline")
    Text(label, fontSize = 12.sp, color = t.mutedForeground)
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, fontSize = 16.sp, color = t.mutedForeground.copy(alpha = 0.7f))
        }
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace("\n", "").take(60)) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = t.foreground),
            cursorBrush = SolidColor(t.primary),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(if (focused) 1.5.dp else 0.5.dp).background(line))
}

@Composable
private fun DetailTabLabelV100(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).testTag(tag).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) MaterialTheme.colorScheme.onBackground else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun DetailsTabV100(book: LocalShelfBook, groupName: String, detail: LocalBookDetail?) {
    val fraction = book.progress.fraction ?: if (book.progress.started) 0f else null
    val total = detail?.chapterCount ?: book.meta.chapterCount ?: book.chapterTotal
    val currentNumber = detail?.currentChapterNumber ?: (book.chapterIndex + 1).coerceAtLeast(if (book.progress.started) 1 else 0)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("detail_details_tab"),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                BookFacts.formatPercent(fraction),
                fontSize = 46.sp,
                lineHeight = 48.sp,
                fontFamily = PROGRESS_FONT_V102,
                letterSpacing = (-0.5).sp,
                color = LocalLanghuanUiTokens.current.foreground,
            )
            Text("%", fontSize = 15.sp, fontFamily = PROGRESS_FONT_V102, modifier = Modifier.padding(start = 3.dp, bottom = 8.dp), color = LocalLanghuanUiTokens.current.mutedForeground)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (total > 0) "读到 ${currentNumber.coerceAtMost(total)} / $total 章" else "尚未阅读",
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = detail?.currentChapterTitle?.takeIf { book.progress.started }.orEmpty().ifBlank { if (book.progress.started) "" else "还没有开始阅读" },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { (fraction ?: 0f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = LocalLanghuanUiTokens.current.primary,
            trackColor = LocalLanghuanUiTokens.current.track,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Spacer(Modifier.height(20.dp))
        val words = detail?.wordCount ?: book.meta.wordCount
        val rows = listOf(
            "文件名称" to book.file.fileName.ifBlank { "《${book.title}》" },
            "文件大小" to BookFacts.formatSize(book.file.sizeBytes),
            "文件格式" to BookFacts.formatLabel(book.file.fileName, book.file.format),
            "全文字数" to (words?.let(BookFacts::formatWords) ?: "计算中…"),
            "总章节数" to (if (total > 0) "$total 章" else "计算中…"),
            "所在书架" to groupName,
            "同步状态" to "仅本地",
            "导入时间" to BookFacts.formatTime(book.importedAt),
            "最近阅读" to BookFacts.formatTime(book.lastReadAt),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(LocalLanghuanUiTokens.current.input)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.5.sp, color = LocalLanghuanUiTokens.current.mutedForeground)
                    Spacer(Modifier.width(24.dp))
                    Text(
                        value,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CoverTabV100(
    book: LocalShelfBook,
    title: String,
    author: String,
    selectedKey: String,
    onPick: (String) -> Unit,
    onGallery: () -> Unit,
) {
    val options = buildList {
        if (book.book.coverPath.isNotBlank()) add("o")
        book.meta.customCoverPath?.takeIf { it.isNotBlank() }?.let { add("c:$it") }
        if (selectedKey.startsWith("c:") && selectedKey !in this) add(selectedKey)
        repeat(GENERATED_COVER_STYLES_V100) { add("g:$it") }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("detail_cover_tab"),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(1) }) {
            Column(
                Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_RATIO_V100).clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .clickable(onClick = onGallery).testTag("cover_gallery"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(ThinIconsV102.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("从相册选择", fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(options, key = { it }) { key ->
            Box(Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_RATIO_V100).clickable { onPick(key) }.testTag("cover_option_$key")) {
                val path = when {
                    key == "o" -> book.book.coverPath
                    key.startsWith("c:") -> key.removePrefix("c:")
                    else -> ""
                }
                CoverImageOrGeneratedV100(
                    path = path,
                    title = title,
                    author = author,
                    style = key.removePrefix("g:").toIntOrNull() ?: 0,
                    modifier = Modifier.fillMaxSize(),
                    targetWidthPx = 240,
                )
                if (key == selectedKey) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                            .border(1.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ThinIconsV102.Check, contentDescription = "已选择", modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
                if (key == "o" || key.startsWith("c:")) {
                    Text(
                        if (key == "o") "原封面" else "相册",
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }
    }
}

/** Condensed bold numerals for the big 进度 figure (reference: a geometric/condensed display face). */
private val PROGRESS_FONT_V102: FontFamily =
    FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
