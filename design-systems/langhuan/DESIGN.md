# 琅嬛 · Mobile Design System v2

> Category: Android reading / AI novel studio
> Status: Production design contract

琅嬛不是 Web dashboard，也不是把工具箱套进小说阅读器。**阅读表面就是产品本体；创作与 AI 能力必须在需要时出现。**

## 1. 设计血统与职责边界

### 阅读产品
- **Readest**：`Content first, chrome recedes`。阅读页必须让正文占主导，工具层只在需要时出现。
- **Book's Story / Material You**：Android 原生阅读器结构、动态色、BottomSheet、手势、导航与设置层级。
- **Open Design / Warm Editorial**：长文优先、暖纸感、近黑正文、克制装饰、明确 anti-pattern。

### 组件与主题
- **HeroUI Native**：借移动组件 anatomy、compound component、BottomSheet/Dialog/Input/Accordion 的层级；不得引入 React Native，仅用 Jetpack Compose 原生重建。
- **tweakcn**：借 semantic token 与主题编辑工作流；颜色/圆角/Surface 层级不得散落写死在业务页。
- **21st.dev**：只挑 AI 对话、Agent 状态、输入区、空状态、微交互中的优秀模式；不得把 Showcase 动效或玻璃效果批量搬进阅读主流程。
- **coss ui**：借 info/success/warning/destructive 状态语义、表单/设置分组、渐进披露与高密度但不拥挤的列表结构。
- **shadcn/ui**：只吸收 open-code、composition、semantic tokens、variants、accessibility；禁止复制黑白 Web 视觉。

### 执行系统
- **Ponytail**：借“先复用、后新增”的最小执行阶梯，进入 Novel Skill OS 的任务深度选择；不作为 UI 参考。

## 2. 不可违背的产品原则

1. **Content first, chrome recedes.** 阅读时默认只看正文和极弱进度；控制层由中央轻触临时唤起。
2. **One primary task per surface.** 书架先找书，阅读页先读，创作页先聊天/写作；不得一屏同时争抢多个主任务。
3. **Progressive disclosure.** 常用设置第一层呈现，高级项进入二级页面/折叠层；AI、编辑、故事不得进入阅读一级工具栏。
4. **No dashboard in reading flows.** 禁止卡片墙、指标墙、粗边框表单、网页式 responsive grid。
5. **Native Android hierarchy.** 使用 Material 3/MIUIx 的 Surface、BottomSheet、动态色与大触控区；视觉精致靠层级、比例和留白，不靠堆圆角。
6. **One accent per surface.** 强调色只表达当前位置、主行动或重要状态，不允许满屏 primaryContainer。
7. **State is semantic.** `neutral / info / success / warning / destructive` 有固定语义，不用随机 Badge 颜色表达状态。
8. **No ornamental glass.** 玻璃/blur 只能用于短暂浮层、封面叠层或 AI 输入区，不能铺满书架和阅读页。

## 3. Semantic Tokens

### Surface
- `background`：页面底色
- `surfaceLowest`：阅读以外的最底层内容面
- `surface`：普通分组容器
- `surfaceHigh`：临时强调/输入区
- `surfaceOverlay`：Sheet/Dialog/浮层

### Content
- `foreground`
- `mutedForeground`
- `disabledForeground`

### Accent
- `primary / onPrimary`
- `primaryContainer / onPrimaryContainer`
- 强调色用于主行动与选中状态，不作为默认卡片背景

### Status
- `neutral`
- `info`
- `success`
- `warning`
- `destructive`

### Shape
- Small：10–12dp
- Medium：14–18dp
- Large：20–24dp
- Sheet：26–30dp 顶部圆角
- 禁止所有元素统一超大圆角

## 4. 书架 v3

书架不是“卡片列表”，而是阅读入口。

### 页面构图
- 顶部：`书架` + 搜索 + 添加；不常驻统计副标题。
- 首屏有作品时：最近阅读作为**唯一主锚点**，横向占满，显示封面、书名、当前章节。
- 其余作品进入固定双列封面墙。
- 只有一本书时，不允许左上角孤零零一张小封面 + 大面积死空白；以最近阅读主锚点稳定构图。
- 底部导航贴底、无巨大悬浮胶囊、无 Dock hover 视觉。

### 封面
- 宽高比约 `0.68–0.70`
- 圆角约 14dp，极轻阴影，无描边
- 封面下方：书名 > 当前章节；类型不是必须常驻
- 无封面时使用克制的编辑型渐变封面，不显示夸张品牌 Logo

### 交互
- 点按：继续阅读
- 长按：书籍动作 Sheet
- 添加：BottomSheet 中选择本地导入 / AI 创建
- 删除：明确 destructive Dialog

## 5. Reader v3

