from pathlib import Path

reader = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = reader.read_text()
# Keep the established preset names so existing UI/tests/book settings stay recognizable;
# only their typography values/descriptions change in alpha22.
for old, new in [
    ('"大字紧凑"', '"番茄小说风格"'),
    ('"纸白舒展"', '"微信读书风格"'),
    ('"经典网文"', '"起点阅读风格"'),
    ('"宋体纸书"', '"掌阅风格"'),
    ('"舒适大字"', '"舒适阅读"'),
]:
    if old not in s:
        raise AssertionError(f"missing alpha22 preset label: {old}")
    s = s.replace(old, new, 1)
reader.write_text(s)

contract = Path("app/src/test/java/com/xiguli/langhuan/ui/QingmoReplicaReaderContractTest.kt")
t = contract.read_text()
old = 'assertTrue(reader.contains("Spacer(Modifier.height(8.dp))"))'
new = 'assertTrue(reader.contains("Spacer(Modifier.height(4.dp))"))'
if old not in t:
    raise AssertionError("old footer-gap contract not found")
t = t.replace(old, new, 1)
contract.write_text(t)
