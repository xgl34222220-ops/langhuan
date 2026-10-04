package com.xiguli.langhuan.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableNumberFormattingV61Test {
    @Test
    fun technicalDecimalsStayAsciiInsteadOfUsingCommaLocaleOutput() {
        assertNotEquals("1.5", String.format(Locale.FRANCE, "%.1f", 1.5))
        assertEquals("1.5", stableOneDecimalV61(1.5))
        assertEquals("9.8s", stableOneDecimalV61(9.75, "s"))
    }
}
