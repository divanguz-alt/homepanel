package hr.divanguz.homepanel.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Updates from the GitHub repo's releases (published by scripts/release.sh). A public repo needs no
 * token; for a private one, a fine-grained token with read-only Contents access, stored encrypted.
 * Release tags are "v<major>.<n>"; the version code is major * 100 + n (v1.3 → 103, old v0.11 → 11).
 */
object Updater {
    private const val REPO = "divanguz-alt/homepanel"

    data class Release(val versionCode: Int, val name: String, val assetUrl: String, val size: Long)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The newest release with an APK attached. */
    fun latest(token: String): Release {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val body = http.newCall(req).execute().use { resp ->
            when (resp.code) {
                200 -> resp.body.string()
                401 -> throw IOException("GitHub rejected the token")
                403 -> throw IOException("GitHub limit reached, try again in an hour")
                404 -> throw IOException(if (token.isBlank()) "No release found (a private repository needs a token)" else "No release found (or the token can't see the repository)")
                else -> throw IOException("GitHub HTTP ${resp.code}")
            }
        }
        return parseRelease(json.parseToJsonElement(body) as JsonObject)
    }

    internal fun parseRelease(root: JsonObject): Release {
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: throw IOException("Release without a tag")
        val parts = tag.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) throw IOException("Unexpected tag $tag")
        val code = parts[0] * 100 + parts[1]
        val asset = (root["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { (it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty().endsWith(".apk") }
            ?: throw IOException("Release $tag has no APK")
        return Release(
            versionCode = code,
            name = tag.removePrefix("v"),
            assetUrl = (asset["url"] as JsonPrimitive).content,
            size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0,
        )
    }

    /** Downloads the APK into the app's cache, reporting progress 0..1. */
    fun download(context: Context, release: Release, token: String, progress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "homepanel-${release.name}.apk")
        // GitHub answers with a redirect to storage; OkHttp drops the token when the host changes.
        val req = Request.Builder()
            .url(release.assetUrl)
            .header("Accept", "application/octet-stream")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
            val total = resp.body.contentLength().takeIf { it > 0 } ?: release.size
            resp.body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        return file
    }

    /** True when Android still needs the user's OK for HomePanel to install apps. */
    fun needsInstallPermission(context: Context): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Hands the APK to Android's installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
