from pathlib import Path
import re

page = Path('app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt')
s = page.read_text()

context_marker = '    val context = LocalContext.current.applicationContext\n'
if 'webResearchEnabled' not in s:
    assert context_marker in s
    s = s.replace(
        context_marker,
        context_marker
        + '    val researchPrefs = remember(context) { context.getSharedPreferences("creation_research", 0) }\n'
        + '    var webResearchEnabled by remember { mutableStateOf(researchPrefs.getBoolean("web_research_enabled", false)) }\n',
        1,
    )

s = s.replace(
    '        if (!research.shouldResearch(text)) {',
    '        if (!webResearchEnabled || !research.shouldResearch(text)) {',
    1,
)

column_marker = '                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n'
if 'Text("联网搜索"' not in s:
    assert column_marker in s
    switch_ui = '''                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("联网搜索", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (webResearchEnabled) "已开启 · 只有明确说‘搜/查/联网’才会访问网页" else "已关闭 · 所有消息只使用 AI、附件和已有研究记忆",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = webResearchEnabled,
                            onCheckedChange = { checked ->
                                webResearchEnabled = checked
                                researchPrefs.edit().putBoolean("web_research_enabled", checked).apply()
                                if (!checked) {
                                    lastSources = emptyList()
                                    lastTargets = emptyList()
                                    researchMessage = null
                                }
                            },
                            enabled = !researching,
                        )
                    }
'''
    s = s.replace(column_marker, column_marker + switch_ui, 1)

page.write_text(s)

engine = Path('app/src/main/java/com/xiguli/langhuan/engine/WebResearchEngine.kt')
s = engine.read_text()
start = s.index('    fun shouldResearch(text: String): Boolean {')
end = s.index('    /** Extract an explicit author', start)
strict = '''    fun shouldResearch(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        // 联网是显式能力。普通“小说 / 作品 / 资料 / 参考 / 融合”等创作词不再触发搜索。
        // 即使联网总开关开启，也必须明确要求“搜 / 查 / 联网”才访问网页。
        val explicitWebActions = listOf(
            "联网搜", "联网查", "联网搜索", "联网查询", "联网看看",
            "搜一下", "搜索一下", "搜索", "搜搜", "继续搜", "再搜",
            "查一下", "查查", "查询一下", "查网页", "查资料", "网页搜索", "网页查询",
            "上网查", "网上查", "网上搜索", "检索一下", "公开资料搜索",
        )
        return explicitWebActions.any(value::contains)
    }

'''
s = s[:start] + strict + s[end:]
engine.write_text(s)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text()
g = re.sub(r'versionCode = \d+', 'versionCode = 47', g, count=1)
g = re.sub(r'versionName = "[^"]+"', 'versionName = "0.24.8-alpha01"', g, count=1)
gradle.write_text(g)
