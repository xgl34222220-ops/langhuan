package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoShuUiFoundationContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun stableShelfUsesFeatureCompleteLuoShuSurface() {
        val shelfEntry = source("src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt")
        val shelf = source("src/main/java/com/xiguli/langhuan/ui/shell/ShelfLuoShuFunctionalV1.kt")
        assertTrue(shelfEntry.contains("ShelfLuoShuFunctionalV1("))
        assertFalse(shelfEntry.contains("ShelfQingmoFunctionalV9("))
        assertFalse(shelfEntry.contains("ShelfQingmoReplicaV8("))
        assertTrue(shelf.contains("GridCells.Fixed(3)"))
        assertTrue(shelf.contains("RoundedCornerShape(30.dp)"))
        assertTrue(shelf.contains("BookEditPageV5("))
    }

    @Test
    fun activeThemeUsesLuoShuGeometryAndCalmBackground() {
        val theme = source("src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt")
        assertTrue(theme.contains("background = Color(0xFFF6F7F9)"))
        assertTrue(theme.contains("extraSmall = RoundedCornerShape(6.dp)"))
        assertTrue(theme.contains("small = RoundedCornerShape(10.dp)"))
        assertTrue(theme.contains("medium = RoundedCornerShape(14.dp)"))
        assertTrue(theme.contains("large = RoundedCornerShape(20.dp)"))
        assertTrue(theme.contains("extraLarge = RoundedCornerShape(26.dp)"))
        assertTrue(theme.contains("LocalLanghuanUiTokens provides uiTokens"))
    }

    @Test
    fun readerImmersionContractStaysIndependentFromVisualMigration() {
        val readerEntry = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt")
        assertTrue(readerEntry.contains("ReaderWindowSessionV27("))
        assertTrue(readerEntry.contains("ReaderEngineV30("))
        assertFalse(readerEntry.contains("key(chapterKey)"))
        assertTrue(readerEntry.contains("resumeRequested"))
        assertFalse(readerEntry.contains("statusBarsPadding()"))
    }
}
