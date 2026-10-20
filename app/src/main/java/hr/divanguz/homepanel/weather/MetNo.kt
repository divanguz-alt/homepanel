package hr.divanguz.homepanel.weather

import hr.divanguz.homepanel.ha.DayForecast
import hr.divanguz.homepanel.ha.WeatherData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** MET Norway Locationforecast (yr.no): free, needs only an identifying User-Agent. */
object MetNo {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetch(lat: Double, lon: Double, european: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): WeatherData {
        val root = json.parseToJsonElement(
            WeatherHttp.get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f".format(Locale.US, lat, lon)),
        ) as? JsonObject ?: throw IOException("MET Norway: bad JSON")
        return parse(root, lat, lon, european, zone, OpenMeteo.aqi(lat, lon, european))
    }

    /** The Locationforecast response → WeatherData; separate from the network call so it can be tested. */
    internal fun parse(root: JsonObject, lat: Double, lon: Double, european: Boolean, zone: ZoneId, aqi: String?): WeatherData {
        val series = ((root["properties"] as? JsonObject)?.get("timeseries") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        if (series.isEmpty()) throw IOException("MET Norway: empty forecast")

        data class Point(val time: Instant, val temp: Double?, val symbol: String?)
        val points = series.mapNotNull { e ->
            val time = (e["time"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val data = e["data"] as? JsonObject
            val temp = (((data?.get("instant") as? JsonObject)?.get("details") as? JsonObject)?.get("air_temperature") as? JsonPrimitive)?.doubleOrNull
            fun symbol(block: String) = (((data?.get(block) as? JsonObject)?.get("summary") as? JsonObject)?.get("symbol_code") as? JsonPrimitive)?.contentOrNull
            Point(time, temp, symbol("next_1_hours") ?: symbol("next_6_hours"))
        }
        val now = points.first()
        val days = points.groupBy { it.time.atZone(zone).toLocalDate() }.map { (date, pts) ->
            val temps = pts.mapNotNull { it.temp }
            // The condition around midday describes the day best.
            val noon = pts.filter { it.symbol != null }.minByOrNull { abs(it.time.atZone(zone).hour - 13) }
            DayForecast(date.toString(), noon?.symbol?.let(::condition), temps.maxOrNull(), temps.minOrNull())
        }.sortedBy { it.date }
        val tomorrow = days.getOrNull(1)
        return WeatherData(
            condition = now.symbol?.let(::condition) ?: "cloudy",
            temperature = now.temp,
            tomorrowCondition = tomorrow?.condition,
            tomorrowHigh = tomorrow?.high,
            aqi = aqi,
            aqiEuropean = european,
            days = days,
            lat = lat,
            lon = lon,
        )
    }

    /** MET symbol codes ("partlycloudy_day", "lightrainshowers_night", …) → HA condition names. */
    fun condition(symbol: String): String {
        val night = symbol.endsWith("_night")
        val s = symbol.substringBefore('_')
        return when {
            s == "clearsky" -> if (night) "clear-night" else "sunny"
            s == "fair" || s == "partlycloudy" -> "partlycloudy"
            s == "cloudy" -> "cloudy"
            s == "fog" -> "fog"
            "thunder" in s -> "lightning-rainy"
            "sleet" in s -> "snowy-rainy"
            "snow" in s -> "snowy"
            "heavyrain" in s -> "pouring"
            "rain" in s -> "rainy"
            else -> "cloudy"
        }
    }
}
