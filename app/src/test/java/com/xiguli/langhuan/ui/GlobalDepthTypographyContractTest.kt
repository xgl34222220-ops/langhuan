package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalDepthTypographyContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun stableThemeUsesV3RadiiAndNovelTypography() {
        val theme = source("src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt")
        // v3：排版收敛到 LanghuanTypography，圆角仅 8/12/16/24。
        assertTrue(theme.contains("typography = LanghuanTypography"))
        assertTrue(theme.contains("extraSmall = RoundedCornerShape(8.dp)"))
        assertTrue(theme.contains("medium = RoundedCornerShape(12.dp)"))
        assertTrue(theme.contains("large = RoundedCornerShape(16.dp)"))
        assertTrue(theme.contains("extraLarge = RoundedCornerShape(24.dp)"))
        assertFalse(theme.contains("26.dp"))
    }

    @Test
    fun commonHierarchyUsesV3BorderAndTokens() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanUiKit.kt")
        val tokens = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanDesignTokens.kt")
        // v3：旧语义收敛进 tokens，卡片去投影改 1px 描边。
        assertTrue(tokens.contains("val strong: Color"))
        assertTrue(tokens.contains("val track: Color"))
        assertTrue(kit.contains("contentPadding: Dp = 16.dp"))
        assertTrue(kit.contains(".border("))
        assertTrue(kit.contains("width = 1.dp"))
        assertFalse(kit.contains("HorizontalDivider"))
        assertFalse(kit.contains(".shadow("))
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
