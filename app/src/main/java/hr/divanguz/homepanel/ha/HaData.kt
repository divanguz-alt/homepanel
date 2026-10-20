package hr.divanguz.homepanel.ha

import hr.divanguz.homepanel.settings.HaSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@Serializable
data class WeatherData(
    val condition: String,
    val temperature: Double?,
    val tomorrowCondition: String?,
    val tomorrowHigh: Double?,
    val aqi: String? = null,
    /** Daily forecast from today on; the widget picks the days after "today" at render time. */
    val days: List<DayForecast> = emptyList(),
    /** Where this forecast is for (the phone's location, or HA home when that's unknown). */
    val lat: Double? = null,
    val lon: Double? = null,
    /** City the forecast is for, shown under the date. */
    val place: String? = null,
    val source: String = "Home Assistant",
    /** [aqi] is the European index (0–100+) rather than US AQI. */
    val aqiEuropean: Boolean = false,
    /** Strongest official warning for this place today/tomorrow, if the provider publishes them. */
    val warning: WeatherWarning? = null,
)

/**
 * Rain in the next few hours from Open-Meteo's 15-minute forecast, as absolute times so the text
 * stays right between refreshes: [startsAt] is the first wet quarter-hour (may be the current one),
 * [endsAt] the first dry one after it (null if it rains through the whole window).
 */
@Serializable
data class RainNowcast(
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val fetchedAt: Long = 0,
)

@Serializable
data class WeatherWarning(
    /** 2 yellow, 3 orange, 4 red (MeteoAlarm awareness levels). */
    val level: Int,
    /** "Wind", "Fog", "Thunderstorm", … */
    val type: String,
    /** Starts tomorrow rather than today. */
    val tomorrow: Boolean,
)

@Serializable
data class DayForecast(
    /** ISO local date, e.g. 2026-10-20. */
    val date: String,
    val condition: String?,
    val high: Double?,
    val low: Double?,
)

@Serializable
data class CarData(
    val address: String?,
    val area: String?,
    val range: String?,
    /** True locked, false unlocked, null when no lock entity is known. */
    val locked: Boolean? = null,
)

/** Last good values. Each part keeps its own timestamp so one failing entity doesn't age the other. */
@Serializable
data class HaSnapshot(
    val weather: WeatherData? = null,
    val weatherAt: Long = 0,
    val car: CarData? = null,
    val carAt: Long = 0,
    /** Version of the cached render; changing it makes the widget redraw with the new image. */
    val carImageVersion: String? = null,
    val rain: RainNowcast? = null,
    /** Version of the cached map; changes only when HA reports a new parked address. */
    val carMapVersion: String? = null,
    val error: String? = null,
)

private val UNUSABLE = setOf("unknown", "unavailable", "none", "")

private fun JsonObject.stateValue(): String? =
    this["state"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.lowercase() in UNUSABLE }

private fun JsonObject.attr(name: String): JsonElement? = (this["attributes"] as? JsonObject)?.get(name)

