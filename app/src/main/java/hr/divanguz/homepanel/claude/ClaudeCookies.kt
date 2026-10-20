package hr.divanguz.homepanel.claude

import android.webkit.CookieManager
import hr.divanguz.homepanel.settings.Settings

/** claude.ai cookie names and the bridge between the WebView cookie jar and our encrypted settings. */
internal object ClaudeCookies {
    const val ORIGIN = "https://claude.ai"
    const val SESSION = "sessionKey"
    const val CLEARANCE = "cf_clearance"
    const val LAST_ORG = "lastActiveOrg"

    fun fromJar(): Map<String, String> =
        CookieManager.getInstance().getCookie(ORIGIN).orEmpty()
            .split(';')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }
            .toMap()

    /** Copies the session, Cloudflare clearance and (if unknown) the org id from the jar. */
    fun save(settings: Settings, jar: Map<String, String> = fromJar()) {
        jar[SESSION]?.takeIf { it.isNotBlank() }?.let { settings.claudeSession = it }
        jar[CLEARANCE]?.takeIf { it.isNotBlank() }?.let { settings.claudeClearance = it }
        if (settings.claudeOrgId.isNullOrBlank()) jar[LAST_ORG]?.takeIf { it.isNotBlank() }?.let { settings.claudeOrgId = it }
    }

    /** Puts our stored session back into the jar so a background WebView is signed in. */
    fun seedJar(settings: Settings) {
        val session = settings.claudeSession ?: return
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(ORIGIN, "$SESSION=$session; Domain=.claude.ai; Path=/; Secure")
        }
    }
}
