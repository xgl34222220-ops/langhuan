package com.xiguli.langhuan.ui

import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.json.JsonParser

/*
 * V95 书源脚本沙箱（Rhino，纯 Java 解释执行，不带原生库）。
 *
 *  - 只装入安全标准对象：没有 Packages/java 包、JavaImporter、importClass；ClassShutter 拒绝一切 Java 类；
 *  - 不暴露任何 Java 对象：java/source/book/cookie/cache 都是用 Kotlin 实现的纯 JS 函数对象；
 *  - 指令计数器检查超时与线程中断，脚本无法用 try/catch 吞掉；
 *  - 网络只有 java.ajax/connect/get/post，全部走书源 HTTP 客户端（公网 DNS 校验、跳转检查、大小上限），
 *    每次操作最多 12 次请求；没有文件、系统或 WebView 能力。
 */

internal class SourceJsTimeoutV95(message: String) : Error(message)

internal class SourceJsNetworkDisabledV95 : IllegalStateException("此处的书源脚本不能联网（发现分类在本机计算，不发请求）")

internal object SourceJsEngineV95 {
    /** Wall-clock budget for one top-level script (network waits included). Tests shorten it. */
    @Volatile internal var TIMEOUT_MS = 20_000L
    private const val MAX_NETWORK_CALLS = 12
    internal const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"

