package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

/** Synthetic model responses and HTML only; no provider credentials or network requests. */
class AiSchemaRecoveryV45Test {
    @Test fun explicitCssMetadataDoesNotRejectOtherwiseValidStaticRules() {
        val parsed = parseRulesV37("""{"type":"css","searchList":"@css:li.book","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}""")
        assertEquals("@css:li.book", parsed["searchList"])
        assertEquals("@css:a@href", parsed["searchBookUrl"])
        assertFalse("Metadata must not become a book-source rule", parsed.containsKey("type"))
    }

    @Test fun explicitHtmlMetadataDoesNotChangeValidatedRules() {
        val parsed = parseRulesV37(withType("html", Stage.CONTENT.validRules))
        assertEquals(mapOf("contentText" to "@css:#content@html"), parsed)
    }

    @Test fun stageMetadataIsAcceptedOnlyForTheMatchingContext() {
        for (type in listOf("search", "ruleSearch")) {
            val parsed = parseRulesV37(withType(type, Stage.SEARCH.validRules), AiRuleStageV45.SEARCH)
            assertEquals("@css:li.book", parsed["searchList"])
            assertFalse(parsed.containsKey("type"))
        }
        val content = parseRulesV37(withType("content", Stage.CONTENT.validRules), AiRuleStageV45.CONTENT)
        assertEquals("@css:#content@html", content["contentText"])
        assertRejected(withType("content", Stage.SEARCH.validRules), AiRuleStageV45.SEARCH)
        assertRejected(withType("search", Stage.SEARCH.validRules))
    }

    @Test fun unsupportedMetadataIsNotSilentlyDropped() {
        for (type in listOf("object", "fixture-unknown", "javascript", "json")) {
            assertRejected(withType(type, Stage.SEARCH.validRules), AiRuleStageV45.SEARCH)
        }
        for (value in listOf("{}", "[]", "null", "true", "7")) {
            assertRejected("""{"type":$value,"searchList":"@css:li.book"}""", AiRuleStageV45.SEARCH)
        }
    }

    @Test fun metadataDoesNotAllowUnknownFieldsNestedRulesOrExecutableRules() {
        val invalid = listOf(
            """{"type":"css","unknownField":"fixture","searchList":"@css:li.book"}""",
            """{"type":"css","searchList":{"selector":"li.book"}}""",
            """{"type":"css","searchList":"@js:document.querySelectorAll('li')"}""",
            """{"type":"html","searchName":"<js>document.title</js>"}""",
            """{"type":"css","searchList":"@json:$.books"}""",
            """{"type":"css","searchList":"$.books"}""",
        )
        invalid.forEach { assertRejected(it, AiRuleStageV45.SEARCH) }
    }

    @Test fun validMetadataKeepsTheCompleteReadingJourneyWithinOneCallPerStage() = runBlocking {
        val fixture = Fixture { stage, _ -> withType("css", stage.validRules) }
        val report = fixture.build()
        fixture.assertReadingJourney(report)
        Stage.entries.forEach { assertEquals(it.name, 1, fixture.calls(it)) }
    }

    @Test fun malformedSchemaCanBeCorrectedOnceAtEachReadingStage() = runBlocking {
        for (target in Stage.entries) {
            val fixture = Fixture { stage, attempt ->
                if (stage == target && attempt == 1) withType("object", stage.validRules) else stage.validRules
            }
            fixture.assertReadingJourney(fixture.build())
            assertEquals(target.name, 2, fixture.calls(target))
            assertEquals("Correction must receive useful validation feedback", true,
                fixture.prompts.filter { it.first == target }.last().second.user.contains("type"))
            Stage.entries.filter { it != target }.forEach { assertEquals(it.name, 1, fixture.calls(it)) }
        }
    }

