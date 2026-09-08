package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Dedicated project memory surface.
 *
 * Canon is the single source of truth. Candidate facts stay reviewable and never silently become
 * Canon. This page is intentionally separate from Agent chat so users can inspect what the model
 * is actually allowed to remember about the current novel.
 */
@Composable
fun ProjectMemoryPageV1(
    state: StudioUiState,
    vm: StudioViewModel,
    onClose: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val snapshot = state.snapshot
    val locked = snapshot.bible.filter { it.locked }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(Icons.Rounded.ArrowBack, "返回作品", onClose)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("项目记忆", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                    Text("Canon 单一事实源 · Candidate 审核区 · 长期状态", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge("${snapshot.bible.size} 条", accent = locked.isNotEmpty())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LanghuanCard(Modifier.fillMaxWidth(), depth = 2, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(t.radiusSm),
                                color = t.warmSurface,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Memory, null, Modifier.size(22.dp), tint = t.accent)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(snapshot.novel.title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
                                Text(
                                    "这里只展示已确认记忆与候选事实；AI 复盘不能绕过确认直接改 Canon。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            LanghuanBadge("Canon ${snapshot.bible.size}")
                            LanghuanBadge("锁定 ${locked.size}", accent = locked.isNotEmpty())
                            LanghuanBadge("角色 ${snapshot.characters.size}")
                            LanghuanBadge("伏笔 ${snapshot.relevantForeshadowing.size}")
                        }
                    }
                }

                item {
                    MemorySectionCardV1("已确认 Canon", Icons.Rounded.FactCheck) {
                        if (snapshot.bible.isEmpty()) {
                            Text("还没有已确认事实。Agent 提取的内容会先进入 Candidate。", color = t.mutedForeground)
                        }
                        snapshot.bible.sortedWith(compareByDescending<com.xiguli.langhuan.domain.BibleEntry> { it.locked }.thenBy { it.category.name }).take(36).forEach { fact ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        fact.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = t.foreground,
                                    )
                                    if (fact.locked) {
                                        Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = t.accent)
                                    }
                                }
                                Text(
                                    "${fact.category.name} · ${fact.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (snapshot.bible.size > 36) {
                            Text("还有 ${snapshot.bible.size - 36} 条 Canon 未展开。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }

                item {
                    MemorySectionCardV1("角色状态记忆", Icons.Rounded.Groups) {
                        if (snapshot.characters.isEmpty()) {
                            Text("还没有结构化角色状态。", color = t.mutedForeground)
                        }
                        snapshot.characters.take(24).forEach { character ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row {
                                    Text(character.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = t.foreground)
                                    Text("第${character.lastUpdatedChapter}章", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                }
                                Text(
                                    "${character.location} · ${character.emotionalState} · 目标：${character.goal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                                if (character.relationshipNotes.isNotEmpty()) {
                                    Text(
                                        character.relationshipNotes.entries.take(4).joinToString(" · ") { (name, relation) -> "$name：$relation" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = t.mutedForeground,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    MemorySectionCardV1("长期时间线", Icons.Rounded.Timeline) {
                        if (snapshot.recentTimeline.isEmpty()) {
                            Text("还没有结构化时间线。", color = t.mutedForeground)
                        }
                        snapshot.recentTimeline.sortedByDescending { it.chapter }.take(20).forEach { event ->
                            val storyTime = event.storyDay.takeIf { it > 0 }?.let {
                                "故事第${it}天 · ${event.timeOfDay.ifBlank { event.storyTime }}"
                            } ?: event.storyTime.ifBlank { "时间未标记" }
                            Text(
                                "第${event.chapter}章 · $storyTime · ${event.location}\n${event.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.foreground,
                            )
                        }
                    }
                }

                item {
                    MemorySectionCardV1("伏笔记忆", Icons.Rounded.Radar) {
                        if (snapshot.relevantForeshadowing.isEmpty()) {
                            Text("还没有结构化伏笔。", color = t.mutedForeground)
                        }
                        snapshot.relevantForeshadowing.take(24).forEach { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, color = t.foreground)
                                Text(
                                    "${item.status.name} · 计划 ${item.expectedChapterStart}-${item.expectedChapterEnd} 章",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp), tint = t.accent)
                            Spacer(Modifier.size(7.dp))
                            Text("Candidate 候选事实", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                        }
                        Text(
                            "候选事实必须通过本地证明或人工确认后才能进入 Canon。",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                item { CandidateCanonPanel(state, vm) }
            }
        }
    }
}

@Composable
private fun MemorySectionCardV1(
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
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.muted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(17.dp), tint = t.mutedForeground)
                    }
                }
                Spacer(Modifier.size(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
            }
            content()
        }
    }
}
