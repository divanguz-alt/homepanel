package hr.divanguz.homepanel.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import hr.divanguz.homepanel.weather.WeatherProvider
import hr.divanguz.homepanel.widget.Look

data class HaSettings(
    // Nothing personal is built in: everything below is entered or picked after installing.
    val internalUrl: String = "",
    val externalUrl: String = "",
    val token: String = "",
    val weatherEntity: String = "weather.forecast_home",
    /** Blank until "Test HA" finds a sensor with device_class aqi, or the user fills it in. */
    val aqiEntity: String = "",
    /** The phone's device_tracker from the HA companion app; blank until "Test HA" finds it. */
    val phoneEntity: String = "",
    val carAddressEntity: String = "",
    val rangeEntity: String = "",
    val rangeFallbackEntity: String = "",
    val carImageEntity: String = "",
    val locationEntity: String = "",
    /** Car lock (lock.* or a binary_sensor with device_class lock); blank until "Test" finds one. */
    val lockEntity: String = "",
) {
    val isConfigured: Boolean get() = token.isNotBlank() && (internalUrl.isNotBlank() || externalUrl.isNotBlank())
}

/** All settings, the token included, live in EncryptedSharedPreferences. */
class Settings private constructor(private val prefs: SharedPreferences) {

    fun ha(): HaSettings {
        val d = HaSettings()
        return HaSettings(
            internalUrl = prefs.getString(INTERNAL_URL, d.internalUrl)!!,
            externalUrl = prefs.getString(EXTERNAL_URL, d.externalUrl)!!,
            token = prefs.getString(TOKEN, d.token)!!,
            weatherEntity = prefs.getString(WEATHER, d.weatherEntity)!!,
            aqiEntity = prefs.getString(AQI, d.aqiEntity)!!,
            phoneEntity = prefs.getString(PHONE, d.phoneEntity)!!,
            carAddressEntity = prefs.getString(CAR_ADDRESS, d.carAddressEntity)!!,
            rangeEntity = prefs.getString(RANGE, d.rangeEntity)!!,
            rangeFallbackEntity = prefs.getString(RANGE_FALLBACK, d.rangeFallbackEntity)!!,
            carImageEntity = prefs.getString(CAR_IMAGE, d.carImageEntity)!!,
            locationEntity = prefs.getString(LOCATION, d.locationEntity)!!,
            lockEntity = prefs.getString(LOCK, d.lockEntity)!!,
        )
    }

    fun saveHa(s: HaSettings) {
        prefs.edit()
            .putString(INTERNAL_URL, s.internalUrl.trim().trimEnd('/'))
            .putString(EXTERNAL_URL, s.externalUrl.trim().trimEnd('/'))
            .putString(TOKEN, s.token.trim())
            .putString(WEATHER, s.weatherEntity.trim())
            .putString(AQI, s.aqiEntity.trim())
            .putString(PHONE, s.phoneEntity.trim())
            .putString(CAR_ADDRESS, s.carAddressEntity.trim())
            .putString(RANGE, s.rangeEntity.trim())
            .putString(RANGE_FALLBACK, s.rangeFallbackEntity.trim())
            .putString(CAR_IMAGE, s.carImageEntity.trim())
            .putString(LOCATION, s.locationEntity.trim())
            .putString(LOCK, s.lockEntity.trim())
            .apply()
    }

    /** claude.ai session from the sign-in WebView. Secrets: only ever stored here. */
    var claudeSession: String?
        get() = prefs.getString(CLAUDE_SESSION, null)
        set(v) = prefs.edit().putString(CLAUDE_SESSION, v).apply()
    var claudeClearance: String?
        get() = prefs.getString(CLAUDE_CF, null)
        set(v) = prefs.edit().putString(CLAUDE_CF, v).apply()
    /** The WebView's User-Agent: cf_clearance is only honoured with the UA that earned it. */
    var claudeUserAgent: String?
        get() = prefs.getString(CLAUDE_UA, null)
        set(v) = prefs.edit().putString(CLAUDE_UA, v).apply()
    var claudeOrgId: String?
        get() = prefs.getString(CLAUDE_ORG, null)
        set(v) = prefs.edit().putString(CLAUDE_ORG, v).apply()

