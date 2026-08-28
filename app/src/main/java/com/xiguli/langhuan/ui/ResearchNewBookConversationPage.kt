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
            researchMessage = null
            viewModel.send(text)
            return
        }
        researching = true
        researchMessage = "正在联网检索公开资料……"
        scope.launch {
            val result = runCatching { research.search(text) }.getOrNull()
            val sources = result?.sources.orEmpty()
            lastSources = sources
            researching = false
            if (sources.isEmpty()) {
                researchMessage = "联网检索暂时没有拿到可靠结果，AI 会明确区分已知与不确定信息。"
                viewModel.send(text)
            } else {
                researchMessage = "已检索到 ${sources.size} 条公开结果，正在交给 AI 核对并继续构思。"
                val hidden = buildString {
                    append(text)
                    append(RESEARCH_MARKER)
                    appendLine()
                    appendLine("App 刚刚针对用户当前问题完成了实时公开网页检索。下面是搜索标题、摘要与来源，不是模型记忆。")
                    appendLine("你必须优先依据这些资料回答‘有哪些作品/作者/作品信息’等事实问题；不同来源冲突时要说存在不确定性。")
                    appendLine("不要再说‘我不能联网’或要求用户自己提供资料，因为联网检索已经由 App 完成。")
                    appendLine("只可从资料提炼高层创作特征用于原创构思，不要复制原作受保护表达、人物或剧情骨架。")
                    appendLine(result!!.context)
                }
                viewModel.send(hidden)
            }
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
                                researching -> "正在检索公开资料"
                                state.foundation != null -> "蓝图阶段：继续聊天修改，满意后正式建书"
                                state.proposal != null -> "方案已成形，下一步搭世界、人物和三级大纲"
                                else -> "可直接问现有作品/作者，琅嬛会先联网检索"
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
                        placeholder = { Text(if (state.foundation == null) "比如：搜一下薄情书生的小说……" else "比如：第二卷太拖，前三章钩子加强……") },
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
                    ResearchStarterChip("搜一下薄情书生有哪些小说，再帮我提炼高层叙事特点", ::submit)
                    ResearchStarterChip("你知道《迷雾之上》吗？先查公开资料，再帮我原创一个不同故事", ::submit)
                }
            }
            items(state.messages) { message -> ResearchChatBubble(message) }
            researchMessage?.let { message -> item { ResearchStatusCard(message, lastSources) } }

            if (state.foundation == null) {
                state.proposal?.let { proposal -> item { ResearchProposalCard(proposal, state.isBusy || researching) { viewModel.generateFoundation(false) } } }
            } else {
                item { ResearchFoundationCard(state.foundation!!, state.isBusy || researching, { viewModel.generateFoundation(true) }, viewModel::createCurrentFoundation) }
            }

            if (state.isBusy || researching) item {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (researching) "正在联网检索……" else state.busyLabel.ifBlank { "AI 正在处理……" })
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

@Composable private fun ResearchStatusCard(message: String, sources: List<WebResearchSource>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TravelExplore, null); Spacer(Modifier.width(6.dp)); Text(message, fontWeight = FontWeight.SemiBold)
            }
            sources.take(4).forEach { source ->
                Text("• ${source.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (source.snippet.isNotBlank()) Text(source.snippet, style = MaterialTheme.typography.labelSmall, maxLines = 3)
            }
            if (sources.isNotEmpty()) Text("搜索结果只作为事实核对与高层风格研究，不会直接照搬原作。", style = MaterialTheme.typography.labelSmall)
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