    private val deadline = ThreadLocal<Long?>()
    private val depth = ThreadLocal<Int>()

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            @Suppress("DEPRECATION")
            optimizationLevel = -1
            languageVersion = Context.VERSION_ES6
            instructionObserverThreshold = 5_000
            maximumInterpreterStackDepth = 400
            setClassShutter(ClassShutter { false })
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (Thread.currentThread().isInterrupted) throw SourceJsTimeoutV95("书源脚本已取消")
            val limit = deadline.get() ?: return
            if (System.nanoTime() > limit) throw SourceJsTimeoutV95("书源脚本运行超时（${TIMEOUT_MS / 1000} 秒）")
        }
    }

    private val compiled = object : LinkedHashMap<String, Script>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Script>?): Boolean = size > 256
    }

    private fun compile(cx: Context, code: String, name: String): Script = synchronized(compiled) {
        compiled[code] ?: cx.compileString(code, name, 1, null).also { compiled[code] = it }
    }

    /** Per-operation JS globals: one scope per rule scope, so jsLib runs once and globals persist like Legado. */
    private val scopes = java.util.Collections.synchronizedMap(java.util.WeakHashMap<SourceRuleScopeV95, ScriptableObject>())

    private inline fun <T> enter(block: (Context) -> T): T {
        val level = depth.get() ?: 0
        if (level == 0) deadline.set(System.nanoTime() + TIMEOUT_MS * 1_000_000)
        depth.set(level + 1)
        val cx = factory.enterContext()
        try {
            return block(cx)
        } catch (e: SourceJsTimeoutV95) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("书源脚本已取消").apply { initCause(e) }
            throw IllegalStateException(e.message, e)
        } finally {
            Context.exit()
            depth.set(level)
            if (level == 0) deadline.remove()
        }
    }

    private fun globalScope(cx: Context, rules: SourceRuleScopeV95?): ScriptableObject {
        rules?.let { scope -> scopes[scope]?.let { return it } }
        val global = cx.initSafeStandardObjects(null, false)
        val host = HostV95(rules)
        host.install(cx, global)
        val lib = rules?.source?.jsLib.orEmpty()
        if (lib.isNotBlank() && !lib.trimStart().startsWith("{")) {
            compile(cx, lib, "jsLib").exec(cx, global)
        }
        rules?.let { scopes[it] = global }
        return global
    }

    /** A rule piece (`@js:` / `<js>` / `{{}}`): `result` is the value so far. Script errors read as empty. */
    fun evalRule(code: String, result: RuleValueV95, content: RuleValueV95, baseUri: String): RuleValueV95 = try {
        eval(code, result, content, baseUri, emptyMap())
    } catch (e: RhinoException) {
        lastError.set(e.details().take(200))
        RuleValueV95.Text("")
    }

    /** A URL or discovery script: errors are reported to the caller. */
    fun evalStrict(code: String, extra: Map<String, Any?> = emptyMap(), baseUri: String = ""): String = try {
        ruleValueStringV95(eval(code, RuleValueV95.Text(extra["result"]?.toString().orEmpty()), RuleValueV95.Text(""), baseUri, extra))
    } catch (e: RhinoException) {
        throw IllegalArgumentException("书源脚本出错：${e.details().take(160)}", e)
    }

    val lastError = ThreadLocal<String?>()

    private fun eval(code: String, result: RuleValueV95, content: RuleValueV95, baseUri: String, extra: Map<String, Any?>): RuleValueV95 {
        require(code.length <= 256 * 1024) { "书源脚本过长" }
        val rules = currentSourceRuleScopeV95()
        return enter { cx ->
            val global = globalScope(cx, rules)
            val host = (ScriptableObject.getProperty(global, "java") as? HostObjectV95)?.host ?: HostV95(rules)
            val previousContent = host.content
            val previousBase = host.baseUrl
            host.content = content
            host.baseUrl = baseUri
            try {
                put(global, "result", toJs(cx, global, result))
                put(global, "baseUrl", baseUri)
                put(global, "src", ruleValueStringV95(content).take(4 * 1024 * 1024))
                put(global, "key", rules?.key.orEmpty())
                put(global, "page", rules?.page ?: 1)
                put(global, "title", rules?.chapter?.title.orEmpty())
                extra.forEach { (name, value) -> if (name != "result") put(global, name, toJs(cx, global, value)) }
                fromJs(cx, global, compile(cx, code, "source").exec(cx, global), baseUri)
            } finally {
                host.content = previousContent
                host.baseUrl = previousBase
            }
        }
    }

    private fun put(scope: Scriptable, name: String, value: Any?) = ScriptableObject.putProperty(scope, name, value)

    // ---- Conversions --------------------------------------------------------------------------

    internal fun toJs(cx: Context, scope: Scriptable, value: Any?): Any? = when (value) {
        null -> null
        is String, is Number, is Boolean -> value
        is RuleValueV95.Text -> value.text
        is RuleValueV95.Json -> jsonToJs(cx, scope, value.json)
        is RuleValueV95.Many -> cx.newArray(scope, value.items.map { toJs(cx, scope, it) }.toTypedArray())
        is RuleValueV95.Node -> jsonOfElementV95(value.element)?.let { jsonToJs(cx, scope, it) } ?: ruleValueStringV95(value)
        is kotlinx.serialization.json.JsonElement -> jsonToJs(cx, scope, value)
        is List<*> -> cx.newArray(scope, value.map { toJs(cx, scope, it) }.toTypedArray())
        is Map<*, *> -> cx.newObject(scope).also { obj -> value.forEach { (k, v) -> ScriptableObject.putProperty(obj, k.toString(), toJs(cx, scope, v)) } }
        is Scriptable -> value
        else -> value.toString()
    }

    private fun jsonToJs(cx: Context, scope: Scriptable, json: kotlinx.serialization.json.JsonElement): Any? =
        if (json is JsonObject || json is JsonArray) JsonParser(cx, scope).parseValue(json.toString()) else jsonTextV95(json)

    internal fun fromJs(cx: Context, scope: Scriptable, value: Any?, baseUri: String): RuleValueV95 = when {
        value == null || Undefined.isUndefined(value) -> RuleValueV95.Text("")
        value is CharSequence -> RuleValueV95.Text(value.toString())
        value is Number -> RuleValueV95.Text(numberText(value))
        value is Boolean -> RuleValueV95.Text(value.toString())
        value is NativeJavaObject -> RuleValueV95.Text(value.unwrap()?.toString().orEmpty())
        value is NativeArray -> RuleValueV95.Many((0 until value.length.coerceAtMost(100_000)).map { i ->
            fromJs(cx, scope, value.get(i.toInt(), value), baseUri)
        })
        value is HostResponseV95 -> RuleValueV95.Text(value.response.body)
        value is Scriptable -> {
            val text = NativeJSON.stringify(cx, scope, value, null, null)
            val json = (text as? CharSequence)?.toString()?.let { runCatching { BookSourceJsonV36.parseToJsonElement(it) }.getOrNull() }
            if (json != null) RuleValueV95.Json(json, baseUri) else RuleValueV95.Text(Context.toString(value))
        }
        else -> RuleValueV95.Text(value.toString())
    }

    internal fun numberText(value: Number): String {
        val d = value.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }

    internal fun str(args: Array<out Any?>, index: Int): String {
        val v = args.getOrNull(index)
        return when {
            v == null || Undefined.isUndefined(v) -> ""
            v is Number -> numberText(v)
            v is NativeJavaObject -> v.unwrap()?.toString().orEmpty()
            v is HostResponseV95 -> v.response.body
            else -> Context.toString(v)
        }
    }

    // ---- Network ------------------------------------------------------------------------------

    internal fun fetch(rules: SourceRuleScopeV95?, request: SourceRequestV36): SourceTextResponseV95 {
        if (rules != null && !rules.allowNetwork) throw SourceJsNetworkDisabledV95()
        rules?.let {
            it.networkCalls++
            check(it.networkCalls <= MAX_NETWORK_CALLS) { "书源脚本请求次数超过 $MAX_NETWORK_CALLS 次限制" }
        }
        deadline.get()?.let { if (System.nanoTime() > it) throw SourceJsTimeoutV95("书源脚本运行超时（${TIMEOUT_MS / 1000} 秒）") }
        val fetcher = rules?.fetchText
        return if (fetcher != null) fetcher(rules.source, request) else fetchSourceTextV95(rules?.source, request)
    }
}

