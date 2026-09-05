package com.xiguli.langhuan.ui.writing

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.SkillInstallResult
import com.xiguli.langhuan.engine.WritingSkillBinding
import com.xiguli.langhuan.engine.WritingSkillCatalog
import com.xiguli.langhuan.engine.WritingSkillDefinition
import com.xiguli.langhuan.engine.WritingSkillStore
import com.xiguli.langhuan.engine.WritingSkillUpdateCandidate
import com.xiguli.langhuan.engine.WritingSkillUpdateCheck
import com.xiguli.langhuan.engine.WritingSkillUpdateClient
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WritingSkillUiItem(
    val definition: WritingSkillDefinition,
    val binding: WritingSkillBinding,
)

data class WritingSkillUiState(
    val skills: List<WritingSkillUiItem> = emptyList(),
    val updateCandidates: Map<String, WritingSkillUpdateCandidate> = emptyMap(),
    val isCheckingUpdates: Boolean = false,
    val updatingSkillId: String? = null,
    val lastUpdateCheckAt: Long = 0L,
    val message: String? = null,
    val error: String? = null,
)

class WritingSkillViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WritingSkillStore(application)
    private val updateClient = WritingSkillUpdateClient()
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

    fun importSkill(uri: Uri) {
        val raw = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文件")
        }.getOrElse {
            _state.value = _state.value.copy(error = "导入失败：${it.message ?: "无法读取文件"}", message = null)
            return
        }
        when (val result = store.install(raw)) {
            is SkillInstallResult.Success -> {
                val suffix = if (result.replaced) "已更新" else "已安装"
                refresh(message = "${result.skillName} $suffix")
            }
            is SkillInstallResult.Error -> {
                _state.value = _state.value.copy(error = result.message, message = null)
            }
        }
    }

    fun checkUpdates() {
        if (_state.value.isCheckingUpdates || _state.value.updatingSkillId != null) return
        _state.value = _state.value.copy(
            isCheckingUpdates = true,
            updateCandidates = emptyMap(),
            message = null,
            error = null,
        )
        viewModelScope.launch {
            val candidates = linkedMapOf<String, WritingSkillUpdateCandidate>()
            val failures = mutableListOf<String>()
            val skills = store.definitions().filter { it.updateUrl.isNotBlank() }
            skills.forEach { skill ->
                when (val result = updateClient.check(skill)) {
                    is WritingSkillUpdateCheck.Update -> candidates[skill.id] = result.candidate
                    is WritingSkillUpdateCheck.Error -> failures += "${skill.name}：${result.message}"
                    is WritingSkillUpdateCheck.UpToDate -> Unit
                }
            }
            val message = when {
                candidates.isNotEmpty() -> "发现 ${candidates.size} 个 Skill 更新"
                failures.isEmpty() -> "已检查 ${skills.size} 个 Skill，当前都是最新版本"
                else -> "检查完成，没有发现可用更新"
            }
            _state.value = load().copy(
                updateCandidates = candidates,
                isCheckingUpdates = false,
                lastUpdateCheckAt = System.currentTimeMillis(),
                message = message,
                error = failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            )
        }
    }

    fun applyUpdate(candidate: WritingSkillUpdateCandidate) {
        val before = _state.value
        if (before.isCheckingUpdates || before.updatingSkillId != null) return
        _state.value = before.copy(updatingSkillId = candidate.skillId, message = null, error = null)
        viewModelScope.launch {
            when (val result = store.applyRemoteUpdate(candidate.skillId, candidate.rawManifest)) {
                is SkillInstallResult.Success -> {
                    val remaining = before.updateCandidates - candidate.skillId
                    _state.value = load().copy(
                        updateCandidates = remaining,
                        lastUpdateCheckAt = before.lastUpdateCheckAt,
                        message = "${result.skillName} 已更新到 ${candidate.remoteVersion}；原有启用状态和任务绑定已保留",
                    )
                }
                is SkillInstallResult.Error -> {
                    _state.value = before.copy(updatingSkillId = null, error = result.message, message = null)
                }
            }
        }
    }

    fun dismissUpdate(skillId: String) {
        _state.value = _state.value.copy(updateCandidates = _state.value.updateCandidates - skillId)
    }

    fun uninstall(skillId: String) {
        val name = store.definitions().firstOrNull { it.id == skillId }?.name ?: skillId
        if (store.uninstall(skillId)) refresh(message = "$name 已卸载")
    }

    fun resetDefaults() {
        store.resetDefaults()
        refresh(message = "已恢复推荐 Skill 绑定")
    }

    fun clearNotice() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private fun refresh(message: String? = null) {
        val before = _state.value
        _state.value = load().copy(
            updateCandidates = before.updateCandidates,
            lastUpdateCheckAt = before.lastUpdateCheckAt,
            message = message,
        )
    }

    private fun load(): WritingSkillUiState {
        val bindings = store.bindings().associateBy { it.skillId }
        return WritingSkillUiState(
            skills = store.definitions().map { skill ->
                WritingSkillUiItem(skill, bindings[skill.id] ?: WritingSkillCatalog.defaultBinding(skill))
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingSkillPanel(viewModel: WritingSkillViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current
    val builtins = state.skills.filter { it.definition.builtin }
    val installed = state.skills.filterNot { it.definition.builtin }
    var deleting by remember { mutableStateOf<WritingSkillDefinition?>(null) }
    var confirmingUpdate by remember { mutableStateOf<WritingSkillUpdateCandidate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val enabledCount = state.skills.count { it.binding.enabled && it.binding.tasks.isNotEmpty() }
        LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = LanghuanShape.chip,
                    color = t.warmSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(19.dp), tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("$enabledCount 个 Skill 正在生效", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text("总开关 + 任务绑定同时满足才会被真正调用。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge("ACTIVE $enabledCount", accent = enabledCount > 0)
            }
        }

        SkillUpdateCenterV8(
            state = state,
            onCheck = viewModel::checkUpdates,
            onUpdate = { confirmingUpdate = it },
            onDismiss = viewModel::dismissUpdate,
        )

        SkillGroup("内置 Skills", builtins, viewModel, onDelete = {})
        if (installed.isNotEmpty()) {
            SkillGroup("我的 Skills", installed, viewModel, onDelete = { deleting = it })
        } else {
            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 17.dp) {
                Column {
                    Text("还没有安装自定义 Skill", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "点击上方“导入 Skill”选择 .json 文件。自定义 Skill 只作为提示词方法层注入，不执行代码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = viewModel::resetDefaults,
            modifier = Modifier.fillMaxWidth(),
            shape = LanghuanShape.card,
            border = BorderStroke(1.dp, t.border),
        ) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("恢复推荐 Skill 绑定")
        }
    }

    confirmingUpdate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (state.updatingSkillId == null) confirmingUpdate = null },
            shape = LanghuanShape.sheet,
            containerColor = t.background,
            icon = { Icon(Icons.Rounded.SystemUpdateAlt, null, tint = t.accent) },
            title = { Text("更新 Skill？", color = t.foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${candidate.currentVersion} → ${candidate.remoteVersion}", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                    candidate.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = t.foreground) }
                    Text(
                        "只更新声明式写作规则，不下载或运行脚本/代码。现有启用状态与任务绑定会保留；内置 Skill 不能借更新扩大执行权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                    if (candidate.sourceUrl.isNotBlank()) {
                        Text(candidate.sourceUrl, style = MaterialTheme.typography.labelSmall, color = t.accent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.updatingSkillId == null,
                    onClick = {
                        viewModel.applyUpdate(candidate)
                        confirmingUpdate = null
                    },
                ) { Text("确认更新") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUpdate = null }, enabled = state.updatingSkillId == null) { Text("取消") }
            },
        )
    }

    deleting?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = LanghuanShape.sheet,
            containerColor = t.background,
            title = { Text("卸载 ${skill.name}？", color = t.foreground) },
            text = { Text("只会移除这个用户 Skill 和它的任务绑定，不会影响小说数据、Canon 或正文。", color = t.mutedForeground) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(skill.id)
                    deleting = null
                }) { Text("卸载", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SkillUpdateCenterV8(
    state: WritingSkillUiState,
    onCheck: () -> Unit,
    onUpdate: (WritingSkillUpdateCandidate) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = LanghuanShape.chip,
                    color = t.muted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp), tint = t.mutedForeground)
                    }
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("Skill 更新中心", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                    Text("发现变化后由你确认，不静默覆盖。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                Button(
                    onClick = onCheck,
                    enabled = !state.isCheckingUpdates && state.updatingSkillId == null,
                    shape = LanghuanShape.chip,
                    colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                ) {
                    if (state.isCheckingUpdates) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp, color = t.primaryForeground)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Text(if (state.isCheckingUpdates) "检查中" else "检查更新", Modifier.padding(start = 5.dp))
                }
            }

            state.updateCandidates.values.forEach { candidate ->
                Surface(
                    shape = LanghuanShape.chip,
                    color = t.warmSurface,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .16f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NewReleases, null, Modifier.size(17.dp), tint = t.accent)
                            Text(candidate.skillId, Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.titleSmall, color = t.foreground)
                            LanghuanBadge("${candidate.currentVersion} → ${candidate.remoteVersion}", accent = true)
                        }
                        Text(candidate.changes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDismiss(candidate.skillId) }) { Text("稍后", color = t.mutedForeground) }
                            TextButton(onClick = { onUpdate(candidate) }) { Text("查看并更新", color = t.accent) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillGroup(
    title: String,
    items: List<WritingSkillUiItem>,
    viewModel: WritingSkillViewModel,
    onDelete: (WritingSkillDefinition) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
        items.forEach { item ->
            val skill = item.definition
            val binding = item.binding
            val activeTasks = skill.supportedTasks.filter { it in binding.tasks }
            val actuallyActive = binding.enabled && activeTasks.isNotEmpty()

            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = LanghuanShape.chip,
                            color = if (actuallyActive) t.warmSurface else t.muted,
                            border = BorderStroke(1.dp, if (actuallyActive) t.accent.copy(alpha = .16f) else t.border),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (actuallyActive) Icons.Rounded.AutoStories else Icons.Rounded.BookmarkBorder,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = if (actuallyActive) t.accent else t.mutedForeground,
                                )
                            }
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(skill.name, style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                Spacer(Modifier.width(7.dp))
                                LanghuanBadge(if (skill.builtin) "内置" else "用户", accent = !skill.builtin)
                            }
                            Text(
                                buildString {
                                    append(skill.version)
                                    if (skill.author.isNotBlank()) append(" · ${skill.author}")
                                    append(" · ${skill.license}")
                                    if (skill.updateUrl.isNotBlank()) append(" · 可更新")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = t.mutedForeground,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (binding.enabled) "总开关：开" else "总开关：关",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (binding.enabled) t.accent else t.mutedForeground,
                            )
                            Switch(
                                checked = binding.enabled,
                                onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LanghuanShape.chip,
                        color = when {
                            actuallyActive -> t.warmSurface
                            binding.enabled -> t.destructive.copy(alpha = .05f)
                            else -> t.muted
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                actuallyActive -> t.accent.copy(alpha = .16f)
                                binding.enabled -> t.destructive.copy(alpha = .12f)
                                else -> t.border
                            },
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    actuallyActive -> Icons.Rounded.CheckCircle
                                    binding.enabled -> Icons.Rounded.WarningAmber
                                    else -> Icons.Rounded.PauseCircle
                                },
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = when {
                                    actuallyActive -> t.accent
                                    binding.enabled -> t.destructive
                                    else -> t.mutedForeground
                                },
                            )
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        actuallyActive -> "正在生效"
                                        binding.enabled -> "不会生效"
                                        else -> "已关闭"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = t.foreground,
                                )
                                Text(
                                    when {
                                        actuallyActive -> "实际调用任务：${activeTasks.joinToString("、") { it.label }}"
                                        binding.enabled -> "总开关虽然已开，但没有绑定任何任务。"
                                        else -> "当前不会注入任何写作任务。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }

                    if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    if (skill.sourceUrl.isNotBlank()) Text(skill.sourceUrl, style = MaterialTheme.typography.labelSmall, color = t.accent)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("任务绑定", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                        Spacer(Modifier.weight(1f))
                        LanghuanBadge("${activeTasks.size}/${skill.supportedTasks.size} 已选", accent = activeTasks.isNotEmpty())
                    }
                    Text(
                        if (binding.enabled) "黑色/强调状态表示真正选中；未选任务不会调用这个 Skill。" else "先开启总开关，再选择要作用的任务。",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.mutedForeground,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        skill.supportedTasks.forEach { task ->
                            val selected = task in binding.tasks
                            SkillTaskToggle(
                                label = task.label,
                                selected = selected,
                                enabled = binding.enabled,
                                onClick = { viewModel.setTaskEnabled(skill.id, task, !selected) },
                            )
                        }
                    }

                    if (!skill.builtin) {
                        Surface(Modifier.fillMaxWidth().height(1.dp), color = t.border) {}
                        TextButton(onClick = { onDelete(skill) }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(17.dp), tint = t.destructive)
                            Spacer(Modifier.width(5.dp))
                            Text("卸载", color = t.destructive)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillTaskToggle(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = LanghuanShape.chip,
        color = when {
            !enabled -> t.muted
            selected -> t.foreground
            else -> t.card
        },
        contentColor = when {
            !enabled -> t.mutedForeground
            selected -> t.primaryForeground
            else -> t.foreground
        },
        border = BorderStroke(1.dp, if (selected && enabled) t.foreground else t.border),
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                Modifier.size(16.dp),
                tint = when {
                    !enabled -> t.mutedForeground
                    selected -> t.primaryForeground
                    else -> t.mutedForeground
                },
            )
            Text(
                label,
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
