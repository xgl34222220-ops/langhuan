package com.xiguli.langhuan.ui

import java.util.Locale

/** Stable technical decimals for reader controls and elapsed run status. */
internal fun stableOneDecimalV61(value: Double, suffix: String = ""): String =
    String.format(Locale.ROOT, "%.1f%s", value, suffix)
