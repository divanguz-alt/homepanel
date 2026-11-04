package hr.divanguz.homepanel.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * The widget's colours. Every zone reads them through [Palette], which points at the theme picked
 * in Settings → Appearance; the widget sets [Palette.theme] before each composition.
 */
data class WidgetTheme(
    val id: String,
    val card: Color,
    val track: Color,
    val hair: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    /** The card's outer 1dp border; the inner dividers use [hair]. */
    val border: Color = hair,
    /** Light background (dark text): the car map and accents switch to their light variants. */
    val light: Boolean = false,
)

/**
 * Built from OKLCH: the neutrals carry a little of the theme's hue, text steps are the same
 * lightness in every theme, and tertiary text keeps at least 5.5:1 on the card (checked for the
 * fixed themes; Glass over a white wallpaper too). Material You uses whatever the system gives.
 */
object Themes {
    const val MIDNIGHT = "midnight"
    const val MATERIAL_YOU = "you"
    const val GLASS = "glass"
    const val LIGHT = "light"
    /** Light by day, Midnight at night, following the system's dark mode. */
    const val AUTO = "auto"

    private fun c(hex: Long) = Color(0xFF000000 or hex)

    val midnight = WidgetTheme(MIDNIGHT, c(0x15181F), c(0x1C2029), c(0x262A33), c(0xECEEF2), c(0xA3A9B5), c(0x8A909C))
    /** Tertiary text #585E6A keeps 6:1 on the card, like the dark themes. */
    val light = WidgetTheme(LIGHT, c(0xF4F5F7), c(0xE2E5EA), c(0xD9DCE2), c(0x15181F), c(0x444A55), c(0x585E6A), light = true)
    private val fixed = listOf(
        midnight,
        light,
        WidgetTheme("graphite", c(0x161616), c(0x202020), c(0x292929), c(0xEBEBEB), c(0xB1B1B1), c(0x929292)),
        WidgetTheme("amoled", c(0x000000), c(0x141414), c(0x1D1D1D), c(0xE8E8E8), c(0xABABAB), c(0x8C8C8C), border = c(0x000000)),
        // Wallpaper shows through a 78% veil: enough that tertiary text keeps 5.5:1 even over white.
        WidgetTheme(GLASS, Color(0xC7000000), Color(0x26FFFFFF), Color(0x29FFFFFF), c(0xFFFFFF), c(0xD6D9E0), c(0xB4B9C3)),
        WidgetTheme("moss", c(0x101911), c(0x19231B), c(0x222D24), c(0xE9EDE0), c(0xACB9A6), c(0x8C9A89)),
        WidgetTheme("espresso", c(0x1D140D), c(0x291E16), c(0x332720), c(0xF2EADB), c(0xC6B4A0), c(0xA5917F)),
        WidgetTheme("indigo", c(0x0C1228), c(0x161D37), c(0x202943), c(0xE7EBF6), c(0xACB7D5), c(0x8C97B8)),
        WidgetTheme("wine", c(0x210D0F), c(0x301719), c(0x3C2224), c(0xF5E8E5), c(0xCDAFAE), c(0xB08F8F)),
        WidgetTheme("phosphor", c(0x050B08), c(0x0A1A10), c(0x122519), c(0x89F3A2), c(0x79C58E), c(0x66A27B)),
    )

    /** In the order Settings lists them. */
    val ids: List<String> = listOf(MIDNIGHT, AUTO, LIGHT, MATERIAL_YOU) + fixed.drop(2).map { it.id }

    /** The system is in dark mode right now (system resources follow it even in a background worker). */
    fun systemNight(): Boolean =
        (android.content.res.Resources.getSystem().configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Material You: the system's dark tones from the wallpaper (Android 12+). */
    private fun materialYou(context: Context): WidgetTheme {
        fun sys(id: Int) = Color(context.getColor(id))
        return WidgetTheme(
            MATERIAL_YOU,
            card = sys(android.R.color.system_neutral1_900),
            track = sys(android.R.color.system_neutral2_800),
            hair = sys(android.R.color.system_neutral2_700),
            ink = sys(android.R.color.system_neutral1_50),
            ink2 = sys(android.R.color.system_neutral2_200),
            ink3 = sys(android.R.color.system_neutral2_300),
        )
    }

    fun resolve(context: Context, id: String): WidgetTheme = when (id) {
        MATERIAL_YOU -> runCatching { materialYou(context) }.getOrDefault(midnight)
        AUTO -> if (systemNight()) midnight.copy(id = AUTO) else light.copy(id = AUTO)
        else -> fixed.firstOrNull { it.id == id } ?: midnight
    }
}

object Palette {
    @Volatile var theme: WidgetTheme = Themes.midnight
    /** Auto theme: colours carry a day and a night value and the launcher picks one when it draws. */
    @Volatile var auto: Boolean = false
        private set

    /** Points the palette at the theme from settings; call at the start of each composition. */
    fun use(context: Context, id: String) {
        theme = Themes.resolve(context, id)
        auto = id == Themes.AUTO
    }

    private fun p(pick: (WidgetTheme) -> Color): ColorProvider =
        if (auto) androidx.glance.color.ColorProvider(day = pick(Themes.light), night = pick(Themes.midnight))
        else ColorProvider(pick(theme))

    val cardP get() = p { it.card }
    /** Auto theme only: a veil in the card colour over the (neutral grey) car map, light by day, dark at night. */
    val mapVeilP get() = androidx.glance.color.ColorProvider(
        day = Themes.light.card.copy(alpha = 0.7f), night = Themes.midnight.card.copy(alpha = 0.7f),
    )
    val trackP get() = p { it.track }
    val hairP get() = p { it.hair }
    val borderP get() = p { it.border }
    /** Blue for the chance of rain, darker on a light card. */
    val rainP get() = if (auto) androidx.glance.color.ColorProvider(Color(0xFF2F6FB0), Color(0xFF7FB2E5))
        else ColorProvider(if (theme.light) Color(0xFF2F6FB0) else Color(0xFF7FB2E5))

    val card get() = theme.card
    val track get() = theme.track
    val hair get() = theme.hair
    val border get() = theme.border
    val ink get() = theme.ink
    val ink2 get() = theme.ink2
    val ink3 get() = theme.ink3
    val accent = Color(0xFFF2A65A)

    val inkP get() = p { it.ink }
    val ink2P get() = p { it.ink2 }
    val ink3P get() = p { it.ink3 }
}