    @Test fun terminalSchemaFailureCompletesTheVisibleSearchStepWithTheSameError() = runBlocking {
        val fixture = Fixture { stage, _ ->
            if (stage == Stage.SEARCH) """{"unknownField":"fixture"}""" else stage.validRules
        }
        val failure = runCatching { fixture.build() }.exceptionOrNull()
        assertNotNull("A response with an unsupported field must still fail", failure)
        fixture.assertFailedStep("分析搜索结果页", failure!!)
        assertEquals("Schema correction has one retry, never an unbounded loop", 2, fixture.calls(Stage.SEARCH))
        assertEquals(0, fixture.calls(Stage.TOC))
    }

    @Test fun repeatedBadSchemaStopsAfterTwoCallsAtEveryReadingStage() = runBlocking {
        for (target in Stage.entries) {
            val fixture = Fixture { stage, _ ->
                if (stage == target) """{"unknownField":"fixture"}""" else stage.validRules
            }
            val failure = runCatching { fixture.build() }.exceptionOrNull()
            assertNotNull(target.name, failure)
            fixture.assertFailedStep(target.label, failure!!)
            assertEquals(target.name, 2, fixture.calls(target))
            Stage.entries.filter { it.ordinal > target.ordinal }.forEach { assertEquals(it.name, 0, fixture.calls(it)) }
        }
    }

    @Test fun schemaCorrectionAndEmptyResultRetryShareTheSameTwoCallBudget() = runBlocking {
        for (target in Stage.entries) {
            val fixture = Fixture { stage, attempt ->
                when {
                    stage != target -> stage.validRules
                    attempt == 1 -> withType("object", stage.validRules)
                    attempt == 2 -> stage.emptyRules
                    else -> stage.validRules
                }
            }
            val failure = runCatching { fixture.build() }.exceptionOrNull()
            assertNotNull("A third call must not rescue ${target.name} after its shared budget is exhausted", failure)
            fixture.assertFailedStep(target.label, failure!!)
            assertEquals(target.name, 2, fixture.calls(target))
        }
    }

    @Test fun emptyResultThenBadSchemaDoesNotOpenAnotherCorrectionBudget() = runBlocking {
        for (target in Stage.entries) {
            val fixture = Fixture { stage, attempt ->
                when {
                    stage != target -> stage.validRules
                    attempt == 1 -> stage.emptyRules
                    attempt == 2 -> withType("object", stage.validRules)
                    else -> stage.validRules
                }
            }
            val failure = runCatching { fixture.build() }.exceptionOrNull()
            assertNotNull("A third call must not rescue ${target.name} after its shared budget is exhausted", failure)
            fixture.assertFailedStep(target.label, failure!!)
            assertEquals(target.name, 2, fixture.calls(target))
        }
    }

    @Test fun theExistingEmptyResultRetryStillRecoversAtEveryReadingStage() = runBlocking {
        for (target in Stage.entries) {
            val fixture = Fixture { stage, attempt ->
                if (stage == target && attempt == 1) stage.emptyRules else stage.validRules
            }
            fixture.assertReadingJourney(fixture.build())
            assertEquals(target.name, 2, fixture.calls(target))
        }
    }

    @Test fun gatewayFailuresTerminalizeTheCurrentStepWithoutASchemaRetry() = runBlocking {
        for (target in Stage.entries) {
            val expected = IOException("fixture provider unavailable at ${target.name}")
            val fixture = Fixture { stage, _ -> if (stage == target) throw expected else stage.validRules }
            val actual = runCatching { fixture.build() }.exceptionOrNull()
            assertOriginalFailure(expected, actual)
            fixture.assertFailedStep(target.label, actual!!)
            assertEquals(target.name, 1, fixture.calls(target))
        }
    }

    @Test fun executableRulesAndUnsupportedEnginesFailWithoutAModelCorrectionCharge() = runBlocking {
        for (target in Stage.entries) {
            for (unsupported in listOf(withType("javascript", target.validRules), target.scriptRules)) {
                val fixture = Fixture { stage, _ -> if (stage == target) unsupported else stage.validRules }
                val failure = runCatching { fixture.build() }.exceptionOrNull()
                assertNotNull(target.name, failure)
                fixture.assertFailedStep(target.label, failure!!)
                assertEquals("Unsupported capabilities must stop at ${target.name}", 1, fixture.calls(target))
            }
        }
    }

