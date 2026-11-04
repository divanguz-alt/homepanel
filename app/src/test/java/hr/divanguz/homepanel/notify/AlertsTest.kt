package hr.divanguz.homepanel.notify

import hr.divanguz.homepanel.notify.Alerts.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsTest {
    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L

    @Test
    fun warnsOncePerWindowDespiteJitter() {
        val resets = now + 2 * hour
        assertEquals(Step.WARN, Alerts.decide(85, resets, 0, now, 80))
        // Next fetch: same window, reset time a few seconds off.
        assertEquals(Step.NONE, Alerts.decide(88, resets + 1_700, resets, now + hour / 4, 80))
    }

    @Test
    fun belowThresholdDoesNothing() {
        assertEquals(Step.NONE, Alerts.decide(79, now + hour, 0, now, 80))
        assertEquals(Step.NONE, Alerts.decide(null, now + hour, 0, now, 80))
    }

    @Test
    fun resetIsAnnouncedOnceThenANewWindowCanWarn() {
        val firstReset = now + hour
        assertEquals(Step.RESET, Alerts.decide(5, now + 6 * hour, firstReset, firstReset + 60_000, 80))
        // After the reset the stored window is cleared (warnedFor = 0); a new high window warns again.
        assertEquals(Step.WARN, Alerts.decide(90, now + 6 * hour, 0, now + 4 * hour, 80))
    }
}
