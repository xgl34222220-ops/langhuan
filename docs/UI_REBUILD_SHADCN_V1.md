# 琅嬛 UI 全面重构：Shadcn-inspired Compose Design System

> 状态：V1 基础层已落地，后续页面按迁移波次逐步收口。

## 目标

这次重构不是把 Android App 画成 Web 后台，也不是直接照搬 React/Tailwind。

我们借鉴 shadcn/ui 的核心方法：

1. **语义 Token，而不是业务页面硬编码颜色**
2. **Primitive 组合，而不是每个页面重复造 Button/Card/Sheet/Menu**
3. **一致的默认视觉与状态**
4. **组件源码归项目自己维护，允许琅嬛按小说产品特性深度定制**
5. **业务内核与 UI 解耦，重构 UI 不改 Novel Skill OS / Story Graph / 写作运行时行为**

## 设计方向

### 产品气质

- 阅读器优先，不做“开发者后台”视觉
- 纸白 / 墨黑为主色，暖橙为品牌强调
- 封面、正文和故事内容是主要视觉焦点
- 控件低噪声，边框轻、阴影少、圆角克制
- 手机单手操作优先，重要入口固定、可预期

### Semantic Tokens

统一使用以下角色：

- `background`
- `foreground`
- `card`
- `cardForeground`
- `muted`
- `mutedForeground`
- `border`
- `input`
- `primary`
- `primaryForeground`
- `accent`
- `accentForeground`
- `destructive`
- `destructiveForeground`
- `ring`
- `warmSurface`

业务页面不得继续新增无语义硬编码主题色；确需特例时先升级 Token。

### Radius Scale

- `sm = 8dp`
- `md = 12dp`
- `lg = 16dp`
- `xl = 20dp`

封面可保留更小圆角，以维持实体书感。

## V1 已完成

### 1. 基础设计系统

新增：

`ui/design/LanghuanUiKit.kt`

首批 Primitive：

- `LanghuanCard`
- `LanghuanPageHeader`
- `LanghuanIconButton`
- `LanghuanBadge`
- `LanghuanMenuRow`
- `LanghuanSeparator`

### 2. 稳定主题语义化

`LanghuanStableTheme` 已同时提供：

- 新 `LocalLanghuanUiTokens`
- 旧 `LocalMiuixTokens`

因此可以渐进迁移，不要求一次重写全部页面，也不会让旧 UI 因 Token 迁移立刻失效。

### 3. 书架主入口 V9

新增 `ReaderShelfV9` 并接入 Root：

- 图书 / 书架 / 我的固定底部导航
- 统一 Page Header
- 统一搜索与添加动作
- 三列封面书架继续保留
- 书架管理改为语义卡片
- 我的页面改为分组 Menu Card
- 添加、移动、详情等 Sheet 使用同一组件语言
- 原导入、删除、AI 新建、故事、Skills、运行中心、设置行为保持不变

## 全面迁移波次

### Wave 2：图书详情与阅读器

目标文件：

- `ReaderFirstBookV11.kt`
- 阅读目录 / 章节导航
- 阅读设置 Sheet
- 图书详情 / 简介 / 封面操作

要求：

- 详情页 Header 与书架共用 Primitive
- 阅读器正文保持沉浸，不强塞卡片 UI
- 目录、设置、章节操作全部统一 Sheet / Menu / Toggle 风格
- 清理重复的 Reader V7/V8/V9/V10 外壳，保留必要兼容层

### Wave 3：AI 建书与研究

目标文件：

- `CreationChatV4.kt`
- `NewBookConversation.kt`
- `ResearchNewBookConversationPage.kt`
- 蒸馏方案、DNA、数据浏览器相关组件

要求：

- 聊天区像正常 AI 对话，不像表单工作流
- 输入区固定、附件/模型/研究能力入口可见
- 建书蓝图、整理方案、蒸馏结果作为可展开 Artifact Card
- 方案生成后动作固定在结果附近，不要求用户滚回顶部
- DNA、人物群像、规则、世界观等必须能进入详情浏览

### Wave 4：写作工作台

目标文件：

- `WritingWorkspaceV10.kt`
- `WritingWorkspaceV8.kt`
- `WritingSkillPanel.kt`
- `ChapterEditorPage.kt`
- `StoryGraphHealthSheetV10.kt`
- Canon / Migration / Run Inspector 相关面板

要求：

- 写作主编辑区永远是视觉中心
- 章纲、Skill、Story Graph、运行状态成为可折叠辅助区
- 后台执行状态统一 Status / Badge / Progress Primitive
- 不再在页面上堆叠多个不同风格的 Card
- 编辑器操作栏、保存状态、AI 动作位置稳定

### Wave 5：故事 / 酒馆体验

目标文件：

- `StoryPlayRuntimeV3.kt`
- `StoryTavernExperienceV1.kt`
- NPC / Memory / Spatial / Perception / Clock 等 Story 页面

要求：

- 对话和角色沉浸优先
- 系统数据默认隐藏在 Drawer / Sheet / Inspector 中
- 角色、场景、记忆、世界状态使用同一 Inspector 组件
- 不把 Story Runtime 做成调试面板

### Wave 6：设置、Skills、运行中心与清理

目标文件：

- `AiProviderSetupPage.kt`
- `SkillsPageV3.kt`
- `RunCenterPage.kt`
- `AgentPage.kt`
- `StoryIntelligencePage.kt`

最后执行：

- 合并重复 Primitive
- 删除已失效的旧页面版本
- 每类页面只保留一个正式入口 + 必要兼容 Wrapper
- 检查深色模式、系统字体缩放、横竖屏和触控目标

## 架构约束

### 不改启动安全边界

数据库检查成功前继续使用最轻量的启动 UI；新主题与复杂组件只在 Gate 成功后加载。

### 不改业务状态机

UI 重构不得修改：

- Novel Skill Router
- Chapter Run Runtime
- Story Graph Health Engine
- Canon Migration
- Distillation Worker
- Repository / Database schema

除非另开功能 PR。

### 逐步删除版本号页面

历史上的 `V4 / V7 / V8 / V10 / V11` 是迭代痕迹，不应永久成为架构。

最终目标目录：

```text
ui/
  design/
  shell/
  library/
  reader/
  creation/
  writing/
  story/
  settings/
```

页面文件以功能命名，而不是以版本号命名。

## 验收标准

每个迁移 Wave 都必须满足：

- 编译通过
- 原业务操作路径可用
- 返回键 / Sheet / Dialog 行为正常
- 空状态、加载态、错误态都有统一表现
- 浅色 / 深色可读
- 主要触控目标 >= 40dp，核心操作优先 >= 44dp
- 不出现同屏 3 种以上不同 Card 风格
- 不为单个页面新建临时主题色
- 不破坏启动数据库保护

完成 Wave 6 后，再把 `feat/shadcn-ui-rebuild-v1` 这套体系视为琅嬛正式 UI 基线。
