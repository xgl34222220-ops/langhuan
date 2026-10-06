package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


/**
 * AI 生成书源 V50。
 *
 * AI 执行状态直接复用 OnlineBooksStateV36：
 * aiProviderLabel / aiSteps / aiRunning / aiReport / aiError / aiStopped / aiLastUseBrowser /
 * aiSavedSourceId / aiSavedSourceName。
 *
 * 真正生成由 OnlineBooksViewModelV36.buildWithAi() 完成。
 */
@Composable
internal fun AiBookSourceScreenV50(
    state: OnlineBooksStateV36,
    siteUrl: String,
    testBookName: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSiteUrlChange: (String) -> Unit,
    onTestBookNameChange: (String) -> Unit,
    onConfigureAi: () -> Unit,
    onStart: (String, String) -> Unit,
    onStartWithBrowser: (String, String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onOpenSavedSource: (String) -> Unit = { onBack() },
) {
    val t = LocalLanghuanUiTokens.current
    val editable = !state.aiRunning && state.aiReport == null

    val leaveScreen = {
        if (state.aiRunning) onCancel()
        onBack()
    }
    BackHandler(onBack = leaveScreen)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(
            start = t.space4,
            end = t.space4,
            top = t.space3,
            bottom = t.space6,
        ),
        verticalArrangement = Arrangement.spacedBy(t.space4),
    ) {
        item("ai-source-header") {
            AiSourceHeaderV50(onBack = leaveScreen)
        }

        item("ai-source-inputs") {
            AiSourceInputCardV50(
                siteUrl = siteUrl,
                testBookName = testBookName,
                editable = editable,
                onSiteUrlChange = onSiteUrlChange,
                onTestBookNameChange = onTestBookNameChange,
            )
        }

        item("ai-source-provider") {
            AiSourceProviderCardV50(
                providerLabel = displayedAiProviderLabelV80(state),
                enabled = !state.aiRunning,
                onConfigureAi = onConfigureAi,
            )
        }

        item("ai-source-capabilities") {
            AiSourceCapabilityCardV50()
        }

        if (state.aiSteps.isNotEmpty()) {
            item("ai-source-progress") {
                AiSourceProgressCardV50(steps = state.aiSteps)
            }
        }

        state.aiError
            ?.takeIf { it.isNotBlank() }
            ?.let { error ->
                item("ai-source-error") {
                    AiSourceErrorCardV50(message = error)
                }
            }

        if (state.aiStopped) {
            item("ai-source-stopped") {
                AiSourceStoppedCardV65()
            }
        }

        state.aiSavedSourceName?.let { sourceName ->
            item("ai-source-saved") {
                AiSourceSavedCardV68(sourceName)
            }
        }

        state.aiReport?.let { report ->
            item("ai-source-report") {
                AiSourceReportCardV50(report = report)
            }
        }

        item("ai-source-main-action") {
            when {
                state.aiRunning -> {
                    AiSourceMainButtonV50(
                        icon = Icons.Rounded.Stop,
                        text = "停止生成",
                        primary = false,
                        enabled = true,
                        onClick = onCancel,
                    )
                }

                state.aiReport != null -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.space2),
                    ) {
                        AiSourceMainButtonV50(
                            icon = Icons.Rounded.Check,
                            text = aiSourceSaveActionLabelV68(state),
                            primary = true,
                            enabled = true,
                            onClick = onSave,
                        )
                        AiSourceMainButtonV50(
                            icon = Icons.Rounded.AutoAwesome,
                            text = "重新生成",
                            primary = false,
                            enabled = siteUrl.isNotBlank() && testBookName.isNotBlank(),
                            onClick = {
                                val browser = state.aiReport.source.useBrowser
                                onCancel()
                                if (browser) onStartWithBrowser(siteUrl, testBookName) else onStart(siteUrl, testBookName)
                            },
                        )
                    }
                }

                state.aiSavedSourceName != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(t.space2)) {
                        AiSourceMainButtonV50(
                            icon = Icons.Rounded.Source,
                            text = "返回书源管理查看",
                            primary = true,
                            enabled = true,
                            onClick = {
                                state.aiSavedSourceId?.let(onOpenSavedSource) ?: onBack()
                            },
                        )
                        aiSourceStartActionsV67(state, siteUrl, testBookName).forEach { action ->
                            AiSourceMainButtonV50(
                                icon = Icons.Rounded.AutoAwesome,
                                text = action.label,
                                primary = false,
                                enabled = siteUrl.isNotBlank() && testBookName.isNotBlank(),
                                onClick = {
                                    if (action.useBrowser) onStartWithBrowser(siteUrl, testBookName)
                                    else onStart(siteUrl, testBookName)
                                },
                            )
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(t.space2)) {
                        aiSourceStartActionsV67(state, siteUrl, testBookName).forEachIndexed { index, action ->
                            AiSourceMainButtonV50(
                                icon = Icons.Rounded.AutoAwesome,
                                text = action.label,
                                primary = index == 0,
                                enabled = siteUrl.isNotBlank() && testBookName.isNotBlank(),
                                onClick = {
                                    if (action.useBrowser) onStartWithBrowser(siteUrl, testBookName)
                                    else onStart(siteUrl, testBookName)
                                },
                            )
                        }
                        Text(
                            "网站需要网页验证或动态加载时，可使用浏览器模式；验证通过后自动继续。",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

internal data class AiSourceStartActionV67(
    val label: String,
    val useBrowser: Boolean,
)

internal fun aiCanResumeValidatedRulesV77(
    state: OnlineBooksStateV36,
    siteUrl: String,
    testBookName: String,
    useBrowser: Boolean,
): Boolean = state.aiCanResumeValidatedRules &&
    state.aiValidationRetryInput == aiValidationRetryInputV77(siteUrl, testBookName, useBrowser)

/** Keeps the failed attempt's transport first and promises rule reuse only for its exact input. */
internal fun aiSourceStartActionsV67(
    state: OnlineBooksStateV36,
    siteUrl: String,
    testBookName: String,
): List<AiSourceStartActionV67> =
    when {
        state.aiError.isNullOrBlank() || state.aiLastUseBrowser == null -> listOf(
            AiSourceStartActionV67("开始生成", useBrowser = false),
            AiSourceStartActionV67("浏览器模式生成", useBrowser = true),
        )

        state.aiLastUseBrowser == true -> listOf(
            AiSourceStartActionV67(
                if (aiCanResumeValidatedRulesV77(state, siteUrl, testBookName, useBrowser = true)) "保留已通过规则重试（浏览器）" else "重试浏览器模式",
                useBrowser = true,
            ),
            AiSourceStartActionV67("改用普通模式", useBrowser = false),
        )

        else -> listOf(
            AiSourceStartActionV67(
                if (aiCanResumeValidatedRulesV77(state, siteUrl, testBookName, useBrowser = false)) "保留已通过规则重试（普通）" else "重试普通模式",
                useBrowser = false,
            ),
            AiSourceStartActionV67("改用浏览器模式", useBrowser = true),
        )
    }

internal fun aiSourceSaveActionLabelV68(state: OnlineBooksStateV36): String {
    val report = state.aiReport ?: return "保存书源"
    val base = if (report.source.enabledExplore) "保存书源" else "保存搜索书源"
    return if (state.aiError.isNullOrBlank()) base else "重试$base"
}


@Composable
private fun AiSourceHeaderV50(onBack: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(t.radiusMd)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(t.card, shape)
                .border(1.dp, t.border, shape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp),
                tint = t.secondaryForeground,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column {
            Text(
                text = "AI 生成书源",
                style = MaterialTheme.typography.headlineLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = "理解网站结构，生成规则并逐项实测",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}


@Composable
private fun AiSourceInputCardV50(
    siteUrl: String,
    testBookName: String,
    editable: Boolean,
    onSiteUrlChange: (String) -> Unit,
    onTestBookNameChange: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
    ) {
        Text(
            text = "网站地址",
            style = MaterialTheme.typography.labelLarge,
            color = t.secondaryForeground,
        )
        Spacer(Modifier.height(t.space2))
        AiSourceTextFieldV50(
            value = siteUrl,
            placeholder = "https://example.com",
            editable = editable,
            keyboardType = KeyboardType.Uri,
            onValueChange = onSiteUrlChange,
        )
        Spacer(Modifier.height(t.space4))
        Text(
            text = "该站能搜到的一本书名",
            style = MaterialTheme.typography.labelLarge,
            color = t.secondaryForeground,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = "用于真实搜索、目录和正文验证",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space2))
        AiSourceTextFieldV50(
            value = testBookName,
            placeholder = "输入一本确定存在的书",
            editable = editable,
            keyboardType = KeyboardType.Text,
            onValueChange = onTestBookNameChange,
        )
    }
}


@Composable
private fun AiSourceProviderCardV50(
    providerLabel: String?,
    enabled: Boolean,
    onConfigureAi: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
    ) {
        Text(
            text = "AI 配置",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(t.accent, RoundedCornerShape(t.radiusMd))
                    .border(1.dp, t.border, RoundedCornerShape(t.radiusMd)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = t.primary,
                )
            }
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "主力服务",
                        style = MaterialTheme.typography.bodyLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(t.space2))
                    Text(
                        text = "PRIMARY",
                        modifier = Modifier
                            .background(t.goldContainer, RoundedCornerShape(t.radiusSm))
                            .border(1.dp, t.border, RoundedCornerShape(t.radiusSm))
                            .padding(horizontal = t.space2, vertical = t.space1),
                        style = MaterialTheme.typography.labelSmall,
                        color = t.goldForeground,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(t.space1))
                Text(
                    text = providerLabel ?: "尚未配置已保存的服务与模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (providerLabel != null) {
                    Spacer(Modifier.height(t.space1))
                    Text(
                        text = "使用已保存的服务与模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.secondaryForeground,
                    )
                }
            }
            Text(
                text = "设置",
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onConfigureAi)
                    .padding(t.space2),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) t.primary else t.mutedForeground,
            )
        }
    }
}


