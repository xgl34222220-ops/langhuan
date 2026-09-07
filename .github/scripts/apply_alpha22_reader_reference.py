from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: source not found")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Reader UI: denser mature-reader baseline + exact render/measure parity.
# References used for the design pass: KOReader, Legado and MoRealm. Keep the
# implementation native Compose, but copy the principles: modest margins,
# restrained line spacing, full justification, large tap zones and a content-
# first page with tiny chrome.
# -----------------------------------------------------------------------------
reader_path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = reader_path.read_text()

# Add TextAlign import.
s = replace_once(
    s,
    "import androidx.compose.ui.text.style.TextIndent\nimport androidx.compose.ui.text.style.TextOverflow\n",
    "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextIndent\nimport androidx.compose.ui.text.style.TextOverflow\n",
    "TextAlign import",
)

# Presets: replace the overly airy app-UI-like typography with reader-like density.
preset_block = '''private val HERO_READER_PRESETS_V13 = listOf(
    HeroReaderPresetV13("langhuan", "琅嬛星图", "轻雾星图 · 克制动态 · 长读低干扰", "langhuan", 18f, 1.76f, 0f, 22f, true, "sans"),
    HeroReaderPresetV13("qingmo", "清墨", "均衡留白 · 温润纸色", "tea", 18f, 1.75f, 3f, 20f, true, "sans"),
    HeroReaderPresetV13("tomato", "番茄小说风格", "稍大字号 · 紧凑行距 · 暖色背景", "tea", 19f, 1.68f, 2f, 19f, true, "sans"),
    HeroReaderPresetV13("weread", "微信读书风格", "宽页边距 · 舒展行距 · 轻纸白", "paper", 17.5f, 1.80f, 4f, 24f, true, "sans"),
    HeroReaderPresetV13("qidian", "起点阅读风格", "正文密度适中 · 页边距偏窄", "paper", 18f, 1.72f, 3f, 18f, true, "sans"),
    HeroReaderPresetV13("ireader", "掌阅风格", "宋体阅读 · 行距更舒展", "tea", 18f, 1.82f, 4f, 22f, true, "serif"),
    HeroReaderPresetV13("compact", "紧凑阅读", "一屏更多文字", "paper", 17f, 1.55f, 1f, 18f, true, "sans"),
    HeroReaderPresetV13("comfort", "舒适阅读", "大字号 · 大行距 · 宽留白", "tea", 19f, 1.88f, 5f, 25f, true, "serif"),
)'''
new_presets = '''private val HERO_READER_PRESETS_V13 = listOf(
    HeroReaderPresetV13("langhuan", "琅嬛星图", "轻雾星图 · 正文优先 · 低干扰", "langhuan", 18f, 1.56f, 0f, 18f, true, "sans"),
    HeroReaderPresetV13("qingmo", "清墨", "成熟网文密度 · 克制留白", "tea", 18f, 1.56f, 0f, 18f, true, "sans"),
    HeroReaderPresetV13("tomato", "大字紧凑", "稍大字号 · 紧凑行距 · 暖色背景", "tea", 19f, 1.50f, 0f, 16f, true, "sans"),
    HeroReaderPresetV13("weread", "纸白舒展", "适中字号 · 轻纸白 · 稍宽页边距", "paper", 17.5f, 1.62f, 1f, 21f, true, "sans"),
    HeroReaderPresetV13("qidian", "经典网文", "正文密度均衡 · 窄页边距", "paper", 18f, 1.54f, 0f, 17f, true, "sans"),
    HeroReaderPresetV13("ireader", "宋体纸书", "宋体阅读 · 适度舒展", "tea", 18f, 1.62f, 1f, 20f, true, "serif"),
    HeroReaderPresetV13("compact", "紧凑阅读", "一屏更多正文", "paper", 17f, 1.42f, 0f, 15f, true, "sans"),
    HeroReaderPresetV13("comfort", "舒适大字", "大字号 · 仍保持正文密度", "tea", 19f, 1.66f, 1f, 21f, true, "serif"),
)'''
s = replace_once(s, preset_block, new_presets, "preset block")

# Replace raw initial settings with a one-shot exact-default migration. This updates
# users who never customized the old airy defaults without overwriting real choices.
old_state = '''    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font", 18f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line", 1.75f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph", 3f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("sidePadding", 20f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "sans") ?: "sans") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "tea") ?: "tea") }
    var presetKey by remember(book.id) { mutableStateOf(prefs.getString("preset", "qingmo") ?: "qingmo") }'''