### 正文
- 默认字号：18.5sp（15–30sp）
- 默认行高：1.78×
- 默认左右页边距：24dp
- 默认段间距：6dp
- 中文正文默认衬线体
- 默认首行缩进 2em；分页从段落中部切开时只取消该残段的重复缩进
- 页面顶部留白必须明显大于工具页列表顶部留白

### 章节标题
- 第一页标题只比正文大约 2sp
- `FontWeight.Medium`
- 标题与正文间距约 18–22dp
- 后续页章节名极弱，仅做定位

### 阅读主题
默认 Paper：
- background `#FAF7F2`
- foreground `#1C1A17`
- secondary `#8A817A`
- chrome `#FEFCF8`

禁止纯黑正文 + 纯白纸张作为默认主题。

## 6. Reader Chrome

### 隐藏状态
只保留正文与单一极弱页码/进度。

### 唤起状态
顶部：
- 返回
- 当前章节（弱化）
- 更多

底部贴底控制面只允许：
- 目录
- A−
- 背景
- A+
- 排版

**禁止**一级出现：故事、编辑、AI 创作、运行中心、Skill、模型设置。

阅读控制不得做成屏幕中央大浮岛，也不得叠两套页码/进度。

## 7. 阅读设置

采用 HeroUI Native / coss 的 progressive disclosure：

### 第一层：常用
1. 背景主题
2. 字号
3. 阅读方式
4. 进入高级排版

### 第二层：高级排版
1. 字体
2. 行距
3. 页边距
4. 段间距
5. 首行缩进

高级排版是 Sheet 内的**二级页面**，不是把所有参数继续往下展开成长表单。

设置变化必须保持阅读锚点。

## 8. 目录

- 打开时优先定位当前章节
- 当前章节使用左侧细 accent + 字重强调
- 不使用整行黑色/高饱和背景
- 搜索默认收起，点图标展开
- 搜索框不得出现“Surface 套 OutlinedTextField 套另一层圆角”的嵌套盒子
- 章节行高约 48–52dp，分隔线极弱

## 9. AI / Skills / 运行中心

### AI 服务
- 页面只负责服务、模型、连接与任务模型路由
- Skill 管理不得混入 AI 服务页
- 任务路由属于高级项，默认折叠
- 新增/编辑连接时才显示完整表单

### Skills
- 默认：名称、简短说明、状态、开关
- 详细任务绑定按需展开
- 禁止一 Skill 一张大 Card
- 禁止黑色实心 Chip 表示选中

### 运行中心
状态统一映射：
- `neutral`：待执行/空闲
- `info`：运行中
- `success`：完成
- `warning`：需确认/部分异常
- `destructive`：失败

运行中心使用任务列表，不做指标墙或 Run Card 墙。

## 10. Novel Skill OS · Minimal Execution

Ponytail 思路转化为琅嬛内部执行阶梯：

1. 这个任务真的需要调用能力吗？
2. 当前上下文能直接完成吗？
3. 已有 StorySnapshot / 记忆 /缓存结果能完成吗？
4. 已有单一 Skill 能完成吗？
5. 已有 Tool 能完成吗？
6. 组合少量现有 Skill 能完成吗？
7. 最后才创建新计划或深度 Agent 流程。

用户可见执行深度：
- `自动`
- `轻量`
- `标准`
- `深度`

`自动`必须根据任务复杂度选择最小足够深度；禁止简单改一句简介却跑完整全书审计、RAG、Agent 重规划。

## 11. Motion

- Sheet / chrome 使用系统 Material motion；150–250ms 为主
- 不给正文做无意义动画
- 切章、翻页不得制造空白过渡页
- 任何动画不能阻塞触控或阅读进度持久化
- 21st.dev 的动效只允许用于 AI 输入、生成状态与短时反馈

## 12. Anti-patterns（代码评审直接拒绝）

- shadcn 黑白 Web 皮直接套 Android
- 书架只放一张小封面导致大片死空白
- 巨型悬浮书架 Dock
- 阅读底栏 6+ 一级操作
- AI / 编辑 / 故事与目录 / 字号同级
- 阅读设置第一层直接展开全部高级参数
- `GridCells.Adaptive` 用于手机主书架
- 每组设置都 `border = BorderStroke(1.dp, ...)`
- selected control 使用近黑实心块
- 默认正文 ≥ 20sp 且行高不足 1.7×
- 只有整章第一个段落缩进，后续段落不缩进
- 所有页面都套 Card
- 所有元素都使用同一个超大圆角
- 为了“高级感”到处 blur/glass
- 用“CI 通过”代替 UI/交互验收

## 13. 验收

每次 UI PR 至少同时通过：
1. 编译与单元测试；
2. 设计契约源码守卫；
3. 真机录屏：书架 → 打开书 → 阅读 → 目录 → 常用设置 → 高级设置 → 更多；
4. 一书、两书、多书三种书架密度检查；
5. 视觉检查：正文占屏主导、chrome 克制、第一层无创作工具；
6. AI/Skills/运行中心不得重新卡片墙化。
