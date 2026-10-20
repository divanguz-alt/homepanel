package hr.divanguz.homepanel.weather

import android.content.Context
import android.location.Location
import hr.divanguz.homepanel.ha.DayForecast
import hr.divanguz.homepanel.ha.WeatherData
import hr.divanguz.homepanel.settings.Settings
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * DHMZ (meteo.hr), Croatia only. Current weather from the nearest observing station (vrijeme.hr), the
 * 7-day hourly forecast from meteo.hr's per-place feed. That feed is big, so it is cached on disk and
 * downloaded again only when its ETag changes. It has no coordinates, so the place is matched by the
 * names of the nearest stations (e.g. "Split-Marjan" ↔ "SPLIT-MARJAN").
 *
 * The forecast's weather symbol codes are undocumented; the day's condition is derived from the
 * forecast precipitation, with the low codes (cloud cover) only used on dry days.
 */
object Dhmz {
    private const val CURRENT_URL = "https://vrijeme.hr/hrvatska_n.xml"
    private const val FORECAST_URL = "https://meteo.hr/7d_graf_i_simboli.xml"
    private const val MAX_STATION_KM = 40f
    private val DATE = DateTimeFormatter.ofPattern("d.M.yyyy.")

    internal data class Station(val name: String, val lat: Double, val lon: Double, val temp: Double?, val text: String?, val night: Boolean)
    internal data class Hour(val date: LocalDate, val hour: Int, val temp: Double?, val symbol: Int?, val rain: Double)

    fun fetch(context: Context, lat: Double, lon: Double): WeatherData {
        val stations = stations(WeatherHttp.get(CURRENT_URL))
            .map { it to distanceKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= MAX_STATION_KM }
            .sortedBy { it.second }
            .map { it.first }
        val nearest = stations.firstOrNull { it.temp != null }
            ?: throw IOException("DHMZ: no station within ${MAX_STATION_KM.toInt()} km (outside Croatia?)")

        val days = runCatching { forecastDays(context, stations.map { it.name.uppercase() }) }.getOrDefault(emptyList())
        val tomorrow = days.firstOrNull { it.date == LocalDate.now().plusDays(1).toString() }
        return WeatherData(
            condition = currentCondition(nearest.text, nearest.night),
            temperature = nearest.temp,
            tomorrowCondition = tomorrow?.condition,
            tomorrowHigh = tomorrow?.high,
            aqi = OpenMeteo.aqi(lat, lon, Settings.get(context).aqiEuropean),
            aqiEuropean = Settings.get(context).aqiEuropean,
            warning = if (Settings.get(context).warnings) runCatching { DhmzWarnings.forPlace(context, lat, lon) }.getOrNull() else null,
            days = days,
            lat = lat,
            lon = lon,
        )
    }

