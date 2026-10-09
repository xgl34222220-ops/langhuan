package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.reader.readerChromeTickDelayMsV85
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCenterAndChromeV85Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    private fun source(path: String) =
        File(root, "src/main/java/com/xiguli/langhuan/$path").readText()

    @Test
    fun labelCacheStoresAndPrunesMissingCheckpoints() {
        val cache = RunCenterLabelCacheV85()
        cache.put("a", 1, RunCenterLabelsV85("书A", "第一章"))
        cache.put("a", 2, RunCenterLabelsV85("书A", "第二章"))
        cache.put("b", 1, RunCenterLabelsV85("书B", "序章"))

        assertEquals("第二章", cache.get("a", 2)?.chapterTitle)
        cache.retainOnly(listOf("a" to 2, "c" to 9))

        assertEquals(1, cache.size)
        assertNull(cache.get("a", 1))
        assertNull(cache.get("b", 1))
        assertEquals("书A", cache.get("a", 2)?.novelTitle)
    }

    @Test
    fun readerChromeTickAlignsToMinuteAndStaysBounded() {
        // 12:00:59.000 -> next tick shortly after 12:01:00.
        assertEquals(1_050L, readerChromeTickDelayMsV85(59_000L))
        // Early in a minute the old 20 s battery cadence is kept.
        assertEquals(20_000L, readerChromeTickDelayMsV85(5_000L))
        // Exactly on a boundary never yields more than 20 s or less than 50 ms.
        assertEquals(20_000L, readerChromeTickDelayMsV85(120_000L))
        for (now in listOf(0L, 1L, 59_999L, 1_700_000_000_123L, -1L)) {
            val delay = readerChromeTickDelayMsV85(now)
            assertTrue(delay in 50L..20_000L)
        }
    }

    @Test
    fun runCenterIsLoadedOnEntryAndPolledOnlyWhileVisible() {
        val router = source("ui/LanghuanRootV4.kt")
        assertTrue(router.contains("runCenterVm.refresh()"))
        assertTrue(router.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(router.contains("runCenterVm.refresh(silent = true)"))

        val vm = source("ui/RunCenterViewModel.kt")
        assertTrue(vm.contains("withContext(Dispatchers.IO)"))
        assertTrue(vm.contains("projects.chapterDraft(novelId, chapterNumber)"))
        assertFalse(
            "polling must not rescan every chapter of a book for each checkpoint",
            vm.contains("val chapter = projects.chapterDrafts(novelId).firstOrNull"),
        )
    }

    @Test
    fun launcherReadsThemeOnceAndKeepsDiagnosticsReadable() {
        val activity = source("MainActivity.kt")
        val composition = activity.substringAfter("is LauncherState.Ready -> {")
        assertFalse(composition.substringBefore("LanghuanRootV4(").contains("LanghuanThemeModeStateV50.init("))
        assertTrue(activity.contains("if (status.ready) LanghuanThemeModeStateV50.init(context)"))
        assertTrue(activity.contains("verticalScroll(rememberScrollState())"))
        assertTrue(activity.contains("SelectionContainer"))
    }

    @Test
    fun readerChromeClockDoesNotTickInBackground() {
        val reader = source("ui/reader/ReaderScreenV30.kt")
        assertTrue(reader.contains("chromeLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertFalse(reader.contains("delay(20_000)"))
    }
}
