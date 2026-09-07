from pathlib import Path
import re

# 1) Provider wire compatibility -------------------------------------------------
gateway = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
s = gateway.read_text()

stream_pattern = re.compile(
    r'    private suspend fun streamOpenAi\(prompt: PromptBundle, azure: Boolean, onDelta: \(String\) -> Unit\): String \{.*?\n    \}\n\n    private suspend fun streamAnthropic',
    re.S,
)
new_stream = '''    private suspend fun streamOpenAi(prompt: PromptBundle, azure: Boolean, onDelta: (String) -> Unit): String {
        val endpoint = if (azure) azureChatEndpoint(config.baseUrl, config.model) else openAiChatEndpoint(config.baseUrl)
        val protocol = if (azure) ApiProtocol.AZURE_OPENAI else ApiProtocol.OPENAI_COMPATIBLE
        val buffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        streamHttp(endpoint, authHeaders(protocol, config.apiKey), openAiBody(prompt, stream = true, azure = azure).toString(), AI_STREAM_IDLE_TIMEOUT_MS) { line ->
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") return@streamHttp
            val root = runCatching { WireJson.parseToJsonElement(data) as? JsonObject }.getOrNull()
            if (root == null) {
                if (!data.startsWith("{") && !data.startsWith("[")) appendDelta(buffer, data, onDelta, prompt.jsonMode)
                return@streamHttp
            }
            val delta = openAiStreamVisibleText(root)
            val reasoning = openAiStreamReasoningText(root)
            if (!reasoning.isNullOrBlank()) reasoningBuffer.append(reasoning)
            appendDelta(buffer, delta, onDelta, prompt.jsonMode)
        }
        val visible = buffer.toString().trim()
        if (visible.isNotBlank()) return visible
        val recovered = recoverReasoningOnlyText(reasoningBuffer.toString())
        if (recovered.isNotBlank()) {
            onDelta(recovered)
            return recovered
        }
        error("AI 返回了成功流，但没有可读文本字段")
    }

    private suspend fun streamAnthropic'''
s, n = stream_pattern.subn(new_stream, s, count=1)
assert n == 1, 'streamOpenAi block not found'

extract_pattern = re.compile(
    r'    private fun extractText\(protocol: ApiProtocol, body: String\): String \{.*?\n    \}\n\n    private fun decodeChapter',
    re.S,
)
new_extract = '''    private fun extractText(protocol: ApiProtocol, body: String): String = extractProviderText(protocol, body)

    private fun decodeChapter'''
s, n = extract_pattern.subn(new_extract, s, count=1)
assert n == 1, 'extractText block not found'

