package com.xiguli.langhuan.ui

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/*
 * V95 阅读（Legado）规则引擎扩展。
 *
 * V36 的静态 HTML 规则（class./id./tag./@css:）保持原样；这里补上 Legado 书源常用、以前会被丢弃的部分：
 *   - JSONPath：$.a.b、$..x、[*]、[0,1]、[1:3]、[?(@.k == 'v')]，以及 @json: 前缀与 {$.id} 模板；
 *   - XPath：//div[@class='x']/a/@href、/text()、@XPath: 前缀（jsoup selectXpath）；
 *   - 脚本：@js:、<js></js>、{{表达式}}（交给 SourceJsEngineV95 的沙箱）；
 *   - @put:{k:规则} / @get:{k} 变量、&& || %% 组合、##正则##替换###。
 * JSON 响应被包成一个 <lh-json> 节点，因此原有以 Element 为参数的搜索/目录/正文流程不需要改签名。
 */

/** Per-operation values a script can read: the source, the keyword/page, the book and chapter in play. */
internal class SourceRuleScopeV95(
    val source: BookSourceV36?,
    val key: String = "",
    val page: Int = 1,
    val book: OnlineBookV36? = null,
    val chapter: OnlineChapterV36? = null,
    /** Discovery tabs are evaluated while composing; scripts there may not use the network. */
    val allowNetwork: Boolean = true,
    /** Test seam: replaces the real HTTP client for java.ajax and friends. */
    val fetchText: ((BookSourceV36?, SourceRequestV36) -> SourceTextResponseV95)? = null,
) {
    var networkCalls: Int = 0
}

internal data class SourceTextResponseV95(val url: String, val body: String, val code: Int = 200, val headers: Map<String, String> = emptyMap())

internal val sourceRuleScopeLocalV95 = ThreadLocal<SourceRuleScopeV95?>()

internal fun currentSourceRuleScopeV95(): SourceRuleScopeV95? = sourceRuleScopeLocalV95.get()

internal inline fun <T> withSourceRuleScopeV95(scope: SourceRuleScopeV95, block: () -> T): T {
    val previous = sourceRuleScopeLocalV95.get()
    sourceRuleScopeLocalV95.set(scope)
    try {
        return block()
    } finally {
        sourceRuleScopeLocalV95.set(previous)
    }
}

/** Legado @put/@get and source.put/get: kept per source in memory, bounded. */
internal object SourceVariablesV95 {
    private val bySource = ConcurrentHashMap<String, MutableMap<String, String>>()
    fun map(source: BookSourceV36?): MutableMap<String, String> =
        bySource.getOrPut(source?.id ?: "") { Collections.synchronizedMap(LinkedHashMap()) }
    fun put(source: BookSourceV36?, key: String, value: String) {
        val map = map(source)
        synchronized(map) {
            if (map.size >= 256 && !map.containsKey(key)) map.remove(map.keys.first())
            map[key] = value.take(256 * 1024)
        }
    }
    fun get(source: BookSourceV36?, key: String): String = map(source)[key].orEmpty()
}

// ---- Values ----------------------------------------------------------------------------------

internal sealed class RuleValueV95 {
    data class Text(val text: String) : RuleValueV95()
    data class Node(val element: Element) : RuleValueV95()
    data class Json(val json: JsonElement, val baseUri: String) : RuleValueV95()
    data class Many(val items: List<RuleValueV95>) : RuleValueV95()
}

private const val JSON_TAG_V95 = "lh-json"
private const val JSON_ATTR_V95 = "data-lh-json"
private val jsonCacheV95: MutableMap<Element, JsonElement> = Collections.synchronizedMap(WeakHashMap())

/** A JSON value carried through the Element-based pipeline. */
internal fun jsonNodeV95(json: JsonElement, baseUri: String): Element {
    val element = Element(Tag.valueOf(JSON_TAG_V95), baseUri)
    element.attr(JSON_ATTR_V95, json.toString())
    jsonCacheV95[element] = json
    return element
}

/** A fetched JSON response as a Document, so `doc.location()` and the catalogue code keep working. */
internal fun jsonDocumentV95(text: String, url: String): Document? {
    val trimmed = text.trim().trimStart('\uFEFF')
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return null
    val json = runCatching { BookSourceJsonV36.parseToJsonElement(trimmed) }.getOrNull() ?: return null
    if (json !is JsonObject && json !is JsonArray) return null
    val doc = Document(url)
    doc.appendChild(jsonNodeV95(json, url))
    jsonCacheV95[doc] = json
    return doc
}