    fun clearClaude() {
        prefs.edit().remove(CLAUDE_SESSION).remove(CLAUDE_CF).remove(CLAUDE_UA).remove(CLAUDE_ORG).apply()
    }

    /** Shown on the widget's car label; blank shows a generic "Car". */
    var carName: String
        get() = prefs.getString(CAR_NAME, null).orEmpty()
        set(v) = prefs.edit().putString(CAR_NAME, v.trim()).apply()

    /** App opened by tapping the car (package name); blank opens Home Assistant. */
    var carApp: String
        get() = prefs.getString(CAR_APP, null).orEmpty()
        set(v) = prefs.edit().putString(CAR_APP, v).apply()

    /** How far ahead the calendar list reaches: 7, 14 or 30 days. */
    var calendarDays: Int
        get() = prefs.getInt(CALENDAR_DAYS, 14)
        set(v) = prefs.edit().putInt(CALENDAR_DAYS, v).apply()

    /** Weather is fetched at most this often (minutes), unless refreshed by hand or after a move. */
    var weatherEveryMin: Int
        get() = prefs.getInt(WEATHER_EVERY_MIN, 60)
        set(v) = prefs.edit().putInt(WEATHER_EVERY_MIN, v).apply()

    /** Moving this far (km) since the last forecast fetches a new one right away. */
    var weatherMoveKm: Int
        get() = prefs.getInt(WEATHER_MOVE_KM, 3)
        set(v) = prefs.edit().putInt(WEATHER_MOVE_KM, v).apply()

    /** European AQI (EAQI) instead of US AQI from Open-Meteo. */
    var aqiEuropean: Boolean
        get() = prefs.getBoolean(AQI_EUROPEAN, false)
        set(v) = prefs.edit().putBoolean(AQI_EUROPEAN, v).apply()

    /** Official weather warnings on the widget; only providers that publish them (DHMZ) can. */
    var warnings: Boolean
        get() = prefs.getBoolean(WARNINGS, true)
        set(v) = prefs.edit().putBoolean(WARNINGS, v).apply()

    /** "Rain from 14:20" / "Rain until 15:10" under the conditions. */
    var showRain: Boolean
        get() = prefs.getBoolean(SHOW_RAIN, true)
        set(v) = prefs.edit().putBoolean(SHOW_RAIN, v).apply()

    /** Fine-grained GitHub token (read-only Contents on the repo) for in-app updates. Secret. */
    var githubToken: String?
        get() = prefs.getString(GITHUB_TOKEN, null)
        set(v) = prefs.edit().putString(GITHUB_TOKEN, v?.trim()).apply()

    /** Widget language: "en" or "hr". */
    var language: String
        get() = prefs.getString(LANGUAGE, null) ?: "en"
        set(v) = prefs.edit().putString(LANGUAGE, v).apply()

    /** Design B: today's date after the city ("Split • Tue 20 Oct"). */
    var showDate: Boolean
        get() = prefs.getBoolean(SHOW_DATE, false)
        set(v) = prefs.edit().putBoolean(SHOW_DATE, v).apply()

    /** Small "Source: …" label on the widget's weather zone. */
    var showSource: Boolean
        get() = prefs.getBoolean(SHOW_SOURCE, false)
        set(v) = prefs.edit().putBoolean(SHOW_SOURCE, v).apply()

    /** OpenFreeMap style for the car map: "fiord", "dark" or "positron". */
    var mapStyle: String
        get() = prefs.getString(MAP_STYLE, null) ?: "fiord"
        set(v) = prefs.edit().putString(MAP_STYLE, v).apply()

    var mapZoom: Float
        get() = prefs.getFloat(MAP_ZOOM, 15.8f)
        set(v) = prefs.edit().putFloat(MAP_ZOOM, v).apply()

