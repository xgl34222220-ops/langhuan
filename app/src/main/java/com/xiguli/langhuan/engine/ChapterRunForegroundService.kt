package com.xiguli.langhuan.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.xiguli.langhuan.MainActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visible keep-alive for user-started long chapter runs.
 *
 * The actual generation still owns its coroutine in the writing flow ViewModel; this service keeps
 * the app process in foreground priority while the activity is merely backgrounded. Durable chapter
 * checkpoints remain the crash/process-death recovery source of truth. There is deliberately no
 * timer or deadline here.
 */
data class ChapterRunKeepAliveState(
    val active: Boolean = false,
    val novelId: String = "",
    val chapterNumber: Int = 0,
    val title: String = "",
    val detail: String = "",
    val canStop: Boolean = false,
)

object ChapterRunKeepAliveRegistry {
    private val _state = MutableStateFlow(ChapterRunKeepAliveState())
    val state: StateFlow<ChapterRunKeepAliveState> = _state.asStateFlow()

    internal fun update(state: ChapterRunKeepAliveState) {
        _state.value = state
    }

    internal fun clear() {
        _state.value = ChapterRunKeepAliveState()
    }

    fun isActive(novelId: String, chapterNumber: Int): Boolean {
        val current = _state.value
        return current.active && current.novelId == novelId && current.chapterNumber == chapterNumber
    }
}

object ChapterRunStopSignals {
    private val _requests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    internal fun requestStop() {
        _requests.tryEmit(Unit)
    }
}

class ChapterRunForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideNow()
            ACTION_STOP -> {
                ChapterRunStopSignals.requestStop()
                val current = ChapterRunKeepAliveRegistry.state.value
                if (current.active) {
                    showNotification(current.copy(detail = "正在停止当前模型请求…", canStop = false))
                } else {
                    hideNow()
                }
            }
            else -> showFrom(intent)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The generation coroutine belongs to the Activity ViewModel. If the user explicitly removes
        // the whole task, that ViewModel will be cleared; do not leave a stale foreground notification.
        ChapterRunStopSignals.requestStop()
        hideNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ChapterRunKeepAliveRegistry.clear()
        super.onDestroy()
    }

    private fun showFrom(intent: Intent?) {
        val state = ChapterRunKeepAliveState(
            active = true,
            novelId = intent?.getStringExtra(EXTRA_NOVEL_ID).orEmpty(),
            chapterNumber = intent?.getIntExtra(EXTRA_CHAPTER_NUMBER, 0) ?: 0,
            title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "琅嬛正在执行章节任务" },
            detail = intent?.getStringExtra(EXTRA_DETAIL).orEmpty().ifBlank { "长任务正在后台执行；没有 App 侧超时" },
            canStop = intent?.getBooleanExtra(EXTRA_CAN_STOP, false) ?: false,
        )
        showNotification(state)
    }

    private fun showNotification(state: ChapterRunKeepAliveState) {
        ensureChannel()
        ChapterRunKeepAliveRegistry.update(state)
        val manager = getSystemService(NotificationManager::class.java)
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ChapterRunKeepAliveState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            2001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(state.title)
            .setContentText(state.detail)
            .setStyle(Notification.BigTextStyle().bigText(state.detail))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (state.canStop) {
            val stopIntent = Intent(this, ChapterRunForegroundService::class.java).apply { action = ACTION_STOP }
            val stopPending = PendingIntent.getService(
                this,
                2002,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "停止生成",
                    stopPending,
                ).build()
            )
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "章节长任务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示正在生成、保存和复盘的章节任务"
                setShowBadge(false)
            }
        )
    }

    private fun hideNow() {
        ChapterRunKeepAliveRegistry.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "chapter_run_foreground"
        private const val NOTIFICATION_ID = 4027
        private const val ACTION_SHOW = "com.xiguli.langhuan.chapter_run.SHOW"
        private const val ACTION_HIDE = "com.xiguli.langhuan.chapter_run.HIDE"
        private const val ACTION_STOP = "com.xiguli.langhuan.chapter_run.STOP"
        private const val EXTRA_NOVEL_ID = "novel_id"
        private const val EXTRA_CHAPTER_NUMBER = "chapter_number"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_CAN_STOP = "can_stop"

        fun show(
            context: Context,
            novelId: String,
            chapterNumber: Int,
            title: String,
            detail: String,
            canStop: Boolean,
        ) {
            val intent = Intent(context, ChapterRunForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_NOVEL_ID, novelId)
                putExtra(EXTRA_CHAPTER_NUMBER, chapterNumber)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_CAN_STOP, canStop)
            }
            if (ChapterRunKeepAliveRegistry.state.value.active) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun hide(context: Context) {
            if (!ChapterRunKeepAliveRegistry.state.value.active) return
            context.startService(
                Intent(context, ChapterRunForegroundService::class.java).apply { action = ACTION_HIDE }
            )
        }
    }
}