/** The JSON a node stands for: a synthetic node, a JSON document, or a page whose body is only JSON text. */
internal fun jsonOfElementV95(element: Element): JsonElement? {
    jsonCacheV95[element]?.let { return it }
    if (element.tagName() == JSON_TAG_V95) {
        return runCatching { BookSourceJsonV36.parseToJsonElement(element.attr(JSON_ATTR_V95)) }.getOrNull()
            ?.also { jsonCacheV95[element] = it }
    }
    if (element is Document) {
        element.children().firstOrNull { it.tagName() == JSON_TAG_V95 }?.let { node ->
            return jsonOfElementV95(node)?.also { jsonCacheV95[element] = it }
        }
        val body = element.body() ?: return null
        val candidate = when {
            body.children().isEmpty() -> body.wholeText()
            body.children().size == 1 && body.child(0).tagName() == "pre" -> body.child(0).wholeText()
            else -> return null
        }.trim()
        if (!(candidate.startsWith("{") || candidate.startsWith("["))) return null
        return runCatching { BookSourceJsonV36.parseToJsonElement(candidate) }.getOrNull()
            ?.takeIf { it is JsonObject || it is JsonArray }?.also { jsonCacheV95[element] = it }
    }
    return null
}

internal fun jsonTextV95(json: JsonElement): String = when (json) {
    is JsonNull -> ""
    is JsonPrimitive -> json.contentOrNull.orEmpty().let { text ->
        // 12.0 -> "12" so ids and counts read like Legado's.
        if (!json.isString && text.endsWith(".0") && json.doubleOrNull?.let { it == Math.floor(it) && Math.abs(it) < 1e15 } == true) text.dropLast(2) else text
    }
    else -> json.toString()
}

internal fun ruleValueStringV95(value: RuleValueV95): String = when (value) {
    is RuleValueV95.Text -> value.text
    is RuleValueV95.Json -> jsonTextV95(value.json)
    is RuleValueV95.Many -> value.items.map(::ruleValueStringV95).filter { it.isNotEmpty() }.joinToString("\n")
    is RuleValueV95.Node -> jsonOfElementV95(value.element)?.let(::jsonTextV95) ?: when (val e = value.element) {
        is Document -> e.outerHtml()
        else -> e.outerHtml()
    }
}

internal fun ruleValueElementV95(value: RuleValueV95, baseUri: String): Element = when (value) {
    is RuleValueV95.Node -> value.element
    is RuleValueV95.Json -> jsonNodeV95(value.json, value.baseUri.ifBlank { baseUri })
    is RuleValueV95.Many -> value.items.firstOrNull()?.let { ruleValueElementV95(it, baseUri) } ?: Jsoup.parse("", baseUri)
    is RuleValueV95.Text -> {
        val trimmed = value.text.trim()
        val json = if (trimmed.startsWith("{") || trimmed.startsWith("[")) runCatching { BookSourceJsonV36.parseToJsonElement(trimmed) }.getOrNull() else null
        if (json != null && (json is JsonObject || json is JsonArray)) jsonNodeV95(json, baseUri)
        else Jsoup.parse(value.text, baseUri)
    }
}

private fun ruleValueItemsV95(value: RuleValueV95, baseUri: String): List<Element> = when (value) {
    is RuleValueV95.Many -> value.items.flatMap { ruleValueItemsV95(it, baseUri) }
    is RuleValueV95.Json -> if (value.json is JsonArray) value.json.map { jsonNodeV95(it, value.baseUri.ifBlank { baseUri }) } else listOf(jsonNodeV95(value.json, value.baseUri.ifBlank { baseUri }))
    is RuleValueV95.Node -> listOf(value.element)
    is RuleValueV95.Text -> {
        val element = ruleValueElementV95(value, baseUri)
        val json = jsonOfElementV95(element)
        if (json is JsonArray) json.map { jsonNodeV95(it, baseUri) }
        else if (element is Document && json == null) element.body().children().toList()
        else listOf(element)
    }
}

// ---- Detection -------------------------------------------------------------------------------

private val JS_PIECE_V95 = Regex("<js>([\\s\\S]*?)</js>|@js:([\\s\\S]*)", RegexOption.IGNORE_CASE)

