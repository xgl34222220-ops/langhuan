package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalDepthTypographyContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun stableThemeUsesLuoShuPageRhythmAndNovelTypography() {
        val theme = source("src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt")
        assertTrue(theme.contains("fontSize = 26.sp"))
        assertTrue(theme.contains("lineHeight = 34.sp"))
        assertTrue(theme.contains("fontSize = 22.sp"))
        assertTrue(theme.contains("lineHeight = 28.sp"))
        assertTrue(theme.contains("fontSize = 19.sp"))
        assertTrue(theme.contains("lineHeight = 24.sp"))
        assertTrue(theme.contains("fontSize = 14.5.sp"))
        assertTrue(theme.contains("lineHeight = 18.sp"))
        assertTrue(theme.contains("extraLarge = RoundedCornerShape(30.dp)"))
    }

    @Test
    fun commonHierarchyUsesCalmLuoShuSurfaces() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanUiKit.kt")
        assertTrue(kit.contains("val strong: Color"))
        assertTrue(kit.contains("val track: Color"))
        assertTrue(kit.contains("contentPadding: Dp = 20.dp"))
        assertTrue(kit.contains(".size(48.dp)"))
        assertTrue(kit.contains(".size(44.dp)"))
        assertTrue(kit.contains(".size(21.dp)"))
        assertTrue(kit.contains(".shadow(1.dp, CircleShape"))
        assertTrue(kit.contains(".background(t.track)"))
        assertFalse(kit.contains("HorizontalDivider"))
        assertFalse(kit.contains("Modifier.border"))
    }

    @Test
    fun readerChromeSharesTheSameGeometryWithoutChangingReaderCore() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanComponentKitV4.kt")
        assertTrue(kit.contains("topStart = 30.dp"))
        assertTrue(kit.contains("RoundedCornerShape(18.dp)"))
        assertTrue(kit.contains(".height(90.dp)"))
        assertTrue(kit.contains(".size(44.dp)"))
        assertTrue(kit.contains(".background(tokens.track)"))
    }
}
