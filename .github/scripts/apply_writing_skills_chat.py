from pathlib import Path

ROOT = Path('.')


def replace_once(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'pattern not unique in {path}: {text.count(old)} matches')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')


def write(path: str, content: str):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')


writing_skills = r'''package com.xiguli.langhuan.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native Android adaptation of external writing skills.
 *
 * Skills are craft-layer guidance only. They may influence how a task is performed, but they never
 * mutate Canon, ChapterContract, knowledge boundaries, chronology, Candidate facts or RAG contents.
 */
data class WritingSkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val license: String,
    val sourceUrl: String,
    val sourceRevision: String,
    val supportedTasks: Set<AiTaskType>,
    val defaultTasks: Set<AiTaskType>,
)

@Serializable
data class WritingSkillBinding(
    val skillId: String,
    val enabled: Boolean = true,
    val tasks: List<AiTaskType> = emptyList(),
)

@Serializable
private data class WritingSkillConfig(
    val schemaVersion: Int = 1,
    val bindings: List<WritingSkillBinding> = emptyList(),
)

object WritingSkillCatalog {
    val all: List<WritingSkillDefinition> = listOf(
        WritingSkillDefinition(
            id = "story-long-write",
            name = "长篇网文写作",
            description = "长篇写作方法：约束锁、情绪目标、章内推进、悬疑/反转、章尾钩子与按需上下文。",
            version = "1.0.0-adapted",
            license = "MIT",
            sourceUrl = "https://github.com/zenstory-ai/oh-story-claudecode/tree/main/skills/story-long-write",
            sourceRevision = "70c294b20ce89440e70edb766b0446d3057bc077",
            supportedTasks = setOf(
                AiTaskType.SCENE_DIRECTOR,
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
                AiTaskType.AUTONOMOUS_PLANNER,
                AiTaskType.FULL_BOOK_EDITOR,
            ),
            defaultTasks = setOf(
                AiTaskType.SCENE_DIRECTOR,
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
                AiTaskType.AUTONOMOUS_PLANNER,
            ),
        ),
        WritingSkillDefinition(
            id = "avoid-ai-writing",
            name = "去 AI 写作痕迹",
            description = "检测并压低模板句、报告腔、解释腔、机械排比、同义复述和过度总结。",
            version = "3.28.0-adapted",
            license = "MIT",
            sourceUrl = "https://github.com/conorbronsdon/avoid-ai-writing",
            sourceRevision = "3bd64f19f41ae941d44e8261fe575624a2b1b8f6",
            supportedTasks = setOf(
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
            ),
            defaultTasks = setOf(
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
            ),
        ),
    )

    fun definition(id: String): WritingSkillDefinition? = all.firstOrNull { it.id == id }

    fun defaultBinding(skill: WritingSkillDefinition): WritingSkillBinding = WritingSkillBinding(
        skillId = skill.id,
        enabled = true,
        tasks = skill.defaultTasks.toList(),
    )

    fun guidance(skill: WritingSkillDefinition, task: AiTaskType): String = when (skill.id) {
        "story-long-write" -> storyLongWriteGuidance(task)
        "avoid-ai-writing" -> avoidAiWritingGuidance(task)
        else -> ""
    }

    private fun storyLongWriteGuidance(task: AiTaskType): String {
        val common = """
            来源适配：oh-story-claudecode / story-long-write（MIT）。
            - 先锁定本次任务的用户明确要求、必须发生、禁止发生、时间锚、人物进出状态与停笔点，再考虑任何写作技法。
            - 技法只属于 C 层。S/A/B、Canon、Chapter Contract、人物认知边界、时间线与用户明确决定拥有更高优先级；发生冲突时立刻放弃 Skill 建议。
            - 每个场景必须有明确的情绪目标和剧情功能，至少推进信息、关系、选择、代价、威胁中的一项；不要写只负责填字数的场景。
            - 只加载/使用本任务真正需要的信息。不要为了展示世界观完整而把设定、人物、规则、伏笔集中解释给读者。
            - 章尾钩子必须由本章已有因果推出新的问题、选择或威胁，禁止靠提前揭露未来答案制造刺激。
            - 悬疑/惊悚优先递进信息差：一次异常只推进一层认知，未到回收期的线索只保持存在感，不解释谜底。
            - 参考作品只能抽取结构、情绪、节奏和功能位，禁止复刻独特角色、专名、具体桥段或连续表达。
        """.trimIndent()
        val taskSpecific = when (task) {
            AiTaskType.SCENE_DIRECTOR -> "章纲导演额外要求：先写清主角本章目标、阻力、关键选择、代价、信息增量和章末新债，再编排场景顺序。"
            AiTaskType.PROSE_AUTHOR -> "正文作者额外要求：把规则和推理写进人物行动、观察、对话和后果，不把后台资料改写成正文报告。"
            AiTaskType.NOVELIZATION -> "小说化额外要求：保留事实与事件顺序，只把说明/清单转换成可感知的场景、动作、阻碍、反应和选择。"
            AiTaskType.EDITOR_REVIEW -> "主编审稿额外要求：检查场景是否真正兑现情绪目标、章内推进与读者契约，而不是只看语句通顺。"
            AiTaskType.EDITOR_REWRITE -> "主编修订额外要求：只修审稿命中的问题，不借重写机会增加新设定、提前伏笔答案或改变角色立场。"
            AiTaskType.AUTONOMOUS_PLANNER -> "自治规划额外要求：滚动计划必须保留未来空间，优先安排承诺兑现、人物选择与新债，不把终局答案提前塞进近期章节。"
            AiTaskType.FULL_BOOK_EDITOR -> "全书主编额外要求：关注情绪模块重复、钩子同质化、人物功能位固化和长期承诺拖欠。"
            else -> ""
        }
        return "$common\n$taskSpecific".trim()
    }

    private fun avoidAiWritingGuidance(task: AiTaskType): String {
        val common = """
            来源适配：conorbronsdon/avoid-ai-writing（MIT）。
            - 这是写作质量提示，不是“AI 检测器”，不得把命中模式当作作者身份判断。
            - 删除报告腔、总结腔、空泛结论、机械排比、同义反复、连续“不是 A 而是 B”、模板化升华和无必要的小标题/清单。
            - 已经自然、具体、有角色声音的段落不要为了“去 AI”而整段重写；优先最小修改，保留作者已经形成的句法节奏和粗粝感。
            - 用具体动作、物件、空间关系、潜台词和后果替代抽象评价；不要把场景结论再解释一遍给读者。
            - 不得为了追求“人味”故意制造事实错误、语法破坏、角色 OOC 或与本书既定文风冲突的口语化。
        """.trimIndent()
        val taskSpecific = when (task) {
            AiTaskType.PROSE_AUTHOR -> "正文阶段只把这些规则当自检清单，不要在正文里谈论 AI、规则、检测或修改过程。"
            AiTaskType.NOVELIZATION -> "小说化阶段优先处理信息清单、功能动作堆叠、解释性结论和泛化恐怖意象。"
            AiTaskType.EDITOR_REVIEW -> "审稿阶段把 AI 腔问题定位到具体段落，并区分‘明显损伤叙事’与‘作者可能有意的重复/节奏’。"
            AiTaskType.EDITOR_REWRITE -> "修订阶段只改命中片段；不要把整章抛光成统一、无棱角的模型腔。"
            else -> ""
        }
        return "$common\n$taskSpecific".trim()
    }
}

class WritingSkillStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun bindings(): List<WritingSkillBinding> {
        val stored = load().bindings.associateBy { it.skillId }
        return WritingSkillCatalog.all.map { skill ->
            val existing = stored[skill.id]
            if (existing == null) WritingSkillCatalog.defaultBinding(skill)
            else existing.copy(tasks = existing.tasks.filter { it in skill.supportedTasks }.distinct())
        }
    }

    @Synchronized
    fun setEnabled(skillId: String, enabled: Boolean) {
        mutate(skillId) { it.copy(enabled = enabled) }
    }

    @Synchronized
    fun setTaskEnabled(skillId: String, task: AiTaskType, enabled: Boolean) {
        val skill = WritingSkillCatalog.definition(skillId) ?: return
        if (task !in skill.supportedTasks) return
        mutate(skillId) { binding ->
            val tasks = binding.tasks.toMutableSet()
            if (enabled) tasks += task else tasks -= task
            binding.copy(tasks = tasks.toList())
        }
    }

    @Synchronized
    fun resetDefaults() {
        save(WritingSkillConfig(bindings = WritingSkillCatalog.all.map(WritingSkillCatalog::defaultBinding)))
    }

    @Synchronized
    fun snapshot(): WritingSkillSnapshot {
        val current = bindings().associateBy { it.skillId }
        val active = AiTaskType.entries.associateWith { task ->
            WritingSkillCatalog.all.filter { skill ->
                val binding = current[skill.id]
                binding?.enabled == true && task in binding.tasks && task in skill.supportedTasks
            }
        }
        return WritingSkillSnapshot(active)
    }

    private fun mutate(skillId: String, update: (WritingSkillBinding) -> WritingSkillBinding) {
        val skill = WritingSkillCatalog.definition(skillId) ?: return
        val all = bindings().associateBy { it.skillId }.toMutableMap()
        val current = all[skillId] ?: WritingSkillCatalog.defaultBinding(skill)
        all[skillId] = update(current)
        save(WritingSkillConfig(bindings = WritingSkillCatalog.all.mapNotNull { all[it.id] }))
    }

    private fun load(): WritingSkillConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return WritingSkillConfig()
        return runCatching { json.decodeFromString(WritingSkillConfig.serializer(), raw) }
            .getOrElse { WritingSkillConfig() }
    }

    private fun save(config: WritingSkillConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(WritingSkillConfig.serializer(), config)).commit()
    }

    private companion object {
        const val PREFS = "langhuan_writing_skills"
        const val KEY_CONFIG = "writing_skills_v1"
    }
}

data class WritingSkillSnapshot(
    private val activeByTask: Map<AiTaskType, List<WritingSkillDefinition>>,
) {
    fun forTask(task: AiTaskType): List<WritingSkillDefinition> = activeByTask[task].orEmpty()
}

class SkillAwareAiGateway(
    private val delegate: AiGateway,
    private val task: AiTaskType,
    private val skills: List<WritingSkillDefinition>,
) : AiGateway {
    override suspend fun generate(prompt: PromptBundle) = delegate.generate(decorate(prompt))

    override suspend fun generateText(prompt: PromptBundle): String = delegate.generateText(decorate(prompt))

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit) =
        delegate.generateStreaming(decorate(prompt), onDelta)

    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String =
        delegate.generateTextStreaming(decorate(prompt), onDelta)

    internal fun decorate(prompt: PromptBundle): PromptBundle {
        if (skills.isEmpty()) return prompt
        val blocks = skills.mapNotNull { skill ->
            WritingSkillCatalog.guidance(skill, task).takeIf { it.isNotBlank() }?.let { guidance ->
                "【Skill · ${skill.name}】\n$guidance"
            }
        }
        if (blocks.isEmpty()) return prompt
        return prompt.copy(
            system = buildString {
                append(prompt.system.trimEnd())
                append("\n\n【C·写作 Skill（只影响写法，不是事实源）】\n")
                append("以下 Skill 不能覆盖用户明确要求、S/A/B、Canon、Chapter Contract、人物知识边界、时间线或 Candidate/Canon 审批。发生冲突时以上层约束为准。\n\n")
                append(blocks.joinToString("\n\n"))
            },
        )
    }
}
'''
write('app/src/main/java/com/xiguli/langhuan/engine/WritingSkills.kt', writing_skills)

