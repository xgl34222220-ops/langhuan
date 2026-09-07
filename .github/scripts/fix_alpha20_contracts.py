from pathlib import Path

conversation = Path("app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
text = conversation.read_text()
old_overlap = "for (size in maxOverlap downTo 6)"
if old_overlap not in text:
    raise SystemExit("continuation overlap source not found")
conversation.write_text(text.replace(old_overlap, "for (size in maxOverlap downTo 3)", 1))

alpha20 = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderChatAlpha20ContractTest.kt")
text = alpha20.read_text().replace("app/src/main/", "src/main/")
alpha20.write_text(text)

legacy = Path("app/src/test/java/com/xiguli/langhuan/ui/QingmoReplicaReaderContractTest.kt")
text = legacy.read_text()
old = '''        listOf(
            "排版预设", "主题", "字体", "字号", "行段", "定位",
            "上下翻页", "仿真翻页", "全文搜索", "音量键翻页", "屏幕常亮",
            "时间电量", "沉浸式", "点击动画", "下拉书签", "全屏下一页",
            "背景图遮罩", "背景跟随", "状态栏", "导航栏", "锁定竖屏",
        ).forEach { label -> assertTrue("missing reader action: $label", reader.contains("\\\"$label\\\"")) }'''
new = '''        listOf(
            "排版预设", "主题", "字体", "字号", "行距页边距", "滚动阅读",
            "全文搜索", "音量键翻页", "屏幕常亮", "时间电量", "沉浸式", "锁定竖屏",
        ).forEach { label -> assertTrue("missing reader action: $label", reader.contains("\\\"$label\\\"")) }
        val visibleActions = reader.substringAfter("val actions = listOf(").substringBefore("LazyVerticalGrid")
        listOf(
            "定位", "上下翻页", "仿真翻页", "点击动画", "下拉书签", "全屏下一页",
            "背景图遮罩", "背景跟随", "状态栏", "导航栏",
        ).forEach { label -> assertFalse("legacy reader action still exposed: $label", visibleActions.contains("\\\"$label\\\"")) }'''
if old not in text:
    raise SystemExit("legacy action contract block not found")
text = text.replace(old, new, 1)
old_threshold = "snapPositionalThreshold = 0.15f"
if old_threshold not in text:
    raise SystemExit("old pager threshold contract not found")
text = text.replace(old_threshold, "snapPositionalThreshold = 0.32f", 1)
legacy.write_text(text)
