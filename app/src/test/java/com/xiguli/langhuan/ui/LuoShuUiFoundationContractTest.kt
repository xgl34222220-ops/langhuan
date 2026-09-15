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
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("RoundedCornerShape(30.dp)"))
        assertTrue(shelf.contains("BookEditPageV5("))
    }

    @Test
    fun activeThemeUsesLuoShuGeometryAndCalmBackground() {
        val theme = source("src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt")
        assertTrue(theme.contains("background = Color(0xFFF4F6FA)"))
        assertTrue(theme.contains("extraSmall = RoundedCornerShape(7.dp)"))
        assertTrue(theme.contains("small = RoundedCornerShape(11.dp)"))
        assertTrue(theme.contains("medium = RoundedCornerShape(18.dp)"))
        assertTrue(theme.contains("large = RoundedCornerShape(24.dp)"))
        assertTrue(theme.contains("extraLarge = RoundedCornerShape(30.dp)"))
        assertTrue(theme.contains("LocalLanghuanUiTokens provides uiTokens"))
    }

    @Test
    fun readerImmersionContractStaysIndependentFromVisualMigration() {
        val readerEntry = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt")
        assertTrue(readerEntry.contains("ReaderWindowSessionV27("))
        assertTrue(readerEntry.contains("key(chapterKey)"))
        assertTrue(readerEntry.contains("resumeRequested"))
        assertFalse(readerEntry.contains("statusBarsPadding()"))
    }
}
