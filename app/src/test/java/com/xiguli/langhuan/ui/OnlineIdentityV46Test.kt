package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.Novel
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OnlineIdentityV46Test {
    private fun chapter(number: Int, url: String = "https://example.org/read/7/$number.html") =
        ChapterDraft("chapter-$number", "book", number, "第${number}章", "", emptyList(), sourceUrl = url)

    @Test fun oldJsonWithoutSourceFieldsRemainsLocal() {
        val json = """{"id":"local","novelId":"book","chapterNumber":1,"title":"空章","objective":"","scenePlan":[]}"""
        assertEquals("", Json.decodeFromString(ChapterDraft.serializer(), json).sourceUrl)
        val book = """{"id":"book","title":"本地书","genre":"","premise":"","theme":"","targetWords":100}"""
        val novel = Json.decodeFromString(Novel.serializer(), book)
        assertEquals("", novel.sourceId)
        assertEquals("", novel.sourceBookUrl)
    }

    @Test fun sourceIdentityAndCachedBodyRoundTripTogether() {
        val original = chapter(1).copy(content = "已经保存的正文")
        assertEquals(original, Json.decodeFromString(ChapterDraft.serializer(), Json.encodeToString(ChapterDraft.serializer(), original)))
    }

    @Test fun appendMatchesFullUrlAndPreservesQuerySemantics() {
        val old = listOf(chapter(1, "https://example.org/read?id=1&part=a"))
        val fresh = listOf(OnlineChapterV36("第一章", old.single().sourceUrl), OnlineChapterV36("第二章", "https://example.org/read?id=2"))
        assertEquals(listOf(fresh.last()), onlineCatalogueAppendV46(old, fresh))
        assertTrue(runCatching { onlineCatalogueAppendV46(old, fresh.map { it.copy(url = it.url.replace("part=a", "part=b")) }) }.isFailure)
    }

    @Test fun legacyThirtySixChapterBookCannotUseCountBasedAppend() {
        val legacy = (1..36).map { chapter(it, "").copy(content = "已下载正文") }
        val fresh = (1..1035).map { OnlineChapterV36("第${it}章", "https://example.org/read/7/$it.html") }
        assertTrue(runCatching { onlineCatalogueAppendV46(legacy, fresh) }.isFailure)
        assertTrue(legacy.all { it.content == "已下载正文" })
    }

    @Test fun insertedChapterCannotReassignCachedChapterNumbers() {
        val old = listOf(chapter(1), chapter(2))
        val changed = listOf(OnlineChapterV36("插章", "https://example.org/read/7/new.html")) + old.map { OnlineChapterV36(it.title, it.sourceUrl) }
        assertTrue(runCatching { onlineCatalogueAppendV46(old, changed) }.isFailure)
    }
}
