package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.StorySnapshot

/**
 * Keeps calendar era, device capabilities and everyday adoption in the same reality.
 *
 * The model still handles semantic judgement, while the deterministic checks below only reject
 * high-signal combinations. This avoids treating every unusual prop as an error: a 2026 hotel can
 * have a landline, but an unexplained personal landline cannot silently behave like a smartphone.
 */
class EraTechnologyGuard {
    fun promptText(snapshot: StorySnapshot): String {
        val years = storyYears(snapshot)
        val anchor = if (years.isEmpty()) {
            "没有可靠公历年份；不得默认套用现实中的 2026 年，也不得在同一场景混用不同年代的设备和生活习惯。"
        } else {
            "已发现的故事年代锚点=${years.joinToString("、")}；如正文写到回忆或旧资料，必须明确区分资料年份与当前叙事年份。"
        }
        return """
            $anchor
            - 先确认当前场景年代，再选择当时真实存在、普通人确实会使用的通信、交通、支付、检索和安防方式。
            - 2020 年后的普通私人通信默认使用手机/即时通讯。座机、传呼机、公用电话等旧式设备只有在办公室前台、酒店、医院、值班室、老人住宅、保密线路、无信号环境等已写明理由时才可承担剧情功能。
            - 设备能力必须精确：座机不能出现锁屏、联系人头像、滑动接听、App 推送、微信通知等手机界面；若座机有号码显示，只能按该设备已建立的液晶屏/号码簿能力描写，不能笼统套用手机 UI。
            - 不要仅为制造悬疑选用反常设备。若反常本身是线索，必须让人物意识到反常，并给出可追踪的原因或疑问。
            - 不确定时，优先使用 Canon 已出现且符合年代的普通方案，不得临时发明便利技术推进剧情。
        """.trimIndent()
    }

    fun deterministicProblems(request: GenerationRequest, text: String): List<String> =
        detect(request, text).map { "${it.message}；证据=${it.evidence}；修法=${it.repair}" }

    fun inspect(request: GenerationRequest, output: GeneratedChapter): List<ConsistencyIssue> =
        detect(request, output.content).map { problem ->
            ConsistencyIssue(
                severity = IssueSeverity.BLOCKING,
                code = problem.code,
                message = problem.message,
                evidence = problem.evidence,
                repairInstruction = problem.repair,
            )
        }

    private fun detect(request: GenerationRequest, text: String): List<Problem> {
        if (text.isBlank()) return emptyList()
        val problems = mutableListOf<Problem>()
        val years = storyYears(request.snapshot)
        val modern = years.any { it >= 2020 }

        deviceMixRegex.find(text)?.let { match ->
            problems += Problem(
                code = "DEVICE_CAPABILITY_MIXED",
                message = "同一部座机被写出了手机界面或手机行为",
                evidence = excerpt(text, match.range),
                repair = "按设备类型重写：改为手机，或删除锁屏、头像、滑动接听、App/微信通知、振动、电量等手机专属描写。",
            )
        }

        ambiguousCallerIdRegex.find(text)?.let { match ->
            problems += Problem(
                code = "LANDLINE_CALLER_ID_AMBIGUOUS",
                message = "座机的来电信息被按手机式界面笼统描写，设备能力没有成立",
                evidence = excerpt(text, match.range),
                repair = "若剧情不依赖座机，改用手机；若必须保留座机，先建立具体场所与设备，并只写液晶屏显示的号码或已存号码簿名称。",
            )
        }

        if (modern) {
            landlineRegex.findAll(text).firstOrNull { match ->
                val context = excerpt(text, match.range, radius = 110)
                modernLandlineReasons.none(context::contains)
            }?.let { match ->
                problems += Problem(
                    code = "MODERN_LANDLINE_UNJUSTIFIED",
                    message = "${years.filter { it >= 2020 }.distinct().joinToString("/")} 年代背景中，座机承担日常通信却没有场所或人物理由",
                    evidence = excerpt(text, match.range, radius = 110),
                    repair = "普通私人通信改用手机/即时通讯；若座机是必要线索，明确它属于前台、值班室、医院、老人住宅、保密线路或无信号环境，并让人物对反常使用作出合理反应。",
                )
            }
        }

        val activeYear = years.maxOrNull()
        if (activeYear != null) {
            anachronisms.firstOrNull { rule -> activeYear < rule.availableFrom && rule.regex.containsMatchIn(text) }
                ?.let { rule ->
                    val match = requireNotNull(rule.regex.find(text))
                    problems += Problem(
                        code = "TECH_NOT_AVAILABLE_IN_ERA",
                        message = "$activeYear 年的当前场景出现了尚未普及或尚不存在的“${match.value}”",
                        evidence = excerpt(text, match.range),
                        repair = "换成当时可用且符合人物条件的技术；若这是后世资料、实验设备或架空设定，必须在场景中明确来源与规则。",
                    )
                }
        }

        return problems.distinctBy { it.code to it.evidence }
    }

