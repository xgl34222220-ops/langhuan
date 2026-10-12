package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Reader V101 · 主题 pages (inside the reader menu sheet).
 *
 *  LIST  — 日间主题 / 夜间主题: theme cards (page colour + decoration) with ✓ on the selection and
 *          ⋮ to remove; + opens 新建主题 / 更多主题.
 *  MORE  — bundled extra themes for the current list (added, never downloaded).
 *  NEW   — create a theme: name, background colour or picture, text colour.
 */

private enum class ReaderThemePageV101 { LIST, MORE, NEW }

@Composable
internal fun ReaderThemePagesV101(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(ReaderThemePageV101.LIST) }
    var nightList by rememberSaveable { mutableStateOf(settings.nightMode) }
    when (page) {
        ReaderThemePageV101.LIST -> ReaderThemeListPageV101(
            settings = settings,
            night = nightList,
            onNight = { nightList = it },
            onBack = onBack,
            onMore = { page = ReaderThemePageV101.MORE },
            onNew = { page = ReaderThemePageV101.NEW },
        )
        ReaderThemePageV101.MORE -> ReaderMoreThemesPageV101(
            settings = settings,
            night = nightList,
            onBack = { page = ReaderThemePageV101.LIST },
        )
        ReaderThemePageV101.NEW -> ReaderNewThemePageV101(
            settings = settings,
            night = nightList,
            onDone = { page = ReaderThemePageV101.LIST },
        )
    }
}

@Composable
private fun ReaderThemeListPageV101(
    settings: ReaderSettingsV30,
    night: Boolean,
    onNight: (Boolean) -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onNew: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var plusOpen by remember { mutableStateOf(false) }
    val keys = if (night) settings.nightInstalled else settings.dayInstalled
    val selected = if (night) settings.nightTheme else settings.dayTheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4)
            .padding(bottom = t.space4)
            .testTag(if (night) "reader_theme_night" else "reader_theme_day"),
    ) {
        ReaderSubPageHeaderV101(
            title = "",
            onBack = onBack,
            titleContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ReaderThemeModeTabV101("日间主题", !night) { onNight(false) }
                    ReaderThemeModeTabV101("夜间主题", night) { onNight(true) }
                }
            },
            action = {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { plusOpen = true }
                            .semantics { contentDescription = "添加主题" }
                            .testTag("reader_theme_add")
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = t.foreground)
                    }
                    DropdownMenu(expanded = plusOpen, onDismissRequest = { plusOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("新建主题") },
                            onClick = { plusOpen = false; onNew() },
                            modifier = Modifier.testTag("reader_theme_new"),
                        )
                        DropdownMenuItem(
                            text = { Text("更多主题") },
                            onClick = { plusOpen = false; onMore() },
                            modifier = Modifier.testTag("reader_theme_more"),
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(t.space2))
        keys.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = t.space3),
                horizontalArrangement = Arrangement.spacedBy(t.space3),
            ) {
                row.forEach { key ->
                    val theme = readerThemeV30(key)
                    var menuOpen by remember(key) { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        ReaderThemeCardV101(
                            theme = theme,
                            selected = key == selected,
                            onClick = { settings.selectTheme(key) },
                            onMenu = if (keys.size > 1) ({ menuOpen = true }) else null,
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (key.startsWith(ReaderThemeCatalogV101.CUSTOM_PREFIX)) "删除主题" else "移出列表") },
                                onClick = { menuOpen = false; settings.removeTheme(key) },
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            text = if (night) "夜间模式下正文使用选中的夜间主题；点阅读菜单的月亮图标或开启「背景跟随」切换。"
            else "白天使用选中的日间主题；主题保存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}

@Composable
private fun ReaderThemeModeTabV101(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) t.foreground else t.mutedForeground,
    )
}