writing_skill_panel = r'''package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.WritingSkillBinding
import com.xiguli.langhuan.engine.WritingSkillCatalog
import com.xiguli.langhuan.engine.WritingSkillDefinition
import com.xiguli.langhuan.engine.WritingSkillStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WritingSkillUiItem(
    val definition: WritingSkillDefinition,
    val binding: WritingSkillBinding,
)

data class WritingSkillUiState(
    val skills: List<WritingSkillUiItem> = emptyList(),
)

class WritingSkillViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WritingSkillStore(application)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<WritingSkillUiState> = _state.asStateFlow()

    fun setEnabled(skillId: String, enabled: Boolean) {
        store.setEnabled(skillId, enabled)
        refresh()
    }

    fun setTaskEnabled(skillId: String, task: AiTaskType, enabled: Boolean) {
        store.setTaskEnabled(skillId, task, enabled)
        refresh()
    }

    fun resetDefaults() {
        store.resetDefaults()
        refresh()
    }

    private fun refresh() {
        _state.value = load()
    }

    private fun load(): WritingSkillUiState {
        val bindings = store.bindings().associateBy { it.skillId }
        return WritingSkillUiState(
            skills = WritingSkillCatalog.all.map { skill ->
                WritingSkillUiItem(skill, bindings[skill.id] ?: WritingSkillCatalog.defaultBinding(skill))
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingSkillPanel(viewModel: WritingSkillViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("写作 Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Skill 只进入 C 层写作方法，不会覆盖 Canon、章节合同、人物知识边界或时间线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.skills.forEach { item ->
                val skill = item.definition
                val binding = item.binding
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${skill.version} · ${skill.license} · GitHub 原生适配",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = binding.enabled,
                                onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                            )
                        }
                        Text(skill.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            skill.sourceUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (binding.enabled) {
                            Text("影响任务", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                skill.supportedTasks.forEach { task ->
                                    FilterChip(
                                        selected = task in binding.tasks,
                                        onClick = {
                                            viewModel.setTaskEnabled(skill.id, task, task !in binding.tasks)
                                        },
                                        label = { Text(task.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::resetDefaults,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Restore, null)
                Spacer(Modifier.width(7.dp))
                Text("恢复推荐 Skill 绑定")
            }
        }
    }
}
'''
write('app/src/main/java/com/xiguli/langhuan/ui/WritingSkillPanel.kt', writing_skill_panel)

