package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraTechnologyGuardTest {
    private val guard = EraTechnologyGuard()

    @Test
    fun `2026 unexplained personal landline is blocked`() {
        val issues = guard.inspect(
            request("故事发生在 2026 年的普通城市家庭。"),
            GeneratedChapter(content = "客厅里的座机突然响了。周衍拿起听筒，对方没有说话。"),
        )

        assertTrue(issues.any { it.code == "MODERN_LANDLINE_UNJUSTIFIED" })
    }

    @Test
    fun `2026 hospital landline remains valid`() {
        val issues = guard.inspect(
            request("故事发生在 2026 年。"),
            GeneratedChapter(content = "护士站的座机响了，值班护士接起医院内线。"),
        )

        assertFalse(issues.any { it.code == "MODERN_LANDLINE_UNJUSTIFIED" })
    }

    @Test
    fun `landline cannot inherit smartphone interface`() {
        val issues = guard.inspect(
            request("故事发生在 2026 年。"),
            GeneratedChapter(content = "酒店前台的座机亮起联系人头像，他滑动接听。"),
        )

        assertTrue(issues.any { it.code == "DEVICE_CAPABILITY_MIXED" })
    }

    @Test
    fun `vague landline caller display is rejected`() {
        val issues = guard.inspect(
            request("故事发生在 2026 年。"),
            GeneratedChapter(content = "办公室的座机响起，来电显示写着母亲。"),
        )

        assertTrue(issues.any { it.code == "LANDLINE_CALLER_ID_AMBIGUOUS" })
    }

    @Test
    fun `old era rejects technology that did not yet exist`() {
        val issues = guard.inspect(
            request("故事当前发生在 1998 年。"),
            GeneratedChapter(content = "周衍打开微信，扫码支付了车费。"),
        )

        assertTrue(issues.any { it.code == "TECH_NOT_AVAILABLE_IN_ERA" })
    }

    @Test
    fun `prompt carries explicit era and adoption rules`() {
        val prompt = guard.promptText(request("故事发生在 2026 年。 ").snapshot)

        assertTrue(prompt.contains("2026"))
        assertTrue(prompt.contains("普通私人通信默认使用手机"))
        assertTrue(prompt.contains("座机不能出现锁屏"))
    }

    private fun request(premise: String): GenerationRequest {
        val novelId = "era-test"
        return GenerationRequest(
            snapshot = StorySnapshot(
                novel = Novel(novelId, "年代测试", "悬疑", premise, "选择", 200_000),
                activeOutline = emptyList(),
                bible = emptyList(),
                characters = emptyList(),
                recentTimeline = emptyList(),
                relevantForeshadowing = emptyList(),
                recentSummaries = emptyList(),
            ),
            chapter = ChapterDraft(
                id = "chapter-1",
                novelId = novelId,
                chapterNumber = 1,
                title = "电话",
                objective = "接到一通异常来电",
                scenePlan = emptyList(),
            ),
            targetWords = 100,
        )
    }
}
