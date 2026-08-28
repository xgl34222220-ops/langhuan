package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.StorySnapshot

enum class ChronologyRepairRisk(val label: String) { LOW("低"), MEDIUM("中"), HIGH("高") }

data class ChronologyAnchor(
    val paragraph: Int,
    val phrase: String,
    val relativeDay: Int? = null,
    val clock: String = "",
    val kind: String,
)

data class ChronologyRepairFinding(
    val risk: ChronologyRepairRisk,
    val code: String,
    val paragraph: Int,
    val title: String,
    val detail: String,
    val evidence: String,
    val repair: String,
)

data class ChronologyRepairReport(
    val anchors: List<ChronologyAnchor>,
    val findings: List<ChronologyRepairFinding>,
) {
    val overallRisk: ChronologyRepairRisk
        get() = findings.maxByOrNull { it.risk.ordinal }?.risk ?: ChronologyRepairRisk.LOW
}

/** 旧正文时间线体检。这里只做确定性/保守提示，复杂语义冲突交给 AI 二次复核。 */
object ChronologyRepairAnalyzer {
    private val relativeDay = Regex("([一二两三四五六七八九十百\\d]+)天前")
    private val clock = Regex("(?:凌晨|清晨|早晨|上午|中午|下午|傍晚|晚上|深夜)?[零一二三四五六七八九十两\\d]{1,4}点(?:[零一二三四五六七八九十\\d]{1,3}分)?")
    private val relativeWords = Regex("当天|当晚|当夜|次日|翌日|第二天|数日后|几天后|数周后|数月后|几年后|多年后")
    private val sceneAction = Regex("赶到|来到|走进|离开|回到|前往|抵达|站在|拨通|进入")
    private val retrospective = Regex("那晚|那天|当晚|整晚|中途离席|曾经|记得|早就回去|后来")
    private val disappearance = Regex("失踪(?:时间)?[^。；\\n]{0,24}(?:为|是|暂定为)?\\s*([一二两三四五六七八九十百\\d]+)天前")

    fun analyze(snapshot: StorySnapshot, content: String): ChronologyRepairReport {
        val paragraphs = content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
        val anchors = mutableListOf<ChronologyAnchor>()
        val findings = mutableListOf<ChronologyRepairFinding>()

        paragraphs.forEachIndexed { index, paragraph ->
            relativeDay.findAll(paragraph).forEach { match ->
                anchors += ChronologyAnchor(index + 1, match.value, chineseNumber(match.groupValues[1])?.let { -it }, kind = "相对日")
            }
            clock.findAll(paragraph).forEach { match ->
                anchors += ChronologyAnchor(index + 1, match.value, clock = match.value, kind = "钟点")
            }
            relativeWords.findAll(paragraph).forEach { match ->
                anchors += ChronologyAnchor(index + 1, match.value, kind = "相对时间")
            }
        }

        val missingMatch = disappearance.find(content)
        if (missingMatch != null) {
            val missingDays = chineseNumber(missingMatch.groupValues[1])
            val sceneIndex = paragraphs.indexOfFirst { sceneAction.containsMatchIn(it) && (it.contains("聚会") || it.contains("饭局") || it.contains("宴")) }
            if (sceneIndex >= 0) {
                val witnessWindow = paragraphs.drop(sceneIndex).take(10).joinToString("\n")
                if (retrospective.containsMatchIn(witnessWindow)) {
                    findings += ChronologyRepairFinding(
                        risk = ChronologyRepairRisk.HIGH,
                        code = "SCENE_EVENT_TIME_COLLISION",
                        paragraph = sceneIndex + 1,
                        title = "当前场景与已发生事件疑似被写成同一现场",
                        detail = "前文已把失踪锚定在${missingDays?.let { "约 $it 天前" } ?: "过去"}，但后面又用正在发生的动作进入聚会，同时证人以“那晚/整晚/中途离席”等方式回忆同一聚会。读者无法判断聚会是现在还是失踪当晚。",
                        evidence = paragraphs[sceneIndex].take(180),
                        repair = "二选一锁死：要么明确“周衍去找参加过聚会的同学/调取聚会资料”；要么把这一整段标成三天前的闪回，并在结束后明确回到现在。",
                    )
                }
            }
        }

        val explicitDayParas = anchors.filter { it.relativeDay != null }.map { it.paragraph }.distinct()
        if (explicitDayParas.size >= 3) {
            val lastHistoryPara = explicitDayParas.maxOrNull() ?: 0
            val candidate = paragraphs.withIndex().firstOrNull { (idx, p) -> idx + 1 > lastHistoryPara && sceneAction.containsMatchIn(p) && relativeDay.find(p) == null }
            if (candidate != null) {
                findings += ChronologyRepairFinding(
                    risk = ChronologyRepairRisk.MEDIUM,
                    code = "TIME_SCOPE_RETURN_UNMARKED",
                    paragraph = candidate.index + 1,
                    title = "历史资料段结束后没有明确回到当前时间",
                    detail = "前文连续使用多个“X天前”建立了历史资料时间层，随后直接进入动作场景，缺少“回到现在/当天下午/三天后的此刻”等桥接。",
                    evidence = candidate.value.take(180),
                    repair = "在镜头切回当前调查时增加一句明确时间桥，或把后续场景显式标为过去时段。",
                )
            }
        }

        val timelineDays = snapshot.recentTimeline.filter { !it.isFlashback && it.storyDay > 0 }.map { it.storyDay }
        if (timelineDays.zipWithNext().any { (a, b) -> b < a }) {
            findings += ChronologyRepairFinding(
                ChronologyRepairRisk.HIGH,
                "STORED_TIMELINE_REVERSED",
                0,
                "长期时间线本身存在倒退",
                "已经保存的主时间线 storyDay 出现后项小于前项，继续生成会把错误当成事实。",
                timelineDays.joinToString(" → "),
                "先修复长期时间线，再让后续章节从最后一个可信主时间锚点继续。",
            )
        }

        if (anchors.isEmpty() && content.length > 1200) {
            findings += ChronologyRepairFinding(
                ChronologyRepairRisk.MEDIUM,
                "NO_EXPLICIT_TIME_ANCHOR",
                1,
                "长章节缺少可追踪时间锚点",
                "正文较长但没有识别到明确相对日、钟点或时间推进词，后续章节很难稳定承接。",
                content.take(160),
                "至少为关键场景补一个故事日/时段锚点，不要求每段都报时间。",
            )
        }

        return ChronologyRepairReport(anchors.distinct(), findings.distinctBy { it.code to it.paragraph })
    }

    private fun chineseNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (raw == "十") return 10
        if ('十' in raw) {
            val parts = raw.split('十')
            val tens = parts.getOrNull(0)?.firstOrNull()?.let { digits[it] } ?: 1
            val ones = parts.getOrNull(1)?.firstOrNull()?.let { digits[it] } ?: 0
            return tens * 10 + ones
        }
        var value = 0
        raw.forEach { ch -> value = value * 10 + (digits[ch] ?: return null) }
        return value.takeIf { it > 0 }
    }
}
