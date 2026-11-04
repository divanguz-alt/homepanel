package hr.divanguz.homepanel.widget

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User-adjustable sizes (Settings → Appearance). Scales multiply each zone's base sizes. */
data class Look(
    val header: Float = 1f,
    val temperatureSp: Int = 84,
    val forecast: Float = 1f,
    /** Current conditions: icon, condition text and AQI. */
    val conditions: Float = 1f,
    val calendar: Float = 1f,
    val bottom: Float = 1f,
    /** Design B ("A2 · Siva karta" redesign) instead of the original layout. */
    val designB: Boolean = false,
    /** Colour theme id from [Themes]. */
    val theme: String = Themes.MIDNIGHT,
    /** Top-to-bottom order of the panel's zones ([Zones]); the default is the original layout. */
    val order: List<String> = Zones.DEFAULT_ORDER,
    /** Zones (and grid halves: [Zones.CAR], [Zones.CLAUDE]) left out of the panel. */
    val hidden: Set<String> = emptySet(),
    /** Claude on the left, the car on the right. */
    val swapGrid: Boolean = false,
) {
    fun shows(zone: String) = zone !in hidden
    /** Same zones, sizes back to default. */
    fun resetSizes() = Look(designB = designB, theme = theme, order = order, hidden = hidden, swapGrid = swapGrid)
}

object Zones {
    const val WEATHER = "weather"
    const val CALENDAR = "calendar"
    const val GRID = "grid"
    const val CAR = "car"
    const val CLAUDE = "claude"
    val DEFAULT_ORDER = listOf(WEATHER, CALENDAR, GRID)

    /** Stored order, repaired: unknown ids dropped, missing ones appended in default order. */
    fun parse(stored: String?): List<String> {
        val ids = stored.orEmpty().split(',').map { it.trim() }.filter { it in DEFAULT_ORDER }.distinct()
        return ids + DEFAULT_ORDER.filter { it !in ids }
    }
}

val LocalLook = staticCompositionLocalOf { Look() }

internal fun sz(base: Int, scale: Float): TextUnit = (base * scale).sp
internal fun dz(base: Int, scale: Float): Dp = (base * scale).dp
