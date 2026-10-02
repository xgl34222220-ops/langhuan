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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlin.math.roundToInt


/* -------------------------------------------------------------------------- */
/*                                   Pitch                                    */
/* -------------------------------------------------------------------------- */

/**
 * 听书页 UI 的三档音调。
 *
 * 真正 TTS pitch 应由上层已有朗读控制层处理，
 * 本页面只通过 onPitchChange 回调传递选择。
 */
@Immutable
internal enum class TtsPitchV50(
    val label: String,
    val value: Float,
) {
    LOW(
        label = "偏低",
        value = 0.85f,
    ),

    STANDARD(
        label = "标准",
        value = 1.00f,
    ),

    HIGH(
        label = "偏高",
        value = 1.15f,
    ),
}


/* -------------------------------------------------------------------------- */
/*                                  Screen                                    */
/* -------------------------------------------------------------------------- */

/**
 * 听书 · V50。
 *
 * 正文队列直接复用 ReaderTtsChunkV35。
 *
 * 定时状态直接复用 ReaderTtsSleepOptionV50：
 *
 * - OFF
 * - MINUTES_10
 * - MINUTES_30
 * - MINUTES_60
 * - END_OF_CHAPTER
 *
 * 原型要求的 90 分钟和自定义时长由 onSleepTimerMinutes()
 * 交给上层朗读控制层处理；当上层当前运行的是扩展分钟定时，
 * 通过 customSleepTimerMinutes / customSleepTimerRemainingMillis
 * 回传给本页面显示。
 *
 * 页面不创建第二套 TTS 数据层。
 */
