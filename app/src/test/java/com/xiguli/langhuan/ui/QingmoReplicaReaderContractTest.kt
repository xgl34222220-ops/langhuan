package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QingmoReplicaReaderContractTest {
    @Test
    fun readerUsesPortedMobileComponentsAndRealActions() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()
        val kit = File(root, "src/main/java/com/xiguli/langhuan/ui/design/LanghuanComponentKitV4.kt").readText()

        assertTrue(entry.contains("ReaderQingmoHeroV13("))
        assertTrue(reader.contains("HeroReaderTabV13.DETAILS"))
        assertTrue(reader.contains("HeroReaderTabV13.DIRECTORY"))
        assertTrue(reader.contains("HeroReaderTabV13.MORE"))
        assertTrue(reader.contains("LanghuanSheetV4("))
        assertTrue(reader.contains("LanghuanTabsV4("))
        assertTrue(reader.contains("LanghuanActionTileV4("))
        assertTrue(kit.contains("LanghuanTokensV4"))
        assertTrue(kit.contains("LanghuanSheetV4"))
        assertTrue(kit.contains("LanghuanTabsV4"))
        assertTrue(kit.contains("LanghuanActionTileV4"))
        assertTrue(kit.contains("LanghuanRowV4"))

        assertTrue(reader.contains("FLAG_KEEP_SCREEN_ON"))
        assertTrue(reader.contains("SCREEN_ORIENTATION_PORTRAIT"))
        assertTrue(reader.contains("WindowCompat.getInsetsController"))
        assertTrue(reader.contains("Key.VolumeUp"))
        assertTrue(reader.contains("Key.VolumeDown"))
        assertTrue(reader.contains("detectVerticalDragGestures"))
        assertTrue(reader.contains("toggleBookmark()"))
        assertTrue(reader.contains("ReaderPageModeV10.COVER"))
        assertTrue(reader.contains("ReaderProgressStoreV11.save("))
        assertTrue(reader.contains("ReaderProgressStoreV11.moveTo("))
        assertTrue(reader.contains("rememberReaderPaginationV18("))

        listOf(
            "排版预设", "主题", "字体", "字号", "行距页边距", "滚动阅读",
            "全文搜索", "音量键翻页", "屏幕常亮", "时间电量", "沉浸式", "锁定竖屏",
        ).forEach { label -> assertTrue("missing reader action: $label", reader.contains("\"$label\"")) }
        val visibleActions = reader.substringAfter("val actions = listOf(").substringBefore("LazyVerticalGrid")
        listOf(
            "定位", "上下翻页", "仿真翻页", "点击动画", "下拉书签", "全屏下一页",
            "背景图遮罩", "背景跟随", "状态栏", "导航栏",
        ).forEach { label -> assertFalse("legacy reader action still exposed: $label", visibleActions.contains("\"$label\"")) }

        assertFalse(reader.contains("IconButton(onClick = {})"))
        assertFalse(reader.contains("onClick = {}"))
    }

    @Test
    fun fixedHeaderBodyFooterAndContinuationIndentAreStructural() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()
        val paginator = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt").readText()

        assertTrue(reader.contains("HeroReaderCanvasV13("))
        assertTrue(reader.contains(".weight(1f)"))
        assertTrue(reader.contains(".onSizeChanged(onBodyViewportChanged)"))
        assertTrue(reader.contains("Spacer(Modifier.height(4.dp))"))
        assertTrue(reader.contains("Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)"))

        assertTrue(reader.contains("pageStartsParagraph"))
        assertTrue(reader.contains("index > 0 || pageStartsParagraph"))
        assertTrue(paginator.contains("indent && startsParagraph"))

        assertTrue(paginator.contains("getLineBottom"))
        assertTrue(paginator.contains("getLineEnd"))
        assertTrue(paginator.contains("lastCompleteLineEndV18"))
        assertTrue(paginator.contains("WindowInsets.systemBarsIgnoringVisibility"))
    }

    @Test
    fun crossingChaptersIsSwipeContinuousAndDirectionallyCorrect() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()

        assertTrue(entry.contains("val chapterKey = state.readingChapter?.id"))
        assertTrue(entry.contains("key(chapterKey)"))
        assertTrue(reader.contains("val pagerPageCount = pages.size.coerceAtLeast(1)"))
        assertTrue(reader.contains("nestedScroll(edgeSwipe)"))
        assertFalse(reader.contains("val leadingBoundary"))
        assertFalse(reader.contains("val trailingBoundary"))
        assertFalse(reader.contains("previousPagination = rememberReaderPaginationV18"))
        assertFalse(reader.contains("nextPagination = rememberReaderPaginationV18"))
        assertTrue(reader.contains("jumpChapter(previous, atEnd = true)"))
        assertTrue(reader.contains("jumpChapter(next, atEnd = false)"))
        assertTrue(reader.contains("positionFraction = if (atEnd) 1f else 0f"))
        assertTrue(reader.contains("textOffset = if (atEnd) Int.MAX_VALUE else 0"))
    }

    @Test
    fun pagerIsSensitiveAndSystemBackReturnsInsideTheApp() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()

        assertTrue(reader.contains("PagerDefaults.flingBehavior"))
        assertTrue(reader.contains("snapPositionalThreshold = 0.32f"))
        assertTrue(reader.contains("BackHandler"))
        assertTrue(reader.contains("panelVisible -> panelVisible = false"))
        assertTrue(reader.contains("persist()\n                onBack()"))
    }

    @Test
    fun readerShipsNamedLayoutPresetsAndKeepsThemEditable() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()

        listOf(
            "清墨",
            "番茄小说风格",
            "微信读书风格",
            "起点阅读风格",
            "掌阅风格",
            "紧凑阅读",
            "舒适阅读",
        ).forEach { name -> assertTrue("missing preset: $name", reader.contains("\"$name\"")) }

        assertTrue(reader.contains("sidePadding"))
        assertTrue(reader.contains("sidePadding, 12f..40f, tokens, onPadding"))
        assertTrue(reader.contains("Slider(value = value.coerceIn(range)"))
        assertTrue(reader.contains("presetKey = \"custom\""))
    }

    @Test
    fun backgroundResumeCannotBecomeAUserPageTurn() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()

        assertTrue(entry.contains("Lifecycle.Event.ON_PAUSE"))
        assertTrue(entry.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(entry.contains("readerMounted = false"))
        assertTrue(entry.contains("delay(120)"))
        assertTrue(entry.contains("Lifecycle.State.RESUMED"))
        assertTrue(reader.contains(".windowInsetsPadding(WindowInsets.systemBars)"))
    }
}

