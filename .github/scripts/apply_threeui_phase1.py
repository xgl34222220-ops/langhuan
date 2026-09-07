from pathlib import Path
import re

shelf = Path('app/src/main/java/com/xiguli/langhuan/ui/AiFirstShelf.kt')
s = shelf.read_text()
anchor = 'import com.xiguli.langhuan.engine.ReferenceDistillationSourceStore\n'
assert anchor in s
s = s.replace(anchor, anchor + 'import com.xiguli.langhuan.ui.design.LanghuanOrb\nimport com.xiguli.langhuan.ui.design.LanghuanSpatialHero\n', 1)

old_header = '''        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛书架", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text(
                        "聊出一本书 · 蒸馏参考 · 长期创作",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.stories.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onClick = onCloseShelf) { Icon(Icons.Rounded.Close, "进入工作台") }
                    }
                }
            }
        }
'''
new_header = '''        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanghuanOrb(active = !state.isBusy, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("琅嬛书架", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text(
                        "小说工作台 · AI 创作 · 阅读与长期记忆",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.stories.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onClick = onCloseShelf) { Icon(Icons.Rounded.Close, "进入工作台") }
                    }
                }
            }
        }
'''
assert old_header in s
s = s.replace(old_header, new_header, 1)

old_hero = '''        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = if (aiReady) onStartCreation else onConfigureAi,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(19.dp),
                    ) {
                        Icon(if (aiReady) Icons.Rounded.AutoAwesome else Icons.Rounded.Key, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (aiReady) "和 AI 聊出一本新小说" else "先配置 AI，再开始创作", fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledTonalButton(
                            onClick = onConfigureAi,
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Rounded.Key, null)
                            Spacer(Modifier.width(6.dp))
                            Text("AI / Key", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = if (aiReady) onDistillReference else onConfigureAi,
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("导入蒸馏", maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShelfPill("TXT / EPUB / MD")
                        ShelfPill("Style + Story DNA")
                        ShelfPill("双层断点")
                    }
                    Text(
                        "整本小说先做全书结构扫描，AI 按书长深度分层阅读；最终 DNA 再分组聚合，长篇不会把全部批次一次塞给模型。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
'''
new_hero = '''        item {
            LanghuanSpatialHero(
                title = if (aiReady) "把一个念头，长成一本书" else "先连接你的 AI",
                subtitle = if (aiReady) {
                    "自然聊天、参考蒸馏、蓝图与长期记忆都从这里开始。视觉会动，创作流程不会被动画打断。"
                } else {
                    "配置任意兼容服务后，就能从一句设定直接进入创作。"
                },
                eyebrow = if (aiReady) "AI READY · ${aiModel.ifBlank { "DEFAULT MODEL" }}" else "AI SETUP REQUIRED",
                active = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    LanghuanOrb(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                        active = !state.isBusy,
                        size = 54.dp,
                    )
                },
            ) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = if (aiReady) onStartCreation else onConfigureAi,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(19.dp),
                ) {
                    Icon(if (aiReady) Icons.Rounded.AutoAwesome else Icons.Rounded.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiReady) "和 AI 聊出一本新小说" else "配置 AI 服务", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilledTonalButton(
                        onClick = onConfigureAi,
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Icon(Icons.Rounded.Key, null)
                        Spacer(Modifier.width(6.dp))
                        Text("AI / Key", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = if (aiReady) onDistillReference else onConfigureAi,
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("参考 DNA", maxLines = 1)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShelfPill("Native Motion")
                    ShelfPill("Story + Style DNA")
                    ShelfPill("断点续跑")
                }
            }
        }
'''
assert old_hero in s
s = s.replace(old_hero, new_hero, 1)
shelf.write_text(s)

chat = Path('app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt')
s = chat.read_text()
anchor = 'import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens\n'
assert anchor in s
s = s.replace(anchor, anchor + 'import com.xiguli.langhuan.ui.design.LanghuanMotionStatus\nimport com.xiguli.langhuan.ui.design.LanghuanOrb\nimport com.xiguli.langhuan.ui.design.LanghuanSpatialHero\n', 1)

old_welcome = '''@Composable
private fun CreationWelcomeV4(onAdvancedResearch: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
        Text(
            "和琅嬛聊一本书",
            style = MaterialTheme.typography.headlineLarge,
            color = t.foreground,
        )
        Text(
            "不用填表。说题材、人物、画面、参考作品，或者直接上传设定文件。我会随着对话整理方案和蓝图。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = t.mutedForeground,
        )
        Surface(
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onAdvancedResearch),
            shape = RoundedCornerShape(t.radiusMd),
            color = t.warmSurface,
            contentColor = t.accent,
            border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(17.dp))
                Text(
                    "需要拆解参考作品？进入高级研究 / Reference DNA",
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
'''
new_welcome = '''@Composable
private fun CreationWelcomeV4(onAdvancedResearch: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanSpatialHero(
        title = "和琅嬛聊一本书",
        subtitle = "不用填表。说题材、人物、画面、参考作品，或者直接上传设定文件；对话会逐步沉淀成方案和蓝图。",
        eyebrow = "CREATION SPACE",
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        trailing = {
            LanghuanOrb(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                size = 48.dp,
            )
        },
    ) {
        Surface(
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onAdvancedResearch),
            shape = RoundedCornerShape(t.radiusMd),
            color = t.card.copy(alpha = .78f),
            contentColor = t.foreground,
            border = BorderStroke(1.dp, t.border.copy(alpha = .8f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "高级研究 / Reference DNA",
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = t.foreground,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
            }
        }
    }
}
'''
assert old_welcome in s
s = s.replace(old_welcome, new_welcome, 1)

old_thinking = '''@Composable
private fun CreationThinkingV4(label: String) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 1.8.dp, color = t.accent)
        Text(
            label,
            modifier = Modifier.padding(start = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}
'''
new_thinking = '''@Composable
private fun CreationThinkingV4(label: String) {
    LanghuanMotionStatus(
        text = label,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        active = true,
    )
}
'''
assert old_thinking in s
s = s.replace(old_thinking, new_thinking, 1)

old_stream = '''                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = t.accent)
                    Text(
'''
new_stream = '''                    LanghuanOrb(active = true, size = 18.dp)
                    Text(
'''
assert old_stream in s
s = s.replace(old_stream, new_stream, 1)
chat.write_text(s)

build = Path('app/build.gradle.kts')
s = build.read_text()
s = re.sub(r'versionCode = \d+', 'versionCode = 95', s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "0.28.0-alpha16-spatial-ui"', s, count=1)
build.write_text(s)

Path('.github/workflows/apply-threeui-phase1.yml').unlink(missing_ok=True)
Path('.github/scripts/apply_threeui_phase1.py').unlink(missing_ok=True)
