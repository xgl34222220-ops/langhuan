package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelTelemetryTest {
    @Test
    fun `telemetry derives real success speed structured and quality rates`() {
        val stats = ModelTaskTelemetry(
            providerId = "p1",
            modelId = "writer-x",
            task = AiTaskType.PROSE_AUTHOR,
            calls = 4,
            successes = 3,
            failures = 1,
            emptyOutputs = 1,
            totalDurationMs = 4_000,
            firstTokenSamples = 2,
            totalFirstTokenMs = 1_200,
            totalOutputChars = 2_000,
            structuredAttempts = 2,
            structuredSuccesses = 1,
            qualityPasses = 2,
            qualityRejects = 1,
        )

        assertEquals(0.75, stats.successRate, 0.0001)
        assertEquals(2.0 / 3.0, stats.nonEmptyRate, 0.0001)
        assertEquals(1_000L, stats.averageDurationMs)
        assertEquals(600L, stats.averageFirstTokenMs)
        assertEquals(500.0, stats.charsPerSecond, 0.0001)
        assertEquals(0.5, stats.structuredRate, 0.0001)
        assertEquals(2.0 / 3.0, stats.qualityPassRate, 0.0001)
    }

    @Test
    fun `zero samples never fabricate a rate`() {
        val stats = ModelTaskTelemetry("p", "m", AiTaskType.FACT_EXTRACTION)
        assertEquals(0.0, stats.successRate, 0.0)
        assertEquals(0.0, stats.charsPerSecond, 0.0)
        assertEquals(0L, stats.averageFirstTokenMs)
        assertTrue(stats.qualitySamples == 0)
    }
}
