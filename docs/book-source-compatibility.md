# 书源格式、发现与 AI 生成能力

核对日期：2026-09-30。琅嬛实现的是独立的静态 HTML 书源子集，不是完整阅读/Legado 引擎。能导入某个 JSON 格式，不等于该书源依赖的所有运行能力都存在。

## 原始格式依据

原 `gedoor/legado` 仓库目前只保留[项目内容删除公告](https://github.com/gedoor/legado)，不能再将其表述为当前可直接集成的源码。本次核对的是仍可访问的 `LegadoTeam/legado` 维护仓库：

- [BookSource.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/data/entities/BookSource.kt)：搜索、发现、详情、目录、正文是不同规则组，发现还有独立启用开关
- [ExploreRule.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/data/entities/rule/ExploreRule.kt)：发现列表的名称、作者、书籍链接、封面等字段
- [AnalyzeRule.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt)：原实现包含 HTML、JSONPath、XPath、JavaScript 等多种运行模式
- [AnalyzeUrl.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt)：原实现的分页、请求选项与脚本能力远多于本项目
- [WebBook.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/model/webBook/WebBook.kt)：发现请求接收独立页码，使用发现列表规则
- [ExploreAdapter.kt](https://github.com/LegadoTeam/legado/blob/master/app/src/main/java/io/legado/app/ui/main/explore/ExploreAdapter.kt)：分类可包含动态控件与脚本动作，不能简单等同于静态链接

上述项目按 GPLv3 发布。本轮只核对字段与行为，没有复制、打包或接入其实现代码。

## 当前已映射的经典字段

| 规则组 | 支持的字段 |
| --- | --- |
| 书源 | `bookSourceUrl`、`bookSourceName`、`bookSourceGroup`、`enabled`、静态对象形式 `header` |
| 搜索 | `searchUrl`；`ruleSearch.bookList/name/author/coverUrl/intro/lastChapter/bookUrl` |
| 发现 | `enabledExplore`、`exploreUrl`；`ruleExplore.bookList/name/author/coverUrl/intro/lastChapter/bookUrl` |
| 详情 | `ruleBookInfo.name/author/coverUrl/intro/tocUrl` |
| 目录 | `ruleToc.chapterList/chapterName/chapterUrl/nextTocUrl` |
| 正文 | `ruleContent.content/nextContentUrl/replaceRegex` |

书源列表与单个对象都可导入；规则组可以是 JSON 对象或包含对象文本的字符串。内部保存和导出使用琅嬛自身的扁平字段，例如 `exploreList`，不声称导出后能保持所有 Legado 专有字段。

支持 CSS、常见 `class./id./tag./text./children` 链、元素索引与排除、`||` 候选规则、`&&` 值拼接、`##正则##替换`、列表前置 `-` 反转。正则使用受限 RE2 语法；不是任意 JavaScript 或完整 Java 正则环境。

`kind/wordCount/updateTime` 等本项目未展示的结果元数据、排序与界面样式等不保证保留。音频、漫画、视频、段评、交互登录 UI、完整变量系统不是已实现的阅读能力。

## 发现、分类、排行榜与分页

排行榜本质上是网站提供的一种发现入口；网站没有榜单，就不会生成榜单。

支持：

- 字符串 `玄幻::/category?page={{page}}&&月榜::/ranking?page={{page}}`，或用换行分隔
- 静态 JSON 数组 `[{"title":"月榜","url":"/ranking"}]`；兼容名称字段 `name`
- 只有标题、没有 URL 的条目作为分组标题跳过，不当作抓取入口
- 保留完整模板，在每次请求时替换 `{{page}}`，不会生成分类按钮时就固定为第 1 页
- `地址,{"method":"POST","body":"type=month&page={{page}}","charset":"gbk"}`；同 URL、不同 body 的分类独立保留，UI 使用完整模板作为身份
- 没有模板的静态页面，从已读取 HTML 中寻找同站 `rel=next` 或明确“下一页”链接。不会自行拼接不存在的地址
- 加载更多、去重、停止和失败重试；切换分类、发起新搜索、修改/禁用/删除书源会取消旧任务并阻止旧结果覆盖新结果

模板分页保留原始 method/body/charset；“还有一页”与“观察到的下一页 URL”分开记录，因此 POST 固定 URL 分页不会退化为 GET 或提前停止。无下一页且无分页模板、或模板返回空页时结束。只有重复置顶书、但存在未访问的下一页链接时继续；模板反复返回相同书目或链接循环时明确提示分页异常并可重试，不伪装为最后一页。每页超过 2000 本、超过 1000 页会明确失败，不把截断结果说成完整；分类超过 60 项、`exploreUrl` 超过 8192 字符也有明确提示。

当前只支持字符串选项 `method/body/charset`（GET、POST、HEAD）。请求级 `headers/header/webView/webJs/js/bodyJs/dnsIp/timeout` 等未实现选项会指出字段并拒绝，书源顶层静态 `header` 不受此限制。`{{JS表达式}}`、`<首页,后续页>` 模板、JSON API 等不等同于受支持的 `{{page}}`。

非空 `jsLib/mainJs/loginCheckJs/coverDecodeJs/exploreScreen` 会在导入时报出具体字段，不再丢掉依赖后假装成功。动态 `exploreUrl`、脚本按钮、动态 `action/viewName/type` 分类控件也明确报错。没有开启无约束 JS 执行。

## AI 生成与验证

AI 生成使用用户已配置的提供商；书源网络请求不会携带 AI 密钥。页面结构与错误反馈按不可信数据处理。

1. 读取首页及重定向后的站点，识别真实搜索表单
2. 写搜索规则并实际取书，失败带证据修正一次
3. 写详情/目录规则并读取目录，再写正文规则并读取至少 60 字样本
4. 从页面已有的同站静态链接中选择分类/榜单；返回名称和 URL 必须与 HTML 证据相符，虚构地址不会请求
5. 对入口逐页生成共同的发现列表规则；允许一层分类/榜单目录，最多验证 12 个入口
6. 每个保留入口实际取得书目，并抽一本完成详情→目录→正文验证；若观察到下一页，再请求它并检查存在新增书籍

报告保存每个入口的名称、原 URL、首页书数、抽样书籍、目录数、下一页 URL 与第二页书数。UI 展示这些结果。

没有静态发现入口时，保留搜索能力，发现步骤显示中性“未找到”状态。部分入口失败时，只保存经过验证的入口，失败名称与原因保留为警告，发现步骤不会显示绿色通过；第一页可读但第二页失败时，明确显示分页未通过，用户可以重试。搜索与阅读通过不会被表述为发现全部通过。AI 响应中的未知字段、嵌套非字符串规则、脚本能力会报错，不静默忽略。

生成失败或停止不会清空表单输入；取消会传播协程取消，晚到结果受任务代次约束，未确认保存的草稿不会覆盖已有书源。

## 可重复验证与边界

`AiDiscoveryJourneyV43Test` 使用受控原创 HTML 和假的模型响应，运行实际 builder/解析器，覆盖完整分类+月榜+分页阅读链路、缺少发现、虚构榜单、部分失败、第二页失败、取消；不调用真实模型或私有 API key。`SourceDiscoveryV41Test` 覆盖静态格式、POST 同 URL 不同 body、分页请求、失败与条数边界、动态能力提示。

Android 的 `SourceHttpsJourneyV41Test` 使用提交固定的公开 HTTPS 夹具验证安全网络传输。`app/src/androidTest/assets/source-fixture/` 的分类、榜单和故事都是明确标注的原创自动化测试数据；它们不会内置为用户书源或真实推荐。完整 APK/设备结果以对应提交的 CI 报告为准，独立 JVM 通过不等于设备全验收。

## HTTP 与网页验证诊断

HTTP 错误保留实际状态码、协议和域名；不再根据 400 推断登录、验证码或动态网页。错误响应最多读取 8 KiB 前缀作分类，不显示服务器返回的原文、查询参数、Cookie 或请求头。HTTP 地址收到 400 时只建议确认 HTTPS 地址，不自动换站或降级安全检查。

Cloudflare 的普通 HTML 页面也可能带有 [JavaScript Detections](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/javascript-detections/) 脚本，因此脚本路径 `challenge-platform`、一般“请稍候”文案或书中引用验证提示均不构成拦截证据。优先依据官方 [cf-mitigated: challenge 响应标记](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/)，其次结合验证页标题、可见提示和专有页面结构。

真正的浏览器验证或访问拦截仍会停止生成，并明确说明尚未获得内容。当前不会执行网站脚本、破解验证码或绕过访问控制；外部浏览器会话不会自动共享给 App。首页失败时不会启动模型调用，失败原因在对应步骤只显示一次。
