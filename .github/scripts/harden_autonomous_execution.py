from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"marker not found: {label}")
    return text.replace(old, new, 1)

path = Path("app/src/main/java/com/xiguli/langhuan/engine/AutonomousExecutionEngine.kt")
text = path.read_text(encoding="utf-8")

# 1) Existing plot-promise debts can actually be settled by later committed prose.
old = '''        val byId = snapshot.longForm.narrativeDebts.associateBy { it.id }.toMutableMap()

        snapshot.relevantForeshadowing.forEach { item ->
'''
new = '''        val byId = snapshot.longForm.narrativeDebts.associateBy { it.id }.toMutableMap()
        val actualChapterText = (generated.summary + "\\n" + generated.content.take(5_000)).trim()

        byId.values
            .filter { debt ->
                debt.kind == NarrativeDebtKind.PLOT_PROMISE &&
                    debt.status != NarrativeDebtStatus.RESOLVED &&
                    debt.openedChapter < current &&
                    debt.resolutionCriteria.isNotBlank()
            }
            .forEach { debt ->
                val payoffScore = semanticSurfaceScore(debt.resolutionCriteria.lowercase(), actualChapterText.lowercase())
                if (payoffScore >= 78) {
                    byId[debt.id] = debt.copy(
                        status = NarrativeDebtStatus.RESOLVED,
                        lastTouchedChapter = current,
                        priority = 0,
                    )
                }
            }

        snapshot.relevantForeshadowing.forEach { item ->
'''
text = replace_once(text, old, new, "plot promise settlement")

# 2) Character arc debt needs an actual relevant character-state change, not any update timestamp.
old = '''            val reached = current >= target.targetChapter
            val character = snapshot.characters.firstOrNull { it.name == target.name }
            val resolved = reached && character != null && character.lastUpdatedChapter >= target.targetChapter
            byId[id] = NarrativeDebt(
'''
new = '''            val reached = current >= target.targetChapter
            val character = snapshot.characters.firstOrNull { it.name == target.name }
            val relevantChanges = generated.stateChanges.filter { change ->
                change.subject.equals(target.name, ignoreCase = true) &&
                    change.field.lowercase() in setOf(
                        "goal", "目标", "emotionalstate", "情绪", "情绪状态",
                        "relationship", "关系", "knownsecrets", "秘密", "已知秘密",
                        "physicalstate", "身体状态", "伤势",
                    )
            }
            val arcEvidence = relevantChanges.joinToString(" ") { it.after.ifBlank { it.before } } + " " + generated.summary
            val resolved = reached && relevantChanges.isNotEmpty() &&
                semanticSurfaceScore(target.desiredChange.lowercase(), arcEvidence.lowercase()) >= 65
            val touchedChapter = if (relevantChanges.isNotEmpty()) current else maxOf(old?.lastTouchedChapter ?: 0, character?.lastUpdatedChapter ?: 0)
            byId[id] = NarrativeDebt(
'''
text = replace_once(text, old, new, "character debt evidence")
text = replace_once(
    text,
    '                lastTouchedChapter = maxOf(old?.lastTouchedChapter ?: 0, character?.lastUpdatedChapter ?: 0),\n',
    '                lastTouchedChapter = touchedChapter,\n',
    "character debt touched chapter",
)

# 3) A new plot debt should remember the actual promised objective as its resolution criterion.
text = replace_once(
    text,
    '                resolutionCriteria = execution.repairHint.ifBlank { execution.plannedObjective }.take(420),\n',
    '                resolutionCriteria = execution.plannedObjective.ifBlank { execution.repairHint }.take(420),\n',
    "plot debt resolution criterion",
)

# 4) If there are more eligible boundaries than the bounded allow-list, the overflow must be denied,
# not silently fall through as neither allowed nor forbidden.
old = '''            return RevealBudget(
                chapterNumber = chapterNumber,
                maxFullReveals = if (full.isEmpty()) 0 else 1,
                maxPartialReveals = partial.size.coerceIn(0, 2),
                allowedFullBoundaryIds = full.take(4),
                allowedPartialBoundaryIds = partial.take(8),
                forbiddenBoundaryIds = forbidden.take(24),
            )
'''
new = '''            val allowedFull = full.take(4)
            val allowedPartial = partial.take(8)
            val denied = (forbidden + full.drop(allowedFull.size) + partial.drop(allowedPartial.size)).distinct()
            return RevealBudget(
                chapterNumber = chapterNumber,
                maxFullReveals = if (allowedFull.isEmpty()) 0 else 1,
                maxPartialReveals = allowedPartial.size.coerceIn(0, 2),
                allowedFullBoundaryIds = allowedFull,
                allowedPartialBoundaryIds = allowedPartial,
                forbiddenBoundaryIds = denied.take(32),
            )
'''
text = replace_once(text, old, new, "reveal allowlist overflow")

path.write_text(text, encoding="utf-8")

# Add regression test for allow-list overflow.
test_path = Path("app/src/test/java/com/xiguli/langhuan/engine/AutonomousExecutionEngineTest.kt")
test = test_path.read_text(encoding="utf-8")
marker = '''    @Test
    fun `partial execution marks only nearby future chapters for replanning`() = runBlocking {
'''
addition = '''    @Test
    fun `reveal budget denies boundaries outside bounded allow lists`() {
        val ledger = (1..7).map { index ->
            KnowledgeBoundary(
                id = "full-$index",
                title = "秘密$index",
                readerState = ReaderKnowledgeState.UNKNOWN,
                revealPolicy = KnowledgeRevealPolicy.FULL,
                earliestFullRevealChapter = 1,
            )
        }
        val budget = AutonomousExecutionEngine.revealBudget(snapshot().copy(knowledgeLedger = ledger), 12)

        assertEquals(4, budget.allowedFullBoundaryIds.size)
        assertTrue("full-5" in budget.forbiddenBoundaryIds)
        assertTrue("full-7" in budget.forbiddenBoundaryIds)
        assertEquals(1, budget.maxFullReveals)
    }

''' + marker
test = replace_once(test, marker, addition, "reveal overflow test")
test_path.write_text(test, encoding="utf-8")
