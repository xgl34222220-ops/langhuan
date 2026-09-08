package com.xiguli.langhuan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Settings information architecture for the Literary MIUIx / Langhuan Glass redesign.
 *
 * Only rows with real destinations are interactive in V1. The remaining groups deliberately show
 * current product behavior as summaries instead of shipping decorative switches that do nothing.
 */
@Composable
fun LanghuanSettingsExperienceV1(
    aiReady: Boolean,
    onBack: () -> Unit,
    onAi: () -> Unit,
    onSkills: () -> Unit,
    onRunCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = t.background,
        contentColor = t.foreground,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsHeader(onBack = onBack)
            }

            item {
                SettingsHero(aiReady = aiReady)
            }

            item { SettingsSectionLabel("AI 与模型") }
            item {
                LanghuanCard(contentPadding = 0.dp, depth = 2) {
                    Column {
                        LanghuanMenuRow(
                            icon = Icons.Rounded.Settings,
                            title = "AI 服务",
                            subtitle = if (aiReady) "已连接 · 模型、中转站与路由" else "尚未配置 · 点击连接 AI 服务",
                            onClick = onAi,
                            trailing = { SettingsTrailingBadge(if (aiReady) "可用" else "待配置", aiReady) },
                        )
                        LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        LanghuanMenuRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "写作能力",
                            subtitle = "Skill、导演、章纲与创作工具",
                            onClick = onSkills,
                            trailing = { SettingsChevron() },
                        )
                        LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        LanghuanMenuRow(
                            icon = Icons.Rounded.TaskAlt,
                            title = "运行中心",
                            subtitle = "查看当前任务、执行状态与历史",
                            onClick = onRunCenter,
                            trailing = { SettingsChevron() },
                        )
                    }
                }
            }

            item { SettingsSectionLabel("创作与记忆") }
            item {
                SettingsSummaryCard(
                    rows = listOf(
                        SettingsSummary(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "项目记忆",
                            value = "随作品维护",
                            subtitle = "角色、时间线、伏笔与长期上下文由作品工作流统一管理",
                        ),
                        SettingsSummary(
                            icon = Icons.Rounded.WorkspacePremium,
                            title = "创作模式",
                            value = "正常对话",
                            subtitle = "由琅嬛内部自动选择 Skill / Agent / Tool，不要求手动堆开关",
                        ),
                    ),
                )
            }

            item { SettingsSectionLabel("阅读") }
            item {
                SettingsSummaryCard(
                    rows = listOf(
                        SettingsSummary(
                            icon = Icons.Rounded.Book,
                            title = "阅读体验",
                            value = "书内设置",
                            subtitle = "字号、字体、主题、亮度与翻页方式在阅读器内就近调整",
                        ),
                    ),
                )
            }

            item { SettingsSectionLabel("外观") }
            item {
                SettingsSummaryCard(
                    rows = listOf(
                        SettingsSummary(
                            icon = Icons.Rounded.Settings,
                            title = "视觉语言",
                            value = "Langhuan Glass",
                            subtitle = "暖纸背景、琥珀强调、淡青辅助、Squircle 与柔和分层阴影",
                        ),
                        SettingsSummary(
                            icon = Icons.Rounded.Settings,
                            title = "深色模式",
                            value = "跟随系统",
                            subtitle = "默认保持琅嬛自身色彩，不被系统动态取色覆盖",
                        ),
                    ),
                )
            }

            item { SettingsSectionLabel("数据与备份") }
            item {
                SettingsSummaryCard(
                    rows = listOf(
                        SettingsSummary(
                            icon = Icons.Rounded.CloudSync,
                            title = "作品数据",
                            value = "本地优先",
                            subtitle = "项目备份与恢复继续使用现有安全链路，不在 UI 重构中改动数据层",
                        ),
                    ),
                )
            }

            item { SettingsSectionLabel("关于琅嬛") }
            item {
                SettingsSummaryCard(
                    rows = listOf(
                        SettingsSummary(
                            icon = Icons.Rounded.Book,
                            title = "琅嬛",
                            value = "阅读 × 创作 × AI",
                            subtitle = "让阅读保持安静，让创作能力在需要时自然展开",
                        ),
                    ),
                )
            }

            item {
                Text(
                    text = "设置项会随着后续页面迁移逐步接入真实状态；V1 不添加无效装饰开关。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .navigationBarsPadding(),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanghuanIconButton(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = t.foreground,
            )
            Text(
                text = "琅嬛的模型、创作、阅读与数据",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun SettingsHero(aiReady: Boolean) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 18.dp,
        depth = 2,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "安静地阅读，自然地创作",
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                    )
                    Text(
                        text = "常用状态放在前面，复杂能力只在需要时展开。",
                        modifier = Modifier.padding(top = 7.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
                LanghuanBadge(
                    text = if (aiReady) "AI 已就绪" else "AI 待配置",
                    accent = aiReady,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanghuanBadge(text = "暖纸", accent = true)
                LanghuanBadge(text = "液态玻璃")
                LanghuanBadge(text = "本地优先")
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = title,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 1.dp),
        style = MaterialTheme.typography.titleSmall,
        color = t.mutedForeground,
        fontWeight = FontWeight.Medium,
    )
}

private data class SettingsSummary(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val subtitle: String,
)

@Composable
private fun SettingsSummaryCard(rows: List<SettingsSummary>) {
    LanghuanCard(contentPadding = 0.dp, depth = 1) {
        Column {
            rows.forEachIndexed { index, row ->
                SettingsSummaryRow(row)
                if (index != rows.lastIndex) {
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSummaryRow(row: SettingsSummary) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = t.strong,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.foreground,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            Text(
                text = row.subtitle,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun SettingsTrailingBadge(text: String, active: Boolean) {
    LanghuanBadge(text = text, accent = active)
}

@Composable
private fun SettingsChevron() {
    val t = LocalLanghuanUiTokens.current
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = t.mutedForeground,
    )
}
