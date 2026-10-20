package hr.divanguz.homepanel.weather

import android.content.Context
import hr.divanguz.homepanel.ha.WeatherData
import hr.divanguz.homepanel.settings.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the widget's weather comes from (Settings → Weather source). */
enum class WeatherProvider(val label: String, val note: String) {
    OPEN_METEO("Open-Meteo", "Best model for your location"),
    ICON("DWD ICON", "German weather service model, via Open-Meteo"),
    ECMWF("ECMWF", "European centre model, via Open-Meteo"),
    METEO_FRANCE("Météo-France", "AROME / ARPEGE, via Open-Meteo"),
    MET_NO("MET Norway (yr.no)", "Same source as Home Assistant's default weather"),
    DHMZ("DHMZ", "Croatian met service: nearest station + 7-day forecast; only in Croatia"),
    HOME_ASSISTANT("Home Assistant", "Your HA weather entity (always forecasts for home)"),
    ;

    /** Weather for the given coordinates. HOME_ASSISTANT is handled by the caller (it needs HA). */
    fun fetch(context: Context, lat: Double, lon: Double): WeatherData {
        val eu = Settings.get(context).aqiEuropean
        return fetchFor(context, lat, lon, eu)
    }

    private fun fetchFor(context: Context, lat: Double, lon: Double, eu: Boolean): WeatherData = when (this) {
        OPEN_METEO -> OpenMeteo.fetch(lat, lon, european = eu)
        ICON -> OpenMeteo.fetch(lat, lon, model = "icon_seamless", european = eu)
        ECMWF -> OpenMeteo.fetch(lat, lon, model = "ecmwf_ifs025", european = eu)
        METEO_FRANCE -> OpenMeteo.fetch(lat, lon, model = "meteofrance_seamless", european = eu)
        MET_NO -> MetNo.fetch(lat, lon, european = eu)
        DHMZ -> Dhmz.fetch(context, lat, lon)
        HOME_ASSISTANT -> throw IOException("Home Assistant weather is fetched through HA")
    }.copy(source = label)

    companion object {
        fun from(id: String?): WeatherProvider = entries.firstOrNull { it.name == id } ?: OPEN_METEO
    }
}

/** Shared HTTP client for the public weather APIs. */
internal object WeatherHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // MET Norway requires an identifying User-Agent.
    const val USER_AGENT = "HomePanel/0.1 (personal Android widget)"

    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from ${req.url.host}")
            return resp.body.string()
        }
    }
}