/** True when a rule needs this engine rather than the V36 static HTML evaluator. */
internal fun needsAdvancedRuleV95(rule: String): Boolean {
    val text = rule.trim()
    if (text.isEmpty()) return false
    val lower = text.lowercase()
    if (lower.contains("<js>") || lower.contains("@js:") || text.contains("{{") || lower.contains("@put:") ||
        lower.contains("@get:") || text.contains("{$.") || text.contains("%%")) return true
    return splitTopLevelV95(text.substringBefore("##")).first.flatMap { splitTopLevelV95(it).first }.any { part ->
        val p = part.trim().removePrefix("-").removePrefix("+").trim()
        val l = p.lowercase()
        l.startsWith("@json:") || p.startsWith("$.") || p.startsWith("$[") || l.startsWith("@xpath:") ||
            p.startsWith("/") || p.startsWith(":") || l.startsWith("@@")
    }
}

// ---- Public entry points ---------------------------------------------------------------------

internal fun advancedRuleStringV95(context: Element, rule: String): String =
    ruleValueStringV95(evaluateRuleV95(RuleValueV95.Node(context), rule, list = false, baseUri = context.baseUri())).trim()

internal fun advancedRuleElementsV95(context: Element, rule: String): List<Element> {
    var text = rule.trim()
    val reverse = text.startsWith("-") && !text.startsWith("-$")
    if (reverse) text = text.drop(1)
    if (text.startsWith("+")) text = text.drop(1)
    val value = evaluateRuleV95(RuleValueV95.Node(context), text, list = true, baseUri = context.baseUri())
    val items = ruleValueItemsV95(value, context.baseUri())
    return if (reverse) items.reversed() else items
}

/** Detail-page `init` (ruleBookInfo.init): the value the other info rules are read from. */
internal fun ruleContextV95(context: Element, rule: String): Element {
    if (rule.isBlank()) return context
    val value = evaluateRuleV95(RuleValueV95.Node(context), rule, list = false, baseUri = context.baseUri(), keepStructure = true)
    return ruleValueElementV95(value, context.baseUri())
}

// ---- Pipeline --------------------------------------------------------------------------------

private data class RulePieceV95(val text: String, val js: Boolean)

private fun splitJsPiecesV95(rule: String): List<RulePieceV95> {
    val pieces = ArrayList<RulePieceV95>()
    var last = 0
    JS_PIECE_V95.findAll(rule).forEach { match ->
        val before = rule.substring(last, match.range.first)
        if (before.isNotBlank()) pieces += RulePieceV95(before.trim(), false)
        pieces += RulePieceV95((match.groups[1]?.value ?: match.groups[2]?.value).orEmpty(), true)
        last = match.range.last + 1
    }
    val tail = rule.substring(last)
    if (tail.isNotBlank()) pieces += RulePieceV95(tail.trim(), false)
    return pieces
}

internal fun evaluateRuleV95(
    content: RuleValueV95,
    rule: String,
    list: Boolean,
    baseUri: String,
    keepStructure: Boolean = false,
): RuleValueV95 {
    require(rule.length <= 64 * 1024) { "书源规则过长" }
    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("书源规则已取消")
    val pieces = splitJsPiecesV95(rule)
    var value: RuleValueV95 = content
    pieces.forEachIndexed { index, piece ->
        val last = index == pieces.lastIndex
        value = if (piece.js) {
            SourceJsEngineV95.evalRule(piece.text, value, content, baseUri)
        } else applyRuleV95(value, piece.text, list = list, baseUri = baseUri, keepStructure = keepStructure || !last)
    }
    return value
}

private val PUT_V95 = Regex("@put:(\\{[^{}]*})", RegexOption.IGNORE_CASE)
private val GET_V95 = Regex("@get:\\{([^{}]+)}", RegexOption.IGNORE_CASE)
private val JSON_TEMPLATE_V95 = Regex("\\{(\\$[.\\[][^{}]*)}")

