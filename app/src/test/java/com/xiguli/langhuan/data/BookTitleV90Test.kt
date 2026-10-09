package com.xiguli.langhuan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BookTitleV90Test {
    @Test fun titleIsTrimmedFoldedAndCapped() {
        assertEquals("惊惧盛宴", normalizeBookTitleV90("  惊惧盛宴 \n"))
        assertEquals("惊惧盛宴 薄情书生", normalizeBookTitleV90("惊惧盛宴\n\n\u3000薄情书生"))
        assertNull(normalizeBookTitleV90("   \u3000\n"))
        assertNull(normalizeBookTitleV90(""))
        val long = "长".repeat(200)
        assertEquals(BOOK_TITLE_MAX_V90, normalizeBookTitleV90(long)!!.length)
        // Supplementary characters are never split in half by the cap.
        val emoji = "😀".repeat(100)
        val capped = normalizeBookTitleV90(emoji)!!
        assertEquals(BOOK_TITLE_MAX_V90, capped.codePointCount(0, capped.length))
    }

    @Test fun renameChangesOnlyTheNovelTitle() {
        val json = """{"novel":{"id":"b1","title":"惊惧盛宴 (薄情书生) (Z-Library)","genre":"导入作品",""" +
            """"premise":"","theme":"","targetWords":0,"currentChapter":3,"futureField":"keep"},""" +
            """"activeOutline":[],"bible":[],"characters":[],"recentTimeline":[],""" +
            """"relevantForeshadowing":[],"recentSummaries":["x"],"unknownRoot":{"a":1}}"""
        val renamed = renameSnapshotJsonV90(json, "惊惧盛宴")
        val tree = Json.parseToJsonElement(renamed).jsonObject
        val novel = tree["novel"]!!.jsonObject
        assertEquals("惊惧盛宴", novel["title"]!!.jsonPrimitive.content)
        assertEquals("b1", novel["id"]!!.jsonPrimitive.content)
        assertEquals(3, novel["currentChapter"]!!.jsonPrimitive.int)
        assertEquals("keep", novel["futureField"]!!.jsonPrimitive.content)
        assertEquals(1, tree["unknownRoot"]!!.jsonObject["a"]!!.jsonPrimitive.int)
        assertTrue(tree.keys.containsAll(listOf("activeOutline", "bible", "recentSummaries")))
        // Quotes and other JSON-special characters in the new title are escaped correctly.
        val quoted = renameSnapshotJsonV90(json, "他说\"好\"\\")
        assertEquals("他说\"好\"\\", Json.parseToJsonElement(quoted).jsonObject["novel"]!!.jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test fun malformedSnapshotIsRejectedInsteadOfOverwritten() {
        try {
            renameSnapshotJsonV90("""{"bible":[]}""", "新书名")
            fail("snapshot without novel must not be rewritten")
        } catch (expected: IllegalStateException) {
        }
    }
}
