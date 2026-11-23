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
            // The forecast's bottom sits on the temperature digits' baseline: big text has empty space
            // below the digits (font descent), about 16% of its size, so the forecast is lifted by that.
            val tempSp = 76f * look.temperatureSp / 84f
            val temperature = weather?.temperature ?: "–"
            val style = Settings.get(context).hoursStyle
            val slots = weather?.slots.orEmpty().takeIf { it.size == 3 }
            val inner = width - 36.dp
            // The list gets a rain-chance column when an hour has one and it costs no forecast day.
            val plain = hoursFit(style, slots, temperature, tempSp, inner, days.size, look.forecast, context)
            val chance = style == Settings.HOURS_LIST && slots?.any { it.rain != null } == true &&
                plain != null && hoursFit(style, slots, temperature, tempSp, inner, days.size, look.forecast, context, chance = true) == plain
            val fit = plain
            // The conditions beside the temperature (one line, else two) when that costs no day or hour.
            val daysOnlyW = if (fit == null && days.isNotEmpty()) min(196.dp * look.forecast, width * 0.5f).value + 8 else 0f
            val conditionLines = weather?.let { w ->
                val scale = context.resources.configuration.fontScale
                val charW = 0.55f * 14 * look.conditions * scale
                val tempW = temperature.sumOf { if (it == '°') 0.38 else 0.56 }.toFloat() * tempSp * scale
                val words = w.condition.split(' ')
                val options = listOf(listOf(w.condition)) +
                    if (words.size > 1) listOf(listOf(words.first(), words.drop(1).joinToString(" "))) else emptyList()
                options.firstOrNull { lines ->
                    val block = maxOf(22f * look.conditions, lines.maxOf { it.length } * charW) + 10
                    if (fit != null) hoursFit(style, slots, temperature, tempSp, inner - block.dp, days.size, look.forecast, context, chance) == fit
                    else tempW + block + daysOnlyW <= inner.value
                }
            }
            // Top row: the small labels together, today's range on the left and "● AQI · place · source"
            // on the right (the invisible settings shortcut).
            val range = weather?.todayRange?.let { "${texts.today} $it" }
            val aqi = weather?.aqiValue
            val corner = corner((range?.length ?: 0) + 2)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) { range?.let { Label(it, sz(13, look.conditions), second) } }
                Box(
                    modifier = GlanceModifier.padding(start = 8.dp).opens(settingsIntent(context)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (aqi != null) {
                            Dot(dz(7, look.conditions), aqiColor(aqi, weather.aqiEuropean))
                            Spacer(GlanceModifier.width(6.dp))
                            Label("AQI $aqi" + if (corner.isNotEmpty()) " " else "", sz(13, look.conditions), second)
                        }
                        if (corner.isNotEmpty()) Label((if (aqi != null) "· " else "") + corner, sz(13, look.conditions), Palette.ink3P)
                        if (aqi == null && corner.isEmpty()) Spacer(GlanceModifier.width(48.dp).height(18.dp))
                    }
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            // Middle: the numbers. Temperature and conditions, then the hours and the days, each set
            // apart by a hairline in the list style.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Label(temperature, tempSp.sp, main)
                if (weather != null && conditionLines != null) {
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.padding(bottom = (tempSp * 0.16f).dp)) {
                        Icon(weather.icon, dz(22, look.conditions), second)
                        Spacer(GlanceModifier.height(4.dp))
                        conditionLines.forEach { Label(it, sz(14, look.conditions), second) }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (fit != null && slots != null) {
                    // Three hours, then the days that still fit beside them.
                    val lift = (tempSp * 0.16f).dp
                    val list = style == Settings.HOURS_LIST
                    if (list) Hairline(dz(ROW_H * 3, look.forecast), lift) else Spacer(GlanceModifier.width(8.dp))
                    if (list) HourListB(slots, main, lift, chance)
                    else HourColumnsB(slots, main, second, lift, fit > 0)
                    if (fit > 0) {
                        if (list) Hairline(dz(ROW_H * 3, look.forecast), lift)
                        Row(modifier = GlanceModifier.padding(bottom = lift), verticalAlignment = Alignment.Bottom) {
                            days.take(fit).forEach { ForecastDayB(it, main, second, locale, GlanceModifier.width(dz(DAY_W, look.forecast))) }
                        }
                    }
                } else if (days.isNotEmpty()) {
                    Spacer(GlanceModifier.width(8.dp))
                    Row(
                        modifier = GlanceModifier.width(min(196.dp * look.forecast, width * 0.5f)).padding(bottom = (tempSp * 0.16f).dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        days.forEach { ForecastDayB(it, main, second, locale, GlanceModifier.defaultWeight()) }
                    }
                }
            }
            // Bottom, only when there is something to say: the conditions (when not beside the
            // temperature) or a warning, the sentence, and the rain line with the next hour's bars.
            val warning = weather?.warning
            val outlook = weather?.outlook?.takeIf { style == Settings.HOURS_SENTENCE }
            val showRain = Settings.get(context).showRain
            val rain = weather?.rain?.takeIf { showRain && outlook == null }
            val bars = weather?.rainBars?.takeIf { showRain }
            val lines = mutableListOf<@Composable () -> Unit>()
            if (weather == null) {
                val t = if (state.haConfigured) texts.waitingWeather else texts.tapToSetUp
                lines += { Label(t, sz(14, look.conditions), second) }
            } else if (conditionLines == null || warning != null) {
                lines += {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conditionLines == null) ConditionRowB(weather, second, look.conditions)
                        if (conditionLines == null && warning != null) Spacer(GlanceModifier.width(8.dp))
                        warning?.let { WarningChip(it, sz(13, look.conditions)) }
                    }
                }
            }
            if (outlook != null) lines += { IconLine(R.drawable.ic_wx_clock, outlook, look.conditions) }
            if (rain != null || bars != null) lines += {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rain != null) IconLine(R.drawable.ic_wx_rain, rain, look.conditions)
                    if (rain != null && bars != null) Spacer(GlanceModifier.width(14.dp))
                    if (bars != null) RainBars(bars, look.conditions)
                }
            }
            lines.forEachIndexed { i, line ->
                Spacer(GlanceModifier.height(if (i == 0) 8.dp else 4.dp))
                line()
            }
            HourlyStrip(weather, main, second)
        }
    }
}

