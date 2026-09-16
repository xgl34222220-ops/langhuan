package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
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

    Scaffold(
        containerColor = t.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("AI 服务", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                    Text(
                        activeProvider?.let { "${it.name} · ${it.model}" } ?: "还没有配置可用服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.ready) t.accentForeground else t.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (p.ready) LanghuanBadge("已连接", accent = true)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (p.savedProviders.isNotEmpty()) {
                item {
                    AiSectionHeader("服务", "点按服务即可切换；模型可以单独快速切换")
                    Spacer(Modifier.height(8.dp))
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                        p.savedProviders.forEach { provider ->
                            val active = provider.id == p.activeProviderId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.activateProvider(provider.id) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(t.radiusSm),
                                    color = if (active) t.accent else t.muted,
                                    contentColor = if (active) t.accentForeground else t.mutedForeground,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                                            null,
                                            Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            provider.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = t.foreground,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (active) {
                                            Spacer(Modifier.width(7.dp))
                                            LanghuanBadge("当前", accent = true)
                                        }
                                    }
                                    Text(
                                        "${provider.model} · ${provider.protocol.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = t.mutedForeground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (active) {
                                    ProviderMiniAction(
                                        icon = Icons.Rounded.Tune,
                                        label = "模型",
                                        onClick = { quickProviderId = provider.id },
                                    )
                                }
                                ProviderMiniAction(
                                    icon = Icons.Rounded.Edit,
                                    label = "编辑",
                                    onClick = {
                                        vm.editProvider(provider.id)
                                        showConnectionEditor = true
                                    },
                                )
                                ProviderMiniAction(
                                    icon = Icons.Rounded.DeleteOutline,
                                    label = "删除",
                                    destructive = true,
                                    onClick = { vm.deleteProvider(provider.id) },
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            vm.newProvider()
                            showConnectionEditor = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusMd),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加 AI 服务")
                    }
                }

                item {
                    AiSectionHeader("高级", "按任务分配模型；不设置时继承当前服务")
                    Spacer(Modifier.height(8.dp))
                    LanghuanCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRouting = !showRouting },
                        contentPadding = 14.dp,
                        depth = 0,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(t.radiusSm),
                                color = t.muted,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Hub, null, Modifier.size(20.dp), tint = t.strong)
                                }
                            }
                            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                Text("任务模型路由", style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium)
                                Text("长篇规划、正文、审查等任务可指定不同模型", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            Icon(if (showRouting) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = t.mutedForeground)
                        }
                    }
                    if (showRouting) {
                        Spacer(Modifier.height(10.dp))
                        TaskModelRoutingPanel(taskRoutingVm)
                    }
                }
            }

            if (showConnectionEditor || p.savedProviders.isEmpty()) {
                item {
                    AiSectionHeader(
                        if (p.editingProviderId == null) "连接服务" else "编辑服务",
                        "支持官方接口、中转站与兼容接口；先识别，也可以手动填写模型名",
                    )
                    Spacer(Modifier.height(8.dp))
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp, depth = 2) {
                        ProviderTextField(
                            value = p.providerName,
                            onValueChange = vm::setProviderName,
                            label = "名称",
                        )
                        Spacer(Modifier.height(9.dp))
                        ProviderTextField(
                            value = p.baseUrl,
                            onValueChange = vm::setBaseUrl,
                            label = "API Base URL",
                        )
                        Spacer(Modifier.height(9.dp))
                        ProviderTextField(
                            value = p.apiKey,
                            onValueChange = vm::setApiKey,
                            label = if (p.hasStoredKey) "API Key（留空沿用已保存）" else "API Key",
                            password = true,
                            leadingIcon = Icons.Rounded.Key,
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = vm::detectProvider,
                            enabled = p.baseUrl.isNotBlank() && !p.isDetecting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(t.radiusMd),
                        ) {
                            if (p.isDetecting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(if (p.isDetecting) "正在识别服务与模型" else "识别服务并读取模型")
                        }

                        p.error?.let {
                            Spacer(Modifier.height(9.dp))
                            Surface(shape = RoundedCornerShape(t.radiusSm), color = t.destructive.copy(alpha = .08f)) {
                                Text(it, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall, color = t.destructive)
                            }
                        }

                        p.discovery?.let { discovery ->
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    discovery.providerLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = t.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(7.dp))
                                LanghuanBadge(discovery.protocol.name)
                            }
                            Spacer(Modifier.height(8.dp))
                            discovery.models.take(40).forEach { model ->
                                val selected = p.selectedModel == model.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.selectModel(model) },
                                    shape = RoundedCornerShape(t.radiusSm),
                                    color = if (selected) t.accent else Color.Transparent,
                                    contentColor = if (selected) t.accentForeground else t.foreground,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Psychology,
                                            null,
                                            Modifier.size(18.dp),
                                            tint = if (selected) t.accentForeground else t.mutedForeground,
                                        )
                                        Text(
                                            model.displayName,
                                            Modifier.padding(start = 8.dp).weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (selected) t.accentForeground else t.foreground,
                                        )
                                        if (selected) Icon(Icons.Rounded.Check, "已选择", Modifier.size(18.dp), tint = t.accentForeground)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            ProviderTextField(
                                value = p.manualModel,
                                onValueChange = vm::setManualModel,
                                label = "模型名 / 部署名（可手动填写）",
                                leadingIcon = Icons.Rounded.Psychology,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    vm.saveProvider()
                                    showConnectionEditor = false
                                },
                                enabled = p.transientReady && !p.isSaving,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(t.radiusMd),
                            ) {
                                if (p.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(if (p.isSaving) "正在保存" else "保存并设为当前服务")
                            }
                        }
                    }
                }
            }

            if (p.ready) {
                item {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(t.radiusMd),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("完成")
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
private fun AiSectionHeader(title: String, subtitle: String) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
        Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
}

@Composable
private fun ProviderMiniAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(t.radiusSm),
        color = if (destructive) t.destructive.copy(alpha = .07f) else t.muted,
        contentColor = if (destructive) t.destructive else t.strong,
    ) {
        Box(Modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProviderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, null) } },
        shape = RoundedCornerShape(t.radiusMd),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = t.muted,
            unfocusedContainerColor = t.muted,
            disabledContainerColor = t.muted.copy(alpha = .6f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = t.foreground,
            unfocusedTextColor = t.foreground,
            focusedLabelColor = t.accentForeground,
            unfocusedLabelColor = t.mutedForeground,
        ),
    )
}