writing_skill_test = r'''package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingSkillsTest {
    @Test
    fun skillDecoratorInjectsCraftLayerWithoutTouchingPayload() = runBlocking {
        val capture = CapturingGateway()
        val skill = WritingSkillCatalog.definition("story-long-write")!!
        val gateway = SkillAwareAiGateway(capture, AiTaskType.PROSE_AUTHOR, listOf(skill))
        val prompt = PromptBundle(
            system = "你是正文作者",
            user = "写第一章",
            attachments = listOf(PromptAttachment("setting.md", "text/markdown", "YWJj")),
            messages = listOf(PromptMessage("user", "保持第一人称")),
            jsonMode = false,
        )

        assertEquals("ok", gateway.generateText(prompt))
        val decorated = capture.lastPrompt!!
        assertTrue(decorated.system.contains("C·写作 Skill"))
        assertTrue(decorated.system.contains("不能覆盖用户明确要求"))
        assertTrue(decorated.system.contains("长篇网文写作"))
        assertEquals(prompt.user, decorated.user)
        assertEquals(prompt.attachments, decorated.attachments)
        assertEquals(prompt.messages, decorated.messages)
        assertEquals(prompt.jsonMode, decorated.jsonMode)
    }

    @Test
    fun emptySkillListLeavesPromptUntouched() = runBlocking {
        val capture = CapturingGateway()
        val gateway = SkillAwareAiGateway(capture, AiTaskType.PROSE_AUTHOR, emptyList())
        val prompt = PromptBundle("system", "user", jsonMode = false)
        gateway.generateText(prompt)
        assertSame(prompt, capture.lastPrompt)
    }

    @Test
    fun recommendedBindingsDoNotLetAntiAiSkillControlPlanning() {
        val anti = WritingSkillCatalog.definition("avoid-ai-writing")!!
        val binding = WritingSkillCatalog.defaultBinding(anti)
        assertTrue(AiTaskType.NOVELIZATION in binding.tasks)
        assertTrue(AiTaskType.EDITOR_REWRITE in binding.tasks)
        assertFalse(AiTaskType.AUTONOMOUS_PLANNER in anti.supportedTasks)
        assertFalse(AiTaskType.SCENE_DIRECTOR in binding.tasks)
    }

    private class CapturingGateway : AiGateway {
        var lastPrompt: PromptBundle? = null

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("not used")

        override suspend fun generateText(prompt: PromptBundle): String {
            lastPrompt = prompt
            return "ok"
        }
    }
}
'''
write('app/src/test/java/com/xiguli/langhuan/engine/WritingSkillsTest.kt', writing_skill_test)