    internal fun stations(xml: String): List<Station> {
        val out = mutableListOf<Station>()
        val p = XmlParsers.factory().apply { setInput(StringReader(xml)) }
        var name: String? = null; var la: Double? = null; var lo: Double? = null
        var temp: Double? = null; var text: String? = null; var sign = ""
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "Grad") { name = null; la = null; lo = null; temp = null; text = null; sign = "" }
                }
                XmlPullParser.TEXT -> {
                    val v = p.text.trim()
                    when (tag) {
                        "GradIme" -> name = v
                        "Lat" -> la = v.toDoubleOrNull()
                        "Lon" -> lo = v.toDoubleOrNull()
                        "Temp" -> temp = v.toDoubleOrNull()
                        "Vrijeme" -> text = v.ifBlank { null }
                        "VrijemeZnak" -> sign = v
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "Grad" && name != null && la != null && lo != null) {
                        out += Station(name!!, la!!, lo!!, temp, text, sign.endsWith("n"))
                    }
                    tag = ""
                }
            }
        }
        return out
    }

    /** Daily high/low/condition for the first of [names] present in the forecast feed. */
    private fun forecastDays(context: Context, names: List<String>): List<DayForecast> {
        return forecastFile(context).reader().use { parseForecast(it, names, LocalDate.now()) }
    }

    /** The 7-day feed → days for the first of [names] it contains; separate from the file so it can be tested. */
    internal fun parseForecast(reader: java.io.Reader, names: List<String>, today: LocalDate): List<DayForecast> {
        val wanted = names.toSet()
        val found = mutableMapOf<String, MutableList<Hour>>()
        reader.let { reader ->
            val p = XmlParsers.factory().apply { setInput(reader) }
            var place: String? = null
            var date: LocalDate? = null; var hour = 0
            var temp: Double? = null; var symbol: Int? = null; var rain = 0.0
            var tag = ""
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        tag = p.name
                        when (tag) {
                            "grad" -> place = p.getAttributeValue(null, "ime")?.uppercase()?.takeIf { it in wanted }
                            "dan" -> if (place != null) {
                                date = runCatching { LocalDate.parse(p.getAttributeValue(null, "datum"), DATE) }.getOrNull()
                                hour = p.getAttributeValue(null, "sat")?.toIntOrNull() ?: 0
                                temp = null; symbol = null; rain = 0.0
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (place != null) {
                        val v = p.text.trim()
                        when (tag) {
                            "t_2m" -> temp = v.toDoubleOrNull()
                            "simbol" -> symbol = v.trimEnd('n').toIntOrNull()
                            "oborina" -> rain = v.toDoubleOrNull() ?: 0.0
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (p.name == "dan" && place != null && date != null) {
                            found.getOrPut(place!!) { mutableListOf() } += Hour(date!!, hour, temp, symbol, rain)
                        }
                        tag = ""
                    }
                }
            }
        }
        val hours = names.firstNotNullOfOrNull { found[it] } ?: return emptyList()
        return hours.filter { it.date >= today }.groupBy { it.date }.toSortedMap().map { (date, hs) ->
            val temps = hs.mapNotNull { it.temp }
            DayForecast(date.toString(), dayCondition(hs), temps.maxOrNull(), temps.minOrNull())
        }
    }

    /** The cached forecast file, refreshed with If-None-Match so unchanged data isn't downloaded. */
    private fun forecastFile(context: Context): File {
        val file = File(context.cacheDir, "dhmz_7d.xml")
        val prefs = context.getSharedPreferences("homepanel_cache", Context.MODE_PRIVATE)
        val etag = prefs.getString("dhmz_etag", null)?.takeIf { file.exists() }
        val req = Request.Builder().url(FORECAST_URL).header("User-Agent", WeatherHttp.USER_AGENT)
            .apply { etag?.let { header("If-None-Match", it) } }.build()
        runCatching {
            WeatherHttp.client.newCall(req).execute().use { resp ->
                if (resp.code == 200) {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.outputStream().use { resp.body.byteStream().copyTo(it) }
                    if (tmp.renameTo(file)) prefs.edit().putString("dhmz_etag", resp.header("ETag")).apply()
                }
            }
        }
        if (!file.exists()) throw IOException("DHMZ forecast unavailable")
        return file
    }

    internal fun dayCondition(hours: List<Hour>): String {
        val rain = hours.sumOf { it.rain }
        val warmest = hours.mapNotNull { it.temp }.maxOrNull() ?: 10.0
        val thunder = hours.any { it.symbol == 30 || it.symbol == 31 }
        if (rain >= 1.0) return when {
            thunder -> "lightning-rainy"
            warmest <= 1.0 -> "snowy"
            rain >= 15.0 -> "pouring"
            else -> "rainy"
        }
        // Dry day: cloud cover from the codes around midday.
        val midday = hours.filter { it.hour in 10..16 }.mapNotNull { it.symbol }.ifEmpty { hours.mapNotNull { it.symbol } }
        val typical = midday.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return when (typical) {
            1 -> "sunny"
            2, 3 -> "partlycloudy"
            15 -> "fog"
            else -> "cloudy"
        }
    }

    /** DHMZ's Croatian observation text ("umjereno oblačno", "slaba kiša", …) → HA condition names. */
    internal fun currentCondition(text: String?, night: Boolean): String {
        val t = text?.lowercase() ?: return "cloudy"
        return when {
            "grmlj" in t -> "lightning-rainy"
            "susnjež" in t -> "snowy-rainy"
            "snijeg" in t -> "snowy"
            "jaka kiša" in t || "pljusak" in t && "jak" in t -> "pouring"
            "kiša" in t || "pljus" in t || "rosulja" in t -> "rainy"
            "magl" in t -> "fog"
            "vedro" in t -> if (night) "clear-night" else "sunny"
            "malo oblačno" in t || "djelomično" in t || "umjereno oblačno" in t -> "partlycloudy"
            "oblačno" in t -> "cloudy"
            else -> "cloudy"
        }
    }

    private fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, out)
        return out[0] / 1000f
    }
}
