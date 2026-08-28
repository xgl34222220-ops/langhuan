package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.engine.CreationResearchArchive
import com.xiguli.langhuan.engine.CreationResearchArchiveStore
import com.xiguli.langhuan.engine.CreationResearchBundle
import com.xiguli.langhuan.engine.ReferenceResearchGroup
import com.xiguli.langhuan.engine.ResearchFallbackEngine
import com.xiguli.langhuan.engine.WebResearchEngine
import com.xiguli.langhuan.engine.WebResearchSource
import kotlinx.coroutines.launch

private const val RESEARCH_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"
private const val MAX_RESEARCH_EVIDENCE_CHARS = 4_200

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResearchNewBookConversationPage(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val archiveStore = remember(context) { CreationResearchArchiveStore(context) }
    var archiveState by remember { mutableStateOf(archiveStore.load()) }
    val research = remember { WebResearchEngine().also { archiveStore.seed(it, archiveState) } }
    val fallbackResearch = remember { ResearchFallbackEngine() }
    var input by remember { mutableStateOf("") }
    var researching by remember { mutableStateOf(false) }
    var lastSources by remember { mutableStateOf<List<WebResearchSource>>(emptyList()) }
    var lastTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var researchMessage by remember { mutableStateOf<String?>(null) }
    var lastSubmitted by remember { mutableStateOf("") }
    var retryFoundation by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addConversationAttachments(uris)
    }

    LaunchedEffect(state.createdStoryId) {
        state.createdStoryId?.let { id ->
            viewModel.consumeCreatedStory()
            onCreated(id)
        }
    }

    fun submit(raw: String) {
        val text = raw.trim().ifBlank {
            if (state.pendingAttachments.isNotEmpty()) defaultAttachmentInstruction(state.pendingAttachments) else ""
        }
        if (text.isBlank() || state.isBusy || researching || state.isLoadingAttachments) return
        input = ""
        lastSubmitted = text
        retryFoundation = false
        if (!research.shouldResearch(text)) {
            lastSources = emptyList()
            lastTargets = emptyList()
            researchMessage = null
            val archived = archiveStore.contextFor(emptyList(), archiveState, maxChars = 3_600)
            viewModel.send(buildResearchCarryPrompt(text, archived))
            return
        }

        researching = true
        val detected = research.referenceTargets(text)
        lastTargets = detected
        researchMessage = if (detected.size > 1) {
            "正在研究 ${detected.size} 个参考对象：先复用长期档案，再补作者、作品、主角、能力、主题和剧情线索……"
        } else {
            "正在研究参考对象：先查本机长期档案，再补缺失的公开资料……"
        }

        scope.launch {
            val primary = runCatching {
                research.researchForCreation(text, preResolvedTargets = detected)
            }.getOrNull()
            var bundle = primary

            if (bundle?.hasSources != true) {
                val fallbackTargets = primary?.groups?.map { it.target }.orEmpty().ifEmpty { detected }
                val fallback = runCatching {
                    fallbackResearch.supplement(text, fallbackTargets)
                }.getOrNull()
                if (fallback?.hasSources == true) {
                    bundle = if (primary != null && primary.groups.size == fallback.groups.size) {
                        CreationResearchBundle(
                            originalText = text,
                            groups = primary.groups.zip(fallback.groups).map { (oldGroup, newGroup) ->
                                ReferenceResearchGroup(oldGroup.target, newGroup.result)
                            },
                        )
                    } else {
                        fallback
                    }
                }
            }

            val sources = bundle?.sources.orEmpty()
            val targets = bundle?.groups?.map { it.target }.orEmpty().ifEmpty { detected }
            archiveState = archiveStore.merge(bundle, detected)
            archiveStore.seed(research, archiveState)
            lastSources = sources
            lastTargets = targets
            researching = false

            val archived = archiveStore.contextFor(targets, archiveState)
            val hidden = buildResearchPrompt(text, bundle, archived)
            if (sources.isEmpty()) {
                researchMessage = if (archived.isBlank()) {
                    "这轮两个公开搜索入口都没有拿到新证据；这只代表网页补搜失败。AI 仍会使用自身已有知识和你明确提供的作者/作品关系继续分析。"
                } else {
                    "本轮没有新增可靠网页，但长期研究档案仍在；不会再把一次搜索失败误判成‘这个作者/作品完全不知道’。"
                }
            } else {
                val deep = bundle?.deepReadCount ?: 0
                researchMessage = if (targets.size > 1) {
                    "新增 ${sources.size} 条公开线索、深读 $deep 个页面；已并入 ${archiveState.entries.size} 个长期研究档案。"
                } else {
                    "新增 ${sources.size} 条公开线索、深读 $deep 个页面；主角、能力、主题、剧情与规则会从累计档案继续交叉核对。"
                }
            }
            viewModel.send(hidden)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("和 AI 聊出一本小说", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                researching -> "正在补全长期研究档案"
                                state.foundation != null && state.blueprintDirty -> "继续正常聊天 · 当前蓝图有新要求待同步"
                                state.foundation != null -> "继续正常聊天修改，满意后正式建书"
                                state.proposal != null -> "方案已成形，下一步搭世界、人物和三级大纲"
                                archiveState.entries.isNotEmpty() -> "已记住 ${archiveState.entries.size} 个作者 / 作品研究档案"
                                else -> "可查作品/作者，也可融合多本小说的高层设定方法"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.reset()
                            research.resetContext()
                            archiveState = archiveStore.clearSessionContext()
                            lastSources = emptyList()
                            lastTargets = emptyList()
                            researchMessage = null
                            retryFoundation = false
                        },
                        enabled = !state.isBusy && !researching,
                    ) { Icon(Icons.Rounded.Refresh, "重新开始") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pendingAttachments.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.pendingAttachments.forEach { attachment ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removePendingAttachment(attachment.id) },
                                    label = { Text(attachment.fileName, maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description,
                                            null,
                                        )
                                    },
                                    trailingIcon = { Icon(Icons.Rounded.Close, "移除附件") },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(
                            onClick = {
                                attachmentLauncher.launch(
                                    arrayOf(
                                        "text/*", "application/json", "application/pdf", "application/epub+zip",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*",
                                    )
                                )
                            },
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Icon(Icons.Rounded.AttachFile, "上传文件")
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (state.foundation == null) "说要求，或上传小说资料、PDF、图片……" else "继续纠正要求，也可以追加文件……") },
                            minLines = 1,
                            maxLines = 5,
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { submit(input) },
                            enabled = (input.isNotBlank() || state.pendingAttachments.isNotEmpty()) &&
                                !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Icon(if (researching) Icons.Rounded.TravelExplore else Icons.Rounded.Send, "发送")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (state.messages.size <= 1) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("直接说你的想法，也可以让我先查资料", style = MaterialTheme.typography.labelLarge)
                    ResearchStarterChip("我想写一本中式悬疑，主角是普通人", ::submit)
                    ResearchStarterChip("搜一下薄情书生的小说，再提炼能借鉴的高层设定", ::submit)
                    ResearchStarterChip("融合《迷雾之上》《十日终焉》《诡舍》的部分优点，但角色、规则和主线全部原创", ::submit)
                }
            }
            item { ReferenceTemplateSelectionPanel(viewModel) }
            if (archiveState.entries.isNotEmpty()) item { ResearchArchiveMemoryCard(archiveState) }
            items(state.messages) { message -> ResearchChatBubble(message) }
            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }

            if (state.foundation == null) {
                state.proposal?.let { proposal ->
                    item {
                        ResearchProposalCard(proposal, state.isBusy || researching) {
                            retryFoundation = true
                            viewModel.generateFoundation(false)
                        }
                    }
                }
            } else {
                item {
                    ResearchFoundationCard(
                        state.foundation!!,
                        state.isBusy || researching,
                        outOfSync = state.blueprintDirty,
                        pendingProposal = state.proposal,
                        onSync = {
                            retryFoundation = true
                            viewModel.generateFoundation(false)
                        },
                        onRegenerate = {
                            retryFoundation = true
                            viewModel.generateFoundation(true)
                        },
                        onCreate = viewModel::createCurrentFoundation,
                    )
                }
            }

            if (state.isBusy || researching || state.isLoadingAttachments) item {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            state.isLoadingAttachments -> "正在读取附件……"
                            researching -> "正在补全作品研究档案……"
                            else -> state.busyLabel.ifBlank { "AI 正在处理……" }
                        }
                    )
                }
            }
            state.error?.let { error -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(friendlyResearchError(error), color = MaterialTheme.colorScheme.onErrorContainer)
                        if (!state.isBusy && !researching && retryFoundation && state.proposal != null) {
                            OutlinedButton(onClick = { viewModel.generateFoundation(state.foundation != null) }) {
                                Icon(Icons.Rounded.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.foundation == null) "重试生成蓝图" else "重试重构蓝图")
                            }
                        } else if (lastSubmitted.isNotBlank() && !state.isBusy && !researching) {
                            OutlinedButton(onClick = { submit(lastSubmitted) }) {
                                Icon(Icons.Rounded.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("重试上一句")
                            }
                        }
                    }
                }
            } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun buildResearchCarryPrompt(text: String, archivedContext: String): String {
    if (archivedContext.isBlank()) return text
    return buildString {
        append(text)
        append(RESEARCH_MARKER)
        appendLine()
        appendLine("本轮没有必要重复联网。下面是琅嬛此前已经保存的长期研究档案；请把它当作本次会谈的研究记忆继续使用。")
        appendLine("如果用户说‘他/这本/前面那几本/这种写法’，优先按档案中的当前作者和作品承接，不要重新猜代词指向。")
        appendLine("用户在会话中明确提供或纠正的作者-作品关系属于用户提供的项目事实。网页未核验只能标‘待网页核验’，不能把它当作无效信息反复否定。")
        appendLine("回答主角、能力、主题、世界规则、剧情或风格时，先用档案已有证据 + 你本来可靠的知识；只有具体事实确实没有支撑时才单独说该细节未核实。")
        appendLine(archivedContext)
    }
}

