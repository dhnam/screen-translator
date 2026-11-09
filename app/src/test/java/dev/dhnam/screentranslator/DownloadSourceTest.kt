package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceTest {
    private val browser = DownloadSource("https://x/a.apk", api = false)
    private val api = DownloadSource("https://api/x", api = true)

    @Test fun shouldTryNext_apiAnyFailure_includingRateLimit() {
        assertTrue(DownloadSource.shouldTryNext(api, 403))
        assertTrue(DownloadSource.shouldTryNext(api, 404))
        assertFalse(DownloadSource.shouldTryNext(api, 200))
    }

    @Test fun order_apiFirstThenBrowser() = assertEquals(
        listOf(DownloadSource("https://api/x", api = true), DownloadSource("https://x/a.apk", api = false)),
        DownloadSource.of(ReleaseInfo("v1", "https://x/a.apk", 1, null, apiUrl = "https://api/x")),
    )

    @Test fun order_noApiUrl_browserOnly() = assertEquals(
        listOf(DownloadSource("https://x/a.apk", api = false)),
        DownloadSource.of(ReleaseInfo("v1", "https://x/a.apk", 1, null)),
    )

    @Test fun shouldTryNext_serverErrorsAndConnectFailures() {
        assertTrue(DownloadSource.shouldTryNext(browser, 504))
        assertTrue(DownloadSource.shouldTryNext(browser, 500))
        assertTrue(DownloadSource.shouldTryNext(browser, null))
    }

    @Test fun shouldTryNext_clientErrorsAndOk_stop() {
        assertFalse(DownloadSource.shouldTryNext(browser, 200))
        assertFalse(DownloadSource.shouldTryNext(browser, 404))
        assertFalse(DownloadSource.shouldTryNext(browser, 403))
    }
}
