from pathlib import Path

path = Path('app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt')
text = path.read_text(encoding='utf-8')

old_palette = 'private data class ReaderExperiencePalette(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)'
new_palette = 'internal data class ReaderExperiencePalette(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)'
if old_palette in text:
    text = text.replace(old_palette, new_palette, 1)

old_call = 'ReaderMatureChrome(\n                    modifier = Modifier.align(Alignment.Center),'
new_call = 'ReaderQingmoChrome(\n                    modifier = Modifier.align(Alignment.Center),'
if old_call not in text:
    raise SystemExit('ReaderMatureChrome call anchor not found')
text = text.replace(old_call, new_call, 1)

path.write_text(text, encoding='utf-8')
