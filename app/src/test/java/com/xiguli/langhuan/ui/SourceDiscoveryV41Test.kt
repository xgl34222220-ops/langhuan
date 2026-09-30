package com.xiguli.langhuan.ui

import org.junit.Assert.*
import org.junit.Test

class SourceDiscoveryV41Test {
    private val source = BookSourceV36("s", "书源", "https://books.example/", exploreUrl = "玄幻::/fantasy?page={{page}}&&完结::/finished")

    @Test fun literalCategoriesResolveWithoutScriptExecution() {
        val sections = sourceDiscoveriesV41(source)
        assertEquals(listOf("玄幻", "完结"), sections.map { it.label })
        assertEquals("https://books.example/fantasy?page=1", sections.first().url)
        assertEquals("s", sections.first().sourceId)
    }
    @Test fun arrayCategoriesAndDisabledDiscoveryAreSupported() {
        val json = """[{"title":"新书","url":"/new"}]"""
        assertEquals("新书", sourceDiscoveriesV41(source.copy(exploreUrl = json)).single().label)
        assertTrue(sourceDiscoveriesV41(source.copy(enabledExplore = false)).isEmpty())
        assertTrue(sourceDiscoveriesV41(source.copy(enabled = false)).isEmpty())
    }
    @Test fun legadoDiscoveryFieldsSurviveImportWithoutInventedResults() {
        val raw = """{"bookSourceUrl":"https://books.example","bookSourceName":"测试","enabledExplore":false,"exploreUrl":"分类::/list","ruleExplore":{"bookList":".book","name":"h2@text","bookUrl":"a@href"}}"""
        val imported = parseBookSourcesV36(raw).sources.single()
        assertFalse(imported.enabledExplore)
        assertEquals(".book", imported.exploreList)
        assertEquals("h2@text", imported.exploreName)
        assertTrue(sourceDiscoveriesV41(imported).isEmpty())
    }
    @Test fun discoveryRejectsScriptsPrivateUrlsAndUnresolvedExpressions() {
        listOf("@js:java.ajax('x')", "榜单::http://127.0.0.1/", "榜单::javascript:alert(1)", "榜单::{{evil()}}")
            .forEach { assertTrue(it, sourceDiscoveriesV41(source.copy(exploreUrl = it)).isEmpty()) }
    }
}
