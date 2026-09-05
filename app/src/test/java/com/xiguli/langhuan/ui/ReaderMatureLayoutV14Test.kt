package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.reader.ReaderExperience
import com.xiguli.langhuan.ui.reader.ReaderMeasuredPaginationV16
import com.xiguli.langhuan.ui.reader.ReaderPagedLayoutV14
import com.xiguli.langhuan.ui.reader.rememberReaderMeasuredPaginationV16
import com.xiguli.langhuan.ui.reader.splitReaderPagesV10
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMatureLayoutV14Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun pagedReaderRendersFrozenComposeMeasuredPages() {
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()
        val page = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderPagedLayoutV14.kt").readText()
        val measured = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV16.kt").readText()

        assertTrue(reader.contains("rememberReaderMeasuredPaginationV16("))
        assertTrue(reader.contains("measuredPagination.pages"))
        assertTrue(reader.contains("measuredPagination.offsets"))
        assertTrue(reader.contains("measuredPagination.layoutToken"))
        assertTrue(reader.contains("indentFirstParagraph = measuredPagination.indentFirstParagraph"))
        assertFalse(reader.contains("splitReaderPagesV10(\n            readingText"))

        assertTrue(measured.contains("rememberTextMeasurer"))
        assertTrue(measured.contains("LocalWindowInfo.current"))
        assertTrue(measured.contains("WindowInsets.safeDrawing"))
        assertTrue(measured.contains("TextIndent"))
        assertFalse(measured.contains("import android.content.res.Resources"))
        assertFalse(measured.contains("import android.graphics.Typeface"))
        assertFalse(measured.contains("import android.text.StaticLayout"))

        // Never force start + 1 when the remaining viewport cannot fit even one full line.
        assertTrue(measured.contains("var best = start"))
        assertTrue(measured.contains("if (splitEnd <= cursor && pieces.isNotEmpty()) break"))
        assertTrue(measured.contains("return best.coerceIn(start, end)"))

        assertTrue(page.contains("ReaderPageParagraphsV16"))
        assertTrue(page.contains("indentFirstParagraph"))
        assertTrue(page.contains("fontSize = MaterialTheme.typography.labelSmall.fontSize"))
        assertTrue(page.contains("lineHeight = MaterialTheme.typography.labelSmall.lineHeight"))
        assertTrue(page.contains("bottom = 4.dp"))
        assertTrue(page.contains("padding(top = 4.dp)"))
        assertTrue(page.contains("Spacer(Modifier.height(paragraphSpacing.coerceIn(0f, 24f).dp))"))
        assertTrue(page.contains("${'$'}{contentPage + 1}/${'$'}{pageCount.coerceAtLeast(1)}"))
    }

    @Test
    fun presetsKeepCompactReaderDefaults() {
        val state = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderReadingStateV11.kt").readText()
        val migration = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderTypographyMigrationV14.kt").readText()

        assertTrue(state.contains("lineFactor = 1.72f"))
        assertTrue(state.contains("lineFactor = 1.70f"))
        assertTrue(state.contains("paragraphSpacing = 7f"))
        assertTrue(migration.contains("exactLegacy"))
        assertTrue(migration.contains("typography_version_"))
    }
}
