package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun parse_ignoresVPrefix() = assertEquals(Version.parse("0.2.0"), Version.parse("v0.2.0"))

    @Test fun compare_numericNotLexical() = assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.0")!!)

    @Test fun compare_missingPartsAreZero() =
        assertEquals(0, Version.parse("0.2")!!.compareTo(Version.parse("0.2.0")!!))

    @Test fun compare_major() = assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)

    @Test fun parse_ignoresPreReleaseAndBuildSuffix() {
        assertEquals(Version.parse("0.3.0"), Version.parse("v0.3.0-rc1"))
        assertEquals(Version.parse("0.3.0"), Version.parse("0.3.0+build.5"))
        assertTrue(Version.isNewer("v0.3.0-rc1", "0.2.0"))
    }

    @Test fun parse_garbage_isNull() {
        assertNull(Version.parse("abc"))
        assertNull(Version.parse("1.x"))
        assertNull(Version.parse(""))
        assertNull(Version.parse(null))
    }

    @Test fun isNewer_cases() {
        assertTrue(Version.isNewer("v0.3.0", "0.2.0"))
        assertFalse(Version.isNewer("v0.2.0", "0.2.0"))
        assertFalse(Version.isNewer("v0.1.0", "0.2.0"))
        assertFalse(Version.isNewer("bad", "0.2.0"))
    }

    @Test fun shouldAutoCheck_cases() {
        val now = 100 * Version.DAY_MS
        assertTrue(Version.shouldAutoCheck(now, 0))
        assertFalse(Version.shouldAutoCheck(now, now - Version.DAY_MS + 1))
        assertTrue(Version.shouldAutoCheck(now, now - Version.DAY_MS))
        assertTrue(Version.shouldAutoCheck(now, now + 1000))
    }
}
