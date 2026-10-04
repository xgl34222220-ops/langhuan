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

搜索实现提交 `af8b1cc` 经必要字号输入修正到 `b5545b3`，本轮独立验收：

| 项目 | 新结果 | 证据 |
| --- | --- | --- |
| 阅读器 | attempt 2 完整141项、0失败、0跳过；原137方法和原16失败逐项通过；预检11/编辑2/创作11通过；EPUB实际进程恢复通过 | [Reader](https://github.com/xgl34222220-ops/langhuan/actions/runs/37167664945) |
| 浏览器 | 11 项、0 失败、0 跳过；两个实际进程阶段的新 PID、Cookie/DOM 断言通过 | [Browser](https://github.com/xgl34222220-ops/langhuan/actions/runs/37167664909) |
| JVM / 构建 / lint | 714 项、0 失败、0 跳过；构建成功；0 错误、197 警告、5 提示 | [Android](https://github.com/xgl34222220-ops/langhuan/actions/runs/37167664937) |

Reader attempt 2 artifact 11290881646 的 SHA-256 为 `1ba7e622eec41f9847c39060720ba3501fcb3dd3ffcd408e269e3d7ed1dcbff2`，原始完整 XML 与实际新 PID/Locator 两阶段恢复已核对。浏览器 artifact 11290367520 的 SHA-256 为 `dd54776a9d1de55eb83b776b57887f94ae397d673b1efcf5ec2893e376f609e7`，JVM/lint artifact 11289722677 为 `f08b07495a1f4ef26a1ced5068d72fdc03e56e17b91e66b622d3307e42460ac5`，均已下载核对原始报告。此前字号失败的运行及必要修正保留在 [字号记录](reader-font-input-v57.md)。Reader attempt 1 artifact 11290348339（SHA-256 `6c0fbf8a70e3c409fb22712627792f846ab8c40ad18ab8d65435fe7eed72ae04`）也已下载保留：Gradle 收到 124/141 个完成结果，XML 另有验证重建未完成的失败条目，共 125；不能当作整组通过。无应用断言堆栈、宿主 OOM 记录为空，仍不把故障原因当作已证明。

## 第三批：目录失败可见、停止与恢复

V50 详情页此前忽略读取目录的错误，只显示「暂无目录」，也缺少同页重试和加载停止。现在保留书籍身份，以原 V3 卡片显示有界失败原因，提供「重试目录」；读取中可「停止加载」，停止后保留书籍并提供「继续加载目录」。收藏、缓存等操作失败也在详情中可见。未确认目录前不显示阅读、入架和下载动作。

目录任务新增代次检查，停止、返回或切换书籍先作废旧代次，再取消旧请求。成功和失败均核对当前代次与协程活跃状态，旧结果与旧错误不能重开详情或覆盖新书。继续使用原目录完整性、同源链接、runInterruptible 和取消异常语义；不对残缺目录放宽校验，不自动循环请求，不写书源配置。

新增 `SourceCatalogueRecoveryV58DeviceTest` 三项：真实目录声明 3 章但只提供 2 章时显示失败并拒绝入架，重试完整目录后才出现操作；可中断的慢目录停止后用可见按钮继续；刻意迟到的旧失败在返回并打开下一书后不能污染新详情。每项使用默认引擎、真实 WebView 和 V50 按钮，核对保存书源原字符串、书架列表及活动书籍不变，并等待测试协程全部结束。

另加一项纯 UI 状态回归：目录加载中或失败时仍可点击既有「取消下载」，取消回调执行并移除进度，保留当前书籍，避免未确认目录时隐藏其他动作也挡住已开始缓存的取消。此项不冒充实际缓存网络测试；已有完整组继续验证真正缓存的数据安全。四项同时加入浏览器整组与阅读器完整组，保留两种真实进程恢复和全部原断言。本批完整 CI 结果在 PR 中记录。

同时加强字号完成帧的证据：真实减小触摸及 21sp 落盘断言后等待绘制，并检查可见 21sp 再截图，避免捕获上一帧 22sp 的按压状态。原触摸、字号落盘、旋转和句子位置断言不变。

第三批 `df8670d` 完整验收：Reader 145/0失败/0跳过，预检15/编辑2/创作11，原137和原16逐项保留；Browser 15/0/0，原11和新增4项保留；JVM 714/0/0，Debug构建，lint0错误/197警告/5提示。两个实际force-stop进程恢复分别确认新PID、保存Locator和持久Cookie/DOM。Reader artifact 11291606594（SHA-256 `627ca5e423c1e21003b67b112866010466662fbe99f028530c143bcdadd19c02`）、Browser 11291031828（`a144717ebf6382fb7ea25223ad0eb9ff352a573ac9c5aa668c7428bef7e5503e`）、JVM/lint 11290139889（`41dc64e0ba3c58516403f8423757bf4e5c0b19ec8d5941d7bb04310a972c4c2e`）均已下载核对。运行：[Reader](https://github.com/xgl34222220-ops/langhuan/actions/runs/37169975889)、[Browser](https://github.com/xgl34222220-ops/langhuan/actions/runs/37169975839)、[Android](https://github.com/xgl34222220-ops/langhuan/actions/runs/37169975871)。

## 第四批：阅读及缓存动作的明确入架状态

详情页此前在未入架时显示可点击阅读/下载按钮，而处理函数因缺少 shelfStoryId 静默返回。现在未入架时显示明确说明，阅读和下载呈禁用状态；显式加入书架期间继续禁用，成功后启用，并将入架按钮显示为禁用的「已在书架」。目录及正文的数据流程、原入架保护和手动缓存意图不变，不自动入架。沿用 V3 的既有按钮、颜色和排版，仅新增就绪状态。继续参考固定版 Legado BookInfoActivity 对 inBookshelf 的明确状态区分，采用让动作状态与书籍状态一致的交互原则；按本应用现有元数据入架流程独立实现，源码及 GPL-3.0 链接见参考表。

新增一项纯 UI 回归，通过实际触摸确认未入架按钮不执行回调，显式入架进入等待状态后仍禁用，完成后阅读和下载回调各执行一次，重复入架被阻止。该用例验证界面控制，不冒充实际数据库入架；原完整组继续覆盖真正入架、正文加载和缓存的数据安全。预期阅读器146项、浏览器16项，两种进程恢复与全部原断言继续执行，本批结果另行核对。

第三批的语义21sp及落盘断言通过，但最终截图仍显示上一帧22sp，不把它当作视觉21sp证据。第四批增加系统UI空闲同步后再次核对落盘21sp和可见21sp，并重新核验截图。窗口证据捕获若返回空位图或PNG写入失败将明确失败，不复用旧文件。原5秒字号变化门槛、真实触摸及位置断言保留。

## 参考、许可证与采用理由

- [Legado Sigma BackstageWebView 源码](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/help/http/BackstageWebView.kt)：取消时释放实例/回调、检查当前 WebView 后再接受结果。参考其生命周期和迟到回调隔离思路，独立实现本应用的服务 IPC 恢复；没有引入其规则 JavaScript 或原生接口。[GPL-3.0 许可证](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/LICENSE)。
- [Legado SearchModel](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/model/webBook/SearchModel.kt)与[SearchViewModel](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/ui/book/search/SearchViewModel.kt)：流式合并结果、取消/完成的独立通知与工作状态控制。第二批采用明确状态和保留已完成工作的交互原则，独立实现按来源 ID 定向重试；不替换现有书源规则或 UI 系统。许可证同上。
- [Legado BookInfoViewModel](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt)与[BookInfoActivity](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt)：目录失败向用户提示并提供刷新书籍/目录操作。第三批按本应用交互独立实现持续可见的错误卡片和手动同页重试，保留严格完整目录证明与数据安全。许可证同上；未采用参考项目的脚本、换源入库或自动更新行为。
- [Readium Kotlin Navigator 源码](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt)及[导航文档](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/docs/guides/navigator/navigator.md)：多页 Fragment、当前 Locator 和视图生命周期需要协调，不能给 EPUB 简单套浏览器重建策略。[BSD-3-Clause 许可证](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/LICENSE)。本批保留现有 SDK 与原版阅读断言。
- [Android WebViewClient.onRenderProcessGone](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail))及[WebViewRenderProcess.terminate](https://developer.android.com/reference/android/webkit/WebViewRenderProcess#terminate())：明确要求处理退出并清理不可复用的实例；用于实际故障注入与新实例恢复验证。

真实在线 AI、第三方站点登录/验证码和用户真机仍未实测。CI 仅在原有隔离模拟器与合法样例执行；不绕过网站验证、不扩大网页权限、不合并、不正式发布或部署。
