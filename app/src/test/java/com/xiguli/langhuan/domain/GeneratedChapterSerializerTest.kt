package com.xiguli.langhuan.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedChapterSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsKeyedArraysFromRelay() {
        val chapter = json.decodeFromString<GeneratedChapter>(
            """
            {
              "title":"蓝图",
              "stateChanges":{
                "BIBLE:WORLD":[{"name":"梦境夹层","description":"现实与梦境之间存在可观测边界","value":"梦域"}],
                "CHAR":[{"name":"周衍","description":"谨慎、执着","value":"找回失踪搭档"}]
              }
            }
            """.trimIndent()
        )

        assertEquals("BIBLE:WORLD", chapter.stateChanges[0].subject)
        assertEquals("梦境夹层", chapter.stateChanges[0].field)
        assertEquals("CHAR", chapter.stateChanges[1].subject)
    }

    @Test
    fun readsPipeSeparatedRelayItems() {
        val chapter = json.decodeFromString<GeneratedChapter>(
            """
            {
              "title":"蓝图",
              "stateChanges":[
                "BIBLE:WORLD|梦境夹层|现实与梦境之间存在边界|梦域|可被监控捕捉",
                "VOLUME:1|入梦|追查失踪者|梦境规则持续收紧|主角主动进入下一层"
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("BIBLE:WORLD", "VOLUME:1"), chapter.stateChanges.map { it.subject })
        assertEquals("梦境夹层", chapter.stateChanges.first().field)
    }
}
