package hr.divanguz.homepanel.widget

import hr.divanguz.homepanel.ha.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HourSlotsTest {
    private val zone = ZoneOffset.UTC
    private fun at(h: Int) = LocalDate.of(2026, 10, 20).atStartOfDay(zone).plusHours(h.toLong()).toInstant().toEpochMilli()
    private fun hour(h: Int, t: Double, c: String = "cloudy", chance: Int? = null) = HourForecast(at(h), t, chance, c)

    // 12:00 to 23:00, warmest at 15, rain 16–18.
    private val hours = (12..23).map { h ->
        when (h) {
            15 -> hour(h, 9.0)
            16, 17, 18 -> hour(h, 7.0, "rainy")
            else -> hour(h, if (h < 15) 6.0 else 4.0)
        }
    }

    @Test fun nextFullHourThenSteps() {
        val now = at(12) + 20 * 60_000L
        assertEquals(listOf(at(13), at(16), at(19)), hourSlots(hours, now, 3).map { it.time })
        assertEquals(listOf(at(13), at(14), at(15)), hourSlots(hours, now, 1).map { it.time })
        assertEquals(listOf(at(13), at(15), at(17)), hourSlots(hours, now, 2).map { it.time })
    }

    @Test fun shortForecastGivesFewerSlots() {
        assertEquals(2, hourSlots(hours, at(20) + 1, 2).size) // 21h and 23h; the forecast ends before 1h
    }

    @Test fun rainBarsCoverTheNextHour() {
        val q = 15 * 60_000L
        val now = at(12) + 5 * 60_000L
        val ends = (1..8).map { at(12) + it * q }
        val wet = hr.divanguz.homepanel.ha.RainNowcast(fetchedAt = now, quarterEnds = ends, quarterMm = listOf(2.0, 0.5, 0.0, 0.05, 1.0, 0.0, 0.0, 0.0))
        val bars = rainBars(wet, now)!!
        assertEquals(4, bars.size)
        assertEquals(1f, bars[0], 0.001f)
        assertEquals(0.5f, bars[1], 0.001f)
        assertEquals(0f, bars[2], 0.001f)
        assertEquals(0f, bars[3], 0.001f) // 0.05 mm counts as dry
        // Dry hour (the wet slot is beyond it), and stale data, give no bars.
        assertNull(rainBars(wet.copy(quarterMm = listOf(0.0, 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0)), now))
        assertNull(rainBars(wet, now + 40 * 60_000L))
    }

    @Test fun rainLineFollowsTheHours() {
        assertEquals("Rain from 16:00", hourlyRainText(hours, at(12) + 1, zone, Texts.EN))
        assertEquals("Rain until 19:00", hourlyRainText(hours, at(16) + 30 * 60_000L, zone, Texts.EN))
        assertNull(hourlyRainText(hours, at(19) + 1, zone, Texts.EN))
        assertNull(hourlyRainText(hours, at(8), zone, Texts.EN)) // rain at 16h is past the six-hour look-ahead
    }

    @Test fun outlookNamesPeakRainAndEnd() {
        val slots = hourSlots(hours, at(12) + 1, 3)
        assertEquals("Up to 9° at 15h, rain 16–19h, 4° at 19h", outlook(slots, hours, Texts.EN, zone))
    }

    @Test fun outlookRainAtStart() {
        val slots = hourSlots(hours, at(15) + 1, 1)
        assertEquals("Rain throughout, 7° at 18h", outlook(slots, hours, Texts.EN, zone))
    }

    @Test fun outlookNeedsTwoSlots() {
        assertNull(outlook(hours.take(1), hours, Texts.EN, zone))
    }
}