private fun applyRuleV95(value: RuleValueV95, rawRule: String, list: Boolean, baseUri: String, keepStructure: Boolean): RuleValueV95 {
    var rule = rawRule.trim()
    if (rule.isEmpty()) return value
    val scope = currentSourceRuleScopeV95()
    // @put:{k:"rule"} stores values for later @get:{k} / java.get(k).
    PUT_V95.findAll(rule).toList().forEach { match ->
        val text = match.groupValues[1]
        (runCatching { parseSourceOptionsV94(text) }.getOrNull()
            ?: runCatching { parseSourceOptionsV94(text.replace(Regex("([{,]\\s*)([A-Za-z_\\u4e00-\\u9fff][\\w\\u4e00-\\u9fff]*)\\s*:"), "$1\"$2\":")) }.getOrNull())
            ?.forEach { (name, ruleValue) ->
            val putRule = (ruleValue as? JsonPrimitive)?.contentOrNull.orEmpty()
            SourceVariablesV95.put(scope?.source, name, ruleValueStringV95(evaluateRuleV95(value, putRule, false, baseUri)))
        }
    }
    rule = rule.replace(PUT_V95, "").trim()
    rule = rule.replace(GET_V95) { SourceVariablesV95.get(scope?.source, it.groupValues[1].trim()) }
    if (rule.isEmpty()) return value
    if (value is RuleValueV95.Many && !list) {
        return RuleValueV95.Text(value.items.map { ruleValueStringV95(applyRuleV95(it, rule, false, baseUri, keepStructure)) }
            .filter { it.isNotEmpty() }.joinToString("\n"))
    }
    val (body, regex, replacement, firstOnly) = splitCleanupV95(rule)
    // {{js or rule}} and {$.json} templates turn the rule into a literal string.
    if (body.contains("{{") || (body.contains("{$") && JSON_TEMPLATE_V95.containsMatchIn(body) && !body.startsWith("$"))) {
        val literal = expandRuleTemplatesV95(body, value, baseUri)
        return RuleValueV95.Text(cleanupV95(literal, regex, replacement, firstOnly))
    }
    val combined = combineV95(body) { sub -> subRuleValuesV95(value, sub, list, baseUri, keepStructure) }
    if (list || keepStructure) {
        if (regex != null && combined.all { it is RuleValueV95.Text }) {
            return RuleValueV95.Many(combined.map { RuleValueV95.Text(cleanupV95((it as RuleValueV95.Text).text, regex, replacement, firstOnly)) })
        }
        return if (keepStructure && !list && combined.size == 1) combined.single() else RuleValueV95.Many(combined)
    }
    val joined = combined.map(::ruleValueStringV95).filter { it.isNotEmpty() }.joinToString("\n")
    return RuleValueV95.Text(cleanupV95(joined, regex, replacement, firstOnly))
}

private data class CleanupV95(val body: String, val regex: String?, val replacement: String, val firstOnly: Boolean)

private fun splitCleanupV95(rule: String): CleanupV95 {
    val index = rule.indexOf("##")
    if (index < 0) return CleanupV95(rule, null, "", false)
    val rest = rule.substring(index + 2)
    val firstOnly = rest.endsWith("###")
    val parts = rest.removeSuffix("###").split("##")
    return CleanupV95(rule.substring(0, index), parts.getOrNull(0), parts.getOrNull(1).orEmpty(), firstOnly)
}

private fun cleanupV95(text: String, regex: String?, replacement: String, firstOnly: Boolean): String {
    if (regex.isNullOrEmpty()) return text
    if (!firstOnly) return sourceRegexReplaceV36(text, regex, replacement)
    // Legado ###: the first match only, and the result is the replacement of that match.
    val matcher = runCatching { com.google.re2j.Pattern.compile(regex).matcher(text) }.getOrNull() ?: return text
    if (!matcher.find()) return ""
    val out = StringBuffer()
    val start = matcher.start()
    matcher.appendReplacement(out, replacement)
    return out.substring(start.coerceAtMost(out.length))
}

