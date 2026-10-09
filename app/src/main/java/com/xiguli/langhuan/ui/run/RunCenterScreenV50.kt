package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.engine.ChapterRunRuntimeState
import com.xiguli.langhuan.engine.ChapterRuntimeTaskKind
import com.xiguli.langhuan.engine.DurableRunPhase
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.design.LanghuanUiTokens
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay


/**
 * 运行中心 V50。
 *
 * UI 只消费上层已有状态：
 *
 * - RunCenterUiState
 * - RunCenterItemUi
 * - ChapterRunRuntimeState
 * - RunEvent
 * - DurableRunPhase
 *
 * 不直接访问：
 *
 * - PersistentChapterRunCheckpointStore
 * - StoryProjectManager
 * - ChapterRunRuntime
 * - SharedPreferences
 *
 * 取消、重试、继续、删除断点全部通过回调交回上层。
 */
@Composable
internal fun RunCenterScreenV50(
    state: RunCenterUiState,
    runtime: ChapterRunRuntimeState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTask: (RunCenterItemUi) -> Unit,
    onRetryTask: (RunCenterItemUi) -> Unit,
    onCancelTask: (RunCenterItemUi) -> Unit,
    onCancelCurrent: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var pendingCancel by
        remember {
            mutableStateOf<
                RunCenterItemUi?
                >(
                null,
            )
        }

    var selectedLogKey by
        rememberSaveable {
            mutableStateOf<
                String?
                >(
                null,
            )
        }

    var now by
        remember {
            mutableLongStateOf(
                System.currentTimeMillis(),
            )
        }


    LaunchedEffect(
        runtime.active,
    ) {
        while (
            runtime.active
        ) {
            delay(
                1_000,
            )

            now =
                System.currentTimeMillis()
        }
    }


    val liveItem =
        state.items.firstOrNull {
            runtime.matches(
                it.novelId,
                it.chapterNumber,
            )
        }


    val selectedItem =
        state.items.firstOrNull {
            runCenterKeyV50(
                it,
            ) ==
                selectedLogKey
        }


    val logEvents =
        when {
            selectedLogKey ==
                LIVE_LOG_KEY_V50 -> {
                runtime.events
            }

            selectedItem !=
                null -> {
                selectedItem.events
            }

            runtime.events.isNotEmpty() -> {
                runtime.events
            }

            else -> {
                state.items
                    .firstOrNull()
                    ?.events
                    .orEmpty()
            }
        }


    val logTitle =
        when {
            selectedLogKey ==
                LIVE_LOG_KEY_V50 -> {
                runtime.draft
                    ?.title
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "当前运行"
            }

            selectedItem !=
                null -> {
                "第${selectedItem.chapterNumber}章 · ${selectedItem.chapterTitle}"
            }

            runtime.events.isNotEmpty() -> {
                runtime.draft
                    ?.title
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "当前运行"
            }

            state.items.isNotEmpty() -> {
                val first =
                    state.items.first()

                "第${first.chapterNumber}章 · ${first.chapterTitle}"
            }

            else -> {
                "暂无任务"
            }
        }


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding =
            PaddingValues(
                start =
                    t.space4,
                end =
                    t.space4,
                top =
                    t.space3,
                bottom =
                    t.space6,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                t.space5,
            ),
    ) {

        /* ------------------------------------------------------------------ */
        /* Header                                                             */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "run-center-header",
        ) {
            RunCenterHeaderV50(
                runtime =
                    runtime,
                onBack =
                    onBack,
            )
        }


        /* ------------------------------------------------------------------ */
        /* Runtime overview                                                   */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "run-center-overview",
        ) {
            Column {
                RunCenterSectionTitleV50(
                    title =
                        "运行状态",
                )

                Spacer(
                    Modifier.height(
                        t.space2,
                    ),
                )

                RunCenterOverviewV50(
                    runtime =
                        runtime,
                    pendingCount =
                        state.items.count {
                            !runtime.matches(
                                it.novelId,
                                it.chapterNumber,
                            )
                        },
                )
            }
        }


        /* ------------------------------------------------------------------ */
        /* Current run                                                        */
        /* ------------------------------------------------------------------ */

        if (
            runtime.active ||
            runtime.novelId.isNotBlank()
        ) {
            item(
                key =
                    "run-center-current",
            ) {
                Column {
                    RunCenterSectionTitleV50(
                        title =
                            "当前任务",
                    )

                    Spacer(
                        Modifier.height(
                            t.space2,
                        ),
                    )

                    RunCenterCurrentTaskV50(
                        runtime =
                            runtime,
                        item =
                            liveItem,
                        now =
                            now,
                        onOpen = {
                            liveItem
                                ?.let(
                                    onOpenTask,
                                )
                        },
                        onLogs = {
                            selectedLogKey =
                                LIVE_LOG_KEY_V50
                        },
                        onCancel =
                            onCancelCurrent,
                    )
                }
            }
        }


        /* ------------------------------------------------------------------ */
        /* Queue management                                                   */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "run-center-queue",
        ) {
            Column {
                RunCenterSectionTitleV50(
                    title =
                        "队列管理",
                )

                Spacer(
                    Modifier.height(
                        t.space2,
                    ),
                )

                RunCenterQueueSummaryV50(
                    runtime =
                        runtime,
                    items =
                        state.items,
                )
            }
        }


        /* ------------------------------------------------------------------ */
        /* Task list                                                          */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "run-center-task-title",
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                RunCenterSectionTitleV50(
                    title =
                        "任务列表",
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                )

                Text(
                    text =
                        "${state.items.size} 个",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        t.mutedForeground,
                )
            }
        }


        when {
            state.isLoading &&
                state.items.isEmpty() -> {
                item(
                    key =
                        "run-center-loading",
                ) {
                    RunCenterLoadingV50()
                }
            }

            // A failed checkpoint read must not claim "no tasks"; the error card below explains it.
            state.items.isEmpty() &&
                state.error.isNullOrBlank() -> {
                item(
                    key =
                        "run-center-empty",
                ) {
                    RunCenterEmptyV50(
                        runtimeActive =
                            runtime.active,
                    )
                }
            }

            else -> {
                items(
                    items =
                        state.items,
                    key = {
                        runCenterKeyV50(
                            it,
                        )
                    },
                ) {
                    item ->

                    val isLive =
                        runtime.active &&
                            runtime.matches(
                                item.novelId,
                                item.chapterNumber,
                            )

                    RunCenterTaskCardV50(
                        item =
                            item,
                        isLive =
                            isLive,
                        liveDetail =
                            if (isLive) {
                                runtime.events
                                    .lastOrNull()
                                    ?.detail
                                    .orEmpty()
                            } else {
                                ""
                            },
                        selectedForLogs =
                            runCenterKeyV50(
                                item,
                            ) ==
                                selectedLogKey,
                        onOpen = {
                            onOpenTask(
                                item,
                            )
                        },
                        onRetry = {
                            onRetryTask(
                                item,
                            )
                        },
                        onLogs = {
                            selectedLogKey =
                                runCenterKeyV50(
                                    item,
                                )
                        },
                        onCancel = {
                            pendingCancel =
                                item
                        },
                    )
                }
            }
        }


        state.error
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                error ->

                item(
                    key =
                        "run-center-error",
                ) {
                    RunCenterErrorV50(
                        error,
                    )
                }
            }


        /* ------------------------------------------------------------------ */
        /* Runtime logs                                                       */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "run-center-logs",
        ) {
            Column {
                RunCenterSectionTitleV50(
                    title =
                        "运行日志",
                )

                Spacer(
                    Modifier.height(
                        t.space2,
                    ),
                )

                RunCenterLogsV50(
                    title =
                        logTitle,
                    events =
                        logEvents,
                    running =
                        runtime.active &&
                            (
                                selectedLogKey ==
                                    null ||
                                    selectedLogKey ==
                                    LIVE_LOG_KEY_V50
                                ),
                    now =
                        now,
                )
            }
        }
    }


    pendingCancel
        ?.let {
            item ->

            RunCenterCancelDialogV50(
                item =
                    item,
                onDismiss = {
                    pendingCancel =
                        null
                },
                onConfirm = {
                    pendingCancel =
                        null

                    onCancelTask(
                        item,
                    )
                },
            )
        }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterHeaderV50(
    runtime: ChapterRunRuntimeState,
    onBack: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        RunCenterIconButtonV50(
            icon =
                Icons.Rounded.ArrowBack,
            description =
                "返回",
            onClick =
                onBack,
        )

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    "运行中心",
                style =
                    MaterialTheme.typography.headlineLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    if (runtime.active) {
                        "任务正在后台执行"
                    } else {
                        "查看任务、断点、队列与运行日志"
                    },
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        RunCenterStatusPillV50(
            text =
                if (runtime.active) {
                    "运行中"
                } else {
                    "空闲"
                },
            foreground =
                if (runtime.active) {
                    t.primary
                } else {
                    t.mutedForeground
                },
            container =
                if (runtime.active) {
                    t.accent
                } else {
                    t.input
                },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Overview                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterOverviewV50(
    runtime: ChapterRunRuntimeState,
    pendingCount: Int,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                t.space2,
            ),
    ) {
        RunCenterMetricV50(
            icon =
                Icons.Rounded.TaskAlt,
            value =
                if (runtime.active) {
                    "1"
                } else {
                    "0"
                },
            label =
                "正在运行",
            accent =
                runtime.active,
            modifier =
                Modifier.weight(
                    1f,
                ),
        )

        RunCenterMetricV50(
            icon =
                Icons.Rounded.Schedule,
            value =
                runtime.queuedCount
                    .coerceAtLeast(
                        0,
                    )
                    .toString(),
            label =
                "等待队列",
            gold =
                runtime.queuedCount >
                    0,
            modifier =
                Modifier.weight(
                    1f,
                ),
        )

        RunCenterMetricV50(
            icon =
                Icons.Rounded.PendingActions,
            value =
                pendingCount
                    .coerceAtLeast(
                        0,
                    )
                    .toString(),
            label =
                "待处理",
            modifier =
                Modifier.weight(
                    1f,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Current Task                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterCurrentTaskV50(
    runtime: ChapterRunRuntimeState,
    item: RunCenterItemUi?,
    now: Long,
    onOpen: () -> Unit,
    onLogs: () -> Unit,
    onCancel: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusXl,
        )

    val latest =
        runtime.events
            .lastOrNull()

    val progress =
        runCenterLiveProgressV50(
            runtime.events,
            runtime.active,
        )

    val canCancel =
        runtime.active &&
            runtime.taskKind ==
            ChapterRuntimeTaskKind.GENERATE


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        46.dp,
                    )
                    .background(
                        color =
                            if (runtime.active) {
                                t.accent
                            } else {
                                t.input
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                if (runtime.active) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                21.dp,
                            ),
                        color =
                            t.primary,
                        strokeWidth =
                            2.dp,
                    )
                } else {
                    Icon(
                        imageVector =
                            Icons.Rounded.History,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                21.dp,
                            ),
                        tint =
                            t.mutedForeground,
                    )
                }
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            runtime.snapshot
                                ?.novel
                                ?.title
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: item
                                    ?.novelTitle
                                    .orEmpty()
                                    .ifBlank {
                                        "当前任务"
                                    },
                        modifier =
                            Modifier.weight(
                                1f,
                            ),
                        style =
                            MaterialTheme.typography.titleMedium,
                        color =
                            t.foreground,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines =
                            1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    RunCenterStatusPillV50(
                        text =
                            if (runtime.active) {
                                "执行中"
                            } else {
                                "已停止"
                            },
                        foreground =
                            if (runtime.active) {
                                t.primary
                            } else {
                                t.mutedForeground
                            },
                        container =
                            if (runtime.active) {
                                t.accent
                            } else {
                                t.input
                            },
                    )
                }

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        "第${runtime.chapterNumber.coerceAtLeast(item?.chapterNumber ?: 0)}章 · ${
                            runtime.draft
                                ?.title
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: item
                                    ?.chapterTitle
                                    .orEmpty()
                                    .ifBlank {
                                        "章节任务"
                                    }
                        }",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                    maxLines =
                        1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space4,
            ),
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    runCenterTaskKindLabelV50(
                        runtime.taskKind,
                    ),
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    t.secondaryForeground,
            )

            if (
                runtime.startedAt >
                0L
            ) {
                Text(
                    text =
                        runCenterDurationV50(
                            now -
                                runtime.startedAt,
                        ),
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        t.mutedForeground,
                )
            }
        }


        Spacer(
            Modifier.height(
                t.space2,
            ),
        )


        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    5.dp,
                ),
            color =
                t.primary,
            trackColor =
                t.border,
        )


        Spacer(
            Modifier.height(
                t.space2,
            ),
        )


        Text(
            text =
                latest
                    ?.let {
                        "${it.stage.label} · ${it.detail}"
                    }
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: runtime.message
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "等待运行事件",
            style =
                MaterialTheme.typography.bodySmall,
            color =
                if (
                    runtime.error !=
                    null
                ) {
                    t.destructive
                } else {
                    t.mutedForeground
                },
            maxLines =
                3,
            overflow =
                TextOverflow.Ellipsis,
        )


        runtime.providerLabel
            .takeIf {
                it.isNotBlank()
            }
            ?.let {
                provider ->

                Spacer(
                    Modifier.height(
                        t.space2,
                    ),
                )

                Text(
                    text =
                        provider,
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.secondaryForeground,
                    maxLines =
                        1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }


        runtime.error
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                error ->

                Spacer(
                    Modifier.height(
                        t.space3,
                    ),
                )

                RunCenterInlineErrorV50(
                    error,
                )
            }


        Spacer(
            Modifier.height(
                t.space4,
            ),
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            RunCenterActionButtonV50(
                icon =
                    Icons.Rounded.History,
                text =
                    "查看日志",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onLogs,
            )

            if (
                item !=
                null
            ) {
                RunCenterActionButtonV50(
                    icon =
                        Icons.Rounded.OpenInNew,
                    text =
                        "打开任务",
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onOpen,
                )
            }

            if (canCancel) {
                RunCenterActionButtonV50(
                    icon =
                        Icons.Rounded.Stop,
                    text =
                        "取消",
                    destructive =
                        true,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onCancel,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Queue Summary                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterQueueSummaryV50(
    runtime: ChapterRunRuntimeState,
    items: List<RunCenterItemUi>,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    val interrupted =
        items.count {
            it.phase ==
                DurableRunPhase.INTERRUPTED ||
                (
                    it.phase ==
                        DurableRunPhase.GENERATING &&
                        !runtime.matches(
                            it.novelId,
                            it.chapterNumber,
                        )
                    )
        }

    val waitingSave =
        items.count {
            it.phase ==
                DurableRunPhase.READY_TO_COMMIT
        }

    val postProcessing =
        items.count {
            it.phase ==
                DurableRunPhase.COMMITTING ||
                it.phase ==
                DurableRunPhase.REVIEWING
        }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        40.dp,
                    )
                    .background(
                        color =
                            if (
                                runtime.queuedCount >
                                0
                            ) {
                                t.goldContainer
                            } else {
                                t.input
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.FormatListBulleted,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            20.dp,
                        ),
                    tint =
                        if (
                            runtime.queuedCount >
                            0
                        ) {
                            t.goldForeground
                        } else {
                            t.secondaryForeground
                        },
                )
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Text(
                    text =
                        "等待队列 ${runtime.queuedCount.coerceAtLeast(0)}",
                    style =
                        MaterialTheme.typography.titleMedium,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        if (
                            runtime.active
                        ) {
                            "当前任务结束后按加入顺序继续执行"
                        } else {
                            "当前没有正在占用运行时的任务"
                        },
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )
            }
        }


        Spacer(
            Modifier.height(
                t.space4,
            ),
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            RunCenterSmallMetricV50(
                value =
                    interrupted.toString(),
                label =
                    "可重试",
                gold =
                    interrupted >
                        0,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )

            RunCenterSmallMetricV50(
                value =
                    waitingSave.toString(),
                label =
                    "待保存",
                accent =
                    waitingSave >
                        0,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )

            RunCenterSmallMetricV50(
                value =
                    postProcessing.toString(),
                label =
                    "后处理",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Task Card                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterTaskCardV50(
    item: RunCenterItemUi,
    isLive: Boolean,
    liveDetail: String,
    selectedForLogs: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onLogs: () -> Unit,
    onCancel: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val tone =
        runCenterToneV50(
            phase =
                item.phase,
            live =
                isLive,
            tokens =
                t,
        )

    val latest =
        item.events
            .lastOrNull()

    val progress =
        runCenterItemProgressV50(
            item,
            isLive,
        )

    val retryable =
        !isLive &&
            (
                item.phase ==
                    DurableRunPhase.INTERRUPTED ||
                    item.phase ==
                    DurableRunPhase.GENERATING
                )

    val primaryLabel =
        when {
            retryable -> {
                "重试"
            }

            item.phase ==
                DurableRunPhase.READY_TO_COMMIT -> {
                "继续保存"
            }

            item.phase ==
                DurableRunPhase.COMMITTING -> {
                "继续后处理"
            }

            item.phase ==
                DurableRunPhase.REVIEWING -> {
                "继续复盘"
            }

            isLive -> {
                "打开"
            }

            else -> {
                "继续"
            }
        }

    val primaryIcon =
        if (retryable) {
            Icons.Rounded.Refresh
        } else if (isLive) {
            Icons.Rounded.OpenInNew
        } else {
            Icons.Rounded.PlayArrow
        }

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    if (selectedForLogs) {
                        t.accent
                    } else {
                        t.card
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    if (selectedForLogs) {
                        t.primary
                    } else {
                        t.border
                    },
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        42.dp,
                    )
                    .background(
                        color =
                            tone.container,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                if (isLive) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                19.dp,
                            ),
                        color =
                            tone.foreground,
                        strokeWidth =
                            2.dp,
                    )
                } else {
                    Icon(
                        imageVector =
                            runCenterPhaseIconV50(
                                item.phase,
                            ),
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                20.dp,
                            ),
                        tint =
                            tone.foreground,
                    )
                }
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            item.novelTitle,
                        modifier =
                            Modifier.weight(
                                1f,
                            ),
                        style =
                            MaterialTheme.typography.titleMedium,
                        color =
                            t.foreground,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines =
                            1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Spacer(
                        Modifier.width(
                            t.space2,
                        ),
                    )

                    RunCenterStatusPillV50(
                        text =
                            tone.label,
                        foreground =
                            tone.foreground,
                        container =
                            tone.container,
                    )
                }

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        "第${item.chapterNumber}章 · ${item.chapterTitle}",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                    maxLines =
                        1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    "已完成 ${item.completedCount} 个阶段",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    t.secondaryForeground,
            )

            Text(
                text =
                    runCenterAgeV50(
                        item.updatedAt,
                    ),
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    t.mutedForeground,
            )
        }


        Spacer(
            Modifier.height(
                t.space2,
            ),
        )


        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    5.dp,
                ),
            color =
                tone.foreground,
            trackColor =
                t.border,
        )


        Spacer(
            Modifier.height(
                t.space3,
            ),
        )


        Text(
            text =
                liveDetail
                    .ifBlank {
                        latest
                            ?.let {
                                "${it.stage.label} · ${it.detail}"
                            }
                            .orEmpty()
                    }
                    .ifBlank {
                        item.note
                    }
                    .ifBlank {
                        "等待继续处理"
                    },
            style =
                MaterialTheme.typography.bodySmall,
            color =
                t.mutedForeground,
            maxLines =
                3,
            overflow =
                TextOverflow.Ellipsis,
        )


        Spacer(
            Modifier.height(
                t.space4,
            ),
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            RunCenterActionButtonV50(
                icon =
                    Icons.Rounded.History,
                text =
                    "日志",
                selected =
                    selectedForLogs,
                modifier =
                    Modifier.weight(
                        0.85f,
                    ),
                onClick =
                    onLogs,
            )

            if (!isLive) {
                RunCenterActionButtonV50(
                    icon =
                        Icons.Rounded.DeleteOutline,
                    text =
                        "取消",
                    destructive =
                        true,
                    modifier =
                        Modifier.weight(
                            0.85f,
                        ),
                    onClick =
                        onCancel,
                )
            }

            RunCenterActionButtonV50(
                icon =
                    primaryIcon,
                text =
                    primaryLabel,
                accent =
                    true,
                modifier =
                    Modifier.weight(
                        1.2f,
                    ),
                onClick =
                    if (retryable) {
                        onRetry
                    } else {
                        onOpen
                    },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Logs                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterLogsV50(
    title: String,
    events: List<RunEvent>,
    running: Boolean,
    now: Long,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        40.dp,
                    )
                    .background(
                        color =
                            if (running) {
                                t.accent
                            } else {
                                t.input
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                18.dp,
                            ),
                        color =
                            t.primary,
                        strokeWidth =
                            2.dp,
                    )
                } else {
                    Icon(
                        imageVector =
                            Icons.Rounded.History,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                19.dp,
                            ),
                        tint =
                            t.secondaryForeground,
                    )
                }
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Text(
                    text =
                        title,
                    style =
                        MaterialTheme.typography.titleMedium,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines =
                        1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        if (events.isEmpty()) {
                            "暂无运行日志"
                        } else {
                            "已记录 ${events.size} 条事件"
                        },
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )
            }
        }

        if (events.isEmpty()) {
            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color =
                            t.input,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .padding(
                        t.space4,
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text =
                        "任务开始后，模型调用、校验、保存与后处理日志会显示在这里。",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                    textAlign =
                        androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            return
        }


        Spacer(
            Modifier.height(
                t.space4,
            ),
        )


        events
            .takeLast(
                32,
            )
            .forEachIndexed {
                index,
                event ->

                RunCenterLogRowV50(
                    event =
                        event,
                    now =
                        now,
                )

                if (
                    index <
                    events
                        .takeLast(
                            32,
                        )
                        .lastIndex
                ) {
                    Spacer(
                        Modifier.height(
                            t.space3,
                        ),
                    )
                }
            }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Log Row                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterLogRowV50(
    event: RunEvent,
    now: Long,
) {
    val t =
        LocalLanghuanUiTokens.current

    val foreground =
        runCenterEventColorV50(
            event.status,
            t,
        )

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(
                    28.dp,
                )
                .background(
                    color =
                        runCenterEventContainerV50(
                            event.status,
                            t,
                        ),
                    shape =
                        RoundedCornerShape(
                            t.radiusSm,
                        ),
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        RoundedCornerShape(
                            t.radiusSm,
                        ),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            when (
                event.status
            ) {
                RunStatus.RUNNING -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                14.dp,
                            ),
                        color =
                            foreground,
                        strokeWidth =
                            1.6.dp,
                    )
                }

                RunStatus.SUCCESS -> {
                    Icon(
                        imageVector =
                            Icons.Rounded.CheckCircle,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                15.dp,
                            ),
                        tint =
                            foreground,
                    )
                }

                RunStatus.WARNING -> {
                    Icon(
                        imageVector =
                            Icons.Rounded.WarningAmber,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                15.dp,
                            ),
                        tint =
                            foreground,
                    )
                }

                RunStatus.FAILED -> {
                    Icon(
                        imageVector =
                            Icons.Rounded.ErrorOutline,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                15.dp,
                            ),
                        tint =
                            foreground,
                    )
                }

                RunStatus.SKIPPED -> {
                    Icon(
                        imageVector =
                            Icons.Rounded.RemoveCircleOutline,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                15.dp,
                            ),
                        tint =
                            foreground,
                    )
                }
            }
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        event.stage.label,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        t.foreground,
                    fontWeight =
                        if (
                            event.status ==
                            RunStatus.RUNNING
                        ) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Medium
                        },
                )

                Text(
                    text =
                        if (
                            event.status ==
                            RunStatus.RUNNING
                        ) {
                            runCenterDurationV50(
                                now -
                                    event.atMillis,
                            )
                        } else {
                            runCenterEventTimeV50(
                                event.atMillis,
                            )
                        },
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.mutedForeground,
                )
            }

            if (
                event.detail.isNotBlank()
            ) {
                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        event.detail,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        if (
                            event.status ==
                            RunStatus.FAILED
                        ) {
                            t.destructive
                        } else {
                            t.mutedForeground
                        },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Cancel Dialog                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterCancelDialogV50(
    item: RunCenterItemUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Dialog(
        onDismissRequest =
            onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress =
                    true,
                dismissOnClickOutside =
                    true,
            ),
    ) {
        val shape =
            RoundedCornerShape(
                t.radiusXl,
            )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color =
                        t.card,
                    shape =
                        shape,
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        shape,
                )
                .padding(
                    t.space4,
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(
                        44.dp,
                    )
                    .background(
                        color =
                            t.destructive.copy(
                                alpha =
                                    0.08f,
                            ),
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.DeleteOutline,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            21.dp,
                        ),
                    tint =
                        t.destructive,
                )
            }

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )

            Text(
                text =
                    "取消这个任务？",
                style =
                    MaterialTheme.typography.headlineLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space2,
                ),
            )

            Text(
                text =
                    "将删除《${item.novelTitle}》第${item.chapterNumber}章的运行断点。已经正式保存到小说的数据不会被删除。",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    t.secondaryForeground,
            )

            Spacer(
                Modifier.height(
                    t.space5,
                ),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        t.space2,
                    ),
            ) {
                RunCenterDialogButtonV50(
                    text =
                        "返回",
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onDismiss,
                )

                RunCenterDialogButtonV50(
                    text =
                        "确认取消",
                    destructive =
                        true,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onConfirm,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Small Pieces                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun RunCenterSectionTitleV50(
    title: String,
    modifier: Modifier = Modifier,
) {
    val t =
        LocalLanghuanUiTokens.current

    Text(
        text =
            title,
        modifier =
            modifier,
        style =
            MaterialTheme.typography.titleLarge,
        color =
            t.foreground,
        fontWeight =
            FontWeight.SemiBold,
    )
}


@Composable
private fun RunCenterMetricV50(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    gold: Boolean = false,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = modifier
            .background(
                color =
                    when {
                        gold -> {
                            t.goldContainer
                        }

                        accent -> {
                            t.accent
                        }

                        else -> {
                            t.card
                        }
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space3,
            ),
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    18.dp,
                ),
            tint =
                when {
                    gold -> {
                        t.goldForeground
                    }

                    accent -> {
                        t.primary
                    }

                    else -> {
                        t.secondaryForeground
                    }
                },
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text =
                value,
            style =
                MaterialTheme.typography.titleLarge,
            color =
                when {
                    gold -> {
                        t.goldForeground
                    }

                    accent -> {
                        t.accentForeground
                    }

                    else -> {
                        t.foreground
                    }
                },
            fontWeight =
                FontWeight.SemiBold,
        )

        Spacer(
            Modifier.height(
                t.space1,
            ),
        )

        Text(
            text =
                label,
            style =
                MaterialTheme.typography.labelSmall,
            color =
                t.mutedForeground,
        )
    }
}


