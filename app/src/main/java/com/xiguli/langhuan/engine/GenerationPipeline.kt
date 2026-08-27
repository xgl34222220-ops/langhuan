package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StateChange
import kotlinx.coroutines.delay

interface AiGateway {
    suspend fun generate(prompt: PromptBundle): GeneratedChapter
}

class GenerationPipeline(
    private val aiGateway: AiGateway,
    private val promptAssembler: PromptAssembler = PromptAssembler(),
    private val consistencyGate: ConsistencyGate = ConsistencyGate(),
) {
    suspend fun generate(request: GenerationRequest): GenerationResult {
        val prompt = promptAssembler.build(request)
        val chapter = aiGateway.generate(prompt)
        return GenerationResult(
            chapter = chapter,
            issues = consistencyGate.inspect(request, chapter),
        )
    }
}

/**
 * 首个可运行原型使用的离线网关。接入真实模型时替换为 OpenAICompatibleGateway，
 * 领域层和一致性门禁不需要改动。
 */
class DemoAiGateway : AiGateway {
    override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
        delay(900)
        return GeneratedChapter(
            title = "雾港来信",
            content = """
                港城的雾在子夜后压得更低。沈砚把那封没有署名的信平放在灯下，纸角残留的银色盐晶与旧案卷上的样本完全一致。

                他没有立刻去码头，而是先敲响了顾遥的门。两人核对城门记录，发现失踪商队入城的日期恰好被人改过一次。顾遥坚持从档案馆追查，沈砚却注意到窗外那道停留过久的影子。

                他们故意熄灯，从后门离开。追踪者把二人引向废弃钟楼，也让沈砚确认：寄信人并不是求救，而是在测试他们是否已经发现时间记录的矛盾。
            """.trimIndent(),
            summary = "沈砚与顾遥通过匿名信确认失踪商队记录被篡改，并在废弃钟楼发现寄信人正在试探他们。",
            stateChanges = listOf(
                StateChange("沈砚", "knownSecrets", "不知道记录被改", "确认商队记录被篡改", "核对城门记录"),
            ),
            touchedForeshadowingIds = listOf("f1"),
        )
    }
}

