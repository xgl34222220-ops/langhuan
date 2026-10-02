package com.xiguli.langhuan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.*

/** The reading-first profile. Existing creative data and routes remain available on demand. */
@Composable
internal fun ReaderProfileV41(
    nickname: String,
    bookCount: Int,
    onEditProfile: () -> Unit,
    onHistory: () -> Unit,
    onShelfManager: () -> Unit,
    onSources: () -> Unit,
    onAiSetup: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    onBlankBook: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) = PaperReaderThemeV44 {
    val p = PaperReaderPaletteV44
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(p.background).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 24.dp),
    ) {
        PaperPageTitleV44("我的", Modifier.padding(bottom = 24.dp))
        PaperCardV44(Modifier.fillMaxWidth(), contentPadding = 0.dp, onClick = onEditProfile) {
            Box(Modifier.fillMaxWidth().heightIn(min = 126.dp)) {
                PaperProfileLandscapeV44(Modifier.matchParentSize())
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 23.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.matchParentSize()) {
                            drawCircle(Color(0xFFEAE6DD), radius = size.minDimension * .44f, center = Offset(size.width * .53f, size.height * .43f))
                        }
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(40.dp), tint = p.ink)
                    }
                    Column(Modifier.padding(start = 18.dp).weight(1f)) {
                        Text("琅嬛", color = p.ink, fontFamily = FontFamily.Serif, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("阅读，保持简单", Modifier.padding(top = 5.dp), color = p.ink.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
                        Text("$nickname · $bookCount 本藏书", Modifier.padding(top = 9.dp), color = p.muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, "编辑资料", Modifier.size(19.dp), tint = p.muted)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PaperCardV44(Modifier.fillMaxWidth(), contentPadding = 4.dp) {
            PaperMenuRowV44(Icons.Rounded.History, "阅读记录", onHistory, subtitle = "接着上次的故事读下去")
            PaperDividerV44(Modifier.padding(horizontal = 16.dp))
            PaperMenuRowV44(Icons.Rounded.Book, "书架管理", onShelfManager, subtitle = "整理分组，让好书各得其所")
            PaperDividerV44(Modifier.padding(horizontal = 16.dp))
            PaperMenuRowV44(Icons.Rounded.FolderOpen, "导入本地书籍", onImport, subtitle = "TXT · EPUB · Markdown")
        }
        Spacer(Modifier.height(18.dp))
        PaperCardV44(Modifier.fillMaxWidth(), contentPadding = 4.dp) {
            PaperMenuRowV44(Icons.Rounded.Public, "书源管理", onSources, subtitle = "导入、启用与整理书源")
            PaperDividerV44(Modifier.padding(horizontal = 16.dp))
            PaperMenuRowV44(Icons.Rounded.SmartToy, "AI 配置", onAiSetup, subtitle = "管理服务、模型与连接")
        }
        Spacer(Modifier.height(18.dp))
        PaperCardV44(Modifier.fillMaxWidth(), contentPadding = 4.dp) {
            PaperMenuRowV44(
                Icons.Rounded.Description, "旧创作工具", { advanced = !advanced },
                subtitle = if (advanced) "收起创作与任务入口" else "创作、任务与技能",
                trailingIcon = if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            )
            AnimatedVisibility(advanced) {
                Column {
                    PaperDividerV44(Modifier.padding(horizontal = 16.dp))
                    PaperMenuRowV44(Icons.Rounded.Edit, "空白新书", onBlankBook, subtitle = "从一个故事开始写起")
                    PaperMenuRowV44(Icons.Rounded.AutoAwesome, "AI 创作", onCreate, subtitle = "继续原有创作工作流")
                    PaperMenuRowV44(Icons.Rounded.TaskAlt, "运行与任务", onRunCenter)
                    PaperMenuRowV44(Icons.Rounded.Tune, "创作技能", onSkills)
                }
            }
        }
        Text("一卷在手，心有远山", Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp), color = p.muted.copy(alpha = .65f), fontFamily = FontFamily.Serif, fontSize = 12.sp)
        // v3: 每日阅读目标。
        Spacer(Modifier.height(18.dp))
        PaperCardV44(Modifier.fillMaxWidth()) {
            ReaderDailyGoalPanelV50()
        }
    }
}

/** Decorative ink-wash geometry; it never substitutes for a real book cover or user data. */
@Composable
private fun PaperProfileLandscapeV44(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        listOf(.07f, .055f, .035f).forEachIndexed { index, opacity ->
            val inset = index * .05f
            val ridge = Path().apply {
                moveTo(w * (.47f - inset), h)
                cubicTo(w * .63f, h * (.78f - inset), w * .60f, h * (.55f + inset), w * .72f, h * (.66f + inset))
                cubicTo(w * .81f, h * (.70f + inset), w * .82f, h * (.13f + inset), w * .90f, h * (.38f + inset))
                cubicTo(w * .95f, h * (.24f + inset), w * .98f, h * (.41f + inset), w, h * (.33f + inset))
                lineTo(w, h)
                close()
            }
            drawPath(ridge, Brush.verticalGradient(listOf(Color(0xFF55748A).copy(alpha = opacity), Color.Transparent)))
        }
    }
}
