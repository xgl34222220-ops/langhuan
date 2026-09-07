package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernCharacterImportV2Test {
    @Test
    fun fallbackExtractsMultipleNamedSpeakers() {
        val source = """
            小明：你好，今天还去旧书店吗？
            小红: 去，下午三点老地方。
            小明：那我带伞，你别忘了钥匙。
            小红：知道啦，你每次都要提醒一遍。
            系统：你撤回了一条消息
        """.trimIndent()

        val cards = fallbackExtractTavernCharactersV2(source, "chat.txt")
        assertEquals(setOf("小明", "小红"), cards.map { it.name }.toSet())
        assertTrue(cards.all { it.sourceTitle == "chat.txt" })
        assertTrue(cards.all { it.dialogueExamples.size == 2 })
    }

    @Test
    fun aiBlocksPreserveSupportedCharacterFields() {
        val content = """
            <CHARACTER>
            name=林夏
            aliases=小夏|林同学
            appearance=短发
            identity=记者
            personality=谨慎|敏锐
            speechStyle=句子简短，常先确认事实
            catchphrases=先等等|有证据吗
            relationshipToUser=旧友
            relationships=陈默：同事
            history=参与过南城调查
            likes=黑咖啡
            dislikes=含糊其辞
            boundaries=不谈家人
            worldFacts=南城曾停电三天
            currentMemory=用户答应今晚回电话
            dialogueExamples=先等等，这件事有证据吗？|你把时间线再说一遍。
            </CHARACTER>
            <CHARACTER>
            name=陈默
            aliases=
            appearance=
            identity=摄影师
            personality=沉默寡言
            speechStyle=
            catchphrases=
            relationshipToUser=
            relationships=林夏：同事
            history=
            likes=
            dislikes=
            boundaries=
            worldFacts=
            currentMemory=
            dialogueExamples=我拍到了。
            </CHARACTER>
        """.trimIndent()

        val cards = parseAiCharacterBlocksV2(content, "import.json")
        assertEquals(2, cards.size)
        val linXia = cards.first { it.name == "林夏" }
        assertEquals(listOf("小夏", "林同学"), linXia.aliases)
        assertEquals("记者", linXia.identity)
        assertEquals("旧友", linXia.relationshipToUser)
        assertEquals("import.json", linXia.sourceTitle)
        assertEquals(2, linXia.dialogueExamples.size)
    }

    @Test
    fun htmlChatIsNormalizedIntoReadableLines() {
        val normalized = normalizeImportedChatV2(
            "<div>张三：你好</div><div>李四：在吗</div><script>bad()</script>"
        )
        assertTrue(normalized.contains("张三：你好"))
        assertTrue(normalized.contains("李四：在吗"))
        assertTrue(normalized.contains("\n"))
        assertFalse(normalized.contains("<div>"))
        assertFalse(normalized.contains("bad()"))
    }
}
