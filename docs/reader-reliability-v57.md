# PR #104 后续可靠性改进

沿用 `fix/ai-source-browser-session-20261003`，保持现有 V3 界面、阅读位置、原文件、脚本安全、书签隔离、草稿取消及独立副本断言。

## 已保留的 f52c04d 完整验收

| 项目 | 当时的新结果 | 固定证据 |
| --- | --- | --- |
| 完整阅读器 | 137 通过、0 失败、0 跳过；原 16 项逐项通过；EPUB seed/force-stop/restore 两阶段通过 | [Reader CI](https://github.com/xgl34222220-ops/langhuan/actions/runs/37159576749) |
| 浏览器 | 7 通过、0 失败、0 跳过；独立进程 Cookie/DOM 存储恢复通过 | [Browser CI](https://github.com/xgl34222220-ops/langhuan/actions/runs/37159576715) |
| 构建与 JVM | 714 通过、0 失败、0 跳过；Debug 构建通过；lint 0 错误、201 警告、5 提示 | [Android CI](https://github.com/xgl34222220-ops/langhuan/actions/runs/37159576770) |

这些记录保留为基线，后续批次分别核对新提交的完整 CI。

## 201 条 lint 的实际影响评估

| 类型 | 基线数量 | 处理判断 |
| --- | --- | --- |
| MissingOnRenderProcessGone | 6 | 多条指向同一实现位置，涉及浏览器和 EPUB 两个 WebViewClient。渲染器退出可能连带宿主进程退出；先补浏览器真实故障恢复。EPUB 需要协调 Readium 多页宿主和 Locator，仍独立跟进。 |
| StaticFieldLeak | 3 | 服务和验证 Activity 的静态强引用改为弱引用，并释放验证页引用；应用级 transport 已使用 applicationContext，生命周期与应用一致，保留它并如实记录剩余警告。 |
| ConfigurationScreenWidthHeight / FrequentlyChangingValue | 15 / 6 | 需区分正在使用的阅读器与历史实现，结合窗口改变及滚动行为验证，避免仅替换 API 后改变分页位置。 |
| ApplySharedPref | 12 | 多处 commit 用于确认落盘成功及数据事务。不能换成 apply 来消除警告而丢掉失败反馈。 |
| UseKtx / 依赖更新等 | 94 / 其余 | 先处理可靠性与用户可见错误；不以批量语法或版本修改冒充功能修复。 |

## 第一批：浏览器渲染异常与生命周期

Android 要求渲染器退出后清理指定 WebView 并返回已处理；该实例不可继续调用加载或复用。浏览器现在先清空当前实例引用，移出视图树并销毁，再取消轮询与超时、结束验证页、向等待请求返回明确可重试错误。下一请求在同一资料目录创建新 WebView；Cookie 与 DOM 存储不被清空。过期实例的页面/错误回调不能操作当前请求。

服务与验证 Activity 的进程内查找改用弱引用，Activity 销毁时清空容器和 WebView 引用，避免静态持有整个界面。现有 applicationContext transport、15 秒空闲释放服务及 IPC 大小限制保留。

新增设备用例 `rendererExitWhileVerifyingReleasesTheWaitAndRetryKeepsTheProfile`：先通过真实 WebView 保存随机持久 Cookie 与 localStorage，再打开原创验证页；仅对当前 debug 保留域名 fixture 调用 Android 的真实渲染进程终止 API。断言等待线程及时收到 IOException、应用 PID 不变、错误准确；再用新 WebView 读取并断言随机 Cookie/localStorage 都恢复。没有模拟 onRenderProcessGone 回调来代替真实故障。

故障入口只在 debuggable 包、当前 fixture 请求、`browser-fixture.example` 和匹配请求 ID 下有效；服务继续不导出，不增加界面入口、站点权限或网页桥接。此用例进入浏览器整组和完整阅读器，预检通过后仍执行原完整组及两种真实进程恢复。

第一批提交 `1b7be1d60cb1eb9a3b22655e169f352961e04b79` 的完整 CI：

| 项目 | 新结果 | 证据 |
| --- | --- | --- |
| 阅读器 | 完整 138 项及 EPUB 实际进程恢复通过；原 137 项与原 16 项失败保留并核对 | [Reader](https://github.com/xgl34222220-ops/langhuan/actions/runs/37162038341) |
| 浏览器 | 8 项、0 失败、0 跳过；新 rendererExit 用例及独立进程 Cookie/DOM 恢复通过 | [Browser](https://github.com/xgl34222220-ops/langhuan/actions/runs/37162038331) |
| JVM / 构建 / lint | 714 项、0 失败、0 跳过；构建成功；0 错误、197 警告、5 提示 | [Android](https://github.com/xgl34222220-ops/langhuan/actions/runs/37162038316) |

两条服务/Activity 静态强引用告警已消除；仍有应用单例 1 条、EPUB 2 条及浏览器匿名类位置的渲染告警 2 条。后者的实际退出回调已由真实终止用例证明执行，告警仍保留并继续核对检测器识别条件，未加抑制。12 条 commit 告警也继续保留数据落盘确认语义。

## 第二批：异常书源、慢网停止和定向重试

V50 书城此前未展示搜索失败摘要；全部书源失败或用户停止后可能仍显示「没有找到」。搜索现在记录逐书源的失败原因和未完成 ID，明确区分失败、主动停止及全部请求成功但没有匹配结果。失败与停止不作为小说不存在的证据。

重试针对同一个已执行关键词，仅重新请求失败或未完成的书源，保留成功行和成功请求计数。新关键词仍开始完整新搜索；既有 generation 检查和 runInterruptible 取消异常传播保留，迟到结果不能更新新请求。取消不记为书源失败。来源配置不被改写，站点冷却规则不变，也没有自动重试循环。

界面复用现有 V3 消息卡片、1px 描边与颜色，显示本次关键词、最多三个有界失败原因和「重试未完成书源」；读取中仍使用原停止按钮。当前页面完整显示数据来自真实 ViewModel，未使用占位计数。

新增 `SourceSearchRecoveryV57DeviceTest` 三项，走真实 ViewModel、默认书源引擎、WebView 与 V50 可见按钮：合成 503 失败后仅重试坏源且稳定源请求次数不变；阻塞的合法慢样例被停止后结果保留，仅继续未完成源；全部失败和成功空搜索显示不同结果。每项核对已保存书源原始字符串不变，取消所有测试 ViewModel 工作后还原原样例。

三项进入浏览器整组和阅读器完整组；完整验收继续包含原阅读/数据断言、两种实际进程恢复、JVM 和 lint，新结果记录在 PR，不以第一批通过代替。

## 参考、许可证与采用理由

- [Legado Sigma BackstageWebView 源码](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/help/http/BackstageWebView.kt)：取消时释放实例/回调、检查当前 WebView 后再接受结果。参考其生命周期和迟到回调隔离思路，独立实现本应用的服务 IPC 恢复；没有引入其规则 JavaScript 或原生接口。[GPL-3.0 许可证](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/LICENSE)。
- [Legado SearchModel](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/model/webBook/SearchModel.kt)与[SearchViewModel](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/ui/book/search/SearchViewModel.kt)：流式合并结果、取消/完成的独立通知与工作状态控制。第二批采用明确状态和保留已完成工作的交互原则，独立实现按来源 ID 定向重试；不替换现有书源规则或 UI 系统。许可证同上。
- [Readium Kotlin Navigator 源码](https://github.com/readium/kotlin-toolkit/blob/develop/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt)及[导航文档](https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/navigator/navigator.md)：多页 Fragment、当前 Locator 和视图生命周期需要协调，不能给 EPUB 简单套浏览器重建策略。[BSD-3-Clause 许可证](https://github.com/readium/kotlin-toolkit/blob/develop/LICENSE)。本批保留现有 SDK 与原版阅读断言。
- [Android WebViewClient.onRenderProcessGone](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail))及[WebViewRenderProcess.terminate](https://developer.android.com/reference/android/webkit/WebViewRenderProcess#terminate())：明确要求处理退出并清理不可复用的实例；用于实际故障注入与新实例恢复验证。

真实在线 AI、第三方站点登录/验证码和用户真机仍未实测。CI 仅在原有隔离模拟器与合法样例执行；不绕过网站验证、不扩大网页权限、不合并、不正式发布或部署。
