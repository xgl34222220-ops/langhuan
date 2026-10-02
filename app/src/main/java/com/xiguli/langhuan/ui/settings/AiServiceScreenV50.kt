package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.TaskModelRoute
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


@Composable
internal fun AiServiceScreenV50(
    providerState: ProviderUiState,
    taskRoutes: Map<AiTaskType, TaskModelRoute>,
    connectionLatencyMs: Long?,
    testingProviderId: String?,
    bookSourceRouteLabel: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onTestConnection: (SavedProviderUi) -> Unit,
    onSwitchModel: (SavedProviderUi) -> Unit,
    onSelectProvider: (SavedProviderUi) -> Unit,
    onAddProvider: () -> Unit,
    onConfigureTaskRoute: (AiTaskType) -> Unit,
    onConfigureBookSourceRoute: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val activeProvider =
        providerState.activeProvider

    val connected =
        activeProvider !=
            null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding(),
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
        item(
            key =
                "ai-service-header",
        ) {
            AiServiceHeaderV50(
                connected =
                    connected,
                latencyMs =
                    connectionLatencyMs,
                onBack =
                    onBack,
            )
        }

        item(
            key =
                "ai-service-primary",
        ) {
            AiServicePrimaryCardV50(
                provider =
                    activeProvider,
                testing =
                    activeProvider?.id ==
                        testingProviderId,
                latencyMs =
                    connectionLatencyMs,
                onTest = {
                    activeProvider
                        ?.let(
                            onTestConnection,
                        )
                },
                onSwitchModel = {
                    activeProvider
                        ?.let(
                            onSwitchModel,
                        )
                },
            )
        }

        item(
            key =
                "ai-service-list-title",
        ) {
            Column {
                Text(
                    text =
                        "服务",
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
                        "点击即可切换",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )
            }
        }

        providerState.savedProviders
            .forEach {
                provider ->

                item(
                    key =
                        "ai-provider-${provider.id}",
                ) {
                    AiServiceProviderCardV50(
                        provider =
                            provider,
                        current =
                            provider.id ==
                                providerState.activeProviderId,
                        testing =
                            provider.id ==
                                testingProviderId,
                        onClick = {
                            onSelectProvider(
                                provider,
                            )
                        },
                    )
                }
            }

        item(
            key =
                "ai-service-add",
        ) {
            AiServiceAddButtonV50(
                onClick =
                    onAddProvider,
            )
        }

        item(
            key =
                "ai-service-routing",
        ) {
            Column {
                Text(
                    text =
                        "任务模型路由",
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
                        "不设置时继承当前服务",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )

                Spacer(
                    Modifier.height(
                        t.space3,
                    ),
                )

                AiServiceRoutingCardV50(
                    providers =
                        providerState.savedProviders,
                    taskRoutes =
                        taskRoutes,
                    bookSourceRouteLabel =
                        bookSourceRouteLabel,
                    onConfigureTaskRoute =
                        onConfigureTaskRoute,
                    onConfigureBookSourceRoute =
                        onConfigureBookSourceRoute,
                )
            }
        }

        item(
            key =
                "ai-service-note",
        ) {
            Text(
                text =
                    "一次运行开始后会冻结路由，写到一半不会突然换模型。API Key 只保存在本机。",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }
    }
}


@Composable
private fun AiServiceHeaderV50(
    connected: Boolean,
    latencyMs: Long?,
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
        AiServiceIconButtonV50(
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

        Text(
            text =
                "AI 服务",
            modifier =
                Modifier.weight(
                    1f,
                ),
            style =
                MaterialTheme.typography.headlineLarge,
            color =
                t.foreground,
            fontWeight =
                FontWeight.SemiBold,
        )

        AiServiceConnectionBadgeV50(
            connected =
                connected,
            latencyMs =
                latencyMs,
        )
    }
}


