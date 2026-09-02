# Novel Skill OS V7：有状态工作流内核

V7 把琅嬛现有的自然语言路由、章节 Runtime、Candidate/Canon 和依赖分析串成一条可恢复的工作流。

核心原则：**外部仍然是正常聊天，内部才是严格状态机。** 用户不需要手动选择“下一步做什么”，系统只在真正阻塞当前阶段时请求最少输入或确认。

## 阶段

`START → BRIEF → RESEARCH → REFERENCE_DISTILLATION → FOUNDATION → BLUEPRINT → MASTER_OUTLINE → VOLUME_OUTLINE → CHAPTER_PLAN → DRAFT → REVIEW → CANON_SYNC → COMPLETE`

其中 `RESEARCH`、`REFERENCE_DISTILLATION`、`VOLUME_OUTLINE` 可以在明确不需要时跳过。

## Gate 语义

- 产物生成后进入 `AWAITING_CONFIRMATION`。
- “继续 / 可以 / 确认”等短回复只确认**当前一个 Gate**。
- 尚未到 Gate 的阶段不会因为一句“继续”被连续跳过。
- “不对 / 重做 / 修改”等回复只把当前阶段标记为 `NEEDS_REWORK`，不会清空项目。

## Artifact 与 stale

工作流只记录产物元数据，不把它当 Canon。上游发生实质修改后：

1. 找到最早受影响阶段；
2. 回退到该阶段；
3. 保留下游产物；
4. 将受影响产物标记 `stale=true`；
5. 重新生成或用户重新确认后，新的 revision 才成为当前版本。

这解决“确认过就不能再改”和“改前面一处就整本推倒重来”两个极端。

## 与章节依赖分析连接

`ChapterDependencyAnalyzer` 已经能找出人物、时间线、伏笔、后续正文和后续章纲依赖。V7 的 `applyChapterDependencyImpact()` 会把报告转换为最小回退范围：

- 后续章纲受影响：最早回到 `CHAPTER_PLAN`；
- 只有正文/状态链受影响：最早回到 `DRAFT`；
- 只标记报告涉及章节的下游产物为 stale；无关章节保持有效。

注意：依赖分析本身是只读的。只有用户真的执行了上游修改，调用方才应应用回退。

## Capability Routing

`NovelSkillRouter` 仍然负责本轮最小能力集合。V7 只把路由结果保存成流程元数据并注入项目会话：

- 当前 intent；
- 本轮启用的 capabilities；
- 当前 workflow stage / gate；
- stale 产物数量。

这些信息不能覆盖 StorySnapshot、章节合同或 Canon。

## 持久化与恢复

`PersistentNovelWorkflowStateStore` 使用独立 SharedPreferences 保存流程状态，和 StorySnapshot/Canon 分离。

- 工作流元数据损坏时直接丢弃并重建，不影响小说事实；
- 每次 Gate/路由变化同步持久化；
- 重新进入项目聊天时恢复当前阶段和待处理项；
- 历史与 artifact 数量做上限裁剪，避免无限膨胀。

## 参考

工作流设计借鉴 `aaronyi97/image-story-video-wizard` 的阶段门控、单一流程状态、断点恢复和下游产物 stale 思路；琅嬛实现为小说领域的独立 Kotlin 状态机，并接入现有 Skill OS、ChapterDependencyAnalyzer 与 Canon 边界。