    var weatherProvider: WeatherProvider
        get() = WeatherProvider.from(prefs.getString(WEATHER_PROVIDER, null))
        set(v) = prefs.edit().putString(WEATHER_PROVIDER, v.name).apply()

    fun look(): Look {
        val d = Look()
        return Look(
            header = prefs.getFloat(LOOK_HEADER, d.header),
            temperatureSp = prefs.getInt(LOOK_TEMP, d.temperatureSp),
            forecast = prefs.getFloat(LOOK_FORECAST, d.forecast),
            conditions = prefs.getFloat(LOOK_CONDITIONS, d.conditions),
            calendar = prefs.getFloat(LOOK_CALENDAR, d.calendar),
            bottom = prefs.getFloat(LOOK_BOTTOM, d.bottom),
            designB = prefs.getBoolean(LOOK_DESIGN_B, d.designB),
        )
    }

    fun saveLook(look: Look) {
        prefs.edit()
            .putFloat(LOOK_HEADER, look.header)
            .putInt(LOOK_TEMP, look.temperatureSp)
            .putFloat(LOOK_FORECAST, look.forecast)
            .putFloat(LOOK_CONDITIONS, look.conditions)
            .putFloat(LOOK_CALENDAR, look.calendar)
            .putFloat(LOOK_BOTTOM, look.bottom)
            .putBoolean(LOOK_DESIGN_B, look.designB)
            .apply()
    }

    companion object {
        private const val WEATHER_PROVIDER = "weather_provider"
        private const val CLAUDE_SESSION = "claude_session_key"
        private const val CLAUDE_CF = "claude_cf_clearance"
        private const val CLAUDE_UA = "claude_user_agent"
        private const val CLAUDE_ORG = "claude_org_id"
        private const val LOOK_HEADER = "look_header"
        private const val LOOK_TEMP = "look_temperature_sp"
        private const val LOOK_FORECAST = "look_forecast"
        private const val LOOK_CONDITIONS = "look_conditions"
        private const val LOOK_CALENDAR = "look_calendar"
        private const val LOOK_BOTTOM = "look_bottom"
        private const val LOOK_DESIGN_B = "look_design_b"
        private const val INTERNAL_URL = "ha_internal_url"
        private const val EXTERNAL_URL = "ha_external_url"
        private const val TOKEN = "ha_token"
        private const val WEATHER = "ha_weather_entity"
        private const val AQI = "ha_aqi_entity"
        private const val PHONE = "ha_phone_entity"
        private const val CAR_ADDRESS = "ha_car_address_entity"
        private const val RANGE = "ha_range_entity"
        private const val RANGE_FALLBACK = "ha_range_fallback_entity"
        private const val CAR_IMAGE = "ha_car_image_entity"
        private const val LOCATION = "ha_location_entity"
        private const val LOCK = "ha_lock_entity"
        private const val CAR_NAME = "car_name"
        private const val CAR_APP = "car_app"
        private const val CALENDAR_DAYS = "calendar_days"
        private const val WEATHER_EVERY_MIN = "weather_every_min"
        private const val WEATHER_MOVE_KM = "weather_move_km"
        private const val AQI_EUROPEAN = "aqi_european"
        private const val WARNINGS = "weather_warnings"
        private const val MAP_STYLE = "map_style"
        private const val SHOW_SOURCE = "show_weather_source"
        private const val SHOW_DATE = "show_date_b"
        private const val LANGUAGE = "widget_language"
        private const val SHOW_RAIN = "show_rain_nowcast"
        private const val GITHUB_TOKEN = "github_token"
        private const val MAP_ZOOM = "map_zoom"

        @Volatile private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(open(context.applicationContext)).also { instance = it }
        }

        // security-crypto is deprecated upstream, but CLAUDE.md asks for EncryptedSharedPreferences.
        @Suppress("DEPRECATION")
        private fun open(context: Context): SharedPreferences {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "homepanel_secure",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
