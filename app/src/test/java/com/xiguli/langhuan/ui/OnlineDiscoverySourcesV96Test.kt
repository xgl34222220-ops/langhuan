package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDiscoverySourcesV96Test {
    private val a = BookSourceV36("https://a.example/", "甲书源", "https://a.example/", exploreUrl = "玄幻::/a1&&都市::/a2&&历史::/a3&&科幻::/a4")
    private val b = BookSourceV36("https://b.example/", "乙书源", "https://b.example/", exploreUrl = "玄幻::/b1&&排行::/b2")

    @Test fun allSourcesInterleaveRoundRobinSoOneSourceCannotFillTheRow() {
        val catalog = onlineDiscoveryCatalogV96(listOf(a, b))
        val visible = visibleDiscoveriesV96(catalog.sources, null)
        assertEquals(listOf("玄幻", "玄幻", "都市", "排行", "历史", "科幻"), visible.map { it.label })
        assertEquals(listOf(a.id, b.id, a.id, b.id, a.id, a.id), visible.map { it.sourceId })
    }

    @Test fun selectingASourceShowsOnlyItsCategoriesAndAVanishedSelectionFallsBackToAll() {
        val catalog = onlineDiscoveryCatalogV96(listOf(a, b))
        assertEquals(listOf("甲书源", "乙书源"), catalog.sources.map { it.name })
        assertEquals(listOf("玄幻", "排行"), visibleDiscoveriesV96(catalog.sources, b.id).map { it.label })
        assertEquals(6, visibleDiscoveriesV96(catalog.sources, "gone").size)
    }

    @Test fun duplicateLabelsAcrossSourcesAreSuffixedWithSourceName() {
        val catalog = onlineDiscoveryCatalogV96(listOf(a, b))
        val visible = visibleDiscoveriesV96(catalog.sources, null)
        val labels = discoveryChipLabelsV96(visible, catalog.sources)
        assertEquals(listOf("玄幻·甲书源", "玄幻·乙书源", "都市", "排行", "历史", "科幻"), labels)
        assertEquals(listOf("玄幻", "排行"), discoveryChipLabelsV96(visibleDiscoveriesV96(catalog.sources, b.id), catalog.sources))
    }

    @Test fun sourcesWithoutUsableDiscoveryAreReportedNotDropped() {
        val noExplore = BookSourceV36("https://c.example/", "丙书源", "https://c.example/")
        val off = a.copy(id = "https://d.example/", name = "丁书源", enabledExplore = false)
        val unsupported = a.copy(id = "https://e.example/", name = "戊书源", exploreUrl = "榜单::http://127.0.0.1/")
        val disabled = a.copy(id = "https://f.example/", name = "己书源", enabled = false)
        val catalog = onlineDiscoveryCatalogV96(listOf(a, noExplore, off, unsupported, disabled))
        assertEquals(listOf(a.id), catalog.sources.map { it.sourceId })
        assertEquals(listOf("丙书源", "丁书源", "戊书源"), catalog.issues.map { it.sourceName })
        assertEquals("未配置发现分类", catalog.issues[0].reason)
        assertEquals("已关闭发现", catalog.issues[1].reason)
        assertTrue(catalog.issues[2].reason.isNotBlank())
    }

    @Test fun emptyInputYieldsNothing() {
        val catalog = onlineDiscoveryCatalogV96(emptyList())
        assertTrue(catalog.sources.isEmpty() && catalog.issues.isEmpty())
        assertTrue(interleaveDiscoveriesV96(emptyList()).isEmpty())
    }
}
