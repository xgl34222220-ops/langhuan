package com.xiguli.langhuan.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.StudioUiState
import com.xiguli.langhuan.ui.StudioViewModel
import com.xiguli.langhuan.ui.agent.TaskModelRoutingPanel
import com.xiguli.langhuan.ui.agent.TaskModelRoutingViewModel
import com.xiguli.langhuan.ui.WritingSkillPanel
import com.xiguli.langhuan.ui.WritingSkillViewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

@Composable
fun AiProviderSetupPage(
    state: StudioUiState,
    vm: StudioViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val p = state.provider
    val t = LocalLanghuanUiTokens.current
    val quickModelVm: ProviderQuickSwitchViewModel = viewModel()
    val taskRoutingVm: TaskModelRoutingViewModel = viewModel()
    val writingSkillVm: WritingSkillViewModel = viewModel()
    var quickProviderId by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("AI 服务", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                    Text("API、中转站、模型与任务路由", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge(if (p.ready) "READY" else "SETUP", accent = p.ready)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = LanghuanShape.chip,
                                color = t.warmSurface,
                                contentColor = t.accent,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Key, null, Modifier.size(20.dp), tint = t.accent)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("直接管理 AI 服务", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                Text(
                                    "在这里添加中转站或官方 API。已有服务只想换模型时，不需要重新编辑 URL 和 Key。",
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }
                }

                if (p.savedProviders.isNotEmpty()) {
                    item { SectionLabel("已保存 AI", "点击卡片即可切换当前服务") }
                    items(p.savedProviders, key = { it.id }) { provider ->
                        val active = provider.id == p.activeProviderId
                        LanghuanCard(
                            modifier = Modifier.fillMaxWidth().clickable { vm.activateProvider(provider.id) },
                            contentPadding = 14.dp,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = LanghuanShape.chip,
                                        color = if (active) t.warmSurface else t.muted,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                                                null,
                                                Modifier.size(20.dp),
                                                tint = if (active) t.accent else t.mutedForeground,
                                            )
                                        }
                                    }
                                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                provider.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = t.foreground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (active) LanghuanBadge("当前", Modifier.padding(start = 8.dp), accent = true)
                                        }
                                        Text(
                                            "${provider.model} · ${provider.protocol.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = t.mutedForeground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = { vm.editProvider(provider.id) }) {
                                        Icon(Icons.Rounded.Edit, "编辑 API 服务", tint = t.mutedForeground)
                                    }
                                    IconButton(onClick = { vm.deleteProvider(provider.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive)
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        vm.activateProvider(provider.id)
                                        quickProviderId = provider.id
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = LanghuanShape.chip,
                                ) {
                                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("换模型")
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = vm::newProvider,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = LanghuanShape.card,
                            border = BorderStroke(1.dp, t.border),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("添加另一个 AI 服务")
                        }
                    }
                }

                if (p.savedProviders.isNotEmpty()) {
                    item { TaskModelRoutingPanel(taskRoutingVm) }
                }

                item { WritingSkillPanel(writingSkillVm) }

                item { SectionLabel(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务", "URL、Key、协议与模型发现") }
                item {
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "已有服务单纯换模型请使用上方“换模型”；这里用于修改连接本身。",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                            OutlinedTextField(
                                value = p.providerName,
                                onValueChange = vm::setProviderName,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("名称") },
                                singleLine = true,
                                shape = LanghuanShape.card,
                            )
                            OutlinedTextField(
                                value = p.baseUrl,
                                onValueChange = vm::setBaseUrl,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API Base URL") },
                                singleLine = true,
                                shape = LanghuanShape.card,
                            )
                            OutlinedTextField(
                                value = p.apiKey,
                                onValueChange = vm::setApiKey,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (p.hasStoredKey) "API Key（留空沿用已保存）" else "API Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                shape = LanghuanShape.card,
                            )
                            Button(
                                onClick = vm::detectProvider,
                                enabled = p.baseUrl.isNotBlank() && !p.isDetecting,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = LanghuanShape.card,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = t.foreground,
                                    contentColor = t.primaryForeground,
                                ),
                            ) {
                                if (p.isDetecting) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        color = t.primaryForeground,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(if (p.isDetecting) "正在识别并读取模型…" else "自动识别并读取模型")
                            }

                            p.error?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = t.destructive)
                            }

                            p.discovery?.let { discovery ->
                                Text(
                                    "${discovery.providerLabel} · ${discovery.protocol.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = t.foreground,
                                )
                                Text(discovery.message, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)

                                discovery.models.take(40).forEach { model ->
                                    val selected = p.selectedModel == model.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { vm.selectModel(model) },
                                        shape = LanghuanShape.chip,
                                        color = if (selected) t.warmSurface else t.muted,
                                        border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .35f) else t.border),
                                    ) {
                                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.Psychology,
                                                null,
                                                Modifier.size(18.dp),
                                                tint = if (selected) t.accent else t.mutedForeground,
                                            )
                                            Text(
                                                model.displayName,
                                                Modifier.padding(start = 8.dp).weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = t.foreground,
                                            )
                                            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = t.accent)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = p.manualModel,
                                    onValueChange = vm::setManualModel,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("模型名 / 部署名（也可手填）") },
                                    singleLine = true,
                                    shape = LanghuanShape.card,
                                )
                                Button(
                                    onClick = vm::saveProvider,
                                    enabled = p.transientReady && !p.isSaving,
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = LanghuanShape.card,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = t.foreground,
                                        contentColor = t.primaryForeground,
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (p.isSaving) "正在保存…" else "保存并设为当前 AI")
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onDone,
                        enabled = p.ready && !p.isSaving && !p.isDetecting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = LanghuanShape.card,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = t.foreground,
                            contentColor = t.primaryForeground,
                        ),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("完成配置", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    quickProviderId?.let { providerId ->
        ProviderQuickSwitchSheet(
            viewModel = quickModelVm,
            preferredProviderId = providerId,
            onProviderActivated = vm::activateProvider,
            onDismiss = { quickProviderId = null },
        )
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
}
