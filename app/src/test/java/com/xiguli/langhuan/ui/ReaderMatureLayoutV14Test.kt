package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMatureLayoutV14Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun pagedReaderUsesDistinctChapterAndOrdinaryPageLayouts() {
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()
        val page = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderPagedLayoutV14.kt").readText()

        assertTrue(reader.contains("ReaderPagedLayoutV14("))
        assertTrue(reader.contains("readerNormalizeBodyV14"))
        assertTrue(reader.contains("pageBookFraction"))
        assertFalse(reader.contains("formattedPageText(pages[contentPage]"))
        assertTrue(page.contains("contentPage == 0"))
        assertTrue(page.contains("fontSize = 12.sp"))
        assertTrue(page.contains("secondary.copy(alpha = .48f)"))
        assertTrue(page.contains("ReaderPageParagraphsV14"))
        assertTrue(page.contains("Spacer(Modifier.height(paragraphSpacing.coerceIn(0f, 24f).dp))"))
        assertTrue(page.contains("${'$'}{contentPage + 1}/${'$'}{pageCount.coerceAtLeast(1)}"))
    }

    @Test
    fun paginatorAndPresetsUseCompactRealParagraphSpacing() {
        val assets = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderAssetsV10.kt").readText()
        val state = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderReadingStateV11.kt").readText()
        val migration = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderTypographyMigrationV14.kt").readText()

        assertTrue(assets.contains("readerNormalizeBodyV14"))
        assertTrue(assets.contains("heightDp - 205f"))
        assertTrue(assets.contains("heightDp - 190f"))
        assertTrue(assets.contains("paragraphSpacing.coerceIn(0f, 24f)"))
        assertFalse(assets.contains("lineEnd < normalized.length && normalized.getOrNull(lineEnd) == '\\n'"))

        assertTrue(state.contains("lineFactor = 1.72f"))
        assertTrue(state.contains("lineFactor = 1.70f"))
        assertTrue(state.contains("paragraphSpacing = 7f"))
        assertTrue(migration.contains("exactLegacy"))
        assertTrue(migration.contains("typography_version_"))
    }
}
