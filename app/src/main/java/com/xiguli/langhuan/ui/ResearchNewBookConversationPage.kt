package com.xiguli.langhuan.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.engine.CreationResearchBundle
import com.xiguli.langhuan.engine.WebResearchEngine
import com.xiguli.langhuan.engine.WebResearchSource
import kotlinx.coroutines.launch

private const val RESEARCH_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchNewBookConversationPage(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val research = remember { WebResearchEngine() }
    var input by remember { mutableStateOf("") }
    var researching by remember { mutableStateOf(false) }
    var lastSources by remember { mutableStateOf<List<WebResearchSource>>(emptyList()) }
    var lastTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var researchMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.createdStoryId) {
        state.createdStoryId?.let { id ->
            viewModel.consumeCreatedStory()
            onCreated(id)
        }
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isBlank() || state.isBusy || researching) return
        input = ""
        if (!research.shouldResearch(text)) {
            lastSources = emptyList()
            lastTargets = emptyList()
            researchMessage = null
            viewModel.send(text)
            return
        }

        researching = true
        val detected = research.referenceTargets(text)
        lastTargets = detected
        researchMessage = if (detected.size > 1) {
            "正在分别检索 ${detected.size} 个参考对象，并深读高相关页面……"
        } else {
            "正在联网检索并深读高相关页面……"
        }

        scope.launch {
            val bundle = runCatching { research.researchForCreation(text) }.getOrNull()
            val sources = bundle?.sources.orEmpty()
            val targets = bundle?.groups?.map { it.target }.orEmpty().ifEmpty { detected }
            lastSources = sources
            lastTargets = targets
            researching = false

            val hidden = buildResearchPrompt(text, bundle)
            if (sources.isEmpty()) {
                researchMessage = "本次网页核验没有拿到可靠资料；AI 仍会先使用自己有把握的既有知识回答，并明确哪些内容未被网页核验。"
            } else {
                val deep = bundle?.deepReadCount ?: 0
                researchMessage = if (targets.size > 1) {
                    "已为 ${targets.size} 个参考对象找到 ${sources.size} 条结果，深读 $deep 个页面；正在合并模型知识与网页证据。"
                } else {
                    "已找到 ${sources.size} 条结果并深读 $deep 个页面；正在整理主角、能力、主题、剧情与世界规则。"
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
                                researching -> "正在联网研究参考作品"
                                state.foundation != null -> "蓝图阶段：继续聊天修改，满意后正式建书"
                                state.proposal != null -> "方案已成形，下一步搭世界、人物和三级大纲"
                                else -> "可查作品/作者，也可融合多本小说的高层设定方法"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                actions = { IconButton(onClick = viewModel::reset, enabled = !state.isBusy && !researching) { Icon(Icons.Rounded.Refresh, "重新开始") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (state.foundation == null) "比如：融合《A》《B》《C》的优点，但做成原创世界……" else "比如：保留A的信息差，删掉B的系统设定……") },
                        minLines = 1,
                        maxLines = 5,
                        enabled = !state.isBusy && !researching,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { submit(input) }, enabled = input.isNotBlank() && !state.isBusy && !researching) {
                        Icon(if (researching) Icons.Rounded.TravelExplore else Icons.Rounded.Send, "发送")
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
            items(state.messages) { message -> ResearchChatBubble(message) }
            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }

            if (state.foundation == null) {
                state.proposal?.let { proposal -> item { ResearchProposalCard(proposal, state.isBusy || researching) { viewModel.generateFoundation(false) } } }
            } else {
                item { ResearchFoundationCard(state.foundation!!, state.isBusy || researching, { viewModel.generateFoundation(true) }, viewModel::createCurrentFoundation) }
            }

            if (state.isBusy || researching) item {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (researching) "正在联网检索并深读作品资料……" else state.busyLabel.ifBlank { "AI 正在处理……" })
                }
            }
            state.error?.let { error -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun buildResearchPrompt(text: String, bundle: CreationResearchBundle?): String = buildString {
    append(text)
    append(RESEARCH_MARKER)
    appendLine()
    appendLine("这是琅嬛 App 本轮执行的公开网页核验与页面深读上下文。它是补充证据，不是你的全部知识来源。")
    appendLine("重要：你拥有自己的训练知识。对于你本来就有把握的作品/作者高层事实，可以直接使用；网页检索用于核验、补充、纠错和更新，绝不能因为网页没搜到就把自己的既有知识清空。")
    appendLine("绝对不要回答‘我不能联网/不能直接搜索/请用户自己提供链接’。联网动作已经由 App 完成。")
    appendLine("回答时区分置信度：被当前网页直接支持的可称‘网页已核验’；来自你既有知识但本轮网页没证实的，应表述为‘按我的既有知识/记忆’，并避免编造具体章节、原句和数字。")

    if (bundle == null || !bundle.hasSources) {
        appendLine("本次网页核验没有返回足够可靠的结果。这只代表‘网页核验失败’，不代表你不知道。")
        appendLine("请先检查自己的既有知识：如果对作品/作者有把握，直接说明你知道的主角、人物性格、能力/机制、世界规则、主题和主线内容，并标明未被本轮网页核验；只有你自己也没把握时，才说具体事实不确定。")
    } else {
        appendLine("下面按参考对象列出搜索摘要与深读页面摘取的信息。不要只复述标题；结合这些证据和你的既有知识，补齐作品档案。来源冲突时明确指出。")
        appendLine(bundle.context)
    }

    appendLine()
    appendLine("【作品档案要求】")
    appendLine("只要用户在问某部作品/作者，或要求参考/融合，就先在回答里尽量整理每个参考作品的高层档案，而不是只说‘能借鉴某种感觉’。至少覆盖：")
    appendLine("1. 作者/作品关系；2. 主角是谁；3. 主角主要性格；4. 主角能力、金手指或核心行动优势；5. 世界观与核心规则；6. 小说主题；7. 主线具体讲什么；8. 核心冲突；9. 叙事与节奏特点；10. 可以借鉴的高层创作机制。")
    appendLine("如果某字段确实不知道，就单独标‘未核实’，不要因为一个字段不确定就把整部作品都说成不知道。")
    appendLine("如果用户问的是作者，先列出你有把握的代表作，再分别给出作品档案；网页结果出现书名时优先用于核验作品归属。")

    appendLine()
    appendLine("【多作品融合工作法】")
    appendLine("为每个参考对象抽取参考基因：世界/规则机制、核心谜团或冲突类型、信息释放与反转、人物关系张力、节奏升级、叙事视角/距离、氛围体验。")
    appendLine("融合时分成 KEEP=保留高层机制，TRANSFORM=原创化改造，AVOID=容易构成照搬的标志性人物/专名/独特剧情骨架。")
    appendLine("最终必须重新设计角色、专名、世界规则、核心谜团、因果链和结局方向；不要复制原作句子，也不要只给原作角色换名字。")
    appendLine("用户已经明确要融合时，不要退回问卷；先给一版有内容的融合方向，再最多追问1个真正影响路线的问题。")
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
            sources.take(6).forEach { source ->
                Text("• ${source.title}${if (source.detail.isNotBlank()) " · 已深读" else ""}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (source.snippet.isNotBlank()) Text(source.snippet, style = MaterialTheme.typography.labelSmall, maxLines = 3)
            }
            if (sources.isNotEmpty()) Text("网页资料负责核验；模型既有知识负责补齐高层作品档案。融合时仍会重做原创人物、规则和主线。", style = MaterialTheme.typography.labelSmall)
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

@Composable private fun ResearchFoundationCard(foundation: StoryFoundation, busy: Boolean, onRegenerate: () -> Unit, onCreate: () -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRegenerate, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Refresh, null); Text("整套重做") }
                Button(onClick = onCreate, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.CheckCircle, null); Text("正式建书") }
            }
        }
    }
}
