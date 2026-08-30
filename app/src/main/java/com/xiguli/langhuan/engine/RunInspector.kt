package com.xiguli.langhuan.engine

/**
 * One observable stage in a creation/writing run. These events are transient UI telemetry only:
 * they are never Canon, never RAG input and are not persisted into the story snapshot.
 */
enum class RunStage(val label: String) {
    CREATION_CHAT("AI 会谈"),
    PROPOSAL_SYNC("整理建书方案"),
    BLUEPRINT_STAGE_1("蓝图 1/3 · 世界与人物"),
    BLUEPRINT_STAGE_2("蓝图 2/3 · 分卷与章纲"),
    BLUEPRINT_STAGE_3("蓝图 3/3 · 伏笔与校验"),
    CREATE_BOOK("正式建书"),
    CONTEXT("构建写作上下文"),
    LOGIC_PLAN("写前逻辑骨架"),
    DRAFT("流式正文初稿"),
    NOVELIZATION("小说化重构"),
    EDITOR_REVIEW_1("四视角主编"),
    EDITOR_REWRITE("主编整章修订"),
    EDITOR_REVIEW_2("二次复审"),
    METADATA("提取章节事实"),
    CONSISTENCY("一致性 Gate"),
    READY_TO_COMMIT("生成结果就绪"),
    SAVE("保存正文与版本"),
    FULL_BOOK_AUDIT("全书主编巡检"),
    EXECUTION_AUDIT("计划 vs 实际审计"),
    CANDIDATE("Candidate 事实提取"),
    AUTONOMOUS_REPLAN("自治局部重规划"),
    COMPLETE("本轮流程完成"),
}

enum class RunStatus {
    RUNNING,
    SUCCESS,
    SKIPPED,
    WARNING,
    FAILED,
}

data class RunEvent(
    val stage: RunStage,
    val status: RunStatus,
    val detail: String = "",
    val atMillis: Long = System.currentTimeMillis(),
)

fun RunStatus.isTerminal(): Boolean = this != RunStatus.RUNNING

fun blueprintRunStage(stage: Int): RunStage = when (stage.coerceIn(1, 3)) {
    1 -> RunStage.BLUEPRINT_STAGE_1
    2 -> RunStage.BLUEPRINT_STAGE_2
    else -> RunStage.BLUEPRINT_STAGE_3
}
