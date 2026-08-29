from pathlib import Path

path = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
text = path.read_text()
old_calls = 'appendDelta(buffer, delta, onDelta)'
count = text.count(old_calls)
if count != 4:
    raise SystemExit(f'expected 4 appendDelta calls, got {count}')
text = text.replace(old_calls, 'appendDelta(buffer, delta, onDelta, prompt.jsonMode)')
old = '''    private fun appendDelta(buffer: StringBuilder, delta: String?, onDelta: (String) -> Unit) {
        if (delta.isNullOrEmpty()) return
        buffer.append(delta)
        onDelta(chapterContentPreview(buffer.toString()))
    }
'''
new = '''    private fun appendDelta(
        buffer: StringBuilder,
        delta: String?,
        onDelta: (String) -> Unit,
        structuredJson: Boolean,
    ) {
        if (delta.isNullOrEmpty()) return
        buffer.append(delta)
        // Novel prose and normal chat are plain text: show the actual cumulative text immediately.
        // Legacy structured streaming still extracts the JSON content field for a readable preview.
        onDelta(if (structuredJson) chapterContentPreview(buffer.toString()) else buffer.toString())
    }
'''
if old not in text:
    raise SystemExit('appendDelta helper not found')
text = text.replace(old, new, 1)
path.write_text(text)
