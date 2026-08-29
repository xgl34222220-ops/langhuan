# 琅嬛用户 Skill 格式

琅嬛用户 Skill 是声明式 JSON，只向 C 层写作任务注入方法提示，不执行脚本、Shell、JavaScript 或其他代码。

必填字段：
- `schemaVersion`: 当前固定为 `1`
- `id`: 3-64 位字母、数字、点、横线、下划线；作为安装/升级唯一标识
- `name`: Skill 显示名
- `guidance`: 实际注入模型的写作方法，最多 24000 字
- `supportedTasks`: 至少一个任务

可选字段：`description`、`version`、`author`、`license`、`sourceUrl`、`sourceRevision`、`defaultTasks`。

当前任务名：`SCENE_DIRECTOR`、`PROSE_AUTHOR`、`NOVELIZATION`、`EDITOR_REVIEW`、`EDITOR_REWRITE`、`FACT_EXTRACTION`、`AGENT_EXTRACTION`、`EXECUTION_AUDIT`、`AUTONOMOUS_PLANNER`、`FULL_BOOK_EDITOR`。

相同 `id` 再次导入视为升级。内置 Skill 的 id 不允许被用户文件覆盖。用户 Skill 可在 App 的 Skills 页面卸载。