// ---- Host objects (pure JS functions implemented in Kotlin) --------------------------------------

private class HostObjectV95(val host: HostV95) : ScriptableObject() {
    override fun getClassName(): String = "JavaHelper"
}

/** A fetched response as seen by scripts: body(), code(), url(), header(name). */
internal class HostResponseV95(val response: SourceTextResponseV95) : ScriptableObject() {
    override fun getClassName(): String = "StrResponse"
    override fun toString(): String = response.body
}

private class HostV95(val rules: SourceRuleScopeV95?) {
    var content: RuleValueV95 = RuleValueV95.Text("")
    var baseUrl: String = ""
    private val source get() = rules?.source

    fun install(cx: Context, global: ScriptableObject) {
        val java = HostObjectV95(this)
        java.parentScope = global
        java.prototype = ScriptableObject.getObjectPrototype(global)
        fun fn(target: Scriptable, name: String, body: (Context, Scriptable, Array<out Any?>) -> Any?) {
            ScriptableObject.putProperty(target, name, LambdaFunction(global, name, 0, Callable { c, s, _, args -> body(c, s, args) }))
        }
        val s = SourceJsEngineV95
        // Network
        fn(java, "ajax") { c, sc, a -> runCatching { request(s.str(a, 0)).body }.getOrElse { rethrowFatal(it); "" } }
        fn(java, "ajaxAll") { c, sc, a ->
            val urls = (a.getOrNull(0) as? NativeArray)?.let { arr -> (0 until arr.length).map { Context.toString(arr.get(it.toInt(), arr)) } }.orEmpty()
            c.newArray(sc, urls.map { url -> response(c, sc, runCatching { request(url) }.getOrElse { rethrowFatal(it); SourceTextResponseV95(url, "", 500) }) }.toTypedArray())
        }
        fn(java, "connect") { c, sc, a -> response(c, sc, request(s.str(a, 0), headers = headersArg(a.getOrNull(1)))) }
        fn(java, "get") { c, sc, a ->
            if (a.size <= 1) SourceVariablesV95.get(source, s.str(a, 0))
            else response(c, sc, request(s.str(a, 0), headers = headersArg(a.getOrNull(1))))
        }
        fn(java, "post") { c, sc, a -> response(c, sc, request(s.str(a, 0), method = "POST", body = s.str(a, 1), headers = headersArg(a.getOrNull(2)))) }
        fn(java, "head") { c, sc, a -> response(c, sc, request(s.str(a, 0), method = "HEAD", headers = headersArg(a.getOrNull(1)))) }
        // Variables and content
        fn(java, "put") { _, _, a -> s.str(a, 1).also { SourceVariablesV95.put(source, s.str(a, 0), it) } }
        fn(java, "getString") { _, _, a ->
            val rule = s.str(a, 0)
            val target = if (a.size > 1 && a[1] != null && !Undefined.isUndefined(a[1])) RuleValueV95.Text(s.str(a, 1)) else content
            ruleValueStringV95(evaluateRuleV95(target, rule, false, baseUrl)).trim()
        }
        fn(java, "getStringList") { c, sc, a ->
            val target = if (a.size > 1 && a[1] != null && !Undefined.isUndefined(a[1])) RuleValueV95.Text(s.str(a, 1)) else content
            val element = ruleValueElementV95(target, baseUrl)
            c.newArray(sc, advancedRuleElementsV95(element, s.str(a, 0)).map { ruleValueStringV95(RuleValueV95.Node(it)) }.toTypedArray<Any?>())
        }
        fn(java, "getElements") { c, sc, a ->
            val element = ruleValueElementV95(content, baseUrl)
            c.newArray(sc, advancedRuleElementsV95(element, s.str(a, 0)).map { s.toJs(c, sc, RuleValueV95.Node(it)) }.toTypedArray())
        }
        fn(java, "getElement") { c, sc, a ->
            val element = ruleValueElementV95(content, baseUrl)
            advancedRuleElementsV95(element, s.str(a, 0)).firstOrNull()?.let { s.toJs(c, sc, RuleValueV95.Node(it)) } ?: ""
        }
        fn(java, "setContent") { _, _, a ->
            content = RuleValueV95.Text(s.str(a, 0))
            if (a.size > 1) baseUrl = s.str(a, 1).ifBlank { baseUrl }
            ""
        }
        // Encoding
        fn(java, "base64Decode") { _, _, a ->
            val charset = (a.getOrNull(1) as? CharSequence)?.toString() ?: "UTF-8"
            String(decodeBase64(s.str(a, 0)), Charset.forName(charset))
        }
        fn(java, "base64Encode") { _, _, a -> Base64.getEncoder().encodeToString(s.str(a, 0).toByteArray()) }
        fn(java, "hexDecodeToString") { _, _, a -> String(hex(s.str(a, 0))) }
        fn(java, "hexEncodeToString") { _, _, a -> s.str(a, 0).toByteArray().joinToString("") { "%02x".format(it) } }
        fn(java, "md5Encode") { _, _, a -> digest(s.str(a, 0), "MD5") }
        fn(java, "md5Encode16") { _, _, a -> digest(s.str(a, 0), "MD5").substring(8, 24) }
        fn(java, "digestHex") { _, _, a -> digest(s.str(a, 0), s.str(a, 1).ifBlank { "MD5" }) }
        fn(java, "digestBase64Str") { _, _, a ->
            Base64.getEncoder().encodeToString(MessageDigest.getInstance(s.str(a, 1).ifBlank { "MD5" }).digest(s.str(a, 0).toByteArray()))
        }
        fn(java, "HMacHex") { _, _, a -> hmac(s.str(a, 0), s.str(a, 1), s.str(a, 2)).joinToString("") { "%02x".format(it) } }
        fn(java, "HMacBase64") { _, _, a -> Base64.getEncoder().encodeToString(hmac(s.str(a, 0), s.str(a, 1), s.str(a, 2))) }
        fn(java, "encodeURI") { _, _, a -> URLEncoder.encode(s.str(a, 0), s.str(a, 1).ifBlank { "UTF-8" }) }
        fn(java, "utf8ToGbk") { _, _, a -> String(s.str(a, 0).toByteArray(Charsets.UTF_8), Charset.forName("GBK")) }
        fn(java, "htmlFormat") { _, _, a -> htmlToTextV36(Jsoup.parse(s.str(a, 0)).body()) }
        fn(java, "timeFormat") { _, _, a ->
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(s.str(a, 0).toDoubleOrNull()?.toLong() ?: 0L))
        }
        fn(java, "timeFormatUTC") { _, _, a ->
            val format = SimpleDateFormat(s.str(a, 1).ifBlank { "yyyy-MM-dd HH:mm:ss" }, Locale.CHINA)
            format.timeZone = TimeZone.getTimeZone("GMT${(s.str(a, 2).toIntOrNull() ?: 0).let { if (it >= 0) "+$it" else "$it" }}")
            format.format(Date(s.str(a, 0).toDoubleOrNull()?.toLong() ?: 0L))
        }
        // Symmetric crypto (AES/DES/DESede) as in Legado's JsEncodeUtils
        listOf("aes" to "AES", "des" to "DES", "tripleDES" to "DESede").forEach { (prefix, algorithm) ->
            fn(java, "${prefix}DecodeToString") { _, _, a -> String(crypt(algorithm, a, Cipher.DECRYPT_MODE, autoDecode(s.str(a, 0)))) }
            fn(java, "${prefix}Base64DecodeToString") { _, _, a -> String(crypt(algorithm, a, Cipher.DECRYPT_MODE, decodeBase64(s.str(a, 0)))) }
            fn(java, "${prefix}EncodeToString") { _, _, a -> crypt(algorithm, a, Cipher.ENCRYPT_MODE, s.str(a, 0).toByteArray()).joinToString("") { "%02x".format(it) } }
            fn(java, "${prefix}EncodeToBase64String") { _, _, a -> Base64.getEncoder().encodeToString(crypt(algorithm, a, Cipher.ENCRYPT_MODE, s.str(a, 0).toByteArray())) }
        }
        fn(java, "tripleDESDecodeStr") { _, _, a -> String(cryptWith("DESede", s.str(a, 2), s.str(a, 1), s.str(a, 4), Cipher.DECRYPT_MODE, autoDecode(s.str(a, 0)))) }
        fn(java, "tripleDESDecodeArgsBase64Str") { _, _, a -> String(cryptWith("DESede", s.str(a, 2), s.str(a, 1), s.str(a, 4), Cipher.DECRYPT_MODE, decodeBase64(s.str(a, 0)))) }
        fn(java, "createSymmetricCrypto") { c, sc, a ->
            val transformation = s.str(a, 0)
            val key = s.str(a, 1)
            val iv = s.str(a, 2)
            val algorithm = transformation.substringBefore('/')
            val crypto = c.newObject(sc)
            fn(crypto, "decryptStr") { _, _, b -> String(cryptWith(algorithm, transformation, key, iv, Cipher.DECRYPT_MODE, autoDecode(s.str(b, 0)))) }
            fn(crypto, "decrypt") { _, _, b -> String(cryptWith(algorithm, transformation, key, iv, Cipher.DECRYPT_MODE, autoDecode(s.str(b, 0)))) }
            fn(crypto, "encryptBase64") { _, _, b -> Base64.getEncoder().encodeToString(cryptWith(algorithm, transformation, key, iv, Cipher.ENCRYPT_MODE, s.str(b, 0).toByteArray())) }
            fn(crypto, "encryptHex") { _, _, b -> cryptWith(algorithm, transformation, key, iv, Cipher.ENCRYPT_MODE, s.str(b, 0).toByteArray()).joinToString("") { "%02x".format(it) } }
            crypto
        }
        // Harmless device/UI helpers
        fn(java, "log") { _, _, a -> s.str(a, 0) }
        fn(java, "logType") { _, _, a -> s.str(a, 0) }
        fn(java, "toast") { _, _, _ -> "" }
        fn(java, "longToast") { _, _, _ -> "" }
        fn(java, "randomUUID") { _, _, _ -> UUID.randomUUID().toString() }
        fn(java, "androidId") { _, _, _ -> "langhuan" }
        fn(java, "deviceID") { _, _, _ -> "langhuan" }
        fn(java, "getWebViewUA") { _, _, _ -> SourceJsEngineV95.USER_AGENT }
        fn(java, "toNumChapter") { _, _, a -> s.str(a, 0) }
        fn(java, "t2s") { _, _, a -> s.str(a, 0) }
        fn(java, "s2t") { _, _, a -> s.str(a, 0) }
        fn(java, "getCookie") { _, _, _ -> "" }
        ScriptableObject.putProperty(global, "java", java)

