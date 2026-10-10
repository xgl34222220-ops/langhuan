package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.FlatListRowV93
import com.xiguli.langhuan.ui.design.FlatSectionLabelV93
import com.xiguli.langhuan.ui.design.FlatTextTabV93
import com.xiguli.langhuan.ui.design.FlatTopBarV93
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/** The form view of a draft: native JSON first, then a single Legado object converted on the fly. */
internal fun sourceDraftFormV94(draft: String): BookSourceV36? {
    val text = normalizeSourceTextV94(draft)
    if (text.isEmpty()) return null
    runCatching { BookSourceJsonV36.decodeFromString(BookSourceV36.serializer(), text) }.getOrNull()?.let { return it }
    return runCatching { parseBookSourcesV36(text).sources.singleOrNull() }.getOrNull()
}

internal fun encodeSourceDraftV94(source: BookSourceV36): String =
    BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), source)

/**
 * V94 书源编辑器（新建与编辑共用）：表单与 JSON 两种视图编辑同一份草稿（草稿保存在 ViewModel，
 * 旋转/重建后保留）。保存前完整校验，可在不保存的情况下「测试搜索」。
 */
@Composable
internal fun BookSourceEditorScreenV94(
    creating: Boolean,
    draft: String,
    error: String?,
    saving: Boolean,
    test: SourceTestStateV94?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    var jsonTab by rememberSaveable { mutableStateOf(false) }
    var keyword by rememberSaveable { mutableStateOf("") }
    val form = remember(draft) { sourceDraftFormV94(draft) }
    BackHandler(onBack = onCancel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        FlatTopBarV93(title = if (creating) "新建书源" else "编辑书源", onBack = onCancel)
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(t.space5),
        ) {
            FlatTextTabV93(label = "表单", selected = !jsonTab, onClick = { jsonTab = false })
            FlatTextTabV93(label = "JSON", selected = jsonTab, onClick = { jsonTab = true })
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (jsonTab) {
                Spacer(Modifier.height(t.space2))
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("书源 JSON") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).testTag("source-json-field"),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = t.foreground),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = t.input,
                        unfocusedContainerColor = t.input,
                        focusedIndicatorColor = t.primary,
                        unfocusedIndicatorColor = t.border,
                    ),
                )
                Spacer(Modifier.height(t.space2))
                Text(
                    "可直接粘贴阅读（Legado）的单个书源 JSON，保存时会转换为琅嬛格式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            } else if (form == null) {
                Spacer(Modifier.height(t.space5))
                Text(
                    "JSON 草稿有语法错误，表单暂时无法显示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.destructive,
                )
                Text(
                    "去 JSON 页修正",
                    modifier = Modifier.clickable { jsonTab = true }.padding(vertical = t.space2),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                )
            } else {
                SOURCE_FORM_GROUPS_V94.forEach { group ->
                    FlatSectionLabelV93(group.title)
                    group.fields.forEach { field ->
                        SourceFormFieldRowV94(
                            field = field,
                            value = field.read(form),
                            onValueChange = { onDraftChange(encodeSourceDraftV94(field.write(form, it))) },
                        )
                    }
                }
                val missing = sourceFormMissingV94(form)
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(t.space2))
                    Text(
                        "还需要填写：${missing.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }

            FlatSectionLabelV93("测试搜索")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourcePlainFieldV94(
                    value = keyword,
                    onValueChange = { keyword = it },
                    hint = "输入一本书名",
                    modifier = Modifier.weight(1f).testTag("source-test-keyword"),
                    description = "测试书名",
                )
                Spacer(Modifier.width(t.space3))
                Text(
                    text = if (test?.running == true) "搜索中…" else "测试搜索",
                    modifier = Modifier
                        .clickable(enabled = test?.running != true) { onTest(keyword) }
                        .padding(horizontal = t.space2, vertical = t.space3),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            test?.let { result ->
                Spacer(Modifier.height(t.space2))
                when {
                    result.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.primary)
                        Spacer(Modifier.width(t.space2))
                        Text("正在用「${result.keyword}」搜索…", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    result.error != null -> Text(result.error, style = MaterialTheme.typography.bodySmall, color = t.destructive)
                    else -> {
                        Text("找到 ${result.books.size} 本", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                        result.books.take(8).forEach { book ->
                            Text(
                                text = listOf(book.name, book.author).filter { it.isNotBlank() }.joinToString(" · "),
                                modifier = Modifier.padding(vertical = 3.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(t.space5))
        }
        error?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = t.space1),
                style = MaterialTheme.typography.bodySmall,
                color = t.destructive,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = t.space3),
            horizontalArrangement = Arrangement.spacedBy(t.space3),
        ) {
            SourceActionButtonV94(text = "取消", primary = false, modifier = Modifier.weight(1f), onClick = onCancel)
            SourceActionButtonV94(
                text = if (saving) "校验中…" else "保存规则",
                primary = true,
                enabled = !saving,
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun SourceFormFieldRowV94(field: SourceFormFieldV94, value: String, onValueChange: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = t.foreground.copy(alpha = 0.78f))
            if (field.required) Text(" *", style = MaterialTheme.typography.labelMedium, color = t.primary)
        }
        Spacer(Modifier.height(4.dp))
        SourcePlainFieldV94(
            value = value,
            onValueChange = onValueChange,
            hint = field.hint,
            modifier = Modifier.fillMaxWidth().testTag("source-field-${field.key}"),
            description = field.label,
        )
    }
}

@Composable
internal fun SourcePlainFieldV94(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    singleLine: Boolean = true,
    minHeight: Int = 44,
) {
    val t = LocalLanghuanUiTokens.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.foreground),
        cursorBrush = SolidColor(t.primary),
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .background(t.foreground.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground.copy(alpha = 0.7f), maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
internal fun SourceActionButtonV94(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                when {
                    !enabled -> t.foreground.copy(alpha = 0.06f)
                    primary -> t.primary
                    else -> t.foreground.copy(alpha = 0.05f)
                },
                RoundedCornerShape(23.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> t.mutedForeground
                primary -> t.primaryForeground
                else -> t.foreground
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * V94 导入书源：文件、网址（含 legado:// 分享链接）、粘贴 JSON 三种入口集中在一页。
 * 结果回到书源管理页，以内联卡片列出导入数量、跳过原因与提示。
 */
@Composable
internal fun BookSourceImportScreenV94(
    importing: Boolean,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportText: (String) -> Unit,
    onCreateFromJson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val clipboard = LocalClipboardManager.current
    var url by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        FlatTopBarV93(title = "导入书源", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            FlatSectionLabelV93("从文件")
            FlatListRowV93(
                title = "选择书源文件",
                subtitle = ".json / .txt，单个书源或书源数组",
                icon = Icons.Outlined.FileOpen,
                enabled = !importing,
                onClick = onPickFile,
            )
            FlatSectionLabelV93("从网址")
            SourcePlainFieldV94(
                value = url,
                onValueChange = { url = it; localError = null },
                hint = "https://…/sources.json 或 legado:// 分享链接",
                modifier = Modifier.fillMaxWidth().testTag("source-import-url"),
                description = "书源网址",
            )
            Spacer(Modifier.height(t.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp), tint = t.mutedForeground)
                Spacer(Modifier.width(t.space2))
                Text(
                    "下载并导入",
                    modifier = Modifier
                        .clickable(enabled = !importing) {
                            if (!sourceInputIsUrlV94(url)) localError = "请输入以 http(s):// 或 legado:// 开头的书源链接"
                            else onImportUrl(url.trim())
                        }
                        .padding(vertical = t.space2),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlatSectionLabelV93("粘贴 JSON")
            SourcePlainFieldV94(
                value = text,
                onValueChange = { text = it; localError = null },
                hint = "[{\"bookSourceName\": …}] 或 {…}",
                modifier = Modifier.fillMaxWidth().testTag("source-import-text"),
                description = "书源 JSON 文本",
                singleLine = false,
                minHeight = 160,
            )
            Spacer(Modifier.height(t.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp), tint = t.mutedForeground)
                Spacer(Modifier.width(t.space2))
                Text(
                    "从剪贴板粘贴",
                    modifier = Modifier
                        .clickable { clipboard.getText()?.text?.let { text = it; localError = null } }
                        .padding(vertical = t.space2),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "在编辑器中打开",
                    modifier = Modifier
                        .clickable(enabled = text.isNotBlank()) { onCreateFromJson(text) }
                        .padding(vertical = t.space2),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (text.isNotBlank()) t.primary else t.mutedForeground,
                )
            }
            localError?.let {
                Spacer(Modifier.height(t.space1))
                Text(it, style = MaterialTheme.typography.bodySmall, color = t.destructive)
            }
            Spacer(Modifier.height(t.space4))
            Text(
                "支持阅读（Legado）书源与琅嬛书源格式。静态网页规则可直接使用；必需规则依赖 JavaScript 或 JSON 接口的书源会被跳过并写明原因，封面、简介等可选规则会自动忽略。已存在的书源不会被覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            Spacer(Modifier.height(t.space5))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = t.space3),
        ) {
            SourceActionButtonV94(
                text = if (importing) "正在导入…" else "导入粘贴的书源",
                primary = true,
                enabled = !importing && text.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = {
                    if (sourceInputIsUrlV94(text)) onImportUrl(text.trim()) else onImportText(text)
                },
            )
        }
    }
}
