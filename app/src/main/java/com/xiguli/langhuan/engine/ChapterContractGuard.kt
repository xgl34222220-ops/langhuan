package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterContract
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.StorySnapshot

/**
 * 把“章纲”升级成可执行的章节合同，并统一处理信息边界。
 *
 * 旧章节没有 contract 时不会失效：这里会从当前章纲、人物状态和知识账本推导一个
 * conservative effective contract，避免要求用户重建旧项目。
 */
object ChapterContractGuard {
    fun resolve(request: GenerationRequest): ChapterContract = resolve(request.snapshot, request.chapter)

    fun resolve(snapshot: StorySnapshot, chapter: ChapterDraft): ChapterContract {
        val outline = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        val raw = chapter.contract
        val chapterNumber = chapter.chapterNumber.coerceAtLeast(1)
        val hidden = snapshot.knowledgeLedger
            .filter { it.mustRemainProtected(chapterNumber) }
            .map { it.title }
        val allowedReveals = snapshot.knowledgeLedger
            .filter { it.revealPolicy in setOf(KnowledgeRevealPolicy.PARTIAL, KnowledgeRevealPolicy.FULL) }
            .filter { it.earliestFullRevealChapter <= 0 || chapterNumber >= it.earliestFullRevealChapter }
            .map { it.title }

        val stateIn = if (raw.characterStateIn.isNotEmpty()) raw.characterStateIn else {
            snapshot.characters.associate { character ->
                character.name to buildString {
                    append("地点=${character.location}；身体=${character.physicalState}；情绪=${character.emotionalState}；目标=${character.goal}")
                    if (character.knownSecrets.isNotEmpty()) {
                        append("；已知=${character.knownSecrets.joinToString("、")}")
                    }
                }
            }
        }

        return raw.copy(
            purpose = raw.purpose.ifBlank { chapter.objective.ifBlank { outline?.objective.orEmpty() } },
            mustHappen = (raw.mustHappen + outline?.mustInclude.orEmpty()).cleanDistinct(),
            mustNotHappen = (raw.mustNotHappen + outline?.forbidden.orEmpty()).cleanDistinct(),
            characterStateIn = stateIn,
            reveals = (raw.reveals + allowedReveals).cleanDistinct(),
            secretsPreserved = (raw.secretsPreserved + hidden).cleanDistinct(),
            foreshadowing = raw.foreshadowing.cleanDistinct(),
            hookOut = raw.hookOut.ifBlank { outline?.turningPoint.orEmpty() },
            continuityRisks = (raw.continuityRisks + defaultRisks(snapshot)).cleanDistinct(),
        )
    }

    fun renderContract(snapshot: StorySnapshot, chapter: ChapterDraft): String =
        renderContract(resolve(snapshot, chapter))

    fun renderContract(contract: ChapterContract): String = buildString {
        appendLine("本章目的：${contract.purpose.ifBlank { "只完成当前章纲目标" }}")
        appendLine("必须发生：${contract.mustHappen.ifEmpty { listOf("围绕本章目的产生不可逆推进") }.joinToString("；")}")
        appendLine("绝不能发生：${contract.mustNotHappen.ifEmpty { listOf("不得提前兑现后续章节内容") }.joinToString("；")}")
        if (contract.characterStateIn.isNotEmpty()) {
            appendLine("人物入场状态：")
            contract.characterStateIn.forEach { (name, value) -> appendLine("- $name：$value") }
        }
        if (contract.characterStateOut.isNotEmpty()) {
            appendLine("人物离场状态：")
            contract.characterStateOut.forEach { (name, value) -> appendLine("- $name：$value") }
        }
        appendLine("本章允许揭露：${contract.reveals.ifEmpty { listOf("只揭露章纲明确要求的信息") }.joinToString("；")}")
        appendLine("必须继续保密：${contract.secretsPreserved.ifEmpty { listOf("所有未获授权的长期谜底") }.joinToString("；")}")
        if (contract.foreshadowing.isNotEmpty()) appendLine("伏笔动作：${contract.foreshadowing.joinToString("；")}")
        appendLine("章末钩子：${contract.hookOut.ifBlank { "由本章已有因果自然推出新的问题、代价或选择" }}")
        appendLine("连续性风险：${contract.continuityRisks.joinToString("；").ifBlank { "不得越过人物知识、时间、地点和设定边界" }}")
    }.trim()

    fun renderKnowledge(snapshot: StorySnapshot, chapterNumber: Int): String {
        if (snapshot.knowledgeLedger.isEmpty()) {
            val known = snapshot.characters
                .filter { it.knownSecrets.isNotEmpty() }
                .joinToString("\n") { "- ${it.name} 已知：${it.knownSecrets.joinToString("、")}" }
            return known.ifBlank { "- 暂无显式信息边界条目；仍以人物 knownSecrets 为上限，不得擅自补全未知答案。" }
        }
        return snapshot.knowledgeLedger
            .sortedWith(compareBy<KnowledgeBoundary> { it.revealPolicy.ordinal }.thenBy { it.earliestFullRevealChapter })
            .take(48)
            .joinToString("\n") { item ->
                val earliest = item.earliestFullRevealChapter.takeIf { it > 0 }?.let { "第${it}章后" } ?: "未指定"
                "- [${item.id}] ${item.title}｜已知者=${item.knownBy.ifEmpty { listOf("未登记") }.joinToString("、")}｜明确未知者=${item.unknownTo.ifEmpty { listOf("未登记") }.joinToString("、")}｜读者=${item.readerState}｜本章策略=${item.revealPolicy}｜最早完整揭露=$earliest${item.note.takeIf(String::isNotBlank)?.let { "｜$it" }.orEmpty()}"
            }
    }

