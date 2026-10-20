package hr.divanguz.homepanel.ha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import hr.divanguz.homepanel.settings.HaSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * The vehicle render from Home Assistant's image entity, kept on disk. It is downloaded again only
 * when the entity's last_updated changes, trimmed of transparent margins and scaled to ≤ 600 px wide.
 */
object CarImage {
    private const val MAX_WIDTH = 600
    private const val PREFS = "homepanel_cache"
    private const val KEY_VERSION = "car_image_version"

    private fun file(context: Context) = File(context.filesDir, "car_render.png")

    /** Identifies the cached image; changes whenever a new one is stored. Null when none is cached. */
    fun version(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VERSION, null)
            ?.takeIf { file(context).exists() }

    @Volatile private var memo: Pair<String, Bitmap>? = null

    /** The cached render, decoded once per version. */
    fun load(context: Context): Bitmap? {
        val version = version(context) ?: return null
        memo?.let { (v, bmp) -> if (v == version) return bmp }
        val bmp = BitmapFactory.decodeFile(file(context).path) ?: return null
        memo = version to bmp
        return bmp
    }

    /** Checks the entity and downloads the picture if it changed. Returns a short status for settings. */
    fun refresh(context: Context, client: HaClient, s: HaSettings): String {
        if (s.carImageEntity.isBlank()) return "no image entity set"
        val st = client.state(s.carImageEntity) ?: return "${s.carImageEntity} not found"
        val picture = ((st["attributes"] as? JsonObject)?.get("entity_picture") as? JsonPrimitive)?.contentOrNull
            ?: return "${s.carImageEntity} has no entity_picture"
        val lastUpdated = (st["last_updated"] as? JsonPrimitive)?.contentOrNull ?: picture
        if (lastUpdated == version(context)) return "unchanged since $lastUpdated"

        val bytes = client.bytes(picture) ?: return "download failed"
        val bitmap = decode(bytes) ?: return "could not decode image"
        val target = file(context)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return "could not save image"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_VERSION, lastUpdated).apply()
        return "downloaded ${bitmap.width}×${bitmap.height}"
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        // Subsample big renders while keeping at least ~2× the target width for the trim and final scale.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH * 2) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val trimmed = trimTransparent(raw)
        if (trimmed.width <= MAX_WIDTH) return trimmed
        val h = (trimmed.height * MAX_WIDTH.toFloat() / trimmed.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(trimmed, MAX_WIDTH, h, true)
    }

    /** Crops fully transparent margins so the car fills the widget slot. Opaque images pass through. */
    private fun trimTransparent(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var top = h; var bottom = -1; var left = w; var right = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 8) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (bottom < 0) return src
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1)
    }
}
