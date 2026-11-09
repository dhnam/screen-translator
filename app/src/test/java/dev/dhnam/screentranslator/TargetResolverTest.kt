package dev.dhnam.screentranslator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TargetResolverTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pm = ctx.packageManager
    private val resolver = TargetResolver(pm)
    private val uri: Uri = Uri.parse("content://dev.dhnam.screentranslator.files/shots/shot-1.png")

    private val lens = ComponentName(Target.GOOGLE.pkg, "com.google.android.apps.search.lens.LensShareEntryPointActivity")
    private val saves = ComponentName(Target.GOOGLE.pkg, "com.google.android.apps.search.googleapp.saves.SavesShareReceiverWithImageType")
    private val papago = ComponentName(Target.PAPAGO.pkg, "com.naver.labs.translator.ui.main.DeepLinkActivity")

    private fun installShareTarget(cn: ComponentName) {
        val sp = shadowOf(pm)
        sp.addActivityIfNotPresent(cn)
        sp.addIntentFilterForActivity(cn, IntentFilter(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("image/*")
        })
    }

    private fun Intent.has(flag: Int) = flags and flag != 0

    @Test fun papago_setsPackageTypeStreamClipAndFlags() {
        val i = resolver.buildIntent(Target.PAPAGO, uri)
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("image/png", i.type)
        assertEquals("com.naver.labs.translator", i.`package`)
        @Suppress("DEPRECATION")
        assertEquals(uri, i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, i.clipData!!.getItemAt(0).uri)
        assertTrue(i.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(i.has(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test fun google_prefersLensComponent() {
        installShareTarget(saves)
        installShareTarget(lens)
        val i = resolver.buildIntent(Target.GOOGLE, uri)
        assertEquals(lens, i.component)
    }

    @Test fun google_noLens_fallsBackToPackage() {
        installShareTarget(saves)
        val i = resolver.buildIntent(Target.GOOGLE, uri)
        assertNull(i.component)
        assertEquals(Target.GOOGLE.pkg, i.`package`)
    }

    @Test fun pickLens_caseInsensitive_firstMatch() {
        val a = ComponentName("p", "x.LENSShare")
        val b = ComponentName("p", "y.lens.Other")
        assertEquals(a, TargetResolver.pickLens(listOf(saves, a, b)))
    }

    @Test fun pickLens_empty_returnsNull() = assertNull(TargetResolver.pickLens(emptyList()))

    @Test fun chooser_whenTargetMissing_keepsGrantAndClip() {
        val out = resolver.finalIntent(resolver.buildIntent(Target.PAPAGO, uri))
        assertEquals(Intent.ACTION_CHOOSER, out.action)
        assertEquals(uri, out.clipData!!.getItemAt(0).uri)
        assertTrue(out.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(out.has(Intent.FLAG_ACTIVITY_NEW_TASK))
        @Suppress("DEPRECATION")
        val inner = out.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertNull(inner.`package`)
        assertNull(inner.component)
    }

    @Test fun finalIntent_whenResolvable_returnsSameIntent() {
        installShareTarget(papago)
        val i = resolver.buildIntent(Target.PAPAGO, uri)
        assertSame(i, resolver.finalIntent(i))
    }
}
