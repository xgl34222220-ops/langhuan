from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"marker not found: {label}")
    return text.replace(old, new, 1)


# Context Builder: expose only boundary IDs/budgets to prose, never secret truth/debt payoff.
path = Path("app/src/main/java/com/xiguli/langhuan/engine/ContextBuilder.kt")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "        val contractText = ChapterContractGuard.renderContract(contract)\n",
    "        val contractText = ChapterContractGuard.renderContract(contract)\n        val revealBudget = AutonomousExecutionEngine.revealBudget(snapshot, chapterNumber)\n",
    "context reveal budget variable",
)
text = replace_once(
    text,
    '            add("章节合同：\\n$contractText")\n',
    '''            add("章节合同：\\n$contractText")
            add(
                "本章信息揭露预算：完整揭露最多${revealBudget.maxFullReveals}条，部分/暗示最多${revealBudget.maxPartialReveals}条；" +
                    "可完整boundaryId=${revealBudget.allowedFullBoundaryIds.joinToString("、")}；" +
                    "只可部分boundaryId=${revealBudget.allowedPartialBoundaryIds.joinToString("、")}；" +
                    "禁止揭底boundaryId=${revealBudget.forbiddenBoundaryIds.joinToString("、")}。不得因为预算存在而主动解释秘密。"
            )
''',
    "context reveal budget execution",
)
path.write_text(text, encoding="utf-8")


# Planner: consume debt/last-execution context, enrich reveal budgets, and stop full-refreshing every 2 chapters.
path = Path("app/src/main/java/com/xiguli/langhuan/engine/AutonomousStoryPlanner.kt")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '        val boundaries = snapshot.knowledgeLedger.take(20).joinToString("\\n") {\n            "- ${it.title}｜读者=${it.readerState}｜最早完整揭露=第${it.earliestFullRevealChapter}章｜策略=${it.revealPolicy}｜knownBy=${it.knownBy.joinToString("、")}｜unknownTo=${it.unknownTo.joinToString("、")}"\n        }\n',
    '        val boundaries = snapshot.knowledgeLedger.take(20).joinToString("\\n") {\n            "- ${it.title}｜读者=${it.readerState}｜最早完整揭露=第${it.earliestFullRevealChapter}章｜策略=${it.revealPolicy}｜knownBy=${it.knownBy.joinToString("、")}｜unknownTo=${it.unknownTo.joinToString("、")}"\n        }\n        val executionContext = AutonomousExecutionEngine.planningContext(snapshot, startChapter)\n',
    "planner execution context variable",
)
text = replace_once(
    text,
    '''                【长篇健康提醒】
                ${snapshot.longForm.health.warnings.joinToString("\\n") { "- $it" }.ifBlank { "- 当前没有本地健康警报。" }}

                请维护未来${horizon}章连续计划。任何与 Canon 冲突的灵感都必须舍弃，而不是解释成“新真相”。
''',
    '''                【长篇健康提醒】
                ${snapshot.longForm.health.warnings.joinToString("\\n") { "- $it" }.ifBlank { "- 当前没有本地健康警报。" }}

                $executionContext

                请维护未来${horizon}章连续计划。优先偿还已经到期/逾期的剧情债，但不得为了还债破坏 Canon。任何与 Canon 冲突的灵感都必须舍弃，而不是解释成“新真相”。
''',
    "planner execution context prompt",
)
text = replace_once(
    text,
    '''    fun apply(snapshot: StorySnapshot, plan: AutonomousStoryPlan): StorySnapshot =
        snapshot.copy(longForm = snapshot.longForm.copy(autonomousPlan = plan))
''',
    '''    fun apply(snapshot: StorySnapshot, plan: AutonomousStoryPlan): StorySnapshot {
        val enriched = AutonomousExecutionEngine().enrichRevealBudgets(snapshot, plan)
        return snapshot.copy(longForm = snapshot.longForm.copy(autonomousPlan = enriched))
    }
''',
    "planner apply reveal budgets",
)
text = replace_once(
    text,
    '''            return remaining < 3 || currentChapter - plan.baseChapter >= 2 ||
                plan.driftSignals.any { it.severity == DriftSeverity.HIGH }
''',
    '''            return remaining < 3 ||
                plan.driftSignals.any { it.severity == DriftSeverity.HIGH } ||
                (plan.canonDigest.isNotBlank() && plan.canonDigest != canonDigest(snapshot))
''',
    "planner refresh policy",
)
text = replace_once(
    text,
    '''                if (plan.correctionStrategy.isNotBlank()) appendLine("最小纠偏：${plan.correctionStrategy.take(900)}")
''',
    '''                appendLine(AutonomousExecutionEngine.planningContext(snapshot, future.firstOrNull()?.chapterNumber ?: current + 1))
                if (plan.correctionStrategy.isNotBlank()) appendLine("最小纠偏：${plan.correctionStrategy.take(900)}")
''',
    "planner prompt debt context",
)
path.write_text(text, encoding="utf-8")


