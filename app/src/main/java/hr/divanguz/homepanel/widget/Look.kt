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
)

val LocalLook = staticCompositionLocalOf { Look() }

internal fun sz(base: Int, scale: Float): TextUnit = (base * scale).sp
internal fun dz(base: Int, scale: Float): Dp = (base * scale).dp
