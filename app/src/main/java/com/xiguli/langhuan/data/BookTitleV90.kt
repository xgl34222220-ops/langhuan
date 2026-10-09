package com.xiguli.langhuan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Longest book title kept; long enough for 「书名（副标题）(来源)」 style imported names. */
const val BOOK_TITLE_MAX_V90 = 80

/**
 * Cleans a title typed by the user: trims it, folds line breaks and runs of whitespace into a
 * single space and caps the length. Returns null when nothing printable is left.
 */
fun normalizeBookTitleV90(raw: String): String? {
    val folded = raw.replace(Regex("[\\s\\u3000]+"), " ").trim()
    if (folded.isEmpty()) return null
    val cps = folded.codePointCount(0, folded.length)
    if (cps <= BOOK_TITLE_MAX_V90) return folded
    return folded.substring(0, folded.offsetByCodePoints(0, BOOK_TITLE_MAX_V90)).trimEnd()
}

private val TitleJsonV90 = Json { ignoreUnknownKeys = true }

/**
 * Returns [snapshotJson] with only `novel.title` replaced. The JSON tree is edited in place so
 * every other field (including ones this build does not know about) is written back untouched.
 */
fun renameSnapshotJsonV90(snapshotJson: String, title: String): String {
    val root = TitleJsonV90.parseToJsonElement(snapshotJson).jsonObject
    val novel = root["novel"]?.jsonObject ?: error("作品数据缺少 novel 字段")
    val renamed = JsonObject(novel + ("title" to JsonPrimitive(title)))
    return JsonObject(root + ("novel" to renamed)).toString()
}
