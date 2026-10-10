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

    @Test fun pageTemplateSurvivesTabParsingAndRequestsSecondPage() {
        val src = source.copy(exploreList = "@css:li", exploreName = "@css:a@text", exploreBookUrl = "@css:a@href")
        val section = sourceDiscoveriesV41(src).first()
        assertTrue(section.template.contains("{{page}}"))
        val seen = mutableListOf<SourceRequestV36>()
        val fetch: (BookSourceV36, SourceRequestV36) -> org.jsoup.nodes.Document = { _, request ->
            seen += request
            org.jsoup.Jsoup.parse("<li><a href='/book/${seen.size}'>书${seen.size}</a></li>", request.url)
        }
        val first = discoverPageV41(src, section, fetchDocument = fetch)
        assertTrue(first.hasMore)
        assertNull(first.nextUrl)
        val second = discoverPageV41(src, section, 2, first.nextUrl, fetch)
        assertEquals("https://books.example/fantasy?page=2", seen.last().url)
        assertEquals("书2", second.books.single().name)
    }

    @Test fun postPaginationPreservesMethodBodyAndCharsetWithTheSameUrl() {
        val src = source.copy(exploreUrl = "榜单::/rank,{\"method\":\"POST\",\"body\":\"page={{page}}\",\"charset\":\"gbk\"}",
            exploreList = "@css:li", exploreName = "@css:a@text", exploreBookUrl = "@css:a@href")
        val section = sourceDiscoveriesV41(src).single()
        val seen = mutableListOf<SourceRequestV36>()
        val fetch: (BookSourceV36, SourceRequestV36) -> org.jsoup.nodes.Document = { _, request ->
            seen += request
            org.jsoup.Jsoup.parse("<li><a href='/book/${seen.size}'>书${seen.size}</a></li>", request.url)
        }
        val first = discoverPageV41(src, section, fetchDocument = fetch)
        val second = discoverPageV41(src, section, 2, first.nextUrl, fetch)
        assertTrue(first.hasMore && second.hasMore)
        assertNull(first.nextUrl)
        assertEquals(listOf("page=1", "page=2"), seen.map { it.body })
        assertTrue(seen.all { it.method == "POST" && it.charset == "gbk" })
        assertEquals(1, seen.map { it.url }.distinct().size)
    }

    @Test fun observedNextLinkWorksWithoutGuessingPageTemplate() {
        val src = source.copy(exploreUrl = "月榜::/ranking", exploreList = "@css:li", exploreName = "@css:a@text", exploreBookUrl = "@css:a@href")
        val section = sourceDiscoveriesV41(src).single()
        val result = discoverPageV41(src, section) { _, request ->
            org.jsoup.Jsoup.parse("<li><a href='/book/1'>书</a></li><a rel='next' href='/rank-page-2'>下一页</a>", request.url)
        }
        assertTrue(result.hasMore)
        assertEquals("https://books.example/rank-page-2", result.nextUrl)
    }

    @Test fun failedNextPageThrowsWithoutReturningAnEmptySuccess() {
        val src = source.copy(exploreList = "@css:li", exploreName = "@css:a@text", exploreBookUrl = "@css:a@href")
        val result = runCatching { discoverPageV41(src, sourceDiscoveriesV41(src).first(), 2) { _, _ -> throw java.io.IOException("offline") } }
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    @Test fun discoveryDoesNotSilentlyTruncateAtThreeHundredBooks() {
        val src = source.copy(exploreUrl = "全部::/all", exploreList = "@css:li", exploreName = "@css:a@text", exploreBookUrl = "@css:a@href")
        val result = discoverPageV41(src, sourceDiscoveriesV41(src).single()) { _, request ->
            org.jsoup.Jsoup.parse((1..301).joinToString("") { "<li><a href='/book/$it'>书$it</a></li>" }, request.url)
        }
        assertEquals(301, result.books.size)
        assertFalse(result.hasMore)
    }

    @Test fun unsupportedDiscoveryAndRuntimeDependenciesAreNamedOnImport() {
        val base = "\"bookSourceUrl\":\"https://books.example\",\"searchUrl\":\"/search?q={{key}}\",\"ruleSearch\":{\"bookList\":\"li\"}"
        // V95: a jsLib string runs in the script sandbox; a remote jsLib map and mainJs are still refused by name.
        assertEquals("function f(){return 1}", parseBookSourcesV36("{$base,\"jsLib\":\"function f(){return 1}\"}").sources.single().jsLib)
        val remoteLib = parseBookSourcesV36("{$base,\"jsLib\":{\"lib\":\"https://cdn.example/lib.js\"}}")
        assertTrue(remoteLib.sources.isEmpty())
        assertTrue(remoteLib.skipped.single().contains("jsLib"))
        val mainJs = parseBookSourcesV36("{$base,\"mainJs\":\"script\"}")
        assertTrue(mainJs.skipped.single().contains("mainJs"))
        // Optional runtime hooks no longer drop the whole source; the import says what is ignored.
        for (field in listOf("loginCheckJs", "coverDecodeJs", "exploreScreen")) {
            val imported = parseBookSourcesV36("{$base,\"$field\":\"script\"}")
            assertEquals(1, imported.sources.size)
            assertTrue(imported.warnings.toString(), imported.warnings.single().contains(field))
        }
        val invalid = sourceDiscoveryCatalogV41(source.copy(exploreUrl = "榜单::/rank,{\"webView\":true}"))
        assertTrue(invalid.sections.isEmpty())
        assertTrue(invalid.issues.single().contains("webView"))
    }

    @Test fun sameUrlWithDifferentPostBodiesRemainsDistinct() {
        val src = source.copy(exploreUrl = """[{"title":"月榜","url":"/rank,{\"method\":\"POST\",\"body\":\"type=month&page={{page}}\"}"},{"title":"总榜","url":"/rank,{\"method\":\"POST\",\"body\":\"type=all&page={{page}}\"}"}]""")
        val sections = sourceDiscoveriesV41(src)
        assertEquals(2, sections.size)
        assertEquals(1, sections.map { it.url }.distinct().size)
        assertEquals(2, sections.map { it.sourceId + "::" + it.template }.distinct().size)
    }

    @Test fun requestOptionsFailExplicitlyWhileSourceHeadersStaySupported() {
        for (field in listOf("headers", "header", "webView", "webJs", "js", "bodyJs", "dnsIp", "proxy")) {
            val imported = parseBookSourcesV36("""{"bookSourceUrl":"https://books.example","searchUrl":"/list, {\"$field\":\"value\"}","ruleSearch":{"bookList":"li"}}""")
            assertTrue(imported.sources.isEmpty())
            assertTrue(imported.skipped.single().contains(field))
        }
        val imported = parseBookSourcesV36("""{"bookSourceUrl":"https://books.example","header":{"User-Agent":"Fixture"},"searchUrl":"/search?q={{key}}","ruleSearch":{"bookList":"li"}}""")
        assertEquals("Fixture", imported.sources.single().headers["User-Agent"])
        assertTrue(imported.skipped.isEmpty())
        // V95: header objects, page expressions and <首页,后续页> lists are Legado features that now work.
        val withHeaders = parseBookSourcesV36("""{"bookSourceUrl":"https://books.example","searchUrl":"/list, {\"headers\":{\"X-A\":\"1\"}}","ruleSearch":{"bookList":"li"}}""")
        assertEquals("1", buildSearchRequestV36(withHeaders.sources.single(), "").headers["X-A"])
        assertEquals("https://books.example/list?page=2", buildSearchRequestV36(source.copy(searchUrl = "/list?page={{page+1}}"), "").url)
        assertEquals("https://books.example/list1.html", buildSearchRequestV36(source.copy(searchUrl = "/list<1,2>.html"), "").url)
        assertEquals("https://books.example/list2.html", buildSearchRequestV36(source.copy(searchUrl = "/list<1,2>.html"), "", 5).url)
    }

    @Test fun dynamicCategoryControlsAreReportedInsteadOfBecomingEmptyHeadings() {
        val catalog = sourceDiscoveryCatalogV41(source.copy(exploreUrl = """[{"title":"刷新","type":"button","action":"refresh()"}]"""))
        assertTrue(catalog.sections.isEmpty())
        assertTrue(catalog.issues.single().contains("action"))
        assertTrue(catalog.issues.single().contains("button"))
    }

    @Test fun repeatedStickyBooksDoNotHideTheObservedThirdPage() {
        val repeated = SourceDiscoveryPageV41(emptyList(), "https://books.example/list?page=3", true)
        assertNull(discoveryPagingIssueV41(repeated, false, setOf("https://books.example/list?page=1", "https://books.example/list?page=2")))
        val templateRepeat = SourceDiscoveryPageV41(emptyList(), null, true)
        assertTrue(discoveryPagingIssueV41(templateRepeat, false, emptySet()).orEmpty().contains("分页重复"))
        val cycle = SourceDiscoveryPageV41(emptyList(), "https://books.example/list?page=1", true)
        assertTrue(discoveryPagingIssueV41(cycle, true, setOf("https://books.example/list?page=1")).orEmpty().contains("循环"))
        assertNull(discoveryPagingIssueV41(SourceDiscoveryPageV41(emptyList(), null, false), false, emptySet()))
    }
}
