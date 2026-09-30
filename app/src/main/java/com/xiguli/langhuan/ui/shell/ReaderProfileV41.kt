package com.xiguli.langhuan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/** The reading-first profile. Existing creative data and routes remain available on demand. */
@Composable
internal fun ReaderProfileV41(
    nickname: String,
    bookCount: Int,
    onEditProfile: () -> Unit,
    onHistory: () -> Unit,
    onShelfManager: () -> Unit,
    onSources: () -> Unit,
    onAiSetup: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    onBlankBook: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text("我的", color = t.foreground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("把时间留给阅读", Modifier.padding(top = 5.dp, bottom = 24.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
        LanghuanCard(Modifier.fillMaxWidth(), depth = 1, onClick = onEditProfile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(54.dp), shape = MaterialTheme.shapes.large, color = t.accent) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoStories, null, Modifier.size(25.dp), tint = t.accentForeground) }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(nickname, color = t.foreground, style = MaterialTheme.typography.titleLarge)
                    Text("$bookCount 本书，等你慢慢读", Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.ChevronRight, "编辑资料", tint = t.mutedForeground)
            }
        }
        Text("阅读工具", Modifier.padding(top = 26.dp, bottom = 10.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelLarge)
        LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
            Column {
                LanghuanMenuRow(Icons.Rounded.History, "阅读记录", onHistory, subtitle = "接着上次的故事读下去")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.Book, "书架管理", onShelfManager, subtitle = "分组整理，随手可读")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入本地书籍", onImport, subtitle = "TXT · EPUB · Markdown")
            }
        }
        Text("书源与 AI", Modifier.padding(top = 26.dp, bottom = 10.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelLarge)
        LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
            Column {
                LanghuanMenuRow(Icons.Rounded.Public, "书源管理", onSources, subtitle = "导入、启用与 AI 生成书源")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.AutoAwesome, "AI 服务与模型", onAiSetup, subtitle = "使用已有配置，为书源生成提供能力")
            }
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(top = 18.dp)) {
            Text(if (advanced) "收起其他工具" else "其他工具")
            Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 5.dp).size(18.dp))
        }
        AnimatedVisibility(advanced) {
            LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
                Column {
                    LanghuanMenuRow(Icons.Rounded.Edit, "空白新书", onBlankBook, subtitle = "保留原有手写创作能力")
                    LanghuanMenuRow(Icons.Rounded.AutoAwesome, "AI 创作", onCreate, subtitle = "继续使用原有创作工作流")
                    LanghuanMenuRow(Icons.Rounded.TaskAlt, "运行与任务", onRunCenter)
                    LanghuanMenuRow(Icons.Rounded.Tune, "创作技能", onSkills)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
