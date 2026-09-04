package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN.md §15 的机械检查。
 *
 * 那份 QA 清单里有几条本来就能用脚本判——「圆角是否全部来自定义尺度」
 * 「UI Chrome 中 Emoji 是否为 0」「强调色是否只有一套」——但一直没人跑，
 * 所以硬编码越堆越多。这个测试把它们变成会红的断言。
 *
 * 用的是**棘轮**写法：记下当前的欠账数，只许降不许升。每收口一批就把基线改小，
 * 改不小说明这次没真收口。降到 0 之后把断言换成 assertEquals 并删掉基线常量。
 */
class DesignTokenGuardTest {

    private val sourceRoot = File(
        System.getProperty("user.dir") ?: ".",
        "src/main/java/com/xiguli/langhuan",
    )

    /** 主题包自己当然要写字面量，它就是尺度的定义处。 */
    private fun sourcesOutsideTheme(): List<File> =
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile.name == "theme" }
            .toList()

    private fun countMatches(regex: Regex): Int =
        sourcesOutsideTheme().sumOf { file -> regex.findAll(file.readText()).count() }

    // ---- 基线：只许降，不许升 ---------------------------------------------
    private companion object {
        /**
         * DESIGN.md §5 字体层级。改用 MaterialTheme.typography.*。
         * 剩下 4 处是刻意保留的字面量，源码里都有注释说明：滑杆两端的「Aa」大小
         * 示例（2 处）、参与分页测量算式的页眉页脚（2 处）。
         */
        const val HARDCODED_FONT_SIZE_BASELINE = 4

        /** DESIGN.md §6「不在页面散落固定 RGB」。改用 colorScheme / LanghuanUiTokens。 */
        const val HARDCODED_COLOR_BASELINE = 55

        /** DESIGN.md §2「优先 Material 3，不混多套设计语言」。 */
        const val MIUIX_FILE_BASELINE = 5

        /** DESIGN.md §3「UI Chrome 中 Emoji：0」。当前是 2 个文件拿符号当状态图标。 */
        const val EMOJI_FILE_BASELINE = 2
    }

    /**
     * 这条已经收干净了，所以直接断言 0，不再留基线。
     * 判据是「RoundedCornerShape 后面直接跟数字」——用 LanghuanRadius 组合出来的
     * 条件圆角不算越界，写死的 dp 值才算。
     */
    @Test
    fun noHardcodedCornerRadiusOutsideTheme() {
        val offenders = sourcesOutsideTheme()
            .filter { Regex("""RoundedCornerShape\(\s*\d""").containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
        assertEquals(
            "圆角必须来自 LanghuanShape.* / LanghuanRadius.*（DESIGN.md §4）。越界文件：$offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun hardcodedFontSizeDoesNotGrow() {
        val found = countMatches(Regex("""fontSize\s*=\s*\d+(\.\d+)?\.sp"""))
        assertTrue(
            "硬编码字号从 $HARDCODED_FONT_SIZE_BASELINE 涨到了 $found。" +
                "新代码请走 MaterialTheme.typography.*（DESIGN.md §5）。",
            found <= HARDCODED_FONT_SIZE_BASELINE,
        )
    }

    @Test
    fun hardcodedColorDoesNotGrow() {
        val found = countMatches(Regex("""Color\(0x"""))
        assertTrue(
            "散落的固定颜色从 $HARDCODED_COLOR_BASELINE 涨到了 $found。" +
                "新代码请走 MaterialTheme.colorScheme 或 LocalLanghuanUiTokens（DESIGN.md §6）。",
            found <= HARDCODED_COLOR_BASELINE,
        )
    }

    @Test
    fun miuixUsageDoesNotGrow() {
        val found = sourcesOutsideTheme().count { it.readText().contains("miuix", ignoreCase = true) }
        assertTrue(
            "引用 MIUIx 的文件从 $MIUIX_FILE_BASELINE 涨到了 $found。" +
                "琅嬛正在收敛到 Material 3 单一设计语言（DESIGN.md §2）。",
            found <= MIUIX_FILE_BASELINE,
        )
    }

    @Test
    fun uiChromeEmojiDoesNotGrow() {
        // 只查会被当成图标用的符号区段，中文标点和汉字不受影响。
        val emoji = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]")
        val offenders = sourcesOutsideTheme()
            .filter { emoji.containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
        assertTrue(
            "用符号当图标的文件从 $EMOJI_FILE_BASELINE 涨到了 ${offenders.size}：$offenders。" +
                "DESIGN.md §3 要求 UI Chrome 中 Emoji 为 0，请换成 Material Symbols。",
            offenders.size <= EMOJI_FILE_BASELINE,
        )
    }

    @Test
    fun onlyOneThemeEntryPointExists() {
        val themeDir = File(sourceRoot, "ui/theme")
        val entryPoints = themeDir.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .sumOf { file ->
                Regex("""^@Composable\s*\nfun \w*Theme\(""", RegexOption.MULTILINE)
                    .findAll(file.readText())
                    .count()
            }
        assertEquals(
            "主题入口必须只有 LanghuanTheme 一个。之前 LanghuanTheme 与 LanghuanStableTheme " +
                "并存，而 token 定义在没人调用的那个文件里。",
            1,
            entryPoints,
        )
    }

    @Test
    fun singleAccentColorAcrossRoles() {
        // DESIGN.md §6：一个页面只允许一个主要强调色。shadcn 原来把品牌色放在
        // tertiary 当「品牌时刻」用，等于第二套强调色，这里要求 primary 与
        // tertiary 同色。
        val color = File(sourceRoot, "ui/theme/Color.kt").readText()
        val lightPrimary = Regex("""primary = (\w+),\s*\n\s*onPrimary""").find(color)
        val tertiary = Regex("""tertiary = (\w+),""").findAll(color).map { it.groupValues[1] }.toList()
        assertTrue("Color.kt 里找不到 primary 定义", lightPrimary != null)
        assertTrue(
            "tertiary 必须与 primary 同色，否则页面上会出现第二种强调色（DESIGN.md §6）。" +
                "当前 tertiary = $tertiary",
            tertiary.isNotEmpty() && tertiary.all { it == lightPrimary!!.groupValues[1] || it.endsWith("OnDark") },
        )
    }

    @Test
    fun readingBodyMatchesDesignSpec() {
        // DESIGN.md §5：正文默认 18–20sp，行高 1.70–1.95 ×。
        val type = File(sourceRoot, "ui/theme/Type.kt").readText()
        val match = Regex("""bodyLarge = cjk\(Serif, (\d+), (\d+)\)""").find(type)
        assertTrue("Type.kt 里找不到 bodyLarge 定义，正文字号无法校验", match != null)
        val size = match!!.groupValues[1].toInt()
        val lineHeight = match.groupValues[2].toInt()
        assertTrue("正文字号 ${size}sp 不在 18–20sp 区间", size in 18..20)
        val ratio = lineHeight / size.toFloat()
        assertTrue("正文行高 ${"%.2f".format(ratio)}× 不在 1.70–1.95 区间", ratio in 1.70f..1.95f)
    }
}
