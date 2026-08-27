package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xiguli.langhuan.data.StoryFoundation

/**
 * Nullable overload used by Compose call sites backed by delegated State.
 * Kotlin cannot smart-cast a delegated nullable property across LazyColumn item lambdas.
 */
@Composable
internal fun FoundationCard(
    foundation: Any?,
    busy: Boolean,
    onRegenerate: () -> Unit,
    onCreate: () -> Unit,
) {
    val resolved = foundation as? StoryFoundation ?: return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("建书蓝图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(resolved.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${resolved.genre} · ${resolved.targetWords / 10_000} 万字", style = MaterialTheme.typography.labelLarge)

            NullableBlueprintSection("故事承诺", resolved.storyPromise)
            NullableBlueprintSection("叙事风格基线", resolved.styleGuide)
            NullableBlueprintSection(
                "总纲",
                "${resolved.masterObjective}\n核心冲突：${resolved.masterConflict}\n关键转折：${resolved.masterTurningPoint}",
            )

            Text("小说圣经 · ${resolved.bible.size} 条", fontWeight = FontWeight.Bold)
            resolved.bible.take(18).forEach { item ->
                Text("${item.category.name} · ${item.name}\n${item.content}", style = MaterialTheme.typography.bodyMedium)
            }

            Text("核心角色 · ${resolved.characters.size} 人", fontWeight = FontWeight.Bold)
            resolved.characters.forEachIndexed { index, character ->
                Text(
                    "${if (index == 0) "主角 · " else ""}${character.name}｜${character.personality.joinToString("、")}\n目标：${character.goal}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("分卷与前期路线 · ${resolved.volumes.size} 卷", fontWeight = FontWeight.Bold)
            resolved.volumes.forEach { volume ->
                Text("第${volume.order}卷 · ${volume.title}", fontWeight = FontWeight.SemiBold)
                Text(
                    "目标：${volume.objective}\n冲突：${volume.conflict}\n转折：${volume.turningPoint}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                volume.chapters.forEach { chapter ->
                    Text("${chapter.order}. ${chapter.title} — ${chapter.objective}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (resolved.foreshadowing.isNotEmpty()) {
                Text("伏笔计划 · ${resolved.foreshadowing.size} 条", fontWeight = FontWeight.Bold)
                resolved.foreshadowing.forEach { item ->
                    Text(
                        "${item.title}｜${item.expectedChapterStart}-${item.expectedChapterEnd}章\n${item.detail} → ${item.expectedPayoff}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                "这一步仍未写入书架。继续聊天会修改整套蓝图；确认后才写入小说圣经、角色状态、三级大纲和长期记忆。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("整套重做")
                }
                Button(
                    onClick = onCreate,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("正式建书")
                }
            }
        }
    }
}

@Composable
private fun NullableBlueprintSection(title: String, content: String) {
    if (content.isBlank()) return
    Text(title, fontWeight = FontWeight.Bold)
    Text(content, style = MaterialTheme.typography.bodyMedium)
}
