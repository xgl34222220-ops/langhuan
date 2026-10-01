package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSpeechResumeV47Test {
    @Test fun placeholderLayoutCannotStartSpeechBeforeOnlineBodyArrives() {
        assertEquals(ReaderSpeechResumeV47.WAIT, readerSpeechResumeV47(true, true, true, false, true))
    }
    @Test fun failurePausesCurrentChapterAndRetryWaitsThenResumes() {
        assertEquals(ReaderSpeechResumeV47.PAUSE_FOR_ERROR, readerSpeechResumeV47(true, true, true, true, true))
        assertEquals(ReaderSpeechResumeV47.WAIT, readerSpeechResumeV47(true, true, true, false, true))
        assertEquals(ReaderSpeechResumeV47.WAIT, readerSpeechResumeV47(true, true, false, false, false))
        assertEquals(ReaderSpeechResumeV47.START, readerSpeechResumeV47(true, true, false, false, true))
    }
    @Test fun cancelledPlaybackNeverRestartsBecauseLayoutOrBodyArrives() {
        assertEquals(ReaderSpeechResumeV47.NONE, readerSpeechResumeV47(true, false, false, false, true))
        assertEquals(ReaderSpeechResumeV47.NONE, readerSpeechResumeV47(false, true, false, false, true))
    }
    @Test fun realLocalEmptyChapterRetainsNormalCompletionBehaviour() {
        assertEquals(ReaderSpeechResumeV47.START, readerSpeechResumeV47(true, true, false, false, true))
    }
}
