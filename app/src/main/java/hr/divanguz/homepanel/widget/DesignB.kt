package hr.divanguz.homepanel.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.EventRow
import hr.divanguz.homepanel.calendar.newEventIntent
import hr.divanguz.homepanel.calendar.toView
import hr.divanguz.homepanel.calendar.viewIntent
import hr.divanguz.homepanel.ha.WeatherWarning
import hr.divanguz.homepanel.settings.Settings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Design B: "A2 · Siva karta" (docs/design-b.md). Weather and calendar only; the bottom grid is shared
// with design A and differs only in the map palette (see CarMap) and the text colours on the map.

private val SunTint = ColorProvider(Color(0xFFE5B454))

/** AQI band colour for the dot next to the value: US AQI bands, or the European index's. */
private fun aqiColor(aqi: Int, european: Boolean): ColorProvider {
    // Band edges for good / moderate / poor; anything above is bad.
    val (good, moderate, poor) = if (european) Triple(40, 60, 80) else Triple(50, 100, 150)
    return ColorProvider(
        when {
            aqi <= good -> Color(0xFF7DCB8E)
            aqi <= moderate -> Color(0xFFE5B454)
            aqi <= poor -> Color(0xFFE8894A)
            else -> Color(0xFFE06A6A)
        },
    )
}

/** Day column in the event list: just wide enough for "Wed 23 Oct" (base 62dp in the spec, plus the month). */
private const val DAY_COLUMN = 70

/** Fixed gap between the day column and the colour dot, so the date never touches it. */
private val DAY_GAP = 8.dp

private fun titleCase(s: String, locale: Locale) = s.replace(".", "").replaceFirstChar { it.titlecase(locale) }

@Composable
internal fun WeatherZoneB(state: PanelState, context: Context) {
    val weather = state.weather
    val look = LocalLook.current
    val tap = if (state.haConfigured) launchIntent(context, Packages.WEATHER) else settingsIntent(context)
    val main = if (weather?.stale == true) Palette.ink3P else Palette.inkP
    val second = if (weather?.stale == true) Palette.ink3P else Palette.ink2P
    val width = LocalSize.current.width
    // Up to three days; narrow widgets show fewer rather than squeezing them.
    val maxDays = when {
        width >= 320.dp -> 3
        width >= 270.dp -> 2
        else -> 0
    }
    val days = weather?.days.orEmpty().take(maxDays)
    val texts = LocalTexts.current
    val locale = texts.locale

    val showSource = weather != null && Settings.get(context).showSource

    // An invisible, finger-sized area over the top-left corner (city line) opens HomePanel's settings;
    // it sits on top of the zone, so the rest still opens the weather app.
    Box(modifier = GlanceModifier.fillMaxWidth()) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 16.dp)
                .opens(tap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(state.place ?: " ", sz(15, look.header), Palette.ink2P, weight = FontWeight.Medium)
                if (Settings.get(context).showDate) {
                    Spacer(GlanceModifier.width(8.dp))
                    Dot(dz(4, look.header), Palette.ink3P)
                    Spacer(GlanceModifier.width(8.dp))
                    Label(
                        titleCase(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), locale),
                        sz(15, look.header), Palette.ink2P, weight = FontWeight.Medium,
                    )
                }
                weather?.warning?.let {
                    Spacer(GlanceModifier.width(10.dp))
                    WarningChip(it, sz(14, look.header))
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            // The forecast's bottom sits on the temperature digits' baseline: big text has empty space
            // below the digits (font descent), about 16% of its size, so the forecast is lifted by that.
            val tempSp = 76f * look.temperatureSp / 84f
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Label(weather?.temperature ?: "–", tempSp.sp, main, modifier = GlanceModifier.defaultWeight())
                if (days.isNotEmpty()) {
                    Spacer(GlanceModifier.width(8.dp))
                    Row(
                        modifier = GlanceModifier.width(min(196.dp * look.forecast, width * 0.5f)).padding(bottom = (tempSp * 0.16f).dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        days.forEach { ForecastDayB(it, main, second, locale, GlanceModifier.defaultWeight()) }
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            // Conditions on the left; the optional source under the forecast, same size, same slider.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    if (weather == null) {
                        Label(if (state.haConfigured) texts.waitingWeather else texts.tapToSetUp, sz(14, look.conditions), second)
                    } else {
                        ConditionRowB(weather, second, look.conditions)
                    }
                }
                if (showSource) {
                    Spacer(GlanceModifier.width(8.dp))
                    Label(texts.source(weather!!.source), sz(14, look.conditions), Palette.ink3P)
                }
            }
            // Rain coming (or stopping) soon, under the conditions; only when it matters.
            weather?.rain?.takeIf { Settings.get(context).showRain }?.let { rain ->
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(R.drawable.ic_wx_rain, dz(16, look.conditions), Palette.ink2P)
                    Spacer(GlanceModifier.width(6.dp))
                    Label(rain, sz(14, look.conditions), Palette.ink2P)
                }
            }
        }
        Box(
            modifier = GlanceModifier
                .width(LocalSize.current.width * 0.55f)
                .height(52.dp)
                .opens(settingsIntent(context)),
        ) {}
    }
}