http_marker = 'private suspend fun http(url: String, method: String, headers: Map<String, String>, body: String? = null, readTimeoutMs: Int = READ_TIMEOUT_MS): HttpResult ='
assert http_marker in s, 'http marker not found'
helpers = r'''/**
 * Decode provider payloads without assuming every OpenAI-compatible relay returns exactly
 * choices[0].message.content as a primitive string. Reasoning relays commonly use content arrays,
 * reasoning_content, Responses API output blocks, choices.text, or SSE-wrapped JSON.
 */
internal fun extractProviderText(protocol: ApiProtocol, body: String): String {
    val raw = body.trim()
    if (raw.isBlank()) error("AI 返回了空响应")

    val sse = extractSseProviderText(protocol, raw)
    if (sse.isNotBlank()) return sse

    val parsed = runCatching { WireJson.parseToJsonElement(raw) }.getOrNull()
    if (parsed != null) {
        val visible = providerVisibleText(protocol, parsed).trim()
        if (visible.isNotBlank()) return visible
        val reasoning = providerReasoningText(parsed).trim()
        if (reasoning.isNotBlank()) return recoverReasoningOnlyText(reasoning)
        error("AI 已返回成功响应，但没有找到可读文本字段（已兼容 content/text/output_text/reasoning_content/Responses API）")
    }

    // Some lightweight relays return the model text directly rather than a JSON envelope.
    if (!raw.startsWith("<") && !raw.startsWith("{") && !raw.startsWith("[")) return raw
    error("AI 返回了无法解析的响应格式")
}

private fun extractSseProviderText(protocol: ApiProtocol, raw: String): String {
    if (raw.lineSequence().none { it.trimStart().startsWith("data:") }) return ""
    val visible = StringBuilder()
    val reasoning = StringBuilder()
    raw.lineSequence().forEach { source ->
        val line = source.trim()
        if (!line.startsWith("data:")) return@forEach
        val data = line.removePrefix("data:").trim()
        if (data.isBlank() || data == "[DONE]") return@forEach
        val element = runCatching { WireJson.parseToJsonElement(data) }.getOrNull()
        if (element == null) {
            if (!data.startsWith("{") && !data.startsWith("[")) visible.append(data)
        } else {
            val chunk = providerVisibleText(protocol, element)
            if (chunk.isNotBlank()) visible.append(chunk)
            val thought = providerReasoningText(element)
            if (thought.isNotBlank()) reasoning.append(thought)
        }
    }
    return visible.toString().trim().ifBlank { recoverReasoningOnlyText(reasoning.toString()) }
}

private fun providerVisibleText(protocol: ApiProtocol, element: JsonElement): String {
    if (element is JsonArray) return element.joinToString("") { providerVisibleText(protocol, it) }
    val root = element as? JsonObject ?: return element.textPayload().orEmpty()
    return when (protocol) {
        ApiProtocol.ANTHROPIC -> root["content"].textPayload().orEmpty()
        ApiProtocol.GEMINI -> root["candidates"].asObjects().joinToString("") { candidate ->
            (candidate["content"] as? JsonObject)?.get("parts").textPayload().orEmpty()
        }
        ApiProtocol.OLLAMA -> (root["message"] as? JsonObject)?.get("content").textPayload()
            ?: root["response"].textPayload()
            ?: ""
        else -> openAiVisibleText(root)
    }
}

private fun openAiVisibleText(root: JsonObject): String {
    val choices = root["choices"].asObjects()
    val choiceText = choices.joinToString("") { choice ->
        val message = choice["message"] as? JsonObject
        val delta = choice["delta"] as? JsonObject
        message?.get("content").textPayload()
            ?: delta?.get("content").textPayload()
            ?: choice["text"].textPayload()
            ?: ""
    }
    if (choiceText.isNotBlank()) return choiceText
    root["output_text"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    root["output"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    if (root.string("type")?.contains("output_text.delta", ignoreCase = true) == true) {
        root["delta"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    }
    root["content"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    return ""
}

private fun providerReasoningText(element: JsonElement): String {
    if (element is JsonArray) return element.joinToString("") { providerReasoningText(it) }
    val root = element as? JsonObject ?: return ""
    val choices = root["choices"].asObjects()
    val fromChoices = choices.joinToString("") { choice ->
        val message = choice["message"] as? JsonObject
        val delta = choice["delta"] as? JsonObject
        message?.get("reasoning_content").textPayload()
            ?: message?.get("reasoning").textPayload()
            ?: delta?.get("reasoning_content").textPayload()
            ?: delta?.get("reasoning").textPayload()
            ?: ""
    }
    if (fromChoices.isNotBlank()) return fromChoices
    return root["reasoning_content"].textPayload()
        ?: root["reasoning"].textPayload()
        ?: root["thinking"].textPayload()
        ?: ""
}

private fun openAiStreamVisibleText(root: JsonObject): String {
    val choice = root["choices"].asObjects().firstOrNull()
    val message = choice?.get("message") as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    return delta?.get("content").textPayload()
        ?: message?.get("content").textPayload()
        ?: choice?.get("text").textPayload()
        ?: if (root.string("type")?.contains("output_text.delta", ignoreCase = true) == true) root["delta"].textPayload() else null
        ?: root["output_text"].textPayload()
        ?: root["output"].textPayload()
        ?: ""
}

private fun openAiStreamReasoningText(root: JsonObject): String? {
    val choice = root["choices"].asObjects().firstOrNull()
    val message = choice?.get("message") as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    return delta?.get("reasoning_content").textPayload()
        ?: delta?.get("reasoning").textPayload()
        ?: message?.get("reasoning_content").textPayload()
        ?: message?.get("reasoning").textPayload()
        ?: root["reasoning_content"].textPayload()
        ?: root["reasoning"].textPayload()
}

private fun JsonElement?.textPayload(): String? = when (this) {
    null -> null
    is JsonPrimitive -> contentOrNull
    is JsonArray -> mapNotNull { it.textPayload() }.joinToString("").takeIf(String::isNotBlank)
    is JsonObject -> listOf("text", "content", "output_text", "value")
        .firstNotNullOfOrNull { key -> this[key].textPayload()?.takeIf(String::isNotBlank) }
    else -> null
}

private fun recoverReasoningOnlyText(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    val afterThink = value.substringAfterLast("</think>", missingDelimiterValue = "").trim()
    if (afterThink.isNotBlank()) return afterThink
    val stripped = value.replace(Regex("(?is)<think>.*?</think>"), "").trim()
    if (stripped.isNotBlank()) return stripped
    return value
}
'''
s = s.replace(http_marker, helpers + '\n\n' + http_marker, 1)
gateway.write_text(s)

