from pathlib import Path

studio_path = Path('app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt')
studio = studio_path.read_text()
studio = studio.replace('import com.xiguli.langhuan.engine.AgentMemoryApplier\n', '')

old_manual = '''            runCatching { NovelAgentEngine(gateway).reviewChapter(current.snapshot, current.draft) }
                .onSuccess { review -> _state.update { it.copy(isAgentReviewing = false, agentReview = review, message = "Agent 章节复盘完成") } }
                .onFailure { error -> _state.update { it.copy(isAgentReviewing = false, error = error.message ?: "Agent 章节复盘失败") } }
'''
new_manual = '''            runCatching {
                val review = NovelAgentEngine(gateway).reviewChapter(current.snapshot, current.draft)
                val staged = CandidateCanonEngine.stage(current.snapshot, current.draft, review)
                val persisted = projects.saveStructure(staged.snapshot, current.draft)
                Triple(review, staged, persisted)
            }.onSuccess { (review, staged, persisted) ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isAgentReviewing = false,
                        agentReview = review,
                        message = "Agent 复盘完成；${staged.stagedCount} 条事实已进入 Candidate${if (staged.autoConfirmedCount > 0) "，${staged.autoConfirmedCount} 条低风险状态自动确认" else ""}",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isAgentReviewing = false, error = error.message ?: "Agent 章节复盘失败") }
            }
'''
if old_manual not in studio:
    raise SystemExit('manual review block not found')
studio = studio.replace(old_manual, new_manual, 1)

old_auto = '''                        _state.update { it.copy(isAgentReviewing = true) }
                        runCatching { NovelAgentEngine(gateway).reviewChapter(working.snapshot, working.draft) }
                            .onSuccess { review -> _state.update { it.copy(isAgentReviewing = false, agentReview = review, message = "正文已保存，Agent 复盘完成") } }
                            .onFailure { _state.update { it.copy(isAgentReviewing = false, message = "正文已保存；Agent 自动复盘失败，可在 Agent 页手动重试") } }
'''
new_auto = '''                        _state.update { it.copy(isAgentReviewing = true) }
                        runCatching {
                            val review = NovelAgentEngine(gateway).reviewChapter(working.snapshot, working.draft)
                            val staged = CandidateCanonEngine.stage(working.snapshot, working.draft, review)
                            val persisted = projects.saveStructure(staged.snapshot, working.draft)
                            Triple(review, staged, persisted)
                        }.onSuccess { (review, staged, persisted) ->
                            working = persisted
                            _state.update {
                                it.copy(
                                    snapshot = persisted.snapshot,
                                    draft = persisted.draft,
                                    isAgentReviewing = false,
                                    agentReview = review,
                                    message = "正文已保存；Agent 复盘完成，${staged.stagedCount} 条事实进入 Candidate${if (staged.autoConfirmedCount > 0) "，${staged.autoConfirmedCount} 条低风险状态自动确认" else ""}",
                                )
                            }
                        }.onFailure {
                            _state.update { it.copy(isAgentReviewing = false, message = "正文已保存；Agent 自动复盘失败，可在 Agent 页手动重试") }
                        }
'''
if old_auto not in studio:
    raise SystemExit('auto review block not found')
studio = studio.replace(old_auto, new_auto, 1)

old_apply = '''    fun applyAgentMemory() {
        val current = _state.value
        val review = current.agentReview ?: return
        if (busy(current) || review.memoryActions.isEmpty()) return
        val staged = CandidateCanonEngine.stage(current.snapshot, current.draft, review)
        val message = buildString {
            append("已加入 ${staged.stagedCount} 条候选事实")
            if (staged.autoConfirmedCount > 0) append("；${staged.autoConfirmedCount} 条正文可直接证明的低风险状态已自动确认")
            append("。其余事实需在 Candidate / Canon 面板确认")
        }
        saveStructure(staged.snapshot, current.draft, message)
        _state.update { it.copy(agentReview = null) }
    }

'''
studio = studio.replace(old_apply, '')
studio_path.write_text(studio)

agent_path = Path('app/src/main/java/com/xiguli/langhuan/ui/AgentPage.kt')
agent = agent_path.read_text()
old_button = '''                        Spacer(Modifier.height(10.dp))
                        Button(vm::applyAgentMemory, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("加入候选事实")
                        }
'''
new_button = '''                        Spacer(Modifier.height(8.dp))
                        Text("这些提取项已经自动进入 Candidate 候选区；这里的报告本身不会直接改 Canon。", color = LocalMiuixTokens.current.textSecondary)
'''
if old_button not in agent:
    raise SystemExit('candidate button block not found')
agent = agent.replace(old_button, new_button, 1)
agent_path.write_text(agent)