private fun buildResearchPrompt(
    text: String,
    bundle: CreationResearchBundle?,
    archivedContext: String,
): String = buildString {
    append(text)
    append(RESEARCH_MARKER)
    appendLine()
    appendLine("这是琅嬛 App 的创作研究上下文。联网结果只是‘新增证据’，不是一次性答案；此前查过的作者/作品保存在长期研究档案里。")
    appendLine("本轮目标优先级：先确定用户真正指的是哪个作者/作品，再核对作者归属、代表作、主角、能力、主题、剧情、世界规则和公开评价，最后提炼可借鉴机制。")
    val resolvedTargets = bundle?.groups.orEmpty().map { it.target }.filter(String::isNotBlank)
    if (resolvedTargets.isNotEmpty()) {
        appendLine("【本轮已解析指代】参考对象明确为：${resolvedTargets.joinToString("、")}。用户本轮说的‘他们/它们/这两本’只能指这些对象，禁止把代词本身当作作品名。")
    }
    appendLine("用户在当前或历史会话中明确说‘这些书是某作者写的’、纠正作者或作品归属时，把它作为用户提供的项目事实继续推理。若网页暂时找不到，只标‘用户提供，待网页核验’，绝对不能反复说‘没有作者绑定硬证据所以无法分析’。")
    appendLine("本轮没有新网页证据时，只能说‘本轮没有新增证据’，不能清空长期档案，也不能把模型原本知道的内容清空。")
    appendLine("网页主要负责核对公开事实；具体章节不是每轮都必须拿到。你拥有自己的训练知识，对本来就有把握的高层事实可以和档案合并，但不得捏造具体章节、原句、数字或不确定人物。")
    appendLine("绝对不要回答‘我不能联网/不能直接搜索/请用户自己提供链接’。联网动作已经由 App 完成。")
    appendLine("同一参考对象已经有长期档案时，新旧证据冲突要指出冲突并降低对应事实置信度，而不是整部作品重新归零。")
    appendLine("置信度表达：网页/长期档案直接支持的写‘公开资料可确认’；用户明确提供的关系写‘用户提供，待/已核验’；多线索 + 既有知识归纳写‘大致判断/高层印象’；只有具体细节真的拿不准才单独标‘细节未核实’。")

    if (archivedContext.isNotBlank()) {
        appendLine()
        appendLine(archivedContext)
    }

    if (bundle == null || !bundle.hasSources) {
        appendLine("本次网页没有新增足够线索。这只代表本轮补搜失败，不代表长期档案失效，更不代表你必须停止分析。")
        appendLine("如果档案、用户明确提供的信息或你已有知识对该作者/作品已有稳定认识，就继续给出题材、主角、核心体验、能力/限制、规则、主题、剧情走向、叙事节奏和可借鉴机制；真正缺的字段单独标未核实。")
    } else {
        appendLine("【本轮新增公开证据】")
        appendLine(compactResearchEvidence(bundle))
    }

    appendLine()
    appendLine("【作品档案回答顺序】")
    appendLine("用户问某一本作品时，优先按：作者与书名归属 → 大致故事起点 → 主角与核心目标 → 主角关键能力/限制 → 世界或规则机制 → 主要冲突/剧情走向 → 主题 → 叙事/节奏/氛围 → 可借鉴的高层机制 → 置信度。")
    appendLine("用户只说‘看看这个作者其他小说/看个大致/有什么能融合’时，可以对多本作品做粗读，不要求逐章考据；但不能只列书名，至少说明每本已知的题材、阅读体验和能借鉴什么。")
    appendLine("用户追问‘主角能力/性格/主题/具体讲什么’时，视为深挖需求：先查长期档案和本轮证据，再回答具体项，不能退回泛泛的‘规则清晰、氛围不错’。")
    appendLine("不要反复强调‘没有可靠具体剧情所以不装懂’。真正缺的只标缺的字段，其余已经能判断的字段照常回答。")

    appendLine()
    appendLine("【多作品融合工作法】")
    appendLine("为每个参考对象抽取参考基因：世界/规则机制、核心谜团或冲突类型、信息释放与反转、人物关系张力、节奏升级、叙事视角/距离、氛围体验。")
    appendLine("融合时分成 KEEP=保留高层机制，TRANSFORM=原创化改造，AVOID=容易构成照搬的标志性人物/专名/独特剧情骨架。")
    appendLine("最终必须重新设计角色、专名、世界规则、核心谜团、因果链和结局方向；不要复制原作句子，也不要只给原作角色换名字。")
    appendLine("用户已经明确要融合时，不要退回问卷；先给一版有内容的融合方向，再最多追问1个真正影响路线的问题。")
}

