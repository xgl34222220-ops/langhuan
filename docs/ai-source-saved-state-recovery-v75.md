# V75 AI 书源保存确认的进程恢复

## 缺口

V73/V74 已让用户在 AI 书源确认写入后直接返回书源管理并精确定位，但确认 ID 与名称只保存在
`OnlineBooksViewModelV36` 内存中。Activity 重建由 ViewModel 保留，系统回收进程后则会丢失；
书源文件其实已经完成事务写入，恢复后的 AI 页却回到未保存外观，用户无法判断是否需要重复生成或保存。

## 实现

- 只把已确认写入书源的轻量 ID 放入 `SavedStateHandle`；URL、规则、报告和正文均不进入 saved state。
- ViewModel 重建时必须用 `BookSourceStoreV36` 的持久列表重新解析该 ID，并从持久对象取得当前名称；
  saved state 中不存在、已删除或空白的 ID 一律不恢复。
- 新生成、无效新请求、显式停止继续清除成功确认，同时删除 saved-state 检查点。
- 书源被删除时，内存状态与检查点同步清除；书源改名时，确认名称以持久列表为准刷新。
- 不改变生成代次、取消顺序、规则验证、保存事务、书源稳定 ID、浏览器 transport、Cookie/DOM
  会话或 V74 的临时置顶规则。

Android 官方把 `SavedStateHandle` 定位为 ViewModel 中轻量 UI 状态应对系统发起进程回收的备份，
并明确其与任务栈绑定、不能替代应用数据持久化：
[Saved State module for ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)。
本批因此只保存可重新验证的 ID，书源文件仍是唯一业务事实来源。

固定版本 [Legado `3a7c4daa...`](https://github.com/apgk/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba)
（GPL-3.0）和 [Readium `1b1f6b...`](https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75)
（BSD-3-Clause）继续只作书源与阅读生命周期参考；实现为独立 Kotlin/Compose 状态恢复代码，
未移植 GPL 代码、规则解释器或原生桥。

## 回归

- `AiSourceSavedStateRecoveryV75Test#savedIdentityRestoresOnlyFromTheDurableSourceAndUsesItsCurrentName`
- `AiSourceSavedStateRecoveryV75Test#reconciliationRefreshesRenamesAndClearsADeletedSavedIdentity`
- `SourceBrowserSessionV56DeviceTest#savedAiSourceConfirmationRestoresFromSavedStateAndRejectsADeletedIdentity`

设备方法用合法隔离书源文件和 `SavedStateHandle` 重建真实 ViewModel，显示真实 V50 保存成功卡片并保存窗口截图；
随后通过真实 ViewModel 删除目标，断言成功卡片和检查点同时消失。它验证 Android saved-state 恢复路径，
不把 Activity `recreate()` 冒充进程死亡，也不声称 user force-stop 后任务栈仍存在。完整 Reader、Browser、
JVM/Debug/lint，以及既有 EPUB/浏览器两个不同 PID force-stop 恢复仍须在同一最终提交重新验收。

## 验收状态

尚未完成；不得用 V74 或更早提交的通过替代本批结果。
