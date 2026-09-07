from pathlib import Path

p = Path('tools/apply_wire_spatial2.py')
s = p.read_text()

# The first patch version bounded streamOpenAi by streamAnthropic, but openAiBody/callAnthropic
# live between those methods. Bound it by the immediately following openAiBody instead.
old_pattern_tail = r'''    \\}\n\n    private suspend fun streamAnthropic','''
new_pattern_tail = r'''    \\}\n\n    private fun openAiBody','''
assert old_pattern_tail in s, 'old stream regex boundary not found'
s = s.replace(old_pattern_tail, new_pattern_tail, 1)

old_new_tail = "    private suspend fun streamAnthropic'''\n"
new_new_tail = "    private fun openAiBody'''\n"
assert old_new_tail in s, 'old stream replacement tail not found'
s = s.replace(old_new_tail, new_new_tail, 1)

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

# Ensure the final successful patch removes both temporary scripts.
old_cleanup = "Path('tools/apply_wire_spatial2.py').unlink(missing_ok=True)\n"
new_cleanup = old_cleanup + "Path('tools/fix_wire_patch_script.py').unlink(missing_ok=True)\n"
assert old_cleanup in s, 'cleanup marker not found'
s = s.replace(old_cleanup, new_cleanup, 1)

p.write_text(s)
