package com.xiguli.langhuan.ui

import java.net.URI

data class ExternalBookRequestV1(val uri: String, val mimeType: String? = null)

/** A request stays in the queue through confirmation, import and result dismissal. */
internal class ExternalBookImportQueueV1(restored: List<ExternalBookRequestV1> = emptyList()) {
    private val requests = mutableListOf<ExternalBookRequestV1>()
    init {
        // SavedStateHandle default arguments can originate in an untrusted launch Intent.
        // Restoration must cross exactly the same validation boundary as a fresh delivery.
        restored.take(4).forEach { request ->
            runCatching { offer("android.intent.action.VIEW", request.uri, request.mimeType) }
        }
    }
    fun snapshot(): List<ExternalBookRequestV1> = requests.toList()

    fun offer(action: String?, uri: String?, mimeType: String?): Boolean {
        require(action == "android.intent.action.VIEW" || action == "android.intent.action.SEND") {
            "请选择一个小说文件再打开或分享"
        }
        require(uri != null && uri.length <= 8192) { "文件地址无效，请重新选择小说文件" }
        val parsed = runCatching { URI(uri) }.getOrNull()
        require(parsed != null && parsed.scheme.equals("content", ignoreCase = true) &&
            !parsed.rawAuthority.isNullOrBlank() && parsed.rawUserInfo == null) {
            "请通过文件管理器打开或分享本地小说文件；不支持网页链接或文件路径"
        }
        if (requests.any { it.uri == uri }) return false
        require(requests.size < 4) { "请先处理当前的小说导入，再分享其他文件" }
        requests += ExternalBookRequestV1(uri, mimeType?.take(160))
        return true
    }

    fun dismiss(uri: String) { requests.removeAll { it.uri == uri } }
}