@Composable
private fun RunCenterSmallMetricV50(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    gold: Boolean = false,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Column(
        modifier = modifier
            .background(
                color =
                    when {
                        gold -> {
                            t.goldContainer
                        }

                        accent -> {
                            t.accent
                        }

                        else -> {
                            t.input
                        }
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space3,
            ),
    ) {
        Text(
            text =
                value,
            style =
                MaterialTheme.typography.titleMedium,
            color =
                when {
                    gold -> {
                        t.goldForeground
                    }

                    accent -> {
                        t.accentForeground
                    }

                    else -> {
                        t.foreground
                    }
                },
            fontWeight =
                FontWeight.SemiBold,
        )

        Spacer(
            Modifier.height(
                t.space1,
            ),
        )

        Text(
            text =
                label,
            style =
                MaterialTheme.typography.labelSmall,
            color =
                t.mutedForeground,
        )
    }
}


@Composable
private fun RunCenterActionButtonV50(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    val background =
        when {
            !enabled -> {
                t.input
            }

            destructive -> {
                t.destructive.copy(
                    alpha =
                        0.08f,
                )
            }

            accent ||
                selected -> {
                t.accent
            }

            else -> {
                t.card
            }
        }

    val foreground =
        when {
            !enabled -> {
                t.mutedForeground.copy(
                    alpha =
                        0.5f,
                )
            }

            destructive -> {
                t.destructive
            }

            accent ||
                selected -> {
                t.accentForeground
            }

            else -> {
                t.secondaryForeground
            }
        }


    Row(
        modifier = modifier
            .height(
                42.dp,
            )
            .background(
                color =
                    background,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    if (
                        selected ||
                        accent
                    ) {
                        t.primary
                    } else {
                        t.border
                    },
                shape =
                    shape,
            )
            .clickable(
                enabled =
                    enabled,
                onClick =
                    onClick,
            )
            .padding(
                horizontal =
                    t.space2,
            ),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    16.dp,
                ),
            tint =
                foreground,
        )

        Spacer(
            Modifier.width(
                t.space1,
            ),
        )

        Text(
            text =
                text,
            style =
                MaterialTheme.typography.labelMedium,
            color =
                foreground,
            fontWeight =
                FontWeight.Medium,
            maxLines =
                1,
        )
    }
}


