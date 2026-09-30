package com.xiguli.langhuan.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonRepairV34Test {
    private fun parses(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun stripsProseFencesNewlinesAndTrailingCommas() {
        val out = repairModelJsonV34("好的：\n```json\n{\"title\":\"A\",\"content\":\"第一行\n第二行\",}\n```")
        assertEquals("第一行\n第二行", parses(out)["content"].toString().trim('"').replace("\\n", "\n"))
    }

    @Test
    fun truncatedValueIsClosed() {
        val out = repairModelJsonV34("{\"title\":\"A\",\"summary\":\"写到一半")
        assertTrue(parses(out).containsKey("summary"))
    }

    @Test
    fun truncatedKeyIsDropped() {
        val out = repairModelJsonV34("{\"title\":\"A\",\"stateChanges\":[{\"subject\":\"x\",\"after\":\"y\"},{\"subj")
        assertTrue(parses(out).containsKey("stateChanges"))
    }

    @Test
    fun danglingKeyWithoutValueIsDropped() {
        assertEquals(setOf("title"), parses(repairModelJsonV34("{\"title\":\"A\",\"summary\":")).keys)
        assertEquals(setOf("title"), parses(repairModelJsonV34("{\"title\":\"A\",\"summary\"")).keys)
    }

    @Test
    fun escapedQuotesSurvive() {
        val out = repairModelJsonV34("{\"a\":\"q\\\"uote\",\"b\":\"c\\")
        assertTrue(parses(out).containsKey("a"))
    }
}