private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun JsonElement?.num(): Double? = (this as? kotlinx.serialization.json.JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

object HaFetch {

    fun weather(client: HaClient, s: HaSettings, zone: ZoneId = ZoneId.systemDefault()): WeatherData {
        val st = client.state(s.weatherEntity) ?: throw HaException("${s.weatherEntity} not found")
        val condition = st.stateValue() ?: throw HaException("${s.weatherEntity} is unavailable")
        // The forecast is a nice-to-have: a failing forecast call still yields current weather.
        val days = runCatching { dailyDays(client.dailyForecast(s.weatherEntity), s.weatherEntity, zone) }
            .getOrDefault(emptyList())
        val tomorrow = LocalDate.now(zone).plusDays(1).toString().let { t -> days.firstOrNull { it.date == t } }
        val aqi = s.aqiEntity.takeIf { it.isNotBlank() }
            ?.let { id -> runCatching { client.state(id)?.let(::formatAqi) }.getOrNull() }
        return WeatherData(
            aqi = aqi,
            condition = condition,
            temperature = st.attr("temperature").num(),
            tomorrowCondition = tomorrow?.condition,
            tomorrowHigh = tomorrow?.high,
            days = days,
        )
    }

    private fun formatAqi(st: JsonObject): String? {
        val raw = st.stateValue() ?: return null
        return raw.toDoubleOrNull()?.roundToInt()?.toString() ?: raw
    }

    /** First entity with device_class aqi and a usable state, sensors first. */
    fun detectAqi(client: HaClient): String? = client.allStates()
        .filter { it.attr("device_class").str() == "aqi" && it.stateValue() != null }
        .sortedBy { if (it["entity_id"].str().orEmpty().startsWith("sensor.")) 0 else 1 }
        .firstNotNullOfOrNull { it["entity_id"].str() }

    private fun dailyDays(response: JsonElement?, entityId: String, zone: ZoneId): List<DayForecast> {
        val root = response as? JsonObject ?: return emptyList()
        val byEntity = (root["service_response"] as? JsonObject ?: root)[entityId] as? JsonObject ?: return emptyList()
        val items = (byEntity["forecast"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return emptyList()
        fun dateOf(o: JsonObject): LocalDate? = o["datetime"].str()?.let { dt ->
            runCatching { OffsetDateTime.parse(dt).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(dt.take(10)) }.getOrNull()
        }
        val today = LocalDate.now(zone)
        return items.mapNotNull { o ->
            val date = dateOf(o)?.takeIf { it >= today } ?: return@mapNotNull null
            DayForecast(date.toString(), o["condition"].str(), o["temperature"].num(), o["templow"].num())
        }.distinctBy { it.date }
    }

    /** Latitude/longitude attributes of an entity, if it has both. */
    fun coordinates(client: HaClient, entityId: String): Pair<Double, Double>? {
        if (entityId.isBlank()) return null
        val attrs = client.state(entityId)?.get("attributes") as? JsonObject ?: return null
        val lat = attrs["latitude"].num() ?: return null
        val lon = attrs["longitude"].num() ?: return null
        return lat to lon
    }

    /** HA's home location from /api/config. */
    fun home(client: HaClient): Pair<Double, Double>? {
        val cfg = client.config() ?: return null
        return (cfg["latitude"].num() ?: return null) to (cfg["longitude"].num() ?: return null)
    }

    /**
     * The phone's GPS tracker reported by the HA companion app: the most recently updated
     * device_tracker with source_type gps and coordinates that isn't one of the car's entities.
     */
    fun detectPhone(client: HaClient, s: HaSettings): String? = trackerCandidates(client, s)
        // Tablets and other devices that stay at home report GPS too; prefer phone-like names.
        .filter { c -> listOf("tab", "ipad", "tv", "watch").none { c.entityId.contains(it) || c.name.lowercase().contains(it) } }
        .firstOrNull { it.gps }
        ?.entityId

    data class TrackerCandidate(val entityId: String, val name: String, val gps: Boolean, val lastUpdated: String)

    /** Every device_tracker with coordinates except the car's: GPS ones first, most recently updated first. */
    fun trackerCandidates(client: HaClient, s: HaSettings): List<TrackerCandidate> = client.allStates()
        .mapNotNull { st ->
            val id = st["entity_id"].str().orEmpty()
            if (!id.startsWith("device_tracker.") || id == s.locationEntity) return@mapNotNull null
            if (st.attr("latitude").num() == null || st.attr("longitude").num() == null) return@mapNotNull null
            TrackerCandidate(
                entityId = id,
                name = st.attr("friendly_name").str() ?: id,
                gps = st.attr("source_type").str() == "gps",
                lastUpdated = st["last_updated"].str().orEmpty(),
            )
        }
        .sortedWith(compareByDescending<TrackerCandidate> { it.gps }.thenByDescending { it.lastUpdated })

    fun car(client: HaClient, s: HaSettings): CarData {
        val parked = s.carAddressEntity.takeIf { it.isNotBlank() }?.let { client.state(it) }
        val range = listOf(s.rangeEntity, s.rangeFallbackEntity)
            .filter { it.isNotBlank() }
            .firstNotNullOfOrNull { id -> client.state(id)?.let(::formatRange) }
        if (parked == null && range == null) throw HaException("Car entities not found")
        val locked = runCatching { lockState(client, s.lockEntity) }.getOrNull()
        return CarData(
            locked = locked,
            address = parked?.stateValue(),
            area = parked?.attr("area").str()?.takeIf { it.isNotBlank() },
            range = range,
        )
    }

    /**
     * true = locked. lock.* and text sensors say "locked"/"unlocked"; a binary_sensor with
     * device_class lock is "on" when unlocked (HA's convention).
     */
    private fun lockState(client: HaClient, entityId: String): Boolean? {
        if (entityId.isBlank() || entityId == "none") return null
        val v = client.state(entityId)?.stateValue()?.lowercase() ?: return null
        return when (v) {
            "locked" -> true
            "unlocked", "open", "opening", "unlocking" -> false
            "off" -> if (entityId.startsWith("binary_sensor.")) true else null
            "on" -> if (entityId.startsWith("binary_sensor.")) false else null
            else -> null
        }
    }

    data class EntityOption(val entityId: String, val name: String, val state: String)

    /** Entities of the given domains ("sensor", "image", …) for pickers in settings. */
    fun entities(client: HaClient, domains: List<String>): List<EntityOption> = client.allStates()
        .mapNotNull { st ->
            val id = st["entity_id"].str().orEmpty()
            if (domains.none { id.startsWith("$it.") }) null
            else EntityOption(id, st.attr("friendly_name").str() ?: id, st["state"].str().orEmpty())
        }
        .sortedBy { it.name.lowercase() }

    data class LockCandidate(val entityId: String, val name: String, val state: String)

    /** Anything in HA that looks like a lock, the car's first. */
    fun lockCandidates(client: HaClient): List<LockCandidate> = client.allStates()
        .mapNotNull { st ->
            val id = st["entity_id"].str().orEmpty()
            val lockish = id.startsWith("lock.") ||
                (id.startsWith("binary_sensor.") && (st.attr("device_class").str() == "lock" || "lock" in id)) ||
                (id.startsWith("sensor.") && "lock" in id)
            if (!lockish) null
            else LockCandidate(id, st.attr("friendly_name").str() ?: id, st["state"].str().orEmpty())
        }
        // Real locks first; the user picks the car's one (nothing here knows which car it is).
        .sortedBy { when { it.entityId.startsWith("lock.") -> 0; it.entityId.startsWith("binary_sensor.") -> 1; else -> 2 } }

    private fun formatRange(st: JsonObject): String? {
        val raw = st.stateValue() ?: return null
        val unit = st.attr("unit_of_measurement").str().orEmpty()
        val value = raw.toDoubleOrNull()?.roundToInt()?.toString() ?: raw
        return if (unit.isBlank()) value else "$value $unit"
    }
}

/** "Main Street 1, 10000 Springfield, Country" → ("Main Street 1", "Springfield"). Unknown formats pass through. */
fun splitAddress(address: String): Pair<String, String> {
    val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size <= 1) return address.trim() to ""
    val rest = if (parts.size >= 3) parts.subList(1, parts.size - 1) else parts.drop(1) // drop the country
    val second = rest.joinToString(", ") { it.replace(Regex("^\\d{4,6}\\s+"), "") }
    return parts[0] to second
}

