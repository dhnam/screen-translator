package dev.dhnam.screentranslator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val resolver = TargetResolver(pm, "dev.dhnam.screentranslator")
    private val uri: Uri = Uri.parse("content://dev.dhnam.screentranslator.files/shots/shot-1.png")

    private val papago = ComponentName.unflattenFromString(TargetList.PAPAGO)!!
    private val lens = ComponentName.unflattenFromString(TargetList.LENS)!!
    private val devSelf = ComponentName("dev.dhnam.screentranslator.dev", "dev.dhnam.screentranslator.Fake")

    private fun install(cn: ComponentName, appLabel: String? = null, activityLabel: String? = null) {
        val sp = shadowOf(pm)
        if (appLabel != null) {
            val app = ApplicationInfo().apply { packageName = cn.packageName; nonLocalizedLabel = appLabel }
            val act = ActivityInfo().apply {
                packageName = cn.packageName; name = cn.className; applicationInfo = app
                nonLocalizedLabel = activityLabel; exported = true; enabled = true
            }
            sp.installPackage(PackageInfo().apply { packageName = cn.packageName; applicationInfo = app })
            sp.addOrUpdateActivity(act)
        } else {
            sp.addActivityIfNotPresent(cn)
        }
        sp.addIntentFilterForActivity(cn, IntentFilter(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("image/*")
        })
    }

    private fun Intent.has(flag: Int) = flags and flag != 0

    @Test fun buildIntent_setsComponentTypeStreamClipAndFlags() {
        val i = resolver.buildIntent(papago, uri)
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("image/png", i.type)
        assertEquals(papago, i.component)
        @Suppress("DEPRECATION")
        assertEquals(uri, i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, i.clipData!!.getItemAt(0).uri)
        assertTrue(i.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(i.has(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test fun shareTargets_excludesSelf() {
        install(papago); install(lens); install(devSelf)
        val list = resolver.shareTargets()
        assertTrue(papago in list && lens in list)
        assertFalse(devSelf in list)
    }

    @Test fun isAvailable_installed_isTrue() {
        install(papago)
        assertTrue(resolver.isAvailable(papago))
    }

    @Test fun isAvailable_uninstalled_isFalse() = assertFalse(resolver.isAvailable(papago))

    @Test fun label_activityDiffersFromApp_joinsWithDot() {
        install(lens, appLabel = "Google", activityLabel = "Lens")
        assertEquals("Google · Lens", resolver.label(lens))
    }

    @Test fun label_sameAsApp_isSingle() {
        install(papago, appLabel = "Papago", activityLabel = "Papago")
        assertEquals("Papago", resolver.label(papago))
    }

    @Test fun label_unknown_isPackageName() = assertEquals(papago.packageName, resolver.label(papago))

    @Test fun finalIntent_withoutComponent_isChooser() {
        val out = resolver.finalIntent(resolver.buildIntent(null, uri))
        assertEquals(Intent.ACTION_CHOOSER, out.action)
        assertEquals(uri, out.clipData!!.getItemAt(0).uri)
        assertTrue(out.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    @Test fun chooser_whenTargetMissing_keepsGrantAndClip() {
        val out = resolver.finalIntent(resolver.buildIntent(papago, uri))
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
        install(papago)
        val i = resolver.buildIntent(papago, uri)
        assertSame(i, resolver.finalIntent(i))
    }
}
