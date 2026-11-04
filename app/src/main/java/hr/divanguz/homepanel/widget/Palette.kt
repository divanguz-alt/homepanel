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
)

/**
 * Built from OKLCH: the neutrals carry a little of the theme's hue, text steps are the same
 * lightness in every theme, and tertiary text keeps at least 5.5:1 on the card.
 */
object Themes {
    const val MIDNIGHT = "midnight"
    const val MATERIAL_YOU = "you"
    const val GLASS = "glass"

    private fun c(hex: Long) = Color(0xFF000000 or hex)

    val midnight = WidgetTheme(MIDNIGHT, c(0x15181F), c(0x1C2029), c(0x262A33), c(0xECEEF2), c(0xA3A9B5), c(0x8A909C))
    private val fixed = listOf(
        midnight,
        WidgetTheme("graphite", c(0x161616), c(0x202020), c(0x292929), c(0xEBEBEB), c(0xB1B1B1), c(0x929292)),
        WidgetTheme("amoled", c(0x000000), c(0x141414), c(0x1D1D1D), c(0xE8E8E8), c(0xABABAB), c(0x8C8C8C)),
        // Wallpaper shows through: dark veil, light hairline, text a touch brighter for unknown backdrops.
        WidgetTheme(GLASS, Color(0x8C000000), Color(0x26FFFFFF), Color(0x29FFFFFF), c(0xFFFFFF), c(0xD6D9E0), c(0xB4B9C3)),
        WidgetTheme("moss", c(0x101911), c(0x19231B), c(0x222D24), c(0xE9EDE0), c(0xACB9A6), c(0x8C9A89)),
        WidgetTheme("espresso", c(0x1D140D), c(0x291E16), c(0x332720), c(0xF2EADB), c(0xC6B4A0), c(0xA5917F)),
        WidgetTheme("indigo", c(0x0C1228), c(0x161D37), c(0x202943), c(0xE7EBF6), c(0xACB7D5), c(0x8C97B8)),
        WidgetTheme("wine", c(0x210D0F), c(0x301719), c(0x3C2224), c(0xF5E8E5), c(0xCDAFAE), c(0xB08F8F)),
        WidgetTheme("phosphor", c(0x050B08), c(0x0A1A10), c(0x122519), c(0x89F3A2), c(0x79C58E), c(0x66A27B)),
    )

    /** In the order Settings lists them. */
    val ids: List<String> = listOf(MIDNIGHT, MATERIAL_YOU) + fixed.drop(1).map { it.id }

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

    fun resolve(context: Context, id: String): WidgetTheme =
        if (id == MATERIAL_YOU) runCatching { materialYou(context) }.getOrDefault(midnight)
        else fixed.firstOrNull { it.id == id } ?: midnight
}

object Palette {
    @Volatile var theme: WidgetTheme = Themes.midnight

    /** Points the palette at the theme from settings; call at the start of each composition. */
    fun use(context: Context, id: String) { theme = Themes.resolve(context, id) }

    val card get() = theme.card
    val track get() = theme.track
    val hair get() = theme.hair
    val ink get() = theme.ink
    val ink2 get() = theme.ink2
    val ink3 get() = theme.ink3
    val accent = Color(0xFFF2A65A)

    val inkP get() = ColorProvider(ink)
    val ink2P get() = ColorProvider(ink2)
    val ink3P get() = ColorProvider(ink3)
}
