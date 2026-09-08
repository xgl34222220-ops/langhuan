package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.design.LanghuanAmbientBackdrop
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanOrb
import com.xiguli.langhuan.ui.design.LanghuanSpatialHero
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

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
            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = false)

            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onClose)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("写作能力", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text("可组合的 Writing Skills", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    LanghuanIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "导入 Skill",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    LanghuanSpatialHero(
                        title = "把能力交给系统自动调度",
                        subtitle = "Skill 只负责“怎么写”；剧情事实仍由大纲、世界、角色与 Canon 管理。",
                        eyebrow = "SKILL LIBRARY",
                        modifier = Modifier.fillMaxWidth(),
                        active = false,
                        trailing = {
                            LanghuanOrb(
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
                                active = false,
                                size = 46.dp,
                            )
                        },
                    ) {
                        Text(
                            "启用、任务绑定、更新与导入全部沿用现有逻辑。",
                            Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }

                    WritingSkillPanel(viewModel)
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            )
        }
    }
}
