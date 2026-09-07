from pathlib import Path


def one(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: expected source not found")
    return text.replace(old, new, 1)


# New-book conversation: reliable failed-turn retry, operation-aware retry, and user cancellation.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
s = p.read_text()

s = one(
    s,
    'private const val RESEARCH_CONTEXT_MARKER = "\\n\\n【琅嬛联网检索资料（隐藏上下文）】"\n',
    'private const val RESEARCH_CONTEXT_MARKER = "\\n\\n【琅嬛联网检索资料（隐藏上下文）】"\n'
    'private const val INTERRUPTED_REPLY_MARKER = "\\n\\n（连接中断，已保留已返回内容；重试时会替换这段未完成回复。）"\n'
    'private const val STOPPED_REPLY_MARKER = "\\n\\n（已停止生成，已保留已返回内容；重试时会替换这段未完成回复。）"\n',
    "interrupted reply markers",
)

s = one(
    s,
    'data class NewBookConversationState(\n',
    'enum class CreationRetryTarget { CHAT, PROPOSAL, BLUEPRINT }\n\n'
    'data class NewBookConversationState(\n',
    "retry target enum",
)

s = one(
    s,
    '    val error: String? = null,\n    val selectedReferenceTemplateIds: List<String> = emptyList(),\n',
    '    val error: String? = null,\n'
    '    val retryTarget: CreationRetryTarget? = null,\n'
    '    val canCancelCurrentOperation: Boolean = false,\n'
    '    val selectedReferenceTemplateIds: List<String> = emptyList(),\n',
    "state retry and cancel fields",
)

s = one(
    s,
    '    private var activeProviderId: String? = null\n    private var foundationJob: kotlinx.coroutines.Job? = null\n',
    '    private var activeProviderId: String? = null\n'
    '    private var conversationJob: kotlinx.coroutines.Job? = null\n'
    '    private var proposalJob: kotlinx.coroutines.Job? = null\n'
    '    private var foundationJob: kotlinx.coroutines.Job? = null\n',
    "operation jobs",
)

old_retry = '''        val retryMessage = before.messages.lastOrNull()?.takeIf { message ->
            before.error != null &&
                message.role == "user" &&
                before.pendingAttachments.isEmpty() &&
                message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim() == userText.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        }
        val turnAttachments = retryMessage?.attachments ?: before.pendingAttachments
        val history = if (retryMessage != null) before.messages
        else before.messages + CreationChatMessage("user", userText, turnAttachments)
'''
new_retry = '''        val normalizedUserText = userText.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        val failedRetryIndex = if (before.error != null && before.pendingAttachments.isEmpty()) {
            before.messages.indexOfLast { message ->
                message.role == "user" &&
                    message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim() == normalizedUserText
            }
        } else -1
        val failedRetryMessage = before.messages.getOrNull(failedRetryIndex)
        val trailingFailedTurnMessages = if (failedRetryIndex >= 0) before.messages.drop(failedRetryIndex + 1) else emptyList()
        val retryMessage = failedRetryMessage?.takeIf {
            trailingFailedTurnMessages.all(::isProvisionalInterruptedReply)
        }
        val turnAttachments = retryMessage?.attachments ?: before.pendingAttachments
        val history = if (retryMessage != null) before.messages.take(failedRetryIndex + 1)
        else before.messages + CreationChatMessage("user", userText, turnAttachments)
'''
s = one(s, old_retry, new_retry, "logical failed-turn retry")

s = one(
    s,
    '                error = null,\n            )\n        }\n\n        viewModelScope.launch {\n            val routingSession = runCatching { taskModelRouter.snapshot() }.getOrElse { error ->\n',
    '                error = null,\n'
    '                retryTarget = null,\n'
    '                canCancelCurrentOperation = true,\n'
    '            )\n'
    '        }\n\n'
    '        val job = viewModelScope.launch {\n'
    '            val routingSession = try {\n'
    '                taskModelRouter.snapshot()\n'
    '            } catch (cancelled: kotlinx.coroutines.CancellationException) {\n'
    '                finishCancelledConversation(routeDecision, null)\n'
    '                return@launch\n'
    '            } catch (error: Throwable) {\n',
    "chat job and routing cancellation",
)

s = one(
    s,
    '                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),\n                        error = error.message?.takeIf(String::isNotBlank) ?: "请先在设置里添加并启用一个 AI 服务",\n',
    '                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),\n'
    '                        retryTarget = CreationRetryTarget.CHAT,\n'
    '                        canCancelCurrentOperation = false,\n'
    '                        error = error.message?.takeIf(String::isNotBlank) ?: "请先在设置里添加并启用一个 AI 服务",\n',
    "routing failure state",
)

s = one(
    s,
    '                }\n                return@launch\n            }\n            val skillSnapshot = writingSkillStore.snapshot()\n',
    '                }\n'
    '                return@launch\n'
    '            }\n'
    '            val skillSnapshot = writingSkillStore.snapshot()\n',
    "routing catch close",
)

s = one(
    s,
    '                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.SUCCESS),\n                    )\n',
    '                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.SUCCESS),\n'
    '                        retryTarget = null,\n'
    '                        canCancelCurrentOperation = false,\n'
    '                    )\n',
    "chat success state",
)

old_failure_start = '''            }.onFailure { error ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "${routeDecision.intent.label} · ${error.message.orEmpty()}")
                val partialReply = _state.value.streamingReply.trim()
'''
new_failure_start = '''            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    finishCancelledConversation(routeDecision, executionPlan)
                    return@onFailure
                }
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "${routeDecision.intent.label} · ${error.message.orEmpty()}")
                val partialReply = _state.value.streamingReply.trim()
'''
s = one(s, old_failure_start, new_failure_start, "chat cancellation handling")

s = one(
    s,
    '                            "$partialReply\\n\\n（连接中断，已保留已返回内容；继续发送要求即可从这里往下接。）",\n',
    '                            partialReply + INTERRUPTED_REPLY_MARKER,\n',
    "partial marker",
)

s = one(
    s,
    '                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.FAILED),\n                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),\n',
    '                        lastExecutionPlan = executionPlan.copy(status = NovelRouteStatus.FAILED),\n'
    '                        retryTarget = CreationRetryTarget.CHAT,\n'
    '                        canCancelCurrentOperation = false,\n'
    '                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),\n',
    "chat failure target",
)

send_end = '''            }
        }
    }

    fun addConversationAttachments'''
send_end_new = '''            }
        }
        conversationJob = job
        job.invokeOnCompletion { if (conversationJob === job) conversationJob = null }
    }

    fun addConversationAttachments'''
s = one(s, send_end, send_end_new, "chat job registration")

# Proposal sync becomes cancellable and retry-aware.
sync_start = '''        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) { _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }; return@launch }
            val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders() ?: defaultProposal()
            _state.update { it.copy(isBusy = true, busyLabel = "正在把当前会谈整理为建书方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "合并用户最新决定，不自动生成蓝图")), error = null) }
'''
sync_new = '''        val job = viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) { _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务", retryTarget = CreationRetryTarget.PROPOSAL, canCancelCurrentOperation = false) }; return@launch }
            val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders() ?: defaultProposal()
            _state.update { it.copy(isBusy = true, busyLabel = "正在把当前会谈整理为建书方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "合并用户最新决定，不自动生成蓝图")), error = null, retryTarget = null, canCancelCurrentOperation = true) }
'''
s = one(s, sync_start, sync_new, "proposal job start")

s = one(
    s,
    '                    _state.update { it.copy(proposal = proposal.sanitizePlaceholders(), blueprintDirty = before.foundation != null, isBusy = false, busyLabel = "", error = null) }\n',
    '                    _state.update { it.copy(proposal = proposal.sanitizePlaceholders(), blueprintDirty = before.foundation != null, isBusy = false, busyLabel = "", error = null, retryTarget = null, canCancelCurrentOperation = false) }\n',
    "proposal success",
)

old_sync_failure = '''                .onFailure { error ->
                    emitRun(RunStage.PROPOSAL_SYNC, RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "整理当前方案失败")) }
                }
        }
    }

    fun generateFoundation'''
new_sync_failure = '''                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        emitRun(RunStage.PROPOSAL_SYNC, RunStatus.FAILED, "已由用户停止整理方案")
                        _state.update { it.copy(isBusy = false, busyLabel = "", error = null, retryTarget = null, canCancelCurrentOperation = false) }
                        return@onFailure
                    }
                    emitRun(RunStage.PROPOSAL_SYNC, RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "整理当前方案失败"), retryTarget = CreationRetryTarget.PROPOSAL, canCancelCurrentOperation = false) }
                }
        }
        proposalJob = job
        job.invokeOnCompletion { if (proposalJob === job) proposalJob = null }
    }

    fun generateFoundation'''
s = one(s, old_sync_failure, new_sync_failure, "proposal cancellation and registration")

# Blueprint: expose cancellation/retry correctly and keep checkpoint semantics.
s = one(
    s,
    '_state.update { it.copy(proposal = baseline, isBusy = true, blueprintDirty = before.blueprintDirty, busyLabel = "正在把整段会谈的最新决定合并为最终方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "先把会谈最新决定锁成蓝图输入")), error = null) }',
    '_state.update { it.copy(proposal = baseline, isBusy = true, blueprintDirty = before.blueprintDirty, busyLabel = "正在把整段会谈的最新决定合并为最终方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "先把会谈最新决定锁成蓝图输入")), error = null, retryTarget = null, canCancelCurrentOperation = true) }',
    "blueprint cancellable start",
)

s = one(
    s,
    'foundationStage = inferFoundationStage(cleanFoundation).coerceAtLeast(1), blueprintDirty = false, messages = it.messages + CreationChatMessage("assistant", "当前有效蓝图已经保存。核心蓝图完成后即可正式建书；章纲或伏笔没补完也不会再把整本书锁死。"), isBusy = false, busyLabel = "") }\n',
    'foundationStage = inferFoundationStage(cleanFoundation).coerceAtLeast(1), blueprintDirty = false, messages = it.messages + CreationChatMessage("assistant", "当前有效蓝图已经保存。核心蓝图完成后即可正式建书；章纲或伏笔没补完也不会再把整本书锁死。"), isBusy = false, busyLabel = "", retryTarget = null, canCancelCurrentOperation = false) }\n',
    "blueprint success",
)

old_blue_fail = '''            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    emitRun(blueprintRunStage((_state.value.foundationStage + 1).coerceIn(1, 3)), RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败")) }
                }
            }
'''
new_blue_fail = '''            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    emitRun(blueprintRunStage((_state.value.foundationStage + 1).coerceIn(1, 3)), RunStatus.FAILED, "已由用户停止，现有蓝图断点已保留")
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = null, retryTarget = null, canCancelCurrentOperation = false) }
                } else {
                    emitRun(blueprintRunStage((_state.value.foundationStage + 1).coerceIn(1, 3)), RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败"), retryTarget = CreationRetryTarget.BLUEPRINT, canCancelCurrentOperation = false) }
                }
            }
'''
s = one(s, old_blue_fail, new_blue_fail, "blueprint cancel and retry target")

s = one(
    s,
    '_state.update { it.copy(isBusy = false, busyLabel = "", error = null) }\n            snapshot = _state.value\n',
    '_state.update { it.copy(isBusy = false, busyLabel = "", error = null, retryTarget = null, canCancelCurrentOperation = false) }\n            snapshot = _state.value\n',
    "create from running checkpoint cancellation",
)

# Formal create is local project materialization, not a cancellable model request.
s = one(
    s,
    '_state.update { it.copy(foundation = foundation, proposal = foundation.toProposal(), foundationStage = stage, blueprintDirty = false, isBusy = true, runEvents = listOf(RunEvent(RunStage.CREATE_BOOK, RunStatus.RUNNING, "把已确认核心蓝图写入正式项目结构")), busyLabel = if (stage < 3) "正在用当前有效核心蓝图建书；未完成的章纲/伏笔可稍后补齐……" else "正在把蓝图写入小说圣经、三级大纲和长期记忆……", error = null) }',
    '_state.update { it.copy(foundation = foundation, proposal = foundation.toProposal(), foundationStage = stage, blueprintDirty = false, isBusy = true, runEvents = listOf(RunEvent(RunStage.CREATE_BOOK, RunStatus.RUNNING, "把已确认核心蓝图写入正式项目结构")), busyLabel = if (stage < 3) "正在用当前有效核心蓝图建书；未完成的章纲/伏笔可稍后补齐……" else "正在把蓝图写入小说圣经、三级大纲和长期记忆……", error = null, retryTarget = null, canCancelCurrentOperation = false) }',
    "formal create state",
)

# Replace the chat-only retry API with operation-aware retry and explicit cancellation.
old_retry_method = '''    fun retryLastTurn() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isLoadingAttachments || snapshot.error == null) return
        val lastUser = snapshot.messages.lastOrNull { it.role == "user" } ?: return
        val text = lastUser.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        if (text.isNotBlank()) send(text)
    }

    fun reset()'''
new_retry_method = '''    fun retryFailedOperation() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isLoadingAttachments || snapshot.error == null) return
        when (snapshot.retryTarget) {
            CreationRetryTarget.CHAT -> {
                val lastUser = snapshot.messages.lastOrNull { it.role == "user" } ?: return
                val text = lastUser.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
                if (text.isNotBlank()) send(text)
            }
            CreationRetryTarget.PROPOSAL -> syncConversationProposal()
            CreationRetryTarget.BLUEPRINT -> generateFoundation(regenerate = snapshot.blueprintDirty || snapshot.foundationStage >= 3)
            null -> Unit
        }
    }

    fun retryLastTurn() = retryFailedOperation()

    fun cancelCurrentAiOperation() {
        val snapshot = _state.value
        if (!snapshot.isBusy || !snapshot.canCancelCurrentOperation) return
        _state.update { it.copy(busyLabel = "正在停止当前生成……") }
        when {
            conversationJob?.isActive == true -> conversationJob?.cancel()
            proposalJob?.isActive == true -> proposalJob?.cancel()
            foundationJob?.isActive == true -> foundationJob?.cancel()
            else -> _state.update { it.copy(isBusy = false, busyLabel = "", canCancelCurrentOperation = false, retryTarget = null) }
        }
    }

    private fun finishCancelledConversation(
        routeDecision: NovelRouteDecision,
        executionPlan: NovelSkillExecutionPlan?,
    ) {
        val partialReply = _state.value.streamingReply.trim()
        emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "已由用户停止本轮生成")
        _state.update {
            it.copy(
                messages = if (partialReply.isBlank()) it.messages else it.messages + CreationChatMessage("assistant", partialReply + STOPPED_REPLY_MARKER),
                isBusy = false,
                busyLabel = "",
                streamingReply = "",
                lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),
                lastExecutionPlan = executionPlan?.copy(status = NovelRouteStatus.FAILED) ?: it.lastExecutionPlan,
                error = null,
                retryTarget = null,
                canCancelCurrentOperation = false,
            )
        }
    }

    fun reset()'''
s = one(s, old_retry_method, new_retry_method, "operation-aware retry API")

# Add provisional-reply helper outside the ViewModel.
insert_before = 'private fun conversationPromptMessages(messages: List<CreationChatMessage>): List<PromptMessage> {'
helper = '''private fun isProvisionalInterruptedReply(message: CreationChatMessage): Boolean =
    message.role == "assistant" &&
        (message.text.endsWith(INTERRUPTED_REPLY_MARKER) || message.text.endsWith(STOPPED_REPLY_MARKER))

'''
s = one(s, insert_before, helper + insert_before, "provisional reply helper")

# Improve nested timeout/disconnect classification.
old_err = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val localSocketTimeout = error is java.net.SocketTimeoutException || error.cause is java.net.SocketTimeoutException
    val timeoutText = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return when {
        localSocketTimeout -> "$fallback：等待模型返回超过当前网络容错时间。当前会谈与蓝图断点已保留，可直接重试；连续出现时请切换更稳定的模型或中转站。"
        timeoutText -> "$fallback：AI 服务或中转站返回了超时/断开：${message.take(260)}"
'''
new_err = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val causes = generateSequence<Throwable?>(error) { it.cause }.filterNotNull().take(8).toList()
    val localSocketTimeout = causes.any { it is java.net.SocketTimeoutException }
    val timeoutText = causes.any { cause ->
        val text = cause.message.orEmpty()
        text.contains("timed out", true) || text.contains("timeout", true) || text.contains("超时")
    }
    val disconnectText = causes.any { cause ->
        val text = cause.message.orEmpty()
        listOf("connection reset", "broken pipe", "unexpected end", "eof", "stream was reset", "连接重置", "连接断开").any { text.contains(it, true) }
    }
    return when {
        localSocketTimeout -> "$fallback：等待模型返回超过当前网络容错时间。当前会谈与蓝图断点已保留，可直接重试；连续出现时请切换更稳定的模型或中转站。"
        timeoutText || disconnectText -> "$fallback：AI 服务、中转站或当前网络连接中断：${message.take(260)}"
'''
s = one(s, old_err, new_err, "nested timeout classification")

p.write_text(s)

# Creation UI: retry the actual failed operation and expose a real stop button.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
s = p.read_text()

s = one(
    s,
    '''                        CreationErrorPanelV4(
                            error = error,
                            onRetry = viewModel::retryLastTurn,
                            onConfigureAi = onConfigureAi,
                        )''',
    '''                        CreationErrorPanelV4(
                            error = error,
                            retryTarget = state.retryTarget,
                            onRetry = viewModel::retryFailedOperation,
                            onConfigureAi = onConfigureAi,
                        )''',
    "error panel retry target",
)

s = one(
    s,
    '                onCreate = viewModel::createCurrentFoundation,\n',
    '                onCreate = viewModel::createCurrentFoundation,\n                onCancelCurrent = viewModel::cancelCurrentAiOperation,\n',
    "composer cancel callback",
)

s = one(
    s,
    '''private fun CreationErrorPanelV4(
    error: String,
    onRetry: () -> Unit,
    onConfigureAi: () -> Unit,
) {''',
    '''private fun CreationErrorPanelV4(
    error: String,
    retryTarget: CreationRetryTarget?,
    onRetry: () -> Unit,
    onConfigureAi: () -> Unit,
) {''',
    "error panel signature",
)

old_error_buttons = '''            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
'''
new_error_buttons = '''            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (retryTarget != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (retryTarget) {
                                CreationRetryTarget.CHAT -> "重试这一轮"
                                CreationRetryTarget.PROPOSAL -> "重新整理"
                                CreationRetryTarget.BLUEPRINT -> "继续蓝图"
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onConfigureAi,
                    modifier = Modifier.weight(1f),
'''
s = one(s, old_error_buttons, new_error_buttons, "operation-aware error actions")

s = one(
    s,
    '    onCreate: () -> Unit,\n) {\n',
    '    onCreate: () -> Unit,\n    onCancelCurrent: () -> Unit,\n) {\n',
    "composer cancel parameter",
)

old_send = '''                    val canSend = !busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(t.radiusSm),
                        color = if (canSend) t.foreground else t.muted,
                        contentColor = if (canSend) t.primaryForeground else t.mutedForeground,
                    ) {
                        Box(
                            Modifier.clickable(enabled = canSend, onClick = onSend),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                "发送",
                                Modifier.size(19.dp),
                                tint = if (canSend) t.primaryForeground else t.mutedForeground,
                            )
                        }
                    }
'''
new_send = '''                    val canSend = !busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())
                    val canStop = state.isBusy && state.canCancelCurrentOperation
                    val actionEnabled = canSend || canStop
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(t.radiusSm),
                        color = when {
                            canStop -> t.destructive
                            canSend -> t.foreground
                            else -> t.muted
                        },
                        contentColor = if (actionEnabled) t.primaryForeground else t.mutedForeground,
                    ) {
                        Box(
                            Modifier.clickable(
                                enabled = actionEnabled,
                                onClick = if (canStop) onCancelCurrent else onSend,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (canStop) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                if (canStop) "停止生成" else "发送",
                                Modifier.size(19.dp),
                                tint = if (actionEnabled) t.primaryForeground else t.mutedForeground,
                            )
                        }
                    }
'''
s = one(s, old_send, new_send, "stop button")
p.write_text(s)

# Progressive blueprint comment must match actual finite transport tolerance.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/ProgressiveFoundationEngine.kt")
s = p.read_text()
s = one(
    s,
    '''    /**
     * 不设置任何 App 侧生成时限。不同模型/中转站的思考耗时差异很大，
     * 只允许用户主动取消，琅嬛不再因为固定秒数擅自终止正常请求。
     */
''',
    '''    /**
     * 蓝图阶段不再叠加短倒计时；实际网络层保留较长但有限的容错窗口，避免坏连接永久挂死。
     * 每个已完成阶段都会保存检查点，用户也可以主动停止并从断点继续。
     */
''',
    "truthful blueprint timeout comment",
)
p.write_text(s)

# Version bump.
p = Path("app/build.gradle.kts")
s = p.read_text()
s = one(s, "versionCode = 97", "versionCode = 98", "version code")
s = one(s, 'versionName = "0.28.0-alpha18-reader-integration"', 'versionName = "0.28.0-alpha19-creation-reliability"', "version name")
p.write_text(s)

# Regression contract: the failure UI must retry the failed operation, not blindly duplicate chat.
p = Path("app/src/test/java/com/xiguli/langhuan/ui/CreationReliabilityAlpha19ContractTest.kt")
p.write_text('''package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationReliabilityAlpha19ContractTest {
    @Test
    fun interruptedChatRetryReusesLogicalUserTurn() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt").readText()

        assertTrue(source.contains("failedRetryIndex"))
        assertTrue(source.contains("trailingFailedTurnMessages.all(::isProvisionalInterruptedReply)"))
        assertTrue(source.contains("before.messages.take(failedRetryIndex + 1)"))
        assertTrue(source.contains("INTERRUPTED_REPLY_MARKER"))
        assertTrue(source.contains("STOPPED_REPLY_MARKER"))
    }

    @Test
    fun retryTargetsChatProposalOrBlueprintAndLongWaitCanBeStopped() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val vm = File(root, "src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt").readText()
        val ui = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()

        assertTrue(vm.contains("enum class CreationRetryTarget { CHAT, PROPOSAL, BLUEPRINT }"))
        assertTrue(vm.contains("fun retryFailedOperation()"))
        assertTrue(vm.contains("CreationRetryTarget.BLUEPRINT -> generateFoundation"))
        assertTrue(vm.contains("fun cancelCurrentAiOperation()"))
        assertTrue(vm.contains("conversationJob?.cancel()"))
        assertTrue(vm.contains("foundationJob?.cancel()"))
        assertTrue(ui.contains("retryTarget = state.retryTarget"))
        assertTrue(ui.contains("Icons.Rounded.Stop"))
        assertTrue(ui.contains("onCancelCurrent = viewModel::cancelCurrentAiOperation"))
    }

    @Test
    fun blueprintDocumentationNoLongerClaimsInfiniteAppWait() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val blueprint = File(root, "src/main/java/com/xiguli/langhuan/ui/ProgressiveFoundationEngine.kt").readText()
        assertFalse(blueprint.contains("不设置任何 App 侧生成时限"))
        assertTrue(blueprint.contains("较长但有限的容错窗口"))
    }
}
''')
