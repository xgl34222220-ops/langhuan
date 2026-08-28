from pathlib import Path
import re

# 1) PromptBundle: support real multi-turn chat and non-JSON requests.
p = Path('app/src/main/java/com/xiguli/langhuan/engine/PromptAssembler.kt')
s = p.read_text()
old = '''data class PromptBundle(
    val system: String,
    val user: String,
    val attachments: List<PromptAttachment> = emptyList(),
)
'''
new = '''data class PromptMessage(
    val role: String,
    val content: String,
)

data class PromptBundle(
    val system: String,
    val user: String,
    val attachments: List<PromptAttachment> = emptyList(),
    val messages: List<PromptMessage> = emptyList(),
    val jsonMode: Boolean = true,
)
'''
assert old in s, 'PromptBundle anchor missing'
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Gateway interface: structured generation stays, plain text chat gets a first-class path.
p = Path('app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt')
s = p.read_text()
old = '''interface AiGateway {
    suspend fun generate(prompt: PromptBundle): GeneratedChapter

    /**
     * 默认兼容旧网关。支持流式协议的网关覆写此方法并持续回传原始文本片段。
     */
'''
new = '''interface AiGateway {
    suspend fun generate(prompt: PromptBundle): GeneratedChapter

    /** Plain text path for normal conversation. Structured tools continue using [generate]. */
    suspend fun generateText(prompt: PromptBundle): String = generate(prompt).content

    /**
     * 默认兼容旧网关。支持流式协议的网关覆写此方法并持续回传原始文本片段。
     */
'''
assert old in s, 'AiGateway anchor missing'
s = s.replace(old, new, 1)
p.write_text(s)

# 3) Universal gateway: raw-text chat + native multi-turn protocol payloads.
p = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
s = p.read_text()
insert_before = '    override suspend fun generateStreaming(\n'
idx = s.index(insert_before)
raw_method = '''    override suspend fun generateText(prompt: PromptBundle): String = withContext(Dispatchers.IO) {
        require(config.baseUrl.isNotBlank()) { "请先配置 API 地址" }
        require(config.model.isNotBlank()) { "请先选择或填写模型" }
        val protocol = resolvedProtocol()
        val response = when (protocol) {
            ApiProtocol.ANTHROPIC -> callAnthropic(prompt)
            ApiProtocol.GEMINI -> callGemini(prompt)
            ApiProtocol.AZURE_OPENAI -> callOpenAi(prompt, azure = true)
            ApiProtocol.OLLAMA -> callOllama(prompt)
            else -> callOpenAi(prompt, azure = false)
        }
        extractText(protocol, response)
    }

'''
assert 'override suspend fun generateText(prompt: PromptBundle)' not in s
s = s[:idx] + raw_method + s[idx:]

start = s.index('    private fun openAiBody(')
end = s.index('    private fun callAnthropic(', start)
openai = '''    private fun openAiBody(prompt: PromptBundle, stream: Boolean, azure: Boolean): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("temperature", config.temperature)
        put("stream", stream)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                val withAttachments = role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()
                add(buildJsonObject {
                    put("role", role)
                    if (!withAttachments) {
                        put("content", turn.content)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                            prompt.attachments.forEach { attachment ->
                                if (attachment.mimeType.startsWith("image/")) {
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                                        })
                                    })
                                } else {
                                    add(buildJsonObject {
                                        put("type", "file")
                                        put("file", buildJsonObject {
                                            put("filename", attachment.fileName)
                                            put("file_data", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                                        })
                                    })
                                }
                            }
                        })
                    }
                })
            }
        })
        if (prompt.jsonMode && config.supportsJsonMode && !azure) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }

'''
s = s[:start] + openai + s[end:]

start = s.index('    private fun anthropicBody(')
end = s.index('    private fun callGemini(', start)
anthropic = '''    private fun anthropicBody(prompt: PromptBundle, stream: Boolean): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("max_tokens", 8192)
        put("temperature", config.temperature)
        put("stream", stream)
        put("system", prompt.system)
        put("messages", buildJsonArray {
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                val withAttachments = role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()
                add(buildJsonObject {
                    put("role", role)
                    if (!withAttachments) {
                        put("content", turn.content)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                            prompt.attachments.forEach { attachment ->
                                val kind = if (attachment.mimeType.startsWith("image/")) "image" else "document"
                                add(buildJsonObject {
                                    put("type", kind)
                                    put("source", buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", attachment.mimeType)
                                        put("data", attachment.base64Data)
                                    })
                                    if (kind == "document") put("title", attachment.fileName)
                                })
                            }
                        })
                    }
                })
            }
        })
    }

'''
s = s[:start] + anthropic + s[end:]

