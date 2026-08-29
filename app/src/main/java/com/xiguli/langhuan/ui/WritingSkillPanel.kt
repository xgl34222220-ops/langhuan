package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.SkillInstallResult
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
    val message: String? = null,
    val error: String? = null,
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
        _state.value = load().copy(message = message)
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
    val builtins = state.skills.filter { it.definition.builtin }
    val installed = state.skills.filterNot { it.definition.builtin }
    var deleting by remember { mutableStateOf<WritingSkillDefinition?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val enabledCount = state.skills.count { it.binding.enabled && it.binding.tasks.isNotEmpty() }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("当前有 $enabledCount 个 Skill 正在生效", fontWeight = FontWeight.Bold)
                    Text("每张卡片都会明确显示开关状态和实际调用任务。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SkillGroup("内置 Skills", builtins, viewModel, onDelete = {})
        if (installed.isNotEmpty()) {
            SkillGroup("我的 Skills", installed, viewModel, onDelete = { deleting = it })
        } else {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("还没有安装自定义 Skill", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "点击上方“导入 Skill”选择 .json 文件。自定义 Skill 只会作为提示词方法层注入，不会执行代码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

    deleting?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("卸载 ${skill.name}？") },
            text = { Text("只会移除这个用户 Skill 和它的任务绑定，不会影响小说数据、Canon 或正文。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(skill.id)
                    deleting = null
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        items.forEach { item ->
            val skill = item.definition
            val binding = item.binding
            val activeTasks = skill.supportedTasks.filter { it in binding.tasks }
            val actuallyActive = binding.enabled && activeTasks.isNotEmpty()

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (actuallyActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Icon(
                                if (actuallyActive) Icons.Rounded.AutoStories else Icons.Rounded.BookmarkBorder,
                                null,
                                tint = if (actuallyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(9.dp),
                            )
                        }
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text(skill.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(skill.version)
                                    if (skill.author.isNotBlank()) append(" · ${skill.author}")
                                    append(" · ${skill.license}")
                                    append(if (skill.builtin) " · 内置" else " · 用户安装")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (binding.enabled) "已启用" else "已关闭",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (binding.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = binding.enabled,
                                onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            actuallyActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            binding.enabled -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when {
                                    actuallyActive -> Icons.Rounded.CheckCircle
                                    binding.enabled -> Icons.Rounded.WarningAmber
                                    else -> Icons.Rounded.PauseCircle
                                },
                                null,
                                modifier = Modifier.size(19.dp),
                                tint = if (actuallyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when {
                                    actuallyActive -> "实际生效：${activeTasks.joinToString("、") { it.label }}"
                                    binding.enabled -> "已启用，但当前没有勾选任何任务，因此不会被调用"
                                    else -> "当前关闭，不会注入任何写作任务"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (actuallyActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }

                    if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodySmall)
                    if (skill.sourceUrl.isNotBlank()) {
                        Text(skill.sourceUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Text("任务绑定", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (binding.enabled) "带 ✓ 的任务会真正调用这个 Skill。" else "先开启总开关，再选择要作用的任务。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        skill.supportedTasks.forEach { task ->
                            val selected = task in binding.tasks
                            FilterChip(
                                selected = selected,
                                enabled = binding.enabled,
                                onClick = { viewModel.setTaskEnabled(skill.id, task, !selected) },
                                label = { Text(task.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                leadingIcon = {
                                    Icon(
                                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        null,
                                        Modifier.size(17.dp),
                                    )
                                },
                            )
                        }
                    }

                    if (!skill.builtin) {
                        HorizontalDivider()
                        TextButton(onClick = { onDelete(skill) }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(5.dp))
                            Text("卸载", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
