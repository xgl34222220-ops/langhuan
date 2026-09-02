package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingSkillUpdateClientTest {
    private val client = WritingSkillUpdateClient()

    @Test
    fun `same version and revision is up to date`() {
        val skill = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }

        val result = client.evaluate(
            skill,
            manifest(
                id = skill.id,
                version = skill.version,
                revision = skill.sourceRevision,
                supported = skill.supportedTasks,
            )
        )

        assertTrue(result is WritingSkillUpdateCheck.UpToDate)
    }

    @Test
    fun `version or revision change produces explicit candidate`() {
        val skill = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }

        val result = client.evaluate(
            skill,
            manifest(
                id = skill.id,
                version = "0.4.2-adapted",
                revision = "new-revision-1234567890",
                supported = skill.supportedTasks,
            )
        )

        assertTrue(result is WritingSkillUpdateCheck.Update)
        val candidate = (result as WritingSkillUpdateCheck.Update).candidate
        assertEquals("0.4.2-adapted", candidate.remoteVersion)
        assertTrue(candidate.changes.any { it.contains("版本") })
        assertTrue(candidate.changes.any { it.contains("revision") })
    }

    @Test
    fun `wrong remote id is rejected`() {
        val skill = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }

        val result = client.evaluate(
            skill,
            manifest(
                id = "other-skill",
                version = "9.0.0",
                revision = "other",
                supported = skill.supportedTasks,
            )
        )

        assertTrue(result is WritingSkillUpdateCheck.Error)
        assertTrue((result as WritingSkillUpdateCheck.Error).message.contains("id"))
    }

    @Test
    fun `builtin remote cannot expand execution task surface`() {
        val skill = WritingSkillCatalog.all.first { it.id == "avoid-ai-writing" }
        val expanded = skill.supportedTasks + AiTaskType.AUTONOMOUS_PLANNER

        val result = client.evaluate(
            skill,
            manifest(
                id = skill.id,
                version = "3.29.0-adapted",
                revision = "new",
                supported = expanded,
            )
        )

        assertTrue(result is WritingSkillUpdateCheck.Error)
        assertTrue((result as WritingSkillUpdateCheck.Error).message.contains("扩大"))
    }

    @Test
    fun `builtin catalog exposes HTTPS trusted update sources`() {
        WritingSkillCatalog.all.forEach { skill ->
            assertTrue(skill.updateUrl.startsWith("https://"))
            assertTrue(skill.updateUrl.contains("xgl34222220-ops/langhuan"))
        }
    }

    private fun manifest(
        id: String,
        version: String,
        revision: String,
        supported: Set<AiTaskType>,
    ): String = """
        {
          "schemaVersion": 2,
          "id": "$id",
          "name": "$id",
          "version": "$version",
          "sourceRevision": "$revision",
          "sourceUrl": "https://github.com/example/source",
          "updateUrl": "https://example.com/$id.json",
          "supportedTasks": [${supported.joinToString(",") { "\"${it.name}\"" }}],
          "defaultTasks": [${supported.joinToString(",") { "\"${it.name}\"" }}],
          "guidance": "只用于测试声明式更新。"
        }
    """.trimIndent()
}
