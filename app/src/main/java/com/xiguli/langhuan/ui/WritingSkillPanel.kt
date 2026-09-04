package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
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
    val enabledCount = state.skills.count { it.binding.enabled && it.binding.tasks.isNotEmpty() }
    var expandedSkillId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<WritingSkillDefinition?>(null) }
    var confirmingUpdate by remember { mutableStateOf<WritingSkillUpdateCandidate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.size(34.dp), shape = CircleShape, color = t.accent) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = t.accentForeground)
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("$enabledCount 个写作能力正在生效", style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("能力与小说事实分离，只在绑定任务中调用", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }

        SkillUpdateCenter(
            state = state,
            onCheck = viewModel::checkUpdates,
            onUpdate = { confirmingUpdate = it },
            onDismiss = viewModel::dismissUpdate,
        )

        SkillGroup(
            title = "内置能力",
            items = builtins,
            expandedSkillId = expandedSkillId,
            onExpand = { expandedSkillId = if (expandedSkillId == it) null else it },
            viewModel = viewModel,
            onDelete = {},
        )

        if (installed.isNotEmpty()) {
            SkillGroup(
                title = "我的能力",
                items = installed,
                expandedSkillId = expandedSkillId,
                onExpand = { expandedSkillId = if (expandedSkillId == it) null else it },
                viewModel = viewModel,
                onDelete = { deleting = it },
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("还没有自定义能力", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                Text("可从上方导入声明式 Skill；不会执行脚本或修改小说 Canon。", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }

        TextButton(onClick = viewModel::resetDefaults, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp), tint = t.mutedForeground)
            Text("恢复推荐绑定", Modifier.padding(start = 6.dp), color = t.mutedForeground)
        }
    }

    confirmingUpdate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (state.updatingSkillId == null) confirmingUpdate = null },
            shape = RoundedCornerShape(t.radiusXl),
            containerColor = t.background,
            icon = { Icon(Icons.Rounded.SystemUpdateAlt, null, tint = t.accent) },
            title = { Text("更新写作能力？", color = t.foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${candidate.currentVersion} → ${candidate.remoteVersion}", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                    candidate.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = t.foreground) }
                    Text("只更新声明式写作规则；现有启用状态与任务绑定会保留。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.updatingSkillId == null,
                    onClick = {
                        viewModel.applyUpdate(candidate)
                        confirmingUpdate = null
                    },
                ) { Text("更新") }
            },
            dismissButton = { TextButton(onClick = { confirmingUpdate = null }) { Text("取消") } },
        )
    }

    deleting?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(t.radiusXl),
            containerColor = t.background,
            title = { Text("卸载 ${skill.name}？", color = t.foreground) },
            text = { Text("只移除这个用户 Skill 和任务绑定，不影响小说正文与数据。", color = t.mutedForeground) },
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
private fun SkillUpdateCenter(
    state: WritingSkillUiState,
    onCheck: () -> Unit,
    onUpdate: (WritingSkillUpdateCandidate) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = t.card) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null, Modifier.size(19.dp), tint = t.mutedForeground)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("更新", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                    Text("只在你确认后应用", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                TextButton(onClick = onCheck, enabled = !state.isCheckingUpdates && state.updatingSkillId == null) {
                    if (state.isCheckingUpdates) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                    Text(if (state.isCheckingUpdates) "检查中" else "检查", Modifier.padding(start = 4.dp))
                }
            }

            state.updateCandidates.values.forEach { candidate ->
                HorizontalDivider(color = t.border.copy(alpha = .45f), modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NewReleases, null, Modifier.size(18.dp), tint = t.accent)
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(candidate.skillId, style = MaterialTheme.typography.bodyMedium, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${candidate.currentVersion} → ${candidate.remoteVersion}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    TextButton(onClick = { onDismiss(candidate.skillId) }) { Text("稍后", color = t.mutedForeground) }
                    TextButton(onClick = { onUpdate(candidate) }) { Text("查看") }
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
    expandedSkillId: String?,
    onExpand: (String) -> Unit,
    viewModel: WritingSkillViewModel,
    onDelete: (WritingSkillDefinition) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground, fontWeight = FontWeight.Medium)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = t.card) {
            Column {
                items.forEachIndexed { index, item ->
                    val skill = item.definition
                    val binding = item.binding
                    val activeTasks = skill.supportedTasks.filter { it in binding.tasks }
                    val active = binding.enabled && activeTasks.isNotEmpty()
                    val expanded = expandedSkillId == skill.id

                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onExpand(skill.id) }.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = if (active) t.accent else t.muted) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (active) Icons.Rounded.AutoStories else Icons.Rounded.PauseCircle,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = if (active) t.accentForeground else t.mutedForeground,
                                    )
                                }
                            }
                            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        active -> "作用于 ${activeTasks.size} 个任务"
                                        binding.enabled -> "已开启 · 尚未绑定任务"
                                        else -> "已关闭"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) t.accentForeground else t.mutedForeground,
                                )
                            }
                            Switch(checked = binding.enabled, onCheckedChange = { viewModel.setEnabled(skill.id, it) })
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 2.dp).size(20.dp), tint = t.mutedForeground)
                        }

                        if (expanded) {
                            Column(Modifier.fillMaxWidth().padding(start = 61.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (skill.description.isNotBlank()) {
                                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                }
                                Text(
                                    buildString {
                                        append(skill.version)
                                        if (skill.author.isNotBlank()) append(" · ${skill.author}")
                                        if (skill.license.isNotBlank()) append(" · ${skill.license}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = t.mutedForeground,
                                )
                                Text("作用任务", style = MaterialTheme.typography.labelMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                                    TextButton(onClick = { onDelete(skill) }, modifier = Modifier.align(Alignment.End)) {
                                        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp), tint = t.destructive)
                                        Text("卸载", Modifier.padding(start = 5.dp), color = t.destructive)
                                    }
                                }
                            }
                        }
                    }

                    if (index != items.lastIndex) HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 61.dp))
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
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> t.muted.copy(alpha = .65f)
            selected -> t.accent
            else -> t.background
        },
        contentColor = when {
            !enabled -> t.mutedForeground
            selected -> t.accentForeground
            else -> t.foreground
        },
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp))
            Text(label, Modifier.padding(start = if (selected) 5.dp else 0.dp), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}
