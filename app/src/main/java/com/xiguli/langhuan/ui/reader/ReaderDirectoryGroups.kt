package com.xiguli.langhuan.ui.reader

import com.xiguli.langhuan.domain.ChapterDraft

internal data class ReaderDirectoryGroupV9(val title: String, val chapters: List<ChapterDraft>)

internal fun buildDirectoryGroupsV9(chapters: List<ChapterDraft>, query: String = ""): List<ReaderDirectoryGroupV9> {
    val key = query.trim()
    return chapters.sortedBy { it.chapterNumber }
        .filter { key.isBlank() || it.title.contains(key, true) }
        .groupBy { directoryVolumeLabelV9(it.title) ?: "正文" }
        .map { ReaderDirectoryGroupV9(it.key, it.value) }
}

internal fun directoryVolumeLabelV9(title: String): String? = listOf(
    Regex("""^(第[零〇一二三四五六七八九十百千万两0-9]+[卷部篇])"""),
    Regex("""^([卷部篇][零〇一二三四五六七八九十百千万两0-9]+)"""),
).firstNotNullOfOrNull { it.find(title.trim())?.groupValues?.getOrNull(1) }