    private fun storyYears(snapshot: StorySnapshot): List<Int> {
        val authoritative = buildList {
            add(snapshot.novel.premise)
            snapshot.bible
                .filter { it.locked && it.category in setOf(BibleCategory.WORLD, BibleCategory.RULE, BibleCategory.LOCATION, BibleCategory.ITEM) }
                .forEach { add("${it.name} ${it.content}") }
            snapshot.recentTimeline.filterNot { it.isFlashback }.takeLast(12).forEach {
                add("${it.storyTime} ${it.summary}")
            }
            snapshot.activeOutline.forEach { add("${it.title} ${it.objective} ${it.conflict} ${it.turningPoint}") }
        }.joinToString("\n")
        return yearRegex.findAll(authoritative)
            .mapNotNull { it.value.removeSuffix("年").toIntOrNull() }
            .filter { it in 1800..2199 }
            .distinct()
            .take(6)
            .toList()
    }

    private fun excerpt(text: String, range: IntRange, radius: Int = 70): String {
        val start = (range.first - radius).coerceAtLeast(0)
        val end = (range.last + radius + 1).coerceAtMost(text.length)
        return text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    }

    private data class Problem(
        val code: String,
        val message: String,
        val evidence: String,
        val repair: String,
    )

    private data class AnachronismRule(val availableFrom: Int, val regex: Regex)

    private companion object {
        val yearRegex = Regex("(?<!\\d)(?:18\\d{2}|19\\d{2}|20\\d{2}|21\\d{2})年?")
        val landlineRegex = Regex("座机|固定电话")
        val deviceMixRegex = Regex(
            "(?:座机|固定电话).{0,50}(?:锁屏|联系人头像|滑动接听|App|APP|应用推送|微信通知|消息通知|振动|震动|电量|充电口)|" +
                "(?:锁屏|联系人头像|滑动接听|App|APP|应用推送|微信通知|消息通知|振动|震动|电量|充电口).{0,50}(?:座机|固定电话)",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val ambiguousCallerIdRegex = Regex(
            "(?:座机|固定电话).{0,45}(?:来电显示|来电界面|来电头像)|(?:来电显示|来电界面|来电头像).{0,45}(?:座机|固定电话)",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val modernLandlineReasons = listOf(
            "办公室", "公司", "单位", "前台", "总机", "分机", "值班", "值守", "酒店", "宾馆", "旅馆", "客房",
            "医院", "诊所", "护士站", "银行", "学校", "派出所", "警局", "消防", "保安", "门卫", "物业", "监控室",
            "老人", "老年", "爷爷", "奶奶", "祖父", "祖母", "老宅", "祖宅", "保密", "专线", "内线", "无信号", "屏蔽",
            "断网", "停电", "应急", "复古", "收藏", "旧电话", "遗留线路", "液晶屏", "号码簿", "分机号",
        )
        val anachronisms = listOf(
            AnachronismRule(2011, Regex("微信|微信支付|扫码支付|二维码付款")),
            AnachronismRule(2016, Regex("抖音|短视频直播|共享单车")),
            AnachronismRule(2014, Regex("网约车|手机人脸解锁|刷脸支付")),
            AnachronismRule(2007, Regex("智能手机|触屏手机|手机应用商店|App\\s*推送|APP\\s*推送")),
        )
    }
}
