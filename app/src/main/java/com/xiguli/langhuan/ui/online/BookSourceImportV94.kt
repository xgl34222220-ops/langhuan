package com.xiguli.langhuan.ui

import kotlinx.serialization.json.JsonObject
import java.net.URLDecoder

/*
 * V94 书源导入/制作的容错层。
 *
 * 真实的阅读（Legado）书源里，searchUrl / 发现地址的请求选项大多写成 JavaScript 对象字面量：
 *   /modules/article/search.php,{'charset':'gbk','method':'POST','body':'searchkey={{key}}'}
 * 旧解析器把它当严格 JSON 处理，单引号直接报 “Unexpected JSON token”，导致大多数书源被整条跳过；
 * 封面、简介这类非关键字段里的一小段 @js: 也会让整个书源被拒绝。
 *
 * 这里只做“无损或明确告知”的整理：
 *  - 请求选项与 header 接受单引号/无引号键的对象字面量；
 *  - 非关键字段（作者、封面、简介、最新章节、正文/目录下一页）如果依赖脚本、JSONPath 或 XPath，清空并给出提示；
 *  - 关键字段（搜索列表/书名/链接、目录、正文）依赖脚本时仍然跳过，并写明原因；
 *  - 无法解析的发现分类只停用发现，不再连累可用的搜索。
 */

/** Strips a UTF-8 BOM / zero-width prefix that `String.trim()` keeps. */
internal fun normalizeSourceTextV94(raw: String): String =
    raw.trim().trimStart('\uFEFF', '\u200B').trim()

/**
 * Converts a JavaScript-style object literal (single-quoted strings) into strict JSON text.
 * Double-quoted strings are copied as-is; single-quoted strings are re-quoted with escapes.
 */
