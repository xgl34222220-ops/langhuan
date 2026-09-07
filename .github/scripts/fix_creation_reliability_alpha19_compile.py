from pathlib import Path

p = Path("app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
s = p.read_text()
old = '''    val causes = generateSequence<Throwable?>(error) { it.cause }.filterNotNull().take(8).toList()
    val localSocketTimeout = causes.any { it is java.net.SocketTimeoutException }
'''
new = '''    val causes = mutableListOf<Throwable>()
    var cursor: Throwable? = error
    while (cursor != null && causes.size < 8) {
        causes += cursor
        cursor = cursor.cause
    }
    val localSocketTimeout = causes.any { it is java.net.SocketTimeoutException }
'''
if old not in s:
    raise AssertionError("generated cause traversal not found")
p.write_text(s.replace(old, new, 1))
