package hr.divanguz.homepanel.widget

import androidx.annotation.DrawableRes
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.claude.ClaudeSnapshot
import hr.divanguz.homepanel.ha.HaSnapshot
import hr.divanguz.homepanel.ha.HourForecast
import hr.divanguz.homepanel.ha.splitAddress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Values older than this render in the tertiary color. */
private const val STALE_AFTER_MS = 60 * 60 * 1000L
/** Weather is fetched hourly, so it only counts as stale after two hours. */
private const val WEATHER_STALE_AFTER_MS = 2 * 60 * 60 * 1000L

data class WeatherState(
    val temperature: String,
    val conditionNow: String,
    /** Condition alone ("Cloudy"), for design B. */
    val condition: String,
    /** HA condition name ("sunny", …), for design B's sunny tint. */
    val conditionKey: String,
    /** Numeric AQI for design B's band dot; null when missing or non-numeric. */
    val aqiValue: Int?,
    /** [aqiValue] is the European index, which has its own bands. */
    val aqiEuropean: Boolean,
    val warning: hr.divanguz.homepanel.ha.WeatherWarning?,
    /** Provider that actually answered ("DHMZ", "Open-Meteo" after a fallback, …). */
    val source: String,
    /** "Rain from 14:20" / "Rain until 15:10"; null when no rain is coming. */
    val rain: String?,
    val tomorrow: String?,
    @param:DrawableRes val icon: Int,
    /** e.g. "AQI 42"; null when no AQI entity is set or it has no value. */
    val aqi: String?,
    /** The next three days after today; empty when HA gave no daily forecast. */
    val days: List<ForecastDayState>,
    /** The next six hours for the strip; empty when unknown or too old. */
    val hours: List<HourState> = emptyList(),
    /** Three upcoming hours [hourStep] apart (the next full hour first), for the compact styles. */
    val slots: List<HourState> = emptyList(),
    /** The same span in words: "Up to 8° at 15h, rain 14–19h, 4° at 21h"; null without hours. */
    val outlook: String? = null,
    /** Today's high and low, "↑7° ↓2°", for design B's top-left corner; null when unknown. */
    val todayRange: String? = null,
    /** Hour the current temperature was measured ("7h"), when the provider gives one; null otherwise. */
    val observed: String? = null,
    val stale: Boolean,
)

data class ForecastDayState(
    val date: LocalDate,
    val conditionKey: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    val high: String,
    val low: String?,
)

data class HourState(
    val label: String,
    @param:DrawableRes val icon: Int,
    val temperature: String,
    val rain: String?,
    val conditionKey: String = "",
)

data class CarState(
    val name: String,
    val locked: Boolean?,
    val area: String?,
    val address: String,
    val city: String,
    val range: String,
    /** "Since today 14:20"; null when HA gave no time. */
    val since: String? = null,
    val stale: Boolean,
)

data class UsageLine(val label: String, val percent: Int, val resets: String)

data class ClaudeState(
    /** Null until the first successful fetch. */
    val fiveHour: UsageLine?,
    val week: UsageLine?,
    /** Not signed in, or the session expired: the zone asks to sign in. */
    val needsLogin: Boolean,
    /** A tap-triggered refresh is running. */
    val busy: Boolean,
    val stale: Boolean,
    /** The last fetch failed (network, Cloudflare, endpoint change); the numbers are from before. */
    val failed: Boolean = false,
)

data class PanelState(
    val date: String,
    /** City the forecast is for. */
    val place: String?,
    /** Null until Home Assistant has answered at least once. */
    val weather: WeatherState?,
    val car: CarState?,
    val haConfigured: Boolean,
    val claude: ClaudeState,
)