@Composable
private fun RunCenterStatusPillV50(
    text: String,
    foreground: Color,
    container: Color,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .background(
                color =
                    container,
                shape =
                    RoundedCornerShape(
                        t.radiusXl,
                    ),
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    RoundedCornerShape(
                        t.radiusXl,
                    ),
            )
            .padding(
                horizontal =
                    t.space2,
                vertical =
                    t.space1,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text =
                text,
            style =
                MaterialTheme.typography.labelSmall,
            color =
                foreground,
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}


@Composable
private fun RunCenterIconButtonV50(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Box(
        modifier = Modifier
            .size(
                40.dp,
            )
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication =
                    null,
                onClick =
                    onClick,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                description,
            modifier =
                Modifier.size(
                    20.dp,
                ),
            tint =
                t.secondaryForeground,
        )
    }
}


@Composable
private fun RunCenterLoadingV50() {
    val t =
        LocalLanghuanUiTokens.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                96.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier.size(
                    22.dp,
                ),
            color =
                t.primary,
            strokeWidth =
                2.dp,
        )
    }
}


@Composable
private fun RunCenterEmptyV50(
    runtimeActive: Boolean,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space5,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector =
                Icons.Rounded.TaskAlt,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    30.dp,
                ),
            tint =
                t.primary,
        )

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )

        Text(
            text =
                if (runtimeActive) {
                    "当前任务尚未形成可恢复断点"
                } else {
                    "暂无运行任务"
                },
            style =
                MaterialTheme.typography.titleMedium,
            color =
                t.foreground,
            fontWeight =
                FontWeight.SemiBold,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text =
                "中断、待保存或待后处理的章节会出现在这里。",
            style =
                MaterialTheme.typography.bodySmall,
            color =
                t.mutedForeground,
        )
    }
}


