package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

/** Regression evidence is synthetic HTML/model output. No live site or provider is contacted. */
class AiDiscoveryRepairV51Test {
    @Test fun documentedStaticLegadoExploreObjectNormalizesWithoutAnotherModelCall() {
        val rules = parseRulesV37("说明：\n```json\n$NESTED_RULES\n```", AiRuleStageV45.EXPLORE)
        assertEquals("@css:div.card, tr.entry", rules["exploreList"])
        assertEquals("@css:h2 a, a.title@href", rules["exploreBookUrl"])
        assertFalse(rules.containsKey("ruleExplore"))
    }

    @Test fun nestedExploreCannotHideUnknownFieldsConflictsScriptsOrOtherStageRules() {
        val invalid = listOf(
            """{"ruleExplore":{"bookList":"li","unknown":"discard me"}}""",
            """{"ruleExplore":{"bookList":"li"},"unknown":"discard me"}""",
            """{"ruleExplore":{"bookList":{"selector":"li"}}}""",
            """{"ruleExplore":{"bookList":"li"},"exploreList":"div"}""",
            """{"ruleExplore":{"bookList":"@js:document.body"}}""",
            """{"ruleExplore":{"bookList":"@css:li","jsLib":"private script"}}""",
            """{"ruleExplore":{"bookList":"$.books"}}""",
            """{"ruleExplore":{"type":"javascript","bookList":"li"}}""",
        )
        invalid.forEach { raw -> assertTrue(raw, runCatching { parseRulesV37(raw, AiRuleStageV45.EXPLORE) }.isFailure) }
        assertTrue(runCatching { parseRulesV37(NESTED_RULES, AiRuleStageV45.SEARCH) }.isFailure)
    }

    @Test fun nestedExecutableRuleIsRejectedBeforeUnknownFieldsCanOpenAFormatRetry() {
        val problem = runCatching {
            parseRulesV37("""{"unexpected":"ignored?","ruleExplore":{"bookList":"@js:document.body"}}""", AiRuleStageV45.EXPLORE)
        }.exceptionOrNull()
        assertTrue(problem is UnsupportedAiRuleCapabilityV45)
    }

    @Test fun discoverySkeletonPreservesBookStructureAfterLargeNavigationAndQueryHref() {
        val doc = Jsoup.parse("<header>${largeNavigation()}</header><main id='books'><div class='card'><h2><a href='/book/2?edition=full'>真实书名</a></h2></div></main>", "$BASE/genre/2")
        assertFalse(pageSkeletonV37(doc, 2500).contains("真实书名"))
        val skeleton = aiDiscoverySkeletonV51(doc, listOf("$BASE/book/1"), 2500)
        assertTrue(skeleton, skeleton.contains("main#books"))
        assertTrue(skeleton, skeleton.contains("div.card"))
        assertTrue(skeleton, skeleton.contains("真实书名"))
        assertTrue(skeleton.length <= 2500)
    }

    @Test fun mixedNavigationAndUnfamiliarBookUrlsAreKeptForModelInspection() {
        val doc = Jsoup.parse("<nav><a href='/categories'>小说分类</a></nav><main><div><a href='/different-detail/42'>山海之外</a></div></main>", "$BASE/genre/2")
        assertFalse(aiDiscoveryDirectoryV51(doc, listOf("$BASE/book/1")))
        assertTrue(aiDiscoverySkeletonV51(doc, listOf("$BASE/book/1"), 2500).contains("山海之外"))
    }

    @Test fun directoryNavigationIgnoresFooterUtilitiesAndSidebarBookRecommendations() {
        val doc = Jsoup.parse("""<header><a href='/account'>用户中心</a></header><main><nav><a href='/category/fantasy'>玄幻</a></nav></main><aside><a href='/book/1'>热门推荐书</a></aside><footer><a href='/cookies'>Cookies Policy</a><a href='/dmca'>DMCA</a><a href='/privacy'>Privacy</a></footer>""", "$BASE/categories")
        assertTrue(aiDiscoveryDirectoryV51(doc, listOf("$BASE/book/1")))
        assertTrue(aiDiscoveryBookLinksV51(doc, listOf("$BASE/book/1")).isEmpty())
        val mixed = Jsoup.parse("""<main><nav><a href='/categories'>小说分类</a></nav><div class='card'><a href='/book/2'>真正分类书</a></div></main><aside><a href='/book/1'>热门推荐书</a></aside>""", "$BASE/genre/2")
        assertFalse(aiDiscoveryDirectoryV51(mixed, listOf("$BASE/book/1")))
        val prompt = aiDiscoverySkeletonV51(mixed, listOf("$BASE/book/1"), 2500)
        assertTrue(prompt.contains("真正分类书"))
        assertFalse(prompt.contains("热门推荐书"))
    }

