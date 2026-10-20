package hr.divanguz.homepanel.ha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.util.concurrent.CountDownLatch
import hr.divanguz.homepanel.settings.HaSettings
import hr.divanguz.homepanel.settings.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Background map for the car cell: OpenFreeMap's "fiord" vector style (a lighter, bluish dark) (OpenStreetMap data) rendered by
 * MapLibre around the parked car, blurred and darkened on the left where the text sits, sharp around a
 * pin holding the car render on the right. Rendered once per parked address: a new map only when Home
 * Assistant reports a new address.
 */
object CarMap {
    private const val W = 640
    private const val H = 600
    private const val PIXEL_RATIO = 2f
    private const val PREFS = "homepanel_cache"
    // Bump the suffix whenever the look changes so cached maps get redrawn.
    private const val KEY_ADDRESS = "car_map_address_v4"
    private const val KEY_VERSION = "car_map_version_v4"
    // Style and zoom come from settings ("fiord" by default: "dark" was too dark to read on the card).
    private fun styleUrl(context: Context) = "https://tiles.openfreemap.org/styles/${Settings.get(context).mapStyle}"
    private const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap"

    /** The pin's tip, as a fraction of the map: right of centre, clear of the text on the left. */
    private const val TIP_X = 0.68f
    private const val TIP_Y = 0.64f

    /** Design A's map, or design B's grey one ([grey]); both are rendered from the same snapshot. */
    private fun file(context: Context, grey: Boolean = false) =
        File(context.filesDir, if (grey) "car_map_grey.png" else "car_map.png")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun version(context: Context): String? = prefs(context).getString(KEY_VERSION, null)?.takeIf { file(context).exists() }

    private val memo = java.util.concurrent.ConcurrentHashMap<Boolean, Pair<String, Bitmap>>()

    fun load(context: Context, grey: Boolean = false): Bitmap? {
        val version = version(context) ?: return null
        memo[grey]?.let { (v, bmp) -> if (v == version) return bmp }
        val bmp = BitmapFactory.decodeFile(file(context, grey).path) ?: return null
        memo[grey] = version to bmp
        return bmp
    }

    /** Forgets the rendered map so the next refresh draws it again (after a style or zoom change). */
    fun invalidate(context: Context) {
        prefs(context).edit().remove(KEY_ADDRESS).apply()
    }