@Composable
private fun AiSourceCapabilityCardV50() {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
    ) {
        Text(
            text = "将验证的能力",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            listOf("搜索", "目录", "正文").forEach { label ->
                AiSourceCapabilityChipV50(
                    text = label,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            listOf("发现", "翻页").forEach { label ->
                AiSourceCapabilityChipV50(
                    text = label,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(t.space3))
        Text(
            text = "只保留网页中真实存在且验证通过的入口。网站没有的分类与榜单，不会编造。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}


@Composable
private fun AiSourceProgressCardV50(steps: List<AiSourceStepV37>) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
        verticalArrangement = Arrangement.spacedBy(t.space3),
    ) {
        Text(
            text = "生成与验证",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            when (step.ok) {
                                true -> t.accent
                                false -> t.destructive.copy(alpha = 0.08f)
                                null -> t.input
                            },
                            RoundedCornerShape(t.radiusSm),
                        )
                        .border(1.dp, t.border, RoundedCornerShape(t.radiusSm)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (step.ok) {
                        true -> {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = t.primary,
                            )
                        }
                        false -> {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = t.destructive,
                            )
                        }
                        null -> {
                            if (!step.completed) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = t.primary,
                                    strokeWidth = 1.5.dp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(t.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${step.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.foreground,
                        fontWeight = FontWeight.Medium,
                    )
                    if (step.detail.isNotBlank()) {
                        Spacer(Modifier.height(t.space1))
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.ok == false) {
                                t.destructive
                            } else {
                                t.mutedForeground
                            },
                        )
                    }
                    val details = step.details
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    if (details.isNotEmpty()) {
                        Spacer(Modifier.height(t.space1))
                        Text(
                            text = details.take(3).joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun AiSourceReportCardV50(report: AiSourceReportV37) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.accent, shape)
            .border(1.dp, t.border, shape)
            .padding(t.space4),
    ) {
        Text(
            text = "规则已生成",
            style = MaterialTheme.typography.titleMedium,
            color = t.accentForeground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = report.source.name,
            style = MaterialTheme.typography.titleLarge,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = "搜索返回 ${report.searchCount} 本 · 抽样《${report.bookName}》 · 目录 ${report.chapterCount} 章",
            style = MaterialTheme.typography.bodySmall,
            color = t.secondaryForeground,
        )
        Spacer(Modifier.height(t.space3))
        Text(
            text = if (report.discoveryLabels.isEmpty()) {
                "没有保存未验证的发现入口"
            } else {
                "已验证发现：${report.discoveryLabels.joinToString("、")}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = t.goldForeground,
        )
        report.readingWarnings.take(3).forEach { warning ->
            Spacer(Modifier.height(t.space1))
            Text(
                text = warning,
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
        }
    }
}


@Composable
private fun AiSourceStoppedCardV65() {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.accent, RoundedCornerShape(t.radiusMd))
            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
            .padding(t.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.primary,
        )
        Spacer(Modifier.width(t.space2))
        Column(verticalArrangement = Arrangement.spacedBy(t.space1)) {
            Text(
                text = "生成已停止",
                style = MaterialTheme.typography.labelLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "网站地址和测试书名已保留，可重新选择普通或浏览器模式。",
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
            )
        }
    }
}

@Composable
private fun AiSourceSavedCardV68(sourceName: String) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.accent, RoundedCornerShape(t.radiusMd))
            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
            .padding(t.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.primary,
        )
        Spacer(Modifier.width(t.space2))
        Column(verticalArrangement = Arrangement.spacedBy(t.space1)) {
            Text(
                text = "书源已保存",
                style = MaterialTheme.typography.labelLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "“$sourceName”已写入书源管理，可继续生成或返回使用。",
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
            )
        }
    }
}


@Composable
private fun AiSourceErrorCardV50(message: String) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                t.destructive.copy(alpha = 0.08f),
                RoundedCornerShape(t.radiusMd),
            )
            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd))
            .padding(t.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.destructive,
        )
        Spacer(Modifier.width(t.space2))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = t.destructive,
        )
    }
}


@Composable
private fun AiSourceTextFieldV50(
    value: String,
    placeholder: String,
    editable: Boolean,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(t.input, shape)
            .border(1.dp, t.border, shape)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.width(t.space2))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = t.foreground,
                ),
                cursorBrush = SolidColor(t.primary),
            )
        }
    }
}


@Composable
private fun AiSourceCapabilityChipV50(
    text: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = modifier
            .height(38.dp)
            .background(t.accent, RoundedCornerShape(t.radiusMd))
            .border(1.dp, t.border, RoundedCornerShape(t.radiusMd)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.accentForeground,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
private fun AiSourceMainButtonV50(
    icon: ImageVector,
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                when {
                    !enabled -> t.input
                    primary -> t.primary
                    else -> t.card
                },
                shape,
            )
            .border(
                1.dp,
                when {
                    !enabled -> t.border
                    primary -> t.primary
                    else -> t.border
                },
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = t.space4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> t.mutedForeground
                primary -> t.card
                else -> t.primary
            },
        )
        Spacer(Modifier.width(t.space2))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> t.mutedForeground
                primary -> t.card
                else -> t.foreground
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}
