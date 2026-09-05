package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.creation.CreationChatAttachment
import com.xiguli.langhuan.ui.creation.attachmentContext
import com.xiguli.langhuan.ui.creation.attachmentPurpose
import com.xiguli.langhuan.ui.creation.defaultAttachmentInstruction
import com.xiguli.langhuan.ui.creation.extractDocxText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationAttachmentTest {
    @Test
    fun includesExtractedTextInConversationContext() {
        val context = attachmentContext(
            listOf(
                CreationChatAttachment(
                    id = "a",
                    fileName = "设定.md",
                    mimeType = "text/markdown",
                    extractedText = "主角不能通过死亡重置代价。",
                )
            )
        )

        assertTrue(context.contains("设定.md"))
        assertTrue(context.contains("主角不能通过死亡重置代价。"))
    }

    @Test
    fun extractsParagraphsAndEscapedCharactersFromDocx() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                "<w:document><w:body><w:p><w:r><w:t>规则&amp;代价</w:t></w:r></w:p><w:p><w:r><w:t>第二段</w:t></w:r></w:p></w:body></w:document>"
                    .toByteArray()
            )
            zip.closeEntry()
        }

        assertEquals("规则&代价\n第二段", extractDocxText(output.toByteArray()))
    }

    @Test
    fun recognizesStructuredNovelSettingAndBuildsAuditInstruction() {
        val attachment = CreationChatAttachment(
            id = "setting",
            fileName = "无人生还的梦-作品设定.md",
            mimeType = "text/markdown",
            extractedText = "## 故事梗概\n## 世界规则\n## 三方势力\n## 分卷大纲",
        )

        assertEquals("作品设定", attachmentPurpose(attachment))
        val instruction = defaultAttachmentInstruction(listOf(attachment))
        assertTrue(instruction.contains("规则闭环"))
        assertTrue(instruction.contains("待确认"))
    }
}
