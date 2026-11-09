package dev.dhnam.screentranslator

import dev.dhnam.screentranslator.UpdateEvent.CheckError
import dev.dhnam.screentranslator.UpdateEvent.CheckOk
import dev.dhnam.screentranslator.UpdateEvent.CheckStarted
import dev.dhnam.screentranslator.UpdateEvent.Cancelled
import dev.dhnam.screentranslator.UpdateEvent.Committed
import dev.dhnam.screentranslator.UpdateEvent.DownloadError
import dev.dhnam.screentranslator.UpdateEvent.DownloadStarted
import dev.dhnam.screentranslator.UpdateEvent.InstallFinished
import dev.dhnam.screentranslator.UpdateEvent.Progress
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateUiTest {
    @Test fun check_flow() {
        assertEquals(UpdateUi.Checking, UpdateUi.Idle.next(CheckStarted))
        assertEquals(UpdateUi.Idle, UpdateUi.Checking.next(CheckOk))
        assertEquals(UpdateUi.CheckFailed, UpdateUi.Checking.next(CheckError))
        assertEquals(UpdateUi.Checking, UpdateUi.CheckFailed.next(CheckStarted))
    }

    @Test fun download_flow() {
        val d = UpdateUi.Idle.next(DownloadStarted)
        assertEquals(UpdateUi.Downloading(0), d)
        assertEquals(UpdateUi.Downloading(40), d.next(Progress(40)))
        assertEquals(UpdateUi.AwaitingInstall, UpdateUi.Downloading(40).next(Committed))
        assertEquals(UpdateUi.Downloading(0), UpdateUi.CheckFailed.next(DownloadStarted))
    }

    @Test fun awaitingInstall_installFinished_isIdle() =
        assertEquals(UpdateUi.Idle, UpdateUi.AwaitingInstall.next(InstallFinished))

    @Test fun download_cancelOrError_isIdle() {
        assertEquals(UpdateUi.Idle, UpdateUi.Downloading(5).next(Cancelled))
        assertEquals(UpdateUi.Idle, UpdateUi.Downloading(5).next(DownloadError))
    }

    @Test fun disallowed_events_keepState() {
        assertEquals(UpdateUi.Downloading(5), UpdateUi.Downloading(5).next(CheckStarted))
        assertEquals(UpdateUi.AwaitingInstall, UpdateUi.AwaitingInstall.next(DownloadStarted))
        assertEquals(UpdateUi.Idle, UpdateUi.Idle.next(Progress(3)))
        assertEquals(UpdateUi.Checking, UpdateUi.Checking.next(DownloadStarted))
        assertEquals(UpdateUi.Idle, UpdateUi.Idle.next(InstallFinished))
    }
}
