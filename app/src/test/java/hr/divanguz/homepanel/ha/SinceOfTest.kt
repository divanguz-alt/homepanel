package hr.divanguz.homepanel.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SinceOfTest {
    private fun st(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun timestampStateWins() {
        val s = st("""{"state":"2026-11-07T08:15:00+00:00","last_changed":"2026-11-07T09:00:00.123456+00:00"}""")
        assertEquals(ms("2026-11-07T08:15:00Z"), HaFetch.sinceOf(s))
    }

    @Test
    fun otherStateUsesLastChanged() {
        val s = st("""{"state":"Example Street 1","last_changed":"2026-11-07T09:00:00.123456+00:00"}""")
        assertEquals(ms("2026-11-07T09:00:00.123Z"), HaFetch.sinceOf(s))
    }

    @Test
    fun zuluState() {
        assertEquals(ms("2026-11-07T08:15:00Z"), HaFetch.parseTime("2026-11-07T08:15:00Z"))
    }
}
