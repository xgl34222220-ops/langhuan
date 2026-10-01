package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.UUID

/** Pure preparation: invalid backup contents are rejected before a database is touched. */
internal data class ProjectRestorePlan(
    val snapshot: StorySnapshot,
    val chapters: List<ChapterDraft>,
    val currentDraft: ChapterDraft,
)

internal fun prepareProjectRestore(
    backup: StoryProjectBackup,
    checkCancelled: () -> Unit = {},
): ProjectRestorePlan {
    require(backup.formatVersion in 1..StoryProjectBackup.CURRENT_VERSION) { "备份版本不受支持，当前琅嬛暂不能恢复" }
    require(backup.chapters.isNotEmpty()) { "项目备份中没有章节" }
    val numbers = HashSet<Int>()
    val readingOrders = HashSet<Int>()
    backup.chapters.forEach { draft ->
        checkCancelled()
        require(draft.chapterNumber > 0) { "备份包含无效章节编号，未恢复" }
        require(numbers.add(draft.chapterNumber)) { "备份包含重复章节编号，未恢复" }
        require(draft.readingOrder > 0 && readingOrders.add(draft.readingOrder)) { "备份阅读顺序无效或重复，未恢复" }
    }
    checkCancelled()
    val totalWords = backup.chapters.sumOf { checkCancelled(); it.content.length.toLong() }
    require(totalWords <= Int.MAX_VALUE) { "项目正文过大，无法恢复" }
    val old = backup.snapshot
    val newNovelId = UUID.randomUUID().toString()
    val sourceOutline = validatedProjectOutline(backup, checkCancelled)
    val outlineIdMap = sourceOutline.associate { it.id to UUID.randomUUID().toString() }
    val newOutline = sourceOutline.map { node ->
        checkCancelled()
        node.copy(
            id = outlineIdMap.getValue(node.id),
            novelId = newNovelId,
            parentId = node.parentId?.let(outlineIdMap::get),
        )
    }

    val characterIdMap = old.characters.associate { it.id to UUID.randomUUID().toString() }
    val foreshadowIdMap = old.relevantForeshadowing.associate { it.id to UUID.randomUUID().toString() }
    val chapterNumber = old.novel.currentChapter.takeIf { it in numbers }
        ?: backup.chapters.minBy { it.readingOrder }.chapterNumber
    val remappedSnapshot = old.copy(
        novel = old.novel.copy(
            id = newNovelId,
            currentChapter = chapterNumber,
            currentWords = totalWords.toInt(),
        ),
        activeOutline = activeChain(newOutline, chapterNumber),
        outline = newOutline,
        bible = old.bible.map { it.copy(id = UUID.randomUUID().toString(), novelId = newNovelId) },
        characters = old.characters.map { character ->
            character.copy(id = characterIdMap.getValue(character.id), novelId = newNovelId)
        },
        recentTimeline = old.recentTimeline.map { it.copy(id = UUID.randomUUID().toString(), novelId = newNovelId) },
        relevantForeshadowing = old.relevantForeshadowing.map { item ->
            item.copy(id = foreshadowIdMap.getValue(item.id), novelId = newNovelId)
        },
        factHistory = old.factHistory.map { fact ->
            fact.copy(id = UUID.randomUUID().toString(), novelId = newNovelId)
        },
        candidateFacts = old.candidateFacts.map { fact ->
            fact.copy(id = UUID.randomUUID().toString(), novelId = newNovelId)
        },
        longForm = old.longForm.copy(
            autonomousPlan = old.longForm.autonomousPlan.copy(
                foreshadowCadence = old.longForm.autonomousPlan.foreshadowCadence.map { item ->
                    item.copy(foreshadowId = foreshadowIdMap[item.foreshadowId] ?: item.foreshadowId)
                },
            ),
            arcs = old.longForm.arcs.map { arc ->
                arc.copy(id = "$newNovelId:arc:${arc.startChapter}")
            },
            characterGrowth = old.longForm.characterGrowth.mapNotNull { growth ->
                val mapped = characterIdMap[growth.characterId]
                    ?: old.characters.firstOrNull { it.name == growth.name }?.id?.let(characterIdMap::get)
                mapped?.let { growth.copy(characterId = it) }
            },
        ),
    )

    val restoredDrafts = backup.chapters.sortedBy { it.readingOrder }.map { draft ->
        checkCancelled()
        draft.copy(
            id = "draft-$newNovelId-${draft.chapterNumber}-${UUID.randomUUID()}",
            novelId = newNovelId,
            version = draft.version.coerceAtLeast(1),
        )
    }
    val currentDraft = restoredDrafts.firstOrNull { it.chapterNumber == chapterNumber } ?: restoredDrafts.first()
    checkCancelled()
    return ProjectRestorePlan(remappedSnapshot, restoredDrafts, currentDraft)
}

private fun validatedProjectOutline(backup: StoryProjectBackup, checkCancelled: () -> Unit): List<OutlineNode> {
    val snapshot = backup.snapshot
    val source = if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline
    val nodes = linkedMapOf<String, OutlineNode>()
    source.forEach { node ->
        checkCancelled()
        require(node.id.isNotBlank()) { "备份大纲缺少标识，未恢复" }
        val previous = nodes.putIfAbsent(node.id, node)
        // Identical repeated nodes in older snapshots remain compatible; conflicting ones do not.
        require(previous == null || previous == node) { "备份大纲标识冲突，未恢复" }
    }
    val completed = HashSet<String>()
    nodes.values.forEach { node ->
        val chain = HashSet<String>()
        var current: OutlineNode? = node
        while (current != null && current.id !in completed) {
            checkCancelled()
            require(chain.add(current.id)) { "备份大纲包含循环引用，未恢复" }
            current = current.parentId?.let { parent ->
                requireNotNull(nodes[parent]) { "备份大纲引用的上级节点不存在，未恢复" }
            }
        }
        completed += chain
    }
    // Outline order is scoped by level/volume; it is not the chapter-state primary identity.
    return nodes.values.toList()
}

private fun activeChain(nodes: List<OutlineNode>, chapterNumber: Int): List<OutlineNode> {
        val chapter = nodes.firstOrNull { it.level == OutlineLevel.CHAPTER && it.order == chapterNumber }
            ?: nodes.filter { it.level == OutlineLevel.CHAPTER }.minByOrNull { it.order }
            ?: return emptyList()
        val volume = nodes.firstOrNull { it.id == chapter.parentId }
        val master = volume?.parentId?.let { id -> nodes.firstOrNull { it.id == id } }
            ?: nodes.firstOrNull { it.level == OutlineLevel.MASTER }
        return listOfNotNull(master, volume, chapter)
    }