private fun compactResearchEvidence(bundle: CreationResearchBundle): String {
    val out = buildString {
        bundle.groups.forEach { group ->
            appendLine("【${group.target}】")
            group.result.sources.take(6).forEachIndexed { index, source ->
                appendLine("- ${source.title}")
                if (source.snippet.isNotBlank()) appendLine("  摘要：${source.snippet.take(280)}")
                if (index < 3 && source.detail.isNotBlank()) appendLine("  页面线索：${source.detail.take(460)}")
            }
        }
    }
    return out.take(MAX_RESEARCH_EVIDENCE_CHARS)
}

private fun friendlyResearchError(raw: String): String {
    val value = raw.trim()
    val lower = value.lowercase()
    return when {
        "timeout" in lower || "timed out" in lower || "sockettimeoutexception" in lower ->
            "AI 服务请求超时：当前阶段没有在规定时间内返回。会谈、方案和长期研究档案都不会丢，可直接重试当前阶段。"
        "429" in lower || "rate limit" in lower || "too many requests" in lower ->
            "AI 服务当前限流（429）。稍后重试即可，当前会谈内容不会丢失。"
        "502" in lower || "503" in lower || "504" in lower ->
            "AI 中转站暂时不可用或上游响应过慢（${value.take(120)}）。当前状态已保留，可直接重试当前阶段。"
        else -> value.ifBlank { "AI 请求失败，当前状态已保留，可以直接重试。" }
    }
}

