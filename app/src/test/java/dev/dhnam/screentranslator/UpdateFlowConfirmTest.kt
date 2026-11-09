package dev.dhnam.screentranslator

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateFlowConfirmTest {
    @Before fun setUp() {
        UpdateFlow.resetForTest()
        UpdateFlow.background = { it.run() }
        UpdateFlow.main = { it.run() }
        UpdateFlow.startInstall({ 7 }) { }
    }

    @After fun tearDown() = UpdateFlow.resetForTest()

    @Test fun setConfirm_whileAwaiting_storesAndNotifies() {
        val seen = mutableListOf<UpdateUi>()
        UpdateFlow.observe { seen += it }
        val confirm = Intent("confirm")
        UpdateFlow.setConfirmIntent(confirm)
        assertSame(confirm, UpdateFlow.confirmIntent)
        assertEquals(listOf<UpdateUi>(UpdateUi.AwaitingInstall, UpdateUi.AwaitingInstall), seen)
    }

    @Test fun leavingAwaiting_clearsConfirm() {
        UpdateFlow.setConfirmIntent(Intent("confirm"))
        UpdateFlow.dispatch(UpdateEvent.InstallFinished)
        assertNull(UpdateFlow.confirmIntent)
    }

    @Test fun confirmArrivingBeforeCommitted_isKept() {
        UpdateFlow.resetForTest()
        UpdateFlow.background = { }
        UpdateFlow.main = { it.run() }
        UpdateFlow.startInstall({ 7 }) { }
        assertEquals(UpdateUi.Downloading(null), UpdateFlow.state)
        val confirm = Intent("confirm")
        UpdateFlow.setConfirmIntent(confirm)
        UpdateFlow.dispatch(UpdateEvent.Progress(100))
        UpdateFlow.dispatch(UpdateEvent.Committed)
        assertSame(confirm, UpdateFlow.confirmIntent)
    }

    @Test fun setConfirm_whenNotAwaiting_isIgnored() {
        UpdateFlow.dispatch(UpdateEvent.InstallFinished)
        UpdateFlow.setConfirmIntent(Intent("confirm"))
        assertNull(UpdateFlow.confirmIntent)
    }
}