/** Splits && / || / %% at the top level (outside quotes and brackets) and combines the results. */
private fun combineV95(rule: String, evaluate: (String) -> List<RuleValueV95>): List<RuleValueV95> {
    val (parts, operator) = splitTopLevelV95(rule)
    if (parts.size <= 1) return evaluate(rule.trim())
    return when (operator) {
        "||" -> parts.firstNotNullOfOrNull { part -> evaluate(part.trim()).takeIf { values -> values.any { ruleValueStringV95(it).isNotBlank() } } }.orEmpty()
        "%%" -> {
            val lists = parts.map { evaluate(it.trim()) }
            val max = lists.maxOfOrNull { it.size } ?: 0
            (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        }
        else -> parts.flatMap { evaluate(it.trim()) }
    }
}

private fun splitTopLevelV95(rule: String): Pair<List<String>, String?> {
    for (operator in listOf("&&", "||", "%%")) {
        val parts = ArrayList<String>()
        var depth = 0
        var quote: Char? = null
        var start = 0
        var i = 0
        while (i < rule.length) {
            val c = rule[i]
            when {
                quote != null -> if (c == quote && rule.getOrNull(i - 1) != '\\') quote = null
                c == '\'' || c == '"' -> quote = c
                c == '[' || c == '(' || c == '{' -> depth++
                c == ']' || c == ')' || c == '}' -> depth = (depth - 1).coerceAtLeast(0)
                depth == 0 && rule.startsWith(operator, i) -> {
                    parts += rule.substring(start, i)
                    i += operator.length
                    start = i
                    continue
                }
            }
            i++
        }
        if (parts.isNotEmpty()) {
            parts += rule.substring(start)
            return parts to operator
        }
    }
    return listOf(rule) to null
}

private fun subRuleValuesV95(value: RuleValueV95, rawSub: String, list: Boolean, baseUri: String, keepStructure: Boolean): List<RuleValueV95> {
    var sub = rawSub.trim()
    if (sub.isEmpty()) return emptyList()
    val element = ruleValueElementV95(value, baseUri)
    val base = element.baseUri().ifBlank { baseUri }
    val json = jsonOfElementV95(element)
    val lower = sub.lowercase()
    return when {
        lower.startsWith("@json:") || sub.startsWith("$.") || sub.startsWith("$[") || (json != null && !lower.startsWith("@css:") &&
            !lower.startsWith("@xpath:") && !lower.startsWith("@@") && !sub.startsWith("//")) -> {
            if (lower.startsWith("@json:")) sub = sub.substring(6)
            val root = json ?: runCatching { BookSourceJsonV36.parseToJsonElement(element.wholeText().trim()) }.getOrNull()
                ?: runCatching { BookSourceJsonV36.parseToJsonElement(ruleValueStringV95(value).trim()) }.getOrNull()
                ?: return emptyList()
            val found = jsonPathV95(root, sub)
            if (list || keepStructure) {
                val expanded = if (list && found.size == 1 && found[0] is JsonArray) (found[0] as JsonArray).toList() else found
                expanded.map { RuleValueV95.Json(it, base) }
            } else found.flatMap { if (it is JsonArray) it.toList() else listOf(it) }.map { RuleValueV95.Text(jsonTextV95(it)) }
        }
        lower.startsWith("@xpath:") || sub.startsWith("/") -> {
            if (lower.startsWith("@xpath:")) sub = sub.substring(7)
            if (list || keepStructure) xpathElementsV95(element, sub).map { RuleValueV95.Node(it) }
            else xpathValuesV95(element, sub).map { RuleValueV95.Text(it) }
        }
        sub.startsWith(":") -> regexAllInOneV95(element, sub.substring(1)).map { RuleValueV95.Text(it) }
        else -> {
            if (lower.startsWith("@@")) sub = sub.substring(2)
            if (list || keepStructure) basicRuleElementsV36(element, sub).map { RuleValueV95.Node(it) }
            else listOf(RuleValueV95.Text(basicRuleValuesV36(element, sub).joinToString("\n")))
        }
    }
}

/** Legado "AllInOne" regex rule (leading ':'): each match's groups joined. */
private fun regexAllInOneV95(element: Element, regex: String): List<String> {
    val html = if (element.tagName() == JSON_TAG_V95) element.attr(JSON_ATTR_V95) else element.outerHtml()
    val matcher = runCatching { com.google.re2j.Pattern.compile(regex).matcher(html) }.getOrNull() ?: return emptyList()
    val out = ArrayList<String>()
    while (matcher.find() && out.size < 5000) {
        out += if (matcher.groupCount() == 0) matcher.group() else (1..matcher.groupCount()).joinToString("") { matcher.group(it).orEmpty() }
    }
    return out
}

/** `{{js or rule}}` (and `{$.path}` for JSON) substituted inside a rule string. */
internal fun expandRuleTemplatesV95(template: String, value: RuleValueV95, baseUri: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < template.length) {
        val open = template.indexOf("{{", i)
        if (open < 0) { out.append(template, i, template.length); break }
        out.append(template, i, open)
        val close = findTemplateCloseV95(template, open + 2)
        if (close < 0) { out.append(template, open, template.length); break }
        val inner = template.substring(open + 2, close).trim()
        out.append(templateValueV95(inner, value, baseUri))
        i = close + 2
    }
    var text = out.toString()
    if (text.contains("{$")) {
        text = text.replace(JSON_TEMPLATE_V95) { match ->
            ruleValueStringV95(evaluateRuleV95(value, match.groupValues[1], false, baseUri))
        }
    }
    return text
}

