package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot

/**
 * V10 public health entry. Narrative-order checks live here so chronology is compared by
 * chapter/order rather than accidentally sorted by the value being validated.
 */
object StoryGraphHealthEngine {
    fun analyze(
        snapshot: StorySnapshot,
        chapters: List<ChapterDraft>,
        migrationQueue: CanonMigrationQueue = CanonMigrationQueue(snapshot.novel.id),
    ): StoryGraphHealthReport {
        val base = StoryGraphHealthAnalyzer.analyze(snapshot, chapters, migrationQueue)
        val chronologyIssues = narrativeChronologyIssues(snapshot)
        if (chronologyIssues.isEmpty()) return base
        val issues = (base.issues + chronologyIssues)
            .distinctBy { listOf(it.category, it.title, it.chapterNumber, it.sourceNodeId) }
            .sortedWith(compareByDescending<StoryHealthIssue> { it.severity.ordinal }.thenBy { it.chapterNumber ?: Int.MAX_VALUE })
        return base.copy(score = score(issues), issues = issues)
    }

    private fun narrativeChronologyIssues(snapshot: StorySnapshot): List<StoryHealthIssue> {
        val mainClock = snapshot.recentTimeline
            .filterNot { it.isFlashback }
            .sortedWith(compareBy({ it.chapter }, { if (it.orderInChapter > 0) it.orderInChapter else Int.MAX_VALUE }))
        return mainClock.zipWithNext().mapNotNull { (a, b) ->
            if (a.storyDay <= 0 || b.storyDay <= 0 || b.storyDay >= a.storyDay) return@mapNotNull null
            StoryHealthIssue(
                id = "TIMELINE:HIGH:main-clock:${a.id}:${b.id}",
                category = StoryHealthCategory.TIMELINE,
                severity = StoryHealthSeverity.HIGH,
                title = "主时间线发生倒退",
                detail = "第${a.chapter}章已到故事第${a.storyDay}天，第${b.chapter}章却回到第${b.storyDay}天，且后者未标记为闪回。",
                chapterNumber = b.chapter,
                sourceNodeId = "timeline:${b.id}",
            )
        }
    }

    private fun score(issues: List<StoryHealthIssue>): Int {
        val high = issues.count { it.severity == StoryHealthSeverity.HIGH }
        val medium = issues.count { it.severity == StoryHealthSeverity.MEDIUM }
        val low = issues.count { it.severity == StoryHealthSeverity.LOW }
        return (100 - minOf(60, high * 10) - minOf(28, medium * 4) - minOf(12, low * 2)).coerceIn(0, 100)
    }
}
