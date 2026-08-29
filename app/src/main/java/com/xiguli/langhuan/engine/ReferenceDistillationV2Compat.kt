package com.xiguli.langhuan.engine

/** Temporary source-compatible helper while the worker and report store evolve together in V2. */
fun ReferenceDistillationReport.kindCounts(@Suppress("UNUSED_PARAMETER") report: ReferenceDistillationReport): Map<String, Int> =
    (retrievalItems.ifEmpty { items } + items)
        .map { item -> if (item.kind.equals("DNA", true)) item.copy(kind = "STYLE") else item.copy(kind = item.kind.uppercase()) }
        .distinctBy { item -> "${item.kind}|${item.dimension.uppercase()}|${item.value.lowercase().replace(Regex("\\s+"), "").take(200)}" }
        .groupingBy { it.kind }
        .eachCount()
