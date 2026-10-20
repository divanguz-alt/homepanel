package hr.divanguz.homepanel.claude

import android.content.Context
import android.util.Log
import hr.divanguz.homepanel.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** Last known Claude plan usage. Percentages are 0–100; reset times are epoch millis (0 = unknown). */
@Serializable
data class ClaudeSnapshot(
    val fiveHour: Float? = null,
    val fiveHourResetsAt: Long = 0,
    val week: Float? = null,
    val weekResetsAt: Long = 0,
    val fetchedAt: Long = 0,
    /** The session is gone (signed out or expired): the widget asks to sign in. */
    val needsLogin: Boolean = false,
    val error: String? = null,
) {
    val hasData: Boolean get() = fiveHour != null || week != null
}

/**
 * Reads usage from the endpoint claude.ai's own settings page uses. It is undocumented and can change
 * at any time, so every failure keeps the last good numbers and only records what went wrong.
 */
object ClaudeUsage {
    private const val PREFS = "homepanel_cache"
    private const val KEY = "claude"
    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false) // a redirect to /login means the session is dead
        .build()

    private sealed interface Outcome {
        data class Ok(val body: String) : Outcome
        data object SignedOut : Outcome
        data object Challenged : Outcome
        data class Failed(val reason: String) : Outcome
    }

    fun cached(context: Context): ClaudeSnapshot =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<ClaudeSnapshot>(it) }.getOrNull() }
            ?: ClaudeSnapshot(needsLogin = Settings.get(context).claudeSession.isNullOrBlank())

    private fun save(context: Context, s: ClaudeSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(ClaudeSnapshot.serializer(), s)).apply()
    }

    /** Fetches usage (solving Cloudflare once if needed), stores and returns the merged snapshot. */
    suspend fun refresh(context: Context): ClaudeSnapshot {
        val settings = Settings.get(context)
        val old = cached(context)
        if (settings.claudeSession.isNullOrBlank()) return old.copy(needsLogin = true, error = null).also { save(context, it) }

        val org = settings.claudeOrgId?.takeIf { it.isNotBlank() } ?: discoverOrg(settings)?.also { settings.claudeOrgId = it }
        if (org == null) return old.copy(error = "Could not find your Claude organization").also { save(context, it) }

        var outcome = get(settings, "/api/organizations/$org/usage")
        if (outcome == Outcome.Challenged && CloudflareSolver.solve(context, settings)) {
            outcome = get(settings, "/api/organizations/$org/usage")
        }
        val next = when (val o = outcome) {
            is Outcome.Ok -> runCatching { parse(o.body) }.getOrElse { old.copy(error = "Unexpected response from claude.ai") }
            Outcome.SignedOut -> old.copy(needsLogin = true, error = "Signed out")
            Outcome.Challenged -> old.copy(error = "Cloudflare check did not pass")
            is Outcome.Failed -> old.copy(error = o.reason)
        }
        if (next.error != null) Log.w("HomePanel", "Claude usage: ${next.error}")
        save(context, next)
        return next
    }

    internal fun parse(body: String): ClaudeSnapshot {
        val root = json.parseToJsonElement(body) as JsonObject
        fun window(name: String): Pair<Float?, Long> {
            val w = root[name] as? JsonObject ?: return null to 0L
            val util = (w["utilization"] as? JsonPrimitive)?.doubleOrNull?.toFloat()
            val reset = (w["resets_at"] as? JsonPrimitive)?.contentOrNull
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
            return util to reset
        }
        val (fh, fhReset) = window("five_hour")
        val (wk, wkReset) = window("seven_day")
        if (fh == null && wk == null) error("no usage windows")
        return ClaudeSnapshot(fh, fhReset, wk, wkReset, fetchedAt = System.currentTimeMillis())
    }

    /** The organization to read usage for: the first one with chat capabilities, else the first. */
    private fun discoverOrg(settings: Settings): String? {
        val o = get(settings, "/api/organizations") as? Outcome.Ok ?: return null
        val orgs = (runCatching { json.parseToJsonElement(o.body) }.getOrNull() as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        fun id(org: JsonObject) = ((org["uuid"] ?: org["id"]) as? JsonPrimitive)?.contentOrNull
        val chat = orgs.firstOrNull { org ->
            (org["capabilities"] as? JsonArray).orEmpty().any {
                val cap = (it as? JsonPrimitive)?.contentOrNull.orEmpty()
                cap.contains("chat", true) || cap.contains("claude_ai", true)
            }
        }
        return (chat ?: orgs.firstOrNull())?.let(::id)
    }

    private fun get(settings: Settings, path: String): Outcome {
        val session = settings.claudeSession ?: return Outcome.SignedOut
        val cookie = buildString {
            append("${ClaudeCookies.SESSION}=$session")
            settings.claudeClearance?.takeIf { it.isNotBlank() }?.let { append("; ${ClaudeCookies.CLEARANCE}=$it") }
        }
        val req = Request.Builder()
            .url(ClaudeCookies.ORIGIN + path)
            .header("Accept", "application/json")
            .header("Cookie", cookie)
            .apply { settings.claudeUserAgent?.let { header("User-Agent", it) } }
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                when {
                    resp.code == 200 -> Outcome.Ok(body)
                    resp.code == 401 || resp.isRedirect -> Outcome.SignedOut
                    resp.code == 403 || resp.code == 503 || body.contains("Just a moment") -> Outcome.Challenged
                    else -> Outcome.Failed("claude.ai HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
