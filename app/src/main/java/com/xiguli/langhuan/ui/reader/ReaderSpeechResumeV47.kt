package com.xiguli.langhuan.ui

internal enum class ReaderSpeechResumeV47 { NONE, WAIT, PAUSE_FOR_ERROR, START }

/** An online placeholder is pending input, while an actual empty local chapter stays empty. */
internal fun readerSpeechResumeV47(
    advancing: Boolean, listening: Boolean, awaitingOnlineBody: Boolean, hasLoadError: Boolean, hasLayout: Boolean,
): ReaderSpeechResumeV47 = when {
    !advancing || !listening -> ReaderSpeechResumeV47.NONE
    awaitingOnlineBody && hasLoadError -> ReaderSpeechResumeV47.PAUSE_FOR_ERROR
    awaitingOnlineBody || !hasLayout -> ReaderSpeechResumeV47.WAIT
    else -> ReaderSpeechResumeV47.START
}
