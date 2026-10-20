package hr.divanguz.homepanel.ha

import hr.divanguz.homepanel.settings.HaSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HaException(message: String) : IOException(message)

/**
 * Minimal Home Assistant REST client. Tries the internal URL with a short connect timeout,
 * then the external one, and sticks with whichever answered for the rest of its lifetime.
 */
class HaClient(private val settings: HaSettings) {

    private val external = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val internal = external.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()

    private var working: Pair<String, OkHttpClient>? = null

    /** The base URL that last answered, for display in settings. */
    val usedBaseUrl: String? get() = working?.first

    private fun candidates(): List<Pair<String, OkHttpClient>> = working?.let(::listOf) ?: listOfNotNull(
        settings.internalUrl.takeIf { it.isNotBlank() && allowed(it) }?.let { it to internal },
        settings.externalUrl.takeIf { it.isNotBlank() && allowed(it) }?.let { it to external },
    )

    private fun isOwnServer(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return listOf(settings.internalUrl, settings.externalUrl)
            .mapNotNull { it.takeIf { u -> u.isNotBlank() }?.toHttpUrlOrNull()?.host }
            .any { it.equals(host, ignoreCase = true) }
    }

    /** Plain http only to local addresses: the token must never cross the internet unencrypted. */
    private fun allowed(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.isHttps || Lan.isLocal(u.host)
    }

    /** Runs the request against each base URL in turn. Returns null on 404. */
    private fun call(path: String, body: String? = null): String? {
        var lastError: IOException = HaException("No usable Home Assistant URL (http:// only works for local addresses)")
        for ((base, client) in candidates()) {
            val builder = Request.Builder()
                .url(base.trimEnd('/') + path)
                .header("Authorization", "Bearer ${settings.token}")
            if (body != null) builder.post(body.toRequestBody(JSON))
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    working = base to client
                    return when {
                        resp.code == 404 -> null
                        resp.code == 401 -> throw HaException("Token rejected (401)")
                        !resp.isSuccessful -> throw HaException("HTTP ${resp.code} for $path")
                        else -> resp.body.string()
                    }
                }
            } catch (e: HaException) {
                throw e // the server answered; another URL won't change that
            } catch (e: IOException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = HaException("Bad URL: $base")
            }
        }
        throw lastError
    }

    fun state(entityId: String): JsonObject? =
        call("/api/states/$entityId")?.let { json.parseToJsonElement(it) as? JsonObject }

    fun config(): JsonObject? = call("/api/config")?.let { json.parseToJsonElement(it) as? JsonObject }

    /** Every entity's state; only used to look for an AQI sensor from settings. */
    fun allStates(): List<JsonObject> =
        call("/api/states")?.let { json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { it as? JsonObject }.orEmpty()

    fun dailyForecast(entityId: String): JsonElement? {
        val body = buildJsonObject {
            put("entity_id", entityId)
            put("type", "daily")
        }.toString()
        return call("/api/services/weather/get_forecasts?return_response", body)?.let(json::parseToJsonElement)
    }

    /** Raw bytes of a path on the HA server, or of an absolute URL (entity_picture can be either). */
    fun bytes(path: String): ByteArray? {
        val absolute = path.startsWith("http://") || path.startsWith("https://")
        val targets = if (absolute) listOf("" to external) else candidates()
        for ((base, client) in targets) {
            val url = if (absolute) path else base.trimEnd('/') + path
            // The token only ever goes to the user's own HA server, never to a host an entity points at.
            val req = Request.Builder().url(url)
                .apply { if (isOwnServer(url)) header("Authorization", "Bearer ${settings.token}") }
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!absolute) working = base to client
                    return if (resp.isSuccessful) resp.body.bytes() else null
                }
            } catch (_: IOException) {
            }
        }
        return null
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
