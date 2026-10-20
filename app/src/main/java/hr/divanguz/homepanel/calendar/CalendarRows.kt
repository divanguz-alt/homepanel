package hr.divanguz.homepanel.calendar

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EventRow(
    val id: Long,
    /** The day the row is listed under (today for events that started earlier). */
    val date: java.time.LocalDate,
    val dayLabel: String,
    val time: String,
    val title: String,
    val event: CalEvent,
)

data class CalendarView(val todayEmpty: Boolean, val rows: List<EventRow>)

/** Turns a stored snapshot into rows as of [nowMillis], dropping instances that have already ended. */
fun CalendarSnapshot.toView(
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    allDayLabel: String = "all",
): CalendarView {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val dayFmt = DateTimeFormatter.ofPattern("EEE d", locale)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

    val todayEmpty = events.none { it.startDate(zone) <= today && it.lastDate(zone) >= today }
    val upcoming = events.filter { e ->
        if (e.allDay) e.lastDate(zone) >= today else e.end > nowMillis
    }
    val rows = upcoming.map { e ->
        val start = e.startDate(zone)
        val shownDay = if (start < today) today else start
        val time = when {
            e.allDay -> allDayLabel
            start < today -> "…" // started on an earlier day, still running
            else -> Instant.ofEpochMilli(e.begin).atZone(zone).format(timeFmt)
        }
        EventRow(
            id = e.eventId * 31 + e.begin,
            date = shownDay,
            dayLabel = shownDay.format(dayFmt).replace(".", "").uppercase(locale),
            time = time,
            title = e.title,
            event = e,
        )
    }
    return CalendarView(todayEmpty, rows)
}

/** Opens the default calendar app's "new event" screen. */
fun newEventIntent(): Intent =
    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Opens the instance in the default calendar app. */
fun CalEvent.viewIntent(): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

