# Distillation V2

目标：把参考小说蒸馏从“最终摘要”升级为“可检索的长期 DNA 库”。

## 原则

1. 批次提取结果不再仅作为中间字符串，必须长期保存。
2. 最终 Story/Style DNA 只是总览，不再是唯一数据源。
3. 创作对话、场景规划、正文、主编按当前任务检索相关 DNA，而不是一次性塞完整摘要。
4. 保留来源层级和 evidence，方便解释本轮实际参考了什么。
5. 原作事实层与可迁移方法层严格分离：STORY 用于理解原作；STYLE/KEEP/TRANSFORM/AVOID 用于原创迁移。

## V2 数据层

- batch observations：每个深读批次的 STYLE / STORY 条目
- aggregate observations：跨批次稳定规律与阶段变化
- final overview：最终 Story DNA + Style DNA 总览
- retrieval items：去重后保留的完整结构化检索条目

## 交互

报告页应显示：
- 深读覆盖率
- 原始批次条目数
- 去重后检索条目数
- Story / Style / Keep / Transform / Avoid 数量
- 可查看“本轮创作实际调用的 DNA”
