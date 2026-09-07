from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: source block not found")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Creation chat: remove opaque composer band, clean basic Markdown markers.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
s = path.read_text()
if "import androidx.compose.ui.graphics.Color\n" not in s:
    s = replace_once(
        s,
        "import androidx.compose.ui.graphics.SolidColor\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.SolidColor\n",
        "creation Color import",
    )
s = replace_once(
    s,
    "Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp, color = t.foreground)",
    "Text(creationChatDisplayTextV20(text), style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp, color = t.foreground)",
    "assistant markdown cleanup",
)
s = replace_once(
    s,
    """    Surface(\n        modifier = Modifier.fillMaxWidth(),\n        color = t.background,\n        shadowElevation = 8.dp,\n    ) {""",
    """    Surface(\n        modifier = Modifier.fillMaxWidth(),\n        color = Color.Transparent,\n        shadowElevation = 0.dp,\n    ) {""",
    "transparent creation composer",
)
insert_at = "\n@Composable\nprivate fun CreationThinkingV4"
helper = r'''

internal fun creationChatDisplayTextV20(raw: String): String = raw
    .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
    .replace(Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
    .replace("```markdown", "")
    .replace("```md", "")
    .replace("```", "")
    .trim()
'''
if "creationChatDisplayTextV20" not in s[s.find("private fun CreationThinkingV4"):]:
    if insert_at not in s:
        raise AssertionError("creation display helper insertion point missing")
    s = s.replace(insert_at, helper + insert_at, 1)
path.write_text(s)

# ---------------------------------------------------------------------------
# Creation conversation: wider plain-chat budget is handled in gateway below;
# additionally auto-continue ONE time when a successful stream clearly ends
# mid-sentence. This catches relays that close cleanly without [DONE].
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
s = path.read_text()
old = r'''        val response = gateway.generateTextStreaming(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书创作搭档。第一职责是像正常可靠的 AI 助手一样理解用户当前这句话并自然回应，而不是把每轮聊天强行变成表格、JSON、方案卡或自动工作流。

                    对话原则：
                    1. 优先回答用户真正问的内容。简单问题简洁回答；复杂设定、长文件分析、剧情推演可以充分展开，不机械限字。
                    2. 承接完整多轮上下文。后出现的明确决定覆盖旧决定；“他/他们/这本/前面那几本”等按最近上下文理解，不把代词当新实体。
                    3. 用户上传的作品设定、世界观、大纲和人物文件属于项目资料。先读文件，再结合实际名称、规则、人物和分卷回答。原文事实不得擅改；新增想法标成建议或待确认。
                    4. 不要因为用户提到“小说、作品、资料、参考、融合”就自行联网。只有页面联网工具明确附带网页研究上下文时才作为辅助证据。
                    5. 普通聊天不自动生成/修改建书方案、蓝图、简介，不输出内部状态字段，也不要要求用户填表。用户满意时会主动整理方案/生成蓝图/正式建书。
                    6. 可以主动指出设定漏洞、人物动机、规则闭环、节奏和更好的方案，但必须区分“原文事实”和“建议”。
                    7. 当上下文出现【本轮主动检索的参考 DNA】时，必须先利用真正相关的命中条目再回答，不能把参考 DNA 当成可有可无的背景。用户问原作事实时可直接依据 STORY；讨论用户自己的新书时只能迁移 STYLE / KEEP / TRANSFORM 并遵守 AVOID，禁止照搬原作专名、具体能力规则、独特谜底和剧情骨架。
                    8. 多本参考同时选中时，要综合它们的共同机制与差异，不要默认只看第一本；用户使用“他们/这几本”时按已选参考和对话上下文解析。
                    9. 不要用“如果你愿意我可以……”空泛收尾。该分析就分析，该给方案就直接给方案。

                    $hiddenContext
                """.trimIndent(),
                user = latest,
                messages = conversationPromptMessages(messages),
                attachments = messagesPromptAttachments(messages.takeLast(1)),
                jsonMode = false,
            ),
            onDelta = onDelta,
        ).trim()
        return ConversationTurn(response.ifBlank { "我在。继续按你刚才的设定往下聊。" })'''
