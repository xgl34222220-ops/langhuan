package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisQualityTest {
    @Test
    fun rejectsSettingListsAndShortFragments() {
        assertTrue(SynopsisQuality.needsRewrite("简介：世界观很宏大，融合两本作品的核心设定。"))
    }

    @Test
    fun acceptsCoherentPlatformSynopsis() {
        val synopsis = "刑警周衍在追查搭档失踪案时，发现所有监控都拍到她走进同一场梦。为了把她带回现实，他必须主动进入梦境并破解每层杀人规律。可每接近真相一步，现实就会多出一条从未存在过的规则，而他的记忆也正被某种力量逐段改写。下一次醒来，他可能已经忘记自己要救的是谁。"

        assertFalse(SynopsisQuality.needsRewrite(synopsis))
    }

    @Test
    fun doesNotMechanicallyTruncateLongCoherentSynopsis() {
        val synopsis = "刑警周衍在追查搭档失踪案时，发现所有监控都拍到她走进同一场梦。为了把她带回现实，他必须主动进入梦境并破解每层杀人规律。可每接近真相一步，现实就会多出一条从未存在过的规则，而他的记忆也正被某种力量逐段改写。下一次醒来，他可能已经忘记自己要救的是谁。与此同时，旧案卷宗里不断出现只有他能看见的新证词，每一份证词都指向一个尚未发生的死亡现场。"

        assertEquals(synopsis, SynopsisQuality.normalize(synopsis))
        assertFalse(SynopsisQuality.needsRewrite(synopsis))
    }
}
