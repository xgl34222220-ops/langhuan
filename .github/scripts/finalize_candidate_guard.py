from pathlib import Path

path = Path('app/src/main/java/com/xiguli/langhuan/data/PersistentStoryRepository.kt')
text = path.read_text()
text = text.replace('import com.xiguli.langhuan.domain.CharacterState\n', '')
text = text.replace('import com.xiguli.langhuan.domain.StateChange\n', '')
text = text.replace(
'''        val withChanges = applyCharacterChanges(snapshot, generated.stateChanges, draft.chapterNumber)
        val chapterSummary = "第${draft.chapterNumber}章：${generated.summary}".trim()
        val summaryHistory = (withChanges.recentSummaries + chapterSummary)
            .filter { it.isNotBlank() }
            .distinct()
        val hotWindow = withChanges.longForm.config.hotChapterWindow.coerceIn(5, 14)
        val foldCount = (summaryHistory.size - hotWindow).coerceAtLeast(0)
        val folded = summaryHistory.take(foldCount)
        val baseSnapshot = withChanges.copy(
            novel = withChanges.novel.copy(
                currentWords = (withChanges.novel.currentWords + wordDelta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            ),
            recentSummaries = summaryHistory.takeLast(hotWindow),
            longTermSummary = foldLongTermSummary(withChanges.longTermSummary, folded),
        )
        val newSnapshot = longFormEngine.settle(baseSnapshot, newDraft, generated)
''',
'''        // Generated metadata is untrusted extraction. Do not let stateChanges mutate Canon here.
        // Character/knowledge/timeline/foreshadow facts must travel Agent -> Candidate -> Canon.
        val chapterSummary = "第${draft.chapterNumber}章：${generated.summary}".trim()
        val summaryHistory = (snapshot.recentSummaries + chapterSummary)
            .filter { it.isNotBlank() }
            .distinct()
        val hotWindow = snapshot.longForm.config.hotChapterWindow.coerceIn(5, 14)
        val foldCount = (summaryHistory.size - hotWindow).coerceAtLeast(0)
        val folded = summaryHistory.take(foldCount)
        val baseSnapshot = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + wordDelta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            ),
            recentSummaries = summaryHistory.takeLast(hotWindow),
            longTermSummary = foldLongTermSummary(snapshot.longTermSummary, folded),
        )
        val safeGenerated = generated.copy(stateChanges = emptyList())
        val newSnapshot = longFormEngine.settle(baseSnapshot, newDraft, safeGenerated)
''')
start = text.find('    private fun applyCharacterChanges(')
end = text.find('    private fun ChapterStateEntity.decodeDraftOrNull()', start)
if start < 0 or end < 0:
    raise SystemExit('legacy direct character change helpers not found')
text = text[:start] + text[end:]
path.write_text(text)

studio_path = Path('app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt')
studio = studio_path.read_text()
studio = studio.replace(
    'message = "正文、版本和长期记忆已保存；正在做 Agent 复盘"',
    'message = "正文与版本已保存；结构化事实将先进入 Candidate，正在做 Agent 复盘"'
)
studio_path.write_text(studio)
