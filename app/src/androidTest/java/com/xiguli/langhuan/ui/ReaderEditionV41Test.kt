package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderEditionV41Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val body = (1..160).joinToString("\n") { i ->
        if (i % 4 == 0) "他抬头，看见远方的一点灯火。"
        else "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。中文与 hello world 相遇，故事仍在继续。"
    }

    private fun saveFrame(name: String) {
        // Wait for lazy content effects to start, then advance past their staggered delays.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // A Compose dialog owns another Window: captureToImage was copying the Activity
        // surface even after locating the dialog semantics root. Use the compositor for it.
        val bitmap = if (name == "v44-ai-source") {
            rule.onNodeWithText("网站链接").assertIsDisplayed()
            val packageName = automation.rootInActiveWindow?.packageName?.toString()
            if (packageName != rule.activity.packageName) deviceWindowEvidenceV46("v46-ai-foreground-failure")
            assertEquals("AI dialog must be the app's active window", rule.activity.packageName, packageName)
            automation.takeScreenshot() ?: error("Unable to capture the active AI window")
        } else rule.onAllNodes(isRoot(), useUnmergedTree = true).onLast().captureToImage().asAndroidBitmap()
        var shelfInk: Int? = null
        if (name == "v44-shelf") {
            var ink = 0
            for (y in bitmap.height / 6 until bitmap.height / 2 step 3) {
                for (x in bitmap.width / 12 until bitmap.width * 11 / 12 step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 190 && android.graphics.Color.green(pixel) < 190 && android.graphics.Color.blue(pixel) < 190) ink++
                }
            }
            shelfInk = ink
        }
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        val target = File(dir, "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        automation.executeShellCommand("cp ${target.absolutePath} /sdcard/Download/reader-qa/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        shelfInk?.let { assertTrue("Shelf fixture exists semantically but covers were not painted", it > 150) }
    }

    @Test fun measuredFullPagesAlignAcrossWidthsAndFontScales() {
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f, 2f)) for (font in listOf(16f, 20f, 28f)) {
            val fontPx = font * 2f * scale
            val spec = ReaderTypeSpecV30((width - 40) * 2, 1207, fontPx, fontPx * 1.65f,
                17f, fontPx * 1.36f, fontPx * 1.8f, 24f, true, "sans", 400, 0f)
            val chapter = readerPaginateChapterV30(0, "fixture", "夜航记", body, spec)
            assertTrue(chapter.pages.size > 2)
            val full = chapter.pages.dropLast(1).filter { it.lines.size > 1 && it.lines.none { line -> line.title } }
            assertTrue(full.isNotEmpty())
            assertChapterGeometry(chapter, body, spec)
            // Ordinary dense pages reach the bottom rail. Very large type may leave a
            // small remainder, but must not distort the requested spacing to hide it.
            full.filter { it.lines.size >= 14 }.forEach { page ->
                assertEquals(spec.bodyHeightPx.toFloat(), page.lines.last().bottom, 0.6f)
            }
        }
    }

    private fun assertChapterGeometry(chapter: ReaderChapterPagesV30, source: String, spec: ReaderTypeSpecV30) {
        chapter.pages.forEach { page ->
            assertEquals(0f, page.lines.first().baseline + page.lines.first().ascent, .6f)
            assertEquals(page.usedHeight, page.lines.last().bottom, .6f)
            assertTrue("page ${page.index} overflows", page.usedHeight <= spec.bodyHeightPx + .6f)
            page.lines.zipWithNext().forEach { (previous, line) ->
                assertTrue("fallback font overlaps its neighbour", previous.bottom <= line.top + .6f)
                if (!previous.title && !line.title) {
                    val paragraph = source.substring(previous.offset, line.offset).contains('\n')
                    val requested = maxOf(spec.lineHeightPx, previous.descent - line.ascent) +
                        if (paragraph) spec.paragraphGapPx else 0f
                    val actual = line.baseline - previous.baseline
                    assertTrue("page ${page.index}: advance=$actual, requested=$requested",
                        abs(actual - requested) <= spec.lineHeightPx * .06f + .6f)
                }
            }
        }
        chapter.pages.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
        assertEquals(source.length, chapter.pages.last().endOffset)
        // Check actual drawn text, not only mutually consistent offset bookkeeping.
        fun visible(text: String) = text.filterNot { it.isWhitespace() || it == '\u3000' }
        assertEquals(visible(source), visible(chapter.pages.flatMap { it.lines }.filterNot { it.title }.joinToString("") { it.text }))
    }

    @Test fun spacingControlsKeepFontRailsAndChapterEndNatural() {
        val sample = "夜色落在水面上。".repeat(12) + "\n他停下脚步。\n" + "岸边仍有微光。".repeat(8)
        for (font in listOf("sans", "serif", "mono")) {
            var topBaseline: Float? = null
            for (factor in listOf(1.3f, 1.65f, 2f)) for (paragraph in listOf(0f, 16f, 40f)) {
                val spec = ReaderTypeSpecV30(640, 6000, 40f, 40f * factor, paragraph,
                    54f, 78f, 48f, true, font, 400, 0f)
                val chapter = readerPaginateChapterV30(0, "spacing", "", sample, spec)
                val page = chapter.pages.single()
                val baseline = page.lines.first().baseline
                topBaseline?.let { assertEquals("line spacing moved the top rail", it, baseline, .01f) }
                topBaseline = baseline
                assertChapterGeometry(chapter, sample, spec)
                page.lines.zipWithNext().forEach { (a, b) ->
                    val extra = if (sample.substring(a.offset, b.offset).contains('\n')) paragraph else 0f
                    assertEquals(maxOf(spec.lineHeightPx, a.descent - b.ascent) + extra, b.baseline - a.baseline, .6f)
                }
                assertTrue("chapter end was stretched", page.usedHeight < spec.bodyHeightPx / 2f)
            }
        }
    }

    @Test fun fallbackFontsAndLargeTypeStayInsideTheMeasuredPage() {
        val mixed = (1..50).joinToString("\n") {
            "中文与 café Ångström gyp 相遇。👩🏽‍💻 🇨🇳 ❤️ 高大的字形也要留得下。下一段继续前行。"
        }
        for (font in listOf("sans", "serif", "mono")) for (size in listOf(40f, 88f, 160f)) {
            val spec = ReaderTypeSpecV30(680, 1207, size, size * 1.3f, 30f,
                size * 1.36f, size * 1.8f, 40f, true, font, 400, 0f)
            val chapter = readerPaginateChapterV30(0, "fallback", "夜航记 👩🏽‍💻", mixed, spec)
            assertChapterGeometry(chapter, mixed, spec)
            // Draw real Android glyphs and inspect pixels beyond the measured font rails.
            val paint = readerTextPaintV30(size, font, 400, 0f)
            chapter.pages.flatMap { it.lines }.filter { !it.title && it.text.contains("👩") }.take(3).forEach { line ->
                val margin = 40
                val extent = kotlin.math.ceil((line.descent - line.ascent).toDouble()).toInt()
                val bitmap = Bitmap.createBitmap(900, extent + margin * 2, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bitmap).drawText(line.text, 5f, margin - line.ascent, paint)
                for (y in 0 until bitmap.height) if (y < margin - 1 || y > margin + extent) {
                    for (x in 0 until bitmap.width) assertEquals("fallback ink crossed its font rail", 0,
                        android.graphics.Color.alpha(bitmap.getPixel(x, y)))
                }
                bitmap.recycle()
            }
        }
    }

    @Test fun nineSpacingCombinationsUseNativeProductionScreenshots() {
        val lineFactor = mutableFloatStateOf(1.3f)
        val paragraphDp = mutableFloatStateOf(0f)
        val selected = mutableIntStateOf(0)
        var current: ReaderChapterPagesV30? = null
        var currentSpec: ReaderTypeSpecV30? = null
        var currentGeometry: ReaderGeometryV30? = null
        val metrics = StringBuilder("line_factor,paragraph_dp,page,title,lines,body_top_px,body_bottom_px,first_font_top_px,last_font_bottom_px,first_ink_top_px,last_ink_bottom_px,target_advance_px,min_body_advance_px,max_body_advance_px,paragraph_gap_px,min_adjustment_px,max_adjustment_px,bottom_whitespace_px,legacy_line_box_inset_px\n")
        rule.setContent {
            ReaderWindowSessionV27(true)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val geometry = remember(constraints, density.density) {
                    ReaderGeometryV30.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density, 0f, 0f, 22f)
                }
                val spec = remember(geometry, lineFactor.floatValue, paragraphDp.floatValue) {
                    val font = 20f * density.density
                    ReaderTypeSpecV30(geometry.bodyWidth.toInt(), geometry.bodyHeight.toInt(), font, font * lineFactor.floatValue,
                        paragraphDp.floatValue * density.density, font * 1.36f, font * 1.8f, font * 1.6f, true, "sans", 400, 0f)
                }
                val chapter = remember(spec) { readerPaginateChapterV30(0, "spacing-visual", "夜航记\n第一章 迟来的信", body, spec) }
                SideEffect { current = chapter; currentSpec = spec; currentGeometry = geometry }
                val theme = readerThemeV30("paper")
                val paints = remember(spec) { readerPaintsV30(spec, theme, 11f * density.density) }
                Canvas(Modifier.fillMaxSize()) {
                    val index = selected.intValue.coerceIn(chapter.pages.indices)
                    drawReaderPageV30(chapter.pages[index], geometry, theme, paints,
                        ReaderChromeInfoV30("夜航记", "${index + 1}/${chapter.pages.size}", "", "21:30", 80, true, false))
                }
            }
        }
        for (factor in listOf(1.3f, 1.65f, 2f)) for (paragraph in listOf(0f, 8f, 20f)) {
            rule.runOnIdle { lineFactor.floatValue = factor; paragraphDp.floatValue = paragraph; selected.intValue = 0 }
            rule.waitForIdle()
            val chapter = checkNotNull(current)
            val spec = checkNotNull(currentSpec)
            val geometry = checkNotNull(currentGeometry)
            assertChapterGeometry(chapter, body, spec)
            val bodyPaint = readerTextPaintV30(spec.fontSizePx, "sans", 400, 0f)
            val titlePaint = readerTextPaintV30(spec.titleSizePx, "sans", 700, 0f)
            val fm = bodyPaint.fontMetrics
            val oldInset = (spec.lineHeightPx - (fm.descent - fm.ascent)) / 2f
            chapter.pages.forEach { page ->
                val pairs = page.lines.zipWithNext().filter { (a, b) -> !a.title && !b.title }
                val advances = pairs.filter { (a, b) -> !body.substring(a.offset, b.offset).contains('\n') }
                    .map { (a, b) -> b.baseline - a.baseline }
                val adjustments = pairs.map { (a, b) ->
                    val gap = if (body.substring(a.offset, b.offset).contains('\n')) spec.paragraphGapPx else 0f
                    b.baseline - a.baseline - maxOf(spec.lineHeightPx, a.descent - b.ascent) - gap
                }
                val first = page.lines.first()
                val last = page.lines.last()
                fun inkBounds(line: ReaderLineV30): android.graphics.Rect = android.graphics.Rect().also {
                    (if (line.title) titlePaint else bodyPaint).getTextBounds(line.text, 0, line.text.length, it)
                }
                val firstInk = geometry.bodyTop + first.baseline + inkBounds(first).top
                val lastInk = geometry.bodyTop + last.baseline + inkBounds(last).bottom
                metrics.appendLine(listOf(factor, paragraph, page.index, page.lines.any { it.title }, page.lines.size,
                    geometry.bodyTop, geometry.bodyBottom, geometry.bodyTop + first.top, geometry.bodyTop + last.bottom,
                    firstInk, lastInk, spec.lineHeightPx, advances.minOrNull(), advances.maxOrNull(), spec.paragraphGapPx,
                    adjustments.minOrNull(), adjustments.maxOrNull(), geometry.bodyBottom - (geometry.bodyTop + last.bottom), oldInset).joinToString(","))
            }
            for (index in listOf(0, 1, 2, chapter.pages.lastIndex)) {
                rule.runOnIdle { selected.intValue = index }
                saveFrame("v43-spacing-l${factor}-p${paragraph.toInt()}-page${index + 1}")
            }
        }
        val target = File(rule.activity.getExternalFilesDir(null), "reader-qa/v43-spacing-metrics.csv")
        target.writeText(metrics.toString())
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${target.absolutePath} /sdcard/Download/reader-qa/${target.name}").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    @Test fun continuousFullPageScreenshotsUseTheProductionRenderer() {
        val selected = mutableIntStateOf(1)
        rule.setContent {
            ReaderWindowSessionV27(true)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val geometry = remember(constraints, density.density) {
                    ReaderGeometryV30.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density,
                        0f, 0f, 22f)
                }
                val spec = remember(geometry) {
                    val font = 20f * density.density
                    ReaderTypeSpecV30(geometry.bodyWidth.toInt(), geometry.bodyHeight.toInt(), font, font * 1.65f,
                        8f * density.density, font * 1.36f, font * 1.8f, 22f, true, "sans", 400, 0f)
                }
                val chapter = remember(spec) { readerPaginateChapterV30(0, "visual", "夜航记", body, spec) }
                val theme = readerThemeV30("paper")
                val paints = remember(spec) { readerPaintsV30(spec, theme, 11f * density.density) }
                Canvas(Modifier.fillMaxSize()) {
                    drawReaderPageV30(chapter.pages[selected.intValue], geometry, theme, paints,
                        ReaderChromeInfoV30("夜航记", "${selected.intValue + 1}/${chapter.pages.size}", "", "21:30", 80, true, false))
                }
            }
        }
        repeat(10) { index ->
            rule.runOnIdle { selected.intValue = index + 1 }
            saveFrame("v41-page-${index + 1}")
        }
    }

    @Test fun readingFirstNavigationAndSourceEmptyStates() {
        val vm = OnlineBooksViewModelV36(rule.activity.application as Application)
        val books = listOf(
            ReaderBookUi("fixture1", "夜航记", "悬疑", "一封迟来的信", "", "", 12000, 100000, 1, 1L),
            ReaderBookUi("fixture2", "山中来信", "文学", "", "", "", 5000, 60000, 1, 2L),
        )
        rule.setContent {
            LanghuanStableTheme {
                ShelfLuoShuFunctionalV1(
                    state = LibraryExperienceState(stories = books, libraryLoaded = true),
                    importState = LocalBookImportUiStateV1(), openingBookId = null,
                    onOpenBook = {}, onOpenTavern = {}, onImportLocal = {}, onDeleteBook = {},
                    onCreate = {}, onAiSetup = {}, onRunCenter = {}, onSkills = {},
                    onlineContent = { sources -> OnlineBooksPageV36(vm, {}, {}, embedded = true, startWithSources = sources) },
                )
            }
        }
        rule.onNodeWithText("夜航记").assertIsDisplayed()
        saveFrame("v44-shelf")
        rule.onAllNodesWithText("书城").onLast().performClick()
        rule.onNodeWithText("书名或作者").assertIsDisplayed()
        saveFrame("v44-bookstore")
        rule.onNodeWithText("管理书源").performClick()
        rule.onNodeWithText("AI 生成书源").assertIsDisplayed()
        saveFrame("v44-sources")
        rule.onNodeWithText("AI 生成书源").performClick()
        rule.onNodeWithText("网站链接").assertIsDisplayed()
        rule.onNodeWithText("开始生成").performScrollTo().assertIsDisplayed()
        val aiWindow = isRoot() and hasAnyDescendant(hasText("网站链接"))
        rule.onNode(hasText("AI 生成书源") and hasAnyAncestor(aiWindow), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("网站链接").assertIsDisplayed()
        saveFrame("v44-ai-source")
        // Dispatch through the focused Android Window. Calling the Activity dispatcher
        // skips Dialog's back handling and can navigate the background shelf instead.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5000) { rule.onAllNodesWithText("网站链接").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("返回书城").assertIsDisplayed()
        rule.onAllNodesWithText("网站链接").assertCountEquals(0)
        rule.onAllNodesWithText("我的").onLast().performClick()
        rule.onNodeWithText("阅读，保持简单").assertIsDisplayed()
        saveFrame("v44-profile")
        rule.onNodeWithText("AI 配置").performScrollTo().assertIsDisplayed()
        repeat(3) {
            rule.onAllNodesWithText("书架").onLast().performClick()
            rule.onNodeWithText("夜航记").assertIsDisplayed()
            rule.onAllNodesWithText("书城").onLast().performClick()
            rule.onNodeWithText("书名或作者").assertIsDisplayed()
        }
    }
}