/** Icon · condition · dot · AQI band dot · "AQI n". Nine children: within Glance's ten per row. */
@Composable
private fun ConditionRowB(weather: WeatherState, second: ColorProvider, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(weather.icon, dz(18, k), second)
        Spacer(GlanceModifier.width(6.dp))
        Label(weather.condition, sz(14, k), second)
        val aqi = weather.aqiValue
        if (aqi != null) {
            Spacer(GlanceModifier.width(6.dp))
            Dot(3.dp, Palette.ink3P)
            Spacer(GlanceModifier.width(6.dp))
            Dot(dz(8, k), aqiColor(aqi, weather.aqiEuropean))
            Spacer(GlanceModifier.width(6.dp))
            Label("AQI $aqi", sz(14, k), second)
        }
    }
}

@Composable
private fun ForecastDayB(day: ForecastDayState, main: ColorProvider, second: ColorProvider, locale: Locale, modifier: GlanceModifier) {
    val k = LocalLook.current.forecast
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(titleCase(day.date.format(DateTimeFormatter.ofPattern("EEE", locale)), locale), sz(14, k), second)
        Spacer(GlanceModifier.height(10.dp))
        Icon(day.icon, dz(32, k), if (day.conditionKey == "sunny") SunTint else second)
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Label(day.high, sz(18, k), main, weight = FontWeight.Medium)
            if (day.low != null) {
                Spacer(GlanceModifier.width(4.dp))
                Label(day.low, sz(18, k), Palette.ink3P)
            }
        }
    }
}

@Composable
internal fun CalendarZoneB(snapshot: CalendarSnapshot?, context: Context, modifier: GlanceModifier) {
    val calendarApp = launchIntent(context, Packages.CALENDAR)
    val texts = LocalTexts.current
    val view = snapshot?.takeIf { it.hasPermission }?.toView(locale = texts.locale, allDayLabel = texts.allDay)
    val k = LocalLook.current.calendar
    val today = LocalDate.now()
    val locale = texts.locale

    Column(modifier = modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp, bottom = 12.dp)) {
        // The + target is 44dp and bleeds 11dp into the right padding, so the row ends 7dp from the edge.
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(dz(44, k)).padding(end = 7.dp).opens(calendarApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Today's date sits over the day column; "Nothing today" starts where event titles start
            // (day column + dot 10 + gap 10).
            DayLabel(today, locale, k, FontWeight.Medium)
            Spacer(GlanceModifier.width(DAY_GAP))
            if (view?.todayEmpty == true) {
                // A hollow dot in the dot column keeps "Nothing today" in line with the events below.
                Icon(R.drawable.dot_ring, dz(10, k), Palette.ink3P)
                Spacer(GlanceModifier.width(10.dp))
                Label(texts.nothingToday, sz(14, k), Palette.ink3P)
            }
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier.width(dz(44, k)).height(dz(44, k)).opens(newEventIntent()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(R.drawable.ic_add, dz(22, k), Palette.ink2P)
            }
        }
        when {
            snapshot != null && !snapshot.hasPermission -> NoteB(texts.allowCalendar, k, settingsIntent(context))
            view == null -> NoteB(texts.loading, k, calendarApp)
            view.rows.isEmpty() -> NoteB(texts.nothingNext(Settings.get(context).calendarDays), k, calendarApp)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(end = 18.dp)) {
                itemsIndexed(view.rows, itemId = { _, row -> row.id }) { i, row ->
                    val firstOfDay = i == 0 || view.rows[i - 1].date != row.date
                    // 2dp between events of a day, 10dp before a new day; not scaled by the slider.
                    val gap = when {
                        i == 0 -> 0.dp
                        firstOfDay -> 10.dp
                        else -> 2.dp
                    }
                    EventRowB(row, if (firstOfDay) row.date else null, row.date == today, gap, k, locale)
                }
            }
        }
    }
}

