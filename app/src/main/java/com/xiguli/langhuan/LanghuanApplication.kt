package com.xiguli.langhuan

import android.app.Application
import com.xiguli.langhuan.engine.ChapterRunRuntime

class LanghuanApplication : Application() {
    val chapterRunRuntime: ChapterRunRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChapterRunRuntime(this)
    }
}
