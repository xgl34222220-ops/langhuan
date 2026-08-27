package com.xiguli.langhuan.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.theme.BlueMist
import com.xiguli.langhuan.ui.theme.Glass
import com.xiguli.langhuan.ui.theme.Ink
import com.xiguli.langhuan.ui.theme.Iris
import com.xiguli.langhuan.ui.theme.Mist
import com.xiguli.langhuan.ui.theme.Success
import com.xiguli.langhuan.ui.theme.Violet
import com.xiguli.langhuan.ui.theme.Warning

private data class Destination(val label: String, val glyph: String)

@Composable
fun LanghuanApp(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }
    val destinations = remember {
        listOf(
            Destination("书架", "书"),
            Destination("创作", "写"),
            Destination("记忆", "忆"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Mist, BlueMist, Color(0xFFF8F2FC)),
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .clip(RoundedCornerShape(28.dp)),
                    containerColor = Glass,
                    tonalElevation = 6.dp,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected == index) Violet else Color.Transparent,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            destination.glyph,
                                            color = if (selected == index) Color.White else Ink,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(targetState = selected, label = "main_destination") { destination ->
                when (destination) {
                    0 -> LibraryScreen(state.snapshot, padding)
                    1 -> StudioScreen(state, padding, viewModel::generateChapter)
                    else -> MemoryScreen(state.snapshot, padding)
                }
            }
        }
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = { Text(if (result.canCommit) "一致性检查通过" else "发现设定冲突") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(result.chapter.summary)
                    if (result.issues.isEmpty()) {
                        StatusPill("0 个冲突 · 可以保存", Success)
                    } else {
                        result.issues.forEach { IssueRow(it) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::dismissResult) {
                    Text(if (result.canCommit) "进入正文" else "按建议重写")
                }
            },
        )
    }
}

@Composable
private fun LibraryScreen(snapshot: StorySnapshot, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("琅嬛", color = Violet, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("让长篇故事\n始终沿着主线生长", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("大纲、设定和长期记忆共同约束每一次生成", color = Ink.copy(alpha = .65f))
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.novel.title, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(snapshot.novel.genre, color = Violet, fontWeight = FontWeight.SemiBold)
                    }
                    StatusPill("写作中", Success)
                }
                Spacer(Modifier.height(18.dp))
                Text(snapshot.novel.premise, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                ProgressMetric(snapshot.novel.currentWords, snapshot.novel.targetWords)
            }
        }
        item {
            Text("故事健康度", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("锁定设定", snapshot.bible.count { it.locked }.toString(), "项", Modifier.weight(1f))
                MetricCard("活跃伏笔", snapshot.relevantForeshadowing.size.toString(), "条", Modifier.weight(1f))
                MetricCard("人物状态", snapshot.characters.size.toString(), "人", Modifier.weight(1f))
            }
        }
        item {
            GlassCard {
                Text("当前章节", style = MaterialTheme.typography.titleMedium, color = Violet)
                Spacer(Modifier.height(8.dp))
                Text("第 ${snapshot.novel.currentChapter} 章 · ${snapshot.activeOutline.last().title}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(snapshot.activeOutline.last().objective, color = Ink.copy(alpha = .68f))
            }
        }
    }
}

@Composable
private fun StudioScreen(
    state: StudioUiState,
    padding: PaddingValues,
    onGenerate: () -> Unit,
) {
    val snapshot = state.snapshot
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("创作台", style = MaterialTheme.typography.displaySmall)
            Text("先规划，后写作，再审查", color = Ink.copy(alpha = .6f))
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("第 ${snapshot.novel.currentChapter} 章", color = Violet, fontWeight = FontWeight.Bold)
                        Text(snapshot.activeOutline.last().title, style = MaterialTheme.typography.headlineMedium)
                    }
                    StatusPill("章纲已锁定", Success)
                }
                Spacer(Modifier.height(16.dp))
                Text("本章唯一目标", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Text(snapshot.activeOutline.last().objective, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Text("生成前上下文", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContextChip("三级大纲", "3/3")
                ContextChip("人物状态", "${snapshot.characters.size}")
                ContextChip("时间线", "${snapshot.recentTimeline.size}")
                ContextChip("伏笔", "${snapshot.relevantForeshadowing.size}")
            }
        }
        item {
            GlassCard {
                Text("AI 将遵守", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                RuleLine("锁定设定不可改写")
                RuleLine("人物知识、地点和伤势必须连续")
                RuleLine("本章必须完成章纲目标")
                RuleLine("写完自动执行一致性审查")
            }
        }
        item {
            Button(
                onClick = onGenerate,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("正在规划并检查上下文…")
                } else {
                    Text("生成本章正文", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            AnimatedVisibility(state.error != null) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MemoryScreen(snapshot: StorySnapshot, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("长期记忆", style = MaterialTheme.typography.displaySmall)
            Text("把剧情事实变成 AI 可检索的结构化状态", color = Ink.copy(alpha = .6f))
        }
        item { MemorySection("小说圣经", "锁定世界规则、人物底色和叙事边界", snapshot.bible.size) }
        item { MemorySection("人物状态", "位置、伤势、情绪、目标、秘密与物品", snapshot.characters.size) }
        item { MemorySection("时间线", "事件先后、地点、参与者和后果", snapshot.recentTimeline.size) }
        item { MemorySection("伏笔追踪", "埋设、发展、回收时机和状态", snapshot.relevantForeshadowing.size) }
        item {
            Text("锁定设定", style = MaterialTheme.typography.titleLarge)
        }
        items(snapshot.bible) { entry -> BibleRow(entry) }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Glass),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Glass),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .58f))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium)
                Text(unit, modifier = Modifier.padding(bottom = 2.dp, start = 2.dp), color = Ink.copy(alpha = .55f))
            }
        }
    }
}

@Composable
private fun ProgressMetric(current: Int, target: Int) {
    val progress = current.toFloat() / target.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${current / 1000.0}k 字", fontWeight = FontWeight.SemiBold)
                Text("目标 ${target / 10_000} 万", color = Ink.copy(alpha = .55f))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Iris.copy(alpha = .23f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).background(Violet))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun ContextChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Glass) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Violet, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun RuleLine(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Success))
        Text(text, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MemorySection(title: String, description: String, count: Int) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = Iris.copy(alpha = .16f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(count.toString(), color = Violet, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, color = Ink.copy(alpha = .58f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BibleRow(entry: BibleEntry) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(if (entry.locked) "已锁定" else "可编辑", if (entry.locked) Success else Warning)
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.content, color = Ink.copy(alpha = .68f))
    }
}

@Composable
private fun IssueRow(issue: ConsistencyIssue) {
    val color = when (issue.severity) {
        IssueSeverity.BLOCKING -> MaterialTheme.colorScheme.error
        IssueSeverity.WARNING -> Warning
        IssueSeverity.INFO -> Violet
    }
    Column {
        StatusPill(issue.severity.name, color)
        Spacer(Modifier.height(4.dp))
        Text(issue.message, fontWeight = FontWeight.SemiBold)
        Text(issue.repairInstruction, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = .65f))
        HorizontalDivider(Modifier.padding(top = 8.dp), color = Ink.copy(alpha = .08f))
    }
}
