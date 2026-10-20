package hr.divanguz.homepanel.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarRowsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 20)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(today, 10)

    @Test fun dropsEndedEventsAndFormatsRows() {
        val friday = today.plusDays(3)
        val allDayUtc = friday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val snap = CalendarSnapshot(
            true,
            listOf(
                CalEvent(1, at(today, 8), at(today, 9), false, "Ended"),
                CalEvent(2, at(friday, 19, 30), at(friday, 20, 30), false, "FP1", 0xFFE67C73.toInt()),
                CalEvent(3, allDayUtc, allDayUtc + 86_400_000, true, "Birthday"),
            ),
            now,
        )
        val view = snap.toView(now, zone, Locale.UK, allDayLabel = "all")
        assertFalse(view.todayEmpty) // the ended event still counts as "today has events"
        assertEquals(listOf("FP1", "Birthday"), view.rows.map { it.title })
        assertEquals("19:30", view.rows[0].time)
        assertEquals("all", view.rows[1].time)
        assertEquals(friday.plusDays(1), view.rows[1].date)
    }

    @Test fun emptyTodayIsReported() {
        val tomorrow = today.plusDays(1)
        val snap = CalendarSnapshot(true, listOf(CalEvent(1, at(tomorrow, 9), at(tomorrow, 10), false, "Later")), now)
        assertTrue(snap.toView(now, zone, Locale.UK).todayEmpty)
    }
}
