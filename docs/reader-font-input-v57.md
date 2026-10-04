# 字号回归重复失败的必要修正

第二批 `af8b1cc` 的 [完整阅读器 CI](https://github.com/xgl34222220-ops/langhuan/actions/runs/37163623050) attempt 1 和 2 均在 A+ 的真实触摸后未观察到字号 21→22。attempt 1 随后设备离线，仅完成 6/11；attempt 2 完成 11 项预检，只有该项失败，未进入完整组。两次均不计作第二批通过。原 artifact 11289205349 与 11289635140、原日志和 f52c04d 验收记录保留。

截图显示按钮可见；它不能证明旧文本节点的可访问坐标就是当前可点击区域。对过期坐标/目标身份的判断仍是由重复超时与界面证据作出的推断，不把设备离线当作全部失败的原因。

字号步进按钮新增明确的「增大阅读字号」/「减小阅读字号」无障碍描述，保留原文字、布局和点击逻辑。设备用例按该描述选择前台可点击、启用的按钮，要求整个按钮在其滚动视口及窗口内，等待坐标稳定并同步系统 UI，再注入原真实 DOWN/UP 触摸。超时也捕获窗口截图及系统节点树，触摸区域写入 logcat。

原 21→22→21 落盘变化、旋转字号、句子位置、可见性与屏幕触摸注入断言继续执行；不直接写字号代替点击，不改等待字号变化的 5 秒门槛，不跳过、不以语义 click 替代物理交互。仍先执行预检，再执行未筛选的完整阅读器和 EPUB 实际进程恢复。此提交同时重跑浏览器整组、独立进程 Cookie/DOM 恢复、714 项 JVM 与 lint，不能用 af8b1cc 的两项成功 CI 代替。

参考 [Android UiAutomation](https://developer.android.com/reference/android/app/UiAutomation#waitForIdle(long,%20long)) 的 UI 同步与输入注入 API，以及 [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#isClickable()) 的按钮可点击、启用、可见与边界信息。仅用于已授权 CI 模拟器，不操作用户设备或增加应用权限。

`18110ef` 的 [Reader CI](https://github.com/xgl34222220-ops/langhuan/actions/runs/37165081802) 完成 11 项预检，字号仍失败，不能记作修复完成。增加的诊断显示滚动内容区域为 y=318..851，而底部标签父区域为 y=790..916，存在重叠。源码的 AnimatedContent 允许子内容不填满分配高度并越界绘制；这与可见内容和触摸区域不一致的现象相符，仍需后续实际触摸验证。

后续修正让动画内容填满菜单剩余高度，并在分配区域内裁剪；上下操作及底部标签继续占用各自位置，原颜色、字号及页面布局不替换。字体按钮补充标准 Button 角色。严格按钮可点击/启用、窗口与滚动视口完整包含、坐标稳定、真实触摸及 21→22→21 断言全部保留，进一步记录候选节点标志，避免把节点可见直接当作可操作。
