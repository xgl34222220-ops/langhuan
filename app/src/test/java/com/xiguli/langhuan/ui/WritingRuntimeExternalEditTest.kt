package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.*
import com.xiguli.langhuan.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sun.misc.Unsafe
import java.util.ArrayDeque
import org.junit.Test

private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
private fun field(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
private fun snapshot(draft: ChapterDraft) = StorySnapshot(Novel(draft.novelId, "合成创作", "测试", "设定", "目标", 10000, currentWords=draft.content.length), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
private fun exercise(label: String, active: Boolean = false, otherBook: Boolean = false, wrongTarget: Boolean = false, failedRun: Boolean = false) {
    val old = ChapterDraft("book:1", "book", 1, "AI 初稿", "目标", emptyList(), content="AI 旧正文", version=2)
    val fresh = old.copy(title="手改新标题", content="用户已在实际编辑器保存的新正文", version=3)
    val vm = unsafe.allocateInstance(WritingFlowViewModel::class.java) as WritingFlowViewModel
    val states = MutableStateFlow(WritingFlowUiState(novelId="book", snapshot=snapshot(old), draft=old, workingScenes=old.scenePlan, chapterCommitted=true))
    val before = states.value
    field(vm, "_state", states)
    field(vm, "requests", StoryRequestScope::class.java.getDeclaredConstructor().newInstance())
    val runtime = unsafe.allocateInstance(ChapterRunRuntime::class.java) as ChapterRunRuntime
    val run = ChapterRunRuntimeState(active=active, taskKind=ChapterRuntimeTaskKind.COMMIT,
        novelId=if(otherBook) "other" else "book", chapterNumber=1, snapshot=snapshot(old), draft=old,
        message=if(failedRun) null else "正文与版本已保存", error=if(failedRun) "旧任务错误" else null)
    val live = MutableStateFlow(run)
    field(runtime,"_state",live); field(runtime,"state",live.asStateFlow()); field(runtime,"lock",Any()); field(runtime,"queue",ArrayDeque<Any>())
    field(vm,"runtime",runtime)
    // Execute actual production invalidation, then inject the durable read used by load's success path.
    vm.invalidateAfterExternalEdit(if(wrongTarget) "different" else "book")
    if((active && !otherBook) || wrongTarget) {
        check(states.value === before && live.value === run) { "Unrelated/active state changed" }
    } else {
        check(!states.value.ready) { "Actual invalidation did not release the ready cache for a durable reload" }
        states.value = WritingFlowUiState(novelId="book",snapshot=snapshot(fresh),draft=fresh,workingScenes=fresh.scenePlan,chapterCommitted=true)
        WritingFlowViewModel::class.java.getDeclaredMethod("syncRuntimeState",ChapterRunRuntimeState::class.java).apply { isAccessible=true }.invoke(vm,live.value)
        check(states.value.draft==fresh && states.value.snapshot==snapshot(fresh)) { "Fresh durable edit replaced: title=${states.value.draft?.title}, body=${states.value.draft?.content}, version=${states.value.draft?.version}" }
        if(otherBook) check(live.value===run) { "Another book's runtime was cleared" }
    }
    println("PASS $label")
}
/** Actual production method execution. Android construction and the durable-load result are
 * replaced by controlled seams; these checks are not Android lifecycle or Room tests.
 * The real reader-to-writing-to-editor device test covers the UI and durable storage path.
 */
class WritingRuntimeExternalEditTest {
    @Test fun completedAiRunCannotRestoreOldBodyAfterManualEdit() = exercise("completed runtime")
    @Test fun failedTerminalRunCannotRestoreOldDraftOrErrorAfterManualEdit() = exercise("failed runtime", failedRun=true)
    @Test fun activeRunForThisBookRemainsIntact() = exercise("active runtime", active=true)
    @Test fun anotherBooksTerminalStateRemainsIntact() = exercise("other book", otherBook=true)
    @Test fun wrongBookInvalidationDoesNothing() = exercise("wrong target", wrongTarget=true)
    @Test fun anotherActiveBookDoesNotBlockThisBooksDurableReload() = exercise("other active book", otherBook=true, active=true)
}