@Composable
private fun EventRowB(row: EventRow, day: LocalDate?, isToday: Boolean, gap: Dp, k: Float, locale: Locale) {
    Column(modifier = GlanceModifier.fillMaxWidth().opens(row.event.viewIntent())) {
        if (gap > 0.dp) Spacer(GlanceModifier.height(gap))
        Row(modifier = GlanceModifier.fillMaxWidth().height(dz(26, k)), verticalAlignment = Alignment.CenterVertically) {
            // Only the first event of a day carries the label; "Today" stands alone.
            when {
                day == null -> Spacer(GlanceModifier.width(dz(DAY_COLUMN, k)))
                isToday -> Label(LocalTexts.current.today, sz(15, k), Palette.ink2P, modifier = GlanceModifier.width(dz(DAY_COLUMN, k)))
                else -> DayLabel(day, locale, k, FontWeight.Normal)
            }
            Spacer(GlanceModifier.width(DAY_GAP))
            Dot(dz(10, k), ColorProvider(Color((row.event.color ?: 0xFF8A909C.toInt()) or 0xFF000000.toInt())))
            Spacer(GlanceModifier.width(10.dp))
            Label(row.title, sz(16, k), Palette.inkP, modifier = GlanceModifier.defaultWeight())
            // All-day events show no time.
            if (!row.event.allDay) {
                Spacer(GlanceModifier.width(8.dp))
                Label(row.time, sz(15, k), Palette.ink3P)
            }
        }
    }
}

/** "Fri" on the left edge of the day column, "23 Oct" on its right edge. */
@Composable
private fun DayLabel(date: LocalDate, locale: Locale, k: Float, weight: FontWeight) {
    Row(modifier = GlanceModifier.width(dz(DAY_COLUMN, k)), verticalAlignment = Alignment.CenterVertically) {
        Label(titleCase(date.format(DateTimeFormatter.ofPattern("EEE", locale)), locale), sz(15, k), Palette.ink2P, weight = weight)
        Spacer(GlanceModifier.defaultWeight())
        Label(date.format(DateTimeFormatter.ofPattern("d MMM", locale)).replace(".", ""), sz(15, k), Palette.ink2P, weight = weight)
    }
}

@Composable
private fun NoteB(text: String, k: Float, tap: android.content.Intent?) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(dz(44, k)).opens(tap), verticalAlignment = Alignment.CenterVertically) {
        Label(text, sz(16, k), Palette.ink2P)
    }
}

/** Official warning next to the city: a dot in the level's colour and "Wind warning" / "Fog tomorrow". */
@Composable
internal fun WarningChip(w: WeatherWarning, size: TextUnit) {
    val color = ColorProvider(
        when (w.level) {
            2 -> Color(0xFFE5B454)
            3 -> Color(0xFFE8894A)
            else -> Color(0xFFE06A6A)
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(8.dp, color)
        Spacer(GlanceModifier.width(6.dp))
        val texts = LocalTexts.current
        Label(texts.warning(texts.warningType(w.type), w.tomorrow), size, color)
    }
}

@Composable
private fun Dot(size: Dp, color: ColorProvider) {
    Box(modifier = GlanceModifier.width(size).height(size).background(color).cornerRadius(size / 2)) {}
}
