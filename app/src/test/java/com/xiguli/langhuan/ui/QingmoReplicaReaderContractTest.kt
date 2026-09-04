package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QingmoReplicaReaderContractTest {
    @Test
    fun readerKeepsQingmoPanelGridThemeMotionAndFunctionalControls() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoFunctionalV9.kt").readText()
        val reverseSpec = File(root.parentFile, "design-systems/langhuan/QINGMO_REVERSE_SPEC.md").takeIf { it.exists() }
            ?: File(root, "../design-systems/langhuan/QINGMO_REVERSE_SPEC.md")

        assertTrue(entry.contains("ReaderQingmoFunctionalV9("))
        assertTrue(reader.contains("QingmoReaderTabV9.DETAILS"))
        assertTrue(reader.contains("QingmoReaderTabV9.DIRECTORY"))
        assertTrue(reader.contains("QingmoReaderTabV9.MORE"))
        assertTrue(reader.contains("QingmoThemeGalleryV9("))
        assertTrue(reader.contains("QingmoFontPageV9("))
        assertTrue(reader.contains("QingmoSearchV9("))
        assertTrue(reader.contains("slideInVertically(tween(180"))
        assertTrue(reader.contains("slideInHorizontally(tween(190"))
        assertTrue(reader.contains("FLAG_KEEP_SCREEN_ON"))
        assertTrue(reader.contains("SCREEN_ORIENTATION_PORTRAIT"))
        assertTrue(reader.contains("WindowCompat.getInsetsController"))
        assertTrue(reader.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(reader.contains("Key.VolumeUp"))
        assertTrue(reader.contains("Key.VolumeDown"))
        assertTrue(reader.contains("detectVerticalDragGestures"))
        assertTrue(reader.contains("toggleBookmark()"))
        assertTrue(reader.contains("if (clickAnimation) pagerState.animateScrollToPage"))
        assertTrue(reader.contains("if (fullNext)"))
        assertTrue(reader.contains("ReaderPageModeV10.COVER"))
        assertTrue(reader.contains("rotationY = rawOffset * -7f"))
        assertTrue(reader.contains("chapters.forEach { chapter ->"))
        assertTrue(reader.contains("putStringSet(\"customThemes\""))
        assertTrue(reader.contains("ReaderProgressStoreV11.save("))
        assertTrue(reader.contains("ReaderProgressStoreV11.moveTo("))
        assertTrue(reader.contains("rememberReaderMeasuredPaginationV16("))

        listOf(
            "主题", "字体", "字号", "行段", "定位",
            "上下翻页", "仿真翻页", "全文搜索", "音量键翻页", "屏幕常亮",
            "时间电量", "沉浸式", "点击动画", "下拉书签", "全屏下一页",
            "背景图遮罩", "背景跟随", "状态栏", "导航栏", "锁定竖屏",
        ).forEach { label -> assertTrue("missing Qingmo action: $label", reader.contains("\"$label\"")) }

        assertFalse(reader.contains("IconButton(onClick = {})"))
        assertFalse(reader.contains("TextButton(onClick = {})"))
        assertTrue(reverseSpec.exists())
    }
}
