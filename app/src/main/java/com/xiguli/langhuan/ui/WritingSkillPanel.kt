package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
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
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
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
                        Switch(
                            checked = binding.enabled,
                            onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                        )
                    }
                    if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodySmall)
                    if (skill.sourceUrl.isNotBlank()) {
                        Text(skill.sourceUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (binding.enabled) {
                        Text("影响任务", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            skill.supportedTasks.forEach { task ->
                                FilterChip(
                                    selected = task in binding.tasks,
                                    onClick = { viewModel.setTaskEnabled(skill.id, task, task !in binding.tasks) },
                                    label = { Text(task.label) },
                                )
                            }
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
