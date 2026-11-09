package dev.dhnam.screentranslator

import dev.dhnam.screentranslator.TargetList.Companion.INITIAL
import dev.dhnam.screentranslator.TargetList.Companion.LENS
import dev.dhnam.screentranslator.TargetList.Companion.PAPAGO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetListTest {
    private val empty = TargetList(emptyList(), null)

    @Test fun initial_hasPapagoAndLens_defaultPapago() {
        assertEquals(listOf(PAPAGO, LENS), INITIAL.items)
        assertEquals(PAPAGO, INITIAL.default)
    }

    @Test fun add_appendsAndIgnoresDuplicate() {
        val once = INITIAL.add("a/b")
        assertEquals(listOf(PAPAGO, LENS, "a/b"), once.items)
        assertEquals(once, once.add("a/b"))
    }

    @Test fun add_toEmpty_becomesDefault() = assertEquals("a/b", empty.add("a/b").default)

    @Test fun remove_default_fallsBackToFirst() =
        assertEquals(TargetList(listOf(LENS), LENS), INITIAL.remove(PAPAGO))

    @Test fun remove_all_leavesEmptyAndNoDefault() =
        assertEquals(empty, INITIAL.remove(PAPAGO).remove(LENS))

    @Test fun setDefault_unknown_isIgnored() = assertEquals(PAPAGO, INITIAL.setDefault("x/y").default)

    @Test fun decode_roundTrip() {
        val l = INITIAL.add("a/b").setDefault("a/b")
        assertEquals(l, TargetList.decode(l.encodeItems(), l.default))
    }

    @Test fun decode_normalizesBlankLinesAndStrayDefault() =
        assertEquals(TargetList(listOf("a/b", "c/d"), "a/b"), TargetList.decode("\na/b\n\na/b\nc/d", "z/z"))

    @Test fun decode_null_isEmpty() = assertEquals(empty, TargetList.decode(null, null))

    @Test fun migrate_google_defaultsToLens() = assertEquals(LENS, TargetList.migrate("GOOGLE").default)

    @Test fun migrate_papagoOrUnknownOrNull_defaultsToPapago() {
        assertEquals(INITIAL, TargetList.migrate("PAPAGO"))
        assertEquals(INITIAL, TargetList.migrate("BING"))
        assertEquals(INITIAL, TargetList.migrate(null))
    }

    @Test fun empty_hasNoDefault() = assertNull(empty.default)
}
