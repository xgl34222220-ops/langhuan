package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter

/**
 * Stable provider boundary shared by every AI workflow.
 * Keep this contract independent from GenerationPipeline so pipeline refactors cannot remove the
 * application-wide gateway abstraction again.
 */
interface AiGateway {
    suspend fun generate(prompt: PromptBundle): GeneratedChapter

    /** Plain text path for normal conversation and novel prose. */
    suspend fun generateText(prompt: PromptBundle): String = generate(prompt).content

    /**
     * Raw text streaming contract. onDelta receives the cumulative visible response so UI can replace
     * its preview without reconstructing provider-specific token deltas. Providers may fall back to a
     * single final callback only when streaming is unavailable before any bytes are emitted.
     */
    suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val text = generateText(prompt)
        onDelta(text)
        return text
    }

    /** Structured streaming remains available for legacy structured generation paths. */
    suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
        val chapter = generate(prompt)
        onDelta(chapter.content)
        return chapter
    }
}
