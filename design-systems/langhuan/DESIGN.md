# 琅嬛 · Reading First

> Category: Mobile reading / AI novel studio
> Status: Production design contract

琅嬛不是 Web dashboard，也不是把工具箱套进小说阅读器。**阅读表面就是产品本体；创作与 AI 能力必须在需要时出现。**

## 1. 设计血统

- **Readest**：内容优先、chrome 后退、阅读面比工具更重要。
- **Book's Story / Material You**：Android 原生移动层级、动态色、底部 Sheet、触控尺寸与系统行为。
- **Open Design / Warm Editorial**：长文优先、暖纸感、近黑正文、克制装饰、明确 anti-pattern。
- **shadcn/ui**：只吸收 open-code、composition、semantic tokens、consistent variants；禁止把 Web 组件外观照搬到 Android。

## 2. 不可违背的产品原则

1. **Content first, chrome recedes.** 阅读时默认只看正文和极弱进度；控制层由中央轻触临时唤起。
2. **One primary task per surface.** 书架先找书，阅读页先读，创作页先对话/写作；不得一屏同时争抢多个主任务。
3. **Progressive disclosure.** 常用阅读设置第一层呈现，高级排版折叠；AI、编辑、进入故事不进入阅读一级工具栏。
4. **No dashboard in reading flows.** 禁止卡片墙、指标墙、粗边框表单、网页式 responsive grid。
5. **Native Android hierarchy.** 使用 Material 3/MIUIx 的 Surface、BottomSheet、动态色与大触控区；视觉精致靠层级和留白，不靠堆圆角。

## 3. 阅读正文规范

### 默认正文
- 默认字号：18.5sp（用户可 15–30sp 调整）
- 默认行高：1.78×
- 默认左右页边距：24dp
- 默认段间距：6dp
- 中文正文默认衬线体
- 默认首行缩进 2em，**每个自然段都缩进**；分页切在段落中间时，仅该页首段残片不重复缩进

### 章节标题
- 第一页标题只比正文大约 2sp，不做网页大标题
- FontWeight.Medium；标题与正文间距 18–22dp
- 后续页仅显示极弱章节名，不与正文抢层级

### 页面色彩
默认 Paper：
- background `#FAF7F2`
- foreground `#1C1A17`
- secondary `#8A817A`
- chrome `#FEFCF8`

禁止纯黑正文、纯白纸张作为默认阅读主题。

## 4. 阅读控制层

### 隐藏状态
只保留：
- 正文
- 极弱章节/进度信息

### 第一级控制
顶部：
- 返回
- 当前章节（单行）
- 更多

底部只允许阅读动作：
- 目录
- A−
- 背景/主题
- A+
- 排版

**禁止**在第一级出现：`故事`、`编辑`、`创作`、`运行中心`、`Skill`、模型设置。
这些进入“更多/书籍信息”Sheet。

## 5. 阅读设置信息架构

### 常用（默认展开）
1. 背景主题
2. 字号 A− / 数值 / A+
3. 翻页方式

### 高级（默认折叠）
1. 字体：衬线 / 无衬线
2. 行距
3. 页边距
4. 段间距
5. 首行缩进

设置变化必须保持阅读锚点。

### 控件外观
- 不使用黑色实心 selected chip
- selected 使用语义 accent / primaryContainer
- 不给每一组都套 1dp 边框卡
- 同组使用单一 Surface + divider 或直接留白

## 6. 目录

- 当前章节用左侧细 accent + 轻背景表示，不整行涂黑
- 默认不显示搜索框，点击搜索图标后展开
- 章节行高 48–54dp
- 分隔线极弱或省略
- 打开目录时优先定位当前章节

## 7. 书架

- 手机竖屏固定双列封面，不使用 Web `Adaptive` 网格
- 封面 > 书名 > 类型/作者，进度是辅助信息
- 顶部只保留标题、搜索、添加
- 书籍长按/更多进入 BottomSheet
- 禁止 Dashboard 指标卡、黑色实心 CTA、封面粗描边

## 8. AI / 创作与阅读的关系

AI 是琅嬛的能力，不是阅读器的 chrome。

阅读页“更多”中可进入：
- 编辑本章
- 进入故事
- AI 创作 / 继续创作
- 书籍信息

离开阅读上下文前必须保存当前阅读进度。

## 9. Motion

- Sheet / chrome 使用系统 Material motion；150–250ms 为主
- 不给正文做无意义动画
- 切章、翻页动效不得制造空白过渡页
- 任何动画都不能阻塞触控或阅读进度持久化

## 10. Anti-patterns（代码评审直接拒绝）

- shadcn 黑白 Web 皮肤直接套 Android
- 阅读底栏 6+ 一级操作
- AI / 编辑 / 故事与目录 / 字号同级
- `GridCells.Adaptive` 用于手机主书架
- 每组设置都 `border = BorderStroke(1.dp, ...)`
- selected control 使用近黑实心块
- 默认正文 ≥ 20sp 且行高不足 1.7×
- 只有整章第一个段落缩进，后续段落不缩进
- 用“CI 通过”代替 UI/交互验收

## 11. 验收

每次 UI PR 至少同时通过：
1. 编译与单元测试；
2. 设计契约源码守卫；
3. 真机录屏：书架 → 打开书 → 阅读 → 目录 → 常用设置 → 高级设置 → 返回阅读；
4. 视觉检查：正文占屏主导、chrome 克制、第一层无创作工具。
