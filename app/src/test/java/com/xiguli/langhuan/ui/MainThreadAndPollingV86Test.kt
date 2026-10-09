package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Second optimisation pass (stacked on the Run Center / reader clock fixes):
 * lifecycle-aware shelf polling, startup story decode off the main thread, cheaper shelf covers
 * and a few accessibility / empty-state corrections.
 */
class MainThreadAndPollingV86Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    private fun source(path: String) =
        File(root, "src/main/java/com/xiguli/langhuan/$path").readText()

    @Test
    fun distillationPollingCadenceIsFastOnlyWhileATaskRuns() {
        assertEquals(1_000L, referenceDistillationPollDelayMsV86(anyActive = true))
        assertEquals(4_000L, referenceDistillationPollDelayMsV86(anyActive = false))
    }

    @Test
    fun aiShelfPollsOnlyWhileVisibleAndOffTheMainThread() {
        val shelf = source("ui/AiFirstShelf.kt")
        assertTrue(shelf.contains("lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)"))
        // WorkManager future and the SharedPreferences-backed source store are read on IO.
        val poll = shelf.substringAfter("lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)")
            .substringBefore("return tasks")
        assertTrue(poll.contains("withContext(Dispatchers.IO)"))
        assertTrue(poll.contains("loadReferenceDistillationTasksV86(workManager, sourceStore)"))
        assertFalse(poll.contains("sourceStore.isDismissed"))
        // Covers go through the downsampling cache instead of a full decode in composition.
        assertFalse(shelf.contains("BitmapFactory"))
        assertTrue(shelf.contains("rememberLanghuanCoverV30(book.coverPath, 240)"))
    }

    @Test
    fun studioStartupDecodesStoryOffTheMainThreadAndKeepsRestoreSemantics() {
        val studio = source("ui/StudioViewModel.kt")
        assertTrue(studio.contains("val loaded = withContext(Dispatchers.IO) { loadStartupStoryV86() }"))
        val helper = studio.substringAfter("private suspend fun loadStartupStoryV86()")
            .substringBefore("private fun busy(")
        assertTrue(helper.contains("repository.seedIfNeeded(demo)"))
        assertTrue(helper.contains("projects.loadStory(preferredId)"))
        // Demo fallback chain and boot-loop guard are unchanged.
        assertTrue(helper.contains("localImportPrefs.contains(\"imported_\$it\")"))
        assertTrue(helper.contains("PersistedStory(demo.snapshot, demo.currentDraft)"))
        // Long-book chapter list, checkpoint recovery and shelf mapping are off main too.
        val refresh = studio.substringAfter("private suspend fun refreshWorkspace()")
            .substringBefore("private suspend fun loadStartupStoryV86()")
        assertTrue(refresh.contains("withContext(Dispatchers.IO)"))
        assertTrue(studio.contains("withContext(Dispatchers.IO) { chapterRuns.recover(snapshot, draft) }"))
        assertTrue(studio.contains(".flowOn(Dispatchers.IO)"))
        assertTrue(studio.contains("withContext(Dispatchers.IO) { projects.loadStory(id) }"))
    }

    @Test
    fun writingWorkspaceLoadsStoryOffTheMainThread() {
        val writing = source("ui/WritingFlowViewModel.kt")
        assertTrue(writing.contains("withContext(Dispatchers.IO) { projects.loadStory(novelId) }"))
        assertTrue(writing.contains("withContext(Dispatchers.IO) { referenceDna.summary(novelId) }"))
        assertTrue(writing.contains("private suspend fun restoreDurableRun"))
    }

    @Test
    fun shelfThumbnailsAreDecodedAtThumbnailSizeAndClearTheNavigationBar() {
        assertTrue(HOME_LIST_COVER_PX_V86 < 720)
        assertTrue(HOME_GRID_COVER_PX_V86 in HOME_LIST_COVER_PX_V86 until 720)
        val home = source("ui/LanghuanHomeV4.kt")
        assertTrue(home.contains("targetWidthPx = HOME_LIST_COVER_PX_V86"))
        assertTrue(home.contains("targetWidthPx = HOME_GRID_COVER_PX_V86"))
        assertTrue(home.contains("bottom = t.space6 + navigationBottom"))
        assertTrue(home.contains("it.key in availableIds"))
        val cover = source("ui/CoverStudioV3.kt")
        assertTrue(cover.contains("targetWidthPx: Int = 720"))
        assertTrue(cover.contains("remember(path) { path.isNotBlank() && File(path).exists() }"))
    }

    @Test
    fun accessibilityAndEmptyStateCorrections() {
        val settings = source("ui/AiProviderSetupPage.kt")
        assertTrue(settings.contains("role = Role.RadioButton"))
        assertTrue(settings.contains("selected = active"))
        assertTrue(settings.contains("if (showRouting) \"已展开\" else \"已收起\""))
        val reader = source("ui/reader/ReaderScreenV30.kt")
        assertTrue(reader.contains("onClickLabel = \"切换朗读语速\""))
        assertTrue(reader.contains("LiveRegionMode.Polite"))
        val runCenter = source("ui/run/RunCenterScreenV50.kt")
        assertTrue(runCenter.contains("state.error.isNullOrBlank() -> {"))
    }
}