# Studio: close the loop after commit, then selectively replan only affected future chapters.
path = Path("app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import com.xiguli.langhuan.engine.AutonomousStoryPlanner\n",
    "import com.xiguli.langhuan.engine.AutonomousStoryPlanner\nimport com.xiguli.langhuan.engine.AutonomousExecutionEngine\n",
    "studio execution import",
)
old = '''                    val gateway = configuredGateway()
                    if (gateway != null) {
                        _state.update { it.copy(isAgentReviewing = true) }
                        runCatching { NovelAgentEngine(gateway).reviewChapter(persisted.snapshot, persisted.draft) }
                            .onSuccess { review -> _state.update { it.copy(isAgentReviewing = false, agentReview = review, message = "正文已保存，Agent 复盘完成") } }
                            .onFailure { _state.update { it.copy(isAgentReviewing = false, message = "正文已保存；Agent 自动复盘失败，可在 Agent 页手动重试") } }
                        if (AutonomousStoryPlanner.shouldRefresh(persisted.snapshot, persisted.draft.chapterNumber)) {
                            _state.update { it.copy(isAutonomousPlanning = true, message = "正文已保存；正在补足未来滚动计划") }
                            runCatching {
                                val planner = AutonomousStoryPlanner(gateway)
                                val plan = planner.plan(persisted.snapshot, persisted.draft, 6)
                                projects.saveStructure(planner.apply(persisted.snapshot, plan), persisted.draft)
                            }.onSuccess { planned ->
                                _state.update {
                                    it.copy(
                                        snapshot = planned.snapshot,
                                        draft = planned.draft,
                                        isAutonomousPlanning = false,
                                        message = "正文已保存；Agent 复盘完成，未来滚动计划已同步",
                                    )
                                }
                                refreshWorkspace()
                            }.onFailure {
                                _state.update { it.copy(isAutonomousPlanning = false, message = "正文已保存；自治计划自动刷新失败，可在 Agent 页手动重试") }
                            }
                        }
                    }
'''
new = '''                    val gateway = configuredGateway()
                    if (gateway != null) {
                        var working = persisted
                        val executionEngine = AutonomousExecutionEngine(gateway)
                        val execution = runCatching {
                            executionEngine.assess(working.snapshot, working.draft, result.chapter)
                        }.getOrNull()
                        if (execution != null) {
                            runCatching {
                                val settled = executionEngine.settle(working.snapshot, working.draft, result.chapter, execution)
                                projects.saveStructure(settled, working.draft)
                            }.onSuccess { settled ->
                                working = settled
                                _state.update { state ->
                                    state.copy(
                                        snapshot = settled.snapshot,
                                        draft = settled.draft,
                                        message = "正文已保存；计划执行审计 ${execution.completionScore} 分",
                                    )
                                }
                            }.onFailure {
                                _state.update { state -> state.copy(message = "正文已保存；计划执行审计未能落库，后续可自动补算") }
                            }
                        }

                        _state.update { it.copy(isAgentReviewing = true) }
                        runCatching { NovelAgentEngine(gateway).reviewChapter(working.snapshot, working.draft) }
                            .onSuccess { review -> _state.update { it.copy(isAgentReviewing = false, agentReview = review, message = "正文已保存，Agent 复盘完成") } }
                            .onFailure { _state.update { it.copy(isAgentReviewing = false, message = "正文已保存；Agent 自动复盘失败，可在 Agent 页手动重试") } }

                        val selective = execution?.let(AutonomousExecutionEngine::shouldSelectiveReplan) == true
                        val fullRefresh = AutonomousStoryPlanner.shouldRefresh(working.snapshot, working.draft.chapterNumber)
                        if (selective || fullRefresh) {
                            _state.update {
                                it.copy(
                                    isAutonomousPlanning = true,
                                    message = if (selective) "正文已保存；正在只重算受影响的后续章节" else "正文已保存；正在补足未来滚动计划",
                                )
                            }
                            runCatching {
                                val planner = AutonomousStoryPlanner(gateway)
                                val candidate = planner.plan(working.snapshot, working.draft, 6)
                                val nextPlan = if (selective && !fullRefresh) {
                                    executionEngine.mergeSelectivePlan(
                                        working.snapshot,
                                        candidate,
                                        execution?.affectedFutureChapters.orEmpty(),
                                    )
                                } else candidate
                                projects.saveStructure(planner.apply(working.snapshot, nextPlan), working.draft)
                            }.onSuccess { planned ->
                                working = planned
                                _state.update {
                                    it.copy(
                                        snapshot = planned.snapshot,
                                        draft = planned.draft,
                                        isAutonomousPlanning = false,
                                        message = if (selective && !fullRefresh) "计划-实际偏差已吸收，只重算了受影响章节" else "未来滚动计划已同步",
                                    )
                                }
                                refreshWorkspace()
                            }.onFailure {
                                _state.update { it.copy(isAutonomousPlanning = false, message = "正文已保存；自治计划重算失败，可在 Agent 页手动重试") }
                            }
                        }
                    }
'''
text = replace_once(text, old, new, "studio commit execution loop")
path.write_text(text, encoding="utf-8")