# Freeze skill bindings together with task-model routing for each run.
replace_once(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '    private val store = AiTaskRoutingStore(app)\n    private val telemetry = AiModelTelemetryStore(app)\n',
    '    private val store = AiTaskRoutingStore(app)\n    private val telemetry = AiModelTelemetryStore(app)\n    private val skillStore = WritingSkillStore(app)\n',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '        val routes = store.routes()\n        val selections = linkedMapOf<AiTaskType, ResolvedTaskModel>()\n',
    '        val routes = store.routes()\n        val skillSnapshot = skillStore.snapshot()\n        val selections = linkedMapOf<AiTaskType, ResolvedTaskModel>()\n',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '                gateway = TelemetryAiGateway(baseGateway, attribution, telemetry),\n',
    '                gateway = TelemetryAiGateway(\n                    SkillAwareAiGateway(baseGateway, task, skillSnapshot.forTask(task)),\n                    attribution,\n                    telemetry,\n                ),\n',
)

# Surface Skill controls next to task-level model routing.
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/AiProviderSetupPage.kt',
    '    val taskRoutingVm: TaskModelRoutingViewModel = viewModel()\n    var quickProviderId by remember { mutableStateOf<String?>(null) }\n',
    '    val taskRoutingVm: TaskModelRoutingViewModel = viewModel()\n    val writingSkillVm: WritingSkillViewModel = viewModel()\n    var quickProviderId by remember { mutableStateOf<String?>(null) }\n',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/AiProviderSetupPage.kt',
    '            if (p.savedProviders.isNotEmpty()) {\n                item { TaskModelRoutingPanel(taskRoutingVm) }\n            }\n\n            item {\n',
    '            if (p.savedProviders.isNotEmpty()) {\n                item { TaskModelRoutingPanel(taskRoutingVm) }\n            }\n\n            item { WritingSkillPanel(writingSkillVm) }\n\n            item {\n',
)