    /** Renders a new map if [address] differs from the one the cached map was made for. */
    fun refresh(context: Context, client: HaClient, s: HaSettings, address: String?): String {
        if (address.isNullOrBlank()) return "no parked address yet"
        if (address == prefs(context).getString(KEY_ADDRESS, null) && version(context) != null) {
            return "unchanged"
        }
        val (lat, lon) = coordinates(client, s) ?: return "no coordinates in ${s.locationEntity}"
        val sharp = snapshot(context, lat, lon) ?: return "map could not be rendered (offline?)"
        val car = CarImage.load(context)
        val maps = listOf(
            false to compose(sharp, car, callout = 0xF21C2029.toInt()),
            true to compose(greyed(sharp), car, callout = 0xFF0E0F11.toInt()),
        )
        for ((grey, map) in maps) {
            val target = file(context, grey)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.outputStream().use { map.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return "could not save map"
            }
        }
        prefs(context).edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_VERSION, "$address@${System.currentTimeMillis()}")
            .apply()
        return "rendered"
    }

    private fun coordinates(client: HaClient, s: HaSettings): Pair<Double, Double>? {
        // The tracker first; the parked-at sensor sometimes carries coordinates too.
        for (id in listOf(s.locationEntity, s.carAddressEntity).filter { it.isNotBlank() }) {
            val attrs = client.state(id)?.get("attributes") as? JsonObject ?: continue
            val lat = (attrs["latitude"] as? JsonPrimitive)?.doubleOrNull
            val lon = (attrs["longitude"] as? JsonPrimitive)?.doubleOrNull
            if (lat != null && lon != null) return lat to lon
        }
        return null
    }

    /**
     * Design B's palette: fully desaturated and darkened so the panel-coloured map lands near
     * background #1F2024, blocks #24262A, main roads #35373D (from the "fiord" style's luminance).
     */
    private fun greyed(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val k = 0.5f
        val matrix = android.graphics.ColorMatrix(
            floatArrayOf(
                0.299f * k, 0.587f * k, 0.114f * k, 0f, 6f,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, 7f,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) })
        return out
    }

    private fun compose(sharp: Bitmap, car: Bitmap?, callout: Int): Bitmap {
        val tipX = W * TIP_X
        val tipY = H * TIP_Y

        // Blurred, darkened copy underneath; the sharp map shows through only on the right.
        val out = blur(sharp)
        val c = Canvas(out)
        val layer = c.saveLayer(0f, 0f, W.toFloat(), H.toFloat(), null)
        c.drawBitmap(sharp, 0f, 0f, null)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(W * 0.34f, 0f, W * 0.56f, 0f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        c.restoreToCount(layer)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W * 0.5f, 0f, 0x8015181F.toInt(), 0x0015181F, Shader.TileMode.CLAMP)
        })

        drawPin(c, tipX, tipY, car, callout)

        c.drawText(ATTRIBUTION, W - 12f, H - 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x998A909C.toInt()
            textSize = 15f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.DEFAULT
        })
        return out
    }

    /**
     * Renders the style around the car with MapLibre's snapshotter, then crops so the car lands at
     * (TIP_X, TIP_Y). The snapshotter must run on the main thread; this blocks the calling worker
     * thread until it's done, so never call it from the main thread.
     */
    private fun snapshot(context: Context, lat: Double, lon: Double): Bitmap? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "CarMap.snapshot blocks; call it off the main thread" }
        // Big enough that a crop of W×H has the car (the snapshot centre) at the tip position.
        val fullW = (2 * W * TIP_X).toInt()
        val fullH = (2 * H * TIP_Y).toInt()
        val done = CountDownLatch(1)
        var result: Bitmap? = null
        var snapshotter: MapSnapshotter? = null
        Handler(Looper.getMainLooper()).post {
            runCatching {
                MapLibre.getInstance(context.applicationContext)
                val options = MapSnapshotter.Options((fullW / PIXEL_RATIO).toInt(), (fullH / PIXEL_RATIO).toInt())
                    .withStyleBuilder(Style.Builder().fromUri(styleUrl(context)))
                    .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(Settings.get(context).mapZoom.toDouble()).build())
                    .withPixelRatio(PIXEL_RATIO)
                    .withLogo(false)
                    .withAttribution(false)
                snapshotter = MapSnapshotter(context.applicationContext, options).also {
                    it.start(
                        { snap -> result = snap.bitmap; done.countDown() },
                        { error -> Log.w("HomePanel", "Map snapshot failed: $error"); done.countDown() },
                    )
                }
            }.onFailure {
                Log.w("HomePanel", "Map snapshot failed", it)
                done.countDown()
            }
        }
        val finished = done.await(45, TimeUnit.SECONDS)
        Handler(Looper.getMainLooper()).post { snapshotter?.cancel() }
        val full = result?.takeIf { finished } ?: return null
        val left = (full.width * 0.5f - W * TIP_X).toInt().coerceIn(0, (full.width - W).coerceAtLeast(0))
        val top = (full.height * 0.5f - H * TIP_Y).toInt().coerceIn(0, (full.height - H).coerceAtLeast(0))
        val cropped = Bitmap.createBitmap(full, left, top, min(W, full.width), min(H, full.height))
        return if (cropped.width == W && cropped.height == H) cropped.copy(Bitmap.Config.ARGB_8888, true)
        else Bitmap.createScaledBitmap(cropped, W, H, true)
    }

    /** Dark rounded tag with a pointer, holding the car render, like Home Assistant's map card. */
    private fun drawPin(c: Canvas, tipX: Float, tipY: Float, car: Bitmap?, callout: Int) {
        val bw = 176f
        val bh = 120f
        val pointer = 18f
        val box = RectF(tipX - bw / 2, tipY - pointer - bh, tipX + bw / 2, tipY - pointer)
        val shape = Path().apply {
            addRoundRect(box, 26f, 26f, Path.Direction.CW)
            moveTo(tipX - 16f, box.bottom - 1f)
            lineTo(tipX, tipY)
            lineTo(tipX + 16f, box.bottom - 1f)
            close()
        }
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        })
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = callout })
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0xFF3A3F4A.toInt()
        })
        if (car != null) {
            val pad = 12f
            val scale = min((bw - 2 * pad) / car.width, (bh - 2 * pad) / car.height)
            val w = car.width * scale
            val h = car.height * scale
            val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            c.drawBitmap(car, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            c.drawCircle(box.centerX(), box.centerY(), 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECEEF2.toInt() })
        }
    }

    /** Smooth blur: three box-blur passes (≈ Gaussian) on a third-size copy, scaled back up. */
    private fun blur(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, W / 3, H / 3, true)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxPass(px, tmp, w, h, 5, horizontal = true)
            boxPass(tmp, px, w, h, 5, horizontal = false)
        }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return Bitmap.createScaledBitmap(small, W, H, true).copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int) = if (horizontal) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[idx(i)]
                a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[idx(i - r)]
                val inn = src[idx(i + r + 1)]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
