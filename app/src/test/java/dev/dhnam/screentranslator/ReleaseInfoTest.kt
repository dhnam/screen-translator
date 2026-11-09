package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseInfoTest {
    private fun json(assets: String) = """{"tag_name":"v0.3.0","assets":[$assets]}"""
    private val notes = """{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10}"""
    private val apk = """{"name":"screen-translator-v0.3.0.apk","browser_download_url":"https://x/a.apk","size":4323071,"digest":"sha256:ABCD"}"""
    private val apkNoDigest = """{"name":"screen-translator-v0.3.0.apk","browser_download_url":"https://x/a.apk","size":5}"""

    @Test fun parse_picksApkAsset() =
        assertEquals(ReleaseInfo("v0.3.0", "https://x/a.apk", 4323071, "abcd"), ReleaseInfo.parse(json("$notes,$apk")))

    @Test fun parse_noDigest_sha256Null() = assertNull(ReleaseInfo.parse(json(apkNoDigest))!!.sha256)

    @Test fun parse_noApkAsset_returnsNull() = assertNull(ReleaseInfo.parse(json(notes)))

    @Test fun parse_malformed_returnsNull() {
        assertNull(ReleaseInfo.parse("{"))
        assertNull(ReleaseInfo.parse("""{"message":"API rate limit exceeded"}"""))
    }
}
