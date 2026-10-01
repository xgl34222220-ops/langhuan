package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.ChapterDraft

/** Missing middle chapters may be inserted without renumbering any existing persisted key. */
internal data class OnlineCataloguePlanV53(val items: List<ImportedChapter>, val existingByUrl: Map<String, ChapterDraft>) {
    val added: List<ImportedChapter> get() = items.filter { it.sourceUrl !in existingByUrl }
}

internal fun planOnlineCatalogueV53(existing: List<ChapterDraft>, items: List<ImportedChapter>): OnlineCataloguePlanV53 {
    require(items.isNotEmpty() && items.size <= 50_000 && items.all { it.sourceUrl.isNotBlank() } &&
        items.map { it.sourceUrl }.distinct().size == items.size) { "新目录包含空地址、重复章节或超出限制，未更新原目录" }
    require(existing.all { it.sourceUrl.isNotBlank() } && existing.map { it.sourceUrl }.distinct().size == existing.size) {
        "旧版目录缺少唯一逐章来源，需核对目录；已保留原正文、书签与阅读进度"
    }
    val previous = existing.sortedBy { it.readingOrder }
    val identities = previous.map { it.sourceUrl }.toSet()
    require(items.filter { it.sourceUrl in identities }.map { it.sourceUrl } == previous.map { it.sourceUrl }) {
        "新目录删除或重排了已有章节，需核对；已保留原目录、正文、书签与阅读进度"
    }
    // Compare the full URL. Removing query values could join different chapters or editions.
    return OnlineCataloguePlanV53(items, previous.associateBy { it.sourceUrl })
}
