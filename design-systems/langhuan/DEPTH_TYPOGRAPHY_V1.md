# 琅嬛 Depth Typography V1

这份规范是应用 UI 的全局视觉基线；阅读正文排版不继承本规范，继续使用 Reader 独立排版系统。

## Typography

- 一级标题：22 / 28，SemiBold，letter-spacing -0.7px。
- 章节/二级标题：19 / 24，SemiBold，letter-spacing +0.6px。
- UI 正文、日期与辅助正文：14.5 / 18，Regular，letter-spacing +0.3px。
- 英文/数字目标视觉为 Inter Tight；当前 Android 字体栈使用系统 SansSerif 并保持上述 Inter Tight 节奏，中文由 CJK fallback 负责。
- 不允许为了视觉统一把这套字阶写入小说正文阅读器。

## Semantic color roles

- `foreground/text`：主要文字。
- `muted` / `mutedForeground`：次要信息与说明。
- `strong`：图标与强调但非品牌色的控件内容。
- `track`：唯一的中性 1px 分隔线。
- 不使用可见灰色边框表达卡片层级。

## Radius and depth

- Depth 0：12dp radius，2dp soft shadow。
- Depth 1：15dp radius，6dp soft shadow。
- Depth 2：18dp radius，10dp soft shadow。
- 大型弹层允许 18–24dp radius 与更高 shadow。
- Surface 顶部使用极弱 inset highlight gradient；避免平面纯色块。

## Buttons

- 图标按钮 38–46dp；默认圆形。
- 采用 soft shadow + inset highlight，无明显 outline。
- selected 状态通过 tonal background / semantic color 变化表达，不增加描边。

## Cards and separators

- 卡片根据重要度使用 12/15/18dp radius 与对应 depth。
- 禁止使用 visible border 作为默认层级墙。
- 分隔线仅为 1dp `track`，低对比。

## Migration rule

新的共享组件必须优先使用 `LanghuanUiKit` / `LanghuanComponentKitV4` 的 semantic roles。旧页面逐步迁移；不得在新页面重新引入独立灰色边框体系。
