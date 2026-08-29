package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SkillsPageV3(
    viewModel: WritingSkillViewModel,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("Skills", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("管理琅嬛的写作方法与任务绑定", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Skill 是写作方法，不是剧情事实", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "它只影响场景规划、正文、小说化和主编修订的写法，不会覆盖 Canon、章节合同、人物认知边界或时间线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        WritingSkillPanel(viewModel)
    }
}