# 2) Creation retry + error categorization --------------------------------------
chat = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
c = chat.read_text()
reset_marker = '    fun reset() { suppressDraftPersistence = false; draftStore.clear(); _state.value = NewBookConversationState() }\n'
assert reset_marker in c, 'reset marker not found'
retry_block = '''    fun retryLastTurn() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isLoadingAttachments || snapshot.error == null) return
        val lastUser = snapshot.messages.lastOrNull { it.role == "user" } ?: return
        val text = lastUser.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        if (text.isNotBlank()) send(text)
    }

'''
c = c.replace(reset_marker, retry_block + reset_marker, 1)
old_error_tail = '''        timeoutText -> "$fallback：AI 服务或中转站返回了超时/断开：${message.take(260)}"
        else -> message.ifBlank { fallback }
'''
new_error_tail = '''        timeoutText -> "$fallback：AI 服务或中转站返回了超时/断开：${message.take(260)}"
        message.contains("没有找到可读文本字段") || message.contains("无法解析的响应格式") || message.contains("成功流，但没有可读文本字段") ->
            "$fallback：模型接口已经连通，但这一轮没有给出可读正文。琅嬛已兼容 content、content 数组、text、output_text、reasoning_content 和 Responses API；可直接点“重试上一轮”，连续出现再切换模型。"
        else -> message.ifBlank { fallback }
'''
assert old_error_tail in c, 'friendly error tail not found'
c = c.replace(old_error_tail, new_error_tail, 1)
chat.write_text(c)

# 3) Active creation spatial UI --------------------------------------------------
ui = Path('app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt')
u = ui.read_text()
anchor = 'import com.xiguli.langhuan.ui.design.LanghuanMotionStatus\n'
assert anchor in u
u = u.replace(anchor, anchor + 'import com.xiguli.langhuan.ui.design.LanghuanAmbientBackdrop\nimport com.xiguli.langhuan.ui.design.LanghuanConstellationField\nimport com.xiguli.langhuan.ui.design.LanghuanGlassPanel\n', 1)

open_shell = '    Surface(Modifier.fillMaxSize(), color = t.background) {\n        Column(Modifier.fillMaxSize()) {\n'
new_shell = '''    Surface(Modifier.fillMaxSize(), color = t.background) {
        Box(Modifier.fillMaxSize()) {
            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = state.isBusy)
            LanghuanConstellationField(Modifier.fillMaxSize(), active = state.isBusy)
            Column(Modifier.fillMaxSize()) {
'''
assert open_shell in u, 'creation shell open not found'
u = u.replace(open_shell, new_shell, 1)
close_marker = '        }\n    }\n}\n\n@Composable\nprivate fun CreationHeaderV4'
new_close = '            }\n        }\n    }\n}\n\n@Composable\nprivate fun CreationHeaderV4'
assert close_marker in u, 'creation shell close not found'
u = u.replace(close_marker, new_close, 1)

error_pattern = re.compile(r'''                state\.error\?\.let \{ error ->\n                    item \{\n                        Surface\(.*?\n                    \}\n                \}\n''', re.S)
new_error = '''                state.error?.let { error ->
                    item {
                        CreationErrorPanelV4(
                            error = error,
                            onRetry = viewModel::retryLastTurn,
                            onConfigureAi = onConfigureAi,
                        )
                    }
                }
'''
u, n = error_pattern.subn(new_error, u, count=1)
assert n == 1, 'error panel block not found'

composer_anchor = '@Composable\nprivate fun CreationComposerV4(\n'
assert composer_anchor in u
error_component = '''@Composable
private fun CreationErrorPanelV4(
    error: String,
    onRetry: () -> Unit,
    onConfigureAi: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        radius = 20.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                LanghuanOrb(active = false, size = 26.dp)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("这一轮没有正常完成", style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text(error, modifier = Modifier.padding(top = 3.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusMd),
                    colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重试上一轮")
                }
                OutlinedButton(
                    onClick = onConfigureAi,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("切换模型")
                }
            }
        }
    }
}

'''
u = u.replace(composer_anchor, error_component + composer_anchor, 1)

old_card = '''            LanghuanCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 5.dp,
            ) {
'''
new_card = '''            LanghuanGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(5.dp),
                radius = 20.dp,
            ) {
'''
assert old_card in u, 'composer card not found'
u = u.replace(old_card, new_card, 1)
ui.write_text(u)