new = r'''        val chatPrompt = PromptBundle(
            system = """
                你是“琅嬛”的新书创作搭档。第一职责是像正常可靠的 AI 助手一样理解用户当前这句话并自然回应，而不是把每轮聊天强行变成表格、JSON、方案卡或自动工作流。

                对话原则：
                1. 优先回答用户真正问的内容。简单问题简洁回答；复杂设定、长文件分析、剧情推演可以充分展开，不机械限字。
                2. 承接完整多轮上下文。后出现的明确决定覆盖旧决定；“他/他们/这本/前面那几本”等按最近上下文理解，不把代词当新实体。
                3. 用户上传的作品设定、世界观、大纲和人物文件属于项目资料。先读文件，再结合实际名称、规则、人物和分卷回答。原文事实不得擅改；新增想法标成建议或待确认。
                4. 不要因为用户提到“小说、作品、资料、参考、融合”就自行联网。只有页面联网工具明确附带网页研究上下文时才作为辅助证据。
                5. 普通聊天不自动生成/修改建书方案、蓝图、简介，不输出内部状态字段，也不要要求用户填表。用户满意时会主动整理方案/生成蓝图/正式建书。
                6. 可以主动指出设定漏洞、人物动机、规则闭环、节奏和更好的方案，但必须区分“原文事实”和“建议”。
                7. 当上下文出现【本轮主动检索的参考 DNA】时，必须先利用真正相关的命中条目再回答，不能把参考 DNA 当成可有可无的背景。用户问原作事实时可直接依据 STORY；讨论用户自己的新书时只能迁移 STYLE / KEEP / TRANSFORM 并遵守 AVOID，禁止照搬原作专名、具体能力规则、独特谜底和剧情骨架。
                8. 多本参考同时选中时，要综合它们的共同机制与差异，不要默认只看第一本；用户使用“他们/这几本”时按已选参考和对话上下文解析。
                9. 不要用“如果你愿意我可以……”空泛收尾。该分析就分析，该给方案就直接给方案。

                $hiddenContext
            """.trimIndent(),
            user = latest,
            messages = conversationPromptMessages(messages),
            attachments = messagesPromptAttachments(messages.takeLast(1)),
            jsonMode = false,
        )
        var response = gateway.generateTextStreaming(chatPrompt, onDelta = onDelta).trim()
        if (shouldAutoContinueCreationReplyV20(response)) {
            val prefix = response
            val continuationInstruction = "从你上一条回复断掉的位置直接继续。不要从头重写，不要重复已经输出的句子，不要总结前文；保持原来的编号、语气和 Markdown 结构，把当前回答完整说完。"
            val continuationPrompt = chatPrompt.copy(
                user = continuationInstruction,
                messages = chatPrompt.messages +
                    PromptMessage("assistant", prefix) +
                    PromptMessage("user", continuationInstruction),
                attachments = emptyList(),
                jsonMode = false,
            )
            val suffix = gateway.generateTextStreaming(continuationPrompt) { delta ->
                onDelta(stitchCreationContinuationV20(prefix, delta))
            }.trim()
            response = stitchCreationContinuationV20(prefix, suffix)
        }
        return ConversationTurn(response.ifBlank { "我在。继续按你刚才的设定往下聊。" })'''
if old not in s:
    raise AssertionError("creation reply block not found")
s = s.replace(old, new, 1)
anchor = "\ninternal fun blueprintDirtyAfterConversation"
helpers = r'''

internal fun shouldAutoContinueCreationReplyV20(raw: String): Boolean {
    val text = raw.trimEnd()
    if (text.length < 180) return false
    if (text.endsWith("```")) return false
    val terminal = setOf('。', '！', '？', '.', '!', '?', '”', '’', '』', '」', '】', ')', '）', '…')
    if (text.lastOrNull() in terminal) return false
    if (text.endsWith("——") || text.endsWith("— 完 —")) return false
    return true
}

internal fun stitchCreationContinuationV20(prefix: String, suffix: String): String {
    val left = prefix.trimEnd()
    val right = suffix.trimStart()
    if (left.isBlank()) return right
    if (right.isBlank()) return left
    val maxOverlap = minOf(320, left.length, right.length)
    for (size in maxOverlap downTo 6) {
        if (left.endsWith(right.take(size))) return left + right.drop(size)
    }
    return when {
        right.firstOrNull()?.let { it in setOf('，', '。', '！', '？', '；', '：', ',', '.', '!', '?', ';', ':') } == true -> left + right
        else -> left + "\n" + right
    }
}
'''
if "shouldAutoContinueCreationReplyV20" not in s[s.find(anchor):]:
    if anchor not in s:
        raise AssertionError("creation helper insertion point missing")
    s = s.replace(anchor, helpers + anchor, 1)
