package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.ChapterDraft

/** Conservative append-only updates preserve old numbers, bookmarks, progress and cached text. */
internal fun onlineCatalogueAppendV46(existing: List<ChapterDraft>, fresh: List<OnlineChapterV36>): List<OnlineChapterV36> {
    require(existing.all { it.sourceUrl.isNotBlank() }) { "旧版离线书缺少逐章来源，需核对目录；已保留原正文与阅读进度" }
    require(fresh.size >= existing.size && existing.indices.all { existing[it].sourceUrl == fresh[it].url }) {
        "网站目录发生插章、删章或顺序变化，需核对；已保留原目录、正文与阅读进度"
    }
    return fresh.drop(existing.size)
}
