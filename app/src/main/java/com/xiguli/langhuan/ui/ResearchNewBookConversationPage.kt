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
            "正在分别检索 ${detected.size} 个参考对象，避免资料混在一起……"
        } else {
            "正在联网检索公开资料……"
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
                researchMessage = "本次实时检索没有拿到足够可靠的公开结果；琅嬛会明确说“这次没搜到”，不会再说自己不能联网。"
            } else {
                researchMessage = if (targets.size > 1) {
                    "已为 ${targets.size} 个参考对象找到 ${sources.size} 条公开结果，正在拆解参考基因并融合。"
                } else {
                    "已检索到 ${sources.size} 条公开结果，正在交给 AI 核对并继续构思。"
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
                    Text(if (researching) "正在联网研究参考作品……" else state.busyLabel.ifBlank { "AI 正在处理……" })
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
    appendLine("这是 App 在本轮会谈中刚刚执行的实时公开网页检索上下文，不是模型训练记忆。")
    appendLine("重要：你绝对不要再回答“我不能联网/不能直接搜索/请用户自己提供链接”。联网动作已经由琅嬛 App 执行。")

    if (bundle == null || !bundle.hasSources) {
        appendLine("本次搜索服务没有返回足够可靠的公开结果。你必须准确表述为“我刚才这次实时检索没有找到足够可靠的公开资料”，而不是声称没有联网能力。")
        appendLine("若用户给的是作者名或小众网文名，可以基于用户已经明确说出的目标继续构思，但不要假装知道该作品的具体事实。可以建议换书名/作者笔名/平台关键词继续检索。")
    } else {
        appendLine("下面按参考对象分组列出搜索标题、摘要和来源。涉及作者、作品名、简介等事实时优先以这些实时资料为依据；来源冲突就明确说不确定。")
        appendLine(bundle.context)
    }

    appendLine()
    appendLine("【多作品融合工作法】")
    appendLine("如果用户提到一部或多部小说作为参考，先在内部为每个参考对象抽取“参考基因”：")
    appendLine("1. 世界/规则机制；2. 核心谜团或冲突类型；3. 信息释放与反转方式；4. 人物关系张力；5. 节奏与升级方式；6. 叙事视角/距离；7. 氛围与情绪体验；8. 用户明确想保留或排除的部分。")
    appendLine("然后做融合：KEEP=保留高层机制，TRANSFORM=换成原创实现，AVOID=容易变成照搬的标志性人物/专名/独特剧情骨架。")
    appendLine("最终方案必须重新设计角色、专名、世界规则、核心谜团、因果链和结局方向。不要复制原作句子，也不要只给原作角色换名字。")
    appendLine("用户如果已经明确说“融合这几本”，不要因为信息很多就退回问卷；先给一版你理解的融合方向，再最多追问1个真正影响路线的问题。")
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
                Text("• ${source.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (source.snippet.isNotBlank()) Text(source.snippet, style = MaterialTheme.typography.labelSmall, maxLines = 3)
            }
            if (sources.isNotEmpty()) Text("资料只用于事实核对和高层创作研究；融合时会重新设计原创人物、规则和主线。", style = MaterialTheme.typography.labelSmall)
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
