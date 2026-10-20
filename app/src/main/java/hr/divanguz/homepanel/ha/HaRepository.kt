package hr.divanguz.homepanel.ha

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import hr.divanguz.homepanel.settings.HaSettings
import hr.divanguz.homepanel.weather.OpenMeteo
import hr.divanguz.homepanel.weather.WeatherProvider
import hr.divanguz.homepanel.settings.Settings
import kotlinx.serialization.json.Json
import java.util.Locale

/** Fetches from Home Assistant (and Open-Meteo for weather) and keeps the last good values on disk (not secret, plain prefs). */
object HaRepository {
    private const val PREFS = "homepanel_cache"
    private const val KEY = "ha"
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(context: Context): HaSnapshot =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<HaSnapshot>(it) }.getOrNull() }
            ?: HaSnapshot()

    private fun save(context: Context, snapshot: HaSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(HaSnapshot.serializer(), snapshot))
            .apply()
    }


    /**
     * Fetches what it can; parts that fail keep their previous value and timestamp. Weather is fetched
     * at most hourly unless [forceWeather] or the phone moved more than 3 km since the last forecast.
     */
    fun refresh(context: Context, forceWeather: Boolean = false): HaSnapshot {
        val old = cached(context)
        val settings = Settings.get(context).ha()
        if (!settings.isConfigured) return old.copy(error = "Home Assistant not set up").also { save(context, it) }

        val client = HaClient(settings)
        val now = System.currentTimeMillis()
        val weather = fetchWeather(context, client, settings, old, now, forceWeather)
        val car = runCatching { HaFetch.car(client, Settings.get(context).ha()) }
        val image = runCatching { CarImage.refresh(context, client, settings) }
            .getOrElse { "failed: ${it.message}" }
        Log.i("HomePanel", "Car image: $image")
        // Address from this fetch, or the last known one when the car call failed.
        val address = car.getOrNull()?.address ?: old.car?.address
        val map = runCatching { CarMap.refresh(context, client, settings, address) }
            .getOrElse { "failed: ${it.message}" }
        Log.i("HomePanel", "Car map: $map")
        val error = listOfNotNull(weather.exceptionOrNull(), car.exceptionOrNull())
            .firstOrNull()?.let { it.message ?: it.javaClass.simpleName }
        if (error != null) Log.w("HomePanel", "HA refresh: $error")

        val next = HaSnapshot(
            weather = weather.getOrNull() ?: old.weather,
            weatherAt = if (weather.isSuccess && weather.getOrNull() !== old.weather) now else old.weatherAt,
            car = car.getOrNull() ?: old.car,
            carAt = if (car.isSuccess) now else old.carAt,
            carImageVersion = CarImage.version(context),
            carMapVersion = CarMap.version(context),
            rain = nowcast(client, weather.getOrNull() ?: old.weather) ?: old.rain,
            error = error,
        )
        save(context, next)
        return next
    }

    /**
     * Weather from the provider chosen in settings, for where the phone is (its HA companion tracker),
     * or for HA home when that's unknown. Falls back to Open-Meteo, then the HA weather entity, when the
     * chosen provider fails (DHMZ outside Croatia, for one). Returns the old value unchanged (same
     * instance) when no fetch is due.
     */
    private fun fetchWeather(
        context: Context,
        client: HaClient,
        s: HaSettings,
        old: HaSnapshot,
        now: Long,
        force: Boolean,
    ): Result<WeatherData> {
        val prefs = Settings.get(context)
        val provider = prefs.weatherProvider
        val everyMs = prefs.weatherEveryMin * 60_000L
        val movedMeters = prefs.weatherMoveKm * 1000f
        val phone = runCatching { HaFetch.coordinates(client, s.phoneEntity) }.getOrNull()
        val moved = phone != null && old.weather?.lat != null && old.weather.lon != null &&
            distance(phone, old.weather.lat to old.weather.lon) > movedMeters
        val due = force || old.weather == null || now - old.weatherAt >= everyMs || moved ||
            old.weather.source != provider.label
        if (!due) return Result.success(old.weather!!)

        val home = runCatching { HaFetch.home(client) }.getOrNull()
        val here = phone ?: home
        if (provider != WeatherProvider.HOME_ASSISTANT && here != null) {
            val chosen = runCatching { provider.fetch(context, here.first, here.second) }
            val result = chosen.recoverCatching {
                Log.w("HomePanel", "${provider.label} failed (${it.message}), falling back to Open-Meteo")
                WeatherProvider.OPEN_METEO.fetch(context, here.first, here.second)
            }
            result.getOrNull()?.let { w -> return Result.success(w.copy(place = placeName(context, here))) }
        }
        // HA's weather entity forecasts for HA home, so name that place.
        return runCatching {
            HaFetch.weather(client, s).copy(place = home?.let { placeName(context, it) }, source = WeatherProvider.HOME_ASSISTANT.label)
        }
    }

    /** Current weather from every provider at the given place, for comparing them in settings. */
    fun compare(context: Context, client: HaClient?, s: HaSettings, lat: Double, lon: Double): List<Pair<WeatherProvider, Result<WeatherData>>> =
        WeatherProvider.entries.map { p ->
            p to if (p == WeatherProvider.HOME_ASSISTANT) {
                if (client == null) Result.failure(IllegalStateException("Home Assistant not set up"))
                else runCatching { HaFetch.weather(client, s) }
            } else runCatching { p.fetch(context, lat, lon) }
        }

    /** Where to compare weather: the phone (via HA), else HA home. */
    fun comparePlace(client: HaClient, s: HaSettings): Pair<Double, Double>? =
        runCatching { HaFetch.coordinates(client, s.phoneEntity) }.getOrNull() ?: runCatching { HaFetch.home(client) }.getOrNull()

    /** Rain in the next hours where the forecast is for (or HA home); every refresh, it changes fast. */
    private fun nowcast(client: HaClient, weather: WeatherData?): RainNowcast? {
        val at = weather?.lat?.let { lat -> weather.lon?.let { lat to it } }
            ?: runCatching { HaFetch.home(client) }.getOrNull()
            ?: return null
        return runCatching { OpenMeteo.nowcast(at.first, at.second) }.getOrNull()
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Float {
        val out = FloatArray(1)
        Location.distanceBetween(a.first, a.second, b.first, b.second, out)
        return out[0]
    }

    @Suppress("DEPRECATION") // the listener overload needs API 33; this runs on a worker thread anyway
    private fun placeName(context: Context, at: Pair<Double, Double>): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale.getDefault()).getFromLocation(at.first, at.second, 1)
            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}
