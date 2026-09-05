package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.engine.ChronologyRepairRisk
import com.xiguli.langhuan.ui.canon.WholeBookChronologyViewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

@Composable
fun StoryIntelligencePage(state: StudioUiState, onClose: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
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

    val chronologyVm: WholeBookChronologyViewModel = viewModel()
    val chronologyState = chronologyVm.state.collectAsStateWithLifecycle().value
    LaunchedEffect(snapshot.novel.id) { chronologyVm.scan(snapshot.novel.id) }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onClose)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("长篇监控", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                    Text("角色弧线 · 节奏 · 伏笔 · 全书时间轴", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge("健康 $score", accent = score >= 90)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = LanghuanShape.chip,
                                    color = t.warmSurface,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Insights, null, Modifier.size(21.dp), tint = t.accent)
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                    Text(snapshot.novel.title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
                                    Text("第 $chapter 章 · ${snapshot.novel.currentWords} 字", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                }
                                Text("$score", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                            }
                            LinearProgressIndicator(
                                progress = { totalProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = t.accent,
                                trackColor = t.muted,
                            )
                            Text(
                                "总进度 ${(totalProgress * 100).toInt()}% · 已写 $written/${chapters.size.coerceAtLeast(state.chapters.size)} 章 · 锁定设定 $hardRules 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                        }
                    }
                }

                item {
                    MonitorCard("全书时间线重建", Icons.Rounded.Schedule) {
                        when {
                            chronologyState.isScanning -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.accent, trackColor = t.muted)
                                Text("正在逐章读取正文、场景时间锁和长期时间线……", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            chronologyState.error != null -> Text(chronologyState.error, color = t.destructive, style = MaterialTheme.typography.bodySmall)
                            chronologyState.report == null -> {
                                Button(
                                    onClick = { chronologyVm.scan(snapshot.novel.id, force = true) },
                                    shape = LanghuanShape.chip,
                                    colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                                ) { Text("扫描全书时间线") }
                            }
                            else -> {
                                val report = chronologyState.report
                                Text(
                                    "候选故事日 1-${report.maxStoryDay.coerceAtLeast(1)} · 高风险 ${report.highCount} · 中风险 ${report.mediumCount} · 旧章推定 ${report.inferredCount}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = t.foreground,
                                )
                                Text(
                                    "“候选推定”只用于发现跨章矛盾，不会自动写进长期记忆。已有时间锁的章节优先使用真实 storyDay。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    report.chapters.take(30).forEach { item ->
                                        val label = if (item.startDay == item.endDay) "第${item.startDay}天" else "第${item.startDay}-${item.endDay}天"
                                        Surface(
                                            shape = LanghuanShape.chip,
                                            color = if (item.risk == ChronologyRepairRisk.HIGH) t.destructive.copy(alpha = .07f) else t.muted,
                                            border = BorderStroke(1.dp, if (item.risk == ChronologyRepairRisk.HIGH) t.destructive.copy(alpha = .18f) else t.border),
                                        ) {
                                            Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (item.risk == ChronologyRepairRisk.HIGH) {
                                                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(14.dp), tint = t.destructive)
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text("${item.chapterNumber}章 · $label", style = MaterialTheme.typography.labelSmall, color = t.foreground)
                                            }
                                        }
                                    }
                                }
                                report.conflicts.take(10).forEach { conflict ->
                                    val danger = conflict.risk == ChronologyRepairRisk.HIGH
                                    Surface(
                                        shape = LanghuanShape.chip,
                                        color = if (danger) t.destructive.copy(alpha = .06f) else t.muted,
                                        border = BorderStroke(1.dp, if (danger) t.destructive.copy(alpha = .16f) else t.border),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(
                                                "${conflict.risk.label}风险 · 第${conflict.chapterNumber}章 · ${conflict.code}",
                                                color = if (danger) t.destructive else t.foreground,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(conflict.message, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                        }
                                    }
                                }
                                if (report.conflicts.size > 10) {
                                    Text(
                                        "还有 ${report.conflicts.size - 10} 个时间问题未展开。可进入对应章节使用“时间线体检 / 旧稿修复”。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = t.mutedForeground,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { chronologyVm.scan(snapshot.novel.id, force = true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = LanghuanShape.chip,
                                    border = BorderStroke(1.dp, t.border),
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("重新扫描全书")
                                }
                            }
                        }
                    }
                }

                item {
                    MonitorCard("当前卷进度", Icons.Rounded.MenuBook) {
                        Text(activeVolume?.title ?: "未识别当前卷", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                        LinearProgressIndicator(progress = { volumeProgress }, modifier = Modifier.fillMaxWidth(), color = t.accent, trackColor = t.muted)
                        Text(
                            if (volumeChapters.isEmpty()) "当前卷还没有章纲" else "蓝图章纲 ${volumeChapters.size} 章 · 当前推进至第 $chapter 章",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                item {
                    MonitorCard("伏笔雷达", Icons.Rounded.Radar) {
                        Text("未回收 ${unresolved.size} · 临近 ${dueSoon.size} · 逾期 ${overdue.size}", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                        overdue.take(6).forEach {
                            Text("⚠ ${it.title}：原计划 ${it.expectedChapterStart}-${it.expectedChapterEnd} 章回收", color = t.destructive, style = MaterialTheme.typography.bodySmall)
                        }
                        dueSoon.filterNot { it in overdue }.take(6).forEach {
                            Text("• ${it.title}：${it.expectedChapterStart}-${it.expectedChapterEnd} 章窗口", style = MaterialTheme.typography.bodySmall, color = t.foreground)
                        }
                        if (unresolved.isEmpty()) Text("目前没有未回收伏笔。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }

                item {
                    MonitorCard("角色状态与弧线", Icons.Rounded.Groups) {
                        snapshot.characters.take(12).forEach { character ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row {
                                    Text(character.name, style = MaterialTheme.typography.titleSmall, color = t.foreground)
                                    Spacer(Modifier.weight(1f))
                                    Text("更新于第${character.lastUpdatedChapter}章", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                }
                                Text(
                                    "${character.location} · ${character.emotionalState} · 目标：${character.goal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                                if (character in staleCharacters) {
                                    Text(
                                        "状态已连续 ${chapter - character.lastUpdatedChapter} 章未更新，注意是否被剧情遗忘。",
                                        color = t.destructive,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    MonitorCard("最近时间线", Icons.Rounded.Timeline) {
                        snapshot.recentTimeline.sortedByDescending { it.chapter }.take(8).forEach { event ->
                            val structured = event.storyDay.takeIf { it > 0 }?.let { "故事第${it}天 · ${event.timeOfDay.ifBlank { event.storyTime }}" }
                                ?: event.storyTime.ifBlank { "时间未标记" }
                            Text(
                                "第${event.chapter}章 · $structured · ${event.location}\n${event.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.foreground,
                            )
                        }
                        if (snapshot.recentTimeline.isEmpty()) Text("还没有结构化时间线。章节复盘后会逐步积累。", color = t.mutedForeground)
                    }
                }

                item {
                    MonitorCard("稳定性提示", Icons.Rounded.HealthAndSafety) {
                        when {
                            score >= 90 -> Text("结构状态健康。继续按章纲推进，并在章后复盘事实变化。", color = t.foreground)
                            score >= 70 -> Text("存在少量需要关注的长期状态，优先处理临近伏笔和长期未更新角色。", color = t.foreground)
                            else -> Text("长期一致性风险较高。建议先处理逾期伏笔、角色状态断档，再继续大段生成。", color = t.destructive)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = LanghuanShape.chip,
                    color = t.muted,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = t.mutedForeground) }
                }
                Spacer(Modifier.width(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
            }
            content()
        }
    }
}
