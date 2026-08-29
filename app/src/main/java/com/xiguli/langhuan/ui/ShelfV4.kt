package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ShelfV4(
    state: LibraryExperienceState,
    aiReady: Boolean,
    aiLabel: String,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onReference: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("长篇小说 AI 创作工作台", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onSkills) { Icon(Icons.Rounded.AutoStories, "写作 Skills") }
                IconButton(onRunCenter) { Icon(Icons.Rounded.TaskAlt, "后台任务") }
                IconButton(onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (aiReady) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            null,
                            tint = if (aiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(if (aiReady) aiLabel.ifBlank { "AI 已连接" } else "还没有可用 AI", fontWeight = FontWeight.Bold)
                            Text(
                                if (aiReady) "可以直接建书、规划和写作" else "先配置中转站或官方 API，再开始 AI 创作",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 新建小说")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onReference,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.LibraryBooks, null)
                            Spacer(Modifier.width(6.dp))
                            Text("参考资料")
                        }
                        OutlinedButton(
                            onClick = onSkills,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.AutoStories, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Skills")
                        }
                    }
                }
            }
        }

        item { Text("我的作品", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        if (state.stories.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MenuBook, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("还没有小说", fontWeight = FontWeight.Bold)
                        Text("从上面的 AI 新建小说开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.stories, key = { it.id }) { book ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(78.dp).height(112.dp))
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(book.genre, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(book.premise, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("第 ${book.currentChapter} 章 · ${book.currentWords} 字", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
