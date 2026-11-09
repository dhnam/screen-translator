package dev.dhnam.screentranslator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun prefs_default_isPapago() = assertEquals(Target.PAPAGO, Prefs(ctx).target)

    @Test fun prefs_roundTrip() {
        Prefs(ctx).target = Target.GOOGLE
        assertEquals(Target.GOOGLE, Prefs(ctx).target)
    }

    @Test fun openingToast_default_isOff() = assertEquals(false, Prefs(ctx).showOpeningToast)

    @Test fun openingToast_roundTrip() {
        Prefs(ctx).showOpeningToast = true
        assertEquals(true, Prefs(ctx).showOpeningToast)
    }

    @Test fun openingMessage_matchesTarget() {
        assertEquals(R.string.opening_papago, Target.PAPAGO.openingMessage)
        assertEquals(R.string.opening_google, Target.GOOGLE.openingMessage)
    }

    @Test fun prefs_unknownStoredValue_fallsBackToPapago() {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("target", "BING").commit()
        assertEquals(Target.PAPAGO, Prefs(ctx).target)
    }
}
