package hr.divanguz.homepanel.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import hr.divanguz.homepanel.calendar.CalendarView
import hr.divanguz.homepanel.calendar.withTasks
import androidx.glance.LocalContext
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

/**
 * Day column in the event list: the weekday in its own sub-column, then the date, so every date starts
 * at the same place (the user's pick: no "Mon23" glued together, no gap after "Fri").
 */
private const val DAY_COLUMN = 84

/** The weekday's sub-column: room for the widest short weekday ("Wed", "Čet") in a wide system font. */
private const val WEEKDAY_COLUMN = 36

/** Fixed gap between the day column and the colour dot, so the date never touches it. */
private val DAY_GAP = 8.dp

internal fun titleCase(s: String, locale: Locale) = s.replace(".", "").replaceFirstChar { it.titlecase(locale) }

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

    // City (with the optional date and source) sits at the right of the conditions; it is the
    // invisible settings shortcut. No header row above the temperature, so everything moves up.
    val showDate = Settings.get(context).showDate
    val place = state.place?.takeIf { it.isNotBlank() }
    val date = if (showDate) titleCase(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), locale) else null
    // The source carries the hour its current reading was taken: "DHMZ (7h)".
    val source = if (showSource) weather!!.source + weather.observed?.let { " ($it)" }.orEmpty() else null
    // What fits beside the left text of the same line (~7.5dp a character, less the AQI): the date goes
    // first, then the source, then the place is shortened, so the rain line or sentence isn't squeezed.
    fun corner(leftChars: Int): String {
        val room = maxOf(8, ((width.value - 44) / (7.5f * look.conditions)).toInt() - leftChars - if (weather?.aqiValue != null) 9 else 0)
        return listOf(listOfNotNull(place, date, source), listOfNotNull(place, source), listOfNotNull(place ?: source))
            .map { it.joinToString(" · ") }.firstOrNull { it.length <= room }
            ?: (place ?: source).orEmpty().let { if (it.length > room) it.take(room - 1).trimEnd() + "…" else it }
    }
    Box(modifier = GlanceModifier.fillMaxWidth()) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 14.dp)
                .opens(tap),
        ) {
            val tempSp = 76f * look.temperatureSp / 84f
            val temperature = weather?.temperature ?: "–"
            val style = Settings.get(context).hoursStyle
            val fontScale = context.resources.configuration.fontScale
            val k = look.forecast
            val kc = look.conditions
            // The hours sit beside the days in the list and columns styles; other styles show the days alone.
            val slots = weather?.slots.orEmpty().takeIf { it.size == 3 && (style == Settings.HOURS_LIST || style == Settings.HOURS_COLUMNS) }
            // Every part has a fixed place sized for the widest values ("-12°", "100%", "-10°" over "-18°"),
            // so nothing moves or disappears when the numbers get longer. Only the width decides what is shown.
            val tempW = (ems("-12°") * tempSp * fontScale + 4).dp
            val condW = (CONDITION_W * kc * fontScale).dp
            val inner = width - 36.dp
            val colW = maxOf(ems("-14°") * VALUE_SP, ems("100%") * CHANCE_SP) * k * fontScale + 2
            val right = inner - tempW - condW - 8.dp
            val hours = slots?.takeIf { 3 * colW + 13 <= right.value }
            val dayCount = (minOf(days.size, 3) downTo 0).first { n -> ((if (hours != null) 3 else 0) + n) * colW + (if (hours != null && n > 0) 13 else 0) <= right.value }
            // Top row: the small labels together, today's range on the left and "● AQI · place · source"
            // on the right (the invisible settings shortcut).
            val range = weather?.todayRange?.let { "${texts.today} $it" }
            val aqi = weather?.aqiValue
            val corner = corner((range?.length ?: 0) + 2)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) { range?.let { Label(it, sz(13, kc), second) } }
                Box(
                    modifier = GlanceModifier.padding(start = 8.dp).opens(settingsIntent(context)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (aqi != null) {
                            Dot(dz(7, kc), aqiColor(aqi, weather.aqiEuropean))
                            Spacer(GlanceModifier.width(6.dp))
                            Label("AQI $aqi" + if (corner.isNotEmpty()) " " else "", sz(13, kc), second)
                        }
                        if (corner.isNotEmpty()) Label((if (aqi != null) "· " else "") + corner, sz(13, kc), Palette.ink3P)
                        if (aqi == null && corner.isEmpty()) Spacer(GlanceModifier.width(48.dp).height(18.dp))
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            val outlook = weather?.outlook?.takeIf { style == Settings.HOURS_SENTENCE }
            val showRain = Settings.get(context).showRain
            val rain = weather?.rain?.takeIf { showRain && outlook == null }
            val bars = weather?.rainBars?.takeIf { showRain }
            // Middle: on the left the temperature, the conditions beside it (icon above the words) and the
            // rain line below; on the right three hours and three days in equal columns.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = GlanceModifier.width(tempW + condW)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = GlanceModifier.width(tempW)) { Label(temperature, tempSp.sp, main) }
                        if (weather != null) Column(modifier = GlanceModifier.width(condW)) {
                            Icon(weather.icon, dz(22, kc), second)
                            Spacer(GlanceModifier.height(3.dp))
                            conditionLines(weather.condition, condW.value / (0.55f * 14 * kc * fontScale)).forEach {
                                Label(it, sz(14, kc), main)
                            }
                        }
                    }
                    // The rain line's place stays even when it is empty.
                    Row(modifier = GlanceModifier.height(dz(20, kc)), verticalAlignment = Alignment.CenterVertically) {
                        if (rain != null) Label(rain, sz(13, kc), second)
                        if (rain != null && bars != null) Spacer(GlanceModifier.width(10.dp))
                        if (bars != null) RainBars(bars, kc)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.width(right), verticalAlignment = Alignment.Top) {
                    hours?.forEach { HourColumnB(it, main, second, GlanceModifier.defaultWeight()) }
                    if (hours != null && dayCount > 0) Hairline(dz(COLUMN_H, k), 0.dp, 6.dp)
                    days.take(dayCount).forEach { DayColumnB(it, main, second, locale, GlanceModifier.defaultWeight()) }
                }
            }
            // Below, only when there is something to say: waiting, a warning, the sentence.
            val warning = weather?.warning
            val lines = mutableListOf<@Composable () -> Unit>()
            if (weather == null) {
                val t = if (state.haConfigured) texts.waitingWeather else texts.tapToSetUp
                lines += { Label(t, sz(14, kc), second) }
            }
            if (warning != null) lines += { WarningChip(warning, sz(13, kc)) }
            if (outlook != null) lines += { IconLine(R.drawable.ic_wx_clock, outlook, kc) }
            lines.forEach { line ->
                Spacer(GlanceModifier.height(6.dp))
                line()
            }
            HourlyStrip(weather, main, second)
        }
    }
}

