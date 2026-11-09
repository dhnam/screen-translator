package dev.dhnam.screentranslator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
    private val papagoRenamed = ComponentName(papago.packageName, "com.naver.labs.translator.ui.share.ShareActivity")
    private val lens = ComponentName.unflattenFromString(TargetList.LENS)!!
    private val saves = ComponentName(lens.packageName, "com.google.android.apps.search.googleapp.saves.SavesShareReceiverWithImageType")

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

    private fun disable(cn: ComponentName) =
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)

    private fun Intent.has(flag: Int) = flags and flag != 0

    // --- resolve ---

    @Test fun resolve_exact_isExact() {
        install(papago)
        assertEquals(Target.Exact(papago), resolver.resolve(papago))
    }

    @Test fun resolve_renamedActivity_singleMatch_isExact() {
        install(papagoRenamed)
        assertEquals(Target.Exact(papagoRenamed), resolver.resolve(papago))
    }

    @Test fun resolve_renamed_twoMatches_isMissing() {
        install(saves)
        install(ComponentName(lens.packageName, "com.google.android.apps.search.lens.NewLensActivity"))
        assertSame(Target.Missing, resolver.resolve(lens))
    }

    @Test fun resolve_noPackage_isMissing() = assertSame(Target.Missing, resolver.resolve(papago))

    @Test fun resolve_disabledActivity_isMissing() {
        install(papago)
        disable(papago)
        assertSame(Target.Missing, resolver.resolve(papago))
    }

    // --- intents ---

    @Test fun buildIntent_exact_setsComponentTypeStreamClipAndFlags() {
        val i = resolver.buildIntent(Target.Exact(papago), uri)
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("image/png", i.type)
        assertEquals(papago, i.component)
        @Suppress("DEPRECATION")
        assertEquals(uri, i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, i.clipData!!.getItemAt(0).uri)
        assertTrue(i.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(i.has(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test fun buildIntent_missing_noTarget() {
        val i = resolver.buildIntent(Target.Missing, uri)
        assertNull(i.component)
        assertNull(i.`package`)
    }

    @Test fun finalIntent_missing_isChooser() {
        val out = resolver.finalIntent(resolver.buildIntent(Target.Missing, uri))
        assertEquals(Intent.ACTION_CHOOSER, out.action)
        assertEquals(uri, out.clipData!!.getItemAt(0).uri)
        assertTrue(out.has(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    @Test fun chooser_whenTargetMissing_keepsGrantAndClip() {
        val out = resolver.finalIntent(resolver.buildIntent(Target.Exact(papago), uri))
        assertEquals(Intent.ACTION_CHOOSER, out.action)
        assertTrue(out.has(Intent.FLAG_ACTIVITY_NEW_TASK))
        @Suppress("DEPRECATION")
        val inner = out.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertNull(inner.`package`)
        assertNull(inner.component)
    }

    @Test fun finalIntent_whenResolvable_returnsSameIntent() {
        install(papago)
        val i = resolver.buildIntent(Target.Exact(papago), uri)
        assertSame(i, resolver.finalIntent(i))
    }

    // --- share targets / labels ---

    @Test fun shareTargets_excludesExactSelfAndDev_keepsLookalike() {
        val self = ComponentName("dev.dhnam.screentranslator", "dev.dhnam.screentranslator.Fake")
        val dev = ComponentName("dev.dhnam.screentranslator.dev", "dev.dhnam.screentranslator.Fake")
        val lookalike = ComponentName("dev.dhnam.screentranslatorfoo", "foo.Share")
        install(papago); install(self); install(dev); install(lookalike)
        val list = resolver.shareTargets()
        assertTrue(papago in list && lookalike in list)
        assertFalse(self in list || dev in list)
    }

    @Test fun shareTargets_fromDevBuild_alsoExcludesRelease() {
        val release = ComponentName("dev.dhnam.screentranslator", "dev.dhnam.screentranslator.Fake")
        install(release)
        assertFalse(release in TargetResolver(pm, "dev.dhnam.screentranslator.dev").shareTargets())
    }

    @Test fun label_activityDiffersFromApp_joinsWithDot() {
        install(lens, appLabel = "Google", activityLabel = "Lens")
        assertEquals("Google · Lens", resolver.label(lens))
    }

    @Test fun label_sameAsApp_isSingle() {
        install(papago, appLabel = "Papago", activityLabel = "Papago")
        assertEquals("Papago", resolver.label(papago))
    }

    @Test fun label_disabledActivity_stillShowsAppName() {
        install(papago, appLabel = "Papago", activityLabel = "Papago")
        disable(papago)
        assertEquals("Papago", resolver.label(papago))
    }

    @Test fun label_unknown_isPackageName() = assertEquals(papago.packageName, resolver.label(papago))
}
