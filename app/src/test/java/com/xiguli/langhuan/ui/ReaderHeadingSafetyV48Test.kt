package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderHeadingSafetyV48Test {
    @Test fun ordinarySentencePrefixIsKept() {
        assertEquals("雨还在下，街道上已经没有行人。", readerBodyWithoutDuplicateHeadingV13("雨", "雨还在下，街道上已经没有行人。"))
        assertEquals("回家的路很长，他走了一整夜。", readerBodyWithoutDuplicateHeadingV13("回家", "回家的路很长，他走了一整夜。"))
    }
    @Test fun onlyACompleteHeadingLineIsRemoved() {
        assertEquals("正文第一句。", readerBodyWithoutDuplicateHeadingV13("第一章  夜雨", "\uFEFF第一章\t夜雨\n\n正文第一句。"))
        assertEquals("正文。", readerBodyWithoutDuplicateHeadingV13("第一章 夜雨", "第一章\n夜雨\n正文。"))
        assertEquals("第一章 夜雨已经停了。", readerBodyWithoutDuplicateHeadingV13("第一章 夜雨", "第一章 夜雨已经停了。"))
    }
    @Test fun titleOnlyAndEnglishCaseStillWork() {
        assertEquals("", readerBodyWithoutDuplicateHeadingV13("序言", "序言"))
        assertEquals("Story.", readerBodyWithoutDuplicateHeadingV13("PROLOGUE", "Prologue\nStory."))
    }
    @Test fun oldPositiveAnchorPreservesTheSameCharacterAfterRestoringPrefix() {
        val body = "回家的路很长，他走了一整夜。"
        assertEquals(7, readerRestoreBodyOffsetV48("回家", body, 5, 0))
        assertEquals(5, readerRestoreBodyOffsetV48("回家", body, 5, 48))
        assertEquals(0, readerRestoreBodyOffsetV48("回家", body, 0, 0))
        assertEquals(5, readerRestoreBodyOffsetV48("回家", "回家\n" + body, 5, 0))
    }
    @Test fun unrelatedHeadingAndDeferredOnlineBodyKeepTheirAnchor() {
        assertEquals(5, readerRestoreBodyOffsetV48("夜雨", "灯光还在亮着。", 5, 0))
        assertEquals(500, readerRestoreBodyOffsetV48("夜雨", "", 500, 0))
    }
}
