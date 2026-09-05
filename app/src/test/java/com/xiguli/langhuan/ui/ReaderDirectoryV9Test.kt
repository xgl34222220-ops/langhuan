package com.xiguli.langhuan.ui
import com.xiguli.langhuan.ui.reader.buildDirectoryGroupsV9
import com.xiguli.langhuan.ui.reader.directoryVolumeLabelV9

import com.xiguli.langhuan.domain.ChapterDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderDirectoryV9Test {
    private fun chapter(number: Int, title: String) = ChapterDraft(
        id = "c$number",
        novelId = "n1",
        chapterNumber = number,
        title = title,
        objective = "",
        scenePlan = emptyList(),
        content = "正文",
    )

    @Test
    fun volumePrefixCreatesDirectoryGroups() {
        val groups = buildDirectoryGroupsV9(
            listOf(
                chapter(1, "第一卷 雨夜"),
                chapter(2, "第一卷 旧宅"),
                chapter(3, "第二卷 来客"),
            )
        )

        assertEquals(listOf("第一卷", "第二卷"), groups.map { it.title })
        assertEquals(listOf(1, 2), groups[0].chapters.map { it.chapterNumber })
        assertEquals(listOf(3), groups[1].chapters.map { it.chapterNumber })
    }

    @Test
    fun ordinaryChaptersStayInBodyGroup() {
        val groups = buildDirectoryGroupsV9(listOf(chapter(1, "楔子"), chapter(2, "第一章 开端")))
        assertEquals(1, groups.size)
        assertEquals("正文", groups.single().title)
    }

    @Test
    fun directorySearchKeepsMatchingChapterOnly() {
        val groups = buildDirectoryGroupsV9(
            listOf(chapter(1, "第一卷 雨夜"), chapter(2, "第一卷 旧宅"), chapter(3, "第二卷 来客")),
            query = "旧宅",
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(2), groups.single().chapters.map { it.chapterNumber })
    }

    @Test
    fun volumeLabelDoesNotInventGroupForNormalTitle() {
        assertEquals("第三部", directoryVolumeLabelV9("第三部 远行"))
        assertEquals("卷二", directoryVolumeLabelV9("卷二 风起"))
        assertNull(directoryVolumeLabelV9("第十二章 风起"))
    }
}