/** One type scale for the forecast: labels (hours, weekdays) and values (every temperature). */
private const val LABEL_SP = 13
private const val VALUE_SP = 16
private const val CHANCE_SP = 12

/** The conditions' place beside the temperature ("Light snow", or "Umjereno" over "oblačno"). */
private const val CONDITION_W = 80

/** A forecast column's height (label, icon, value and the line under it), for the hairline beside them. */
private const val COLUMN_H = 92

/** Rough text width in em of the system font: digits ≈ 0.56, minus 0.36, degree sign 0.38, percent 0.85. */
private fun ems(s: String) = s.sumOf { when (it) { '°' -> 0.38; '-', '−' -> 0.36; '%' -> 0.85; else -> 0.56 } }.toFloat()

/** The condition on one line when it fits [chars], else the first word over the rest. */
internal fun conditionLines(condition: String, chars: Float): List<String> {
    val words = condition.split(' ')
    return if (condition.length <= chars || words.size < 2) listOf(condition)
    else listOf(words.first(), words.drop(1).joinToString(" "))
}

/** An hour: "9h", icon, temperature, the chance of rain under it (its place kept when there is none). */
@Composable
private fun HourColumnB(h: HourState, main: ColorProvider, second: ColorProvider, modifier: GlanceModifier) {
    val k = LocalLook.current.forecast
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(h.label, sz(LABEL_SP, k), Palette.ink3P)
        Spacer(GlanceModifier.height(6.dp))
        Icon(h.icon, dz(22, k), if (h.conditionKey == "sunny") SunTint else second)
        Spacer(GlanceModifier.height(6.dp))
        Label(h.temperature, sz(VALUE_SP, k), main)
        Label(h.rain ?: " ", sz(CHANCE_SP, k), Palette.rainP)
    }
}