@Composable
private fun ResearchArchiveMemoryCard(archive: CreationResearchArchive) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null)
                Spacer(Modifier.width(6.dp))
                Text("长期研究记忆", fontWeight = FontWeight.SemiBold)
            }
            archive.lastAuthorTarget?.let { Text("当前作者：$it", style = MaterialTheme.typography.bodySmall) }
            if (archive.lastWorkTargets.isNotEmpty()) {
                Text("当前作品：${archive.lastWorkTargets.take(5).joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "已归档 ${archive.entries.size} 个作者/作品；退出 App 后仍保留。本轮搜索失败不会再把以前的资料清零。",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable private fun ResearchStarterChip(text: String, onClick: (String) -> Unit) {
    AssistChip(onClick = { onClick(text) }, label = { Text(text) }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) })
}

@Composable private fun ResearchChatBubble(message: CreationChatMessage) {
    val user = message.role == "user"
    val display = if (user) message.text.substringBefore(RESEARCH_MARKER) else message.text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (user) "你" else "琅嬛 AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp)); Text(display, style = MaterialTheme.typography.bodyLarge)
                message.attachments.forEach { attachment ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description,
                                null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(attachment.fileName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (attachment.extractedText.isNotBlank()) "已读取文本内容" else "已作为原生附件发送",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ResearchStatusCard(message: String, targets: List<String>, sources: List<WebResearchSource>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TravelExplore, null); Spacer(Modifier.width(6.dp)); Text(message, fontWeight = FontWeight.SemiBold)
            }
            if (targets.isNotEmpty()) {
                Text("参考对象：${targets.joinToString(" · ")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            sources.take(3).forEach { source ->
                Text("• ${source.title}${if (source.detail.isNotBlank()) " · 已深读" else ""}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (sources.size > 3) Text("另有 ${sources.size - 3} 条线索已归档，不在对话里展开。", style = MaterialTheme.typography.labelSmall)
            if (sources.isNotEmpty()) Text("资料已交给 AI 归纳；对话只显示结论，不再铺满搜索摘要。", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun ResearchProposalCard(proposal: NewBookProposal, busy: Boolean, onNext: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("新书方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(proposal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${proposal.genre} · 目标 ${proposal.targetWords / 10_000} 万字")
            Text("简介", fontWeight = FontWeight.SemiBold); Text(proposal.premise)
            Text("核心钩子", fontWeight = FontWeight.SemiBold); Text(proposal.coreHook)
            Text("主题", fontWeight = FontWeight.SemiBold); Text(proposal.theme)
            Button(onClick = onNext, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("下一步：生成建书蓝图") }
        }
    }
}

@Composable private fun ResearchFoundationCard(
    foundation: StoryFoundation,
    busy: Boolean,
    outOfSync: Boolean,
    pendingProposal: NewBookProposal?,
    onSync: () -> Unit,
    onRegenerate: () -> Unit,
    onCreate: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("建书蓝图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(foundation.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${foundation.genre} · ${foundation.targetWords / 10_000} 万字")
            Text("故事承诺", fontWeight = FontWeight.SemiBold); Text(foundation.storyPromise)
            Text("总纲", fontWeight = FontWeight.SemiBold); Text("${foundation.masterObjective}\n${foundation.masterConflict}\n${foundation.masterTurningPoint}")
            Text("核心角色 · ${foundation.characters.size} 人", fontWeight = FontWeight.SemiBold)
            foundation.characters.take(8).forEach { Text("${it.name}｜目标：${it.goal}", style = MaterialTheme.typography.bodyMedium) }
            Text("分卷 · ${foundation.volumes.size} 卷 / 第一卷 ${foundation.volumes.firstOrNull()?.chapters?.size ?: 0} 章详细章纲", fontWeight = FontWeight.SemiBold)
            Text("伏笔 · ${foundation.foreshadowing.size} 条 / 圣经 · ${foundation.bible.size} 条", style = MaterialTheme.typography.bodyMedium)
            if (outOfSync) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("你刚才在聊天里提出了新要求。这里暂时显示上一次同步的蓝图；聊天不会被打断，也不会每说一句就强制重跑整套蓝图。")
                        pendingProposal?.let { proposal ->
                            Text("聊天中的当前方案", fontWeight = FontWeight.SemiBold)
                            Text(proposal.title, style = MaterialTheme.typography.titleMedium)
                            Text(proposal.premise, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = onSync, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Sync, null)
                            Spacer(Modifier.width(6.dp))
                            Text("同步当前聊天到蓝图")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRegenerate, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Refresh, null); Text("整套重做") }
                Button(onClick = onCreate, enabled = !busy && !outOfSync, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.CheckCircle, null); Text("正式建书") }
            }
        }
    }
}
