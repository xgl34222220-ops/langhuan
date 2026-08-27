package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent

class DemoStoryRepository {
    val snapshot = StorySnapshot(
        novel = Novel(
            id = "novel-1",
            title = "雾港残卷",
            genre = "悬疑 / 奇幻",
            premise = "能看见被篡改记忆的调查员，追查一座港城集体遗忘的真相。",
            theme = "真相是否值得以幸福为代价",
            targetWords = 800_000,
            currentWords = 36_820,
            currentChapter = 13,
            status = NovelStatus.WRITING,
        ),
        activeOutline = listOf(
            OutlineNode(
                id = "master-1", novelId = "novel-1", level = OutlineLevel.MASTER, order = 1,
                title = "被抹去的潮汐", objective = "揭开港城以记忆换取平静的循环",
                conflict = "个人幸福与公共真相冲突", turningPoint = "主角发现自己曾参与第一次抹除",
            ),
            OutlineNode(
                id = "volume-1", novelId = "novel-1", parentId = "master-1", level = OutlineLevel.VOLUME,
                order = 1, title = "失踪的第七码头", objective = "证明失踪案与记忆税有关",
                conflict = "调查组遭到城务厅阻挠", turningPoint = "商队其实从未离港",
            ),
            OutlineNode(
                id = "chapter-13", novelId = "novel-1", parentId = "volume-1", level = OutlineLevel.CHAPTER,
                order = 13, title = "雾港来信", objective = "确认城门记录遭到篡改",
                conflict = "匿名寄信人试探调查组", turningPoint = "线索指向废弃钟楼",
                mustInclude = listOf("银色盐晶", "钟楼"),
            ),
        ),
        bible = listOf(
            BibleEntry("b1", "novel-1", BibleCategory.RULE, "记忆显影", "沈砚只能看见七日内被人为修改的记忆痕迹。"),
            BibleEntry("b2", "novel-1", BibleCategory.CHARACTER, "沈砚", "克制、多疑；不会在没有证据时公开指控。"),
            BibleEntry("b3", "novel-1", BibleCategory.FORBIDDEN, "无代价读心", "任何角色都不能直接读取他人思想。", aliases = listOf("读心", "读取思想")),
            BibleEntry("b4", "novel-1", BibleCategory.STYLE, "叙事风格", "第三人称限知，克制冷峻，信息随行动逐步揭示。"),
        ),
        characters = listOf(
            CharacterState(
                id = "c1", novelId = "novel-1", name = "沈砚", personality = listOf("克制", "多疑", "执着"),
                location = "旧城区公寓", physicalState = "左肩旧伤", emotionalState = "警惕",
                goal = "确认商队失踪是否涉及记忆篡改", knownSecrets = listOf("记忆税并非自愿"),
                possessions = listOf("银壳怀表", "旧案卷"), lastUpdatedChapter = 12,
            ),
            CharacterState(
                id = "c2", novelId = "novel-1", name = "顾遥", personality = listOf("理性", "直接", "守序"),
                location = "旧城区公寓", physicalState = "正常", emotionalState = "怀疑沈砚隐瞒线索",
                goal = "从官方档案证明记录被修改", lastUpdatedChapter = 12,
            ),
        ),
        recentTimeline = listOf(
            TimelineEvent("t11", "novel-1", 11, "霜月十六日傍晚", "第七码头", listOf("沈砚"), "找到没有装卸痕迹的空货箱。"),
            TimelineEvent("t12", "novel-1", 12, "霜月十六日深夜", "旧城区公寓", listOf("沈砚", "顾遥"), "收到带有银色盐晶的匿名信。"),
        ),
        relevantForeshadowing = listOf(
            Foreshadowing("f1", "novel-1", "停摆的钟楼", 3, "钟楼每天会少敲一次。", "揭示被抹除的一天", 28, 34, ForeshadowStatus.DEVELOPING),
        ),
        recentSummaries = listOf(
            "第11章：沈砚发现商队货箱从未真正装卸。",
            "第12章：匿名信把调查方向引向被修改的城门记录。",
        ),
    )

    val currentDraft = ChapterDraft(
        id = "draft-13",
        novelId = "novel-1",
        chapterNumber = 13,
        title = "雾港来信",
        objective = "确认城门记录遭到篡改，并把线索推进到废弃钟楼。",
        scenePlan = listOf(
            ScenePlan(1, "沈砚", "旧城区公寓", "验证匿名信", "是否相信寄信人", "决定先查档案"),
            ScenePlan(2, "沈砚", "城务档案馆", "核对城门记录", "记录被二次加密", "确认日期被改"),
            ScenePlan(3, "沈砚", "废弃钟楼", "追踪监视者", "对方故意留下假线索", "意识到这是一场测试"),
        ),
    )
}
