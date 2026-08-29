package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GenerationRequest
import kotlin.math.roundToInt

/** Deterministic prose-quality diagnostics used before the adversarial editor. */
data class ProseQualityReport(
    val score: Int,
    val requiresNovelization: Boolean,
    val blocking: Boolean,
    val problems: List<String>,
    val metrics: Map<String, Int> = emptyMap(),
) {
    fun summary(): String = buildString {
        append("小说化质量=${score}分")
        metrics.entries.sortedBy { it.key }.take(8).forEach { (key, value) -> append(" · $key=$value") }
    }
}

/**
 * Scene-first rewrite layer.
 *
 * It never changes Canon and never invents missing facts. The detector is local; a model call happens only
 * when the first draft clearly looks like a report, setting card, AI template or function-log narrative.
 */
class NovelizationEngine(
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
) {
    fun analyze(prose: String): ProseQualityReport {
        val text = prose.trim()
        if (text.isBlank()) {
            return ProseQualityReport(
                score = 0,
                requiresNovelization = true,
                blocking = true,
                problems = listOf("正文为空，无法作为小说正文提交。"),
            )
        }

        val backendMarkers = listOf(
            "他目前掌握的信息", "她目前掌握的信息", "目前掌握的信息", "本章总结", "状态更新",
            "已确认事实", "本章要点", "信息汇总", "调查结果如下", "touchedForeshadowingIds", "stateChanges",
            "场景计划", "本章约", "人物状态", "伏笔状态",
        ).sumOf { countLiteral(text, it) }
        val numberedLines = text.lineSequence().count { line ->
            Regex("^\\s*(?:第[一二三四五六七八九十百]+(?:人|项|条)|第\\d+(?:人|项|条)|\\d+[.、)])").containsMatchIn(line)
        }
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotBlank)
        val colonHeavyParagraphs = paragraphs.count { paragraph ->
            paragraph.count { it == '：' || it == ':' } >= 3 ||
                Regex("(?:姓名|年龄|职业|地点|结果|结论|状态|证据)[：:]").findAll(paragraph).count() >= 3
        }
        val explanationMarkers = listOf(
            "这说明", "这意味着", "也就是说", "换句话说", "显然", "不难看出", "由此可见", "可以确定",
            "因此可以", "结论是", "事实证明",
        ).sumOf { countLiteral(text, it) }
        val functionalVerbs = Regex("(?:搜索|检索|核对|记录|分类|整理|归档|重新排列|逐条|输入|查询|调取|打开|写下|列出|筛选|比对)")
            .findAll(text).count()
        val aiCliches = listOf(
            "仿佛", "似乎", "某种", "无声地", "缓缓", "微微", "猛地", "不由得", "空气中", "那一刻",
            "莫名的", "说不清", "难以言喻", "一股寒意", "心头一紧",
        ).sumOf { countLiteral(text, it) }
        val contrastTemplates = Regex("不是[^。！？\\n]{0,36}[，,]?而是").findAll(text).count()
        val genericHorror = listOf("雾气", "水汽", "敲门声", "脚步声", "低语", "血字", "镜子", "黑影", "人影", "冷气")
            .sumOf { countLiteral(text, it) }
        val longParagraphs = paragraphs.count { it.length >= 850 }
        val actionSignals = Regex("(?:走|停|抬|按|推|拉|转|看|盯|听|拿|放|收|递|敲|开|关|坐|站|蹲|退|追|躲|翻|拨|接|问|答|说)")
            .findAll(text).count()
        val quoteMarks = text.count { it == '“' || it == '”' || it == '「' || it == '」' }

        var score = 100
        val problems = mutableListOf<String>()
        if (backendMarkers > 0) {
            score -= 34
            problems += "正文出现后台总结/状态字段或“目前掌握的信息”式汇报口吻；必须彻底场景化。"
        }
        if (numberedLines >= 6) {
            score -= 24
            problems += "存在连续编号枚举，信息被当成清单倾倒；保留少数有戏剧价值的证据，其余通过场景自然释放。"
        } else if (numberedLines >= 3) {
            score -= 10
            problems += "枚举式信息偏多，避免把小说写成调查笔记或资料卡。"
        }
        if (colonHeavyParagraphs >= 3) {
            score -= 18
            problems += "多个段落呈现字段:值式资料卡结构，应改为人物行动、对白、物件和现场发现。"
        }
        if (functionalVerbs >= 18) {
            score -= 20
            problems += "搜索/核对/记录等功能性动作过密，人物像检索程序；必须补入欲望、关系、阻力、选择和代价。"
        } else if (functionalVerbs >= 11) {
            score -= 9
            problems += "功能性调查动作偏密，减少机械流程，保留真正改变局面的动作。"
        }
        val perThousand = 1000.0 / text.length.coerceAtLeast(1)
        val explainDensity = (explanationMarkers * perThousand).roundToInt()
        if (explanationMarkers >= 6 || explainDensity >= 5) {
            score -= 13
            problems += "解释性结论句密度过高；让动作、反应和上下文自己证明含义。"
        } else if (explanationMarkers >= 3) {
            score -= 6
            problems += "减少“这说明/这意味着/也就是说”类替读者总结。"
        }
        if (aiCliches >= 10) {
            score -= 12
            problems += "模板化 AI 氛围词过密，改用具体可观察的声音、动作、距离、物件和生理反应。"
        } else if (aiCliches >= 6) {
            score -= 6
            problems += "泛化氛围词偏多，优先使用具体可感知细节。"
        }
        if (contrastTemplates >= 4) {
            score -= 9
            problems += "“不是A而是B”模板重复，句式需要自然变化。"
        }
        if (genericHorror >= 10) {
            score -= 7
            problems += "通用恐怖意象堆叠过多；恐怖应来自本书规则、人物处境和因果异常。"
        }
        if (longParagraphs >= 2) {
            score -= 8
            problems += "长段解释过多，拆成可发生、可观察、可反应的场景节拍。"
        }
        if (text.length >= 1400 && actionSignals < 10 && quoteMarks < 4) {
            score -= 12
            problems += "正文篇幅不短但场景动作/互动信号太少，疑似说明文；需要让信息在人物行为中发生。"
        }

        score = score.coerceIn(0, 100)
        val hardTrigger = backendMarkers > 0 || numberedLines >= 6 || colonHeavyParagraphs >= 3 || functionalVerbs >= 18
        val requires = hardTrigger || score < 76
        val blocking = backendMarkers > 0 || score < 48
        return ProseQualityReport(
            score = score,
            requiresNovelization = requires,
            blocking = blocking,
            problems = problems.distinct().take(10),
            metrics = linkedMapOf(
                "后台痕迹" to backendMarkers,
                "编号枚举" to numberedLines,
                "资料卡段" to colonHeavyParagraphs,
                "功能动作" to functionalVerbs,
                "解释句" to explanationMarkers,
                "AI腔词" to aiCliches,
                "模板对照" to contrastTemplates,
                "长解释段" to longParagraphs,
            ),
        )
    }

    fun buildRewrite(
        request: GenerationRequest,
        prose: String,
        report: ProseQualityReport,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
    ): PromptBundle {
        val context = contextBuilder.build(request, retrievedContext)
        val diagnoses = report.problems.ifEmpty { listOf("当前稿件场景化不足，需要提升小说叙事感。") }
            .joinToString("\n") { "- $it" }
        return PromptBundle(
            system = """
                你是中文长篇小说的“小说化重构编辑”。你的任务不是润色句子，而是把报告体、设定卡体、调查清单体、AI模板体改造成真正发生在场景里的小说正文。

                绝对规则：
                1. 只重构表达方式，不得新增、删改或提前揭露事实；S/A 层章节合同、Canon、时间轴和信息边界绝对优先。
                2. 原稿中的有效剧情因果必须保留，但信息要通过人物行动、对话、物件、现场、阻力、误判、选择与后果逐步出现。
                3. 禁止“他目前掌握的信息”“本章总结”“已确认事实”“状态更新”“调查结果如下”等后台/汇报语言。
                4. 禁止把人物写成搜索引擎：查询、核对、记录只能保留真正改变局面的少数动作，并让每个动作带着动机、阻力或代价。
                5. 不要把十几个姓名、年龄、职业、死亡地点连续罗列；只展示当下场景真正需要的少数证据，其余用自然概括或延后释放。
                6. 不替读者解释“这说明/这意味着”；能由动作、表情、停顿、物件和因果看出的内容不要再总结。
                7. 不靠雾、敲门、血字、低语、黑影等通用恐怖意象堆气氛；异常必须服务于本章因果和本书规则。
                8. 保持原 POV、人物知识边界、故事时间、地点和已确认状态；不能为了更戏剧化让人物突然获得权限、证据或知识。
                9. 章末只能留下因当前场景自然产生的新问题、代价或选择，禁止用总结清单收尾。
                10. 输出完整重写后的小说正文；不要 Markdown、不要解释、不要点评、不要标题前缀。
            """.trimIndent(),
            user = """
                【本章执行合同 S】
                ${context.execution}

                【Canon 与硬边界 A】
                ${context.canon}

                【当前状态 B】
                ${context.state}

                【文风与长期写法 C】
                ${context.style}

                【本地小说化诊断｜${report.score}分】
                $diagnoses

                【待重构原稿】
                $prose

                请从头重写整章。只改变“怎么写”，不能改变“发生了什么”和“谁知道什么”。
            """.trimIndent(),
            jsonMode = false,
        )
    }

    private fun countLiteral(text: String, token: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(token, index)
            if (index < 0) return count
            count++
            index += token.length.coerceAtLeast(1)
        }
    }
}