@Composable
private fun AiServicePrimaryCardV50(
    provider: SavedProviderUi?,
    testing: Boolean,
    latencyMs: Long?,
    onTest: () -> Unit,
    onSwitchModel: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusXl,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.accent,
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
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        44.dp,
                    )
                    .background(
                        color =
                            t.card,
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
                        Icons.Rounded.SmartToy,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            22.dp,
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
                        "主力服务",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        t.accentForeground,
                    fontWeight =
                        FontWeight.Medium,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        provider
                            ?.model
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: "尚未配置模型",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines =
                        1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                provider
                    ?.baseUrl
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        url ->

                        Spacer(
                            Modifier.height(
                                t.space1,
                            ),
                        )

                        Text(
                            text =
                                url,
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
        }

        if (
            provider !=
            null &&
            latencyMs !=
            null
        ) {
            Spacer(
                Modifier.height(
                    t.space3,
                ),
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Speed,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            16.dp,
                        ),
                    tint =
                        t.primary,
                )

                Spacer(
                    Modifier.width(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        "最近连接 ${latencyMs.coerceAtLeast(0L)} ms",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.secondaryForeground,
                )
            }
        }

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
            AiServiceActionButtonV50(
                icon =
                    Icons.Rounded.Refresh,
                text =
                    if (testing) {
                        "测试中…"
                    } else {
                        "测试连接"
                    },
                primary =
                    true,
                enabled =
                    provider !=
                        null &&
                        !testing,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onTest,
            )

            AiServiceActionButtonV50(
                icon =
                    Icons.Rounded.Edit,
                text =
                    "切换模型",
                primary =
                    false,
                enabled =
                    provider !=
                        null &&
                        !testing,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onSwitchModel,
            )
        }
    }
}


@Composable
private fun AiServiceProviderCardV50(
    provider: SavedProviderUi,
    current: Boolean,
    testing: Boolean,
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
                    if (current) {
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
                    if (current) {
                        t.primary
                    } else {
                        t.border
                    },
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
                        if (current) {
                            t.card
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
            if (testing) {
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
                        Icons.Rounded.CloudDone,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            20.dp,
                        ),
                    tint =
                        if (current) {
                            t.primary
                        } else {
                            t.secondaryForeground
                        },
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
                    provider.name
                        .ifBlank {
                            "AI 服务"
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
                    provider.model
                        .ifBlank {
                            "未选择模型"
                        },
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

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                if (current) {
                    "当前"
                } else {
                    "未测试"
                },
            modifier = Modifier
                .background(
                    color =
                        if (current) {
                            t.accent
                        } else {
                            t.input
                        },
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
                )
                .padding(
                    horizontal =
                        t.space2,
                    vertical =
                        t.space1,
                ),
            style =
                MaterialTheme.typography.labelSmall,
            color =
                if (current) {
                    t.accentForeground
                } else {
                    t.mutedForeground
                },
            fontWeight =
                if (current) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
        )
    }
}


@Composable
private fun AiServiceAddButtonV50(
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
            .height(
                48.dp,
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
            .clickable(
                onClick =
                    onClick,
            ),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                Icons.Rounded.Add,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    19.dp,
                ),
            tint =
                t.primary,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                "添加 AI 服务",
            style =
                MaterialTheme.typography.labelLarge,
            color =
                t.primary,
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}


@Composable
private fun AiServiceRoutingCardV50(
    providers: List<SavedProviderUi>,
    taskRoutes: Map<AiTaskType, TaskModelRoute>,
    bookSourceRouteLabel: String,
    onConfigureTaskRoute: (AiTaskType) -> Unit,
    onConfigureBookSourceRoute: () -> Unit,
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
                horizontal =
                    t.space3,
            ),
    ) {
        AiServiceRouteRowV50(
            icon =
                Icons.Rounded.Route,
            title =
                "长篇规划",
            value =
                aiRouteLabelV50(
                    route =
                        taskRoutes[
                            AiTaskType.AUTONOMOUS_PLANNER
                        ],
                    providers =
                        providers,
                ),
            onClick = {
                onConfigureTaskRoute(
                    AiTaskType.AUTONOMOUS_PLANNER,
                )
            },
        )

        AiServiceDividerV50()

        AiServiceRouteRowV50(
            icon =
                Icons.Rounded.AutoAwesome,
            title =
                "正文",
            value =
                aiRouteLabelV50(
                    route =
                        taskRoutes[
                            AiTaskType.PROSE_AUTHOR
                        ],
                    providers =
                        providers,
                ),
            onClick = {
                onConfigureTaskRoute(
                    AiTaskType.PROSE_AUTHOR,
                )
            },
        )

        AiServiceDividerV50()

        AiServiceRouteRowV50(
            icon =
                Icons.Rounded.Check,
            title =
                "审查",
            value =
                aiRouteLabelV50(
                    route =
                        taskRoutes[
                            AiTaskType.EDITOR_REVIEW
                        ],
                    providers =
                        providers,
                ),
            onClick = {
                onConfigureTaskRoute(
                    AiTaskType.EDITOR_REVIEW,
                )
            },
        )

        AiServiceDividerV50()

        AiServiceRouteRowV50(
            icon =
                Icons.Rounded.SmartToy,
            title =
                "书源生成",
            value =
                bookSourceRouteLabel
                    .ifBlank {
                        "继承全局默认"
                    },
            onClick =
                onConfigureBookSourceRoute,
        )
    }
}