new_state = '''    val legacyPreset = remember(book.id) { prefs.getString("preset", "qingmo") ?: "qingmo" }
    val legacyFont = remember(book.id) { prefs.getFloat("font", 18f) }
    val legacyLine = remember(book.id) { prefs.getFloat("line", 1.75f) }
    val legacyParagraph = remember(book.id) { prefs.getFloat("paragraph", 3f) }
    val legacySide = remember(book.id) { prefs.getFloat("sidePadding", 20f) }
    val densityMigrationNeeded = remember(book.id) {
        if (prefs.getBoolean("reader_density_v22", false)) false
        else when (legacyPreset) {
            "qingmo" -> legacyFont == 18f && legacyLine == 1.75f && legacyParagraph == 3f && legacySide == 20f
            "langhuan" -> legacyFont == 18f && legacyLine == 1.76f && legacyParagraph == 0f && legacySide == 22f
            else -> false
        }
    }
    val initialLine = if (densityMigrationNeeded) 1.56f else legacyLine
    val initialParagraph = if (densityMigrationNeeded) 0f else legacyParagraph
    val initialSide = if (densityMigrationNeeded) 18f else legacySide

    var fontSize by remember(book.id) { mutableFloatStateOf(legacyFont) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(initialLine) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(initialParagraph) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(initialSide) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "sans") ?: "sans") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "tea") ?: "tea") }
    var presetKey by remember(book.id) { mutableStateOf(legacyPreset) }

    LaunchedEffect(book.id) {
        if (!prefs.getBoolean("reader_density_v22", false)) {
            val edit = prefs.edit().putBoolean("reader_density_v22", true)
            if (densityMigrationNeeded) {
                edit.putFloat("line", 1.56f)
                    .putFloat("paragraph", 0f)
                    .putFloat("sidePadding", 18f)
            }
            edit.apply()
        }
    }'''
s = replace_once(s, old_state, new_state, "reader default migration")

# Wider side tap zones, matching mature reader conventions.
s = s.replace("point.x < size.width * .28f -> previousPage()", "point.x < size.width * .33f -> previousPage()")
s = s.replace("point.x > size.width * .72f -> nextPage()", "point.x > size.width * .67f -> nextPage()")

# Chrome geometry: content first. Keep this in exact lock-step with the paginator below.
s = replace_once(
    s,
    ".padding(start = sidePadding.dp, end = sidePadding.dp, top = 16.dp, bottom = 12.dp),",
    ".padding(start = sidePadding.dp, end = sidePadding.dp, top = 8.dp, bottom = 6.dp),",
    "paged chrome padding",
)
s = replace_once(s, "fontSize = 11.sp,\n            lineHeight = 15.sp,", "fontSize = 10.sp,\n            lineHeight = 13.sp,", "header typography")
s = replace_once(s, "Spacer(Modifier.height(14.dp))\n        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds())", "Spacer(Modifier.height(7.dp))\n        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds())", "header gap")
s = replace_once(s, "Spacer(Modifier.height(8.dp))\n        Row(Modifier.fillMaxWidth()", "Spacer(Modifier.height(4.dp))\n        Row(Modifier.fillMaxWidth()", "footer gap")
s = s.replace("fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f)", "fontSize = 8.sp, lineHeight = 10.sp, color = palette.secondary.copy(alpha = .44f)")

# Match KOReader-like full justification. For Chinese prose it mostly affects punctuation/
# mixed Latin lines, while preserving the 2-em first-line indent.
needle = '''                    fontWeight = FontWeight.Normal,
                    color = color,
                    textIndent = TextIndent(firstLine = if (shouldIndent) (fontSize * 2f).sp else 0.sp),'''
replacement = '''                    fontWeight = FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Justify,
                    textIndent = TextIndent(firstLine = if (shouldIndent) (fontSize * 2f).sp else 0.sp),'''
s = replace_once(s, needle, replacement, "paged justification")
needle2 = '''                fontFamily = family,
                color = color,
                textIndent = TextIndent(firstLine = if (indent) (fontSize * 2f).sp else 0.sp),'''
replacement2 = '''                fontFamily = family,
                color = color,
                textAlign = TextAlign.Justify,
                textIndent = TextIndent(firstLine = if (indent) (fontSize * 2f).sp else 0.sp),'''
s = replace_once(s, needle2, replacement2, "scroll justification")

reader_path.write_text(s)

