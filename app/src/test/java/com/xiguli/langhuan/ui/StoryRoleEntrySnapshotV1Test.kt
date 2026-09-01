package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRoleEntrySnapshotV1Test {

    @Test
    fun `entry snapshot uses latest visible role event and blocks future chapter`() {
        val canon = OriginalCanonArchiveV1(
            novelId = "n1",
            title = "测试",
            digests = listOf(
                digest(
                    chapter = 3,
                    title = "旧医院",
                    entities = listOf(
                        CanonEntityObservationV1(3, 1, CanonEntityTypeV1.CHARACTER, "沈砚", listOf("沈医生"), "医生"),
                    ),
                    events = listOf(
                        CanonEventObservationV1(
                            chapterNumber = 3,
                            partIndex = 1,
                            storyTime = "23:10",
                            location = "旧医院大厅",
                            participants = listOf("沈医生", "顾宁"),
                            summary = "沈砚和顾宁刚进入旧医院大厅。",
                            consequences = listOf("两人准备调查值班室"),
                            evidence = "沈砚抬头看向大厅尽头。",
                        ),
                    ),
                    knowledge = listOf(
                        CanonKnowledgeObservationV1(3, 1, "沈医生", "沈砚计划调查值班室", "他低声说先去值班室"),
                    ),
                ),
                digest(
                    chapter = 8,
                    title = "未来",
                    events = listOf(
                        CanonEventObservationV1(
                            chapterNumber = 8,
                            partIndex = 1,
                            storyTime = "第二天",
                            location = "未来基地",
                            participants = listOf("沈砚"),
                            summary = "沈砚发现真正的幕后人。",
                        ),
                    ),
                    knowledge = listOf(
                        CanonKnowledgeObservationV1(8, 1, "沈砚", "幕后人是周某", "未来证据"),
                    ),
                ),
            ),
        )
        val session = StoryPlaySession(
            id = "s1",
            anchorChapter = 5,
            anchorTitle = "第五章",
            playerProfile = StoryPlayerProfile(name = "沈砚", identity = "原著角色 · 截至第5章"),
        )

        val snapshot = buildStoryRoleEntrySnapshotV1(canon, session)!!

        assertEquals("旧医院大厅", snapshot.location)
        assertEquals("23:10", snapshot.storyTime)
        assertEquals(listOf("顾宁"), snapshot.companions)
        assertTrue(snapshot.knownFacts.any { it.contains("调查值班室") })
        assertFalse(snapshot.knownFacts.any { it.contains("幕后人") })
        assertFalse(snapshot.recentEvents.any { it.location == "未来基地" })
        assertEquals(3, snapshot.sourceChapter)
    }

    @Test
    fun `item is only treated as carried when canon explicitly says role possesses it`() {
        val canon = OriginalCanonArchiveV1(
            novelId = "n2",
            title = "物品测试",
            digests = listOf(
                digest(
                    chapter = 2,
                    title = "房间",
                    entities = listOf(
                        CanonEntityObservationV1(2, 1, CanonEntityTypeV1.CHARACTER, "顾宁", emptyList(), "记者"),
                        CanonEntityObservationV1(2, 1, CanonEntityTypeV1.ITEM, "铜钥匙", emptyList(), "顾宁随身携带的旧铜钥匙", "顾宁把钥匙攥在手里"),
                        CanonEntityObservationV1(2, 1, CanonEntityTypeV1.ITEM, "手术刀", emptyList(), "桌上的手术刀", "手术刀放在桌面"),
                    ),
                    events = listOf(
                        CanonEventObservationV1(2, 1, location = "诊室", participants = listOf("顾宁"), summary = "顾宁进入诊室"),
                    ),
                ),
            ),
        )
        val session = StoryPlaySession(
            id = "s2",
            anchorChapter = 2,
            playerProfile = StoryPlayerProfile(name = "顾宁", identity = "原著角色 · 截至第2章"),
        )

        val snapshot = buildStoryRoleEntrySnapshotV1(canon, session)!!

        assertTrue(snapshot.carriedItems.any { it.contains("铜钥匙") })
        assertFalse(snapshot.carriedItems.any { it.contains("手术刀") })
    }

    @Test
    fun `condition and goal require explicit visible evidence`() {
        val canon = OriginalCanonArchiveV1(
            novelId = "n3",
            title = "状态测试",
            digests = listOf(
                digest(
                    chapter = 4,
                    title = "追逐",
                    entities = listOf(CanonEntityObservationV1(4, 1, CanonEntityTypeV1.CHARACTER, "程野", emptyList(), "刑警")),
                    events = listOf(
                        CanonEventObservationV1(4, 1, location = "巷口", participants = listOf("程野"), summary = "程野左臂受伤流血，但仍保持清醒。"),
                    ),
                    knowledge = listOf(
                        CanonKnowledgeObservationV1(4, 1, "程野", "程野决定继续追查失踪案", "他告诉同事自己必须查下去"),
                    ),
                ),
            ),
        )
        val session = StoryPlaySession(
            id = "s3",
            anchorChapter = 4,
            playerProfile = StoryPlayerProfile(name = "程野", identity = "原著角色 · 截至第4章"),
        )

        val snapshot = buildStoryRoleEntrySnapshotV1(canon, session)!!

        assertTrue(snapshot.conditionSignals.any { it.contains("受伤") || it.contains("流血") })
        assertTrue(snapshot.declaredGoal.contains("追查失踪案"))
    }

    @Test
    fun `auto apply only happens before first branch turn and once per source`() {
        val snapshot = StoryRoleEntrySnapshotV1(
            sessionId = "s4",
            roleName = "A",
            roleIdentity = "原著角色 · 截至第6章",
            anchorChapter = 6,
            sourceChapter = 6,
            sourceKey = "k1",
        )
        val fresh = StoryPlaySession(
            id = "s4",
            anchorChapter = 6,
            playerProfile = StoryPlayerProfile(name = "A", identity = "原著角色 · 截至第6章"),
        )
        val played = fresh.copy(turns = listOf(StoryPlayTurn(player = "向前走", narration = "走了一步")))

        assertTrue(shouldAutoApplyStoryRoleEntrySnapshotV1(snapshot, fresh))
        assertFalse(shouldAutoApplyStoryRoleEntrySnapshotV1(snapshot.copy(appliedKey = "k1"), fresh))
        assertFalse(shouldAutoApplyStoryRoleEntrySnapshotV1(snapshot, played))
        assertFalse(shouldAutoApplyStoryRoleEntrySnapshotV1(snapshot.copy(autoApply = false), fresh))
    }

    @Test
    fun `auto world apply preserves manual fields while force apply restores snapshot`() {
        val snapshot = StoryRoleEntrySnapshotV1(
            sessionId = "s5",
            roleName = "A",
            roleIdentity = "原著角色 · 截至第5章",
            anchorChapter = 5,
            sourceChapter = 5,
            sourceKey = "k",
            location = "医院",
            storyTime = "午夜",
            recentEvents = listOf(StoryRoleEntryEventV1(5, 1, summary = "刚刚醒来")),
        )
        val session = StoryPlaySession(id = "s5", anchorChapter = 5, anchorTitle = "醒来")
        val manualWorld = StoryWorldStateV3(location = "作者指定地点", time = "作者指定时间", situation = "作者指定局势")

        val auto = applyStoryRoleEntrySnapshotToWorldV1(snapshot, session, manualWorld, force = false)
        val forced = applyStoryRoleEntrySnapshotToWorldV1(snapshot, session, manualWorld, force = true)

        assertEquals("作者指定地点", auto.location)
        assertEquals("作者指定时间", auto.time)
        assertEquals("作者指定局势", auto.situation)
        assertEquals("医院", forced.location)
        assertEquals("午夜", forced.time)
        assertEquals("刚刚醒来", forced.situation)
    }

    @Test
    fun `director note calls snapshot a starting baseline and preserves future boundary`() {
        val snapshot = StoryRoleEntrySnapshotV1(
            sessionId = "s6",
            roleName = "沈砚",
            roleIdentity = "原著角色 · 截至第9章",
            anchorChapter = 9,
            sourceChapter = 9,
            sourceKey = "k",
            location = "医院",
            knownFacts = listOf("第9章：知道停电"),
        )
        val original = "作者备注\n\n【故事空间约束｜导演层】\n空间状态\n【/故事空间约束】"
        val once = mergeStoryRoleEntrySnapshotDirectorNoteV1(original, renderStoryRoleEntrySnapshotDirectorNoteV1(snapshot))
        val twice = mergeStoryRoleEntrySnapshotDirectorNoteV1(once, renderStoryRoleEntrySnapshotDirectorNoteV1(snapshot))

        assertTrue(twice.contains("分支起点基线"))
        assertTrue(twice.contains("第 10 章及之后"))
        assertTrue(twice.contains("【故事空间约束｜导演层】"))
        assertEquals(1, "【原著角色入场快照｜导演层】".toRegex().findAll(twice).count())
    }

    private fun digest(
        chapter: Int,
        title: String,
        entities: List<CanonEntityObservationV1> = emptyList(),
        events: List<CanonEventObservationV1> = emptyList(),
        knowledge: List<CanonKnowledgeObservationV1> = emptyList(),
        relations: List<CanonRelationObservationV1> = emptyList(),
    ) = CanonSourceDigestV1(
        chapterNumber = chapter,
        chapterTitle = title,
        partIndex = 1,
        partCount = 1,
        fingerprint = "f$chapter",
        summary = title,
        entities = entities,
        events = events,
        knowledge = knowledge,
        relations = relations,
    )
}
