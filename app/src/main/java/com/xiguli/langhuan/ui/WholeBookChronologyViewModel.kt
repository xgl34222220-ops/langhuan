package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.ChronologyRepairAnalyzer
import com.xiguli.langhuan.engine.ChronologyRepairRisk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WholeBookChapterClock(
    val chapterNumber: Int,
    val title: String,
    val startDay: Int,
    val endDay: Int,
    val timeLabel: String,
    val source: String,
    val risk: ChronologyRepairRisk,
    val issueCount: Int,
    val flashbackCount: Int,
)

data class WholeBookChronologyConflict(
    val risk: ChronologyRepairRisk,
    val chapterNumber: Int,
    val code: String,
    val message: String,
)

data class WholeBookChronologyReport(
    val chapters: List<WholeBookChapterClock> = emptyList(),
    val conflicts: List<WholeBookChronologyConflict> = emptyList(),
) {
    val highCount: Int get() = conflicts.count { it.risk == ChronologyRepairRisk.HIGH }
    val mediumCount: Int get() = conflicts.count { it.risk == ChronologyRepairRisk.MEDIUM }
    val inferredCount: Int get() = chapters.count { it.source == "候选推定" }
    val maxStoryDay: Int get() = chapters.maxOfOrNull { it.endDay } ?: 0
}

data class WholeBookChronologyUiState(
    val novelId: String = "",
    val isScanning: Boolean = false,
    val report: WholeBookChronologyReport? = null,
    val error: String? = null,
)

class WholeBookChronologyViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(WholeBookChronologyUiState())
    val state: StateFlow<WholeBookChronologyUiState> = _state.asStateFlow()

    fun scan(novelId: String, force: Boolean = false) {
        if (novelId.isBlank() || _state.value.isScanning) return
        if (!force && _state.value.novelId == novelId && _state.value.report != null) return
        viewModelScope.launch {
            _state.value = WholeBookChronologyUiState(novelId = novelId, isScanning = true)
            runCatching {
                val story = projects.loadStory(novelId) ?: error("找不到这本小说")
                val chapters = projects.chapterDrafts(novelId).sortedBy { it.chapterNumber }
                buildReport(story.snapshot, chapters)
            }.onSuccess { report ->
                _state.update { it.copy(isScanning = false, report = report) }
            }.onFailure { error ->
                _state.update { it.copy(isScanning = false, error = error.message ?: "全书时间线扫描失败") }
            }
        }
    }

    private fun buildReport(snapshot: StorySnapshot, chapters: List<ChapterDraft>): WholeBookChronologyReport {
        val clocks = mutableListOf<WholeBookChapterClock>()
        val conflicts = mutableListOf<WholeBookChronologyConflict>()
        var cursorDay = 1
        var previousEnd = 0

        chapters.forEach { chapter ->
            val local = ChronologyRepairAnalyzer.analyze(snapshot, chapter.content)
            local.findings.forEach { finding ->
                conflicts += WholeBookChronologyConflict(
                    risk = finding.risk,
                    chapterNumber = chapter.chapterNumber,
                    code = finding.code,
                    message = finding.title + "：" + finding.detail,
                )
            }

            val sceneMain = chapter.scenePlan.filterNot { it.isFlashback }
            val sceneDays = sceneMain.mapNotNull { it.storyDay.takeIf { day -> day > 0 } }
            val storedEvents = snapshot.recentTimeline.filter { it.chapter == chapter.chapterNumber && !it.isFlashback }
            val storedDays = storedEvents.mapNotNull { it.storyDay.takeIf { day -> day > 0 } }
            val explicitDays = (sceneDays + storedDays).sorted()
            val flashbacks = chapter.scenePlan.count { it.isFlashback } + snapshot.recentTimeline.count { it.chapter == chapter.chapterNumber && it.isFlashback }

            val start: Int
            val end: Int
            val source: String
            if (explicitDays.isNotEmpty()) {
                start = explicitDays.first()
                end = explicitDays.last()
                source = if (sceneDays.isNotEmpty()) "场景时间锁" else "长期时间线"
                cursorDay = maxOf(cursorDay, end)
            } else {
                val signalsNextDay = chapter.content.contains("第二天") || chapter.content.contains("次日") || chapter.content.contains("翌日")
                start = if (previousEnd > 0) previousEnd else cursorDay
                end = if (signalsNextDay) start + 1 else start
                source = "候选推定"
                cursorDay = end
                conflicts += WholeBookChronologyConflict(
                    risk = ChronologyRepairRisk.MEDIUM,
                    chapterNumber = chapter.chapterNumber,
                    code = "CHAPTER_TIME_INFERRED",
                    message = "本章没有结构化故事日，当前只按章节承接关系生成候选第${start}${if (end > start) "-$end" else ""}天；确认前不会写入长期记忆。",
                )
            }

            if (previousEnd > 0 && start < previousEnd) {
                conflicts += WholeBookChronologyConflict(
                    risk = ChronologyRepairRisk.HIGH,
                    chapterNumber = chapter.chapterNumber,
                    code = "CROSS_CHAPTER_TIME_REVERSED",
                    message = "上一章主时间已经推进到故事第${previousEnd}天，本章却从第${start}天开始。除非本章明确是闪回，否则主时间线发生倒退。",
                )
            }
            if (previousEnd > 0 && start > previousEnd + 2 && !hasLongSkipHint(chapter)) {
                conflicts += WholeBookChronologyConflict(
                    risk = ChronologyRepairRisk.MEDIUM,
                    chapterNumber = chapter.chapterNumber,
                    code = "CROSS_CHAPTER_LARGE_GAP",
                    message = "本章从故事第${start}天开始，与上一章第${previousEnd}天相隔较大，但章纲/正文没有识别到明确跳时说明。",
                )
            }

            val timeLabel = sceneMain.map { it.timeOfDay }.filter { it.isNotBlank() }.distinct().joinToString(" → ")
                .ifBlank { storedEvents.map { it.timeOfDay.ifBlank { it.storyTime } }.filter { it.isNotBlank() }.distinct().joinToString(" → ") }
                .ifBlank { "时段待确认" }
            val chapterRisk = (conflicts.filter { it.chapterNumber == chapter.chapterNumber }.maxByOrNull { it.risk.ordinal }?.risk
                ?: local.overallRisk)

            clocks += WholeBookChapterClock(
                chapterNumber = chapter.chapterNumber,
                title = chapter.title,
                startDay = start,
                endDay = end,
                timeLabel = timeLabel,
                source = source,
                risk = chapterRisk,
                issueCount = conflicts.count { it.chapterNumber == chapter.chapterNumber },
                flashbackCount = flashbacks,
            )
            previousEnd = maxOf(previousEnd, end)
        }

        return WholeBookChronologyReport(
            chapters = clocks,
            conflicts = conflicts.distinctBy { Triple(it.chapterNumber, it.code, it.message) },
        )
    }

    private fun hasLongSkipHint(chapter: ChapterDraft): Boolean {
        val text = buildString {
            append(chapter.objective).append(' ')
            append(chapter.summary).append(' ')
            append(chapter.content.take(1_500))
        }
        return Regex("第二天|次日|翌日|数日后|几天后|周后|月后|个月后|年后|多年后|数周|数月|数年|时间跳跃").containsMatchIn(text)
    }
}
