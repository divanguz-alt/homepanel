package hr.divanguz.homepanel.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import hr.divanguz.homepanel.settings.Settings
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Serializable
data class CalEvent(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    /** ARGB the calendar app shows for this event (its own color, else its calendar's). */
    val color: Int? = null,
) {
    /** All-day instances are stored at UTC midnight, so read their dates in UTC. */
    fun startDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    /** Last day the event touches (end is exclusive). */
    fun lastDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli((end - 1).coerceAtLeast(begin))
            .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}

@Serializable
data class CalendarSnapshot(
    val hasPermission: Boolean,
    val events: List<CalEvent>,
    val loadedAt: Long,
)

/** A calendar the phone syncs, for choosing which ones the widget shows. */
data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int?)

object CalendarSource {
    /** Calendars Android shows (visible), grouped by account. */
    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    android.provider.CalendarContract.Calendars._ID,
                    android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
                    android.provider.CalendarContract.Calendars.CALENDAR_COLOR,
                ),
                "${android.provider.CalendarContract.Calendars.VISIBLE} = 1", null,
                "${android.provider.CalendarContract.Calendars.ACCOUNT_NAME}, ${android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), if (c.isNull(3)) null else c.getInt(3))
                }
            }
        }
        return out
    }

    private const val MAX_EVENTS = 60

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Instances from the start of today through the configured number of days, all visible calendars. */
    fun load(context: Context, zone: ZoneId = ZoneId.systemDefault()): CalendarSnapshot {
        val daysAhead = Settings.get(context).calendarDays.toLong()
        val now = System.currentTimeMillis()
        if (!hasPermission(context)) return CalendarSnapshot(false, emptyList(), now)

        val today = LocalDate.now(zone)
        // Widen by a day on both sides so all-day (UTC) instances near the edges are included.
        val from = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(daysAhead + 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.TITLE,
            Instances.DISPLAY_COLOR, Instances.CALENDAR_ID,
        )
        val hidden = Settings.get(context).hiddenCalendars
        val events = mutableListOf<CalEvent>()
        try {
            context.contentResolver.query(
                uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(6) in hidden) continue
                    val e = CalEvent(
                        eventId = c.getLong(0),
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        title = c.getString(4)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        color = if (c.isNull(5)) null else c.getInt(5),
                    )
                    if (e.lastDate(zone) >= today && e.startDate(zone) <= today.plusDays(daysAhead)) {
                        events += e
                    }
                }
            }
        } catch (_: SecurityException) {
            return CalendarSnapshot(false, emptyList(), now)
        }
        // All-day instances sort by UTC midnight, which can land after local timed events; re-sort by local day.
        val sorted = events.sortedWith(
            compareBy<CalEvent> { it.startDate(zone).coerceAtLeast(today) }
                .thenBy { !it.allDay }
                .thenBy { it.begin },
        )
        return CalendarSnapshot(true, sorted.take(MAX_EVENTS), now)
    }
}