@Composable
internal fun TtsScreenV50(
    chapterTitle: String,
    bookProgress: Float,
    timeLabel: String,
    batteryPercent: Int,
    chunks: List<ReaderTtsChunkV35>,
    currentOffset: Int,

    listening: Boolean,
    currentVoiceLabel: String,
    speechRate: Float,
    pitch: TtsPitchV50,
    volume: Float,

    skipImageDescriptions: Boolean,
    lockScreenControls: Boolean,
    autoResumeCarBluetooth: Boolean,
    backgroundPlayback: Boolean,

    sleepTimerOption: ReaderTtsSleepOptionV50,
    sleepTimerRemainingMillis: Long,
    customSleepTimerMinutes: Int? = null,
    customSleepTimerRemainingMillis: Long = 0L,

    modifier: Modifier = Modifier,

    onBack: () -> Unit,
    onOpenVoiceManager: () -> Unit,
    onPreviewCurrentVoice: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onPitchChange: (TtsPitchV50) -> Unit,
    onVolumeChange: (Float) -> Unit,

    onSkipImageDescriptionsChange: (Boolean) -> Unit,
    onLockScreenControlsChange: (Boolean) -> Unit,
    onAutoResumeCarBluetoothChange: (Boolean) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,

    onSleepTimerMinutes: (Int) -> Unit,
    onSleepTimerEndOfChapter: () -> Unit,
    onSleepTimerCancel: () -> Unit,

    onStop: () -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var settingsOpen by
        rememberSaveable {
            mutableStateOf(
                false,
            )
        }

    var speedOpen by
        rememberSaveable {
            mutableStateOf(
                false,
            )
        }

    var sleepTimerOpen by
        rememberSaveable {
            mutableStateOf(
                false,
            )
        }


    val listState =
        rememberLazyListState()


    val currentIndex =
        remember(
            chunks,
            currentOffset,
        ) {
            if (chunks.isEmpty()) {
                -1
            } else {
                chunks.indexOfLast {
                    it.offset <=
                        currentOffset
                }
                    .coerceAtLeast(
                        0,
                    )
            }
        }


    /*
     * TTS onChunkStart(offset) 更新 currentOffset 后，
     * 自动把当前朗读段滚到视口中部附近。
     */
    LaunchedEffect(
        currentIndex,
    ) {
        if (
            currentIndex in
            chunks.indices
        ) {
            listState.animateScrollToItem(
                index =
                    currentIndex,
                scrollOffset =
                    0,
            )
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding(),
    ) {

        /* ------------------------------------------------------------------ */
        /* Status bar                                                         */
        /* ------------------------------------------------------------------ */

        TtsStatusBarV50(
            chapterTitle =
                chapterTitle,
            bookProgress =
                bookProgress,
            timeLabel =
                timeLabel,
            batteryPercent =
                batteryPercent,
            onBack =
                onBack,
        )


        /* ------------------------------------------------------------------ */
        /* Reading text                                                       */
        /* ------------------------------------------------------------------ */

        Box(
            modifier = Modifier
                .weight(
                    1f,
                )
                .fillMaxWidth(),
        ) {
            if (
                chunks.isEmpty()
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Text(
                        text =
                            "本章暂无可朗读文本",
                        style =
                            MaterialTheme.typography.bodyLarge,
                        color =
                            t.mutedForeground,
                    )
                }
            } else {
                LazyColumn(
                    state =
                        listState,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start =
                                t.space5,
                            end =
                                t.space5,
                            top =
                                t.space5,
                            bottom =
                                t.space6,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            t.space3,
                        ),
                ) {
                    items(
                        items =
                            chunks,
                        key = {
                            "${it.offset}:${it.text.hashCode()}"
                        },
                    ) {
                        chunk ->

                        TtsReadingChunkV50(
                            chunk =
                                chunk,
                            active =
                                chunk.offset ==
                                    chunks
                                        .getOrNull(
                                            currentIndex,
                                        )
                                        ?.offset,
                        )
                    }
                }
            }
        }


        /* ------------------------------------------------------------------ */
        /* Bottom controls                                                    */
        /* ------------------------------------------------------------------ */

        TtsBottomControlBarV50(
            listening =
                listening,
            speechRate =
                speechRate,
            timerLabel =
                ttsTimerCompactLabelV50(
                    option =
                        sleepTimerOption,
                    remainingMillis =
                        sleepTimerRemainingMillis,
                    customMinutes =
                        customSleepTimerMinutes,
                    customRemainingMillis =
                        customSleepTimerRemainingMillis,
                ),
            onPanel = {
                settingsOpen =
                    true
            },
            onTimer = {
                sleepTimerOpen =
                    true
            },
            onSpeed = {
                speedOpen =
                    true
            },
            onTogglePlayback =
                onTogglePlayback,
            onStop =
                onStop,
        )
    }


    /* ---------------------------------------------------------------------- */
    /* Settings panel                                                        */
    /* ---------------------------------------------------------------------- */

    if (
        settingsOpen
    ) {
        TtsSettingsPanelV50(
            currentVoiceLabel =
                currentVoiceLabel,
            speechRate =
                speechRate,
            pitch =
                pitch,
            volume =
                volume,

            skipImageDescriptions =
                skipImageDescriptions,
            lockScreenControls =
                lockScreenControls,
            autoResumeCarBluetooth =
                autoResumeCarBluetooth,
            backgroundPlayback =
                backgroundPlayback,

            timerLabel =
                ttsTimerFullLabelV50(
                    option =
                        sleepTimerOption,
                    remainingMillis =
                        sleepTimerRemainingMillis,
                    customMinutes =
                        customSleepTimerMinutes,
                    customRemainingMillis =
                        customSleepTimerRemainingMillis,
                ),

            onDismiss = {
                settingsOpen =
                    false
            },

            onVoiceManager =
                onOpenVoiceManager,

            onPreviewVoice =
                onPreviewCurrentVoice,

            onRateChange =
                onSpeechRateChange,

            onPitchChange =
                onPitchChange,

            onVolumeChange =
                onVolumeChange,

            onSkipImageDescriptionsChange =
                onSkipImageDescriptionsChange,

            onLockScreenControlsChange =
                onLockScreenControlsChange,

            onAutoResumeCarBluetoothChange =
                onAutoResumeCarBluetoothChange,

            onBackgroundPlaybackChange =
                onBackgroundPlaybackChange,

            onTimer = {
                settingsOpen =
                    false

                sleepTimerOpen =
                    true
            },
        )
    }


    /* ---------------------------------------------------------------------- */
    /* Speed panel                                                           */
    /* ---------------------------------------------------------------------- */

    if (
        speedOpen
    ) {
        TtsSpeedPanelV50(
            current =
                speechRate,
            onDismiss = {
                speedOpen =
                    false
            },
            onSelect = {
                rate ->

                onSpeechRateChange(
                    rate,
                )

                speedOpen =
                    false
            },
        )
    }


    /* ---------------------------------------------------------------------- */
    /* Timer panel                                                           */
    /* ---------------------------------------------------------------------- */

    if (
        sleepTimerOpen
    ) {
        TtsSleepTimerPanelV50(
            option =
                sleepTimerOption,
            remainingMillis =
                sleepTimerRemainingMillis,
            customMinutes =
                customSleepTimerMinutes,
            customRemainingMillis =
                customSleepTimerRemainingMillis,
            onDismiss = {
                sleepTimerOpen =
                    false
            },
            onMinutes = {
                minutes ->

                onSleepTimerMinutes(
                    minutes,
                )

                sleepTimerOpen =
                    false
            },
            onEndOfChapter = {
                onSleepTimerEndOfChapter()

                sleepTimerOpen =
                    false
            },
            onCancelTimer = {
                onSleepTimerCancel()

                sleepTimerOpen =
                    false
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Status Bar                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsStatusBarV50(
    chapterTitle: String,
    bookProgress: Float,
    timeLabel: String,
    batteryPercent: Int,
    onBack: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                t.background,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        t.space4,
                    vertical =
                        t.space2,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            TtsIconButtonV50(
                icon =
                    Icons.Rounded.ArrowBack,
                description =
                    "返回阅读",
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
                        chapterTitle
                            .ifBlank {
                                "听书"
                            },
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
                        "全书 ${
                            (
                                bookProgress
                                    .coerceIn(
                                        0f,
                                        1f,
                                    ) *
                                    100f
                                )
                                .roundToInt()
                        }%",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.mutedForeground,
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End,
            ) {
                Text(
                    text =
                        timeLabel,
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        t.secondaryForeground,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        "${
                            batteryPercent.coerceIn(
                                0,
                                100,
                            )
                        }%",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.mutedForeground,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    1.dp,
                )
                .background(
                    t.border,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Reading Chunk                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsReadingChunkV50(
    chunk: ReaderTtsChunkV35,
    active: Boolean,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    if (active) {
                        t.accent
                    } else {
                        Color.Transparent
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    if (active) {
                        t.primary
                    } else {
                        Color.Transparent
                    },
                shape =
                    shape,
            )
            .padding(
                horizontal =
                    t.space3,
                vertical =
                    t.space3,
            ),
        verticalAlignment =
            Alignment.Top,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .width(
                        3.dp,
                    )
                    .height(
                        28.dp,
                    )
                    .background(
                        color =
                            t.gold,
                        shape =
                            RoundedCornerShape(
                                t.radiusSm,
                            ),
                    ),
            )

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )
        }

        Text(
            text =
                chunk.text,
            modifier =
                Modifier.weight(
                    1f,
                ),
            style =
                MaterialTheme.typography.bodyLarge,
            color =
                if (active) {
                    t.accentForeground
                } else {
                    t.secondaryForeground
                },
            fontFamily =
                FontFamily.Serif,
            fontWeight =
                if (active) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
            lineHeight =
                MaterialTheme.typography.bodyLarge
                    .lineHeight *
                    1.35f,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                           Bottom Control Bar                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsBottomControlBarV50(
    listening: Boolean,
    speechRate: Float,
    timerLabel: String,
    onPanel: () -> Unit,
    onTimer: () -> Unit,
    onSpeed: () -> Unit,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                t.background,
            )
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    1.dp,
                )
                .background(
                    t.border,
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        t.space3,
                    vertical =
                        t.space2,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            TtsControlButtonV50(
                icon =
                    Icons.Rounded.Settings,
                label =
                    "面板",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onPanel,
            )

            TtsControlButtonV50(
                icon =
                    Icons.Rounded.Timer,
                label =
                    timerLabel,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onTimer,
            )

            TtsControlButtonV50(
                icon =
                    Icons.Rounded.Speed,
                label =
                    ttsRateLabelV50(
                        speechRate,
                    ),
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onSpeed,
            )

            TtsControlButtonV50(
                icon =
                    if (listening) {
                        Icons.Rounded.PauseCircle
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                label =
                    if (listening) {
                        "暂停"
                    } else {
                        "继续"
                    },
                selected =
                    listening,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onTogglePlayback,
            )

            TtsControlButtonV50(
                icon =
                    Icons.Rounded.Stop,
                label =
                    "停止",
                destructive =
                    true,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onStop,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Control Button                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsControlButtonV50(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Column(
        modifier = modifier
            .height(
                58.dp,
            )
            .background(
                color =
                    when {
                        destructive -> {
                            t.destructive.copy(
                                alpha =
                                    0.08f,
                            )
                        }

                        selected -> {
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
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                t.space2,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                label,
            modifier =
                Modifier.size(
                    19.dp,
                ),
            tint =
                when {
                    destructive -> {
                        t.destructive
                    }

                    selected -> {
                        t.primary
                    }

                    else -> {
                        t.secondaryForeground
                    }
                },
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
                when {
                    destructive -> {
                        t.destructive
                    }

                    selected -> {
                        t.accentForeground
                    }

                    else -> {
                        t.secondaryForeground
                    }
                },
            maxLines =
                1,
            overflow =
                TextOverflow.Ellipsis,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Settings Panel                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsSettingsPanelV50(
    currentVoiceLabel: String,
    speechRate: Float,
    pitch: TtsPitchV50,
    volume: Float,

    skipImageDescriptions: Boolean,
    lockScreenControls: Boolean,
    autoResumeCarBluetooth: Boolean,
    backgroundPlayback: Boolean,

    timerLabel: String,

    onDismiss: () -> Unit,
    onVoiceManager: () -> Unit,
    onPreviewVoice: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (TtsPitchV50) -> Unit,
    onVolumeChange: (Float) -> Unit,

    onSkipImageDescriptionsChange: (Boolean) -> Unit,
    onLockScreenControlsChange: (Boolean) -> Unit,
    onAutoResumeCarBluetoothChange: (Boolean) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,

    onTimer: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Dialog(
        onDismissRequest =
            onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth =
                    false,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha =
                            0.30f,
                    ),
                )
                .clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication =
                        null,
                    onClick =
                        onDismiss,
                ),
            contentAlignment =
                Alignment.BottomCenter,
        ) {
            val shape =
                RoundedCornerShape(
                    topStart =
                        t.radiusXl,
                    topEnd =
                        t.radiusXl,
                )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max =
                            680.dp,
                    )
                    .background(
                        color =
                            t.background,
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
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            },
                        indication =
                            null,
                        onClick = {},
                    ),
                contentPadding =
                    PaddingValues(
                        start =
                            t.space4,
                        end =
                            t.space4,
                        top =
                            t.space3,
                        bottom =
                            t.space5,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        t.space4,
                    ),
            ) {
                item(
                    key =
                        "tts-panel-header",
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(
                                    1f,
                                ),
                        ) {
                            Text(
                                text =
                                    "听书面板",
                                style =
                                    MaterialTheme.typography.titleLarge,
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
                                    "声音、定时与朗读偏好",
                                style =
                                    MaterialTheme.typography.bodySmall,
                                color =
                                    t.mutedForeground,
                            )
                        }

                        TtsIconButtonV50(
                            icon =
                                Icons.Rounded.Close,
                            description =
                                "关闭听书面板",
                            onClick =
                                onDismiss,
                        )
                    }
                }


                /* ---------------------------------------------------------- */
                /* Timer                                                      */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-timer",
                ) {
                    TtsNavigationCardV50(
                        icon =
                            Icons.Rounded.Timer,
                        title =
                            "定时关闭",
                        subtitle =
                            "10 / 30 / 60 / 90 分钟、本章结束或自定义",
                        value =
                            timerLabel,
                        gold =
                            timerLabel !=
                                "不开启",
                        onClick =
                            onTimer,
                    )
                }


                /* ---------------------------------------------------------- */
                /* Voice                                                      */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-voice",
                ) {
                    Column {
                        TtsSectionTitleV50(
                            title =
                                "发音人",
                        )

                        Spacer(
                            Modifier.height(
                                t.space2,
                            ),
                        )

                        val cardShape =
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
                                        cardShape,
                                )
                                .border(
                                    width =
                                        1.dp,
                                    color =
                                        t.border,
                                    shape =
                                        cardShape,
                                )
                                .padding(
                                    t.space4,
                                ),
                        ) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(
                                            42.dp,
                                        )
                                        .background(
                                            color =
                                                t.accent,
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
                                            Icons.Rounded.Headphones,
                                        contentDescription =
                                            null,
                                        modifier =
                                            Modifier.size(
                                                21.dp,
                                            ),
                                        tint =
                                            t.primary,
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
                                            currentVoiceLabel
                                                .ifBlank {
                                                    "系统发音人"
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
                                            t.space1,
                                        ),
                                    )

                                    Text(
                                        text =
                                            "青叔 · 成熟男声 / 知遥 · 温柔女声 / 铁牛 · 浑厚男声 / 阿梨 · 清亮女声",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            t.mutedForeground,
                                        maxLines =
                                            2,
                                        overflow =
                                            TextOverflow.Ellipsis,
                                    )
                                }

                                Spacer(
                                    Modifier.width(
                                        t.space2,
                                    ),
                                )

                                Icon(
                                    imageVector =
                                        Icons.Rounded.ChevronRight,
                                    contentDescription =
                                        null,
                                    modifier =
                                        Modifier
                                            .clickable(
                                                onClick =
                                                    onVoiceManager,
                                            )
                                            .padding(
                                                t.space2,
                                            )
                                            .size(
                                                20.dp,
                                            ),
                                    tint =
                                        t.mutedForeground,
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    t.space3,
                                ),
                            )

                            TtsSecondaryActionV50(
                                icon =
                                    Icons.Rounded.MusicNote,
                                text =
                                    "试听当前发音人",
                                onClick =
                                    onPreviewVoice,
                            )
                        }
                    }
                }


                /* ---------------------------------------------------------- */
                /* Speed                                                      */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-speed",
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            TtsSectionTitleV50(
                                title =
                                    "语速",
                                modifier =
                                    Modifier.weight(
                                        1f,
                                    ),
                            )

                            Text(
                                text =
                                    ttsRateLabelV50(
                                        speechRate,
                                    ),
                                style =
                                    MaterialTheme.typography.labelLarge,
                                color =
                                    t.primary,
                                fontWeight =
                                    FontWeight.SemiBold,
                            )
                        }

                        Spacer(
                            Modifier.height(
                                t.space2,
                            ),
                        )

                        LazyRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    t.space2,
                                ),
                        ) {
                            items(
                                items =
                                    ttsRateOptionsV50(),
                                key = {
                                    it
                                },
                            ) {
                                rate ->

                                TtsChoiceChipV50(
                                    text =
                                        ttsRateLabelV50(
                                            rate,
                                        ),
                                    selected =
                                        ttsRateEqualsV50(
                                            speechRate,
                                            rate,
                                        ),
                                    onClick = {
                                        onRateChange(
                                            rate,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }


                /* ---------------------------------------------------------- */
                /* Pitch                                                      */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-pitch",
                ) {
                    Column {
                        TtsSectionTitleV50(
                            title =
                                "音调",
                        )

                        Spacer(
                            Modifier.height(
                                t.space2,
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
                            TtsPitchV50.entries
                                .forEach {
                                    option ->

                                    TtsChoiceChipV50(
                                        text =
                                            option.label,
                                        selected =
                                            option ==
                                                pitch,
                                        modifier =
                                            Modifier.weight(
                                                1f,
                                            ),
                                        onClick = {
                                            onPitchChange(
                                                option,
                                            )
                                        },
                                    )
                                }
                        }
                    }
                }


                /* ---------------------------------------------------------- */
                /* Volume                                                     */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-volume",
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            TtsSectionTitleV50(
                                title =
                                    "朗读音量",
                                modifier =
                                    Modifier.weight(
                                        1f,
                                    ),
                            )

                            Text(
                                text =
                                    "${
                                        (
                                            volume.coerceIn(
                                                0f,
                                                1f,
                                            ) *
                                                100f
                                            )
                                            .roundToInt()
                                    }%",
                                style =
                                    MaterialTheme.typography.labelLarge,
                                color =
                                    t.primary,
                                fontWeight =
                                    FontWeight.SemiBold,
                            )
                        }

                        Spacer(
                            Modifier.height(
                                t.space1,
                            ),
                        )

                        Text(
                            text =
                                "朗读音量独立保存，不改变系统媒体音量",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                t.mutedForeground,
                        )

                        Slider(
                            value =
                                volume.coerceIn(
                                    0f,
                                    1f,
                                ),
                            onValueChange =
                                onVolumeChange,
                            valueRange =
                                0f..1f,
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                SliderDefaults.colors(
                                    thumbColor =
                                        t.primary,
                                    activeTrackColor =
                                        t.primary,
                                    inactiveTrackColor =
                                        t.border,
                                    activeTickColor =
                                        Color.Transparent,
                                    inactiveTickColor =
                                        Color.Transparent,
                                ),
                        )
                    }
                }


                /* ---------------------------------------------------------- */
                /* Reader preferences                                         */
                /* ---------------------------------------------------------- */

                item(
                    key =
                        "tts-panel-reader-settings",
                ) {
                    Column {
                        TtsSectionTitleV50(
                            title =
                                "朗读设置",
                        )

                        Spacer(
                            Modifier.height(
                                t.space2,
                            ),
                        )

                        val groupShape =
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
                                        groupShape,
                                )
                                .border(
                                    width =
                                        1.dp,
                                    color =
                                        t.border,
                                    shape =
                                        groupShape,
                                )
                                .padding(
                                    horizontal =
                                        t.space3,
                                ),
                        ) {
                            TtsSwitchRowV50(
                                icon =
                                    Icons.Rounded.GraphicEq,
                                title =
                                    "跳过配图说明",
                                subtitle =
                                    "朗读正文时忽略图片说明文字",
                                checked =
                                    skipImageDescriptions,
                                onCheckedChange =
                                    onSkipImageDescriptionsChange,
                            )

                            TtsDividerV50()

                            TtsSwitchRowV50(
                                icon =
                                    Icons.Rounded.Lock,
                                title =
                                    "锁屏显示朗读控制",
                                subtitle =
                                    "复用通知栏播放控制",
                                checked =
                                    lockScreenControls,
                                required =
                                    true,
                                onCheckedChange =
                                    onLockScreenControlsChange,
                            )

                            TtsDividerV50()

                            TtsSwitchRowV50(
                                icon =
                                    Icons.Rounded.Headphones,
                                title =
                                    "连接车载蓝牙时自动继续",
                                subtitle =
                                    "重新连接车载蓝牙后恢复听书",
                                checked =
                                    autoResumeCarBluetooth,
                                onCheckedChange =
                                    onAutoResumeCarBluetoothChange,
                            )

                            TtsDividerV50()

                            TtsSwitchRowV50(
                                icon =
                                    Icons.Rounded.PlayArrow,
                                title =
                                    "后台播放",
                                subtitle =
                                    "离开阅读页后继续朗读",
                                checked =
                                    backgroundPlayback,
                                required =
                                    true,
                                onCheckedChange =
                                    onBackgroundPlaybackChange,
                            )
                        }
                    }
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Speed Panel                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsSpeedPanelV50(
    current: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    TtsBottomDialogV50(
        title =
            "语速",
        subtitle =
            "0.5x – 2.0x，每档 0.25x",
        onDismiss =
            onDismiss,
    ) {
        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            items(
                items =
                    ttsRateOptionsV50(),
                key = {
                    it
                },
            ) {
                rate ->

                TtsChoiceChipV50(
                    text =
                        ttsRateLabelV50(
                            rate,
                        ),
                    selected =
                        ttsRateEqualsV50(
                            current,
                            rate,
                        ),
                    onClick = {
                        onSelect(
                            rate,
                        )
                    },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Sleep Timer Panel                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsSleepTimerPanelV50(
    option: ReaderTtsSleepOptionV50,
    remainingMillis: Long,
    customMinutes: Int?,
    customRemainingMillis: Long,
    onDismiss: () -> Unit,
    onMinutes: (Int) -> Unit,
    onEndOfChapter: () -> Unit,
    onCancelTimer: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var customDialogOpen by
        rememberSaveable {
            mutableStateOf(
                false,
            )
        }


    TtsBottomDialogV50(
        title =
            "定时关闭",
        subtitle =
            ttsTimerFullLabelV50(
                option =
                    option,
                remainingMillis =
                    remainingMillis,
                customMinutes =
                    customMinutes,
                customRemainingMillis =
                    customSleepTimerRemainingMillis,
            ),
        onDismiss =
            onDismiss,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        t.space2,
                    ),
            ) {
                listOf(
                    10,
                    30,
                    60,
                )
                    .forEach {
                        minutes ->

                        TtsTimerChoiceV50(
                            title =
                                "$minutes 分钟",
                            selected =
                                customMinutes ==
                                    null &&
                                    when (minutes) {
                                        10 -> {
                                            option ==
                                                ReaderTtsSleepOptionV50.MINUTES_10
                                        }

                                        30 -> {
                                            option ==
                                                ReaderTtsSleepOptionV50.MINUTES_30
                                        }

                                        60 -> {
                                            option ==
                                                ReaderTtsSleepOptionV50.MINUTES_60
                                        }

                                        else -> {
                                            false
                                        }
                                    },
                            modifier =
                                Modifier.weight(
                                    1f,
                                ),
                            onClick = {
                                onMinutes(
                                    minutes,
                                )
                            },
                        )
                    }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        t.space2,
                    ),
            ) {
                TtsTimerChoiceV50(
                    title =
                        "90 分钟",
                    selected =
                        customMinutes ==
                            90,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick = {
                        onMinutes(
                            90,
                        )
                    },
                )

                TtsTimerChoiceV50(
                    title =
                        "播完本章后关闭",
                    selected =
                        customMinutes ==
                            null &&
                            option ==
                            ReaderTtsSleepOptionV50.END_OF_CHAPTER,
                    modifier =
                        Modifier.weight(
                            2f,
                        ),
                    onClick =
                        onEndOfChapter,
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        t.space2,
                    ),
            ) {
                TtsTimerChoiceV50(
                    title =
                        "自定义",
                    selected =
                        customMinutes !=
                            null &&
                            customMinutes !=
                            90,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick = {
                        customDialogOpen =
                            true
                    },
                )

                TtsTimerChoiceV50(
                    title =
                        "取消定时",
                    selected =
                        option ==
                            ReaderTtsSleepOptionV50.OFF &&
                            customMinutes ==
                            null,
                    destructive =
                        option !=
                            ReaderTtsSleepOptionV50.OFF ||
                            customMinutes !=
                            null,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onCancelTimer,
                )
            }
        }
    }


    if (
        customDialogOpen
    ) {
        TtsCustomTimerDialogV50(
            initialMinutes =
                customMinutes
                    ?.takeIf {
                        it !=
                            90
                    }
                    ?: 45,
            onDismiss = {
                customDialogOpen =
                    false
            },
            onConfirm = {
                minutes ->

                customDialogOpen =
                    false

                onMinutes(
                    minutes,
                )
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                          Custom Timer Dialog                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsCustomTimerDialogV50(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var minutesText by
        rememberSaveable(
            initialMinutes,
        ) {
            mutableStateOf(
                initialMinutes
                    .coerceIn(
                        1,
                        720,
                    )
                    .toString(),
            )
        }


    val minutes =
        minutesText
            .toIntOrNull()
            ?.coerceIn(
                1,
                720,
            )


    Dialog(
        onDismissRequest =
            onDismiss,
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
            Text(
                text =
                    "自定义定时",
                style =
                    MaterialTheme.typography.titleLarge,
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
                    "输入 1–720 分钟",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )

            val fieldShape =
                RoundedCornerShape(
                    t.radiusMd,
                )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        48.dp,
                    )
                    .background(
                        color =
                            t.input,
                        shape =
                            fieldShape,
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            fieldShape,
                    )
                    .padding(
                        horizontal =
                            t.space3,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value =
                        minutesText,
                    onValueChange = {
                        raw ->

                        minutesText =
                            raw.filter {
                                it.isDigit()
                            }
                                .take(
                                    3,
                                )
                    },
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    singleLine =
                        true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number,
                        ),
                    textStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            color =
                                t.foreground,
                        ),
                    cursorBrush =
                        SolidColor(
                            t.primary,
                        ),
                )

                Text(
                    text =
                        "分钟",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        t.mutedForeground,
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
                TtsDialogButtonV50(
                    text =
                        "取消",
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick =
                        onDismiss,
                )

                TtsDialogButtonV50(
                    text =
                        "开始定时",
                    primary =
                        true,
                    enabled =
                        minutes !=
                            null,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    onClick = {
                        minutes?.let(
                            onConfirm,
                        )
                    },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Bottom Dialog                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsBottomDialogV50(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Dialog(
        onDismissRequest =
            onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth =
                    false,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha =
                            0.30f,
                    ),
                )
                .clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication =
                        null,
                    onClick =
                        onDismiss,
                ),
            contentAlignment =
                Alignment.BottomCenter,
        ) {
            val shape =
                RoundedCornerShape(
                    topStart =
                        t.radiusXl,
                    topEnd =
                        t.radiusXl,
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color =
                            t.background,
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
                    .navigationBarsPadding()
                    .imePadding()
                    .clickable(
                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            },
                        indication =
                            null,
                        onClick = {},
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
                                MaterialTheme.typography.titleLarge,
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
                                subtitle,
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                t.mutedForeground,
                        )
                    }

                    TtsIconButtonV50(
                        icon =
                            Icons.Rounded.KeyboardArrowDown,
                        description =
                            "收起",
                        onClick =
                            onDismiss,
                    )
                }

                Spacer(
                    Modifier.height(
                        t.space4,
                    ),
                )

                content()
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                           Navigation Card                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun TtsNavigationCardV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    gold: Boolean,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    if (gold) {
                        t.goldContainer
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
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                t.space4,
            ),
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
                        if (gold) {
                            t.goldContainer
                        } else {
                            t.accent
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
                    icons.Rounded.ChevronRight,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        18.dp,
                    ),
                tint =
                    t.mutedForeground,
            )
        }

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                value,
            style =
                MaterialTheme.typography.labelMedium,
            color =
                if (gold) {
                    t.goldForeground
                } else {
                    t.secondaryForeground
                },
            maxLines =
                1,
        )

        Spacer(
            Modifier.width(
                t.space1,
            ),
        )

        Icon(
            imageVector =
                Icons.Rounded.ChevronRight,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    18.dp,
                ),
            tint =
                t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                          Switch Row + Divider                              */
/* -------------------------------------------------------------------------- */

/**
 * 听书设置面板的开关行。
 *
 * 注：原稿中 required = true 的两行带有"V3 必做"开发徽标，落地时已去掉，
 * 该参数保留仅为兼容调用方，不再渲染任何徽标。
 */
@Composable
private fun TtsSwitchRowV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    required: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { onCheckedChange(!checked) },
                )
                .padding(
                    vertical = t.space3,
                ),
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
                    22.dp,
                ),
            tint =
                t.secondaryForeground,
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
                    title,
                style =
                    MaterialTheme.typography.bodyLarge,
                color =
                    t.foreground,
            )

            Text(
                text =
                    subtitle,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Switch(
            checked =
                checked,
            onCheckedChange =
                onCheckedChange,
        )
    }
}

@Composable
private fun TtsDividerV50() {
    val t =
        LocalLanghuanUiTokens.current
    HorizontalDivider(
        color =
            t.border,
        thickness =
            1.dp,
    )
}
