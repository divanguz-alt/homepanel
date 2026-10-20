package hr.divanguz.homepanel.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoTest {
    private fun json(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun parsesCurrentAndDays() {
        val w = OpenMeteo.parseForecast(
            json(
                """{"current":{"temperature_2m":15.7,"weather_code":3,"is_day":0},
                "daily":{"time":["2026-10-19","2026-10-20","2026-10-21"],"weather_code":[3,61,96],
                "temperature_2m_max":[21.0,19.3,19.9],"temperature_2m_min":[12.0,11.5,10.2]}}""",
            ),
            45.0, 15.0, european = false, aqi = "59",
        )
        assertEquals("cloudy", w.condition)
        assertEquals(15.7, w.temperature!!, 0.001)
        assertEquals(3, w.days.size)
        assertEquals("rainy", w.tomorrowCondition)
        assertEquals(19.3, w.tomorrowHigh!!, 0.001)
        assertEquals("lightning-rainy", w.days[2].condition)
        assertEquals("59", w.aqi)
    }

    @Test fun mapsWmoCodes() {
        assertEquals("sunny", OpenMeteo.condition(0, isDay = true))
        assertEquals("clear-night", OpenMeteo.condition(1, isDay = false))
        assertEquals("partlycloudy", OpenMeteo.condition(2, isDay = true))
        assertEquals("fog", OpenMeteo.condition(45, isDay = true))
        assertEquals("pouring", OpenMeteo.condition(65, isDay = true))
        assertEquals("snowy", OpenMeteo.condition(73, isDay = true))
    }

    // Each value is the rain of the 15 minutes ending at its time.
    private val t0 = 1_800_000_000L // seconds, on a quarter-hour
    private fun nowcast(vararg mm: Double) = json(
        """{"minutely_15":{"time":[${mm.indices.joinToString { (t0 + it * 900).toString() }}],
        "precipitation":[${mm.joinToString()}]}}""",
    )

    @Test fun rainStartsLater() {
        val now = (t0 + 60) * 1000 // inside the slot ending at t0+900
        val r = OpenMeteo.parseNowcast(nowcast(0.0, 0.0, 0.0, 0.4, 0.6, 0.0), now)
        // Wet slot ends at t0+3*900, so the rain starts 15 minutes before that.
        assertEquals((t0 + 2 * 900) * 1000, r.startsAt)
        assertEquals((t0 + 4 * 900) * 1000, r.endsAt)
    }

    @Test fun dryMeansNoRain() {
        val r = OpenMeteo.parseNowcast(nowcast(0.0, 0.05, 0.0, 0.0), (t0 + 60) * 1000)
        assertNull(r.startsAt)
    }

    @Test fun rainingThroughTheWindowHasNoEnd() {
        val r = OpenMeteo.parseNowcast(nowcast(0.5, 0.5, 0.5, 0.5), (t0 + 60) * 1000)
        assertNull(r.endsAt)
    }
}