/** A day: weekday, icon, the high and the low under it. */
@Composable
private fun DayColumnB(day: ForecastDayState, main: ColorProvider, second: ColorProvider, locale: Locale, modifier: GlanceModifier) {
    val k = LocalLook.current.forecast
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(titleCase(day.date.format(DateTimeFormatter.ofPattern("EEE", locale)), locale), sz(LABEL_SP, k), Palette.ink3P)
        Spacer(GlanceModifier.height(6.dp))
        Icon(day.icon, dz(22, k), if (day.conditionKey == "sunny") SunTint else second)
        Spacer(GlanceModifier.height(6.dp))
        Label(day.high, sz(VALUE_SP, k), main, weight = FontWeight.Medium)
        Label(day.low ?: " ", sz(VALUE_SP, k), Palette.ink3P)
    }
}

/** A vertical hairline between the hours and the days, with room on both sides. */
@Composable
private fun Hairline(height: Dp, lift: Dp, side: Dp = 8.dp) {
    // One child of the row (Glance drops children past ten in a row).
    Box(modifier = GlanceModifier.padding(start = side, end = side, bottom = lift)) {
        Box(modifier = GlanceModifier.width(1.dp).height(height).background(Palette.hairP)) {}
    }
}

/** The next hour's rain as four 15-minute bars over "now" and "1h". */
@Composable
private fun RainBars(bars: List<Float>, k: Float) {
    val full = 12 * k
    Row(modifier = GlanceModifier.height(full.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, b ->
            if (i > 0) Spacer(GlanceModifier.width(3.dp))
            val h = if (b <= 0f) 2f else maxOf(3f, b * full)
            Box(modifier = GlanceModifier.width(dz(8, k)).height(h.dp).background(if (b <= 0f) Palette.hairP else Palette.rainP)) {}
        }
    }
}

/** A small icon and one line of text, as the sentence. */
@Composable
private fun IconLine(@androidx.annotation.DrawableRes icon: Int, text: String, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, dz(16, k), Palette.ink2P)
        Spacer(GlanceModifier.width(6.dp))
        Label(text, sz(14, k), Palette.ink2P)
    }
}

@Composable
internal fun CalendarZoneB(snapshot: CalendarSnapshot?, context: Context, modifier: GlanceModifier, first: Boolean = false) {
    if (sideBySide(context)) {
        SplitZone(snapshot, context, modifier, top = if (first) 14.dp else 8.dp, bottom = 12.dp)
        return
    }
    val calendarApp = launchIntent(context, Packages.CALENDAR)
    val texts = LocalTexts.current
    val tasks = shownTasks(context, LocalTasks.current)
    val filter = if (tasks.isEmpty() && Settings.get(context).taskLists.isEmpty()) "calendar" else LocalListFilter.current
    // The tasks-only list needs no calendar access.
    val base = snapshot?.takeIf { it.hasPermission }?.toView(locale = texts.locale, allDayLabel = texts.allDay)
        ?: CalendarView(false, emptyList()).takeIf { filter == "tasks" }
    val view = base?.withTasks(tasks, filter, texts.noDate, locale = texts.locale)
    val switch = Settings.get(context).taskLists.isNotEmpty()
    val k = LocalLook.current.calendar
    val today = LocalDate.now()
    val locale = texts.locale

    Column(modifier = modifier.fillMaxWidth().padding(start = 18.dp, top = if (first) 14.dp else 4.dp, bottom = 12.dp)) {
        // The + target is 44dp and bleeds 11dp into the right padding, so the row ends 7dp from the edge.
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(dz(44, k)).padding(end = 7.dp).opens(calendarApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Today's date sits over the day column; "Nothing today" starts where event titles start
            // (day column + dot 10 + gap 10).
            DayLabel(today, locale, k, FontWeight.Medium)
            Spacer(GlanceModifier.width(DAY_GAP))
            if (switch) {
                Spacer(GlanceModifier.defaultWeight())
                ListSwitch(k)
                Spacer(GlanceModifier.width(4.dp))
            } else if (view?.todayEmpty == true) {
                // A hollow dot in the dot column keeps "Nothing today" in line with the events below.
                Icon(R.drawable.dot_ring, dz(10, k), Palette.ink3P)
                Spacer(GlanceModifier.width(10.dp))
                Label(texts.nothingToday, sz(14, k), Palette.ink3P)
            }
            if (!switch) Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier.width(dz(44, k)).height(dz(44, k)).opens(plusTap(context, filter)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(R.drawable.ic_add, dz(22, k), Palette.ink2P)
            }
        }
        when {
            filter != "tasks" && snapshot != null && !snapshot.hasPermission -> NoteB(texts.allowCalendar, k, settingsIntent(context))
            view == null -> NoteB(texts.loading, k, calendarApp)
            view.rows.isEmpty() && filter == "tasks" -> NoteB(texts.noTasks, k, OpenAppActivity.intent(context, hr.divanguz.homepanel.calendar.GOOGLE_TASKS))
            view.rows.isEmpty() -> NoteB(texts.nothingNext(Settings.get(context).calendarDays), k, calendarApp)
            LocalStaticList.current -> Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(end = 18.dp)) {
                view.rows.take(STATIC_ROWS).forEachIndexed { i, row -> EventB(view.rows, i, row, today, k, locale) }
            }
            else -> LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(end = 18.dp)) {
                itemsIndexed(view.rows, itemId = { _, row -> row.id }) { i, row -> EventB(view.rows, i, row, today, k, locale) }
            }
        }
    }
}

