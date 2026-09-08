package com.xiguli.langhuan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderComfortDefaultsV26Test {
    @Test fun migratesBothPreviouslyShippedDefaults() {
        assertTrue(readerUsesLegacyDefaultV26("qingmo", 18f, 1.75f, 3f, 20f))
        assertTrue(readerUsesLegacyDefaultV26("qingmo", 18f, 1.56f, 0f, 18f))
    }
    @Test fun preservesCustomTypographyAndOtherPresets() {
        assertFalse(readerUsesLegacyDefaultV26("custom", 18f, 1.56f, 0f, 18f))
        assertFalse(readerUsesLegacyDefaultV26("qingmo", 21f, 1.56f, 0f, 18f))
        assertFalse(readerUsesLegacyDefaultV26("qingmo", 18f, 1.80f, 0f, 18f))
        assertFalse(readerUsesLegacyDefaultV26("qingmo", 18f, 1.56f, 8f, 18f))
        assertFalse(readerUsesLegacyDefaultV26("qingmo", 18f, 1.56f, 0f, 24f))
        assertFalse(readerUsesLegacyDefaultV26("comfort", 20f, 1.65f, 8f, 22f))
    }
}