start = s.index('    private fun geminiBody(')
end = s.index('    private fun callOllama(', start)
gemini = '''    private fun geminiBody(prompt: PromptBundle): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("system_instruction", buildJsonObject {
            put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt.system) }) })
        })
        put("contents", buildJsonArray {
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "model" else "user"
                add(buildJsonObject {
                    put("role", role)
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", turn.content) })
                        if (role == "user" && index == turns.lastIndex) {
                            prompt.attachments.forEach { attachment ->
                                add(buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", attachment.mimeType)
                                        put("data", attachment.base64Data)
                                    })
                                })
                            }
                        }
                    })
                })
            }
        })
        put("generationConfig", buildJsonObject {
            put("temperature", config.temperature)
            if (prompt.jsonMode) put("responseMimeType", "application/json")
        })
    }

'''
s = s[:start] + gemini + s[end:]

start = s.index('    private fun ollamaBody(')
end = s.index('    private fun appendDelta(', start)
ollama = '''    private fun ollamaBody(prompt: PromptBundle, stream: Boolean): JsonObject = buildJsonObject {
        require(prompt.attachments.all { it.mimeType.startsWith("image/") }) {
            "当前 Ollama 对话只支持图片附件；PDF 请切换支持文档输入的 OpenAI、Claude 或 Gemini 模型。"
        }
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("stream", stream)
        if (prompt.jsonMode) put("format", "json")
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                add(buildJsonObject {
                    put("role", role)
                    put("content", turn.content)
                    if (role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()) {
                        put("images", buildJsonArray { prompt.attachments.forEach { add(JsonPrimitive(it.base64Data)) } })
                    }
                })
            }
        })
        put("options", buildJsonObject { put("temperature", config.temperature) })
    }

'''
s = s[:start] + ollama + s[end:]
p.write_text(s)

# 4) New-book conversation: normal chat first, tools only on explicit action.
p = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
s = p.read_text()
assert 'import com.xiguli.langhuan.engine.PromptBundle\n' in s
s = s.replace(
    'import com.xiguli.langhuan.engine.PromptBundle\n',
    'import com.xiguli.langhuan.engine.PromptBundle\nimport com.xiguli.langhuan.engine.PromptMessage\n',
    1,
)

# Make any non-question message after a foundation mark the blueprint as needing sync.
old = '''                        blueprintDirty = blueprintDirtyAfterConversation(
                            alreadyDirty = it.blueprintDirty,
                            hasFoundation = before.foundation != null,
                            proposalUpdated = turn.proposal != null,
                        ),
'''
new = '''                        blueprintDirty = it.blueprintDirty || (before.foundation != null && !isQuestionLike(plainInstruction)),
'''
assert old in s, 'blueprintDirty send anchor missing'
s = s.replace(old, new, 1)

# Add explicit proposal sync tool before foundation generation.
marker = '    fun generateFoundation(regenerate: Boolean = false) {\n'
assert marker in s
sync_method = '''    fun syncConversationProposal() {
        val before = _state.value
        if (before.isBusy || before.isLoadingAttachments || before.messages.none { it.role == "user" }) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders()
                ?: NewBookProposal(
                    title = "未命名",
                    genre = "未分类",
                    premise = "尚未整理",
                    theme = DEFAULT_THEME,
                    targetWords = 500_000,
                    coreHook = "待整理",
                    coverBrief = "",
                    rationale = "",
                )
            _state.update { it.copy(isBusy = true, busyLabel = "正在把当前会谈整理为建书方案……", error = null) }
            runCatching {
                ProposalConsolidator(gateway).consolidate(baseline, before.messages)
            }.onSuccess { proposal ->
                _state.update {
                    it.copy(
                        proposal = proposal.sanitizePlaceholders(),
                        blueprintDirty = before.foundation != null,
                        isBusy = false,
                        busyLabel = "",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "整理当前方案失败")) }
            }
        }
    }

'''
s = s.replace(marker, sync_method + marker, 1)

# User files are hard constraints when the explicit blueprint tool runs.
s = s.replace(
    '"以会谈里最后确认的决定为准，重新设计一套明显不同但更自洽的角色、规则、分卷结构和前期章节路线。任何已被用户否定或替换的旧方案都不得复用。"',
    '"以会谈和用户上传作品设定为硬约束，重新整理并补全蓝图。只重做 AI 补充部分；附件明确的人物、规则、势力、卷数、卷序、主线节点和终局不得改动。"',
    1,
)
s = s.replace(
    '"以会谈里最后确认的决定为唯一准绳，把当前最新新书方案扩展成可直接开始长篇写作的完整建书蓝图。旧简介、旧能力、旧冲突若已被后续决定替换，禁止回滚。"',
    '"以会谈最新决定和用户上传作品设定为唯一准绳，把当前方案扩展成可写作蓝图。附件原文是硬约束；AI 只能补缺口，禁止删卷、并卷、把类别当人物或偷换既定规则。"',
    1,
)

