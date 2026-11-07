package hr.divanguz.homepanel.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SinceTextTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun todayYesterdayAndOlder() {
        assertEquals("Since today 14:20", sinceText(at(7, 14, 20), today, zone, Texts.EN))
        assertEquals("Since yesterday 09:05", sinceText(at(6, 9, 5), today, zone, Texts.EN))
        assertEquals("Since 3 Nov 18:40", sinceText(at(3, 18, 40), today, zone, Texts.EN))
        assertEquals("Od danas 14:20", sinceText(at(7, 14, 20), today, zone, Texts.HR))
    }
}