    fun inspect(request: GenerationRequest, output: GeneratedChapter): List<ConsistencyIssue> {
        val issues = mutableListOf<ConsistencyIssue>()
        val contract = resolve(request)
        val text = output.content
        val chapterNumber = request.chapter.chapterNumber.coerceAtLeast(1)

        contract.mustNotHappen.forEach { forbidden ->
            val token = forbiddenToken(forbidden)
            if (token.length >= 2 && text.contains(token, ignoreCase = true)) {
                issues += blocking(
                    code = "CHAPTER_CONTRACT_FORBIDDEN",
                    message = "正文触发章节合同的禁止项：$forbidden",
                    evidence = token,
                    repair = "删除或改写该内容；本章合同优先于文风、RAG 与临时发挥。",
                )
            }
        }

        contract.mustHappen.forEach { required ->
            val token = requiredToken(required)
            if (token.length >= 2 && !text.contains(token, ignoreCase = true)) {
                issues += ConsistencyIssue(
                    severity = IssueSeverity.WARNING,
                    code = "CHAPTER_CONTRACT_MISSING",
                    message = "章节合同要求的关键内容可能没有落实：$required",
                    repairInstruction = "复核章纲语义；若确实缺失，在不提前抢戏的前提下补足。",
                )
            }
        }

        request.snapshot.knowledgeLedger
            .filter { it.mustRemainProtected(chapterNumber) }
            .forEach { boundary ->
                val leakedTerm = boundary.fullRevealTerms().firstOrNull { term ->
                    term.length >= 2 && text.contains(term, ignoreCase = true)
                }
                if (leakedTerm != null) {
                    issues += blocking(
                        code = "KNOWLEDGE_REVEAL_LEAK",
                        message = "本章提前泄露了仍受保护的信息：${boundary.title}",
                        evidence = leakedTerm,
                        repair = if (boundary.revealPolicy == KnowledgeRevealPolicy.HINT_ONLY) {
                            "只能保留模糊、可多解的暗示，删除能直接确认答案的表达。"
                        } else {
                            "删除完整答案，只保留当前章节获准出现的信息。"
                        },
                    )
                }
            }

        output.stateChanges
            .filter { it.field.equals("knownSecrets", true) || it.field.equals("knowledge", true) || it.field == "已知秘密" }
            .forEach { change ->
                request.snapshot.knowledgeLedger
                    .filter { boundary -> boundary.unknownTo.any { it.equals(change.subject, ignoreCase = true) } }
                    .filter { it.mustRemainProtected(chapterNumber) }
                    .filter { boundary -> boundary.matches(change.after) }
                    .forEach { boundary ->
                        issues += blocking(
                            code = "CHARACTER_KNOWLEDGE_OVERREACH",
                            message = "${change.subject}获得了其当前明确不应知道的信息：${boundary.title}",
                            evidence = change.evidence.ifBlank { change.after },
                            repair = "重写获取信息的段落，或先在章节合同/信息边界中明确授权该人物获知。",
                        )
                    }
            }

        return issues.distinctBy { listOf(it.code, it.message, it.evidence) }
    }

    private fun defaultRisks(snapshot: StorySnapshot): List<String> = buildList {
        add("人物不得知道其 knownSecrets / 信息边界之外的答案")
        add("不得把总纲或后续章纲的转折提前兑现")
        if (snapshot.relevantForeshadowing.isNotEmpty()) add("未到回收窗口的伏笔不得直接揭底")
        if (snapshot.recentTimeline.isNotEmpty()) add("地点移动、等待与跳时必须服从主时间钟")
    }

    private fun KnowledgeBoundary.mustRemainProtected(chapterNumber: Int): Boolean =
        revealPolicy in setOf(KnowledgeRevealPolicy.HIDDEN, KnowledgeRevealPolicy.HINT_ONLY) ||
            (earliestFullRevealChapter > 0 && chapterNumber < earliestFullRevealChapter)

    private fun KnowledgeBoundary.fullRevealTerms(): List<String> =
        (triggerTerms + truth.takeIf { it.length in 4..80 }.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()

    private fun KnowledgeBoundary.matches(value: String): Boolean {
        if (value.isBlank()) return false
        return value.contains(title, ignoreCase = true) ||
            fullRevealTerms().any { value.contains(it, ignoreCase = true) || it.contains(value, ignoreCase = true) }
    }

    private fun forbiddenToken(value: String): String = value.trim()
        .replace(Regex("^(绝对)?(禁止|不得|不能|不可|不准|不要|不应|避免)(出现|发生|解释|揭露|透露|提及|让)?[:：\\s]*"), "")
        .trim('。', '；', ';', '，', ',')

    private fun requiredToken(value: String): String = value.trim()
        .replace(Regex("^(必须|需要|应当|要)(出现|发生|完成|体现)?[:：\\s]*"), "")
        .trim('。', '；', ';', '，', ',')

    private fun List<String>.cleanDistinct(): List<String> = map(String::trim).filter(String::isNotBlank).distinct()

    private fun blocking(code: String, message: String, evidence: String, repair: String) =
        ConsistencyIssue(IssueSeverity.BLOCKING, code, message, evidence, repair)
}
