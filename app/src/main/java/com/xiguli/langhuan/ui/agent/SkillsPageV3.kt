package com.xiguli.langhuan.ui.agent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.writing.WritingSkillPanel
import com.xiguli.langhuan.ui.writing.WritingSkillViewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import com.xiguli.langhuan.ui.shell.verticalScroll

@Composable
fun SkillsPageV3(
    viewModel: WritingSkillViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val t = LocalLanghuanUiTokens.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSkill(uri)
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearNotice()
        }
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanghuanIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onClose,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            "Skills",
                            style = MaterialTheme.typography.headlineMedium,
                            color = t.foreground,
                        )
                        Text(
                            "安装、更新、启用与分配写作方法",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                    LanghuanBadge("WRITING", accent = true)
                }

                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = LanghuanShape.chip,
                                color = t.muted,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AutoStories,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = t.foreground,
                                    )
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    "Skill 是写作方法，不是剧情事实",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = t.foreground,
                                )
                                Text(
                                    "能力层与 Canon / 时间线 / 人物认知保持分离",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            "用户 Skill 只允许导入或在线拉取声明式 JSON 提示词，不执行脚本或代码；它不会覆盖 Canon、章节合同、人物认知边界或时间线。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.mutedForeground,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = LanghuanShape.card,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = t.foreground,
                                contentColor = t.primaryForeground,
                            ),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("导入 Skill", fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "自定义 Skill 可提供 HTTPS updateUrl。在线更新会先展示版本与来源变化，确认后才应用，并保留现有启用状态和任务绑定。",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                WritingSkillPanel(viewModel)
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
            )
        }
    }
}
