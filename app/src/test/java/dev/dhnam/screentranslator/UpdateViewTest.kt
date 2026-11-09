package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateViewTest {
    private val newer = ReleaseInfo("v0.4.0", "https://x/a.apk", 1, null)

    @Test fun idle_newer_showsInstall() = assertEquals(
        UpdateView(R.string.update_available, listOf("v0.4.0"), UpdateButton.INSTALL, true),
        UpdateView.of(UpdateUi.Idle, newer, "0.3.0"),
    )

    @Test fun idle_notNewer_showsCheck() {
        val expected = UpdateView(R.string.update_latest, listOf("0.3.0"), UpdateButton.CHECK, true)
        assertEquals(expected, UpdateView.of(UpdateUi.Idle, null, "0.3.0"))
        assertEquals(expected, UpdateView.of(UpdateUi.Idle, newer.copy(tag = "v0.3.0"), "0.3.0"))
    }

    @Test fun busyStates_disableButton() {
        assertEquals(UpdateView(R.string.update_checking, emptyList(), UpdateButton.NONE, false), UpdateView.of(UpdateUi.Checking, null, "0.3.0"))
        assertEquals(UpdateView(R.string.update_downloading, listOf(42), UpdateButton.NONE, false), UpdateView.of(UpdateUi.Downloading(42), newer, "0.3.0"))
        val awaiting = UpdateView.of(UpdateUi.AwaitingInstall, newer, "0.3.0")
        assertEquals(R.string.update_awaiting_install, awaiting.text)
        assertFalse(awaiting.enabled)
    }

    @Test fun awaitingInstall_withConfirm_showsReopen() = assertEquals(
        UpdateView(R.string.update_awaiting_install, emptyList(), UpdateButton.REOPEN, true),
        UpdateView.of(UpdateUi.AwaitingInstall, newer, "0.3.0", canReopen = true),
    )

    @Test fun checkFailed_showsRetry() = assertEquals(
        UpdateView(R.string.update_failed, emptyList(), UpdateButton.RETRY, true),
        UpdateView.of(UpdateUi.CheckFailed, null, "0.3.0"),
    )
}
