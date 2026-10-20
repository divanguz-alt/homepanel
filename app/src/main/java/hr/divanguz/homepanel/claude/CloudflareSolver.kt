package hr.divanguz.homepanel.claude

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import hr.divanguz.homepanel.settings.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Gets a fresh cf_clearance without the user: an off-screen WebView, signed in with the stored
 * session, opens claude.ai and lets Cloudflare's script run until the clearance cookie appears.
 */
internal object CloudflareSolver {

    suspend fun solve(context: Context, settings: Settings, timeoutMs: Long = 30_000): Boolean {
        ClaudeCookies.seedJar(settings)
        val ok = withTimeoutOrNull(timeoutMs) { load(context.applicationContext, settings) } ?: false
        return ok
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(app: Context, settings: Settings): Boolean = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        main.post {
            var finished = false
            val jar = CookieManager.getInstance()
            val web = WebView(app)

            fun end(success: Boolean) {
                if (finished) return
                finished = true
                jar.flush()
                if (success) ClaudeCookies.save(settings)
                web.stopLoading()
                web.destroy()
                if (cont.isActive) cont.resume(success)
            }

            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            // Use the UA the session already has; a pasted session has none yet, so adopt the WebView's.
            settings.claudeUserAgent?.let { web.settings.userAgentString = it }
                ?: run { settings.claudeUserAgent = web.settings.userAgentString }
            jar.setAcceptCookie(true)
            jar.setAcceptThirdPartyCookies(web, true)
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // The challenge page ("Just a moment…") reloads until it passes; done once a real
                    // claude.ai page has loaded. Its cookies (a new cf_clearance, if any) are saved.
                    val challenge = view.title.orEmpty().contains("Just a moment", ignoreCase = true)
                    if (!challenge) end(true)
                }
            }
            web.loadUrl("${ClaudeCookies.ORIGIN}/new")
            cont.invokeOnCancellation { main.post { end(false) } }
        }
    }
}