/** A forecast day's column width beside the hours (narrower than the days-only layout). */
private const val DAY_W = 56

/** One type scale for the forecast: labels (hours, weekdays) and values (every temperature). */
private const val LABEL_SP = 13
private const val VALUE_SP = 16

/** An hour row in the list, three of them as tall as a forecast day's column. */
private const val ROW_H = 30

/** The rain-chance column in the hour list ("80%"). */
private const val CHANCE_W = 48

/** One hour column in the "columns" style. */
private const val HOUR_W = 42

/** The hour list's width in the "list" style: "21h", icon, "-12°". */
private const val LIST_W = 76

/**
 * How many forecast days fit beside the hours, or null when the hours themselves don't fit (or are off).
 * Widths are estimated from the text: digits ≈ 0.56em, the degree sign ≈ 0.38em, scaled by the font size
 * setting, so a "-12°" or a large system font drops a day rather than clipping.
 */
private fun hoursFit(style: String, slots: List<HourState>?, temperature: String, tempSp: Float, inner: Dp, days: Int, k: Float, context: Context, chance: Boolean = false): Int? {
    if (slots == null || (style != Settings.HOURS_LIST && style != Settings.HOURS_COLUMNS)) return null
    val scale = context.resources.configuration.fontScale
    val tempW = temperature.sumOf { if (it == '°') 0.38 else 0.56 }.toFloat() * tempSp * scale
    val hoursW = if (style == Settings.HOURS_LIST) (LIST_W + if (chance) CHANCE_W else 0) * k * scale + 34 else 3 * HOUR_W * k * scale + 17
    val maxDays = if (style == Settings.HOURS_LIST) 3 else 2
    return (minOf(days, maxDays) downTo 0).firstOrNull { n -> tempW + 8 + hoursW + n * DAY_W * k * scale <= inner.value }
}

/** "List": three short rows (hour, icon, temperature) between the temperature and the days. */
@Composable
private fun HourListB(slots: List<HourState>, main: ColorProvider, lift: Dp, chance: Boolean) {
    val k = LocalLook.current.forecast
    // Three rows as tall as a forecast day's column, so the list fills the same height.
    Column(modifier = GlanceModifier.width(dz(LIST_W + if (chance) CHANCE_W else 0, k)).padding(bottom = lift)) {
        slots.forEach { h ->
            Row(modifier = GlanceModifier.height(dz(ROW_H, k)), verticalAlignment = Alignment.CenterVertically) {
                Label(h.label, sz(LABEL_SP, k), Palette.ink3P, modifier = GlanceModifier.width(dz(30, k)))
                Icon(h.icon, dz(16, k), if (h.conditionKey == "sunny") SunTint else Palette.ink2P)
                Spacer(GlanceModifier.width(8.dp))
                Label(h.temperature, sz(VALUE_SP, k), main, modifier = if (chance) GlanceModifier.width(dz(30, k)) else GlanceModifier)
                if (chance) {
                    Spacer(GlanceModifier.width(4.dp))
                    Label(h.rain.orEmpty(), sz(LABEL_SP, k), Palette.rainP)
                }
            }
        }
    }
}

