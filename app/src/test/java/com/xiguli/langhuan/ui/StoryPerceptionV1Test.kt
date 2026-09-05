package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.story.StoryActorLocationV1
import com.xiguli.langhuan.ui.story.StoryAudibilityV1
import com.xiguli.langhuan.ui.story.StoryLightLevelV1
import com.xiguli.langhuan.ui.story.StoryObscurityV1
import com.xiguli.langhuan.ui.story.StoryPerceptionPlaceV1
import com.xiguli.langhuan.ui.story.StoryPerceptionRouteV1
import com.xiguli.langhuan.ui.story.StoryPerceptionSceneV1
import com.xiguli.langhuan.ui.story.StoryPlaceKindV1
import com.xiguli.langhuan.ui.story.StoryPlaceV1
import com.xiguli.langhuan.ui.story.StoryPortalStateV1
import com.xiguli.langhuan.ui.story.StoryRouteEdgeV1
import com.xiguli.langhuan.ui.story.StorySpatialSceneV1
import com.xiguli.langhuan.ui.story.StoryTravelModeV1
import com.xiguli.langhuan.ui.story.currentPlayerPerceptionsV1
import com.xiguli.langhuan.ui.story.mergeStoryPerceptionDirectorNoteV1
import com.xiguli.langhuan.ui.story.normalizePerceptionSceneV1
import com.xiguli.langhuan.ui.story.renderStoryPerceptionDirectorNoteV1
import com.xiguli.langhuan.ui.story.storySenseBetweenPlacesV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPerceptionV1Test {

    @Test
    fun darkSameRoomBlocksSightButNotOrdinarySound() {
        val room = place("room", "卧室", StoryPlaceKindV1.ROOM)
        val spatial = spatial(listOf(room), emptyList(), room.id)
        val perception = StoryPerceptionSceneV1(
            sessionId = "s",
            placeStates = listOf(StoryPerceptionPlaceV1(room.id, StoryLightLevelV1.DARK, StoryObscurityV1.CLEAR, 10)),
        )

        val result = storySenseBetweenPlacesV1(spatial, perception, room.id, room.id)
        assertFalse(result.canSee)
        assertEquals(StoryAudibilityV1.CLEAR, result.audibility)
    }

    @Test
    fun closedDoorBlocksSightAndMufflesConversation() {
        val a = place("a", "卧室", StoryPlaceKindV1.ROOM)
        val b = place("b", "走廊", StoryPlaceKindV1.ROOM)
        val route = StoryRouteEdgeV1(id = "r", fromId = a.id, toId = b.id, minutes = 1)
        val spatial = spatial(listOf(a, b), listOf(route), a.id)
        val perception = StoryPerceptionSceneV1(
            sessionId = "s",
            placeStates = listOf(
                StoryPerceptionPlaceV1(a.id, ambientNoise = 10),
                StoryPerceptionPlaceV1(b.id, ambientNoise = 10),
            ),
            routeStates = listOf(
                StoryPerceptionRouteV1(
                    routeId = route.id,
                    portalState = StoryPortalStateV1.CLOSED,
                    visionPassWhenOpen = true,
                    soundLossOpen = 10,
                    soundLossClosed = 55,
                ),
            ),
        )

        val result = storySenseBetweenPlacesV1(spatial, perception, a.id, b.id)
        assertFalse(result.canSee)
        assertEquals(StoryAudibilityV1.MUFFLED, result.audibility)
    }

    @Test
    fun sealedDoorBlocksSoundCompletely() {
        val a = place("a", "卧室", StoryPlaceKindV1.ROOM)
        val b = place("b", "密室", StoryPlaceKindV1.ROOM)
        val route = StoryRouteEdgeV1(id = "r", fromId = a.id, toId = b.id, minutes = 1)
        val spatial = spatial(listOf(a, b), listOf(route), a.id)
        val perception = StoryPerceptionSceneV1(
            sessionId = "s",
            routeStates = listOf(StoryPerceptionRouteV1(route.id, portalState = StoryPortalStateV1.SEALED)),
        )

        val result = storySenseBetweenPlacesV1(spatial, perception, a.id, b.id)
        assertFalse(result.canSee)
        assertEquals(StoryAudibilityV1.NONE, result.audibility)
    }

    @Test
    fun openShortPassageAllowsVision() {
        val a = place("a", "客厅", StoryPlaceKindV1.ROOM)
        val b = place("b", "开放式厨房", StoryPlaceKindV1.ROOM)
        val route = StoryRouteEdgeV1(id = "r", fromId = a.id, toId = b.id, minutes = 1)
        val spatial = spatial(listOf(a, b), listOf(route), a.id)
        val perception = StoryPerceptionSceneV1(
            sessionId = "s",
            placeStates = listOf(StoryPerceptionPlaceV1(a.id), StoryPerceptionPlaceV1(b.id)),
            routeStates = listOf(StoryPerceptionRouteV1(route.id, StoryPortalStateV1.OPEN, visionPassWhenOpen = true, soundLossOpen = 10)),
        )

        val result = storySenseBetweenPlacesV1(spatial, perception, a.id, b.id)
        assertTrue(result.canSee)
        assertEquals(StoryAudibilityV1.CLEAR, result.audibility)
    }

    @Test
    fun longDistanceRouteDoesNotCarryOrdinarySoundByDefault() {
        val a = place("a", "A城", StoryPlaceKindV1.CITY)
        val b = place("b", "B城", StoryPlaceKindV1.CITY)
        val route = StoryRouteEdgeV1(id = "r", fromId = a.id, toId = b.id, minutes = 120, mode = StoryTravelModeV1.TRANSIT)
        val spatial = spatial(listOf(a, b), listOf(route), a.id)
        val perception = normalizePerceptionSceneV1(StoryPerceptionSceneV1("s"), spatial, "")

        val result = storySenseBetweenPlacesV1(spatial, perception, a.id, b.id)
        assertFalse(result.canSee)
        assertEquals(StoryAudibilityV1.NONE, result.audibility)
    }

    @Test
    fun atmosphereCanAutomaticallyTurnCurrentSceneDarkAndObscured() {
        val room = place("room", "仓库", StoryPlaceKindV1.BUILDING)
        val spatial = spatial(listOf(room), emptyList(), room.id)
        val normalized = normalizePerceptionSceneV1(
            StoryPerceptionSceneV1("s"),
            spatial,
            "仓库里漆黑一片，浓烟弥漫，几乎看不清",
        )
        val env = normalized.placeStates.single()
        assertEquals(StoryLightLevelV1.DARK, env.light)
        assertEquals(StoryObscurityV1.HEAVY, env.obscurity)
    }

    @Test
    fun directorNoteDoesNotDuplicateWhenMergedRepeatedly() {
        val room = place("room", "客厅", StoryPlaceKindV1.ROOM)
        val spatial = spatial(listOf(room), emptyList(), room.id)
        val perception = normalizePerceptionSceneV1(StoryPerceptionSceneV1("s"), spatial, "")
        val block = renderStoryPerceptionDirectorNoteV1(spatial, perception, "玩家")
        val once = mergeStoryPerceptionDirectorNoteV1("原有作者备注", block)
        val twice = mergeStoryPerceptionDirectorNoteV1(once, block)

        assertEquals(1, Regex("【场景感知约束｜导演层】").findAll(twice).count())
        assertTrue(twice.contains("原有作者备注"))
    }

    @Test
    fun currentPlayerPerceptionRespectsActorLocations() {
        val room = place("room", "客厅", StoryPlaceKindV1.ROOM)
        val hall = place("hall", "走廊", StoryPlaceKindV1.ROOM)
        val route = StoryRouteEdgeV1(id = "r", fromId = room.id, toId = hall.id, minutes = 1)
        val spatial = spatial(listOf(room, hall), listOf(route), room.id).copy(
            actorLocations = listOf(
                StoryActorLocationV1("@PLAYER", room.id, "客厅"),
                StoryActorLocationV1("顾宁", hall.id, "走廊"),
            ),
        )
        val perception = StoryPerceptionSceneV1(
            sessionId = "s",
            placeStates = listOf(StoryPerceptionPlaceV1(room.id), StoryPerceptionPlaceV1(hall.id)),
            routeStates = listOf(StoryPerceptionRouteV1(route.id, StoryPortalStateV1.CLOSED, false, 18, 55)),
        )

        val result = currentPlayerPerceptionsV1(spatial, perception).single()
        assertEquals("顾宁", result.first.actor)
        assertFalse(result.second.canSee)
        assertEquals(StoryAudibilityV1.MUFFLED, result.second.audibility)
    }

    private fun place(id: String, name: String, kind: StoryPlaceKindV1) = StoryPlaceV1(id = id, name = name, kind = kind)

    private fun spatial(places: List<StoryPlaceV1>, routes: List<StoryRouteEdgeV1>, playerPlaceId: String) = StorySpatialSceneV1(
        sessionId = "s",
        anchorChapter = 10,
        places = places,
        routes = routes,
        playerPlaceId = playerPlaceId,
    )
}