@Composable
private fun RunCenterInlineErrorV50(
    message: String,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.destructive.copy(
                        alpha =
                            0.08f,
                    ),
                shape =
                    RoundedCornerShape(
                        t.radiusMd,
                    ),
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    RoundedCornerShape(
                        t.radiusMd,
                    ),
            )
            .padding(
                t.space3,
            ),
        verticalAlignment =
            Alignment.Top,
    ) {
        Icon(
            imageVector =
                Icons.Rounded.ErrorOutline,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    18.dp,
                ),
            tint =
                t.destructive,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                message,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                t.destructive,
        )
    }
}


@Composable
private fun RunCenterErrorV50(
    message: String,
) {
    RunCenterInlineErrorV50(
        message,
    )
}


@Composable
private fun RunCenterDialogButtonV50(
    text: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Box(
        modifier = modifier
            .height(
                44.dp,
            )
            .background(
                color =
                    if (destructive) {
                        t.destructive
                    } else {
                        t.card
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    if (destructive) {
                        t.destructive
                    } else {
                        t.border
                    },
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                text,
            style =
                MaterialTheme.typography.labelLarge,
            color =
                if (destructive) {
                    t.destructiveForeground
                } else {
                    t.secondaryForeground
                },
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Tone                                      */
/* -------------------------------------------------------------------------- */

private data class RunCenterToneV50(
    val label: String,
    val foreground: Color,
    val container: Color,
)


private fun runCenterToneV50(
    phase: DurableRunPhase,
    live: Boolean,
    tokens: LanghuanUiTokens,
): RunCenterToneV50 {
    if (live) {
        return RunCenterToneV50(
            label =
                "执行中",
            foreground =
                tokens.primary,
            container =
                tokens.accent,
        )
    }

    return when (
        phase
    ) {
        DurableRunPhase.GENERATING -> {
            RunCenterToneV50(
                label =
                    "待恢复",
                foreground =
                    tokens.goldForeground,
                container =
                    tokens.goldContainer,
            )
        }

        DurableRunPhase.INTERRUPTED -> {
            RunCenterToneV50(
                label =
                    "已中断",
                foreground =
                    tokens.goldForeground,
                container =
                    tokens.goldContainer,
            )
        }

        DurableRunPhase.READY_TO_COMMIT -> {
            RunCenterToneV50(
                label =
                    "待保存",
                foreground =
                    tokens.accentForeground,
                container =
                    tokens.accent,
            )
        }

        DurableRunPhase.COMMITTING -> {
            RunCenterToneV50(
                label =
                    "待后处理",
                foreground =
                    tokens.accentForeground,
                container =
                    tokens.accent,
            )
        }

        DurableRunPhase.REVIEWING -> {
            RunCenterToneV50(
                label =
                    "待复盘",
                foreground =
                    tokens.accentForeground,
                container =
                    tokens.accent,
            )
        }

        DurableRunPhase.COMPLETE -> {
            RunCenterToneV50(
                label =
                    "已完成",
                foreground =
                    tokens.primary,
                container =
                    tokens.accent,
            )
        }
    }
}


private fun runCenterEventColorV50(
    status: RunStatus,
    tokens: LanghuanUiTokens,
): Color {
    return when (
        status
    ) {
        RunStatus.RUNNING -> {
            tokens.primary
        }

        RunStatus.SUCCESS -> {
            tokens.primary
        }

        RunStatus.WARNING -> {
            tokens.gold
        }

        RunStatus.FAILED -> {
            tokens.destructive
        }

        RunStatus.SKIPPED -> {
            tokens.mutedForeground
        }
    }
}


private fun runCenterEventContainerV50(
    status: RunStatus,
    tokens: LanghuanUiTokens,
): Color {
    return when (
        status
    ) {
        RunStatus.RUNNING,
        RunStatus.SUCCESS -> {
            tokens.accent
        }

        RunStatus.WARNING -> {
            tokens.goldContainer
        }

        RunStatus.FAILED -> {
            tokens.destructive.copy(
                alpha =
                    0.08f,
            )
        }

        RunStatus.SKIPPED -> {
            tokens.input
        }
    }
}


private fun runCenterPhaseIconV50(
    phase: DurableRunPhase,
): ImageVector {
    return when (
        phase
    ) {
        DurableRunPhase.GENERATING,
        DurableRunPhase.INTERRUPTED -> {
            Icons.Rounded.History
        }

        DurableRunPhase.READY_TO_COMMIT -> {
            Icons.Rounded.CheckCircle
        }

        DurableRunPhase.COMMITTING -> {
            Icons.Rounded.PendingActions
        }

        DurableRunPhase.REVIEWING -> {
            Icons.Rounded.HourglassTop
        }

        DurableRunPhase.COMPLETE -> {
            Icons.Rounded.CheckCircle
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Progress                                    */
/* -------------------------------------------------------------------------- */

private fun runCenterLiveProgressV50(
    events: List<RunEvent>,
    active: Boolean,
): Float {
    if (events.isEmpty()) {
        return if (active) {
            0.06f
        } else {
            0f
        }
    }

    val latest =
        events
            .groupBy {
                it.stage
            }
            .mapValues {
                it.value.last()
            }
            .values

    val terminal =
        latest.count {
            it.status !=
                RunStatus.RUNNING
        }

    val running =
        latest.any {
            it.status ==
                RunStatus.RUNNING
        }

    val denominator =
        terminal +
            if (running) {
                1
            } else {
                0
            }

    if (denominator <= 0) {
        return 0.06f
    }

    return (
        terminal.toFloat() /
            denominator.toFloat()
        )
        .coerceIn(
            0.06f,
            if (active) {
                0.92f
            } else {
                1f
            },
        )
}


private fun runCenterItemProgressV50(
    item: RunCenterItemUi,
    live: Boolean,
): Float {
    if (live) {
        return runCenterLiveProgressV50(
            item.events,
            active =
                true,
        )
    }

    val eventProgress =
        runCenterLiveProgressV50(
            item.events,
            active =
                false,
        )

    return when (
        item.phase
    ) {
        DurableRunPhase.GENERATING -> {
            eventProgress
                .coerceIn(
                    0.08f,
                    0.76f,
                )
        }

        DurableRunPhase.INTERRUPTED -> {
            eventProgress
                .coerceIn(
                    0.08f,
                    0.82f,
                )
        }

        DurableRunPhase.READY_TO_COMMIT -> {
            maxOf(
                eventProgress,
                0.84f,
            )
        }

        DurableRunPhase.REVIEWING -> {
            maxOf(
                eventProgress,
                0.78f,
            )
        }

        DurableRunPhase.COMMITTING -> {
            maxOf(
                eventProgress,
                0.90f,
            )
        }

        DurableRunPhase.COMPLETE -> {
            1f
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Helpers                                    */
/* -------------------------------------------------------------------------- */

private fun runCenterTaskKindLabelV50(
    kind: ChapterRuntimeTaskKind,
): String {
    return when (
        kind
    ) {
        ChapterRuntimeTaskKind.IDLE -> {
            "空闲"
        }

        ChapterRuntimeTaskKind.GENERATE -> {
            "正文生成"
        }

        ChapterRuntimeTaskKind.COMMIT -> {
            "保存与后处理"
        }

        ChapterRuntimeTaskKind.REVIEW -> {
            "Agent 复盘"
        }
    }
}


private fun runCenterDurationV50(
    durationMillis: Long,
): String {
    val seconds =
        durationMillis
            .coerceAtLeast(
                0L,
            ) /
            1_000L

    return when {
        seconds <
            60L -> {
            "${seconds}s"
        }

        seconds <
            3_600L -> {
            val minutes =
                seconds /
                    60L

            val remain =
                seconds %
                    60L

            "${minutes}m ${remain}s"
        }

        else -> {
            val hours =
                seconds /
                    3_600L

            val minutes =
                (
                    seconds %
                        3_600L
                    ) /
                    60L

            "${hours}h ${minutes}m"
        }
    }
}


private fun runCenterAgeV50(
    time: Long,
): String {
    if (time <= 0L) {
        return "未知时间"
    }

    val seconds =
        (
            System.currentTimeMillis() -
                time
            )
            .coerceAtLeast(
                0L,
            ) /
            1_000L

    return when {
        seconds <
            60L -> {
            "刚刚"
        }

        seconds <
            3_600L -> {
            "${seconds / 60L} 分钟前"
        }

        seconds <
            86_400L -> {
            "${seconds / 3_600L} 小时前"
        }

        else -> {
            "${seconds / 86_400L} 天前"
        }
    }
}


private fun runCenterEventTimeV50(
    time: Long,
): String {
    if (time <= 0L) {
        return ""
    }

    return SimpleDateFormat(
        "HH:mm:ss",
        Locale.getDefault(),
    ).format(
        Date(
            time,
        ),
    )
}


private fun runCenterKeyV50(
    item: RunCenterItemUi,
): String {
    return "${item.novelId}:${item.chapterNumber}:${item.runId}"
}


private const val LIVE_LOG_KEY_V50 =
    "__live_run__"