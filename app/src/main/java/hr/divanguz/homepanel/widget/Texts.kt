package hr.divanguz.homepanel.widget

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Every word the widget shows, in English and Croatian (Settings → Appearance → Language).
 * [locale] also formats dates and weekdays ("Tue 20 Oct" / "Uto 20 lis").
 */
data class Texts(
    val locale: Locale,
    internal val conditions: Map<String, String>,
    val now: (String) -> String,
    val tomorrow: (condition: String, high: String) -> String,
    val waitingHa: String,
    val setUpHa: String,
    val waitingWeather: String,
    val tapToSetUp: String,
    val source: (String) -> String,
    val rainFrom: (String) -> String,
    val rainUntil: (String) -> String,
    val rainHours: String,
    private val warningTypes: Map<String, String>,
    val warning: (type: String, tomorrow: Boolean) -> String,
    val comingUp: String,
    val todayNoEvents: String,
    val nothingToday: String,
    val today: String,
    val allDay: String,
    val allowCalendar: String,
    val loading: String,
    val nothingNext: (days: Int) -> String,
    val claudeTitle: String,
    val fiveHours: String,
    val week: String,
    val resetsIn: (String) -> String,
    val resetsNow: String,
    val updating: String,
    val signedOut: String,
    val tapToSignIn: String,
    val toShowUsage: String,
    /** Car label when no name is set. */
    val car: String,
) {
    /** HA condition name → words; unknown ones are prettified. */
    fun condition(key: String): String =
        conditions[key] ?: key.replace('-', ' ').replaceFirstChar { it.uppercase(locale) }

    /** DHMZ warning type ("Wind", "Snow ice", …) in this language. */
    fun warningType(type: String): String = warningTypes[type.lowercase()] ?: type

    companion object {
        val EN = Texts(
            locale = Locale.UK,
            conditions = mapOf(
                "sunny" to "Sunny", "clear-night" to "Clear", "partlycloudy" to "Partly cloudy", "cloudy" to "Cloudy",
                "rainy" to "Rain", "pouring" to "Heavy rain", "snowy" to "Snow", "snowy-rainy" to "Sleet", "hail" to "Hail",
                "fog" to "Fog", "lightning" to "Thunderstorms", "lightning-rainy" to "Thunderstorms", "windy" to "Windy",
                "windy-variant" to "Windy", "exceptional" to "Unusual weather",
            ),
            now = { "$it now" },
            tomorrow = { c, high -> "Tomorrow ${c.lowercase(Locale.UK)}$high" },
            waitingHa = "Waiting for Home Assistant",
            setUpHa = "Tap to set up Home Assistant",
            waitingWeather = "Waiting for weather",
            tapToSetUp = "Tap to set up",
            source = { "Source: $it" },
            rainFrom = { "Rain from $it" },
            rainUntil = { "Rain until $it" },
            rainHours = "Rain for the next hours",
            warningTypes = emptyMap(),
            warning = { type, tomorrow -> if (tomorrow) "$type tomorrow" else "$type warning" },
            comingUp = "Coming up",
            todayNoEvents = "Today: no events",
            nothingToday = "Nothing today",
            today = "Today",
            allDay = "all",
            allowCalendar = "Tap to allow calendar access",
            loading = "Loading…",
            nothingNext = { "Nothing in the next $it days" },
            claudeTitle = "Claude usage",
            fiveHours = "5 hours",
            week = "Week",
            resetsIn = { "resets in $it" },
            resetsNow = "resets now",
            updating = "updating…",
            signedOut = "signed out · tap to sign in",
            tapToSignIn = "Tap to sign in",
            toShowUsage = "to show your plan usage",
            car = "Car",
        )

        val HR = Texts(
            locale = Locale.forLanguageTag("hr-HR"),
            conditions = mapOf(
                "sunny" to "Sunčano", "clear-night" to "Vedro", "partlycloudy" to "Djelomično oblačno", "cloudy" to "Oblačno",
                "rainy" to "Kiša", "pouring" to "Jaka kiša", "snowy" to "Snijeg", "snowy-rainy" to "Susnježica", "hail" to "Tuča",
                "fog" to "Magla", "lightning" to "Grmljavina", "lightning-rainy" to "Grmljavina", "windy" to "Vjetrovito",
                "windy-variant" to "Vjetrovito", "exceptional" to "Neobično vrijeme",
            ),
            now = { "Trenutno ${it.lowercase(Locale.forLanguageTag("hr-HR"))}" },
            tomorrow = { c, high -> "Sutra ${c.lowercase(Locale.forLanguageTag("hr-HR"))}$high" },
            waitingHa = "Čekam Home Assistant",
            setUpHa = "Dodirni za postavljanje Home Assistanta",
            waitingWeather = "Čekam prognozu",
            tapToSetUp = "Dodirni za postavljanje",
            source = { "Izvor: $it" },
            rainFrom = { "Kiša od $it" },
            rainUntil = { "Kiša do $it" },
            rainHours = "Kiša idućih sati",
            warningTypes = mapOf(
                "wind" to "vjetar", "fog" to "magla", "thunderstorm" to "grmljavina", "snow ice" to "snijeg i led",
                "high temperature" to "vrućina", "low temperature" to "hladnoća", "rain" to "kiša",
            ),
            warning = { type, tomorrow -> if (tomorrow) "Sutra: $type" else "Upozorenje: $type" },
            comingUp = "Nadolazeće",
            todayNoEvents = "Danas: ništa",
            nothingToday = "Danas ništa",
            today = "Danas",
            allDay = "cijeli",
            allowCalendar = "Dodirni za pristup kalendaru",
            loading = "Učitavam…",
            nothingNext = { "Ništa u idućih $it dana" },
            claudeTitle = "Potrošnja Claudea",
            fiveHours = "5 sati",
            week = "Tjedan",
            resetsIn = { "reset za $it" },
            resetsNow = "reset upravo",
            updating = "osvježavam…",
            signedOut = "odjavljeno · dodirni za prijavu",
            tapToSignIn = "Dodirni za prijavu",
            toShowUsage = "za prikaz potrošnje",
            car = "Auto",
        )

        fun of(language: String): Texts = if (language == "hr") HR else EN
    }
}

val LocalTexts = staticCompositionLocalOf { Texts.EN }