internal fun jsObjectLiteralToJsonV94(text: String): String {
    val out = StringBuilder(text.length + 8)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when (c) {
            '"' -> {
                out.append(c)
                i++
                while (i < text.length) {
                    val d = text[i]
                    out.append(d)
                    i++
                    if (d == '\\' && i < text.length) { out.append(text[i]); i++ } else if (d == '"') break
                }
            }
            '\'' -> {
                out.append('"')
                i++
                while (i < text.length) {
                    val d = text[i]
                    i++
                    when {
                        d == '\\' && i < text.length -> {
                            val next = text[i]
                            i++
                            if (next == '\'') out.append('\'') else out.append('\\').append(next)
                        }
                        d == '\'' -> break
                        d == '"' -> out.append("\\\"")
                        d == '\n' -> out.append("\\n")
                        else -> out.append(d)
                    }
                }
                out.append('"')
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}

/** Request options / header objects: strict JSON first, then the Legado object-literal form. */
internal fun parseSourceOptionsV94(text: String): JsonObject {
    val trimmed = text.trim()
    val strict = runCatching { BookSourceJsonV36.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
    if (strict != null) return strict
    return runCatching { BookSourceJsonV36.parseToJsonElement(jsObjectLiteralToJsonV94(trimmed)) as? JsonObject }
        .getOrNull() ?: throw IllegalArgumentException("请求选项必须是 JSON 对象（例如 {\"method\":\"POST\",\"body\":\"q={{key}}\"}）")
}

/** Unwraps `legado://import/bookSource?src=…` / `yuedu://booksource/importonline?src=…` share links. */
internal fun sourceImportUrlV94(input: String): String {
    val text = normalizeSourceTextV94(input)
    val scheme = text.substringBefore("://", "").lowercase()
    if (scheme in setOf("legado", "yuedu")) {
        val src = text.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("src=") }?.removePrefix("src=")
            ?: throw IllegalArgumentException("分享链接里没有书源地址（src=…）")
        return URLDecoder.decode(src, "UTF-8").trim()
    }
    return text
}

/** True when the pasted text is a link to download rather than JSON to parse. */
internal fun sourceInputIsUrlV94(input: String): Boolean {
    val text = normalizeSourceTextV94(input)
    if (text.startsWith("{") || text.startsWith("[")) return false
    val lower = text.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower.startsWith("legado://") || lower.startsWith("yuedu://")
}

/** Friendly message for a JSON syntax failure; never leaks a raw parser stack. */
internal fun sourceJsonErrorV94(error: Throwable): String {
    val message = error.message.orEmpty()
    val offset = Regex("offset (\\d+)").find(message)?.groupValues?.get(1)
    return when {
        message.contains("4 MiB") || message.contains("500 个") -> message
        offset != null -> "不是有效的书源 JSON（第 $offset 个字符附近有语法错误）。请确认复制完整，书源应以 [ 或 { 开头。"
        message.isNotBlank() && !message.contains("Unexpected JSON token") -> message.take(160)
        else -> "不是有效的书源 JSON。请确认复制完整，书源应以 [ 或 { 开头。"
    }
}

/** Why a rule cannot run in the static HTML engine, or null when it can. */
internal fun sourceRuleUnsupportedV94(rule: String): String? {
    val text = rule.trim()
    if (text.isEmpty()) return null
    return when {
        text.length > 8192 -> "规则过长"
        text.contains("<js>", true) || text.contains("@js:", true) || text.startsWith("{{") || text.contains("java.") -> "JavaScript"
        text.startsWith("@json:", true) || text.startsWith("$.") || text.startsWith("$[") -> "JSON 接口"
        text.startsWith("//") || text.startsWith("@xpath:", true) -> "XPath"
        else -> null
    }
}

/** A field that can be dropped with a notice instead of rejecting the whole source. */
private data class OptionalRuleV94(val label: String, val read: (BookSourceV36) -> String, val clear: (BookSourceV36) -> BookSourceV36)

private val OPTIONAL_RULES_V94 = listOf(
    OptionalRuleV94("搜索作者", { it.searchAuthor }, { it.copy(searchAuthor = "") }),
    OptionalRuleV94("搜索封面", { it.searchCover }, { it.copy(searchCover = "") }),
    OptionalRuleV94("搜索简介", { it.searchIntro }, { it.copy(searchIntro = "") }),
    OptionalRuleV94("搜索最新章节", { it.searchLatest }, { it.copy(searchLatest = "") }),
    OptionalRuleV94("详情书名", { it.infoName }, { it.copy(infoName = "") }),
    OptionalRuleV94("详情作者", { it.infoAuthor }, { it.copy(infoAuthor = "") }),
    OptionalRuleV94("详情封面", { it.infoCover }, { it.copy(infoCover = "") }),
    OptionalRuleV94("详情简介", { it.infoIntro }, { it.copy(infoIntro = "") }),
    OptionalRuleV94("目录下一页", { it.tocNext }, { it.copy(tocNext = "") }),
    OptionalRuleV94("正文下一页", { it.contentNext }, { it.copy(contentNext = "") }),
    OptionalRuleV94("正文替换", { it.contentReplace }, { it.copy(contentReplace = "") }),
    OptionalRuleV94("发现作者", { it.exploreAuthor }, { it.copy(exploreAuthor = "") }),
    OptionalRuleV94("发现封面", { it.exploreCover }, { it.copy(exploreCover = "") }),
    OptionalRuleV94("发现简介", { it.exploreIntro }, { it.copy(exploreIntro = "") }),
    OptionalRuleV94("发现最新章节", { it.exploreLatest }, { it.copy(exploreLatest = "") }),
)

internal data class SourceSanitizeV94(val source: BookSourceV36, val notices: List<String>)

/**
 * Drops optional rules the engine cannot run (with a notice). Search and discovery that cannot run
 * are disabled rather than failing the source, as long as something usable remains; the caller still
 * applies the existing usability checks afterwards.
 */
internal fun sanitizeImportedSourceV94(input: BookSourceV36): SourceSanitizeV94 {
    var source = input
    val notices = ArrayList<String>()
    val dropped = ArrayList<String>()
    OPTIONAL_RULES_V94.forEach { rule ->
        val reason = sourceRuleUnsupportedV94(rule.read(source))
        if (reason != null) {
            source = rule.clear(source)
            dropped += "${rule.label}（$reason）"
        }
    }
    if (dropped.isNotEmpty()) notices += "已忽略不支持的规则：${dropped.joinToString("、")}"
    val exploreRules = listOf(source.exploreUrl, source.exploreList, source.exploreName, source.exploreBookUrl)
    val exploreReason = exploreRules.firstNotNullOfOrNull(::sourceRuleUnsupportedV94)
    if (source.exploreUrl.isNotBlank() && exploreReason != null) {
        source = source.copy(exploreUrl = "")
        notices += "发现分类依赖 $exploreReason，已停用发现"
    }
    val searchRules = listOf(source.searchUrl, source.searchList, source.searchName, source.searchBookUrl)
    val searchReason = searchRules.firstNotNullOfOrNull(::sourceRuleUnsupportedV94)
    val hasExplore = source.exploreUrl.isNotBlank() && source.exploreList.isNotBlank()
    if (searchReason != null && hasExplore && sourceRuleUnsupportedV94(source.exploreList) == null) {
        source = source.copy(searchUrl = "", searchList = "")
        notices += "搜索依赖 $searchReason，已停用搜索（仍可通过发现分类浏览）"
    }
    if (source.exploreUrl.isNotBlank()) {
        val catalog = sourceDiscoveryCatalogV41(source.copy(enabled = true, enabledExplore = true))
        when {
            catalog.sections.isEmpty() && catalog.issues.isNotEmpty() && source.searchUrl.isNotBlank() -> {
                source = source.copy(exploreUrl = "")
                notices += "发现分类无法使用（${catalog.issues.first().take(60)}），已停用发现"
            }
            catalog.sections.isNotEmpty() && catalog.issues.isNotEmpty() ->
                notices += "跳过 ${catalog.issues.size} 个无法使用的发现分类"
        }
    }
    return SourceSanitizeV94(source, notices)
}

/** Stable id for a hand-made source: its site address, like Legado's bookSourceUrl. */
internal fun manualSourceIdV94(baseUrl: String): String =
    baseUrl.trim().trimEnd('/').substringBefore('#')

/** Starting point for 「新建书源」: a valid skeleton with the common Legado-style rules. */
internal fun newSourceTemplateV94(): BookSourceV36 = BookSourceV36(
    id = "",
    name = "",
    baseUrl = "https://",
    searchUrl = "/search?q={{key}}",
    searchList = "",
    searchName = "",
    searchBookUrl = "",
    tocList = "",
    tocName = "",
    tocUrl = "",
    contentText = "",
)

/** Field labels for the 书源 form. Grouped the way Legado users know them. */
internal data class SourceFormFieldV94(
    val key: String,
    val label: String,
    val hint: String,
    val required: Boolean = false,
    val read: (BookSourceV36) -> String,
    val write: (BookSourceV36, String) -> BookSourceV36,
)

internal data class SourceFormGroupV94(val title: String, val fields: List<SourceFormFieldV94>)

internal val SOURCE_FORM_GROUPS_V94: List<SourceFormGroupV94> = listOf(
    SourceFormGroupV94("基本信息", listOf(
        SourceFormFieldV94("name", "书源名称", "例如：某某小说网", true, { it.name }, { s, v -> s.copy(name = v) }),
        SourceFormFieldV94("baseUrl", "网站地址", "https://www.example.com", true, { it.baseUrl }, { s, v -> s.copy(baseUrl = v) }),
        SourceFormFieldV94("group", "分组", "可选，例如：综合", false, { it.group }, { s, v -> s.copy(group = v) }),
    )),
    SourceFormGroupV94("搜索", listOf(
        SourceFormFieldV94("searchUrl", "搜索地址", "/search?q={{key}}&page={{page}}", true, { it.searchUrl }, { s, v -> s.copy(searchUrl = v) }),
        SourceFormFieldV94("searchList", "书籍列表", "class.book-item 或 @css:.book-item", true, { it.searchList }, { s, v -> s.copy(searchList = v) }),
        SourceFormFieldV94("searchName", "书名", "tag.h3@text", true, { it.searchName }, { s, v -> s.copy(searchName = v) }),
        SourceFormFieldV94("searchBookUrl", "书籍链接", "tag.a.0@href", true, { it.searchBookUrl }, { s, v -> s.copy(searchBookUrl = v) }),
        SourceFormFieldV94("searchAuthor", "作者", "class.author@text", false, { it.searchAuthor }, { s, v -> s.copy(searchAuthor = v) }),
        SourceFormFieldV94("searchCover", "封面", "tag.img@src", false, { it.searchCover }, { s, v -> s.copy(searchCover = v) }),
        SourceFormFieldV94("searchIntro", "简介", "class.intro@text", false, { it.searchIntro }, { s, v -> s.copy(searchIntro = v) }),
        SourceFormFieldV94("searchLatest", "最新章节", "class.latest@text", false, { it.searchLatest }, { s, v -> s.copy(searchLatest = v) }),
    )),
    SourceFormGroupV94("详情页", listOf(
        SourceFormFieldV94("infoName", "书名", "可选", false, { it.infoName }, { s, v -> s.copy(infoName = v) }),
        SourceFormFieldV94("infoAuthor", "作者", "可选", false, { it.infoAuthor }, { s, v -> s.copy(infoAuthor = v) }),
        SourceFormFieldV94("infoCover", "封面", "可选", false, { it.infoCover }, { s, v -> s.copy(infoCover = v) }),
        SourceFormFieldV94("infoIntro", "简介", "可选", false, { it.infoIntro }, { s, v -> s.copy(infoIntro = v) }),
        SourceFormFieldV94("infoTocUrl", "目录地址", "目录不在详情页时填写", false, { it.infoTocUrl }, { s, v -> s.copy(infoTocUrl = v) }),
    )),
    SourceFormGroupV94("目录", listOf(
        SourceFormFieldV94("tocList", "章节列表", "id.list@tag.dd", false, { it.tocList }, { s, v -> s.copy(tocList = v) }),
        SourceFormFieldV94("tocName", "章节名", "tag.a@text", false, { it.tocName }, { s, v -> s.copy(tocName = v) }),
        SourceFormFieldV94("tocUrl", "章节链接", "tag.a@href", false, { it.tocUrl }, { s, v -> s.copy(tocUrl = v) }),
        SourceFormFieldV94("tocNext", "目录下一页", "可选", false, { it.tocNext }, { s, v -> s.copy(tocNext = v) }),
    )),
    SourceFormGroupV94("正文", listOf(
        SourceFormFieldV94("contentText", "正文内容", "id.content@html", false, { it.contentText }, { s, v -> s.copy(contentText = v) }),
        SourceFormFieldV94("contentNext", "正文下一页", "可选", false, { it.contentNext }, { s, v -> s.copy(contentNext = v) }),
        SourceFormFieldV94("contentReplace", "净化替换", "##广告文字", false, { it.contentReplace }, { s, v -> s.copy(contentReplace = v) }),
    )),
    SourceFormGroupV94("发现（可选）", listOf(
        SourceFormFieldV94("exploreUrl", "发现地址", "分类名::/list/{{page}}.html（每行一个）", false, { it.exploreUrl }, { s, v -> s.copy(exploreUrl = v) }),
        SourceFormFieldV94("exploreList", "书籍列表", "不填则沿用搜索规则", false, { it.exploreList }, { s, v -> s.copy(exploreList = v) }),
        SourceFormFieldV94("exploreName", "书名", "不填则沿用搜索规则", false, { it.exploreName }, { s, v -> s.copy(exploreName = v) }),
        SourceFormFieldV94("exploreBookUrl", "书籍链接", "不填则沿用搜索规则", false, { it.exploreBookUrl }, { s, v -> s.copy(exploreBookUrl = v) }),
    )),
)

/** Validates one draft (JSON text, Langhuan or Legado shape) into exactly one runnable source. */
internal fun validateSourceDraftV94(raw: String): BookSourceV36 {
    val text = normalizeSourceTextV94(raw)
    require(text.isNotEmpty()) { "书源内容为空" }
    val result = try {
        parseBookSourcesV36(text)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException(sourceJsonErrorV94(error), error)
    } catch (error: kotlinx.serialization.SerializationException) {
        throw IllegalArgumentException(sourceJsonErrorV94(error), error)
    }
    require(result.sources.size + result.skipped.size == 1) { "这里一次只能编辑一个书源；批量书源请使用「导入」" }
    result.skipped.firstOrNull()?.let { throw IllegalArgumentException("无法保存：$it") }
    return result.sources.single()
}

/** Missing required fields in the form, named the way the form labels them. */
internal fun sourceFormMissingV94(source: BookSourceV36): List<String> {
    val missing = ArrayList<String>()
    if (source.name.isBlank()) missing += "书源名称"
    if (runCatching { publicSourceUrlV36(source.baseUrl) }.isFailure) missing += "网站地址"
    val searchReady = source.searchUrl.isNotBlank() && source.searchList.isNotBlank()
    val exploreReady = source.exploreUrl.isNotBlank() && source.exploreList.isNotBlank()
    if (!searchReady && !exploreReady) missing += "搜索地址与书籍列表（或发现地址与列表）"
    if (searchReady && source.searchName.isBlank()) missing += "书名"
    return missing
}
