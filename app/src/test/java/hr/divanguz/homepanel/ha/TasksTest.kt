package hr.divanguz.homepanel.ha

import hr.divanguz.homepanel.calendar.CalEvent
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.toView
import hr.divanguz.homepanel.calendar.withTasks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class TasksTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun parsesServiceResponseSkippingDone() {
        val answer = Json.parseToJsonElement(
            """{"changed_states":[],"service_response":{"todo.a":{"items":[
               {"summary":"Pay the bill","uid":"1","status":"needs_action","due":"2026-11-16"},
               {"summary":"Old","uid":"2","status":"completed"},
               {"summary":"Call","uid":"3","status":"needs_action","due":"2026-11-17T15:30:00+00:00"}]},
               "todo.b":{"items":[{"summary":"Tyres","uid":"9","status":"needs_action"}]}}}""",
        ).jsonObject
        val tasks = HaFetch.parseTasks(answer, listOf("todo.a", "todo.b"))
        assertEquals(listOf("Pay the bill", "Call", "Tyres"), tasks.map { it.summary })
        assertEquals("todo.b", tasks.last().entity)
    }

    @Test
    fun mergesByDayOverdueFirstUndatedLast() {
        val ev = CalEvent(1, now + 3_600_000, now + 7_200_000, false, "Work")
        val view = CalendarSnapshot(true, listOf(ev), now).toView(now, zone, Locale.UK)
        val tasks = listOf(
            TodoTask("todo.a", "1", "Pay the bill", "2026-11-16"),
            TodoTask("todo.a", "3", "Call", "2026-11-17T15:30:00+00:00"),
            TodoTask("todo.b", "9", "Tyres"),
        )
        val all = view.withTasks(tasks, "all", "No date", now, zone, Locale.UK).rows
        assertEquals(listOf("Pay the bill", "Work", "Call", "Tyres"), all.map { it.title })
        assertTrue(all[0].overdue)
        assertEquals("15:30", all[2].time)
        assertTrue(all[3].undated)
        assertEquals("No date", all[3].dayLabel)
        assertEquals(LocalDate.MAX, all[3].date)

        assertEquals(listOf("Work"), view.withTasks(tasks, "calendar", "No date", now, zone, Locale.UK).rows.map { it.title })
        assertEquals(3, view.withTasks(tasks, "tasks", "No date", now, zone, Locale.UK).rows.size)
    }
}
