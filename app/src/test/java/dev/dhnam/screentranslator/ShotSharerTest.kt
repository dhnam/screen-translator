package dev.dhnam.screentranslator

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShotSharerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir get() = File(tmp.root, "shots")
    private fun store() = ShotStore(dir) { 1_000L }

    @Test fun prepare_success_returnsUriForSavedFile() {
        val out = ShotSharer.prepare(store(), { it.write(7); true }) { f -> "uri:" + f.name }
        assertEquals("uri:shot-1000.png", out)
        assertTrue(File(dir, "shot-1000.png").exists())
    }

    @Test fun prepare_whenCompressReturnsFalse_returnsNullAndLeavesNoFile() {
        val out = ShotSharer.prepare(store(), { false }) { "uri" }
        assertNull(out)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun prepare_whenToUriThrowsNonIo_returnsNull() {
        val out = ShotSharer.prepare(store(), { it.write(1); true }) { throw IllegalArgumentException("bad root") }
        assertNull(out)
    }

    @Test fun prepare_whenCompressThrowsRuntime_returnsNull() {
        val out = ShotSharer.prepare(store(), { throw IllegalStateException("recycled") }) { "uri" }
        assertNull(out)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun tryStart_whenActivityNotFound_returnsFalse() =
        assertFalse(ShotSharer.tryStart { throw ActivityNotFoundException() })

    @Test fun tryStart_whenSecurityException_returnsFalse() =
        assertFalse(ShotSharer.tryStart { throw SecurityException() })

    @Test fun tryStart_success_returnsTrue() = assertTrue(ShotSharer.tryStart { })
}