path.write_text(s)

# ---------------------------------------------------------------------------
# Gateway: give plain conversation more headroom. Structured JSON keeps 4096;
# prose/rewrite retain their dedicated budgets.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt")
s = path.read_text()
s = replace_once(
    s,
    """        AiTaskType.EDITOR_REWRITE -> 7_168\n        else -> 4_096\n    }""",
    """        AiTaskType.EDITOR_REWRITE -> 7_168\n        else -> if (prompt.jsonMode) 4_096 else 6_144\n    }""",
    "plain chat token budget",
)
path.write_text(s)

# ---------------------------------------------------------------------------
# Spatial glass: keep the background visible instead of painting an opaque card.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/xiguli/langhuan/ui/design/LanghuanSpatialMotion.kt")
s = path.read_text()
s = replace_once(s, "color = t.card.copy(alpha = .90f)", "color = t.card.copy(alpha = .74f)", "glass alpha")
s = replace_once(s, "border = BorderStroke(1.dp, t.border.copy(alpha = .82f))", "border = BorderStroke(1.dp, t.border.copy(alpha = .58f))", "glass border")
s = replace_once(s, "shadowElevation = 2.dp", "shadowElevation = 0.dp", "glass shadow")
path.write_text(s)

# ---------------------------------------------------------------------------
# Reader: make paged reading calmer and deterministic, add the spatial preset,
# simplify interaction, and hide legacy controls that do not deliver value.
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = path.read_text()
for old_import, new_import in [
    ("import com.xiguli.langhuan.ui.design.LanghuanActionTileV4\n", "import com.xiguli.langhuan.ui.design.LanghuanActionTileV4\nimport com.xiguli.langhuan.ui.design.LanghuanAmbientBackdrop\nimport com.xiguli.langhuan.ui.design.LanghuanConstellationField\n"),
]:
    if "LanghuanAmbientBackdrop" not in s:
        s = replace_once(s, old_import, new_import, "reader spatial imports")

preset_anchor = 'private val HERO_READER_PRESETS_V13 = listOf(\n'
if '"langhuan", "琅嬛星图"' not in s:
    s = replace_once(
        s,
        preset_anchor,
        preset_anchor + '    HeroReaderPresetV13("langhuan", "琅嬛星图", "轻雾星图 · 克制动态 · 长读低干扰", "langhuan", 18f, 1.76f, 0f, 22f, true, "sans"),\n',
        "reader spatial preset",
    )

# Migrate old fake cover mode to normal PAGE without destroying saved progress.
s = replace_once(
    s,
    '    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE\n',
    '    val requestedPageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE\n    val pageMode = if (requestedPageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE else requestedPageMode\n',
    "reader page mode migration",
)

old_palette = r'''    val rawPalette = heroReaderPaletteV13(themeKey)
    val palette = remember(rawPalette, backgroundFollow, chapter.chapterNumber) {
        if (!backgroundFollow || themeKey == "night") rawPalette
        else rawPalette.copy(
            page = lerp(
                rawPalette.page,
                if (chapter.chapterNumber % 2 == 0) Color.White else Color(0xFFB8C8AE),
                .035f,
            ),
        )
    }
    val tokens = remember(palette) { langhuanTokensV4(palette.page, palette.text, palette.accent) }'''
new_palette = r'''    val rawPalette = heroReaderPaletteV13(themeKey)
    // A reading background must remain stable between chapters. The old chapter-parity tint
    // changed the page colour while reading and made the book feel visually inconsistent.
    val palette = rawPalette
    val spatialBackground = themeKey == "langhuan"
    val tokens = remember(palette) { langhuanTokensV4(palette.page, palette.text, palette.accent) }'''
s = replace_once(s, old_palette, new_palette, "stable reader palette")

# In paged mode paragraph gaps are removed so every page follows the same baseline grid.
s = replace_once(
    s,
    """    val pagination = rememberReaderPaginationV18(\n        text = readingText,""",
    """    val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f\n    val pagination = rememberReaderPaginationV18(\n        text = readingText,""",
    "paged baseline spacing",
)
s = replace_once(s, "        paragraphSpacing = paragraphSpacing,\n        firstLineIndent = firstLineIndent,\n        family = family,\n    )\n\n    val pages", "        paragraphSpacing = pagedParagraphSpacing,\n        firstLineIndent = firstLineIndent,\n        family = family,\n    )\n\n    val pages", "pagination spacing argument")
s = replace_once(s, "snapPositionalThreshold = 0.15f", "snapPositionalThreshold = 0.32f", "reader fling threshold")
s = replace_once(s, "val edgeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }", "val edgeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }", "chapter edge threshold")

