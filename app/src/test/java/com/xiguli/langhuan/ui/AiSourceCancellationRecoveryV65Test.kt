package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSourceCancellationRecoveryV65Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun explicitStopClearsOnlyInFlightArtifactsAndLeavesARetryableState() {
        val stopped = aiSourceStoppedStateV65(
            OnlineBooksStateV36(
                query = "保留的搜索",
                aiSteps = listOf(AiSourceStepV37("正在验证", completed = false)),
                aiRunning = true,
                aiError = "旧错误",
            ),
        )

        assertTrue(stopped.aiStopped)
        assertFalse(stopped.aiRunning)
        assertTrue(stopped.aiSteps.isEmpty())
        assertNull(stopped.aiReport)
        assertNull(stopped.aiError)
        assertEquals("保留的搜索", stopped.query)
    }

    @Test
    fun stopActionPublishesVisibleRecoveryAndTheNextStartClearsIt() {
        val viewModel = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/online/OnlineBooksViewModelV36.kt",
        ).readText()
        val screen = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/online/AiBookSourceScreenV50.kt",
        ).readText()

        assertTrue(viewModel.contains("_state.update(::aiSourceStoppedStateV65)"))
        assertTrue(viewModel.contains("_state.update { aiSourceStartingStateV67(it, useBrowser) }"))
        assertTrue(screen.contains("if (state.aiStopped)"))
        assertTrue(screen.contains("生成已停止"))
        assertTrue(screen.contains("网站地址和测试书名已保留，可重新选择普通或浏览器模式。"))
    }
}