        // source
        val sourceObj = cx.newObject(global)
        val src = source
        ScriptableObject.putProperty(sourceObj, "bookSourceUrl", src?.baseUrl.orEmpty())
        ScriptableObject.putProperty(sourceObj, "bookSourceName", src?.name.orEmpty())
        ScriptableObject.putProperty(sourceObj, "key", src?.baseUrl.orEmpty())
        ScriptableObject.putProperty(sourceObj, "loginUrl", src?.loginUrl.orEmpty())
        ScriptableObject.putProperty(sourceObj, "jsLib", src?.jsLib.orEmpty())
        fn(sourceObj, "getKey") { _, _, _ -> src?.baseUrl.orEmpty() }
        fn(sourceObj, "getVariable") { _, _, _ -> SourceVariablesV95.get(src, "__source_variable") }
        fn(sourceObj, "setVariable") { _, _, a -> SourceVariablesV95.put(src, "__source_variable", s.str(a, 0)); "" }
        fn(sourceObj, "put") { _, _, a -> s.str(a, 1).also { SourceVariablesV95.put(src, s.str(a, 0), it) } }
        fn(sourceObj, "get") { _, _, a -> SourceVariablesV95.get(src, s.str(a, 0)) }
        fn(sourceObj, "getLoginInfo") { _, _, _ -> null }
        fn(sourceObj, "getLoginInfoMap") { _, _, _ -> null }
        fn(sourceObj, "getLoginHeader") { _, _, _ -> null }
        fn(sourceObj, "getHeaderMap") { c, sc, _ -> SourceJsEngineV95.toJs(c, sc, src?.headers.orEmpty()) }
        ScriptableObject.putProperty(global, "source", sourceObj)