internal fun findTemplateCloseV95(text: String, from: Int): Int {
    var depth = 0
    var quote: Char? = null
    var i = from
    while (i < text.length) {
        val c = text[i]
        when {
            quote != null -> if (c == quote && text.getOrNull(i - 1) != '\\') quote = null
            c == '\'' || c == '"' || c == '`' -> quote = c
            c == '{' -> depth++
            c == '}' && depth > 0 -> depth--
            c == '}' && text.getOrNull(i + 1) == '}' -> return i
        }
        i++
    }
    return -1
}

private fun templateValueV95(inner: String, value: RuleValueV95, baseUri: String): String {
    val lower = inner.lowercase()
    val isRule = inner.startsWith("@") || inner.startsWith("$.") || inner.startsWith("$[") || inner.startsWith("//")
    return if (isRule) {
        val rule = if (lower.startsWith("@@")) inner.substring(2) else inner
        ruleValueStringV95(evaluateRuleV95(value, rule, false, baseUri))
    } else {
        ruleValueStringV95(SourceJsEngineV95.evalRule(inner, value, value, baseUri))
    }
}

// ---- XPath -----------------------------------------------------------------------------------

private val XPATH_TAIL_V95 = Regex("^(.*?)/(@[\\w:.-]+|text\\(\\)|allText\\(\\)|textNodes\\(\\)|ownText\\(\\)|html\\(\\)|outerHtml\\(\\)|innerHtml\\(\\))$")

internal fun xpathElementsV95(context: Element, rawPath: String): List<Element> {
    val path = rawPath.trim()
    if (path.isEmpty()) return emptyList()
    val match = XPATH_TAIL_V95.find(path)
    val elementsPath = match?.groupValues?.get(1)?.trimEnd('/')?.ifBlank { "." } ?: path
    return runCatching { context.selectXpath(elementsPath).toList() }.getOrDefault(emptyList())
}

internal fun xpathValuesV95(context: Element, rawPath: String): List<String> {
    val path = rawPath.trim()
    if (path.isEmpty()) return emptyList()
    val match = XPATH_TAIL_V95.find(path)
    val tail = match?.groupValues?.get(2)
    val elementsPath = match?.groupValues?.get(1)?.let { base ->
        // `//a//@href` style: a trailing `/` belongs to the axis, keep the descendant meaning.
        if (base.endsWith("/")) base + "*" else base
    }?.ifBlank { "." } ?: path
    val elements = runCatching { context.selectXpath(elementsPath).toList() }.getOrDefault(emptyList())
    return elements.mapNotNull { element ->
        when {
            tail == null -> element.text()
            tail.startsWith("@") -> element.attr(tail.substring(1)).takeIf { it.isNotEmpty() }
            tail == "text()" || tail == "ownText()" -> element.ownText()
            tail == "allText()" -> element.text()
            tail == "textNodes()" -> element.textNodes().joinToString("\n") { it.text().trim() }.trim()
            tail == "html()" || tail == "innerHtml()" -> element.html()
            tail == "outerHtml()" -> element.outerHtml()
            else -> element.text()
        }
    }.filter { it.isNotBlank() }
}

// ---- JSONPath --------------------------------------------------------------------------------