# Conversation: restore directly to the tail and keep following streaming only while the user stays near the tail.
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt',
    'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\n',
    'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState\n',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt',
    '    val state by viewModel.state.collectAsStateWithLifecycle()\n    val scope = rememberCoroutineScope()\n    val context = LocalContext.current.applicationContext\n',
    '    val state by viewModel.state.collectAsStateWithLifecycle()\n    val scope = rememberCoroutineScope()\n    val listState = rememberLazyListState()\n    var followConversationTail by remember { mutableStateOf(true) }\n    var initialTailPositioned by remember { mutableStateOf(false) }\n    val context = LocalContext.current.applicationContext\n',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt',
    '    LaunchedEffect(state.createdStoryId) {\n',
    '''    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && initialTailPositioned) {
            // User touch/drag takes priority over automatic streaming follow.
            followConversationTail = false
        } else if (!listState.isScrollInProgress && initialTailPositioned) {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            followConversationTail = layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 2
        }
    }

    LaunchedEffect(
        state.messages.size,
        state.streamingReply.length,
        state.proposal != null,
        state.foundationStage,
        state.blueprintDirty,
    ) {
        withFrameNanos { }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0 && (!initialTailPositioned || followConversationTail)) {
            listState.scrollToItem(lastIndex)
            initialTailPositioned = true
            followConversationTail = true
        }
    }

    LaunchedEffect(state.createdStoryId) {
''',
)
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt',
    '        LazyColumn(\n            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),\n',
    '        LazyColumn(\n            state = listState,\n            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),\n',
)

# Add an always-visible blueprint action after the user has started the conversation.
needle = '''                    if (state.foundation == null && state.messages.any { it.role == "user" }) {
                        FilledTonalButton(
                            onClick = viewModel::syncConversationProposal,
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.proposal == null) "整理方案" else "同步方案")
                        }
                    }
'''
replacement = needle + '''                    if (state.messages.any { it.role == "user" }) {
                        FilledTonalButton(
                            onClick = {
                                when {
                                    state.foundation != null && !state.blueprintDirty -> scope.launch {
                                        withFrameNanos { }
                                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                                        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                                    }
                                    else -> {
                                        retryFoundation = true
                                        viewModel.generateFoundation(false)
                                    }
                                }
                            },
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    state.foundation == null -> "建书蓝图"
                                    state.blueprintDirty -> "同步蓝图"
                                    else -> "查看蓝图"
                                }
                            )
                        }
                    }
'''
replace_once('app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt', needle, replacement)

# Allow the blueprint button to build directly from conversation even before a cached proposal exists.
replace_once(
    'app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt',
    '        val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders() ?: return\n        if (before.isBusy) return\n',
    '''        val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders()
            ?: if (before.messages.any { it.role == "user" }) {
                NewBookProposal(
                    title = "未命名",
                    genre = "未分类",
                    premise = "尚未整理",
                    theme = DEFAULT_THEME,
                    targetWords = 500_000,
                    coreHook = "待整理",
                    coverBrief = "",
                    rationale = "",
                )
            } else return
        if (before.isBusy) return
''',
)

# Version.
replace_once(
    'app/build.gradle.kts',
    '        versionCode = 68\n        versionName = "0.27.0-alpha01"\n',
    '        versionCode = 69\n        versionName = "0.27.1-alpha01"\n',
)

# Remove one-shot migration machinery from the final tree.
Path('.github/scripts/apply_writing_skills_chat.py').unlink(missing_ok=True)
Path('.github/workflows/apply-writing-skills-chat.yml').unlink(missing_ok=True)