        // book / chapter
        val book = rules?.book
        val bookObj = cx.newObject(global)
        mapOf(
            "name" to book?.name, "author" to book?.author, "bookUrl" to book?.bookUrl, "tocUrl" to book?.bookUrl,
            "coverUrl" to book?.cover, "intro" to book?.intro, "latestChapterTitle" to book?.latest,
            "origin" to src?.baseUrl, "originName" to src?.name, "kind" to "",
        ).forEach { (k, v) -> ScriptableObject.putProperty(bookObj, k, v.orEmpty()) }
        fn(bookObj, "getVariable") { _, _, a -> SourceVariablesV95.get(src, "__book_" + (book?.bookUrl.orEmpty()) + s.str(a, 0)) }
        fn(bookObj, "putVariable") { _, _, a -> SourceVariablesV95.put(src, "__book_" + (book?.bookUrl.orEmpty()) + s.str(a, 0), s.str(a, 1)); "" }
        fn(bookObj, "get") { _, _, a -> SourceVariablesV95.get(src, s.str(a, 0)) }
        fn(bookObj, "put") { _, _, a -> s.str(a, 1).also { SourceVariablesV95.put(src, s.str(a, 0), it) } }
        ScriptableObject.putProperty(global, "book", bookObj)
        val chapter = rules?.chapter
        val chapterObj = cx.newObject(global)
        ScriptableObject.putProperty(chapterObj, "title", chapter?.title.orEmpty())
        ScriptableObject.putProperty(chapterObj, "url", chapter?.url.orEmpty())
        fn(chapterObj, "getVariable") { _, _, _ -> "" }
        ScriptableObject.putProperty(global, "chapter", chapterObj)

