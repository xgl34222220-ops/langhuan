from pathlib import Path

ROOT = Path('.')

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s): (ROOT/p).write_text(s,encoding='utf-8')

# Only successful/skipped stages are safe to skip on resume.
p='app/src/main/java/com/xiguli/langhuan/engine/ChapterRunCoordinator.kt'
s=read(p)
s=s.replace('        val terminal = event.status != RunStatus.RUNNING\n', '        val terminal = event.status in setOf(RunStatus.SUCCESS, RunStatus.SKIPPED)\n', 1)
write(p,s)

# Restore durable run when switching books too.
p='app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt'
s=read(p)
start=s.index('    fun selectStory(id: String) {')
end=s.index('    fun createStory(', start)
block=s[start:end]
if 'restoreDurableRun(loaded.snapshot, loaded.draft)' not in block:
    block=block.replace('            refreshWorkspace()\n', '            restoreDurableRun(loaded.snapshot, loaded.draft)\n            refreshWorkspace()\n', 1)
s=s[:start]+block+s[end:]
write(p,s)

# Existing coordinator test implements the pre-0.26.6 store contract.
p='app/src/test/java/com/xiguli/langhuan/engine/ChapterRunCoordinatorTest.kt'
s=read(p)
s=s.replace(
'''        override suspend fun commitGenerated(
            snapshot: StorySnapshot,
            draft: ChapterDraft,
            generated: GeneratedChapter,
        ): PersistedStory {
            commitCalls++
''',
'''        override suspend fun commitGenerated(
            snapshot: StorySnapshot,
            draft: ChapterDraft,
            generated: GeneratedChapter,
            runId: String,
        ): PersistedStory {
            commitCalls++
''',1)
s=s.replace(
'''                summary = generated.summary,
                version = draft.version + 1,
            )
''',
'''                summary = generated.summary,
                version = draft.version + 1,
                lastCommittedRunId = runId,
            )
''',1)
s=s.replace(
'        override suspend fun chapterDrafts(novelId: String): List<ChapterDraft> = listOf(draft)\n',
'        override suspend fun chapterDrafts(novelId: String): List<ChapterDraft> = listOf(draft)\n        override suspend fun loadStory(novelId: String): PersistedStory = PersistedStory(snapshot, draft)\n',1)
write(p,s)

# Make fresh vs restored metadata messages accurate (no behavioral effect, clearer Run Inspector).
p='app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt'
s=read(p)
s=s.replace(
'''        val metadata: GeneratedChapter
        val metadataSucceeded: Boolean
        if (checkpoint.metadataAttempted && checkpoint.metadata != null) {
''',
'''        val metadata: GeneratedChapter
        val metadataSucceeded: Boolean
        val metadataRestored = checkpoint.metadataAttempted && checkpoint.metadata != null
        if (metadataRestored) {
''',1)
s=s.replace(
'            if (checkpoint.metadataAttempted) "元数据从断点恢复；未重复请求模型" else if (metadataSucceeded) "结构化提取完成" else "元数据提取失败，使用正文摘要兜底；不会凭空写入 Canon",\n',
'            if (metadataRestored) "元数据从断点恢复；未重复请求模型" else if (metadataSucceeded) "结构化提取完成" else "元数据提取失败，使用正文摘要兜底；不会凭空写入 Canon",\n',1)
write(p,s)

print('resumable run hardening applied')
