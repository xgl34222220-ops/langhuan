from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"expected block not found: {path}\n{old[:180]}")
    p.write_text(text.replace(old, new, 1))


reader = "app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt"
replace_once(
    reader,
    '    val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f\n',
    '    // Page and scroll modes must honor the same typography controls. Previously paged mode\n'
    '    // forced paragraph spacing to zero, making part of the type sheet look ineffective.\n'
    '    val pagedParagraphSpacing = paragraphSpacing\n',
)
replace_once(
    reader,
    '            LanghuanRowV4("减小行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor - .05f).coerceAtLeast(1.35f)) })\n'
    '            LanghuanRowV4("增大行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor + .05f).coerceAtMost(2.20f)) })\n'
    '            LanghuanRowV4("减小段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing - 1f).coerceAtLeast(0f)) })\n'
    '            LanghuanRowV4("增大段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing + 1f).coerceAtMost(20f)) })\n'
    '            LanghuanRowV4("减小页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding - 2f).coerceAtLeast(14f)) })\n'
    '            LanghuanRowV4("增大页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding + 2f).coerceAtMost(36f)) })\n',
    '            // Make each tap visually meaningful while keeping the exact value in state/prefs.\n'
    '            LanghuanRowV4("减小行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor - .10f).coerceAtLeast(1.30f)) })\n'
    '            LanghuanRowV4("增大行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor + .10f).coerceAtMost(2.30f)) })\n'
    '            LanghuanRowV4("减小段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing - 2f).coerceAtLeast(0f)) })\n'
    '            LanghuanRowV4("增大段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing + 2f).coerceAtMost(24f)) })\n'
    '            LanghuanRowV4("减小页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding - 4f).coerceAtLeast(12f)) })\n'
    '            LanghuanRowV4("增大页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding + 4f).coerceAtMost(48f)) })\n',
)

chat = "app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt"
old = '''            val gateway = executionPlan.primaryTask?.let { routingSession.selection(it).gateway } ?: routingSession.defaultGateway
            runCatching {
                NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                    referenceContext = referenceContext,
                    routeDecision = routeDecision,
                    executionPlan = executionPlan,
                    onDelta = { partial -> _state.update { it.copy(streamingReply = partial) } },
                )
            }.onSuccess { turn ->
'''
new = '''            val routedSelection = executionPlan.primaryTask?.let(routingSession::selection)
            val primaryGateway = routedSelection?.gateway ?: routingSession.defaultGateway
            val canFallbackToDefault = routedSelection?.inheritedGlobal == false
            runCatching {
                var emittedContent = false
                suspend fun runReply(gateway: AiGateway): ConversationTurn = NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                    referenceContext = referenceContext,
                    routeDecision = routeDecision,
                    executionPlan = executionPlan,
                    onDelta = { partial ->
                        if (partial.isNotBlank()) emittedContent = true
                        _state.update { it.copy(streamingReply = partial) }
                    },
                )

                try {
                    runReply(primaryGateway)
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    // A stale/unsupported task override must not make creation chat appear dead.
                    // Retry only before the model emitted anything, so a paid/visible partial turn
                    // is never replayed or duplicated.
                    if (!canFallbackToDefault || emittedContent) throw error
                    emitRun(RunStage.CREATION_CHAT, RunStatus.RUNNING, "任务模型未响应，回退全局默认模型")
                    _state.update {
                        it.copy(
                            streamingReply = "",
                            busyLabel = "任务模型未响应，正在切回全局默认模型……",
                        )
                    }
                    runReply(routingSession.defaultGateway)
                }
            }.onSuccess { turn ->
'''
replace_once(chat, old, new)

build = "app/build.gradle.kts"
replace_once(build, '        versionCode = 102\n        versionName = "0.28.0-alpha23-reader-exact-viewport"\n',
             '        versionCode = 103\n        versionName = "0.28.0-alpha24-reader-chat-hotfix"\n')

test = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderChatAlpha24ContractTest.kt")
test.write_text('''package com.xiguli.langhuan.ui\n\nimport java.io.File\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass ReaderChatAlpha24ContractTest {\n    private fun source(path: String): String = File(path).readText()\n\n    @Test\n    fun pagedReaderHonorsAllTypeControls() {\n        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")\n        assertTrue(reader.contains("val pagedParagraphSpacing = paragraphSpacing"))\n        assertFalse(reader.contains("else 0f\\n    val pagination"))\n        assertTrue(reader.contains("lineFactor - .10f"))\n        assertTrue(reader.contains("lineFactor + .10f"))\n        assertTrue(reader.contains("sidePadding - 4f"))\n        assertTrue(reader.contains("sidePadding + 4f"))\n        assertTrue(reader.contains("coerceAtLeast(12f)"))\n        assertTrue(reader.contains("coerceAtMost(48f)"))\n    }\n\n    @Test\n    fun creationChatFallsBackOnlyBeforeAnyVisibleOutput() {\n        val chat = source("src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")\n        assertTrue(chat.contains("val canFallbackToDefault = routedSelection?.inheritedGlobal == false"))\n        assertTrue(chat.contains("if (!canFallbackToDefault || emittedContent) throw error"))\n        assertTrue(chat.contains("runReply(routingSession.defaultGateway)"))\n        assertTrue(chat.contains("任务模型未响应，正在切回全局默认模型"))\n    }\n}\n''')

# Keep the feature branch clean after the one-shot application workflow commits the result.
Path(".github/scripts/apply_alpha24_reader_chat_hotfix.py").unlink(missing_ok=True)
Path(".github/workflows/apply-alpha24-reader-chat-hotfix.yml").unlink(missing_ok=True)
