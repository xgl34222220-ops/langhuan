# EPUB 渲染退出恢复 V60

继续 PR #104 原分支 `fix/ai-source-browser-session-20261003`。第四批 c298cfe 已完整验收：Reader 146、Browser 16、JVM 714，均无失败/跳过；Debug 和 lint 通过（0 错误、197 警告、5 提示），两种实际 force-stop 独立进程恢复通过。最终横屏原图为 21sp。原 f52c04d 的 137/7/714 和两种恢复证据，以及原 16 项失败身份均保留，旧通过不替代本批验收。

第四批新证据：[Reader](https://github.com/xgl34222220-ops/langhuan/actions/runs/37171775094)、[Browser](https://github.com/xgl34222220-ops/langhuan/actions/runs/37171775072)、[JVM/Debug/lint](https://github.com/xgl34222220-ops/langhuan/actions/runs/37171775117)。已下载校验的 artifact SHA-256：Reader 11291837684 `1c8054d31b0992c82ee36cff4c2ea94594a40102e88fa44d4bc5dde5a2478784`；Browser 11291229609 `d82f5c76cb0fbb450342521d8be6bf5d7214c9bc94d6fda601abf8ef42cdec24`；JVM/lint 11292135473 `6aea9b53fdef2a630d51c4cd14ae01566367c1cd79d506421dbf648f9dcedc81`。原始字号 PNG `3f4daafc2e663f5ebc631f9974e302e3b801b7c36267128f07f740f316b36c75`。

## 具体缺口与采用理由

197 条 lint 中 EPUB 的渲染退出处理确有缺口。安全包装器未覆盖 `onRenderProcessGone`：渲染进程被系统回收或退出后，无明确恢复入口，仍可能接受旧 Navigator 的加载/进度回调。Readium 的多个页面及 Locator 由同一个 Navigator 协调，不能套用浏览器模式中单视图替换的做法。

检查异步保存还发现：被新序列淘汰而没有写盘的旧保存任务，原先仍可更新内存 `savedLocator`。本批只允许当前序列、当前书籍、有效阅读状态且实际写盘成功的任务更新内存；渲染故障进入时冻结新导航和保存、取消恢复与选区、使旧代次失效，读取磁盘中最后确认的 Locator。正在执行的旧写入在锁内结束后读取其结果，尚未执行的旧序列不得继续保存。

## 行为

- 每个安全包装器覆盖真实退出回调，先禁止其后续文档加载回调。
- 只在回调视图确属当前 Navigator 时退休该会话。清空宿主引用后移除 Navigator，让 Readium 的 `onDetach` 统一销毁它拥有的页视图；迟到的旧回调只清理自己的受影响视图，不能清理新会话。
- 退出提示为“原版页面渲染已中断。已保留上次确认进度，重新打开可继续阅读。”；显示“重新打开原版”，不保留无期限转圈或“从当前页继续”的失效入口。
- 手动重开重新校验原 EPUB、创建 Publication/Navigator 和新 WebView，继续复用既有安全包装器及受限 Readium 脚本。失败仍提供重试，未设置自动重试循环。
- Activity 重建保存故障标记，继续等待用户选择；后台/恢复不会自动重新触碰失效视图。文字版切换及重新关联入口保留。
- 几何计算等待 WebView 响应后重新检查 Navigator 和有效阅读状态，不能以迟到 UI 刷新清除故障提示。

`commitNowAllowingStateLoss` 仅用于销毁已不可用的临时 Navigator，即使系统已保存 Activity 状态也必须完成清理；没有书架、文件、书签或进度数据事务被丢弃。宿主此前已禁用 Fragment 自动恢复，并从独立持久 Locator 主动重建。原文件没有删除、替换或重新关联，未新增生产环境故障注入接口或权限。

## 回归与完整验收

新增两个设备测试，使用现有合法原创 `original-reflow.epub`、`original-fixed.epub`。测试通过 Instrumentation 直接获取当前真实 WebView 的 `WebViewRenderProcess` 并调用 `terminate()`，不调用故障回调伪造退出，不添加应用可达的调试入口。

各例要求至少两个实际 Readium 页视图，先进入第二章（流式还翻页到非零 progression）并等待 Locator 真正保存。断言应用 PID 不变、受损 Navigator 与其视图离开窗口、失败期间原磁盘 Locator 字符串完全不变、后台返回不自动重试；固定版式再重建 Activity 后验证同样的等待状态。点击可见重开按钮后必须得到新的 Navigator/WebView，原章节及 progression 恢复，安全 DOM/禁止书内脚本/文件和内容访问/网络策略保留，EPUB 原字节、关联摘要、原版与文字版书签/设置保持不变。之后重新导航到插画页，并继续要求原 PNG/SVG 的四种实际窗口颜色。

新证据为 `reader-qa/v60-*-renderer-proof.json` 及 `v60-*-renderer-stopped/restored.png`，记录真实故障、同一应用 PID、多页数量、前后 Locator 和原文件摘要；PNG 不得为空或复用旧文件。

本批预期完整 Reader 148（上一批 146 全保留）、预检 18、编辑保存 2、创作 11，Browser 完整 16，JVM 714；Debug/lint、EPUB 和浏览器实际 force-stop 两阶段仍必须重新执行。新增预检不替代未筛选完整组。实际 CI、原 XML/HTML、两阶段证明、截图及 artifact 校验以本批新提交在 PR #104 的结果为准，未运行前不记为通过。

## 参考源码、文档、许可证

独立实现宿主状态与持久化保护，不移植同类项目的规则解释器或桥接，不更换既定 V3 界面及原版排版。

- [Android WebViewClient.onRenderProcessGone](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail))：同一个渲染器可影响多个视图，每个回调必须处理自身受影响视图；失效视图不可继续使用。宿主重建整个 Navigator 是本应用对多页会话的选择，并未假定所有其他页面都已崩溃。
- [Android WebViewRenderProcess.terminate](https://developer.android.com/reference/android/webkit/WebViewRenderProcess#terminate())：正式公开 API 产生真实退出，关联视图全部处理后应用进程才能继续。本轮设备证据限定已授权 API 35 模拟器，不冒充旧系统或真机结果。
- Readium 固定源码 `1b1f6b308a7b6f968b2bf1e66c84912879466f75`：[EpubNavigatorFragment](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt)、[R2EpubPageFragment](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/readium/navigator/src/main/java/org/readium/r2/navigator/pager/R2EpubPageFragment.kt)、[R2FXLPageFragment](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/readium/navigator/src/main/java/org/readium/r2/navigator/pager/R2FXLPageFragment.kt)、[R2BasicWebView](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/readium/navigator/src/main/java/org/readium/r2/navigator/R2BasicWebView.kt)、[Navigator 文档](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/docs/guides/navigator/navigator.md)、[BSD-3-Clause LICENSE](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/LICENSE)。实际核对多页 Locator 协调、页面 `onDestroyView` 清理 listener、`onDetach` 销毁 WebView，以及基础 WebView 销毁前移除 SDK 桥，选择让 SDK 完整退场而不是对单视图套用浏览器重试。

真实 AI 服务、第三方登录/验证码、真机、API 28 的真实渲染回收仍未验证；不绕过限制，不操作用户设备或扩大敏感权限。此前的文件/插画/脚本安全、损坏保护、书签隔离、位置恢复、草稿取消、数据库事务及独立副本断言继续完整执行。
