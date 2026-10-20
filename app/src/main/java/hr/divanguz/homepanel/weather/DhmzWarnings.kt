package hr.divanguz.homepanel.weather

import android.content.Context
import android.location.Geocoder
import hr.divanguz.homepanel.ha.WeatherWarning
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * DHMZ's official warnings (MeteoAlarm CAP feeds for today and tomorrow). Areas are counties named in
 * Croatian ("Splitsko-dalmatinska", "Primorsko-goranska"), without coordinates, so the phone's position is
 * reverse-geocoded in Croatian and matched by county name. Only DHMZ publishes these, so the widget
 * shows warnings only while DHMZ is the weather source.
 */
object DhmzWarnings {
    private val FEEDS = listOf(
        "https://meteo.hr/upozorenja/cap_hr_today.xml" to false,
        "https://meteo.hr/upozorenja/cap_hr_tomorrow.xml" to true,
    )
    // CAP times here look like "2026-10-19 09:49:29+02:00".
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX")

    internal data class Alert(val area: String, val level: Int, val type: String, val expires: OffsetDateTime?)

    /** The strongest warning for the county at [lat]/[lon] that hasn't expired; null if none. */
    fun forPlace(context: Context, lat: Double, lon: Double): WeatherWarning? {
        val county = county(context, lat, lon) ?: return null
        val now = OffsetDateTime.now()
        return FEEDS.flatMap { (url, tomorrow) ->
            runCatching { parse(WeatherHttp.get(url)) }.getOrDefault(emptyList()).map { it to tomorrow }
        }
            .filter { (a, _) -> matches(county, a.area) && (a.expires == null || a.expires.isAfter(now)) }
            // Strongest first; today before tomorrow at the same level.
            .sortedWith(compareByDescending<Pair<Alert, Boolean>> { it.first.level }.thenBy { it.second })
            .firstOrNull()
            ?.let { (a, tomorrow) -> WeatherWarning(a.level, a.type, tomorrow) }
    }

    @Suppress("DEPRECATION") // runs on a worker thread; the listener overload needs API 33
    private fun county(context: Context, lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale.forLanguageTag("hr")).getFromLocation(lat, lon, 1)?.firstOrNull()?.adminArea
    }.getOrNull()

    /** "Primorsko-goranska županija" ↔ "Primorsko-goranska", "Istarska županija" ↔ "Istarska". */
    internal fun matches(county: String, area: String): Boolean {
        val c = county.lowercase().replace(" županija", "").trim()
        val a = area.lowercase().trim()
        return c == a || c.startsWith(a) || a.startsWith(c)
    }

    /** English-language <info> blocks: area, awareness level ("2; yellow; …") and type ("1; Wind"). */
    internal fun parse(xml: String): List<Alert> {
        val out = mutableListOf<Alert>()
        val p = XmlParsers.factory().apply { setInput(StringReader(xml)) }
        var lang = ""; var expires: OffsetDateTime? = null
        var level = 0; var type = ""; var valueName = ""
        val areas = mutableListOf<String>()
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "info") { lang = ""; expires = null; level = 0; type = ""; areas.clear() }
                }
                XmlPullParser.TEXT -> {
                    val v = p.text.trim()
                    if (v.isEmpty()) continue
                    when (tag) {
                        "language" -> lang = v
                        "expires" -> expires = runCatching { OffsetDateTime.parse(v, TIME) }.getOrNull()
                        "valueName" -> valueName = v
                        "value" -> when (valueName) {
                            "awareness_level" -> level = v.substringBefore(';').trim().toIntOrNull() ?: 0
                            "awareness_type" -> type = v.substringAfter(';').trim()
                                .replace('-', ' ').replaceFirstChar { it.uppercase() }
                        }
                        "areaDesc" -> areas += v
                    }
                }
                XmlPullParser.END_TAG -> {
                    // Both languages describe the same alert; the Croatian block is kept (county names).
                    if (p.name == "info" && lang == "hr" && level >= 2) {
                        areas.forEach { out += Alert(it, level, type, expires) }
                    }
                    tag = ""
                }
            }
        }
        return out
    }
}
