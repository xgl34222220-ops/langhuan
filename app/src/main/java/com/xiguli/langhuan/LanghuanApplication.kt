package com.xiguli.langhuan

import android.app.Application
import com.xiguli.langhuan.engine.ChapterRunRuntime
import com.xiguli.langhuan.engine.NovelWorkflowRuntimeObserver
import com.xiguli.langhuan.ui.BookSourceBrowserV38

class LanghuanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register only the application context. WebView itself is created lazily after a direct
        // book-source request is rejected, so launcher cold-start remains side-effect free.
        BookSourceBrowserV38.install(this)
    }

    // Keep process startup side-effect free. Reference-library installation and indexing are
    // deliberately not started from Application.onCreate(); the launcher must render first.
    // Workflow observation is attached only when ChapterRunRuntime itself is first requested.
    val chapterRunRuntime: ChapterRunRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChapterRunRuntime(this).also { runtime ->
            NovelWorkflowRuntimeObserver.attach(this, runtime)
        }
    }
}