# Replace the old JSON-driven chat engine with plain-text native chat.
start = s.index('    suspend fun reply(\n', s.index('private class NewBookConversationEngine'))
end = s.index('    private fun proposalContext(', start)
reply = '''    suspend fun reply(
        messages: List<CreationChatMessage>,
        currentProposal: NewBookProposal? = null,
        referenceContext: String = "",
    ): ConversationTurn {
        val latest = messages.lastOrNull { it.role == "user" }?.text
            ?.substringBefore(RESEARCH_CONTEXT_MARKER)
            ?.trim()
            .orEmpty()
        val hiddenContext = buildString {
            currentProposal?.let {
                appendLine(proposalContext(it))
                appendLine()
            }
            if (referenceContext.isNotBlank()) {
                appendLine("【用户显式选择的参考双层 DNA】")
                appendLine(referenceContext)
            }
        }
        val response = gateway.generateText(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书创作搭档。你的第一职责是像一个正常、可靠的 AI 助手一样理解用户当前这句话并自然回应，而不是把每轮聊天强行变成表格、JSON、方案卡或自动工作流。

                    对话原则：
                    1. 优先回答用户真正问的内容。简单问题简洁回答；复杂设定、长文件分析、剧情推演可以充分展开，不机械限字。
                    2. 认真承接完整多轮上下文。后出现的用户明确决定覆盖旧决定；“他/他们/这本/前面那几本”等指代按最近上下文理解，不把代词当新实体。
                    3. 用户上传的作品设定、世界观、大纲和人物文件属于项目资料。先读文件，再结合其中实际名称、规则、人物和分卷回答。文件明确写出的内容不得擅自改写；你的新增想法必须明确标成建议或待确认。
                    4. 不要因为用户提到“小说、作品、资料、参考、融合”就自行联网。只有页面联网工具明确附带了网页研究上下文时，才把那些资料作为辅助证据。
                    5. 普通聊天不自动生成或修改建书方案，不自动生成蓝图，不自动改简介，不输出内部状态字段，也不要要求用户填表。用户会在满意时主动点“整理当前方案 / 生成蓝图 / 正式建书”。
                    6. 可以主动指出设定漏洞、人物动机问题、规则闭环风险、节奏问题和更好的方案，但必须区分“原文事实”和“你的建议”。不要为了显得专业而反复追问已经明确的信息。
                    7. 参考作品用于讨论时可以正常谈其高层特点；真正创作新书时避免照搬专名、标志性规则和剧情骨架。
                    8. 不要用“如果你愿意我可以……”之类空泛收尾。该分析就分析，该给方案就直接给方案。

                    $hiddenContext
                """.trimIndent(),
                user = latest,
                messages = conversationPromptMessages(messages),
                attachments = messagesPromptAttachments(messages.takeLast(1)),
                jsonMode = false,
            )
        ).trim()
        return ConversationTurn(
            reply = response.ifBlank { "我在。继续按你刚才的设定往下聊。" },
        )
    }

'''
s = s[:start] + reply + s[end:]

# Preserve real user/assistant roles and attachment text instead of flattening all history into one user blob.
marker = 'private fun conversationTranscript(\n'
assert marker in s
helper = '''private fun conversationPromptMessages(messages: List<CreationChatMessage>): List<PromptMessage> {
    val firstUser = messages.indexOfFirst { it.role == "user" }
    if (firstUser < 0) return emptyList()
    val relevant = messages.drop(firstUser)
    val lastUser = relevant.indexOfLast { it.role == "user" }
    return relevant.mapIndexedNotNull { index, message ->
        var text = message.text
        if (message.role == "user" && index != lastUser) {
            text = text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        }
        val attachments = attachmentContext(message.attachments)
        val content = if (attachments.isBlank()) text else "$text\n$attachments"
        content.trim().takeIf { it.isNotBlank() }?.let {
            PromptMessage(
                role = if (message.role == "assistant") "assistant" else "user",
                content = it,
            )
        }
    }
}

'''
s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# 5) UI: proposal syncing is an explicit tool, not an automatic side effect of chat.
p = Path('app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt')
s = p.read_text()
s = s.replace(
    'state.proposal != null -> "方案已成形，下一步搭世界、人物和三级大纲"',
    'state.proposal != null -> "方案已整理 · 可继续聊天，满意后再生成蓝图"',
    1,
)
anchor = '''            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }

            if (state.foundation == null) {
'''
assert anchor in s, 'proposal UI anchor missing'
insert = '''            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }

            if (state.foundation == null && state.messages.any { it.role == "user" }) item {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("建书方案", fontWeight = FontWeight.SemiBold)
                            Text(
                                "普通聊天不会自动改方案。聊满意后再手动整理，避免每句话都被工作流打断。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = viewModel::syncConversationProposal,
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Text(if (state.proposal == null) "整理方案" else "重新同步")
                        }
                    }
                }
            }

            if (state.foundation == null) {
'''
s = s.replace(anchor, insert, 1)
p.write_text(s)

# 6) Version bump: this is an architecture change, not another tiny hotfix.
p = Path('app/build.gradle.kts')
s = p.read_text()
s = re.sub(r'versionCode = \d+', 'versionCode = 48', s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "0.25.0-alpha01"', s, count=1)
p.write_text(s)
