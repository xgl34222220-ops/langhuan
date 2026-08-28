from pathlib import Path

p = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
s = p.read_text()
bad = '''        val content = if (attachments.isBlank()) text else "$text
$attachments"
'''
good = '''        val content = if (attachments.isBlank()) text else "$text\\n$attachments"
'''
assert bad in s, 'native chat attachment string anchor missing'
s = s.replace(bad, good, 1)
p.write_text(s)