    @Test fun twoLevelDirectoriesAreReplacedBeforeGeneratingTwelveRealCategoriesAndRankings() = runBlocking {
        val fixture = Fixture()
        val report = fixture.build()
        assertEquals(12, report.discoveryLabels.size)
        assertTrue(report.discoveryLabels.contains("月榜"))
        assertTrue(report.discoveryLabels.contains("玄幻"))
        assertFalse(report.discoveryLabels.contains("小说分类"))
        assertFalse(report.discoveryLabels.contains("排行榜"))
        assertEquals(1, fixture.explorePrompts.size)
        val prompt = fixture.explorePrompts.single().user
        assertTrue(prompt.contains("div.card"))
        assertTrue(prompt.contains("tr.entry"))
        assertFalse(prompt.contains("【小说分类 $BASE/categories】"))
        assertFalse(prompt.contains("【玄幻分类 $BASE/categories/fiction】"))
        assertEquals(1, report.discoveryWarnings.count { it.contains("最多验证") })
        assertEquals(report.discoveryWarnings.distinct(), report.discoveryWarnings)
        assertEquals(report.discoveryWarnings, fixture.steps.last().details)
        assertTrue(fixture.steps.last().detail.length < 80)
        report.discoveryEvidence.forEach { assertTrue(it.chapterCount > 0); assertTrue(it.bookCount > 0) }
        assertTrue(fixture.requests.count { it.contains("/genre/") || it.contains("/categor") || it.contains("/rank") } <= 24)
    }

    @Test fun twoInvalidJsonResponsesUseOnlyObservedStaticFallbackAndKeepReadingRules() = runBlocking {
        val fixture = Fixture(response = { "This is invalid JSON" })
        val report = fixture.build()
        assertEquals(2, fixture.explorePrompts.size)
        assertEquals(12, report.discoveryLabels.size)
        assertTrue(report.source.exploreList.contains("a[href^='/book/']"))
        assertTrue(report.discoveryWarnings.any { it.contains("规则生成") && it.contains("2 次") })
        assertTrue(report.discoveryWarnings.none { it.contains("未保存书源") })
        assertTrue(report.searchCount > 0)
        assertTrue(report.sample.isNotBlank())
        assertTrue(bookSourceSupportedV36(report.source))
    }

    @Test fun emptyExtractionAndSchemaRepairShareTheFinalPageSetsTwoCallLimit() = runBlocking {
        val fixture = Fixture(response = { attempt -> if (attempt == 1) """{"exploreList":"@css:.missing","exploreName":"@css:a@text","exploreBookUrl":"@css:a@href"}""" else "bad json" })
        val report = fixture.build()
        assertEquals(2, fixture.explorePrompts.size)
        assertEquals(12, report.discoveryLabels.size)
        assertTrue(report.discoveryWarnings.any { it.contains("发现规则纠正") })
    }

    @Test fun invalidJsonWithoutAUsableFallbackRemainsAnExplicitOptionalFailure() = runBlocking {
        val fixture = Fixture(response = { "bad json" }, emptyPages = true)
        val report = fixture.build()
        assertEquals(2, fixture.explorePrompts.size)
        assertFalse(report.source.enabledExplore)
        assertTrue(report.discoveryLabels.isEmpty())
        assertEquals(1, report.searchCount)
        assertTrue(report.chapterCount > 0)
        assertTrue(report.sample.isNotBlank())
        assertTrue(fixture.steps.last().detail.contains("已保留搜索与阅读规则"))
        assertTrue(report.discoveryWarnings.none { it.contains("未保存书源") })
    }

    @Test fun firstSiteRateLimitOrChallengeStopsAllLaterRequestsAndModelCalls() = runBlocking {
        for (problem in listOf(SourceHttpStatusExceptionV44(429, BASE, "fixture rate limit"), SourceBrowserChallengeV46(BASE))) {
            val fixture = Fixture(stop = problem)
            val report = fixture.build()
            assertEquals("fetch:$BASE/genre/11", fixture.events.last())
            assertEquals(0, fixture.explorePrompts.size)
            assertEquals(1, fixture.requests.count { it == "$BASE/genre/11" })
            assertFalse(report.source.enabledExplore)
            assertTrue(report.discoveryWarnings.any { it.contains("停止") })
            assertTrue(report.sample.isNotBlank())
        }
    }