/** "Columns": three small hour columns like the days, then a hairline before the days. */
@Composable
private fun HourColumnsB(slots: List<HourState>, main: ColorProvider, second: ColorProvider, lift: Dp, line: Boolean) {
    val k = LocalLook.current.forecast
    Row(modifier = GlanceModifier.padding(bottom = lift), verticalAlignment = Alignment.Bottom) {
        slots.forEach { h ->
            Column(modifier = GlanceModifier.width(dz(HOUR_W, k)), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(h.label, sz(LABEL_SP, k), Palette.ink3P)
                Spacer(GlanceModifier.height(9.dp))
                Icon(h.icon, dz(24, k), if (h.conditionKey == "sunny") SunTint else second)
                Spacer(GlanceModifier.height(9.dp))
                Label(h.temperature, sz(VALUE_SP, k), main)
            }
        }
        if (line) {
            Spacer(GlanceModifier.width(8.dp))
            Box(modifier = GlanceModifier.width(1.dp).height(dz(64, k)).background(Palette.hairP)) {}
            Spacer(GlanceModifier.width(8.dp))
        }
    }
}

/** Icon · condition · dot · AQI band dot · "AQI n". Nine children: within Glance's ten per row. */
/** A vertical hairline between the conditions, the hours and the days, with room on both sides. */
@Composable
private fun Hairline(height: Dp, lift: Dp) {
    // One child of the row (Glance drops children past ten in a row).
    Box(modifier = GlanceModifier.padding(start = 8.dp, end = 8.dp, bottom = lift)) {
        Box(modifier = GlanceModifier.width(1.dp).height(height).background(Palette.hairP)) {}
    }
}

/** The next hour's rain as four 15-minute bars over "now" and "1h". */
@Composable
private fun RainBars(bars: List<Float>, k: Float) {
    val full = 16 * k
    Column {
        Row(modifier = GlanceModifier.height(full.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEachIndexed { i, b ->
                if (i > 0) Spacer(GlanceModifier.width(3.dp))
                val h = if (b <= 0f) 2f else maxOf(3f, b * full)
                Box(modifier = GlanceModifier.width(dz(10, k)).height(h.dp).background(if (b <= 0f) Palette.hairP else Palette.rainP)) {}
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.width(dz(40, k) + 9.dp)) {
            Label(LocalTexts.current.nowWord, sz(10, k), Palette.ink3P)
            Spacer(GlanceModifier.defaultWeight())
            Label("1h", sz(10, k), Palette.ink3P)
        }
    }
}

/** A small icon and one line of text, as the rain line and the sentence. */
@Composable
private fun IconLine(@androidx.annotation.DrawableRes icon: Int, text: String, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, dz(16, k), Palette.ink2P)
        Spacer(GlanceModifier.width(6.dp))
        Label(text, sz(14, k), Palette.ink2P)
    }
}

@Composable
private fun ConditionRowB(weather: WeatherState, second: ColorProvider, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(weather.icon, dz(18, k), second)
        Spacer(GlanceModifier.width(6.dp))
        Label(weather.condition, sz(14, k), second)
    }
}

@Composable
private fun ForecastDayB(day: ForecastDayState, main: ColorProvider, second: ColorProvider, locale: Locale, modifier: GlanceModifier) {
    val k = LocalLook.current.forecast
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(titleCase(day.date.format(DateTimeFormatter.ofPattern("EEE", locale)), locale), sz(LABEL_SP, k), Palette.ink3P)
        Spacer(GlanceModifier.height(10.dp))
        Icon(day.icon, dz(32, k), if (day.conditionKey == "sunny") SunTint else second)
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Label(day.high, sz(VALUE_SP, k), main, weight = FontWeight.Medium)
            if (day.low != null) {
                Spacer(GlanceModifier.width(4.dp))
                Label(day.low, sz(VALUE_SP, k), Palette.ink3P)
            }
        }
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
