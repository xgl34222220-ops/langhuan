from pathlib import Path
import re

# Universal AI transport: keep short discovery timeouts, but allow slow generation jobs more time.
gateway = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
s = gateway.read_text()

pattern = re.compile(
    r'\s{12}if \(error is SocketTimeoutException \|\| error\.cause is SocketTimeoutException\) \{\n'
    r'\s{16}throw IllegalStateException\("AI 服务长时间没有返回数据，本次请求已停止；请重试或切换模型/中转站。", error\)\n'
    r'\s{12}\}\n'
)
s, n = pattern.subn('', s, count=1)
assert n == 1, 'stream timeout early-abort block not found'
s = s.replace('import java.net.SocketTimeoutException\n', '')

old = 'return requireSuccess(http(endpoint, "POST", authHeaders(protocol, config.apiKey), body.toString()))'
assert old in s
s = s.replace(old, 'return requireSuccess(http(endpoint, "POST", authHeaders(protocol, config.apiKey), body.toString(), AI_RESPONSE_TIMEOUT_MS))', 1)
old = 'return requireSuccess(http(anthropicMessagesEndpoint(config.baseUrl), "POST", authHeaders(ApiProtocol.ANTHROPIC, config.apiKey), body.toString()))'
assert old in s
s = s.replace(old, 'return requireSuccess(http(anthropicMessagesEndpoint(config.baseUrl), "POST", authHeaders(ApiProtocol.ANTHROPIC, config.apiKey), body.toString(), AI_RESPONSE_TIMEOUT_MS))', 1)

old = '                geminiBody(prompt).toString(),\n            )'
assert old in s
s = s.replace(old, '                geminiBody(prompt).toString(),\n                AI_RESPONSE_TIMEOUT_MS,\n            )', 1)
old = '                ollamaBody(prompt, stream = false).toString(),\n            )'
assert old in s
s = s.replace(old, '                ollamaBody(prompt, stream = false).toString(),\n                AI_RESPONSE_TIMEOUT_MS,\n            )', 1)

old = 'streamHttp(endpoint, authHeaders(protocol, config.apiKey), openAiBody(prompt, stream = true, azure = azure).toString()) { line ->'
assert old in s
s = s.replace(old, 'streamHttp(endpoint, authHeaders(protocol, config.apiKey), openAiBody(prompt, stream = true, azure = azure).toString(), AI_STREAM_IDLE_TIMEOUT_MS) { line ->', 1)
old = '            anthropicBody(prompt, stream = true).toString(),\n        ) { line ->'
assert old in s
s = s.replace(old, '            anthropicBody(prompt, stream = true).toString(),\n            AI_STREAM_IDLE_TIMEOUT_MS,\n        ) { line ->', 1)
old = '            geminiBody(prompt).toString(),\n        ) { line ->'
assert old in s
s = s.replace(old, '            geminiBody(prompt).toString(),\n            AI_STREAM_IDLE_TIMEOUT_MS,\n        ) { line ->', 1)
old = 'streamHttp(ollamaChatEndpoint(config.baseUrl), emptyMap(), ollamaBody(prompt, stream = true).toString()) { line ->'
assert old in s
s = s.replace(old, 'streamHttp(ollamaChatEndpoint(config.baseUrl), emptyMap(), ollamaBody(prompt, stream = true).toString(), AI_STREAM_IDLE_TIMEOUT_MS) { line ->', 1)

old = 'private suspend fun http(url: String, method: String, headers: Map<String, String>, body: String? = null): HttpResult =\n'
assert old in s
s = s.replace(old, 'private suspend fun http(url: String, method: String, headers: Map<String, String>, body: String? = null, readTimeoutMs: Int = READ_TIMEOUT_MS): HttpResult =\n', 1)
assert s.count('connection.readTimeout = READ_TIMEOUT_MS') == 1
s = s.replace('connection.readTimeout = READ_TIMEOUT_MS', 'connection.readTimeout = readTimeoutMs', 1)

old = '    body: String,\n    onLine: (String) -> Unit,\n) = runInterruptible(Dispatchers.IO) {'
assert old in s
s = s.replace(old, '    body: String,\n    readTimeoutMs: Int = STREAM_IDLE_TIMEOUT_MS,\n    onLine: (String) -> Unit,\n) = runInterruptible(Dispatchers.IO) {', 1)
assert s.count('connection.readTimeout = STREAM_IDLE_TIMEOUT_MS') == 1
s = s.replace('connection.readTimeout = STREAM_IDLE_TIMEOUT_MS', 'connection.readTimeout = readTimeoutMs', 1)

