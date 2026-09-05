package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.story.parseStoryCanonRoleCandidatesV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRoleBindingV1Test {

    @Test
    fun `anchor chapter drops future character facts completely`() {
        val raw = """
            {
              "novelId":"book-1",
              "digests":[
                {
                  "chapterNumber":2,
                  "entities":[{"type":"CHARACTER","name":"林默","aliases":["小林"],"description":"仍然不知道门后的真相"}],
                  "knowledge":[{"character":"林默","fact":"钥匙来自旧仓库","evidence":"亲眼找到"}]
                },
                {
                  "chapterNumber":4,
                  "entities":[{"type":"CHARACTER","name":"林默","description":"已经确认幕后人身份"}],
                  "knowledge":[{"character":"林默","fact":"幕后人就是周启","evidence":"第4章揭晓"}]
                }
              ]
            }
        """.trimIndent()

        val roles = parseStoryCanonRoleCandidatesV1(raw, anchorChapter = 3)
        val role = roles.single { it.name == "林默" }

        assertTrue(role.knownFacts.any { it.fact == "钥匙来自旧仓库" })
        assertFalse(role.knownFacts.any { it.fact.contains("周启") })
        assertFalse(role.description.contains("幕后人身份"))
        assertEquals(2, role.lastChapter)
    }

    @Test
    fun `alias knowledge is attached to canonical character`() {
        val raw = """
            {
              "novelId":"book-2",
              "digests":[
                {
                  "chapterNumber":1,
                  "entities":[{"type":"CHARACTER","name":"沈砚","aliases":["阿砚","沈医生"],"description":"急诊医生"}],
                  "knowledge":[{"character":"沈医生","fact":"病历被人调换过","evidence":"核对编号"}]
                }
              ]
            }
        """.trimIndent()

        val role = parseStoryCanonRoleCandidatesV1(raw, 1).single()

        assertEquals("沈砚", role.name)
        assertTrue("沈医生" in role.aliases)
        assertEquals(listOf("病历被人调换过"), role.knownFacts.map { it.fact })
    }

    @Test
    fun `future relation never enters role relationship list`() {
        val raw = """
            {
              "novelId":"book-3",
              "digests":[
                {
                  "chapterNumber":3,
                  "entities":[
                    {"type":"CHARACTER","name":"顾宁","description":"调查员"},
                    {"type":"CHARACTER","name":"程野","description":"记者"}
                  ],
                  "relations":[{"from":"顾宁","to":"程野","label":"信任","value":"谨慎合作","evidence":"交换线索"}]
                },
                {
                  "chapterNumber":8,
                  "relations":[{"from":"顾宁","to":"程野","label":"身份","value":"确认其为内鬼","evidence":"第8章揭晓"}]
                }
              ]
            }
        """.trimIndent()

        val role = parseStoryCanonRoleCandidatesV1(raw, 5).first { it.name == "顾宁" }

        assertTrue(role.relationships.any { it.label == "信任" && it.value == "谨慎合作" })
        assertFalse(role.relationships.any { it.value.contains("内鬼") })
        assertTrue(role.relationships.all { it.chapter <= 5 })
    }
}
