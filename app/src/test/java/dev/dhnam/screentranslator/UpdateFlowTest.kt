package dev.dhnam.screentranslator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException

class UpdateFlowTest {
    private val seen = mutableListOf<UpdateUi>()

    @Before fun setUp() {
        UpdateFlow.resetForTest()
        UpdateFlow.background = { it.run() }
        UpdateFlow.main = { it.run() }
    }

    @After fun tearDown() = UpdateFlow.resetForTest()

    @Test fun observe_receivesCurrentStateImmediately() {
        UpdateFlow.observe { seen += it }
        assertEquals(listOf<UpdateUi>(UpdateUi.Idle), seen)
    }

    @Test fun startCheck_success_goesCheckingThenIdle() {
        UpdateFlow.observe { seen += it }
        UpdateFlow.startCheck { }
        assertEquals(listOf(UpdateUi.Idle, UpdateUi.Checking, UpdateUi.Idle), seen)
    }

    @Test fun startCheck_ioError_goesCheckFailed() {
        UpdateFlow.startCheck { throw IOException("offline") }
        assertEquals(UpdateUi.CheckFailed, UpdateFlow.state)
    }

    @Test fun startCheck_whileDownloading_isIgnored() {
        UpdateFlow.dispatch(UpdateEvent.DownloadStarted)
        var ran = false
        assertFalse(UpdateFlow.startCheck { ran = true })
        assertFalse(ran)
        assertEquals(UpdateUi.Downloading(0), UpdateFlow.state)
    }

    @Test fun noObserver_stateStillAdvances() {
        UpdateFlow.observe { seen += it }
        UpdateFlow.stopObserving()
        UpdateFlow.startCheck { }
        assertEquals(UpdateUi.Idle, UpdateFlow.state)
        assertEquals(1, seen.size)
    }

    @Test fun startInstall_success_awaitingInstall() {
        UpdateFlow.observe { seen += it }
        UpdateFlow.startInstall({ progress -> progress(50) }) { }
        assertEquals(listOf(UpdateUi.Idle, UpdateUi.Downloading(0), UpdateUi.Downloading(50), UpdateUi.AwaitingInstall), seen)
    }

    @Test fun startInstall_cancelled_isIdleWithoutError() {
        var error: IOException? = null
        UpdateFlow.startInstall({ throw IOException(UpdateInstaller.CANCELLED) }) { error = it }
        assertEquals(UpdateUi.Idle, UpdateFlow.state)
        assertEquals(null, error)
    }

    @Test fun startInstall_failure_reportsError() {
        var error: IOException? = null
        UpdateFlow.startInstall({ throw IOException("HTTP 500") }) { error = it }
        assertEquals(UpdateUi.Idle, UpdateFlow.state)
        assertEquals("HTTP 500", error?.message)
    }
}
