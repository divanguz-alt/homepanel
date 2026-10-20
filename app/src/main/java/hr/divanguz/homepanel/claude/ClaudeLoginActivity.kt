package hr.divanguz.homepanel.claude

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.widget.PanelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One-time claude.ai sign-in in a WebView. As soon as a sessionKey cookie shows up we keep it (with
 * cf_clearance and the WebView's User-Agent), fetch usage once and close.
 *
 * "Continue with Google" opens a popup window, so popups are hosted as child WebViews on top; and
 * claude.ai finishes sign-in without a full page load, so the cookie jar is also polled every second.
 */
class ClaudeLoginActivity : Activity() {
    private lateinit var container: FrameLayout
    private lateinit var web: WebView
    private val popups = mutableListOf<WebView>()
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    private val poll = object : Runnable {
        override fun run() {
            captureIfSignedIn()
            if (!done) handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)

        // Keep the page out from under the status and navigation bars (edge-to-edge is forced on 15+).
        container = FrameLayout(this).apply {
            setBackgroundColor(0xFF0B0D12.toInt())
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        web = newWebView()
        Settings.get(this).claudeUserAgent = web.settings.userAgentString
        container.addView(web, MATCH)
        setContentView(container)
        web.loadUrl("${ClaudeCookies.ORIGIN}/login")
        handler.postDelayed(poll, 1000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView = WebView(this).apply {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = captureIfSignedIn()
        }
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = newWebView()
                popups += popup
                container.addView(popup, MATCH)
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                popups.remove(window)
                container.removeView(window)
                window.destroy()
                captureIfSignedIn()
            }
        }
    }

    private fun captureIfSignedIn() {
        if (done) return
        val jar = ClaudeCookies.fromJar()
        if (jar[ClaudeCookies.SESSION].isNullOrBlank()) return
        done = true
        CookieManager.getInstance().flush()
        val settings = Settings.get(this)
        settings.claudeOrgId = null // a new account may belong to another organization
        ClaudeCookies.save(settings, jar)
        Toast.makeText(this, "Signed in to Claude", Toast.LENGTH_SHORT).show()
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { PanelStore.refreshClaude(app) } }
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Back navigates inside the page first")
    override fun onBackPressed() {
        val top = popups.lastOrNull() ?: web
        when {
            top.canGoBack() -> top.goBack()
            popups.isNotEmpty() -> top.webChromeClient?.onCloseWindow(top)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        popups.forEach { it.destroy() }
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    private companion object {
        val MATCH = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