/** A practical JSONPath subset (Jayway-style, as Legado uses). A path without `$` is relative to the root. */
internal fun jsonPathV95(root: JsonElement, rawPath: String): List<JsonElement> {
    var path = rawPath.trim()
    if (path.isEmpty()) return listOf(root)
    path = when {
        path.startsWith("$") -> path.substring(1)
        path.startsWith("@") -> path.substring(1)
        path.startsWith(".") || path.startsWith("[") -> path
        else -> ".$path"
    }
    var current = listOf(root)
    var i = 0
    while (i < path.length && current.isNotEmpty()) {
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("书源规则已取消")
        when {
            path.startsWith("..", i) -> {
                i += 2
                val all = current.flatMap(::descendantsV95)
                if (i < path.length && path[i] == '[') {
                    val end = matchingBracketV95(path, i)
                    current = all.flatMap { applyBracketV95(it, path.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    val (name, next) = readNameV95(path, i)
                    current = if (name == "*") all.flatMap(::childrenV95) else all.mapNotNull { (it as? JsonObject)?.get(name) }
                    i = next
                }
            }
            path[i] == '.' -> {
                val (name, next) = readNameV95(path, i + 1)
                current = when {
                    name.isEmpty() -> current
                    name == "*" -> current.flatMap(::childrenV95)
                    name == "length()" || name == "length" && current.all { it is JsonArray } ->
                        current.map { JsonPrimitive(((it as? JsonArray)?.size ?: (it as? JsonObject)?.size ?: 0)) }
                    else -> current.mapNotNull { (it as? JsonObject)?.get(name) }
                }
                i = next
            }
            path[i] == '[' -> {
                val end = matchingBracketV95(path, i)
                val inner = path.substring(i + 1, end)
                current = current.flatMap { applyBracketV95(it, inner) }
                i = end + 1
            }
            else -> {
                val (name, next) = readNameV95(path, i)
                current = current.mapNotNull { (it as? JsonObject)?.get(name) }
                i = next.coerceAtLeast(i + 1)
            }
        }
    }
    return current.filter { it !is JsonNull }
}

private fun readNameV95(path: String, from: Int): Pair<String, Int> {
    var i = from
    while (i < path.length && path[i] != '.' && path[i] != '[') i++
    return path.substring(from, i).trim() to i
}

private fun matchingBracketV95(path: String, open: Int): Int {
    var depth = 0
    var quote: Char? = null
    for (i in open until path.length) {
        val c = path[i]
        when {
            quote != null -> if (c == quote) quote = null
            c == '\'' || c == '"' -> quote = c
            c == '[' -> depth++
            c == ']' -> { depth--; if (depth == 0) return i }
        }
    }
    throw IllegalArgumentException("JSONPath 括号不匹配：$path")
}

private fun childrenV95(element: JsonElement): List<JsonElement> = when (element) {
    is JsonArray -> element.toList()
    is JsonObject -> element.values.toList()
    else -> emptyList()
}

private fun descendantsV95(element: JsonElement): List<JsonElement> {
    val out = ArrayList<JsonElement>()
    fun walk(e: JsonElement) {
        out += e
        check(out.size <= 200_000) { "JSON 结构过大" }
        childrenV95(e).forEach(::walk)
    }
    walk(element)
    return out
}

private fun applyBracketV95(element: JsonElement, rawInner: String): List<JsonElement> {
    val inner = rawInner.trim()
    if (inner == "*") return childrenV95(element)
    if (inner.startsWith("?")) {
        val expression = inner.substring(1).trim().removePrefix("(").removeSuffix(")")
        return childrenV95(element).filter { jsonFilterV95(it, expression) }
    }
    val parts = splitOutsideQuotesV95(inner, ',')
    return parts.flatMap { part ->
        val p = part.trim()
        when {
            p.startsWith("'") || p.startsWith("\"") -> listOfNotNull((element as? JsonObject)?.get(p.trim('\'', '"')))
            p.contains(':') -> {
                val array = element as? JsonArray ?: return@flatMap emptyList()
                val (a, b) = p.split(':', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                fun norm(v: Int) = if (v < 0) (array.size + v).coerceAtLeast(0) else v.coerceAtMost(array.size)
                val start = a.toIntOrNull()?.let(::norm) ?: 0
                val end = b.toIntOrNull()?.let(::norm) ?: array.size
                if (start < end) array.subList(start, end) else emptyList()
            }
            p.toIntOrNull() != null -> {
                val array = element as? JsonArray ?: return@flatMap emptyList()
                val index = p.toInt().let { if (it < 0) array.size + it else it }
                listOfNotNull(array.getOrNull(index))
            }
            else -> listOfNotNull((element as? JsonObject)?.get(p))
        }
    }
}

private fun splitOutsideQuotesV95(text: String, separator: Char): List<String> {
    val out = ArrayList<String>()
    var quote: Char? = null
    var start = 0
    text.forEachIndexed { i, c ->
        when {
            quote != null -> if (c == quote) quote = null
            c == '\'' || c == '"' -> quote = c
            c == separator -> { out += text.substring(start, i); start = i + 1 }
        }
    }
    out += text.substring(start)
    return out
}

/** `@.a`, `@.a == 'x'`, `@.n > 3`, joined with && / ||. */
private fun jsonFilterV95(item: JsonElement, expression: String): Boolean {
    val ors = expression.split("||")
    if (ors.size > 1) return ors.any { jsonFilterV95(item, it) }
    val ands = expression.split("&&")
    if (ands.size > 1) return ands.all { jsonFilterV95(item, it) }
    val match = Regex("^\\s*(!?)@([.\\[][^=!<>~]*?)\\s*(==|!=|>=|<=|>|<|=~)?\\s*(.*?)\\s*$").find(expression) ?: return false
    val negate = match.groupValues[1] == "!"
    val found = jsonPathV95(item, "$" + match.groupValues[2].trim())
    val operator = match.groupValues[3]
    if (operator.isEmpty()) return found.isNotEmpty() != negate
    val left = found.firstOrNull() ?: return false
    val literal = match.groupValues[4].trim()
    val leftText = jsonTextV95(left)
    val rightText = literal.trim('\'', '"')
    val leftNumber = leftText.toDoubleOrNull()
    val rightNumber = if (literal.startsWith("'") || literal.startsWith("\"")) null else rightText.toDoubleOrNull()
    val rightBool = if (literal == "true" || literal == "false") literal.toBoolean() else null
    return when (operator) {
        "==" -> if (rightBool != null) (left as? JsonPrimitive)?.booleanOrNull == rightBool
            else if (leftNumber != null && rightNumber != null) leftNumber == rightNumber else leftText == rightText
        "!=" -> if (leftNumber != null && rightNumber != null) leftNumber != rightNumber else leftText != rightText
        ">" -> leftNumber != null && rightNumber != null && leftNumber > rightNumber
        "<" -> leftNumber != null && rightNumber != null && leftNumber < rightNumber
        ">=" -> leftNumber != null && rightNumber != null && leftNumber >= rightNumber
        "<=" -> leftNumber != null && rightNumber != null && leftNumber <= rightNumber
        "=~" -> runCatching {
            val pattern = rightText.removePrefix("/").substringBeforeLast("/")
            com.google.re2j.Pattern.compile(pattern).matcher(leftText).find()
        }.getOrDefault(false)
        else -> false
    }
}

// ---- Import-time checks ----------------------------------------------------------------------

private val JAVA_CLASS_ACCESS_V95 = Regex(
    "\\bPackages\\.|\\bJavaImporter\\b|\\bimportClass\\b|\\bimportPackage\\b|\\bjava\\.(lang|io|net|util|nio|security|text)\\.|\\bjavax\\.|\\borg\\.(jsoup|json|apache)\\.|\\bandroid\\.|\\bcom\\.(google|github|script)\\.|\\bio\\.legado\\.|\\bThread\\b",
)
private val UNSUPPORTED_JAVA_API_V95 = listOf(
    "java.webView", "java.startBrowser", "java.getVerificationCode", "java.importScript", "java.downloadFile",
    "java.readFile", "java.readTxtFile", "java.deleteFile", "java.unzipFile", "java.un7zFile", "java.unrarFile",
    "java.unArchiveFile", "java.getTxtInFolder", "java.getZipStringContent", "java.getZipByteArrayContent",
    "java.cacheFile", "java.openUrl", "java.openVideoPlayer", "java.webViewGetSource", "java.webViewGetOverrideUrl",
    "java.getFile", "java.queryBase64TTF", "java.queryTTF", "java.replaceFont", "java.startBrowserAwait",
)

/** Why a script cannot run in the sandbox (Java classes, WebView, files), or null when it can. */
internal fun sourceScriptUnsupportedV95(script: String): String? {
    if (script.isBlank()) return null
    UNSUPPORTED_JAVA_API_V95.firstOrNull { script.contains(it) }?.let { return "需要网页视图或文件的脚本（$it）" }
    JAVA_CLASS_ACCESS_V95.find(script)?.let { return "直接调用 Java 类的脚本（${it.value.trimEnd('.')}，沙箱禁止）" }
    return null
}