    @Test fun explicitUnsupportedCapabilitiesTakePrecedenceOverUnknownFields() = runBlocking {
        for (target in Stage.entries) {
            val unsupported = listOf("javascript", "jsonpath", "xpath").map { type ->
                "{\"unexpected\":\"fixture\",${withType(type, target.validRules).removePrefix("{")}"
            } + "{\"unexpected\":\"fixture\",${target.scriptRules.removePrefix("{")}"
            for (raw in unsupported) {
                val fixture = Fixture { stage, attempt ->
                    if (stage == target && attempt == 1) raw else stage.validRules
                }
                val failure = runCatching { fixture.build() }.exceptionOrNull()
                assertTrue("An explicit unsupported capability must stop without a corrective response: $raw", failure is UnsupportedAiRuleCapabilityV45)
                fixture.assertFailedStep(target.label, failure!!)
                assertEquals("Unknown fields must not open a paid format correction for $raw", 1, fixture.calls(target))
            }
        }
    }

    @Test fun directoriesAreExpandedBeforeSpendingTheFinalDiscoverySetsBudget() = runBlocking {
        val fixture = DiscoveryFixture { expanded, attempt ->
            if (!expanded && attempt == 1) """{"type":"object","exploreList":"@css:li.book"}"""
            else DiscoveryFixture.ALL_BOOKS
        }
        val report = fixture.build()
        fixture.assertVerified(report, listOf("月榜", "玄幻"))
        assertTrue(report.discoveryWarnings.toString(), report.discoveryWarnings.isEmpty())
        assertEquals("Directory HTML must not consume a book-rule generation call", 0, fixture.calls(expanded = false))
        assertEquals("Only the final observed book pages are sent for rule generation", 1, fixture.calls(expanded = true))
        assertTrue(fixture.requests.contains("https://books.example/category/fantasy"))
        assertEquals(true, fixture.steps.last().ok)
    }

    @Test fun exhaustedDiscoveryCorrectionKeepsOtherVerifiedEntries() = runBlocking {
        val fixture = DiscoveryFixture(withHub = false) { _, attempt ->
            if (attempt == 1) """{"unexpected":"fixture"}""" else DiscoveryFixture.ALL_BOOKS
        }
        val report = fixture.build()
        fixture.assertVerified(report, listOf("月榜"))
        assertTrue("The empty entry must be named rather than erasing the valid rank", report.discoveryWarnings.any { it.contains("空分类") })
        assertEquals(2, fixture.calls(expanded = false))
        assertEquals(0, fixture.calls(expanded = true))
        assertEquals(false, fixture.steps.last().ok)
    }

    @Test fun expandedDiscoverySchemaAndEmptyResultCorrectionsShareTwoCallsAndKeepValidEntries() = runBlocking {
        for (schemaFirst in listOf(true, false)) {
            val fixture = DiscoveryFixture { expanded, attempt ->
                when {
                    !expanded -> DiscoveryFixture.ALL_BOOKS
                    (attempt == 1) == schemaFirst -> """{"unexpected":"fixture"}"""
                    else -> DiscoveryFixture.RANK_ONLY
                }
            }
            val report = fixture.build()
            fixture.assertVerified(report, listOf("月榜", "玄幻"))
            assertEquals("Directory-only rules are no longer generated", 0, fixture.calls(expanded = false))
            assertEquals("Final-set schema and extraction recovery must share two calls", 2, fixture.calls(expanded = true))
            assertEquals("A valid observed static fallback still undergoes the complete reading check", 2, report.discoveryEvidence.size)
            assertEquals(schemaFirst, fixture.steps.last().ok)
        }
    }

    @Test fun failedFinalGenerationStillValidatesObservedStaticFallbacks() = runBlocking {
        val fixture = DiscoveryFixture { expanded, _ ->
            if (expanded) """{"unexpected":"fixture"}""" else DiscoveryFixture.ALL_BOOKS
        }
        val report = fixture.build()
        fixture.assertVerified(report, listOf("月榜", "玄幻"))
        assertTrue("A failed optional regeneration must be reported", report.discoveryWarnings.isNotEmpty())
        assertEquals(0, fixture.calls(expanded = false))
        assertEquals(2, fixture.calls(expanded = true))
        assertEquals(false, fixture.steps.last().ok)
    }

    @Test fun gatewayCancellationPropagatesWithoutAFormatRetryOrLateFailureCallback() = runBlocking {
        val expected = CancellationException("fixture cancelled")
        val fixture = Fixture { stage, _ -> if (stage == Stage.SEARCH) throw expected else stage.validRules }
        val actual = runCatching { fixture.build() }.exceptionOrNull()
        assertOriginalFailure(expected, actual)
        assertEquals(1, fixture.calls(Stage.SEARCH))
        assertEquals("分析搜索结果页", fixture.steps.last().label)
        assertFalse(fixture.steps.last().completed)
        assertNull(fixture.steps.last().ok)
        assertEquals("No callback may convert cancellation into failure", 5, fixture.snapshots.size)
    }

    @Test fun cancellingTheBuildJobDoesNotPublishALateTerminalStep() = runBlocking {
        val enteredGateway = CompletableDeferred<Unit>()
        val fixture = Fixture { stage, _ ->
            if (stage == Stage.SEARCH) {
                enteredGateway.complete(Unit)
                awaitCancellation()
            }
            stage.validRules
        }
        val job = launch { fixture.build() }
        enteredGateway.await()
        val beforeCancel = fixture.snapshots.toList()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(beforeCancel, fixture.snapshots)
        assertEquals(1, fixture.calls(Stage.SEARCH))
        assertEquals(0, fixture.calls(Stage.TOC))
    }

    private fun withType(type: String, rules: String) = "{\"type\":\"$type\",${rules.removePrefix("{")}"

    private fun assertRejected(raw: String, stage: AiRuleStageV45? = null) {
        assertTrue("Unsafe or unsupported schema must be rejected: $raw", runCatching { parseRulesV37(raw, stage) }.isFailure)
    }

    private fun assertOriginalFailure(expected: Throwable, actual: Throwable?) {
        assertNotNull(actual)
        // Gradle enables coroutine stack-trace recovery, which may clone the exception
        // while retaining the original in its cause chain. Identity of the wrapper is not
        // cancellation/transport semantics; type, message and the original cause are.
        assertEquals(expected.javaClass, actual!!.javaClass)
        assertEquals(expected.message, actual.message)
        assertTrue("The original failure must remain in the causal chain",
            generateSequence(actual) { it.cause }.take(8).any { it === expected })
    }

    private enum class Stage(val label: String, val validRules: String, val emptyRules: String, val scriptRules: String) {
        SEARCH("分析搜索结果页",
            """{"searchList":"@css:li.book","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}""",
            """{"searchList":"@css:.missing","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}""",
            """{"searchList":"@js:document.querySelectorAll('li')"}"""),
        TOC("分析书籍页与目录",
            """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@data-url"}""",
            """{"tocList":"@css:.missing","tocName":"@css:@text","tocUrl":"@css:@data-url"}""",
            """{"tocList":"@js:document.querySelectorAll('a')"}"""),
        CONTENT("分析正文页",
            """{"contentText":"@css:#content@html"}""",
            """{"contentText":"@css:.missing@html"}""",
            """{"contentText":"@js:document.body.innerText"}"""),
    }

    private class DiscoveryFixture(
        private val withHub: Boolean = true,
        private val respond: suspend (expanded: Boolean, attempt: Int) -> String,
    ) {
        val steps = ArrayList<AiSourceStepV37>()
        val requests = ArrayList<String>()
        private val prompts = ArrayList<Pair<Boolean, PromptBundle>>()
        private val base = "https://books.example"
        private val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("Only fixture text generation is supported")
            override suspend fun generateText(prompt: PromptBundle): String = when {
                prompt.user.contains("识别网站已有的发现分类") -> when {
                    prompt.user.contains("页面：$base/categories\n") -> """{"exploreUrl":"玄幻::$base/category/fantasy"}"""
                    prompt.user.contains("页面：$base/\n") -> if (withHub)
                        """{"exploreUrl":"月榜::$base/ranking&&分类::$base/categories"}"""
                    else """{"exploreUrl":"月榜::$base/ranking&&空分类::$base/empty"}"""
                    else -> error("Unexpected discovery evidence page: ${prompt.user.take(300)}")
                }
                prompt.user.contains("共用的发现书目规则") -> {
                    // Inspect a page header, not a href inside the original category-directory HTML.
                    val expanded = prompt.user.contains("【玄幻 $base/category/fantasy】")
                    prompts += expanded to prompt
                    check(calls(expanded) <= 3) { "Fixture refuses unbounded discovery retries" }
                    respond(expanded, calls(expanded))
                }
                prompt.user.contains("搜索结果页") -> Stage.SEARCH.validRules
                prompt.user.contains("书籍详情页") -> Stage.TOC.validRules
                prompt.user.contains("章节正文页") -> Stage.CONTENT.validRules
                else -> error("Unexpected fixture prompt: ${prompt.user.take(100)}")
            }
        }

        fun calls(expanded: Boolean) = prompts.count { it.first == expanded }

        private fun fetch(source: BookSourceV36, request: SourceRequestV36): Document {
            requests += request.url
            require(request.url.startsWith("$base/")) { "Fixture refuses an unexpected origin: ${request.url}" }
            fun bookRow(number: Int, kind: String) = "<li class='book $kind'><a href='/book/$number'>示例小说$number</a></li>"
            val html = when (val path = request.url.removePrefix(base).substringBefore('?')) {
                "/" -> "<title>发现预算测试站</title><form action='/search'><input name='q'></form><nav><a href='/ranking'>月榜</a>" +
                    (if (withHub) "<a href='/categories'>分类</a>" else "<a href='/empty'>空分类</a>") + "</nav>"
                "/search", "/ranking" -> "<ul>${bookRow(1, "rank")}</ul>"
                "/categories" -> "<nav><a href='/category/fantasy'>玄幻</a></nav>"
                "/category/fantasy" -> "<ul>${bookRow(2, "category")}</ul>"
                "/empty" -> "<p>暂无书籍</p>"
                "/book/1", "/book/2" -> "<h1>示例小说${path.substringAfterLast('/')}</h1><div id='chapters'><a data-url='/read/1'>第一章</a><a data-url='/read/2'>第二章</a></div>"
                "/read/1", "/read/2" -> "<div id='content'><p>${"这是发现预算回归的原创正文，用于实际验证保留入口的目录和正文。".repeat(12)}</p></div>"
                else -> error("No fixture page for ${request.url}")
            }
            return Jsoup.parse(html, request.url)
        }

        suspend fun build() = BookSourceAiBuilderV37(gateway, { steps.clear(); steps.addAll(it) }, fetchDocument = ::fetch)
            .build("$base/", "示例小说1")

        fun assertVerified(report: AiSourceReportV37, labels: List<String>) {
            assertTrue("Verified discovery entries must survive optional correction failure: ${report.discoveryWarnings}", report.source.enabledExplore)
            assertEquals(labels, report.discoveryLabels)
            assertEquals(labels, report.discoveryEvidence.map { it.label })
            report.discoveryEvidence.forEach {
                assertEquals(1, it.bookCount)
                assertEquals(2, it.chapterCount)
            }
            assertEquals(1, report.searchCount)
            assertEquals(2, report.chapterCount)
            assertTrue(report.sample.length >= 60)
            assertTrue(steps.all { it.completed })
            assertTrue(steps.take(5).all { it.ok == true })
        }

        companion object {
            const val ALL_BOOKS = """{"exploreList":"@css:li.book","exploreName":"@css:a@text","exploreBookUrl":"@css:a@href"}"""
            const val RANK_ONLY = """{"exploreList":"@css:li.rank","exploreName":"@css:a@text","exploreBookUrl":"@css:a@href"}"""
        }
    }

    private class Fixture(private val respond: suspend (Stage, Int) -> String = { stage, _ -> stage.validRules }) {
        val steps = ArrayList<AiSourceStepV37>()
        val snapshots = ArrayList<List<AiSourceStepV37>>()
        val prompts = ArrayList<Pair<Stage, PromptBundle>>()
        val requests = ArrayList<String>()
        private val base = "https://books.example"
        private val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("Only the text path is supported by this fixture")
            override suspend fun generateText(prompt: PromptBundle): String {
                val stage = when {
                    prompt.user.contains("搜索结果页") -> Stage.SEARCH
                    prompt.user.contains("书籍详情页") -> Stage.TOC
                    prompt.user.contains("章节正文页") -> Stage.CONTENT
                    else -> error("Unexpected fixture prompt: ${prompt.user.take(100)}")
                }
                prompts += stage to prompt
                check(calls(stage) <= 3) { "Fixture refuses unbounded model retries at $stage" }
                return respond(stage, calls(stage))
            }
        }

        fun calls(stage: Stage) = prompts.count { it.first == stage }

        private fun fetch(source: BookSourceV36, request: SourceRequestV36): Document {
            requests += request.url
            require(request.url.startsWith("$base/")) { "Fixture refuses an unexpected origin: ${request.url}" }
            val html = when (request.url.removePrefix(base).substringBefore('?')) {
                "/" -> "<title>静态规则测试站</title><form action='/search'><input name='q'></form>"
                "/search" -> "<ul><li class='book'><a href='/book/1'>示例小说</a></li></ul>"
                // The data attribute needs the declared rule; a href heuristic cannot hide an empty-rule retry.
                "/book/1" -> "<h1>示例小说</h1><div id='chapters'><a data-url='/read/1'>第一章</a><a data-url='/read/2'>第二章</a></div>"
                "/read/1", "/read/2" -> "<div id='content'><p>${"这是静态测试页面的原创正文，用来验证格式纠正后仍有完整阅读链路。".repeat(12)}</p></div>"
                else -> error("No fixture page for ${request.url}")
            }
            return Jsoup.parse(html, request.url)
        }

        suspend fun build() = BookSourceAiBuilderV37(gateway, { steps.clear(); steps.addAll(it); snapshots += it }, fetchDocument = ::fetch)
            .build("$base/", "示例小说")

        fun assertReadingJourney(report: AiSourceReportV37) {
            assertEquals("示例小说", report.bookName)
            assertEquals(1, report.searchCount)
            assertEquals(2, report.chapterCount)
            assertTrue(report.sample.length >= 60)
            assertFalse(report.source.enabledExplore)
            assertTrue("Every visible stage is complete after a successful build", steps.all { it.completed })
            assertTrue(steps.take(5).all { it.ok == true })
        }

        fun assertFailedStep(label: String, failure: Throwable) {
            val last = steps.last()
            assertEquals(label, last.label)
            assertTrue("A failed stage must stop displaying as in progress: $last", last.completed)
            assertEquals(false, last.ok)
            assertEquals("The progress detail must explain the same failure returned to the caller", failure.message, last.detail)
            assertTrue("Earlier successful stages stay completed", steps.dropLast(1).all { it.completed && it.ok == true })
        }
    }
}
