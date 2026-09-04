package com.xiguli.langhuan.engine

/**
 * Runtime contract for the Sepia fiction adaptation.
 *
 * Sepia remains a C-layer writing method: it never owns Canon, memory, timeline,
 * ChapterContract or Candidate approval. This resolver only makes the selected
 * Sepia operation explicit and observable in Novel Skill OS telemetry.
 */
enum class SepiaOperation(
    val wireName: String,
    val label: String,
    val contract: String,
) {
    WRITE(
        wireName = "write",
        label = "新写",
        contract = "先做叙事架构与节奏选择，再写正文；风格清扫放最后",
    ),
    REVIEW(
        wireName = "review",
        label = "诊断",
        contract = "只列缺陷与证据，不直接修改正文",
    ),
    REFACTOR(
        wireName = "refactor",
        label = "最小修订",
        contract = "先完成缺陷表，再从最深层问题开始做最小原位修改",
    ),
    RECREATE(
        wireName = "recreate",
        label = "重构重写",
        contract = "先抽取不可变事实、事件顺序与意图，再从事实重新小说化",
    ),
}

object SepiaNarrativeEngine {
    const val SKILL_ID = "sepia-fiction"
    const val UPSTREAM_VERSION = "0.6.0"
    const val UPSTREAM_REVISION = "2b87154c1bd58e16f228b7a2142c734482417fd7"

    fun operationFor(task: AiTaskType): SepiaOperation? = when (task) {
        AiTaskType.SCENE_DIRECTOR,
        AiTaskType.PROSE_AUTHOR,
        AiTaskType.AUTONOMOUS_PLANNER,
        -> SepiaOperation.WRITE

        AiTaskType.EDITOR_REVIEW,
        AiTaskType.FULL_BOOK_EDITOR,
        -> SepiaOperation.REVIEW

        AiTaskType.EDITOR_REWRITE -> SepiaOperation.REFACTOR
        AiTaskType.NOVELIZATION -> SepiaOperation.RECREATE
        else -> null
    }

    fun executionDetail(operation: SepiaOperation): String =
        "${operation.wireName} · ${operation.contract}；只影响写法，不覆盖 Canon / 时间线 / 章节合同"
}