    private class Fixture(
        val response: (Int) -> String = { NESTED_RULES },
        val emptyPages: Boolean = false,
        val stop: Exception? = null,
    ) {
        val requests = ArrayList<String>()
        val events = ArrayList<String>()
        val steps = ArrayList<AiSourceStepV37>()
        val explorePrompts = ArrayList<PromptBundle>()
        val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("unused")
            override suspend fun generateText(prompt: PromptBundle): String {
                events += "model:" + prompt.user.lineSequence().first()
                return when {
                    prompt.user.contains("识别网站已有的发现分类") -> {
                        val links = when {
                            prompt.user.contains("页面：$BASE/categories/fiction\n") -> "玄幻::$BASE/genre/11&&奇幻::$BASE/genre/12"
                            prompt.user.contains("页面：$BASE/categories\n") -> "玄幻分类::$BASE/categories/fiction"
                            prompt.user.contains("页面：$BASE/rankings\n") -> "月榜::$BASE/ranks/month"
                            prompt.user.contains("页面：$BASE/\n") -> homeLinks().joinToString("&&") { "${it.first}::$BASE${it.second}" }
                            else -> error("Unexpected link page")
                        }
                        buildJsonObject { put("exploreUrl", JsonPrimitive(links)) }.toString()
                    }
                    prompt.user.contains("共用的发现书目规则") -> { explorePrompts += prompt; response(explorePrompts.size) }
                    prompt.user.contains("搜索结果页") -> """{"searchList":"@css:li.search","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
                    prompt.user.contains("书籍详情页") -> """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
                    prompt.user.contains("章节正文页") -> """{"contentText":"@css:#content@html"}"""
                    else -> error("Unexpected prompt")
                }
            }
        }
        suspend fun build(): AiSourceReportV37 = BookSourceAiBuilderV37(gateway, { steps.clear(); steps.addAll(it) }, fetchDocument = ::fetch).build("$BASE/", "真实小说1")
        private fun fetch(source: BookSourceV36, request: SourceRequestV36): Document {
            requests += request.url
            events += "fetch:${request.url}"
            if (request.url == "$BASE/genre/11" && stop != null) throw stop
            val path = request.url.removePrefix(BASE).substringBefore('?')
            val html = when {
                path == "/" -> "<title>发现回归夹具</title><form action='/search'><input name='q'></form>" + homeLinks().joinToString("") { "<a href='${it.second}'>${it.first}</a>" }
                path == "/search" -> "<ul><li class='search'><a href='/book/1'>真实小说1</a></li></ul>"
                path == "/categories" -> "<main><nav><a href='/categories/fiction'>玄幻分类</a></nav></main><aside><a href='/book/1'>热门推荐书</a></aside><footer><a href='/cookies'>Cookies Policy</a><a href='/dmca'>DMCA</a></footer>"
                path == "/categories/fiction" -> "<nav><a href='/genre/11'>玄幻</a><a href='/genre/12'>奇幻</a><a href='/categories'>小说分类</a></nav>"
                path == "/rankings" -> "<nav><a href='/ranks/month'>月榜</a></nav>"
                path.startsWith("/genre/") || path == "/ranks/month" -> if (emptyPages) "<p>暂无书籍</p>" else {
                    val number = if (path == "/ranks/month") "20" else path.substringAfterLast('/')
                    val row = if (path == "/ranks/month") "<table><tr class='entry'><td><a class='title' href='/book/$number'>真实小说$number</a></td></tr></table>"
                        else "<div class='card'><h2><a href='/book/$number'>真实小说$number</a></h2></div>"
                    "<header>${largeNavigation()}</header><nav><a href='/categories'>小说分类</a></nav><main id='books'>$row</main>"
                }
                path.startsWith("/book/") -> "<h1>真实小说${path.substringAfterLast('/')}</h1><div id='chapters'><a href='/read/1'>第一章</a><a href='/read/2'>第二章</a></div>"
                path.startsWith("/read/") -> "<div id='content'>${"这是自行编写的测试正文，用于确认发现入口确实可以打开目录和正文。".repeat(8)}</div>"
                else -> error("Unexpected fetch ${request.url}")
            }
            return Jsoup.parse(html, request.url)
        }
    }

    companion object {
        private const val BASE = "https://books.example"
        private const val NESTED_RULES = """{"ruleExplore":{"bookList":"@css:div.card, tr.entry","name":"@css:h2 a, a.title@text","bookUrl":"@css:h2 a, a.title@href"}}"""
        private fun homeLinks() = listOf("小说分类" to "/categories", "排行榜" to "/rankings") + (1..10).map { "分类$it" to "/genre/$it" }
        private fun largeNavigation() = (1..120).joinToString("") { "<div id='navigation$it'><a href='/categories'>小说分类</a></div>" }
    }
}