# -----------------------------------------------------------------------------
# Paginator: use all available body height. Alpha21 quantized DOWN to an integer
# line count, which could throw away nearly a full line on every page. Mature
# readers don't reserve invisible blank space just to force an artificial grid.
# -----------------------------------------------------------------------------
pag_path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
p = pag_path.read_text()
p = replace_once(
    p,
    "import androidx.compose.ui.text.style.TextIndent\n",
    "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextIndent\n",
    "paginator TextAlign import",
)
p = p.replace("val pageTop = with(density) { 16.dp.roundToPx() }", "val pageTop = with(density) { 8.dp.roundToPx() }")
p = p.replace("val pageBottom = with(density) { 12.dp.roundToPx() }", "val pageBottom = with(density) { 6.dp.roundToPx() }")
p = p.replace("val headerGap = with(density) { 14.dp.roundToPx() }", "val headerGap = with(density) { 7.dp.roundToPx() }")
p = p.replace("val footerGap = with(density) { 8.dp.roundToPx() }", "val footerGap = with(density) { 4.dp.roundToPx() }")
p = p.replace("val rasterGuard = with(density) { 3.dp.roundToPx() }", "val rasterGuard = with(density) { 1.dp.roundToPx() }")
p = p.replace("fontSize = 11.sp,\n        lineHeight = 15.sp,", "fontSize = 10.sp,\n        lineHeight = 13.sp,")
p = p.replace("fontSize = 9.sp,\n        lineHeight = 12.sp,", "fontSize = 8.sp,\n        lineHeight = 10.sp,")
p = replace_once(
    p,
    '''        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )''',
    '''        fontFamily = family,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Justify,
    )''',
    "body measurement justification",
)
old_quant = '''    val rawBodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val lineBoxHeight = measurer.measure(
        text = "阅",
        style = bodyStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height.coerceAtLeast(1)
    // Quantize the viewport to complete line boxes. This removes the page-to-page
    // one-line drift caused by fractional dp/sp rounding on high-density screens.
    val bodyHeight = ((rawBodyHeight / lineBoxHeight).coerceAtLeast(1) * lineBoxHeight)'''
new_quant = '''    // Use the full measured viewport. The previous integer-line quantization rounded
    // DOWN and could discard almost one complete line per page, which appeared as
    // a large empty band. Text is still split only at complete measured line ends.
    val bodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })'''
p = replace_once(p, old_quant, new_quant, "remove artificial line-grid blank")
pag_path.write_text(p)

# -----------------------------------------------------------------------------
# Tests: preserve alpha21 pager safety but update the contract to the corrected
# density model. Add an explicit regression contract for render/measure geometry.
# -----------------------------------------------------------------------------
test_path = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderPaginationAlpha21ContractTest.kt")
t = test_path.read_text()
t = t.replace('point.x < size.width * .28f -> previousPage()', 'point.x < size.width * .33f -> previousPage()')
t = t.replace('point.x > size.width * .72f -> nextPage()', 'point.x > size.width * .67f -> nextPage()')
old_test = '''    @Test
    fun pageBodyUsesAnIntegerLineGrid() {
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(paginator.contains("val lineBoxHeight = measurer.measure("))
        assertTrue(paginator.contains("rawBodyHeight / lineBoxHeight"))
        assertTrue(paginator.contains("* lineBoxHeight"))
    }'''
new_test = '''    @Test
    fun pageBodyUsesFullMeasuredViewportWithoutArtificialBlankBand() {
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(paginator.contains("val bodyHeight = ("))
        assertTrue(paginator.contains("stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard"))
        assertTrue(!paginator.contains("rawBodyHeight / lineBoxHeight"))
    }'''
t = replace_once(t, old_test, new_test, "alpha21 density contract")
test_path.write_text(t)

Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderReferenceDensityAlpha22Test.kt").write_text('''package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReferenceDensityAlpha22Test {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun renderAndPaginationUseSameCompactChromeGeometry() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        val pager = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(reader.contains("top = 8.dp, bottom = 6.dp"))
        assertTrue(reader.contains("Spacer(Modifier.height(7.dp))"))
        assertTrue(reader.contains("Spacer(Modifier.height(4.dp))"))
        assertTrue(pager.contains("pageTop = with(density) { 8.dp.roundToPx() }"))
        assertTrue(pager.contains("pageBottom = with(density) { 6.dp.roundToPx() }"))
        assertTrue(pager.contains("headerGap = with(density) { 7.dp.roundToPx() }"))
        assertTrue(pager.contains("footerGap = with(density) { 4.dp.roundToPx() }"))
    }

    @Test
    fun matureReaderDefaultsUseContentFirstDensity() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("1.56f"))
        assertTrue(reader.contains("initialSide = if (densityMigrationNeeded) 18f"))
        assertTrue(reader.contains("reader_density_v22"))
        assertTrue(reader.contains("textAlign = TextAlign.Justify"))
        assertTrue(reader.contains("point.x < size.width * .33f -> previousPage()"))
        assertTrue(reader.contains("point.x > size.width * .67f -> nextPage()"))
    }

    @Test
    fun paginatorDoesNotThrowAwayAWholeLineForAlignment() {
        val pager = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertFalse(pager.contains("rawBodyHeight / lineBoxHeight"))
        assertFalse(pager.contains("* lineBoxHeight"))
        assertTrue(pager.contains("lastCompleteLineEndV18"))
    }
}
''')

# Version bump.
build = Path("app/build.gradle.kts")
b = build.read_text()
b = replace_once(b, "versionCode = 100", "versionCode = 101", "versionCode")
b = replace_once(b, 'versionName = "0.28.0-alpha21-reader-pagination-fix"', 'versionName = "0.28.0-alpha22-reader-reference-density"', "versionName")
build.write_text(b)
