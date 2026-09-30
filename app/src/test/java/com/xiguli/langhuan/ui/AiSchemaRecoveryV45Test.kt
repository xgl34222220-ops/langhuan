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
            assertSame("Non-schema provider errors must keep their cause", expected, actual)
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

    @Test fun gatewayCancellationPropagatesWithoutAFormatRetryOrLateFailureCallback() = runBlocking {
        val expected = CancellationException("fixture cancelled")
        val fixture = Fixture { stage, _ -> if (stage == Stage.SEARCH) throw expected else stage.validRules }
        val actual = runCatching { fixture.build() }.exceptionOrNull()
        assertSame(expected, actual)
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

        suspend fun build() = BookSourceAiBuilderV37(gateway, { steps.clear(); steps.addAll(it); snapshots += it }, ::fetch)
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
