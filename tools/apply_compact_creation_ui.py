from pathlib import Path

repo = Path('.')
page = repo / 'app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt'
s = page.read_text()

# Expose AI/model actions to the page so they no longer float over conversation content.
old = '''fun ResearchNewBookConversationPage(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {'''
new = '''fun ResearchNewBookConversationPage(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onConfigureAi: () -> Unit,
    onSwitchModel: () -> Unit,
    onCreated: (String) -> Unit,
) {'''
assert old in s, 'page signature anchor missing'
s = s.replace(old, new, 1)

old = '    var retryFoundation by remember { mutableStateOf(false) }\n'
new = '''    var retryFoundation by remember { mutableStateOf(false) }
    var showReferenceTools by remember { mutableStateOf(false) }
    var showResearchMemory by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }
'''
assert old in s, 'state anchor missing'
s = s.replace(old, new, 1)

# Replace crowded top actions with model button + overflow menu.
start = s.index('                actions = {\n', s.index('TopAppBar('))
end = s.index('                },\n            )\n', start) + len('                },\n')
new_actions = '''                actions = {
                    IconButton(onClick = onSwitchModel) {
                        Icon(Icons.Rounded.Tune, "切换 AI 服务 / 模型")
                    }
                    Box {
                        IconButton(onClick = { topMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("AI 服务") },
                                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                onClick = {
                                    topMenuExpanded = false
                                    onConfigureAi()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重新开始") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                enabled = !state.isBusy && !researching,
                                onClick = {
                                    topMenuExpanded = false
                                    viewModel.reset()
                                    research.resetContext()
                                    archiveState = archiveStore.clearSessionContext()
                                    lastSources = emptyList()
                                    lastTargets = emptyList()
                                    researchMessage = null
                                    retryFoundation = false
                                },
                            )
                        }
                    }
                },
'''
s = s[:start] + new_actions + s[end:]

# Replace the tall bottom panel with a compact composer.
start = s.index('        bottomBar = {\n')
end = s.index('    ) { padding ->', start)
new_bottom = '''        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.pendingAttachments.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.pendingAttachments.forEach { attachment ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removePendingAttachment(attachment.id) },
                                    label = { Text(attachment.fileName, maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description,
                                            null,
                                        )
                                    },
                                    trailingIcon = { Icon(Icons.Rounded.Close, "移除附件") },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = webResearchEnabled,
                            onClick = {
                                val checked = !webResearchEnabled
                                webResearchEnabled = checked
                                researchPrefs.edit().putBoolean("web_research_enabled", checked).apply()
                                if (!checked) {
                                    lastSources = emptyList()
                                    lastTargets = emptyList()
                                    researchMessage = null
                                }
                            },
                            enabled = !researching,
                            label = { Text(if (webResearchEnabled) "联网开" else "联网关") },
                            leadingIcon = { Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)) },
                        )
                        if (archiveState.entries.isNotEmpty()) {
                            AssistChip(
                                onClick = { showResearchMemory = !showResearchMemory },
                                label = { Text("${archiveState.entries.size} 条记忆") },
                                leadingIcon = { Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(if (state.foundation == null) "说要求，或上传小说资料……" else "继续聊天修改这本书……")
                        },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            IconButton(
                                onClick = {
                                    attachmentLauncher.launch(
                                        arrayOf(
                                            "text/*", "application/json", "application/pdf", "application/epub+zip",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*",
                                        )
                                    )
                                },
                                enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                            ) {
                                Icon(Icons.Rounded.AttachFile, "上传文件")
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { submit(input) },
                                enabled = (input.isNotBlank() || state.pendingAttachments.isNotEmpty()) &&
                                    !state.isBusy && !researching && !state.isLoadingAttachments,
                            ) {
                                Icon(if (researching) Icons.Rounded.TravelExplore else Icons.Rounded.Send, "发送")
                            }
                        },
                    )
                }
            }
        },
'''
s = s[:start] + new_bottom + s[end:]

# Collapse research/template utilities by default and remove the large starter cards.
start = s.index('            if (state.messages.size <= 1) item {\n')
end = s.index('            items(state.messages) { message -> ResearchChatBubble(message) }\n', start)
new_tools = '''            if (state.messages.none { it.role == "user" }) item {
                Text(
                    "直接说你的想法，或上传设定文件开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = { showReferenceTools = !showReferenceTools },
                        label = { Text(if (showReferenceTools) "收起参考" else "参考 DNA") },
                        leadingIcon = { Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp)) },
                    )
                    if (archiveState.entries.isNotEmpty()) {
                        AssistChip(
                            onClick = { showResearchMemory = !showResearchMemory },
                            label = { Text(if (showResearchMemory) "收起记忆" else "研究记忆") },
                            leadingIcon = { Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (state.foundation == null && state.messages.any { it.role == "user" }) {
                        FilledTonalButton(
                            onClick = viewModel::syncConversationProposal,
                            enabled = !state.isBusy && !researching && !state.isLoadingAttachments,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.proposal == null) "整理方案" else "同步方案")
                        }
                    }
                }
            }
            if (showReferenceTools) item { ReferenceTemplateSelectionPanel(viewModel) }
            if (showResearchMemory && archiveState.entries.isNotEmpty()) item { ResearchArchiveMemoryCard(archiveState) }
'''
s = s[:start] + new_tools + s[end:]

# Remove the old full-width "建书方案" helper card; the compact toolbar owns this action now.
start = s.find('            if (state.foundation == null && state.messages.any { it.role == "user" }) item {\n')
if start != -1:
    end = s.index('            if (state.foundation == null) {\n', start)
    s = s[:start] + s[end:]

page.write_text(s)

# Remove creation-page floating buttons and route them through the top bar.
root = repo / 'app/src/main/java/com/xiguli/langhuan/ui/LanghuanRoot.kt'
r = root.read_text()
old = '''                    viewModel = creationViewModel,
                    onClose = { showCreation = false },
                    onCreated = { id ->'''
new = '''                    viewModel = creationViewModel,
                    onClose = { showCreation = false },
                    onConfigureAi = {
                        pendingCreationAfterAiSetup = false
                        showAiSetup = true
                    },
                    onSwitchModel = { showModelSwitch = true },
                    onCreated = { id ->'''
assert old in r, 'root creation call anchor missing'
r = r.replace(old, new, 1)

old_fabs = '''
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        pendingCreationAfterAiSetup = false
                        showAiSetup = true
                    }
                ) {
                    Icon(Icons.Rounded.Key, "管理 Key / AI 服务")
                }
                SmallFloatingActionButton(
                    onClick = { showModelSwitch = true },
                ) {
                    Icon(Icons.Rounded.Tune, "切换 AI 服务 / 模型")
                }
            }
'''
assert old_fabs in r, 'creation FAB block missing'
r = r.replace(old_fabs, '\n', 1)
root.write_text(r)

# Version bump so the compact UI build is unmistakable on-device.
gradle = repo / 'app/build.gradle.kts'
g = gradle.read_text()
assert 'versionCode = 49' in g and 'versionName = "0.25.1-alpha01"' in g
g = g.replace('versionCode = 49', 'versionCode = 50', 1)
g = g.replace('versionName = "0.25.1-alpha01"', 'versionName = "0.25.2-alpha01"', 1)
gradle.write_text(g)

print('compact creation UI patch applied')
