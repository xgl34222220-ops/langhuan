package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AiSourceSaveRecoveryV68Test {
    @Test
    fun successfulRetryClearsTheFailedSaveAndPublishesTheStoredSource() {
        val report = fixtureReport(enabledExplore = true)
        val saved = aiSourceSavedStateV68(
            OnlineBooksStateV36(
                aiSteps = listOf(AiSourceStepV37("保存", ok = false, completed = true)),
                aiRunning = true,
                aiReport = report,
                aiError = "书源保存失败",
                aiStopped = true,
            ),
            report.source,
        )

        assertEquals("save-recovery", saved.aiSavedSourceId)
        assertEquals("合成恢复书源", saved.aiSavedSourceName)
        assertNull(saved.aiReport)
        assertNull(saved.aiError)
        assertEquals(emptyList<AiSourceStepV37>(), saved.aiSteps)
        assertFalse(saved.aiRunning)
        assertFalse(saved.aiStopped)
    }

    @Test
    fun failedSaveUsesAnExplicitRetryLabelForEitherSourceKind() {
        val explore = OnlineBooksStateV36(aiReport = fixtureReport(true), aiError = "写盘失败")
        val search = OnlineBooksStateV36(aiReport = fixtureReport(false), aiError = "写盘失败")

        assertEquals("重试保存书源", aiSourceSaveActionLabelV68(explore))
        assertEquals("重试保存搜索书源", aiSourceSaveActionLabelV68(search))
        assertEquals("保存书源", aiSourceSaveActionLabelV68(explore.copy(aiError = null)))
    }

    private fun fixtureReport(enabledExplore: Boolean): AiSourceReportV37 = AiSourceReportV37(
        source = BookSourceV36(
            id = "save-recovery",
            name = "合成恢复书源",
            baseUrl = "https://example.invalid",
            enabledExplore = enabledExplore,
        ),
        searchCount = 1,
        bookName = "合成书",
        chapterCount = 1,
        sample = "合成正文",
    )
}