@Composable
private fun AiServiceRouteRowV50(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                vertical =
                    t.space3,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(
                    36.dp,
                )
                .background(
                    color =
                        t.input,
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
                    t.secondaryForeground,
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Text(
            text =
                title,
            modifier =
                Modifier.weight(
                    1f,
                ),
            style =
                MaterialTheme.typography.bodyLarge,
            color =
                t.foreground,
            fontWeight =
                FontWeight.Medium,
        )

        Text(
            text =
                value,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                if (
                    value ==
                    "继承全局默认"
                ) {
                    t.mutedForeground
                } else {
                    t.secondaryForeground
                },
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


@Composable
private fun AiServiceConnectionBadgeV50(
    connected: Boolean,
    latencyMs: Long?,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusXl,
        )

    Row(
        modifier = Modifier
            .background(
                color =
                    if (connected) {
                        t.accent
                    } else {
                        t.input
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
                horizontal =
                    t.space2,
                vertical =
                    t.space1,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (connected) {
                    Icons.Rounded.CloudDone
                } else {
                    Icons.Rounded.SmartToy
                },
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    14.dp,
                ),
            tint =
                if (connected) {
                    t.primary
                } else {
                    t.mutedForeground
                },
        )

        Spacer(
            Modifier.width(
                t.space1,
            ),
        )

        Text(
            text =
                when {
                    !connected -> {
                        "未连接"
                    }

                    latencyMs !=
                        null -> {
                        "已连接 ${latencyMs.coerceAtLeast(0L)} ms"
                    }

                    else -> {
                        "已连接"
                    }
                },
            style =
                MaterialTheme.typography.labelSmall,
            color =
                if (connected) {
                    t.accentForeground
                } else {
                    t.mutedForeground
                },
            fontWeight =
                if (connected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
        )
    }
}


@Composable
private fun AiServiceActionButtonV50(
    icon: ImageVector,
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Row(
        modifier = modifier
            .height(
                46.dp,
            )
            .background(
                color =
                    when {
                        !enabled -> {
                            t.input
                        }

                        primary -> {
                            t.primary
                        }

                        else -> {
                            t.accent
                        }
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    when {
                        !enabled -> {
                            t.border
                        }

                        primary -> {
                            t.primary
                        }

                        else -> {
                            t.border
                        }
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
                    t.space3,
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
                    18.dp,
                ),
            tint =
                when {
                    !enabled -> {
                        t.mutedForeground
                    }

                    primary -> {
                        t.card
                    }

                    else -> {
                        t.accentForeground
                    }
                },
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                text,
            style =
                MaterialTheme.typography.labelLarge,
            color =
                when {
                    !enabled -> {
                        t.mutedForeground
                    }

                    primary -> {
                        t.card
                    }

                    else -> {
                        t.accentForeground
                    }
                },
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}


@Composable
private fun AiServiceDividerV50() {
    val t =
        LocalLanghuanUiTokens.current

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


@Composable
private fun AiServiceIconButtonV50(
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


private fun aiRouteLabelV50(
    route: TaskModelRoute?,
    providers: List<SavedProviderUi>,
): String {
    if (
        route ==
        null
    ) {
        return "继承全局默认"
    }

    val provider =
        providers.firstOrNull {
            it.id ==
                route.providerId
        }

    if (
        provider ==
        null
    ) {
        return "继承全局默认"
    }

    return buildString {
        append(
            provider.name,
        )

        if (
            route.modelId.isNotBlank()
        ) {
            append(
                " · ",
            )

            append(
                route.modelId,
            )
        }
    }
}
