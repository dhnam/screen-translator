package dev.dhnam.screentranslator

import dev.dhnam.screentranslator.SessionEffect.Launch
import dev.dhnam.screentranslator.SessionEffect.Message
import dev.dhnam.screentranslator.SessionEffect.None
import dev.dhnam.screentranslator.SessionEffect.Save
import dev.dhnam.screentranslator.SessionEffect.StartTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMachineTest {
    private val m = SessionMachine()

    @Test fun happyPath() {
        assertEquals(StartTimer(1), m.show())
        assertEquals(Save(1), m.screenshot(true))
        assertEquals(Launch, m.saved(1, true))
        assertEquals(SessionState.Done, m.state)
    }

    @Test fun nullScreenshot_messagesAndDone() {
        m.show()
        assertEquals(Message(R.string.no_screenshot), m.screenshot(false))
        assertEquals(SessionState.Done, m.state)
    }

    @Test fun timeout_whileWaiting_messages() {
        m.show()
        assertEquals(Message(R.string.no_screenshot), m.timeout(1))
        assertEquals(SessionState.Done, m.state)
    }

    @Test fun timeout_staleGeneration_isIgnored() {
        m.show(); m.show()
        assertEquals(None, m.timeout(1))
        assertEquals(SessionState.Waiting(2), m.state)
    }

    @Test fun saved_staleGeneration_isIgnored() {
        m.show()
        m.screenshot(true)
        assertEquals(StartTimer(2), m.show())
        assertEquals(None, m.saved(1, true))
        assertEquals(Save(2), m.screenshot(true))
        assertEquals(Launch, m.saved(2, true))
    }

    @Test fun screenshot_afterDone_isIgnored() {
        m.show(); m.timeout(1)
        assertEquals(None, m.screenshot(true))
        assertEquals(SessionState.Done, m.state)
    }

    @Test fun saved_failure_messages() {
        m.show(); m.screenshot(true)
        assertEquals(Message(R.string.save_failed), m.saved(1, false))
    }
}