/** A theme card: page colour + decoration, name in the white corner notch, ✓ when selected. */
@Composable
internal fun ReaderThemeCardV101(
    theme: ReaderThemeV30,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, theme.imagePath) {
        value = theme.imagePath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(File(path).absolutePath, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(shape)
            .background(theme.page)
            .border(0.5.dp, t.border.copy(alpha = 0.5f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "主题 ${theme.name}"; this.selected = selected }
            .testTag("reader_theme_card_${theme.key}"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val img = image
            if (img != null) {
                val (srcOffset, srcSize) = readerCenterCropV92(IntSize(img.width, img.height), Size(size.width, size.height))
                drawImage(img, srcOffset = srcOffset, srcSize = srcSize, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                drawRect(theme.page.copy(alpha = if (theme.dark) 0.45f else 0.30f))
            }
            when (theme.decor) {
                ReaderThemeDecorV101.BAMBOO -> drawReaderBambooV93(
                    theme.text.copy(alpha = if (theme.dark) 0.12f else 0.15f), size.width, size.height,
                )
                ReaderThemeDecorV101.FLOWER -> drawReaderBlossomV101(
                    theme.text.copy(alpha = if (theme.dark) 0.12f else 0.14f), size.width, size.height,
                )
                else -> Unit
            }
            // Every card carries the reference's soft corner arcs.
            drawReaderArcsV101(
                if (theme.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f),
                size.width * 0.62f, size.height * 0.80f,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(26.dp)
                    .background(Color(0xFF2F7BF6), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "已选择", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (onMenu != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onMenu)
                    .semantics { contentDescription = "主题选项 ${theme.name}" }
                    .padding(8.dp),
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = theme.text.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.62f)
                .background(t.card, RoundedCornerShape(topStart = 60.dp))
                .padding(start = 26.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (theme.subtitle.isNotBlank()) {
                Text(
                    text = theme.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            footer?.let {
                Spacer(Modifier.height(6.dp))
                it()
            }
        }
    }
}

@Composable
private fun ReaderMoreThemesPageV101(
    settings: ReaderSettingsV30,
    night: Boolean,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val extras = ReaderThemeCatalogV101.more(night) + (if (night) ReaderThemeCatalogV101.BUILT_IN_NIGHT else ReaderThemeCatalogV101.BUILT_IN_DAY)
    val installed = if (night) settings.nightInstalled else settings.dayInstalled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4)
            .padding(bottom = t.space4)
            .testTag("reader_theme_more_page"),
    ) {
        ReaderSubPageHeaderV101(title = if (night) "更多夜间主题" else "更多日间主题", onBack = onBack)
        Text(
            text = "主题随应用内置，添加后出现在主题列表，无需联网下载。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space3))
        extras.distinctBy { it.key }.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = t.space3),
                horizontalArrangement = Arrangement.spacedBy(t.space3),
            ) {
                row.forEach { spec ->
                    val added = spec.key in installed
                    Box(modifier = Modifier.weight(1f)) {
                        ReaderThemeCardV101(
                            theme = spec.toTheme(),
                            selected = false,
                            onClick = { settings.installTheme(spec.key) },
                            footer = {
                                Text(
                                    text = if (added) "已添加" else "添加",
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (added) t.foreground.copy(alpha = 0.08f) else Color(0xFF2F7BF6))
                                        .clickable(enabled = !added) { settings.installTheme(spec.key) }
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    color = if (added) t.mutedForeground else Color.White,
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val READER_THEME_DAY_SWATCHES_V101 = listOf(
    0xFFDCD8C2, 0xFFF7F5EF, 0xFFEBDDBF, 0xFFCFE0CB, 0xFFC7DFC4, 0xFFE3E7EE, 0xFFF4E4E1, 0xFFFFFFFF,
)
private val READER_THEME_NIGHT_SWATCHES_V101 = listOf(
    0xFF151516, 0xFF000000, 0xFF161B24, 0xFF161A16, 0xFF1E1914, 0xFF221A20, 0xFF1C1C1E, 0xFF2A2A2D,
)
private val READER_THEME_TEXT_SWATCHES_V101 = listOf(
    0xFF1C1A16, 0xFF151515, 0xFF3A2E1F, 0xFF1D2A1C, 0xFF1C2129, 0xFFA8A8AA, 0xFF9AA4B4, 0xFFB3A48C,
)

@Composable
private fun ReaderNewThemePageV101(
    settings: ReaderSettingsV30,
    night: Boolean,
    onDone: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { UUID.randomUUID().toString().replace("-", "").take(10) }
    var name by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(if (night) 0xFF151516 else 0xFFDCD8C2) }
    var text by rememberSaveable { mutableStateOf(if (night) 0xFFA8A8AA else 0xFF1C1A16) }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val target = File(context.filesDir, "reader/themes_v101/$id.jpg")
            if (importReaderBackdropImageV92(context, uri, target = target)) {
                imagePath = target.absolutePath
                error = null
            } else {
                error = "无法读取这张图片，请换一张试试"
            }
        }
    }
    val preview = ReaderThemeCatalogV101.newCustom(
        name.ifBlank { "我的主题" }, Color(page.toInt()), Color(text.toInt()), night, imagePath, id,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4)
            .padding(bottom = t.space4)
            .testTag("reader_theme_new_page"),
    ) {
        ReaderSubPageHeaderV101(title = if (night) "新建夜间主题" else "新建日间主题", onBack = onDone)
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(132.dp)) {
                ReaderThemeCardV101(theme = preview.toTheme(), selected = false, onClick = {})
            }
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    singleLine = true,
                    label = { Text("主题名称") },
                    modifier = Modifier.fillMaxWidth().testTag("reader_theme_name"),
                )
                Spacer(Modifier.height(t.space2))
                Text(
                    text = "正文示例：琅嬛福地，书香一卷。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(page.toInt()))
                        .padding(10.dp),
                    color = Color(text.toInt()),
                )
            }
        }
        Spacer(Modifier.height(t.space3))
        ReaderSwatchRowV101("背景颜色", if (night) READER_THEME_NIGHT_SWATCHES_V101 else READER_THEME_DAY_SWATCHES_V101, page) { page = it }
        Spacer(Modifier.height(t.space2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背景图片", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground, modifier = Modifier.weight(1f))
            if (imagePath != null) TextButton(onClick = { imagePath = null }) { Text("移除图片") }
            TextButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (imagePath == null) "从相册选择" else "更换图片") }
        }
        Spacer(Modifier.height(t.space2))
        ReaderSwatchRowV101("文字颜色", READER_THEME_TEXT_SWATCHES_V101, text) { text = it }
        error?.let {
            Text(it, color = t.destructive, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = t.space2))
        }
        Spacer(Modifier.height(t.space4))
        Text(
            text = "保存并使用",
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color(0xFF2F7BF6))
                .clickable {
                    if (settings.addCustomTheme(preview)) onDone()
                    else error = "自定义主题最多 ${ReaderThemeCatalogV101.MAX_CUSTOM} 个，请先删除一些"
                }
                .testTag("reader_theme_save")
                .padding(vertical = 12.dp),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ReaderSwatchRowV101(label: String, swatches: List<Long>, value: Long, onPick: (Long) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Text(label, style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        swatches.forEach { swatch ->
            val selected = swatch == value
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(swatch.toInt()))
                    .border(if (selected) 2.dp else 0.5.dp, if (selected) Color(0xFF2F7BF6) else t.border, CircleShape)
                    .clickable { onPick(swatch) }
                    .semantics { contentDescription = "$label ${"%08X".format(swatch)}"; this.selected = selected },
            )
        }
    }
}
