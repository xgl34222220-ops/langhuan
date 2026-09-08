package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalDepthTypographyContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun stableThemeUsesRequestedTypographyMetrics() {
        val theme = source("src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt")
        assertTrue(theme.contains("fontSize = 22.sp"))
        assertTrue(theme.contains("lineHeight = 28.sp"))
        assertTrue(theme.contains("letterSpacing = (-.7).sp"))
        assertTrue(theme.contains("fontSize = 19.sp"))
        assertTrue(theme.contains("lineHeight = 24.sp"))
        assertTrue(theme.contains("letterSpacing = .6.sp"))
        assertTrue(theme.contains("fontSize = 14.5.sp"))
        assertTrue(theme.contains("lineHeight = 18.sp"))
        assertTrue(theme.contains("letterSpacing = .3.sp"))
    }

    @Test
    fun coreHierarchyUsesDepthInsteadOfVisibleBorders() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanUiKit.kt")
        assertTrue(kit.contains("val strong: Color"))
        assertTrue(kit.contains("val track: Color"))
        assertTrue(kit.contains(".shadow(6.dp, CircleShape"))
        assertTrue(kit.contains(".background(t.track)"))
        assertFalse(kit.contains("HorizontalDivider"))
        assertFalse(kit.contains("Modifier.border"))
    }

    @Test
    fun readerMobilePrimitivesShareDepthLanguage() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanComponentKitV4.kt")
        assertTrue(kit.contains("val strong: Color"))
        assertTrue(kit.contains("val track: Color"))
        assertTrue(kit.contains(".height(90.dp)"))
        assertTrue(kit.contains(".background(tokens.track)"))
    }
}

