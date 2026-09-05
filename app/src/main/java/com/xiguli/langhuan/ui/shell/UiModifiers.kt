package com.xiguli.langhuan.ui.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll as composeVerticalScroll
import androidx.compose.ui.Modifier

/** Keeps long editor dialogs scrollable without leaking foundation imports across the UI file. */
internal fun Modifier.verticalScroll(state: ScrollState): Modifier = this.composeVerticalScroll(state)
