package com.xiguli.langhuan.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

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
    var quickProviderId by remember { mutableStateOf<String?>(null) }
    var showRouting by remember { mutableStateOf(false) }
    var showConnectionEditor by remember(p.savedProviders.size, p.editingProviderId) {
        mutableStateOf(p.savedProviders.isEmpty() || p.editingProviderId != null)
    }
    val activeProvider = p.savedProviders.firstOrNull { it.id == p.activeProviderId }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(onClick = onBack, modifier = Modifier.size(42.dp), shape = CircleShape, color = Color.Transparent) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowBack, "返回", Modifier.size(21.dp), tint = t.foreground) }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("AI 服务", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text(
                        activeProvider?.let { "${it.name} · ${it.model}" } ?: "还没有配置可用服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.ready) t.accentForeground else t.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (p.ready) Icon(Icons.Rounded.CheckCircle, "已连接", Modifier.size(20.dp), tint = t.accentForeground)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (p.savedProviders.isNotEmpty()) {
                    item {
                        SectionLabel("服务", "点按切换当前服务")
                        Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(22.dp), color = t.card) {
                            Column {
                                p.savedProviders.forEachIndexed { index, provider ->
                                    val active = provider.id == p.activeProviderId
                                    Row(
                                        Modifier.fillMaxWidth().clickable { vm.activateProvider(provider.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = if (active) t.accent else t.muted) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue, null, Modifier.size(18.dp), tint = if (active) t.accentForeground else t.mutedForeground)
                                            }
                                        }
                                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                            Text(provider.name, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${provider.model} · ${provider.protocol.name}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (active) {
                                            TextButton(onClick = { quickProviderId = provider.id }) {
                                                Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp))
                                                Text("模型", Modifier.padding(start = 3.dp))
                                            }
                                        }
                                        TextButton(onClick = {
                                            vm.editProvider(provider.id)
                                            showConnectionEditor = true
                                        }) { Icon(Icons.Rounded.Edit, "编辑", Modifier.size(18.dp), tint = t.mutedForeground) }
                                        TextButton(onClick = { vm.deleteProvider(provider.id) }) { Icon(Icons.Rounded.DeleteOutline, "删除", Modifier.size(18.dp), tint = t.destructive) }
                                    }
                                    if (index != p.savedProviders.lastIndex) HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 61.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                vm.newProvider()
                                showConnectionEditor = true
                            }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
                                Text("添加服务", Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }

                if (p.savedProviders.isNotEmpty()) {
                    item {
                        SectionLabel("高级", "需要时再展开")
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { showRouting = !showRouting },
                            shape = RoundedCornerShape(22.dp),
                            color = t.card,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = t.muted) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Hub, null, Modifier.size(18.dp), tint = t.mutedForeground) }
                                }
                                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                    Text("任务模型路由", style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium)
                                    Text("不同任务指定不同模型；默认继承当前服务", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                }
                                Icon(if (showRouting) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = t.mutedForeground)
                            }
                        }
                        if (showRouting) {
                            Box(Modifier.padding(top = 10.dp)) { TaskModelRoutingPanel(taskRoutingVm) }
                        }
                    }
                }

                if (showConnectionEditor || p.savedProviders.isEmpty()) {
                    item {
                        SectionLabel(if (p.editingProviderId == null) "连接服务" else "编辑服务", "只在新增或修改连接时填写")
                        Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(22.dp), color = t.card) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = p.providerName,
                                    onValueChange = vm::setProviderName,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("名称") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                OutlinedTextField(
                                    value = p.baseUrl,
                                    onValueChange = vm::setBaseUrl,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("API Base URL") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                OutlinedTextField(
                                    value = p.apiKey,
                                    onValueChange = vm::setApiKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(if (p.hasStoredKey) "API Key（留空沿用已保存）" else "API Key") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                    shape = RoundedCornerShape(16.dp),
                                )
                                Surface(
                                    onClick = vm::detectProvider,
                                    enabled = p.baseUrl.isNotBlank() && !p.isDetecting,
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = t.accent,
                                    contentColor = t.accentForeground,
                                ) {
                                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        if (p.isDetecting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = t.accentForeground)
                                        else Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp))
                                        Text(if (p.isDetecting) "正在读取模型" else "识别服务并读取模型", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Medium)
                                    }
                                }

                                p.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.destructive) }

                                p.discovery?.let { discovery ->
                                    Text("${discovery.providerLabel} · ${discovery.protocol.name}", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                                    discovery.models.take(40).forEachIndexed { index, model ->
                                        val selected = p.selectedModel == model.id
                                        Row(
                                            Modifier.fillMaxWidth().clickable { vm.selectModel(model) }.padding(vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp), tint = if (selected) t.accentForeground else t.mutedForeground)
                                            Text(model.displayName, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = t.foreground)
                                            if (selected) Icon(Icons.Rounded.Check, "已选择", Modifier.size(18.dp), tint = t.accentForeground)
                                        }
                                        if (index != discovery.models.take(40).lastIndex) HorizontalDivider(color = t.border.copy(alpha = .38f), modifier = Modifier.padding(start = 26.dp))
                                    }
                                    OutlinedTextField(
                                        value = p.manualModel,
                                        onValueChange = vm::setManualModel,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("模型名 / 部署名") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    Surface(
                                        onClick = {
                                            vm.saveProvider()
                                            showConnectionEditor = false
                                        },
                                        enabled = p.transientReady && !p.isSaving,
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = t.accent,
                                        contentColor = t.accentForeground,
                                    ) {
                                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                                            Text(if (p.isSaving) "正在保存" else "保存并设为当前服务", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (p.ready) {
                    item {
                        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                            Text("完成", Modifier.padding(start = 6.dp), fontWeight = FontWeight.Medium)
                        }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
        Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
}
