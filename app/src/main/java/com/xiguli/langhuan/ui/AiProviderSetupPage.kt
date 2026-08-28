package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSetupPage(
    state: StudioUiState,
    vm: StudioViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val p = state.provider
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("配置 AI 服务", fontWeight = FontWeight.SemiBold)
                        Text("先把 API 接好，再聊天建书", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Key, null)
                            Spacer(Modifier.width(9.dp))
                            Text("首次使用不再要求先进入工作台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("在这里直接添加中转站或官方 API。保存成功后即可返回书架，或直接开始“和 AI 聊一本小说”。")
                    }
                }
            }

            if (p.savedProviders.isNotEmpty()) {
                item { Text("已保存 AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(p.savedProviders, key = { it.id }) { provider ->
                    val active = provider.id == p.activeProviderId
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { vm.activateProvider(provider.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(provider.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${provider.model} · ${provider.protocol.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.editProvider(provider.id) }) { Icon(Icons.Rounded.Edit, "编辑") }
                            IconButton(onClick = { vm.deleteProvider(provider.id) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = vm::newProvider, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("添加另一个 AI 服务")
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("支持 OpenAI 兼容、Claude、Gemini、Azure 与 Ollama；常见中转站可自动探测模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(p.providerName, vm::setProviderName, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
                        OutlinedTextField(p.baseUrl, vm::setBaseUrl, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
                        OutlinedTextField(
                            p.apiKey,
                            vm::setApiKey,
                            Modifier.fillMaxWidth(),
                            label = { Text(if (p.hasStoredKey) "API Key（留空沿用已保存）" else "API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        )
                        Button(
                            onClick = vm::detectProvider,
                            enabled = p.baseUrl.isNotBlank() && !p.isDetecting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (p.isDetecting) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.TravelExplore, null)
                            Spacer(Modifier.width(7.dp)); Text(if (p.isDetecting) "正在识别并读取模型…" else "自动识别并读取模型")
                        }
                        p.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        p.discovery?.let { discovery ->
                            Text("${discovery.providerLabel} · ${discovery.protocol.name}", fontWeight = FontWeight.Bold)
                            Text(discovery.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            discovery.models.take(40).forEach { model ->
                                val selected = p.selectedModel == model.id
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { vm.selectModel(model) },
                                    shape = RoundedCornerShape(15.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
                                ) {
                                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(model.displayName, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            OutlinedTextField(p.manualModel, vm::setManualModel, Modifier.fillMaxWidth(), label = { Text("模型名 / 部署名（也可手填）") }, singleLine = true)
                            Button(
                                onClick = vm::saveProvider,
                                enabled = p.transientReady && !p.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text(if (p.isSaving) "正在保存…" else "保存并设为当前 AI")
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onDone,
                    enabled = p.ready && !p.isSaving && !p.isDetecting,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("完成配置")
                }
            }
        }
    }
}
