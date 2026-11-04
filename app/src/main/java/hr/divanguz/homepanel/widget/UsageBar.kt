package hr.divanguz.homepanel.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** The Claude usage bar as a bitmap: rounded track, gradient filled up to the percentage. */
internal object UsageBar {
    private val colours = intArrayOf(0xFF5DBB7C.toInt(), 0xFFE5C452.toInt(), 0xFFE35D4F.toInt())

    fun draw(context: Context, width: Dp, height: Dp, percent: Int, track: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (width.value * density).roundToInt().coerceAtLeast(1)
        val h = (height.value * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = h / 2f
        val shape = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.clipPath(shape)
        canvas.drawRect(bounds, Paint().apply { color = track })
        val fill = w * percent.coerceIn(0, 100) / 100f
        if (fill > 0f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colours, null, Shader.TileMode.CLAMP)
            }
            // Clear first, so a see-through track doesn't tint the filled part.
            canvas.drawRect(0f, 0f, fill, h.toFloat(), Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            canvas.drawRect(0f, 0f, fill, h.toFloat(), paint)
        }
        return bitmap
    }
}