        // cookie / cache: no persistent jar; cache is in memory
        val cookieObj = cx.newObject(global)
        fn(cookieObj, "getCookie") { _, _, _ -> "" }
        fn(cookieObj, "getKey") { _, _, _ -> "" }
        fn(cookieObj, "setCookie") { _, _, _ -> "" }
        fn(cookieObj, "replaceCookie") { _, _, _ -> "" }
        fn(cookieObj, "removeCookie") { _, _, _ -> "" }
        ScriptableObject.putProperty(global, "cookie", cookieObj)
        val cacheObj = cx.newObject(global)
        fn(cacheObj, "get") { _, _, a -> SourceVariablesV95.get(src, "__cache_" + s.str(a, 0)) }
        fn(cacheObj, "getFromMemory") { _, _, a -> SourceVariablesV95.get(src, "__cache_" + s.str(a, 0)) }
        fn(cacheObj, "put") { _, _, a -> SourceVariablesV95.put(src, "__cache_" + s.str(a, 0), s.str(a, 1)); "" }
        fn(cacheObj, "putMemory") { _, _, a -> SourceVariablesV95.put(src, "__cache_" + s.str(a, 0), s.str(a, 1)); "" }
        fn(cacheObj, "delete") { _, _, a -> SourceVariablesV95.put(src, "__cache_" + s.str(a, 0), ""); "" }
        ScriptableObject.putProperty(global, "cache", cacheObj)
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is SourceJsTimeoutV95 || error is CancellationException || error is SourceJsNetworkDisabledV95 || error is InterruptedException) throw error
        if (Thread.currentThread().isInterrupted) throw CancellationException("书源脚本已取消")
    }

    private fun headersArg(value: Any?): Map<String, String> {
        if (value == null || Undefined.isUndefined(value)) return emptyMap()
        val obj = value as? Scriptable ?: return runCatching {
            parseSourceOptionsV94(value.toString()).mapValues { (_, v) -> jsonTextV95(v) }
        }.getOrDefault(emptyMap())
        return obj.ids.associate { id -> id.toString() to Context.toString(ScriptableObject.getProperty(obj, id.toString())) }
    }

    private fun request(raw: String, method: String? = null, body: String? = null, headers: Map<String, String> = emptyMap()): SourceTextResponseV95 {
        val base = baseUrl.ifBlank { source?.baseUrl.orEmpty() }
        val parsed = parseSourceRequestUrlV95(raw, base, source)
        val request = parsed.copy(
            method = method ?: parsed.method,
            body = body ?: parsed.body,
            headers = parsed.headers + headers,
        )
        return SourceJsEngineV95.fetch(rules, request)
    }

    private fun response(cx: Context, scope: Scriptable, response: SourceTextResponseV95): Scriptable {
        val obj = HostResponseV95(response)
        obj.parentScope = scope
        obj.prototype = ScriptableObject.getObjectPrototype(scope)
        fun fn(name: String, body: (Array<out Any?>) -> Any?) =
            ScriptableObject.putProperty(obj, name, LambdaFunction(scope, name, 0, Callable { _, _, _, args -> body(args) }))
        fn("body") { response.body }
        fn("code") { response.code }
        fn("url") { response.url }
        fn("header") { a -> response.headers.entries.firstOrNull { it.key.equals(SourceJsEngineV95.str(a, 0), true) }?.value }
        fn("headers") { SourceJsEngineV95.toJs(cx, scope, response.headers) }
        fn("cookies") { cx.newObject(scope) }
        fn("toString") { response.body }
        ScriptableObject.putProperty(obj, "raw", obj)
        return obj
    }

    private fun digest(data: String, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(data.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun hmac(data: String, algorithm: String, key: String): ByteArray {
        val name = algorithm.ifBlank { "HmacSHA256" }.let { if (it.startsWith("Hmac", true)) it else "Hmac$it" }
        return Mac.getInstance(name).apply { init(SecretKeySpec(key.toByteArray(), name)) }.doFinal(data.toByteArray())
    }

    private fun decodeBase64(text: String): ByteArray {
        val clean = text.trim().replace('-', '+').replace('_', '/')
        return Base64.getMimeDecoder().decode(clean)
    }

    private fun hex(text: String): ByteArray {
        val clean = text.trim()
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun autoDecode(text: String): ByteArray {
        val clean = text.trim()
        return if (clean.length % 2 == 0 && clean.isNotEmpty() && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) hex(clean)
        else decodeBase64(clean)
    }

    /** Legado argument order: (data, key, transformation, iv). */
    private fun crypt(algorithm: String, args: Array<out Any?>, mode: Int, data: ByteArray): ByteArray {
        val s = SourceJsEngineV95
        return cryptWith(algorithm, s.str(args, 2).ifBlank { "$algorithm/ECB/PKCS5Padding" }, s.str(args, 1), s.str(args, 3), mode, data)
    }

    private fun cryptWith(algorithm: String, transformation: String, key: String, iv: String, mode: Int, data: ByteArray): ByteArray {
        val name = if (algorithm.equals("tripleDES", true) || algorithm.equals("3DES", true)) "DESede" else algorithm
        val cipher = Cipher.getInstance(transformation.ifBlank { "$name/ECB/PKCS5Padding" }.replace("PKCS7Padding", "PKCS5Padding"))
        val keySpec = SecretKeySpec(key.toByteArray(), name)
        if (iv.isNotEmpty() && !transformation.contains("/ECB/", true)) cipher.init(mode, keySpec, IvParameterSpec(iv.toByteArray()))
        else cipher.init(mode, keySpec)
        return cipher.doFinal(data)
    }
}

/**
 * A Legado request URL: `url,{"method":"POST","body":"…","headers":{…},"charset":"gbk"}`.
 * Relative URLs resolve against [base]. Script templates are handled earlier by the caller.
 */
internal fun parseSourceRequestUrlV95(raw: String, base: String, source: BookSourceV36?): SourceRequestV36 {
    val text = raw.trim()
    val optionIndex = Regex(",\\s*(?=\\{)").find(text)?.range?.first ?: -1
    val urlPart = if (optionIndex > 0) text.substring(0, optionIndex).trim() else text
    val options = if (optionIndex > 0) parseSourceOptionsV94(text.substring(optionIndex + 1)) else null
    // Request-rewriting scripts (js/bodyJs/webJs), custom DNS/proxy and server ids are not run.
    val unsupported = options?.keys.orEmpty() - setOf("method", "body", "charset", "headers", "type", "retry", "webView")
    require(unsupported.isEmpty()) { "请求包含不支持的选项：$unsupported" }
    val webView = options?.get("webView")?.let { jsonTextV95(it) }?.let { it.isNotBlank() && it != "false" && it != "0" } == true
    require(!webView || source?.useBrowser == true) { "请求要求网页视图（webView），暂不支持；可在书源中开启浏览器模式" }
    val charset = options?.get("charset")?.let(::jsonTextV95)?.takeIf { it.isNotBlank() }
    val method = options?.get("method")?.let(::jsonTextV95)?.uppercase()?.ifBlank { null } ?: "GET"
    require(method in setOf("GET", "POST", "HEAD")) { "书源仅支持 GET、POST、HEAD 请求" }
    val bodyElement = options?.get("body")
    val body = bodyElement?.let { if (it is JsonObject || it is JsonArray) it.toString() else jsonTextV95(it) }
    val headers = when (val h = options?.get("headers")) {
        null -> emptyMap()
        is JsonObject -> h.mapValues { (_, v) -> jsonTextV95(v) }
        else -> runCatching { parseSourceOptionsV94(jsonTextV95(h)).mapValues { (_, v) -> jsonTextV95(v) } }
            .getOrElse { throw IllegalArgumentException("headers 请求选项必须是对象") }
    }
    val url = resolveUrlV36(base, encodeUnsafeUrlCharsV95(urlPart, charset))
    publicSourceUrlV36(url)
    return SourceRequestV36(url, method, body, charset, headers.filterKeys { it.isNotBlank() })
}

/** Percent-encodes characters that are not legal in a URL (e.g. a Chinese keyword produced by a script). */
internal fun encodeUnsafeUrlCharsV95(url: String, charset: String?): String {
    if (url.all { it.code in 0x21..0x7e && it != '"' && it != '<' && it != '>' && it != '\\' && it != '^' && it != '`' && it != '{' && it != '|' && it != '}' }) return url
    val cs = runCatching { Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
    val out = StringBuilder()
    url.forEach { c ->
        if (c.code in 0x21..0x7e && c !in "\"<>\\^`{|}") out.append(c)
        else String(charArrayOf(c)).toByteArray(cs).forEach { b -> out.append('%').append("%02X".format(b.toInt() and 0xff)) }
    }
    return out.toString()
}
