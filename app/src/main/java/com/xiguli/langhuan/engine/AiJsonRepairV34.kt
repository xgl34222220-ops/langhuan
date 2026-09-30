package com.xiguli.langhuan.engine

/**
 * Turns "almost JSON" from a model into JSON a strict decoder accepts.
 *
 * Handles what actually breaks book creation in practice:
 * - prose or ``` fences around the object;
 * - raw newlines / tabs inside strings;
 * - trailing commas before } or ];
 * - output cut off mid-way by the token limit (open strings, half-written keys, missing closers).
 *
 * It never invents content: a truncated tail is dropped back to the last complete value.
 */
internal fun repairModelJsonV34(raw: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)(```|$)").find(raw)?.groupValues?.get(1)
    val source = (fenced?.takeIf { it.contains('{') } ?: raw)
    val start = source.indexOf('{')
    if (start < 0) return source.trim()

    val out = StringBuilder()
    // Each frame: '{' or '['; for objects, whether the next string is a key.
    val stack = ArrayList<Char>()
    val expectKey = ArrayList<Boolean>()
    var inString = false
    var escape = false
    var stringIsKey = false
    var keyStart = -1
    // Start of the last complete key whose value has not begun yet (-1 when none).
    var pendingKeyStart = -1
    var closed = false

    fun trimTrailingComma() {
        var i = out.length - 1
        while (i >= 0 && out[i].isWhitespace()) i--
        if (i >= 0 && out[i] == ',') out.setLength(i)
    }

    var index = start
    while (index < source.length) {
        val c = source[index]
        if (inString) {
            when {
                escape -> { out.append(c); escape = false }
                c == '\\' -> { out.append(c); escape = true }
                c == '"' -> {
                    out.append(c)
                    inString = false
                    if (stringIsKey) pendingKeyStart = keyStart
                }
                c == '\n' -> out.append("\\n")
                c == '\r' -> Unit
                c == '\t' -> out.append("\\t")
                else -> out.append(c)
            }
        } else {
            // Anything but whitespace or ':' after a key means its value has started.
            if (pendingKeyStart >= 0 && !c.isWhitespace() && c != ':' && !(c == '"' && stack.lastOrNull() == '{' && expectKey.lastOrNull() == true)) {
                pendingKeyStart = -1
            }
            when (c) {
                '"' -> {
                    inString = true
                    stringIsKey = stack.lastOrNull() == '{' && expectKey.lastOrNull() == true
                    if (stringIsKey) keyStart = out.length
                    out.append(c)
                }
                '{', '[' -> {
                    stack += c
                    expectKey += (c == '{')
                    out.append(c)
                }
                '}', ']' -> {
                    trimTrailingComma()
                    if (stack.isNotEmpty()) {
                        stack.removeAt(stack.lastIndex)
                        expectKey.removeAt(expectKey.lastIndex)
                    }
                    out.append(c)
                    if (stack.isEmpty()) {
                        closed = true
                        break
                    }
                }
                ':' -> {
                    if (expectKey.isNotEmpty()) expectKey[expectKey.lastIndex] = false
                    out.append(c)
                }
                ',' -> {
                    if (stack.lastOrNull() == '{') expectKey[expectKey.lastIndex] = true
                    out.append(c)
                }
                else -> out.append(c)
            }
        }
        index++
    }
    if (closed) return out.toString()

    // Truncated. Drop a half-written key entirely; close a half-written value string.
    if (inString) {
        if (stringIsKey && keyStart >= 0) {
            out.setLength(keyStart)
        } else {
            if (escape) out.setLength(out.length - 1)
            out.append('"')
        }
    }
    // A key with no value yet (`"name"` or `"name":`) is dropped together with its comma.
    if (!inString && pendingKeyStart >= 0 && stack.lastOrNull() == '{') out.setLength(pendingKeyStart)
    val tail = out.toString().trimEnd().removeSuffix(",").trimEnd()
    val closers = stack.asReversed().joinToString("") { if (it == '{') "}" else "]" }
    return tail + closers
}
