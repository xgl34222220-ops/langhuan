package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.canon.CanonEntityObservationV1
import com.xiguli.langhuan.ui.canon.CanonEntityTypeV1
import com.xiguli.langhuan.ui.canon.CanonEventObservationV1
import com.xiguli.langhuan.ui.canon.CanonSourceDigestV1
import com.xiguli.langhuan.ui.canon.OriginalCanonArchiveV1
import com.xiguli.langhuan.ui.story.StoryPlaceV1
import com.xiguli.langhuan.ui.story.StoryPlaySession
import com.xiguli.langhuan.ui.story.StoryPlayTurn
import com.xiguli.langhuan.ui.story.StoryPlayVariable
import com.xiguli.langhuan.ui.story.StoryRouteEdgeV1
import com.xiguli.langhuan.ui.story.StorySpatialSceneV1
import com.xiguli.langhuan.ui.story.evaluateStorySpatialMoveV1
import com.xiguli.langhuan.ui.story.extractStorySpatialTargetV1
import com.xiguli.langhuan.ui.story.findShortestStoryRouteV1
import com.xiguli.langhuan.ui.story.mergeStorySpatialDirectorNoteV1
import com.xiguli.langhuan.ui.story.renderStorySpatialDirectorNoteV1
import com.xiguli.langhuan.ui.story.resolveStoryPlaceV1
import com.xiguli.langhuan.ui.story.seedStorySpatialSceneV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySpatialV1Test {

    @Test
    fun `canon spatial seed never includes future chapter locations`() {
        val canon = OriginalCanonArchiveV1(
            novelId = "n1",
            title = "测试",
            digests = listOf(
                CanonSourceDigestV1(
                    chapterNumber = 3,
                    chapterTitle = "旧医院",
                    partIndex = 1,
                    partCount = 1,
                    fingerprint = "a",
                    summary = "进入旧医院",
                    entities = listOf(
                        CanonEntityObservationV1(3, 1, CanonEntityTypeV1.LOCATION, "旧医院", emptyList(), "城北医院"),
                    ),
                    events = listOf(
                        CanonEventObservationV1(3, 1, location = "旧医院大厅", participants = listOf("顾宁"), summary = "顾宁在大厅等人"),
                    ),
                ),
                CanonSourceDigestV1(
                    chapterNumber = 30,
                    chapterTitle = "未来基地",
                    partIndex = 1,
                    partCount = 1,
                    fingerprint = "b",
                    summary = "发现未来基地",
                    entities = listOf(
                        CanonEntityObservationV1(30, 1, CanonEntityTypeV1.LOCATION, "未来基地", emptyList(), "后期地点"),
                    ),
                ),
            ),
        )
        val session = StoryPlaySession(anchorChapter = 10, anchorTitle = "锚点")

        val scene = seedStorySpatialSceneV1(session, canon, worldLocation = "旧医院", offscreen = null, clockMinute = 0)

        assertNotNull(resolveStoryPlaceV1(scene, "旧医院"))
        assertNotNull(resolveStoryPlaceV1(scene, "旧医院大厅"))
        assertNull(resolveStoryPlaceV1(scene, "未来基地"))
    }

    @Test
    fun `shortest route chooses lower total travel time`() {
        val scene = StorySpatialSceneV1(
            sessionId = "s",
            anchorChapter = 1,
            places = listOf(
                StoryPlaceV1(id = "a", name = "卧室"),
                StoryPlaceV1(id = "b", name = "走廊"),
                StoryPlaceV1(id = "c", name = "楼下"),
            ),
            routes = listOf(
                StoryRouteEdgeV1(id = "ab", fromId = "a", toId = "b", minutes = 2),
                StoryRouteEdgeV1(id = "bc", fromId = "b", toId = "c", minutes = 3),
                StoryRouteEdgeV1(id = "ac", fromId = "a", toId = "c", minutes = 12),
            ),
        )

        val path = findShortestStoryRouteV1(scene, "a", "c")

        assertNotNull(path)
        assertEquals(5, path!!.totalMinutes)
        assertEquals(listOf("a", "b", "c"), path.placeIds)
    }

    @Test
    fun `movement is blocked when elapsed time is shorter than route`() {
        val scene = StorySpatialSceneV1(
            sessionId = "s",
            anchorChapter = 1,
            places = listOf(StoryPlaceV1(id = "a", name = "学校"), StoryPlaceV1(id = "b", name = "医院")),
            routes = listOf(StoryRouteEdgeV1(fromId = "a", toId = "b", minutes = 25)),
        )

        val tooFast = evaluateStorySpatialMoveV1(scene, "a", "b", actualMinutes = 5)
        val enough = evaluateStorySpatialMoveV1(scene, "a", "b", actualMinutes = 25)

        assertFalse(tooFast.accepted)
        assertEquals(25, tooFast.requiredMinutes)
        assertTrue(enough.accepted)
    }

    @Test
    fun `unconnected places cannot be used as teleport destinations`() {
        val scene = StorySpatialSceneV1(
            sessionId = "s",
            anchorChapter = 1,
            places = listOf(StoryPlaceV1(id = "a", name = "A城"), StoryPlaceV1(id = "b", name = "B城")),
        )

        val check = evaluateStorySpatialMoveV1(scene, "a", "b", actualMinutes = 600)

        assertFalse(check.accepted)
        assertTrue(check.reason.contains("没有"))
    }

    @Test
    fun `dm spatial target metadata can be read from turn variables`() {
        val turn = StoryPlayTurn(
            player = "去医院",
            narration = "他离开学校。",
            variablesAfter = listOf(
                StoryPlayVariable(STORY_SPATIAL_SUBJECT_V1, STORY_SPATIAL_TARGET_FIELD_V1, "医院", "准备移动"),
            ),
        )

        assertEquals("医院", extractStorySpatialTargetV1(turn))
    }

    @Test
    fun `spatial director block preserves other director notes without duplication`() {
        val scene = StorySpatialSceneV1(
            sessionId = "s",
            anchorChapter = 5,
            places = listOf(StoryPlaceV1(id = "a", name = "医院"), StoryPlaceV1(id = "b", name = "医院大厅", parentId = "a")),
            routes = listOf(StoryRouteEdgeV1(fromId = "a", toId = "b", minutes = 2)),
            playerPlaceId = "b",
        )
        val original = "作者备注\n\n【原著章节边界证据】\n- 第5章：医院已出现"
        val first = mergeStorySpatialDirectorNoteV1(original, renderStorySpatialDirectorNoteV1(scene))
        val second = mergeStorySpatialDirectorNoteV1(first, renderStorySpatialDirectorNoteV1(scene))

        assertTrue(second.contains("【原著章节边界证据】"))
        assertTrue(second.contains("玩家当前空间位置：医院大厅"))
        assertEquals(1, "【故事空间约束｜导演层】".toRegex().findAll(second).count())
    }
}