# System bars: one understandable immersive switch. Legacy separate status/nav prefs no longer alter UI.
s, count = re.subn(
    r'''    DisposableEffect\(activity, immersive, statusBar, navigationBar\) \{.*?\n    \}\n\n    val edgeThresholdPx''',
    '''    DisposableEffect(activity, immersive) {\n        val window = activity?.window\n        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }\n        if (controller != null) {\n            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE\n            if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars())\n            else controller.show(WindowInsetsCompat.Type.systemBars())\n        }\n        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }\n    }\n\n    val edgeThresholdPx''',
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("reader system bars block not replaced")

# Put spatial background behind text and remove the confusing full-screen-next branch.
s = replace_once(
    s,
    """    ) {\n        Box(\n            Modifier""",
    """    ) {\n        if (spatialBackground) {\n            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = false)\n            LanghuanConstellationField(Modifier.fillMaxSize(), active = false)\n        }\n        Box(\n            Modifier""",
    "reader spatial background layer",
)
s = replace_once(
    s,
    ".pointerInput(chapter.id, pageModeKey, fullNext, panelVisible)",
    ".pointerInput(chapter.id, pageModeKey, panelVisible)",
    "reader tap gesture key",
)
old_tap = r'''                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            else if (fullNext) {
                                if (point.x < size.width * .18f) previousPage() else nextPage()
                            } else when {
                                point.x < size.width * .28f -> previousPage()
                                point.x > size.width * .72f -> nextPage()
                                else -> panelVisible = true
                            }'''
new_tap = r'''                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            else when {
                                point.x < size.width * .28f -> previousPage()
                                point.x > size.width * .72f -> nextPage()
                                else -> panelVisible = true
                            }'''
s = replace_once(s, old_tap, new_tap, "reader tap zones")
s = replace_once(s, "                            paragraphSpacing = paragraphSpacing,\n                            sidePadding = sidePadding,", "                            paragraphSpacing = pagedParagraphSpacing,\n                            sidePadding = sidePadding,", "reader canvas paged spacing")
s = replace_once(s, "                            showTimeBattery = showTimeBattery,\n                        )", "                            showTimeBattery = showTimeBattery,\n                            spatialBackground = spatialBackground,\n                        )", "reader canvas spatial arg")
s = s.replace("            if (backgroundMask) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .045f)))\n", "", 1)

# Remove the invisible top-right pull gesture that could eat reader touches.
s, count = re.subn(
    r'''\n        if \(pullBookmark\) \{\n            Box\(\n                Modifier\n                    \.align\(Alignment\.TopEnd\).*?\n            \)\n        \}\n''',
    "\n",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("pull bookmark overlay not removed")

# Canvas can be transparent for the spatial theme.
s = replace_once(s, "    showTimeBattery: Boolean,\n) {", "    showTimeBattery: Boolean,\n    spatialBackground: Boolean,\n) {", "reader canvas signature")
s = replace_once(s, ".background(palette.page)\n            .padding(start = sidePadding.dp", ".background(if (spatialBackground) Color.Transparent else palette.page)\n            .padding(start = sidePadding.dp", "reader canvas background")

# Settings surface: keep only controls that have clear value. Legacy callbacks stay for prefs compatibility.
s = replace_once(s, 'labels = listOf("详情", "目录", "更多")', 'labels = listOf("详情", "目录", "设置")', "reader settings tab label")
actions_pattern = re.compile(r'''                val actions = listOf\(.*?\n                \)\n                LazyVerticalGrid''', re.S)
match = actions_pattern.search(s)
if not match:
    raise AssertionError("reader actions block not found")
new_actions = '''                val actions = listOf(\n                    HeroReaderActionV13("排版预设", Icons.Rounded.Tune, onClick = onPreset),\n                    HeroReaderActionV13("主题", Icons.Rounded.Palette, onClick = onTheme),\n                    HeroReaderActionV13("字体", Icons.Rounded.TextFields, onClick = onFont),\n                    HeroReaderActionV13("字号", Icons.Rounded.FormatSize, onClick = { onType("字号") }),\n                    HeroReaderActionV13("行距页边距", Icons.Rounded.FormatAlignJustify, onClick = { onType("行段") }),\n                    HeroReaderActionV13("滚动阅读", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),\n                    HeroReaderActionV13("全文搜索", Icons.Rounded.Search, onClick = onSearch),\n                    HeroReaderActionV13("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),\n                    HeroReaderActionV13("屏幕常亮", Icons.Rounded.LightMode, keepScreen, onKeepScreen),\n                    HeroReaderActionV13("时间电量", Icons.Rounded.BatteryFull, showTimeBattery, onTimeBattery),\n                    HeroReaderActionV13("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),\n                    HeroReaderActionV13("锁定竖屏", Icons.Rounded.Landscape, lockPortrait, onLockPortrait),\n                )\n                LazyVerticalGrid'''
s = s[:match.start()] + new_actions + s[match.end():]

# Theme sheet + palette for the exact creation-space visual language.
s = replace_once(
    s,
    'listOf("paper" to "纸白", "tea" to "茶纸", "green" to "青叶", "night" to "夜间")',
    'listOf("langhuan" to "琅嬛星图", "paper" to "纸白", "tea" to "茶纸", "green" to "青叶", "night" to "夜间")',
    "reader theme list",
)
s = replace_once(
    s,
    'private fun heroReaderPaletteV13(key: String): HeroReaderPaletteV13 = when (key) {\n    "paper" ->',
    'private fun heroReaderPaletteV13(key: String): HeroReaderPaletteV13 = when (key) {\n    "langhuan" -> HeroReaderPaletteV13(Color(0xFFF5F6FE), Color(0xFF22232A), Color(0xFF747784), Color(0xFF5D78B8))\n    "paper" ->',
    "reader spatial palette",
)
path.write_text(s)

# ---------------------------------------------------------------------------
# Version + regression contracts.
# ---------------------------------------------------------------------------
path = Path("app/build.gradle.kts")
s = path.read_text()
s = replace_once(s, "versionCode = 98", "versionCode = 99", "alpha20 version code")
s = replace_once(s, 'versionName = "0.28.0-alpha19-creation-reliability"', 'versionName = "0.28.0-alpha20-reader-chat-polish"', "alpha20 version name")
path.write_text(s)

Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderChatAlpha20ContractTest.kt").write_text(r'''package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChatAlpha20ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun creationReplyDetectsAndStitchesCleanCutoff() {
        assertTrue(shouldAutoContinueCreationReplyV20("这是一段已经足够长的创作分析。".repeat(20) + "附件设计了"))
        assertFalse(shouldAutoContinueCreationReplyV20("这是一段已经正常完成的回答。".repeat(20)))
        assertEquals("前文重复片段继续", stitchCreationContinuationV20("前文重复片段", "重复片段继续"))
    }

    @Test
    fun creationComposerKeepsSpatialBackgroundVisible() {
        val creation = source("app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
        assertTrue(creation.contains("color = Color.Transparent"))
        assertTrue(creation.contains("creationChatDisplayTextV20(text)"))
        val glass = source("app/src/main/java/com/xiguli/langhuan/ui/design/LanghuanSpatialMotion.kt")
        assertTrue(glass.contains("t.card.copy(alpha = .74f)"))
    }

    @Test
    fun readerUsesStablePagedBaselineAndSpatialPreset() {
        val reader = source("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("琅嬛星图"))
        assertTrue(reader.contains("LanghuanConstellationField"))
        assertTrue(reader.contains("val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f"))
        assertTrue(reader.contains("snapPositionalThreshold = 0.32f"))
        assertTrue(reader.contains("labels = listOf(\"详情\", \"目录\", \"设置\")"))
        val actionBlock = reader.substringAfter("val actions = listOf(").substringBefore("LazyVerticalGrid")
        assertFalse(actionBlock.contains("仿真翻页"))
        assertFalse(actionBlock.contains("全屏下一页"))
        assertFalse(actionBlock.contains("背景图遮罩"))
        assertFalse(actionBlock.contains("背景跟随"))
        assertFalse(actionBlock.contains("下拉书签"))
        assertTrue(actionBlock.contains("滚动阅读"))
    }

    @Test
    fun plainChatGetsMoreOutputHeadroom() {
        val gateway = source("app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt")
        assertTrue(gateway.contains("else -> if (prompt.jsonMode) 4_096 else 6_144"))
    }
}
''')
