# 琅嬛阅读版测试分支

## 本轮范围

- 默认进入原有书架，主导航简化为书架 / 书城 / 我的
- 原书架网格、封面、分组、排序和长按交互保留；单测固定主体与封面源代码指纹
- 书城使用真实的已启用书源搜索，不内置小说源或虚构榜单
- 导入、启停、删除、搜索书源与 AI 生成集中到书源管理
- AI 复用现有服务配置，生成过程展示实际搜索、目录、正文、发现分类/榜单与下一页验证结果；生成的分类名称和链接必须能在该网站真实HTML中找到
- 原有创作数据和页面保留，主要入口移至“我的 → 其他工具”
- 正文边界采用逐行原生字体度量，行距作为基线间距，段距独立加入；普通满页只在基线距 6% / 0.1em 的较小上限内微调，章末、标题和单行页保留自然留白
- 复合 emoji 不再被两端对齐拆成多个独立绘制单元

## 支持边界

书城支持跨源搜索与源提供的真实静态发现分类；发现入口支持字面量“标题::URL”列表及 title/url JSON 数组，保留 {{page}} 分页模板和 POST 请求体，也可跟随网页真实下一页链接，尊重 enabledExplore。详见 [书源兼容范围](book-source-compatibility.md)。不提供虚构推荐、评分或热榜。仅支持明确声明的网页规则子集，不声称完整兼容 Legado 的 JavaScript、JSONPath、XPath 或脚本生成发现规则。

测试 APK 按要求保留原应用身份 `com.xiguli.langhuan` 和名称「琅嬛」。不创建并排应用，不修改系统签名校验或用户设备安全设置。

历史 GitHub Actions 调试 APK 使用临时证书，不同构建的证书并不相同。不能承诺此 APK 可直接安全覆盖手机上的版本。请先保留书籍和项目备份；不要为了安装而卸载原版或清空数据。本项目当前支持逐书导出 TXT/EPUB/Markdown 与单项目备份，不提供整个书架、分组和阅读进度的一键无损迁移。

## 排版取舍与参考

行高不再作为上下各塞半份留白的字形盒：改变行距时首行真实字形不会跟着下移，末行之后也不占一次额外行距。CJK、拉丁文和 emoji 使用实际行字体上下界，避免 fallback 字体重叠或被裁切。

当下一行只差很少空间，分页器比较“少一行后拉开”和“多一行后轻微收紧”，选择所需调整更小的方案。补偿最多为 `min(基线距×6%, 字号×0.1)`，不足以自然齐底时允许少量余白，避免为几何齐底牺牲行距节奏。标题、短末页不做齐底拉伸。

独立实现参考维护项目 LegadoTeam 的 [TextLine 字形边界](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/ui/book/read/page/entities/TextLine.kt)、[TextPage 齐底处理](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/ui/book/read/page/entities/TextPage.kt) 和 [TextChapterLayout 分页](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt)；没有复制其 GPL 实现。并未声称像素级复刻阅读。

## 第二轮可靠性修复

- 快速翻页后切后台、退出、字号重排中断会保存最新句子锚点；Activity 重建保持阅读页，进程重启后可从书架继续阅读
- 声明目录存在时优先采用该目录，不误跟推荐书；目录后页失败或超过安全页数上限不会伪装为完整目录
- 正文规则的 `##` 清理表达式不再被自动补充的属性损坏
- 数据库先在临时副本预检；不兼容、损坏、复制失败或磁盘不足时保留原始文件并显示诊断，禁止自动清空重建
- SQLite 损坏回调也使用保留策略；正常 1→2 迁移及数据库字段保持不变

## 验证入口

- `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- `gradle :app:connectedDebugAndroidTest`（本地 HTTPS 外部链路测试需显式提供 sourceFixtureBase；CI 自动绑定当前提交）
- `ReaderEditionV41Test` 覆盖三档宽度、三档字体缩放、三档字号、fallback 字体的真实边界与无丢字/重叠验证；保存连续十页，以及九档行距×段距的标题/连续两页/章末 36 图和间距 CSV
- 设备测试保存书架、书城、书源、“我的”、连续阅读与受控分类/榜单截图；截图夹具明确为测试原创内容
- `ReaderProgressV42DeviceTest` 在冻结保存防抖时触发暂停/销毁；`ReaderRecreationV42DeviceTest` 覆盖长章换字号、真实 Activity 重建与横屏
- `StartupDatabasePreservationTest` 仅使用随机隔离数据库，校验坏库/旧schema/备份失败/磁盘满模拟/取消时原始字节保留
- `AiDiscoveryJourneyV43Test` 使用确定性的模型响应与原始测试网页覆盖 AI 完整生成路径，不需要真实 API key；生产AI服务付费调用未在CI执行
- `SourceHttpsJourneyV41Test` / `SourceDiscoveryV43DeviceTest` 经公共HTTPS读取本仓库固定版本原创夹具，验证分类、月榜、第二页、详情、目录、正文与入书架

测试分支基于 PR #103 的 `cd844c6`；相对最初快照 `31a8380`，只额外接纳了同分支的目录父节点循环编译修复。未改动原 PR 分支，未合并主分支，未发布正式版。
