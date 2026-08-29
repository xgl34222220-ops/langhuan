package com.xiguli.langhuan.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserWritingSkillManifestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `user skill manifest parses supported tasks`() {
        val manifest = json.decodeFromString(
            UserWritingSkillManifest.serializer(),
            """
            {
              "schemaVersion": 1,
              "id": "voice-test",
              "name": "声线测试",
              "supportedTasks": ["PROSE_AUTHOR", "EDITOR_REVIEW"],
              "defaultTasks": ["PROSE_AUTHOR"],
              "guidance": "保持人物声线差异。"
            }
            """.trimIndent(),
        )

        assertEquals("voice-test", manifest.id)
        assertEquals(listOf(AiTaskType.PROSE_AUTHOR, AiTaskType.EDITOR_REVIEW), manifest.supportedTasks)
        assertEquals(listOf(AiTaskType.PROSE_AUTHOR), manifest.defaultTasks)
        assertTrue(manifest.guidance.isNotBlank())
    }
}
