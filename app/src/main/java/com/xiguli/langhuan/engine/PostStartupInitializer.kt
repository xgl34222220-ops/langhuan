package com.xiguli.langhuan.engine

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Starts non-critical data preparation only after the database gate and first UI have succeeded.
 * Nothing in this initializer is allowed to participate in cold-start success.
 */
object PostStartupInitializer {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            // Give first composition a chance to settle before touching packaged assets/indexes.
            delay(1_200)
            runCatching { BuiltInReferenceLibraryInstaller.install(app) }
            runCatching { OriginalCanonIndexCoordinator.start(app) }
        }
    }
}