@Composable
private fun EventB(rows: List<EventRow>, i: Int, row: EventRow, today: LocalDate, k: Float, locale: Locale) {
    val firstOfDay = i == 0 || rows[i - 1].date != row.date
    // 2dp between events of a day, 10dp before a new day; not scaled by the slider.
    val gap = when {
        i == 0 -> 0.dp
        firstOfDay -> 10.dp
        else -> 2.dp
    }
    EventRowB(row, if (firstOfDay) row.date else null, row.date == today, gap, k, locale)
}

@Composable
private fun EventRowB(row: EventRow, day: LocalDate?, isToday: Boolean, gap: Dp, k: Float, locale: Locale) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth().opens(rowTap(context, row))) {
        if (gap > 0.dp) Spacer(GlanceModifier.height(gap))
        Row(modifier = GlanceModifier.fillMaxWidth().height(dz(26, k)), verticalAlignment = Alignment.CenterVertically) {
            // Only the first event of a day carries the label; "Today" stands alone.
            when {
                day == null -> Spacer(GlanceModifier.width(dz(DAY_COLUMN, k)))
                row.undated -> Label(LocalTexts.current.noDate, sz(15, k), Palette.ink2P, modifier = GlanceModifier.width(dz(DAY_COLUMN, k)))
                row.overdue -> DayLabel(day, locale, k, FontWeight.Normal, OverdueP)
                isToday -> Label(LocalTexts.current.today, sz(15, k), Palette.ink2P, modifier = GlanceModifier.width(dz(DAY_COLUMN, k)))
                else -> DayLabel(day, locale, k, FontWeight.Normal)
            }
            if (row.task != null) {
                // The ring's tap target reaches 6dp into the gaps on both sides, so the title stays in line.
                Spacer(GlanceModifier.width(DAY_GAP - 6.dp))
                TaskRing(row.task, dz(12, k), dz(10, k) + 12.dp, dz(26, k))
                Spacer(GlanceModifier.width(4.dp))
            } else {
                Spacer(GlanceModifier.width(DAY_GAP))
                Dot(dz(10, k), ColorProvider(Color((row.event?.color ?: 0xFF8A909C.toInt()) or 0xFF000000.toInt())))
                Spacer(GlanceModifier.width(10.dp))
            }
            Label(row.title, sz(16, k), Palette.inkP, modifier = GlanceModifier.defaultWeight())
            // Events over several days show their first and last day; other all-day events show no time.
            val right = row.span ?: row.time.takeIf { row.event?.allDay == false || (row.task != null && it.isNotEmpty()) }
            if (right != null) {
                Spacer(GlanceModifier.width(8.dp))
                Label(right, sz(15, k), Palette.ink3P)
            }
        }
    }
}

/** "Fri" in the weekday sub-column, then "23 Oct" left-aligned after it. */
@Composable
private fun DayLabel(date: LocalDate, locale: Locale, k: Float, weight: FontWeight, color: ColorProvider = Palette.ink2P) {
    Row(modifier = GlanceModifier.width(dz(DAY_COLUMN, k)), verticalAlignment = Alignment.CenterVertically) {
        Label(
            titleCase(date.format(DateTimeFormatter.ofPattern("EEE", locale)), locale), sz(15, k), color, weight = weight,
            modifier = GlanceModifier.width(dz(WEEKDAY_COLUMN, k)),
        )
        Label(date.format(DateTimeFormatter.ofPattern("d MMM", locale)).replace(".", ""), sz(15, k), color, weight = weight)
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
        // Yellow text is unreadable on a light card; there the dot alone carries the level.
        Label(texts.warning(texts.warningType(w.type), w.tomorrow), size, if (Palette.theme.light) Palette.inkP else color)
    }
}

@Composable
private fun Dot(size: Dp, color: ColorProvider) {
    Box(modifier = GlanceModifier.width(size).height(size).background(color).cornerRadius(size / 2)) {}
}
