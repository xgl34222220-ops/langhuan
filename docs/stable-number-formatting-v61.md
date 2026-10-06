# 稳定数值显示 V61

继续 PR #104 原分支 `fix/ai-source-browser-session-20261003`。第五批 `bcc96f63` 已完整验收：Reader 148、Browser 16、JVM 714 均为零失败/零跳过，Debug 与 lint 通过（0 错误、196 警告、5 提示），EPUB 与浏览器两种实际 force-stop 独立进程恢复通过。旧结果不替代本批新提交验收。

## 第五批窗口观察复核

第五批报告曾把固定版式恢复长图的缩放预览误判为“顶栏未显示”。重新直接读取 artifact 11293337399 中原始 `v60-fixed-renderer-restored.png`（1080×2400，SHA-256 `fc08b201cf740cf7d3d2c919ef5e2752b5b2b06f64bb2d8392701ee2a686d79e`）并检查顶部 530 像素后，返回按钮、书名、`EPUB 原版`、原版/文字版切换、目录、第二章标题和书签均完整可见。顶部区域还实际包含 primary `(30,106,90)` 420 个、gold `(138,83,18)` 776 个精确像素。这个观察不成立，因此不改 Compose/Readium 布局，不增加无依据的裁剪或层级逻辑；PR 保留更正记录。

## 实际 lint 缺口与采用理由

剩余唯一 `StaticFieldLeak` 指向 `BookSourceBrowserV38` 的应用级 transport。构造时已经强制使用 `context.applicationContext`，其生命周期有意与应用一致，用于跨页面及进程恢复后的浏览器配置；现有真实 Cookie/DOM 会话回归依赖该语义。本批不为消除告警破坏会话，也不把该警告虚报为已修复。

两条 `DefaultLocale` 则有实际用户可见影响：阅读设置的字号/行距等一位小数，以及运行中心不足十秒的耗时，原来使用没有显式 Locale 的 `String.format`。在法语等逗号小数区域会显示 `1,5` / `9,8s`，与应用其余稳定技术数值的点号表示不一致；使用不同数字字形的区域还可能改变字符。新增单一 `stableOneDecimalV61`，固定使用语言/国家中立的 `Locale.ROOT`，两个入口共用，保持一位小数和原有阈值、单位及 UI 不变。

新增 JVM 回归不修改全局 Locale：先证明法语格式不是 `1.5`，再断言公共格式器输出 `1.5` 与 `9.8s`。这样不会污染并行测试进程。没有修改阅读进度、文件、书签、草稿、数据库、浏览器会话或网络规则。

本批预期 JVM 从 714 增至 715；未筛选 Reader 仍为 148、Browser 为 16。Debug、lint、两种实际 force-stop 恢复及全部既有方法身份必须在新提交重新核对；预期 lint 变化不能写成已通过结果，以新 CI 原始 XML 为准。

## 参考

- [Android：支持不同语言和文化](https://developer.android.com/training/basics/supporting-devices/languages#format-numbers)：默认数字格式可能使用本地数字和分隔符；需要稳定 ASCII 数字时应给 `String.format` 显式 Locale。
- [Java 17 `Locale.ROOT`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html#ROOT)：语言/国家中立的根 Locale，适合不应随用户区域改变的技术格式。
- Readium 固定版本 `1b1f6b308a7b6f968b2bf1e66c84912879466f75` 及 [BSD-3-Clause](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/LICENSE) 未因错误窗口观察而修改或包裹；Legado 固定版本 `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` / GPL-3.0 仅作为既有行为参考，没有移植代码。

真实 AI、第三方登录/验证码、真机及 API 28 实际渲染回收仍未验证；不绕过限制，不操作用户设备或扩大权限。
