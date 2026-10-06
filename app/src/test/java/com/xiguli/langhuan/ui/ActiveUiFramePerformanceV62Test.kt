package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveUiFramePerformanceV62Test {
    private fun source(path: String): String = File("src/main/java/com/xiguli/langhuan/ui/$path").readText()

    @Test
    fun activeAnimatedAndScrollingStateReadsAreDeferredOrCoalesced() {
        val app = source("LanghuanApp.kt")
        val reader = source("reader/ReaderScreenV30.kt")

        assertTrue(app.contains(".offset { IntOffset((x + 3.dp).roundToPx(), 0) }"))
        assertFalse(app.contains("Modifier.offset(x + 3.dp)"))

        assertTrue(reader.contains("val visibleScrollLocation by remember(listState, chapterIndex, currentPageIndex)"))
        assertTrue(reader.contains("derivedStateOf {"))
        assertTrue(reader.contains("val (visibleChapter, visiblePage) = visibleScrollLocation"))
        assertTrue(reader.contains("mutableFloatStateOf(prefs.getFloat(\"tts_rate\", 1f))"))
    }
}
