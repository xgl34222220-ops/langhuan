package com.xiguli.langhuan

import android.app.Application
import com.xiguli.langhuan.engine.ChapterRunRuntime
import com.xiguli.langhuan.engine.NovelWorkflowRuntimeObserver
import com.xiguli.langhuan.ui.BookSourceBrowserV38

class LanghuanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Application.getProcessName().endsWith(":book_source_browser")) {
            android.webkit.WebView.setDataDirectorySuffix("book_source_v56")
            return
        }
        // Register only the application context. Browser requests opt in explicitly and use a
        // separate process/profile, leaving the reader's WebView and launcher untouched.
        BookSourceBrowserV38.install(this)
    }

    // Keep process startup side-effect free. Reference-library installation and indexing are
    // deliberately not started from Application.onCreate(); the launcher must render first.
    // Workflow observation is attached only when ChapterRunRuntime itself is first requested.
    private val chapterRunRuntimeLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChapterRunRuntime(this).also { runtime ->
            NovelWorkflowRuntimeObserver.attach(this, runtime)
        }
    }
    val chapterRunRuntime: ChapterRunRuntime by chapterRunRuntimeLazy

    /**
     * The runtime only if something already started it. Passive observers (the shelf's run
     * badge) use this so that merely rendering the shelf never creates the runtime.
     */
    val chapterRunRuntimeIfStarted: ChapterRunRuntime?
        get() = if (chapterRunRuntimeLazy.isInitialized()) chapterRunRuntimeLazy.value else null
}