fun panelState(
    ha: HaSnapshot?,
    haConfigured: Boolean,
    claude: ClaudeSnapshot?,
    claudeBusy: Boolean,
    carName: String = "",
    texts: Texts = Texts.EN,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = texts.locale,
    /** Hours between the compact styles' three slots: 1, 2 or 3. */
    hourStep: Int = 3,
): PanelState {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    fun stale(at: Long, after: Long = STALE_AFTER_MS) = nowMillis - at > after

    val dayFmt = DateTimeFormatter.ofPattern("EEE", locale)
    val weather = ha?.weather?.let { w ->
        val upcoming = w.days.filter { (runCatching { LocalDate.parse(it.date) }.getOrNull() ?: today) > today }
        val tomorrow = upcoming.firstOrNull { it.date == today.plusDays(1).toString() }
        // The chosen provider's hours, else Open-Meteo's from the nowcast (fetched every refresh).
        val hourly = w.hours.filter { it.time > nowMillis - 3_600_000L }.ifEmpty {
            ha.rain?.takeIf { nowMillis - it.fetchedAt < 3 * 3_600_000L }?.hours.orEmpty()
        }
        val slots = hourSlots(hourly, nowMillis, hourStep)
        WeatherState(
            temperature = w.temperature?.let { "${it.roundToInt()}°" } ?: "–",
            conditionNow = texts.now(texts.condition(w.condition)),
            condition = texts.condition(w.condition),
            conditionKey = w.condition,
            aqiValue = w.aqi?.toDoubleOrNull()?.roundToInt(),
            aqiEuropean = w.aqiEuropean,
            warning = w.warning,
            source = w.source,
            // The rain line follows the hours on screen: the chosen provider's own when it has them
            // (otherwise "Rain from 12:45" from Open-Meteo could sit above a dry 13h from DHMZ).
            rain = if (w.hours.isNotEmpty()) hourlyRainText(w.hours, nowMillis, zone, texts)
                else ha.rain?.let { rainText(it, nowMillis, zone, texts) },
            // Prefer the stored day list so the line stays right after midnight; older caches lack it.
            tomorrow = (tomorrow?.condition ?: w.tomorrowCondition.takeIf { w.days.isEmpty() })?.let { c ->
                val high = (tomorrow?.high ?: w.tomorrowHigh)?.let { ", ${it.roundToInt()}°" }.orEmpty()
                texts.tomorrow(texts.condition(c), high)
            },
            days = upcoming.take(3).map { d ->
                ForecastDayState(
                    date = LocalDate.parse(d.date),
                    conditionKey = d.condition.orEmpty(),
                    label = LocalDate.parse(d.date).format(dayFmt).replace(".", "").uppercase(locale),
                    icon = Conditions.icon(d.condition.orEmpty()),
                    high = d.high?.let { "${it.roundToInt()}°" } ?: "–",
                    low = d.low?.let { "${it.roundToInt()}°" },
                )
            },
            icon = Conditions.icon(w.condition),
            hours = hourly.filter { it.time > nowMillis - 3_600_000L / 2 }.take(6).map { hourState(it, zone) },
            slots = slots.map { hourState(it, zone, texts.hourLabel) },
            outlook = outlook(slots, hourly, texts, zone),
            observed = w.observedAt?.takeIf { nowMillis - it < 6 * 3_600_000L }?.let {
                texts.hourLabel(Instant.ofEpochMilli(it).atZone(zone).hour)
            },
            todayRange = w.days.firstOrNull { it.date == today.toString() }?.let { d ->
                if (d.high == null || d.low == null) null else "↑${d.high.roundToInt()}° ↓${d.low.roundToInt()}°"
            },
            aqi = w.aqi?.let { "AQI $it" },
            stale = stale(ha.weatherAt, WEATHER_STALE_AFTER_MS),
        )
    }
    val car = ha?.car?.let { c ->
        val (line1, line2) = c.address?.let(::splitAddress) ?: ("–" to "")
        CarState(
            name = carName.ifBlank { texts.car },
            locked = c.locked,
            area = c.area,
            address = line1,
            city = line2,
            range = c.range ?: "–",
            since = c.since?.let { sinceText(it, today, zone, texts) },
            stale = stale(ha.carAt),
        )
    }
    return PanelState(
        date = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)),
        place = ha?.weather?.place,
        weather = weather,
        car = car,
        haConfigured = haConfigured,
        claude = ClaudeState(
            fiveHour = claude?.fiveHour?.let { UsageLine(texts.fiveHours, it.roundToInt(), resetsIn(claude.fiveHourResetsAt, nowMillis, texts)) },
            week = claude?.week?.let { UsageLine(texts.week, it.roundToInt(), resetsIn(claude.weekResetsAt, nowMillis, texts)) },
            needsLogin = claude == null || claude.needsLogin,
            busy = claudeBusy,
            stale = claude != null && claude.hasData && stale(claude.fetchedAt),
            failed = claude != null && claude.hasData && !claude.needsLogin && claude.error != null,
        ),
    )
}

