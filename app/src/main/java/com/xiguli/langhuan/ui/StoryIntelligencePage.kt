package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.OutlineLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryIntelligencePage(state: StudioUiState, onClose: () -> Unit) {
    val snapshot = state.snapshot
    val chapter = snapshot.novel.currentChapter
    val outline = if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline
    val chapters = outline.filter { it.level == OutlineLevel.CHAPTER }
    val written = state.chapters.count { it.words > 0 }
    val unresolved = snapshot.relevantForeshadowing.filter { it.status != ForeshadowStatus.RESOLVED && it.status != ForeshadowStatus.ABANDONED }
    val overdue = unresolved.filter { chapter > it.expectedChapterEnd }
    val dueSoon = unresolved.filter { chapter in (it.expectedChapterStart - 2).coerceAtLeast(1)..it.expectedChapterEnd }
    val staleCharacters = snapshot.characters.filter { chapter - it.lastUpdatedChapter >= 5 }
    val hardRules = snapshot.bible.count { it.locked }
    val score = (100 - overdue.size * 12 - staleCharacters.size * 4 - if (snapshot.characters.isEmpty()) 20 else 0 - if (hardRules < 4) 8 else 0).coerceIn(0, 100)
    val activeVolume = snapshot.activeOutline.firstOrNull { it.level == OutlineLevel.VOLUME }
    val volumeChapters = activeVolume?.let { volume -> chapters.filter { it.parentId == volume.id } }.orEmpty()
    val volumeProgress = if (volumeChapters.isEmpty()) 0f else (volumeChapters.count { it.order <= chapter }.toFloat() / volumeChapters.size).coerceIn(0f, 1f)
    val totalProgress = (snapshot.novel.currentWords.toFloat() / snapshot.novel.targetWords.coerceAtLeast(1)).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("长篇监控", fontWeight = FontWeight.SemiBold); Text("角色弧线 · 节奏 · 伏笔 · 卷进度", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Insights, null, Modifier.size(30.dp)); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(snapshot.novel.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("第 $chapter 章 · ${snapshot.novel.currentWords} 字") }
                            Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = totalProgress, modifier = Modifier.fillMaxWidth())
                        Text("总进度 ${(totalProgress * 100).toInt()}% · 已写 $written/${chapters.size.coerceAtLeast(state.chapters.size)} 章 · 锁定设定 $hardRules 条", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { MonitorCard("当前卷进度", Icons.Rounded.MenuBook) {
                Text(activeVolume?.title ?: "未识别当前卷", fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = volumeProgress, modifier = Modifier.fillMaxWidth())
                Text(if (volumeChapters.isEmpty()) "当前卷还没有章纲" else "蓝图章纲 ${volumeChapters.size} 章 · 当前推进至第 $chapter 章", style = MaterialTheme.typography.bodySmall)
            } }
            item { MonitorCard("伏笔雷达", Icons.Rounded.Radar) {
                Text("未回收 ${unresolved.size} · 临近 ${dueSoon.size} · 逾期 ${overdue.size}", fontWeight = FontWeight.Bold)
                overdue.take(6).forEach { Text("⚠ ${it.title}：原计划 ${it.expectedChapterStart}-${it.expectedChapterEnd} 章回收", color = MaterialTheme.colorScheme.error) }
                dueSoon.filterNot { it in overdue }.take(6).forEach { Text("• ${it.title}：${it.expectedChapterStart}-${it.expectedChapterEnd} 章窗口") }
                if (unresolved.isEmpty()) Text("目前没有未回收伏笔。", style = MaterialTheme.typography.bodySmall)
            } }
            item { MonitorCard("角色状态与弧线", Icons.Rounded.Groups) {
                snapshot.characters.take(12).forEach { character ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row { Text(character.name, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("更新于第${character.lastUpdatedChapter}章", style = MaterialTheme.typography.labelSmall) }
                        Text("${character.location} · ${character.emotionalState} · 目标：${character.goal}", style = MaterialTheme.typography.bodySmall)
                        if (character in staleCharacters) Text("状态已连续 ${chapter - character.lastUpdatedChapter} 章未更新，注意是否被剧情遗忘。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } }
            item { MonitorCard("最近时间线", Icons.Rounded.Timeline) {
                snapshot.recentTimeline.sortedByDescending { it.chapter }.take(8).forEach { event ->
                    Text("第${event.chapter}章 · ${event.storyTime.ifBlank { "时间未标记" }} · ${event.location}\n${event.summary}", style = MaterialTheme.typography.bodySmall)
                }
                if (snapshot.recentTimeline.isEmpty()) Text("还没有结构化时间线。章节复盘后会逐步积累。")
            } }
            item { MonitorCard("稳定性提示", Icons.Rounded.HealthAndSafety) {
                when {
                    score >= 90 -> Text("结构状态健康。继续按章纲推进，并在章后复盘事实变化。")
                    score >= 70 -> Text("存在少量需要关注的长期状态，优先处理临近伏笔和长期未更新角色。")
                    else -> Text("长期一致性风险较高。建议先处理逾期伏笔、角色状态断档，再继续大段生成。", color = MaterialTheme.colorScheme.error)
                }
            } }
        }
    }
}

@Composable
private fun MonitorCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}
