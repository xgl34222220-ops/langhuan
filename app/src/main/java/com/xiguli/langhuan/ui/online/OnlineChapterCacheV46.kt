package com.xiguli.langhuan.ui

import android.content.Context
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document

/** Reading and explicit offline downloads share a cache lock, so one chapter is fetched once. */
internal object OnlineChapterCacheV46 {
    private val locks = List(32) { Mutex() }

    suspend fun load(
        context: Context, novelId: String, number: Int, tocUrls: Set<String>,
        fetch: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
    ): ChapterDraft = locks[("$novelId:$number".hashCode() and Int.MAX_VALUE) % locks.size].withLock {
        val projects = StoryProjectManager(context)
        val novel = projects.loadStory(novelId)?.snapshot?.novel ?: error("书籍已移除，已停止读取")
        val chapter = projects.chapterDraft(novelId, number) ?: error("章节已移除")
        if (chapter.content.isNotBlank()) return@withLock chapter
        check(novel.sourceId.isNotBlank() && chapter.sourceUrl.isNotBlank()) { "本章没有可用的在线来源" }
        val source = BookSourceStoreV36.load(context).firstOrNull { it.id == novel.sourceId }
            ?: error("原书源不可用；已有正文仍保留，请先恢复书源")
        val body = runInterruptible(Dispatchers.IO) {
            loadChapterTextV36(source, OnlineChapterV36(chapter.title, chapter.sourceUrl), tocUrls, fetch)
        }
        currentCoroutineContext().ensureActive()
        check(BookSourceStoreV36.load(context).firstOrNull { it.id == novel.sourceId } == source) { "书源已变化，未保存本次响应，请重试" }
        projects.cacheOnlineChapter(novelId, number, chapter.id, chapter.sourceUrl, body, novel.sourceId, novel.sourceBookUrl)
            ?: error("书籍已移除，未保存本次响应")
    }
}
