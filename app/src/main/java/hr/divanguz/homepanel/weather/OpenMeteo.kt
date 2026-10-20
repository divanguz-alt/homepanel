package hr.divanguz.homepanel.weather

import hr.divanguz.homepanel.ha.DayForecast
import hr.divanguz.homepanel.ha.RainNowcast
import hr.divanguz.homepanel.ha.WeatherData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/** Weather and air quality for any coordinates from Open-Meteo (free, no key). */
object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    /** [model] picks one weather model (e.g. "icon_seamless"); null lets Open-Meteo choose the best. */
    fun fetch(lat: Double, lon: Double, model: String? = null, european: Boolean = false): WeatherData {
        val forecast = get(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5" +
                model?.let { "&models=$it" }.orEmpty(),
        )
        return parseForecast(forecast, lat, lon, european, aqi(lat, lon, european))
    }

    /** The forecast response → WeatherData; separate from the network call so it can be tested. */
    internal fun parseForecast(forecast: JsonObject, lat: Double, lon: Double, european: Boolean, aqi: String?): WeatherData {
        val current = forecast["current"] as? JsonObject ?: throw IOException("Open-Meteo: no current weather")
        val isDay = current.num("is_day")?.toInt() != 0
        val condition = condition(current.num("weather_code")?.toInt(), isDay)

        val daily = forecast["daily"] as? JsonObject
        val dates = daily.list("time").map { (it as? JsonPrimitive)?.contentOrNull }
        val codes = daily.list("weather_code")
        val highs = daily.list("temperature_2m_max")
        val lows = daily.list("temperature_2m_min")
        val days = dates.mapIndexedNotNull { i, date ->
            date ?: return@mapIndexedNotNull null
            DayForecast(
                date = date,
                condition = condition((codes.getOrNull(i) as? JsonPrimitive)?.intOrNull, isDay = true),
                high = (highs.getOrNull(i) as? JsonPrimitive)?.doubleOrNull,
                low = (lows.getOrNull(i) as? JsonPrimitive)?.doubleOrNull,
            )
        }
        val tomorrow = days.getOrNull(1)

        return WeatherData(
            condition = condition,
            temperature = current.num("temperature_2m"),
            tomorrowCondition = tomorrow?.condition,
            tomorrowHigh = tomorrow?.high,
            aqi = aqi,
            days = days,
            lat = lat,
            lon = lon,
            source = "Open-Meteo",
            aqiEuropean = european,
        )
    }

    /** Quarter-hours with more than this much precipitation (mm) count as rain (≈0.4 mm/h). */
    private const val WET_MM = 0.1

    /** Rain start/end within the next 3 hours, from the 15-minute forecast. */
    fun nowcast(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): RainNowcast {
        val root = get(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
                "&minutely_15=precipitation&past_minutely_15=1&forecast_minutely_15=12&timeformat=unixtime",
        )
        return parseNowcast(root, now)
    }

    internal fun parseNowcast(root: JsonObject, now: Long): RainNowcast {
        val m = root["minutely_15"] as? JsonObject ?: return RainNowcast(fetchedAt = now)
        val times = (m["time"] as? JsonArray).orEmpty().map { ((it as? JsonPrimitive)?.doubleOrNull ?: 0.0).toLong() * 1000 }
        val rain = (m["precipitation"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
        // Each value is the total of the 15 minutes *ending* at its time, so a slot covers (t-15, t].
        // Keep the slots that haven't finished yet, starting with the one we're in.
        val quarter = 15 * 60_000L
        val slots = times.zip(rain).filter { (t, _) -> t > now }
        val first = slots.indexOfFirst { it.second > WET_MM }
        if (first < 0) return RainNowcast(fetchedAt = now)
        val dryAfter = slots.drop(first).indexOfFirst { it.second <= WET_MM }
        return RainNowcast(
            startsAt = slots[first].first - quarter,
            endsAt = if (dryAfter < 0) null else slots[first + dryAfter].first - quarter,
            fetchedAt = now,
        )
    }

    /** US AQI, or European AQI when [european], for the coordinates; null when unavailable. Used by every provider. */
    fun aqi(lat: Double, lon: Double, european: Boolean = false): String? = runCatching {
        val index = if (european) "european_aqi" else "us_aqi"
        val air = get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=$index"
            .format(Locale.US, lat, lon))
        (air["current"] as? JsonObject)?.num(index)?.roundToInt()?.toString()
    }.getOrNull()

    /** WMO weather code → the Home Assistant condition names the widget already understands. */
    fun condition(code: Int?, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "sunny" else "clear-night"
        2 -> "partlycloudy"
        3 -> "cloudy"
        45, 48 -> "fog"
        51, 53, 55, 56, 57, 61, 63, 80, 81 -> "rainy"
        65, 82 -> "pouring"
        66, 67 -> "snowy-rainy"
        71, 73, 75, 77, 85, 86 -> "snowy"
        95 -> "lightning"
        96, 99 -> "lightning-rainy"
        else -> "cloudy"
    }

    private fun get(url: String): JsonObject =
        json.parseToJsonElement(WeatherHttp.get(url)) as? JsonObject ?: throw IOException("Open-Meteo: bad JSON")

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject?.list(key: String): List<Any?> = (this?.get(key) as? JsonArray).orEmpty()
}