# Agent UI: show execution score, debt ledger and per-chapter reveal budgets.
path = Path("app/src/main/java/com/xiguli/langhuan/ui/AutonomousPlanPanel.kt")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    val watchRisk = plan.driftSignals.count { it.severity == DriftSeverity.WATCH }\n",
    '''    val watchRisk = plan.driftSignals.count { it.severity == DriftSeverity.WATCH }
    val activeDebts = snapshot.longForm.narrativeDebts.filter { it.status.name != "RESOLVED" }
    val overdueDebts = activeDebts.count { it.status.name == "OVERDUE" }
    val lastExecution = snapshot.longForm.executionHistory.lastOrNull()
''',
    "ui debt variables",
)
text = replace_once(
    text,
    '''        Button(
            onClick = { vm.refreshAutonomousPlan(6) },
''',
    '''        lastExecution?.let { execution ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (execution.status.name == "DEVIATED") MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("最近计划执行 · 第${execution.chapterNumber}章 · ${execution.completionScore}分", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text("状态：${execution.status.name}｜实际：${execution.actualSummary}", style = MaterialTheme.typography.bodySmall)
                    if (execution.affectedFutureChapters.isNotEmpty()) Text("受影响后续：${execution.affectedFutureChapters.joinToString("、") { "第${it}章" }}", style = MaterialTheme.typography.labelSmall, color = LocalMiuixTokens.current.textSecondary)
                    if (execution.repairHint.isNotBlank()) Text("最小修复：${execution.repairHint}", style = MaterialTheme.typography.labelSmall, color = LocalMiuixTokens.current.textSecondary)
                }
            }
        }

        if (activeDebts.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (overdueDebts > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("剧情债务 · ${activeDebts.size} 项${if (overdueDebts > 0) " · $overdueDebts 项逾期" else ""}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    activeDebts.sortedByDescending { it.priority }.take(6).forEach { debt ->
                        Text("• [${debt.status.name}/${debt.kind.name}] ${debt.title}｜截止 ${debt.dueStartChapter}-${debt.dueEndChapter}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = { vm.refreshAutonomousPlan(6) },
''',
    "ui execution debt cards",
)
text = replace_once(
    text,
    '''                        if (beat.guardrail.isNotBlank()) Text("护栏：${beat.guardrail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
''',
    '''                        if (beat.guardrail.isNotBlank()) Text("护栏：${beat.guardrail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        val budget = beat.revealBudget
                        Text(
                            "揭露预算：完整≤${budget.maxFullReveals} · 部分/暗示≤${budget.maxPartialReveals}${if (budget.forbiddenBoundaryIds.isEmpty()) "" else " · ${budget.forbiddenBoundaryIds.size}条禁止揭底"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
''',
    "ui reveal budget",
)
path.write_text(text, encoding="utf-8")


# Version
path = Path("app/build.gradle.kts")
text = path.read_text(encoding="utf-8")
text = replace_once(text, "versionCode = 56", "versionCode = 57", "version code")
text = replace_once(text, 'versionName = "0.25.8-alpha01"', 'versionName = "0.25.9-alpha01"', "version name")
path.write_text(text, encoding="utf-8")
