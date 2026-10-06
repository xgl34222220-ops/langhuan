# V74 AI 书源保存后的管理定位

## 缺口

V73 在 AI 书源确认保存后提供了“返回书源管理查看”主动作，但返回只切换页面，
没有携带刚确认写入的书源身份。书源管理按名称排序且可能包含较多条目，用户仍需再次搜索，
也无法从同名书源中确认哪一条是刚才完成事务写入的记录。

## 实现

- `OnlineBooksStateV36` 在保存事务确认成功后同时记录书源 ID 和名称。
- 新生成、显式停止、无效的新请求都会一起清除该短期确认身份，避免旧记录误导下一次操作。
- 保存成功页的主动作把书源 ID 作为 UI 导航事件交给在线书城宿主。
- 书源管理收到 ID 后恢复“全部”分组、清空可能遮挡目标的查询，只把目标书源在当前可见列表中临时置顶，并显示“刚保存”。
- 清理旧查询时同步释放搜索焦点并收起软键盘，避免返回后键盘继续遮挡目标卡片。
- 临时置顶不修改 `BookSourceStoreV36` 的持久顺序，也不触发保存、启停、生成或取消。
- 打开目标、离开管理页或再次进入 AI 生成时清除定位 ID。

该处理遵循 Android UI 事件与单向数据流原则：确认写盘的业务结果仍由 ViewModel 状态提供，
页面切换和列表定位由 UI 层消费；列表继续以稳定书源 ID 作为 key。Android 官方
[UI events](https://developer.android.com/topic/architecture/ui-layer/events)、
[Compose UI architecture](https://developer.android.com/develop/ui/compose/architecture) 与
[Lazy lists](https://developer.android.com/develop/ui/compose/lists) 用于核对这些边界。

固定版本 [Legado `3a7c4daa...`](https://github.com/apgk/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba)
（GPL-3.0）和 [Readium `1b1f6b...`](https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75)
（BSD-3-Clause）继续只作书源管理行为与阅读生命周期参考；本批为独立 Compose/Kotlin 实现，
未移植 GPL 代码、规则解释器或原生桥。

## 回归

- `AiSourceSavedManagementV74Test#confirmedSaveCarriesTheExactStoredIdentityAndTheNextAttemptClearsIt`
- `AiSourceSavedManagementV74Test#managementFocusPinsOnlyTheExactSavedIdentityWithoutChangingStoredOrder`
- `SourceBrowserSessionV56DeviceTest#savedAiSourceReturnPinsTheExactStoredIdentityInManagement`

设备方法从书源管理进入 AI 页，再点击真实保存成功主动作返回；断言目标 ID 被置顶并显示“刚保存”，
软键盘已收起，实际点击打开的也是该 ID，同时取消与两种生成调用均为零。测试保存实际窗口截图。

## 验收状态

实现提交 `6401177e21898c1d0121abbd322725f2ad34287b` 的预验收已完成：Reader
[37228101599](https://github.com/xgl34222220-ops/langhuan/actions/runs/37228101599) 为 157/0/0/0，
Browser [37228101572](https://github.com/xgl34222220-ops/langhuan/actions/runs/37228101572) 为
25/0/0/0，Android [37228101551](https://github.com/xgl34222220-ops/langhuan/actions/runs/37228101551)
为 JVM 734/0/0/0、Debug 通过、lint 0 错误/183 警告/3 提示；EPUB 与浏览器两种不同 PID
force-stop 恢复均通过。V73 的 Reader 156、Browser 24、JVM 732 个完整类名/方法名逐项保留，
只新增本节列出的 1 项设备方法和 2 项 JVM 方法。

预验收人工看图发现 Reader 产物的 V74 截图仍保留搜索软键盘（Browser 独立组截图已收起），
因此没有把该图算作最终视觉成功；随后补入明确焦点/IME 清理和 `!keyboardVisible()` 设备断言。
提交 `2c10bc04791494b07396315a4b51bc8e0feac3e4` 的 Reader 157、Browser 25、JVM 734
和两种进程恢复虽均通过，但 Reader 截图又取得键盘收起后的空白 SurfaceFlinger 过渡帧；该图同样不算
视觉成功。测试进一步加入实际窗口像素门槛，要求中部已呈现足量正文墨色后才能取证，而不是用固定延时
或 Compose 语义树替代真实绘制。包含此同步修正的最终提交必须再次从头重跑三组完整验收，最终原始
运行与 artifact 记录在 PR #104。
