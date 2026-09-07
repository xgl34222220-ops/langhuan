from pathlib import Path

p = Path('tools/apply_wire_spatial2.py')
s = p.read_text()

# In the patch source this phrase occurs exactly twice in the streamOpenAi replacement:
# once as the regex boundary and once as the replacement tail. The original boundary crossed
# over openAiBody/callAnthropic; both must instead stop at the immediately following openAiBody.
needle = 'private suspend fun streamAnthropic'
assert s.count(needle) >= 2, f'unexpected stream boundary count: {s.count(needle)}'
s = s.replace(needle, 'private fun openAiBody', 2)

old_visible = '''private fun openAiStreamVisibleText(root: JsonObject): String {
    val choice = root["choices"].asObjects().firstOrNull()
    val message = choice?.get("message") as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    return delta?.get("content").textPayload()
        ?: message?.get("content").textPayload()
        ?: choice?.get("text").textPayload()
        ?: if (root.string("type")?.contains("output_text.delta", ignoreCase = true) == true) root["delta"].textPayload() else null
        ?: root["output_text"].textPayload()
        ?: root["output"].textPayload()
        ?: ""
}
'''
new_visible = '''private fun openAiStreamVisibleText(root: JsonObject): String {
    val choice = root["choices"].asObjects().firstOrNull()
    val message = choice?.get("message") as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    delta?.get("content").textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    message?.get("content").textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    choice?.get("text").textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    if (root.string("type")?.contains("output_text.delta", ignoreCase = true) == true) {
        root["delta"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    }
    root["output_text"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    root["output"].textPayload()?.takeIf(String::isNotBlank)?.let { return it }
    return ""
}
'''
assert old_visible in s, 'nullable stream helper not found'
s = s.replace(old_visible, new_visible, 1)

old_cleanup = "Path('tools/apply_wire_spatial2.py').unlink(missing_ok=True)\n"
new_cleanup = old_cleanup + "Path('tools/fix_wire_patch_script.py').unlink(missing_ok=True)\n"
assert old_cleanup in s, 'cleanup marker not found'
s = s.replace(old_cleanup, new_cleanup, 1)

p.write_text(s)
