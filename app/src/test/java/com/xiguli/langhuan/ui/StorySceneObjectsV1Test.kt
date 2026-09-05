package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.story.StoryLightLevelV1
import com.xiguli.langhuan.ui.story.StoryPerceptionRouteV1
import com.xiguli.langhuan.ui.story.StoryPerceptionSceneV1
import com.xiguli.langhuan.ui.story.StoryPlaceKindV1
import com.xiguli.langhuan.ui.story.StoryPlaceV1
import com.xiguli.langhuan.ui.story.StoryPlayTurn
import com.xiguli.langhuan.ui.story.StoryPlayVariable
import com.xiguli.langhuan.ui.story.StoryPortalStateV1
import com.xiguli.langhuan.ui.story.StoryRouteEdgeV1
import com.xiguli.langhuan.ui.story.StorySceneObjectActionV1
import com.xiguli.langhuan.ui.story.StorySceneObjectKindV1
import com.xiguli.langhuan.ui.story.StorySceneObjectSceneV1
import com.xiguli.langhuan.ui.story.StorySceneObjectV1
import com.xiguli.langhuan.ui.story.StorySpatialSceneV1
import com.xiguli.langhuan.ui.story.applyStorySceneObjectActionV1
import com.xiguli.langhuan.ui.story.desiredLightForStorySceneObjectV1
import com.xiguli.langhuan.ui.story.extractStorySceneObjectActionsV1
import com.xiguli.langhuan.ui.story.findBlockingStorySceneObjectV1
import com.xiguli.langhuan.ui.story.mergeStorySceneObjectsDirectorNoteV1
import com.xiguli.langhuan.ui.story.normalizeStorySceneObjectsV1
import com.xiguli.langhuan.ui.story.renderStorySceneObjectsDirectorNoteV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySceneObjectsV1Test {

    private fun spatial(): StorySpatialSceneV1 = StorySpatialSceneV1(
        sessionId = "s",
        anchorChapter = 8,
        places = listOf(
            StoryPlaceV1(id = "room", name = "卧室", kind = StoryPlaceKindV1.ROOM),
            StoryPlaceV1(id = "hall", name = "走廊", kind = StoryPlaceKindV1.ROOM),
            StoryPlaceV1(id = "stairs", name = "楼下", kind = StoryPlaceKindV1.AREA),
        ),
        routes = listOf(
            StoryRouteEdgeV1(id = "door-route", fromId = "room", toId = "hall", minutes = 1),
            StoryRouteEdgeV1(id = "hall-stairs", fromId = "hall", toId = "stairs", minutes = 2),
        ),
        playerPlaceId = "room",
    )

    @Test
    fun `room boundary route seeds a real door object`() {
        val p = StoryPerceptionSceneV1(
            sessionId = "s",
            routeStates = listOf(StoryPerceptionRouteV1("door-route", portalState = StoryPortalStateV1.CLOSED)),
        )
        val scene = normalizeStorySceneObjectsV1(StorySceneObjectSceneV1("s"), spatial(), p)

        val door = scene.objects.firstOrNull { it.linkedRouteId == "door-route" }
        assertNotNull(door)
        assertEquals(StorySceneObjectKindV1.DOOR, door!!.kind)
        assertFalse(door.open)
    }

    @Test
    fun `locked door cannot be opened until it is unlocked`() {
        val door = StorySceneObjectV1(name = "门", kind = StorySceneObjectKindV1.DOOR, locked = true)

        val denied = applyStorySceneObjectActionV1(door, StorySceneObjectActionV1.OPEN)
        val unlocked = applyStorySceneObjectActionV1(door, StorySceneObjectActionV1.UNLOCK)
        val opened = applyStorySceneObjectActionV1(unlocked.objectAfter, StorySceneObjectActionV1.OPEN)

        assertFalse(denied.accepted)
        assertTrue(denied.reason.contains("解锁"))
        assertTrue(unlocked.accepted)
        assertTrue(opened.accepted)
        assertTrue(opened.objectAfter.open)
    }

    @Test
    fun `closed door blocks a route while opened door releases it`() {
        val s = spatial()
        val closedDoor = StorySceneObjectV1(
            id = "d",
            name = "卧室门",
            kind = StorySceneObjectKindV1.DOOR,
            linkedRouteId = "door-route",
            open = false,
        )
        val closedScene = StorySceneObjectSceneV1("s", objects = listOf(closedDoor))
        val openScene = closedScene.copy(objects = listOf(closedDoor.copy(open = true)))

        assertNotNull(findBlockingStorySceneObjectV1(closedScene, s, "room", "stairs"))
        assertEquals(null, findBlockingStorySceneObjectV1(openScene, s, "room", "stairs"))
    }

    @Test
    fun `light power state immediately maps to scene light`() {
        val light = StorySceneObjectV1(name = "顶灯", kind = StorySceneObjectKindV1.LIGHT, powered = true)
        assertEquals(StoryLightLevelV1.BRIGHT, desiredLightForStorySceneObjectV1(light))
        assertEquals(StoryLightLevelV1.DARK, desiredLightForStorySceneObjectV1(light.copy(powered = false)))
        assertEquals(StoryLightLevelV1.DARK, desiredLightForStorySceneObjectV1(light.copy(broken = true)))
    }

    @Test
    fun `dm object action metadata is parsed from state changes`() {
        val turn = StoryPlayTurn(
            player = "我把卧室门锁上",
            narration = "门锁咔哒一声扣住。",
            variablesAfter = listOf(
                StoryPlayVariable(STORY_SCENE_OBJECT_SUBJECT_V1, "卧室门", "LOCK", "玩家亲手上锁"),
            ),
        )

        assertEquals(listOf("卧室门" to StorySceneObjectActionV1.LOCK), extractStorySceneObjectActionsV1(turn))
    }

    @Test
    fun `director note tells dm that object changes are hard world state`() {
        val s = spatial()
        val scene = StorySceneObjectSceneV1(
            "s",
            objects = listOf(
                StorySceneObjectV1(name = "卧室门", kind = StorySceneObjectKindV1.DOOR, placeId = "room", linkedRouteId = "door-route", locked = true),
            ),
        )
        val note = renderStorySceneObjectsDirectorNoteV1(scene, s)
        val once = mergeStorySceneObjectsDirectorNoteV1("作者备注", note)
        val twice = mergeStorySceneObjectsDirectorNoteV1(once, note)

        assertTrue(twice.contains("卧室门"))
        assertTrue(twice.contains("STORY_OBJECT"))
        assertTrue(twice.contains("必须先 UNLOCK"))
        assertEquals(1, "【场景物件状态｜导演层】".toRegex().findAll(twice).count())
    }
}
