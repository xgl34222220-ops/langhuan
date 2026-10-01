package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.ChapterDraft
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OnlineCatalogueRepairV53Test {
    private fun item(n: Int) = ImportedChapter("第${n}章", "", "https://books.example/txt/7/$n.html")
    private fun draft(key: Int, n: Int) = ChapterDraft("old-$key", "book", key, "第${n}章", "", emptyList(), content = "缓存$n", sourceUrl = item(n).sourceUrl)
    @Test fun existingThirtySixHeadAndTailRowsHaveStableKeysWhenMiddleIsFilled() {
        val numbers = (1..15).toList() + (985..1005).toList()
        val old = numbers.mapIndexed { index, n -> draft(index + 1, n) }
        val plan = planOnlineCatalogueV53(old, (1..1005).map(::item))
        assertEquals(969, plan.added.size)
        assertSame(old[15], plan.existingByUrl.getValue(item(985).sourceUrl))
        assertEquals(16, plan.existingByUrl.getValue(item(985).sourceUrl).chapterNumber)
        assertEquals("缓存985", old[15].content)
    }
    @Test fun deletionReorderingDuplicatesAndUnknownLegacyIdentitiesFailBeforeWrites() {
        val old = listOf(draft(1, 1), draft(2, 3))
        listOf(listOf(item(1)), listOf(item(3),item(1)),listOf(item(1),item(3),item(3)),listOf(item(1),item(3),ImportedChapter("bad",""))).forEach {
            assertTrue(runCatching { planOnlineCatalogueV53(old, it) }.isFailure)
        }
        assertTrue(runCatching { planOnlineCatalogueV53(old.map { it.copy(sourceUrl = "") }, (1..3).map(::item)) }.isFailure)
    }
    @Test fun querySemanticsCannotReassignExistingChapter() {
        val old = listOf(draft(1,1).copy(sourceUrl = "https://books.example/read?id=1&edition=a"))
        assertTrue(runCatching { planOnlineCatalogueV53(old, listOf(ImportedChapter("one", "", "https://books.example/read?id=1&edition=b"))) }.isFailure)
    }
    @Test fun readingOrderIsSeparateFromStableStorageKeyAndRoundTrips() {
        val changed = draft(16,985).copy(readingOrder = 985)
        val restored = Json.decodeFromString(ChapterDraft.serializer(), Json.encodeToString(ChapterDraft.serializer(),changed))
        assertEquals(changed,restored)
        val old = """{"id":"a","novelId":"b","chapterNumber":16,"title":"old","objective":"","scenePlan":[]}"""
        assertEquals(16,Json.decodeFromString(ChapterDraft.serializer(),old).readingOrder)
    }
}