old = 'private const val CONNECT_TIMEOUT_MS = 20_000\nprivate const val READ_TIMEOUT_MS = 90_000\nprivate const val STREAM_IDLE_TIMEOUT_MS = 120_000\n'
assert old in s
s = s.replace(
    old,
    old + '// Slow reasoning models need a wider per-request transport window. Chapter generation still has its own outer deadlines.\nprivate const val AI_RESPONSE_TIMEOUT_MS = 300_000\nprivate const val AI_STREAM_IDLE_TIMEOUT_MS = 300_000\n',
    1,
)
gateway.write_text(s)

# Conversation retry: don't duplicate a failed user turn, preserve partial streamed output, and report timeout source truthfully.
chat = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
s = chat.read_text()
old = '''        val userText = clean.ifBlank { defaultAttachmentInstruction(before.pendingAttachments) }
        val history = before.messages + CreationChatMessage("user", userText, before.pendingAttachments)
'''
new = '''        val userText = clean.ifBlank { defaultAttachmentInstruction(before.pendingAttachments) }
        val retryMessage = before.messages.lastOrNull()?.takeIf { message ->
            before.error != null &&
                message.role == "user" &&
                before.pendingAttachments.isEmpty() &&
                message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim() == userText.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        }
        val turnAttachments = retryMessage?.attachments ?: before.pendingAttachments
        val history = if (retryMessage != null) before.messages
        else before.messages + CreationChatMessage("user", userText, turnAttachments)
'''
assert old in s
s = s.replace(old, new, 1)
old = 'attachmentPurposes = before.pendingAttachments.map(::attachmentPurpose),'
assert old in s
s = s.replace(old, 'attachmentPurposes = turnAttachments.map(::attachmentPurpose),', 1)

old = '''            }.onFailure { error ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "${routeDecision.intent.label} · ${error.message.orEmpty()}")
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),
                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.FAILED),
                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),
                    )
                }
            }
'''
new = '''            }.onFailure { error ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "${routeDecision.intent.label} · ${error.message.orEmpty()}")
                val partialReply = _state.value.streamingReply.trim()
                _state.update {
                    it.copy(
                        messages = if (partialReply.isBlank()) it.messages else it.messages + CreationChatMessage(
                            "assistant",
                            "$partialReply\\n\\n（连接中断，已保留已返回内容；继续发送要求即可从这里往下接。）",
                        ),
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),
                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.FAILED),
                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),
                    )
                }
            }
'''
assert old in s
s = s.replace(old, new, 1)

old = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty(); val timeout = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return if (timeout) "$fallback：AI 服务或中转站主动返回了超时/断开。琅嬛本身没有设置生成倒计时，也没有因为等待时间过长主动终止请求。" else message.ifBlank { fallback }
}
'''
new = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val localSocketTimeout = error is java.net.SocketTimeoutException || error.cause is java.net.SocketTimeoutException
    val timeoutText = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return when {
        localSocketTimeout -> "$fallback：等待模型返回超过当前网络容错时间。当前会谈与蓝图断点已保留，可直接重试；连续出现时请切换更稳定的模型或中转站。"
        timeoutText -> "$fallback：AI 服务或中转站返回了超时/断开：${message.take(260)}"
        else -> message.ifBlank { fallback }
    }
}
'''
assert old in s
s = s.replace(old, new, 1)
chat.write_text(s)

# Blueprint core stages must not silently swallow network failures and continue into malformed data.
foundation = Path('app/src/main/java/com/xiguli/langhuan/ui/ProgressiveFoundationEngine.kt')
s = foundation.read_text()
old = '''    private suspend fun requestOptional(stage: String, prompt: PromptBundle): GeneratedChapter? = try {
        gateway.generate(prompt)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
'''
new = '''    private suspend fun requestOptional(stage: String, prompt: PromptBundle): GeneratedChapter? = try {
        gateway.generate(prompt)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        if (stage.startsWith("1/3")) {
            throw IllegalStateException("$stage 请求失败：${error.message.orEmpty()}", error)
        }
        null
    }
'''
assert old in s
s = s.replace(old, new, 1)
foundation.write_text(s)

build = Path('app/build.gradle.kts')
s = build.read_text()
s = re.sub(r'versionCode = \d+', 'versionCode = 94', s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "0.28.0-alpha15-creation-hotfix"', s, count=1)
build.write_text(s)
