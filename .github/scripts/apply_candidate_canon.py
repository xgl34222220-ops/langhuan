from pathlib import Path

studio_path = Path('app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt')
studio = studio_path.read_text()

studio = studio.replace(
    'import com.xiguli.langhuan.engine.AgentMemoryApplier\n',
    'import com.xiguli.langhuan.engine.AgentMemoryApplier\nimport com.xiguli.langhuan.engine.CandidateCanonEngine\n',
)

old = '''    fun applyAgentMemory() {
        val current = _state.value
        val review = current.agentReview ?: return
        if (busy(current) || review.memoryActions.isEmpty()) return
        val updated = AgentMemoryApplier.apply(current.snapshot, current.draft.chapterNumber, review)
        saveStructure(updated, current.draft, "Agent 提取的事实已写入长期记忆")
        _state.update { it.copy(agentReview = null) }
    }
'''
new = '''    fun applyAgentMemory() {
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

    fun confirmCandidateFact(candidateId: String) {
        val current = _state.value
        if (busy(current)) return
        val updated = runCatching { CandidateCanonEngine.confirm(current.snapshot, candidateId) }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "候选事实无法写入 Canon") } }
            .getOrNull() ?: return
        saveStructure(updated, current.draft, "候选事实已确认并写入 Canon")
    }

    fun rejectCandidateFact(candidateId: String) {
        val current = _state.value
        if (busy(current)) return
        val updated = CandidateCanonEngine.reject(current.snapshot, candidateId)
        saveStructure(updated, current.draft, "候选事实已拒绝；不会进入 Canon")
    }
'''
if old not in studio:
    raise SystemExit('StudioViewModel applyAgentMemory block not found')
studio = studio.replace(old, new)
studio_path.write_text(studio)

agent_path = Path('app/src/main/java/com/xiguli/langhuan/ui/AgentPage.kt')
agent = agent_path.read_text()
anchor = '        item { AutonomousPlanPanel(state, vm) }\n'
if anchor not in agent:
    raise SystemExit('AgentPage autonomous panel anchor not found')
agent = agent.replace(anchor, anchor + '        item { CandidateCanonPanel(state, vm) }\n', 1)
agent = agent.replace(
    'Text("待写入长期记忆", style = MaterialTheme.typography.titleMedium)',
    'Text("本次提取的候选事实", style = MaterialTheme.typography.titleMedium)'
)
agent = agent.replace(
    'Text("${report.memoryActions.size} 项结构化事实。确认后才会进入人物/时间线/伏笔和 RAG。", color = LocalMiuixTokens.current.textSecondary)',
    'Text("${report.memoryActions.size} 项结构化事实。先加入 Candidate；只有通过本地证明或你确认后才会进入 Canon。", color = LocalMiuixTokens.current.textSecondary)'
)
agent = agent.replace(
    'Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("确认并写入长期记忆")',
    'Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("加入候选事实")'
)
agent_path.write_text(agent)
