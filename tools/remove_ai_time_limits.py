from pathlib import Path
import re

# Progressive blueprint stages: no coroutine deadline.
p = Path('app/src/main/java/com/xiguli/langhuan/ui/ProgressiveFoundationEngine.kt')
s = p.read_text()
s = s.replace('import kotlinx.coroutines.TimeoutCancellationException\n', '')
s = s.replace('import kotlinx.coroutines.withTimeout\n', '')
old = '''    /** 单个阶段最多等待 75 秒，避免 UI 永久卡在 2/3。 */
    private suspend fun requestOptional(stage: String, prompt: PromptBundle): GeneratedChapter? = try {
        withTimeout(STAGE_TIMEOUT_MS) { gateway.generate(prompt) }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
'''
new = '''    /**
     * 不设置任何 App 侧生成时限。不同模型/中转站的思考耗时差异很大，
     * 只允许用户主动取消，琅嬛不再因为固定秒数擅自终止正常请求。
     */
    private suspend fun requestOptional(stage: String, prompt: PromptBundle): GeneratedChapter? = try {
        gateway.generate(prompt)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
'''
assert old in s, 'progressive timeout block not found'
s = s.replace(old, new, 1)
s = s.replace('        const val STAGE_TIMEOUT_MS = 75_000L\n', '')
p.write_text(s)

# Proposal consolidation: no 75 second deadline.
p = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
s = p.read_text()
old = '''                val refreshed = runCatching {
          kotlinx.coroutines.withTimeout(75_000L) {
              ProposalConsolidator(gateway).consolidate(
                  current = baseline,
                  messages = before.messages,
              )
          }
      }.getOrElse { baseline }
'''
new = '''                val refreshed = runCatching {
                    ProposalConsolidator(gateway).consolidate(
                        current = baseline,
                        messages = before.messages,
                    )
                }.getOrElse { baseline }
'''
assert old in s, 'proposal timeout block not found'
s = s.replace(old, new, 1)
old_error = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val timeout = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return if (timeout) {
        "$fallback：当前阶段请求超时。已经成功完成的蓝图阶段会保留为断点；直接重试会从下一阶段继续，不需要重新发送整批网页资料，也不会重复生成已完成阶段。"
    } else {
        message.ifBlank { fallback }
    }
}
'''
new_error = '''private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val timeout = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return if (timeout) {
        "$fallback：AI 服务或中转站主动返回了超时/断开。琅嬛本身没有设置生成倒计时，也没有因为等待时间过长主动终止请求。"
    } else {
        message.ifBlank { fallback }
    }
}
'''
assert old_error in s, 'friendly timeout message block not found'
s = s.replace(old_error, new_error, 1)
p.write_text(s)

# HTTP normal + streaming calls: Java timeout=0 means infinite.
p = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
s = p.read_text()
assert s.count('connection.connectTimeout = 25_000') == 2, 'unexpected connect timeout count'
assert s.count('connection.readTimeout = 180_000') == 2, 'unexpected read timeout count'
s = s.replace('connection.connectTimeout = 25_000', 'connection.connectTimeout = 0')
s = s.replace('connection.readTimeout = 180_000', 'connection.readTimeout = 0')
p.write_text(s)

# Version bump.
p = Path('app/build.gradle.kts')
s = p.read_text()
s = re.sub(r'versionCode = \d+', 'versionCode = 51', s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "0.25.3-alpha01"', s, count=1)
p.write_text(s)

print('removed all app-side AI time limits')
