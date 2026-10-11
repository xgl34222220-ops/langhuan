package com.xiguli.langhuan.ui

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live 书源校验 of the bundled real Legado source lists (search → detail/toc → first chapter), the same
 * pipeline as 书源管理 →「校验书源」. It needs the public internet, so it only runs when
 * LANGHUAN_LIVE_SOURCES=1 (workflow 「Source live diagnostic」); the report is written to
 * app/build/reports/source-live-diagnostic.md. Results depend on where the runner is: many Chinese
 * novel sites block or time out from overseas data centres.
 */
class SourceLiveDiagnosticV97Test {
    @Test fun liveSourcesPerStage() = runBlocking {
        assumeTrue(System.getenv("LANGHUAN_LIVE_SOURCES") == "1")
        val keyword = System.getenv("LANGHUAN_LIVE_KEYWORD")?.takeIf { it.isNotBlank() } ?: "斗罗大陆"
        val files = listOf("legado/xiu2-shuyuan.json", "legado/kooofu-biquge.json", "legado/yolo52-shuyuan.json")
        val rows = ArrayList<String>()
        val counts = LinkedHashMap<SourceCheckStatusV97, Int>()
        for (file in files) {
            val raw = requireNotNull(javaClass.classLoader!!.getResource(file)).readText()
            val sources = parseBookSourcesV36(raw).sources
            val results = java.util.Collections.synchronizedList(ArrayList<Pair<BookSourceV36, SourceCheckResultV97>>())
            checkSourcesV97(sources, keyword, concurrency = 8, check = { checkSourceV97(it, keyword) }) { result ->
                results += sources.first { it.id == result.sourceId } to result
            }
            for ((source, result) in results.sortedBy { it.second.status.ordinal }) {
                counts[result.status] = (counts[result.status] ?: 0) + 1
                rows += "| ${file.substringAfter('/')} | ${source.name.replace("|", "/")} | ${source.baseUrl} | ${result.status.label} | " +
                    "${result.elapsedMs} ms | ${result.detail.replace("|", "/").replace("\n", " ").take(160)} |"
            }
        }
        val report = buildString {
            appendLine("# 书源实网校验（关键词「$keyword」）")
            appendLine()
            appendLine(counts.entries.joinToString("，") { "${it.key.label} ${it.value}" })
            appendLine()
            appendLine("| 合集 | 书源 | 地址 | 结果 | 用时 | 说明 |")
            appendLine("| --- | --- | --- | --- | ---: | --- |")
            rows.forEach(::appendLine)
        }
        File("build/reports").mkdirs()
        File("build/reports/source-live-diagnostic.md").writeText(report)
        println(report)
    }
}
