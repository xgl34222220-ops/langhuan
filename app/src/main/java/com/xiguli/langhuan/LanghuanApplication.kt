package com.xiguli.langhuan

import android.app.Application
import com.xiguli.langhuan.engine.ChapterRunRuntime

class LanghuanApplication : Application() {
    // Keep process startup side-effect free. Reference-library installation and indexing are
    // deliberately not started from Application.onCreate(); the launcher must render first.
    val chapterRunRuntime: ChapterRunRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChapterRunRuntime(this)
    }
}
