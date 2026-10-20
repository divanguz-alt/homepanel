package hr.divanguz.homepanel.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import hr.divanguz.homepanel.calendar.CalEvent
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.CalendarSource
import hr.divanguz.homepanel.claude.ClaudeSnapshot
import hr.divanguz.homepanel.claude.ClaudeUsage
import hr.divanguz.homepanel.ha.CarData
import hr.divanguz.homepanel.ha.DayForecast
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.ha.HaSnapshot
import hr.divanguz.homepanel.ha.WeatherData
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the widget picker shows (Android 15+ generated previews): the real widget drawn from cached
 * data, or sample data matching the design before anything has been fetched.
 */
object Preview {
    private const val HOUR = 3_600_000L

    fun ha(context: Context): HaSnapshot =
        HaRepository.cached(context).takeIf { it.weather != null } ?: sampleHa()

    fun claude(context: Context): ClaudeSnapshot =
        ClaudeUsage.cached(context).takeIf { it.hasData } ?: sampleClaude()

    fun calendar(context: Context): CalendarSnapshot =
        CalendarSource.load(context).takeIf { it.hasPermission && it.events.isNotEmpty() } ?: sampleCalendar()

    /** Publishes the generated preview; the system rate-limits this, so it's done once per install/update. */
    suspend fun publishOnce(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("homepanel_cache", Context.MODE_PRIVATE)
        // Debug builds keep versionCode 1, so key on the install time instead.
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (prefs.getLong("preview_version", -1) == version) return
        val result = runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(HomePanelReceiver::class) }
            .onFailure { Log.w("HomePanel", "Widget preview not published", it) }
            .getOrNull()
        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
            prefs.edit().putLong("preview_version", version).apply()
        }
    }

    private fun sampleHa(): HaSnapshot {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val days = listOf(
            DayForecast(today.toString(), "partlycloudy", 18.0, 9.0),
            DayForecast(today.plusDays(1).toString(), "sunny", 20.0, 9.0),
            DayForecast(today.plusDays(2).toString(), "partlycloudy", 19.0, 11.0),
            DayForecast(today.plusDays(3).toString(), "rainy", 14.0, 8.0),
        )
        return HaSnapshot(
            weather = WeatherData(
                condition = "clear-night", temperature = 17.0, tomorrowCondition = "sunny", tomorrowHigh = 20.0,
                aqi = "42", days = days, place = "Springfield", source = "Open-Meteo",
            ),
            weatherAt = now,
            car = CarData(address = "Main Street 1, Springfield", area = null, range = "412 km"),
            carAt = now,
        )
    }

    private fun sampleClaude(): ClaudeSnapshot {
        val now = System.currentTimeMillis()
        return ClaudeSnapshot(17f, now + 4 * HOUR + 60_000, 46f, now + 57 * HOUR, fetchedAt = now)
    }

    private fun sampleCalendar(): CalendarSnapshot {
        val zone = ZoneId.systemDefault()
        val friday = LocalDate.now().plusDays(4)
        fun at(day: LocalDate, h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val allDay = friday.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val events = listOf(
            CalEvent(1, at(friday, 9, 30), at(friday, 10, 30), false, "Team meeting", 0xFF4285F4.toInt()),
            CalEvent(2, at(friday, 18, 0), at(friday, 19, 0), false, "Dentist", 0xFFE67C73.toInt()),
            CalEvent(3, allDay, allDay + 24 * HOUR, true, "Birthday", 0xFF33B679.toInt()),
            CalEvent(4, at(friday.plusDays(2), 20, 0), at(friday.plusDays(2), 22, 0), false, "Concert", 0xFFF6BF26.toInt()),
        )
        return CalendarSnapshot(true, events, System.currentTimeMillis())
    }
}
