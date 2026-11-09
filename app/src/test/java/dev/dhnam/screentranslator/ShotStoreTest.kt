package dev.dhnam.screentranslator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ShotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 0L
    private lateinit var dir: File
    private lateinit var store: ShotStore

    @Before fun setUp() {
        dir = File(tmp.root, "shots")
        store = ShotStore(dir) { now }
    }

    @Test fun save_writesPngNamedByTimestamp() {
        now = 1_000L
        val f = store.save { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals("shot-1000.png", f.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), f.readBytes())
    }

    @Test fun save_twiceSameMillis_createsDistinctFiles() {
        now = 1_000L
        val a = store.save { it.write(1) }
        val b = store.save { it.write(2) }
        assertNotEquals(a, b)
        assertTrue(a.exists() && b.exists())
    }

    @Test fun save_whenWriterThrows_leavesNoFile() {
        assertThrows(IOException::class.java) { store.save { throw IOException("full") } }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun purge_deletesOnlyFilesOlderThanMaxAge() {
        now = 100 * 60 * 1000L
        dir.mkdirs()
        val old = File(dir, "shot-1.png").apply { writeText("x"); setLastModified(now - ShotStore.MAX_AGE_MS - 1) }
        val fresh = File(dir, "shot-2.png").apply { writeText("x"); setLastModified(now - ShotStore.MAX_AGE_MS) }
        assertEquals(1, store.purgeOlderThan(ShotStore.MAX_AGE_MS))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun purge_ignoresNonShotFiles() {
        now = 100 * 60 * 1000L
        dir.mkdirs()
        val other = File(dir, "other.txt").apply { writeText("x"); setLastModified(0) }
        assertEquals(0, store.purgeOlderThan(ShotStore.MAX_AGE_MS))
        assertTrue(other.exists())
    }

    @Test fun purge_whenDirMissing_returnsZero() {
        assertEquals(0, store.purgeOlderThan(ShotStore.MAX_AGE_MS))
    }
}
