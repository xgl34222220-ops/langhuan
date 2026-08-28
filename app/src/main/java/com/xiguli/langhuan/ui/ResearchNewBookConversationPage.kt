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
private const val MAX_RESEARCH_EVIDENCE_CHARS = 6_500

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
    var lastSubmitted by remember { mutableStateOf("") }

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
        lastSubmitted = text
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
            "正在粗读 ${detected.size} 个参考对象：先核对作品归属、简介和公开评价，再提炼高层创作机制……"
        } else {
            "正在联网粗读：先核对作品/作者，再整理大致题材、体验和可借鉴机制……"
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
                researchMessage = "网页这轮没拿到足够线索，但不会因此停下；AI 会用已有知识做大致高层分析，并把拿不准的细节单独标出来。"
            } else {
                val deep = bundle?.deepReadCount ?: 0
                researchMessage = if (targets.size > 1) {
                    "已拿到 ${sources.size} 条公开线索、深读 $deep 个页面；这次按“粗读”整理共同气质和可融合机制，不要求逐章剧情证据。"
                } else {
                    "已拿到 ${sources.size} 条公开线索、深读 $deep 个页面；正在做高层粗读，不会因为缺少章节级资料就说整本不了解。"
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
                                researching -> "正在联网粗读参考作品"
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
                    Text(if (researching) "正在联网粗读作品资料……" else state.busyLabel.ifBlank { "AI 正在处理……" })
                }
            }
            state.error?.let { error -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(friendlyResearchError(error), color = MaterialTheme.colorScheme.onErrorContainer)
                        if (lastSubmitted.isNotBlank() && !state.isBusy && !researching) {
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

private fun buildResearchPrompt(text: String, bundle: CreationResearchBundle?): String = buildString {
    append(text)
    append(RESEARCH_MARKER)
    appendLine()
    appendLine("这是琅嬛 App 本轮执行的公开网页粗读上下文，只用于帮助你理解参考对象的大致方向，不是要求做学术级或逐章级考证。")
    appendLine("本轮目标优先级：先把作者/作品范围看个大致，再提炼可用于原创小说的高层机制。不要因为搜不到完整剧情、章节或人物细节，就把整部作品判定为‘不了解’。")
    appendLine("网页主要负责核对：作者与书名归属、公开简介、平台标签、作品列表、读者评价和可见的高层描述。具体章节并不是本任务的必要条件。")
    appendLine("你拥有自己的训练知识。对于你本来就有把握的作者/作品高层事实，可以与网页线索合并使用；网页没覆盖到的部分不要自动清空。")
    appendLine("绝对不要回答‘我不能联网/不能直接搜索/请用户自己提供链接’。联网动作已经由 App 完成。")
    appendLine("置信度表达：网页直接支持的写‘公开资料可确认’；由公开线索+既有知识归纳的写‘大致判断/高层印象’；只有具体细节真的拿不准时才单独标‘细节未核实’。")

    if (bundle == null || !bundle.hasSources) {
        appendLine("本次网页没有返回足够线索。这只代表网页粗读失败，不代表你必须停止分析。")
        appendLine("如果你对该作者/作品已有较稳定的高层认识，就继续给出题材、核心体验、规则/冲突类型、叙事节奏和可借鉴机制；不要编具体章节、原句、数字或你并不确定的人名。")
    } else {
        appendLine("下面是已经压缩过的公开线索。重点看作品归属、简介、标签、评价和共同气质，不要求从网页还原完整剧情。")
        appendLine(compactResearchEvidence(bundle))
    }

    appendLine()
    appendLine("【默认：作者/多作品粗读模式】")
    appendLine("当用户说‘看看这个作者其他小说/看个大致/有什么能融合’时，不要强迫每本作品都做完整剧情档案。先做作品组合粗读：")
    appendLine("1. 作品名与作者归属；2. 大致题材/类型；3. 主要阅读体验与氛围；4. 能看出的核心冲突或规则思路；5. 叙事/节奏特点；6. 最值得借鉴的高层机制；7. 置信度。")
    appendLine("作品列表确认了，但某一本缺少详细剧情资料时，仍然可以从公开简介、标签、评价和你已有知识给‘粗略印象’，明确它不是逐章核验即可。")
    appendLine("只有用户明确说‘详细分析某一本/具体讲什么/主角能力是什么’时，才升级为深挖模式，尽量补主角、能力、世界规则、主题、主线和具体冲突。")
    appendLine("不要反复强调‘没有可靠具体剧情所以不装懂’。用户当前要的是可用于创作决策的大致高层认识，不是考据报告。")

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
            group.result.sources.take(5).forEachIndexed { index, source ->
                appendLine("- ${source.title}")
                if (source.snippet.isNotBlank()) appendLine("  摘要：${source.snippet.take(260)}")
                if (index < 2 && source.detail.isNotBlank()) appendLine("  页面线索：${source.detail.take(420)}")
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
            "AI 服务请求超时：模型或中转站在规定时间内没有返回结果。联网资料现在已经做了压缩，后续不会再把整批网页内容越堆越大；可以直接点“重试上一句”。"
        "429" in lower || "rate limit" in lower || "too many requests" in lower ->
            "AI 服务当前限流（429）。稍后重试即可，当前会谈内容不会丢失。"
        "502" in lower || "503" in lower || "504" in lower ->
            "AI 中转站暂时不可用或上游响应过慢（${value.take(120)}）。可以直接重试上一句。"
        else -> value.ifBlank { "AI 请求失败，请重试上一句。" }
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
            if (sources.isNotEmpty()) Text("这里是公开资料粗读，不要求逐章考据；模型会结合已有知识提炼高层机制，拿不准的细节单独标注。", style = MaterialTheme.typography.labelSmall)
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
