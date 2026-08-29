package com.xiguli.langhuan

import android.app.Application
import com.xiguli.langhuan.engine.BuiltInReferenceLibraryInstaller
import com.xiguli.langhuan.engine.ChapterRunRuntime

class LanghuanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BuiltInReferenceLibraryInstaller.install(this)
    }

    val chapterRunRuntime: ChapterRunRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChapterRunRuntime(this)
    }
}