# Route trace gets a persistent spatial status orb.
route = Path('app/src/main/java/com/xiguli/langhuan/ui/NovelRouteTraceCard.kt')
r = route.read_text()
route_import = 'import com.xiguli.langhuan.engine.NovelSkillExecutionPlan\n'
assert route_import in r
r = r.replace(route_import, route_import + 'import com.xiguli.langhuan.ui.design.LanghuanOrb\n', 1)
old_icon = '''            Icon(
                Icons.Rounded.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
'''
new_icon = '''            LanghuanOrb(
                size = 28.dp,
                active = effectiveStatus == NovelRouteStatus.RUNNING,
            )
'''
assert old_icon in r, 'route icon not found'
r = r.replace(old_icon, new_icon, 1)
r = r.replace('MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .36f)', 'MaterialTheme.colorScheme.surface.copy(alpha = .78f)', 1)
route.write_text(r)

# Reference DNA gets the spatial hero treatment.
ref = Path('app/src/main/java/com/xiguli/langhuan/ui/ReferenceTemplateSelectionPanel.kt')
q = ref.read_text()
ref_import = 'import com.xiguli.langhuan.ui.design.LanghuanCard\n'
assert ref_import in q
q = q.replace(ref_import, ref_import + 'import com.xiguli.langhuan.ui.design.LanghuanOrb\nimport com.xiguli.langhuan.ui.design.LanghuanSpatialHero\n', 1)
card_start = '    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {\n        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {\n'
hero_start = '''    LanghuanSpatialHero(
        title = "Reference DNA",
        subtitle = when {
            loading -> "正在校验内置参考库……"
            selectedReports.isEmpty() -> "共 ${reports.size} 本参考 · 本次创作尚未绑定"
            selectedReports.size == 1 -> "已绑定《${selectedReports.first().title}》 · $totalSearchable 条可检索 DNA"
            else -> "已绑定 ${selectedReports.size} 本 · $totalSearchable 条可检索 DNA"
        },
        eyebrow = "REFERENCE CONSTELLATION",
        modifier = Modifier.fillMaxWidth(),
        trailing = {
            LanghuanOrb(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                size = 44.dp,
                active = loading,
            )
        },
    ) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
'''
assert card_start in q, 'reference card start not found'
q = q.replace(card_start, hero_start, 1)
ref.write_text(q)

# 4) Regression tests ------------------------------------------------------------
test = Path('app/src/test/java/com/xiguli/langhuan/engine/UniversalAiWireCompatibilityTest.kt')
test.write_text(r'''package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAiWireCompatibilityTest {
    @Test fun readsClassicOpenAiContent() {
        val body = """{"choices":[{"message":{"content":"正常回答"}}]}"""
        assertEquals("正常回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsContentArrayUsedByCompatibleRelays() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"数组回答"}]}}]}"""
        assertEquals("数组回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun recoversReasoningOnlyDeepSeekRelay() {
        val body = """{"choices":[{"message":{"content":"","reasoning_content":"可恢复回答"}}]}"""
        assertEquals("可恢复回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsLegacyChoiceText() {
        val body = """{"choices":[{"text":"legacy text"}]}"""
        assertEquals("legacy text", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsResponsesApiOutputBlocks() {
        val body = """{"output":[{"content":[{"type":"output_text","text":"responses text"}]}]}"""
        assertEquals("responses text", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsSseAndPrefersVisibleContentOverReasoning() {
        val body = """
            data: {"choices":[{"delta":{"reasoning_content":"内部推演"}}]}
            data: {"choices":[{"delta":{"content":"最终回答"}}]}
            data: [DONE]
        """.trimIndent()
        assertEquals("最终回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun preservesPlainTextRelayResponse() {
        assertEquals("直接返回的文本", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, "直接返回的文本"))
    }

    @Test fun stillRejectsHtmlErrorPage() {
        val result = runCatching { extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, "<html>bad gateway</html>") }
        assertTrue(result.isFailure)
    }
}
''')

build = Path('app/build.gradle.kts')
b = build.read_text()
b = re.sub(r'versionCode = \d+', 'versionCode = 96', b, count=1)
b = re.sub(r'versionName = "[^"]+"', 'versionName = "0.28.0-alpha17-wirefix-spatial2"', b, count=1)
build.write_text(b)

# One-shot machinery should not survive the real commit.
Path('.github/workflows/apply-wire-spatial2.yml').unlink(missing_ok=True)
Path('tools/apply_wire_spatial2.py').unlink(missing_ok=True)
