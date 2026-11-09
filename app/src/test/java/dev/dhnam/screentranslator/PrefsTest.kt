package dev.dhnam.screentranslator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.dhnam.screentranslator.TargetList.Companion.INITIAL
import dev.dhnam.screentranslator.TargetList.Companion.LENS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sp get() = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Test fun prefs_freshInstall_isInitial() = assertEquals(INITIAL, Prefs(ctx).targetList)

    @Test fun prefs_roundTrip() {
        val l = INITIAL.add("a/a.B").setDefault("a/a.B")
        Prefs(ctx).targetList = l
        assertEquals(l, Prefs(ctx).targetList)
    }

    @Test fun prefs_emptyListPersists() {
        Prefs(ctx).targetList = TargetList(emptyList(), null)
        assertEquals(TargetList(emptyList(), null), Prefs(ctx).targetList)
    }

    @Test fun prefs_migratesOldGoogle_andRemovesOldKey() {
        sp.edit().putString("target", "GOOGLE").commit()
        assertEquals(LENS, Prefs(ctx).targetList.default)
        assertFalse(sp.contains("target"))
        assertEquals(LENS, Prefs(ctx).targetList.default)
    }

    @Test fun updateCheckedAt_defaultZero() = assertEquals(0L, Prefs(ctx).updateCheckedAt)

    @Test fun latestRelease_roundTripAndClear() {
        val r = ReleaseInfo("v0.3.0", "https://x/a.apk", 5, null)
        Prefs(ctx).latestRelease = r
        assertEquals(r, Prefs(ctx).latestRelease)
        Prefs(ctx).latestRelease = null
        assertEquals(null, Prefs(ctx).latestRelease)
        assertFalse(sp.contains("update_tag"))
    }

    @Test fun openingToast_default_isOff() = assertEquals(false, Prefs(ctx).showOpeningToast)

    @Test fun openingToast_roundTrip() {
        Prefs(ctx).showOpeningToast = true
        assertEquals(true, Prefs(ctx).showOpeningToast)
    }
}
