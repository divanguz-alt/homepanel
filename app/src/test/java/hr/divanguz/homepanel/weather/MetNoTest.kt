package hr.divanguz.homepanel.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class MetNoTest {
    @Test fun parsesNowAndDays() {
        val root = Json.parseToJsonElement(
            """{"properties":{"timeseries":[
            {"time":"2026-10-19T20:00:00Z","data":{"instant":{"details":{"air_temperature":10.6}},"next_1_hours":{"summary":{"symbol_code":"partlycloudy_night"}}}},
            {"time":"2026-10-20T13:00:00Z","data":{"instant":{"details":{"air_temperature":18.2}},"next_1_hours":{"summary":{"symbol_code":"lightrain"}}}},
            {"time":"2026-10-20T04:00:00Z","data":{"instant":{"details":{"air_temperature":7.1}},"next_6_hours":{"summary":{"symbol_code":"fog"}}}}
            ]}}""",
        ) as JsonObject
        val w = MetNo.parse(root, 45.0, 15.0, european = false, zone = ZoneOffset.UTC, aqi = null)
        assertEquals("partlycloudy", w.condition)
        assertEquals(10.6, w.temperature!!, 0.001)
        val tue = w.days.first { it.date == "2026-10-20" }
        assertEquals(18.2, tue.high!!, 0.001)
        assertEquals(7.1, tue.low!!, 0.001)
        assertEquals("rainy", tue.condition) // the symbol nearest 13:00
    }

    @Test fun mapsSymbols() {
        assertEquals("sunny", MetNo.condition("clearsky_day"))
        assertEquals("clear-night", MetNo.condition("clearsky_night"))
        assertEquals("lightning-rainy", MetNo.condition("rainandthunder"))
        assertEquals("snowy-rainy", MetNo.condition("lightsleetshowers_day"))
        assertEquals("pouring", MetNo.condition("heavyrain"))
    }
}
