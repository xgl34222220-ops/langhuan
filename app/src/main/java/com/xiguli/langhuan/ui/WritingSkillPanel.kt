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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp, depth = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = RoundedCornerShape(t.radiusSm), color = t.accent) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = t.accentForeground)
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        "$enabledCount 个写作能力正在生效",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "能力与小说事实分离，只在绑定任务中调用",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                LanghuanBadge(text = "${state.skills.size} Skills", accent = enabledCount > 0)
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
            LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp, depth = 0) {
                Text("还没有自定义能力", style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.Medium)
                Text(
                    "可从右上角导入声明式 Skill；不会执行脚本或修改小说 Canon。",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }

        Surface(
            onClick = viewModel::resetDefaults,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(t.radiusMd),
            color = t.muted,
            contentColor = t.mutedForeground,
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("恢复推荐绑定", fontWeight = FontWeight.Medium)
            }
        }
    }

    confirmingUpdate?.let { candidate ->
        ModalBottomSheet(
            onDismissRequest = { if (state.updatingSkillId == null) confirmingUpdate = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(t.radiusSm), color = t.accent) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(20.dp), tint = t.accentForeground) }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("更新写作能力？", style = MaterialTheme.typography.headlineSmall, color = t.foreground, fontWeight = FontWeight.SemiBold)
                        Text("${candidate.currentVersion} → ${candidate.remoteVersion}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
                LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, depth = 0) {
                    candidate.changes.forEach { change ->
                        Text("• $change", style = MaterialTheme.typography.bodySmall, color = t.foreground, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Text(
                        "只更新声明式写作规则；现有启用状态与任务绑定会保留。",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                Button(
                    enabled = state.updatingSkillId == null,
                    onClick = {
                        viewModel.applyUpdate(candidate)
                        confirmingUpdate = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    if (state.updatingSkillId != null) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.updatingSkillId != null) "正在更新" else "确认更新")
                }
                Surface(
                    onClick = { confirmingUpdate = null },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.muted,
                    contentColor = t.foreground,
                ) { Box(contentAlignment = Alignment.Center) { Text("取消", fontWeight = FontWeight.Medium) } }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    deleting?.let { skill ->
        ModalBottomSheet(
            onDismissRequest = { deleting = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("卸载 ${skill.name}？", style = MaterialTheme.typography.headlineSmall, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("只移除这个用户 Skill 和任务绑定，不影响小说正文与数据。", style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                Button(
                    onClick = {
                        viewModel.uninstall(skill.id)
                        deleting = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("卸载 Skill")
                }
                Surface(
                    onClick = { deleting = null },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.muted,
                    contentColor = t.foreground,
                ) { Box(contentAlignment = Alignment.Center) { Text("取消", fontWeight = FontWeight.Medium) } }
                Spacer(Modifier.height(4.dp))
            }
        }
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
    LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, depth = 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(t.radiusSm), color = t.muted) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Sync, null, Modifier.size(19.dp), tint = t.mutedForeground) }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Skill 更新", style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium)
                Text("只在你确认后应用", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            LanghuanIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "检查 Skill 更新",
                onClick = onCheck,
                selected = state.updateCandidates.isNotEmpty(),
            )
        }

        if (state.isCheckingUpdates) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = t.primary)
                Text("正在检查可用更新…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }

        state.updateCandidates.values.forEach { candidate ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(t.radiusMd),
                color = t.muted,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NewReleases, null, Modifier.size(19.dp), tint = t.accentForeground)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(candidate.skillId, style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${candidate.currentVersion} → ${candidate.remoteVersion}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    Surface(
                        onClick = { onDismiss(candidate.skillId) },
                        shape = RoundedCornerShape(t.radiusSm),
                        color = t.card,
                        contentColor = t.mutedForeground,
                    ) { Text("稍后", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = { onUpdate(candidate) },
                        shape = RoundedCornerShape(t.radiusSm),
                        color = t.accent,
                        contentColor = t.accentForeground,
                    ) { Text("查看", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
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
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            val skill = item.definition
            val binding = item.binding
            val activeTasks = skill.supportedTasks.filter { it in binding.tasks }
            val active = binding.enabled && activeTasks.isNotEmpty()
            val expanded = expandedSkillId == skill.id

            LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp, depth = if (expanded) 2 else 1) {
                Row(
                    Modifier.fillMaxWidth().clickable { onExpand(skill.id) }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(Modifier.size(40.dp), shape = RoundedCornerShape(t.radiusSm), color = if (active) t.accent else t.muted) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (active) Icons.Rounded.AutoStories else Icons.Rounded.PauseCircle,
                                null,
                                Modifier.size(19.dp),
                                tint = if (active) t.accentForeground else t.mutedForeground,
                            )
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(skill.name, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp), tint = t.mutedForeground)
                }

                if (expanded) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (skill.description.isNotBlank()) {
                            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LanghuanBadge(text = skill.version)
                            if (skill.author.isNotBlank()) LanghuanBadge(text = skill.author)
                            if (skill.license.isNotBlank()) LanghuanBadge(text = skill.license)
                        }
                        Text("作用任务", style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
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
                            Surface(
                                onClick = { onDelete(skill) },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(t.radiusSm),
                                color = t.muted,
                                contentColor = t.destructive,
                            ) {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("卸载", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                                }
                            }
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
        shape = RoundedCornerShape(t.radiusSm),
        color = when {
            !enabled -> t.muted.copy(alpha = .65f)
            selected -> t.accent
            else -> t.muted
        },
        contentColor = when {
            !enabled -> t.mutedForeground
            selected -> t.accentForeground
            else -> t.foreground
        },
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp))
            Text(
                label,
                Modifier.padding(start = if (selected) 5.dp else 0.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