/** "Since today 14:20" / "Since yesterday 9:05" / "Since 3 Nov 18:40". */
internal fun sinceText(at: Long, today: LocalDate, zone: ZoneId, texts: Texts): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val day = when (t.toLocalDate()) {
        today -> texts.todayWord
        today.minusDays(1) -> texts.yesterdayWord
        else -> t.format(DateTimeFormatter.ofPattern(texts.sinceDate, texts.locale)).replace("..", ".")
    }
    return texts.since(day, t.format(DateTimeFormatter.ofPattern("HH:mm", texts.locale)))
}

/** Rain line for the current moment; absolute times so it stays right between refreshes. */
private fun rainText(r: hr.divanguz.homepanel.ha.RainNowcast, now: Long, zone: ZoneId, texts: Texts): String? {
    val start = r.startsAt ?: return null
    if (now - r.fetchedAt > 3 * 3_600_000L) return null
    val fmt = DateTimeFormatter.ofPattern("HH:mm", texts.locale)
    fun at(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(fmt)
    val end = r.endsAt
    return when {
        now < start -> texts.rainFrom(at(start))
        end != null && now < end -> texts.rainUntil(at(end))
        end == null -> texts.rainHours
        else -> null
    }
}

/** The rain line from hourly steps, within the next [RAIN_AHEAD_H] hours; an hour counts by its condition (as its icon). */
internal fun hourlyRainText(hours: List<HourForecast>, now: Long, zone: ZoneId, texts: Texts): String? {
    val hour = 3_600_000L
    val ahead = hours.sortedBy { it.time }.filter { it.time + hour > now && it.time < now + RAIN_AHEAD_H * hour }
    val first = ahead.indexOfFirst { it.condition in WET }
    if (first < 0) return null
    val fmt = DateTimeFormatter.ofPattern("HH:mm", texts.locale)
    fun at(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(fmt)
    val start = ahead[first]
    if (start.time > now) return texts.rainFrom(at(start.time))
    val rest = hours.sortedBy { it.time }.filter { it.time > start.time }
    val dry = rest.firstOrNull { it.condition !in WET } ?: return texts.rainHours
    return texts.rainUntil(at(dry.time))
}

private const val RAIN_AHEAD_H = 6

/** "resets in 3h 12m" / "resets in 2d 9h"; empty when the reset time is unknown. */
private fun resetsIn(at: Long, now: Long, texts: Texts): String {
    if (at <= 0) return ""
    var s = (at - now) / 1000
    if (s <= 0) return texts.resetsNow
    val d = s / 86_400; s %= 86_400
    val h = s / 3_600; s %= 3_600
    val m = s / 60
    val span = when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
    return texts.resetsIn(span)
}

/** Home Assistant weather conditions → text and icon. */
object Conditions {
    fun label(condition: String): String = when (condition) {
        "sunny" -> "Sunny"
        "clear-night" -> "Clear"
        "partlycloudy" -> "Partly cloudy"
        "cloudy" -> "Cloudy"
        "rainy" -> "Rain"
        "pouring" -> "Heavy rain"
        "snowy" -> "Snow"
        "snowy-rainy" -> "Sleet"
        "hail" -> "Hail"
        "fog" -> "Fog"
        "lightning", "lightning-rainy" -> "Thunderstorms"
        "windy", "windy-variant" -> "Windy"
        "exceptional" -> "Unusual weather"
        else -> condition.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    @DrawableRes
    fun icon(condition: String): Int = when (condition) {
        "sunny" -> R.drawable.ic_wx_sun
        "clear-night" -> R.drawable.ic_moon
        "partlycloudy" -> R.drawable.ic_wx_partly
        "rainy", "pouring", "hail" -> R.drawable.ic_wx_rain
        "snowy", "snowy-rainy" -> R.drawable.ic_wx_snow
        "fog" -> R.drawable.ic_wx_fog
        "lightning", "lightning-rainy" -> R.drawable.ic_wx_thunder
        "windy", "windy-variant" -> R.drawable.ic_wx_wind
        else -> R.drawable.ic_wx_cloud
    }
}

private fun hourState(h: HourForecast, zone: ZoneId, label: (Int) -> String = { it.toString() }) = HourState(
    label = label(Instant.ofEpochMilli(h.time).atZone(zone).hour),
    icon = Conditions.icon(h.condition),
    temperature = h.temperature?.let { "${it.roundToInt()}°" } ?: "–",
    // Only a chance worth noticing.
    rain = h.rainChance?.takeIf { it >= 20 }?.let { "$it%" },
    conditionKey = h.condition,
)

/** The next full hour, then two more [step] hours apart; fewer when the forecast doesn't reach. */
internal fun hourSlots(hours: List<HourForecast>, nowMillis: Long, step: Int): List<HourForecast> {
    val first = hours.firstOrNull { it.time > nowMillis } ?: return emptyList()
    val byTime = hours.associateBy { it.time }
    return (0 until 3).mapNotNull { byTime[first.time + it * step.coerceIn(1, 3) * 3_600_000L] }
}

private val WET = setOf("rainy", "pouring", "lightning-rainy", "snowy-rainy", "snowy", "hail")

/** A forecast hour that counts as rain (or snow) for the outlook line. */
internal fun HourForecast.wet() = condition in WET || (rainChance ?: 0) >= 50

/**
 * The slots' span in one line: the warmest hour when it is neither the first nor the last, when rain
 * (or snow) falls, and the temperature at the last slot. "Up to 8° at 15h, rain 14–19h, 4° at 21h".
 */
internal fun outlook(slots: List<HourForecast>, hours: List<HourForecast>, texts: Texts, zone: ZoneId): String? {
    if (slots.size < 2) return null
    val start = slots.first().time
    val end = slots.last().time
    val span = hours.filter { it.time in start..end }
    fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour
    fun deg(v: Double) = "${v.roundToInt()}°"
    val parts = mutableListOf<String>()
    val peak = span.filter { it.temperature != null }.maxByOrNull { it.temperature!! }
    val firstT = slots.first().temperature
    if (peak != null && peak.time != start && peak.time != end && firstT != null && peak.temperature!! - firstT >= 1.0) {
        parts += texts.upTo(deg(peak.temperature), hourOf(peak.time))
    }
    val wet = span.filter { it.wet() }
    if (wet.isNotEmpty()) {
        val snow = wet.count { it.condition == "snowy" } * 2 > wet.size
        val from = hourOf(wet.first().time)
        val until = hourOf(wet.last().time + 3_600_000L)
        parts += when {
            wet.first().time == start && wet.last().time == end -> texts.wetThrough(snow)
            wet.first().time == start -> texts.wetUntil(snow, until)
            wet.last().time == end -> texts.wetFrom(snow, from)
            else -> texts.wetBetween(snow, from, until)
        }
    }
    slots.last().temperature?.let { parts += texts.tempAt(deg(it), hourOf(end)) }
    return parts.joinToString(", ").replaceFirstChar { it.titlecase(texts.locale) }.ifEmpty { null }
}
