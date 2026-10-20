package hr.divanguz.homepanel.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent

/**
 * Firefox's own search widget, reproduced with intents Firefox accepts from other apps.
 * Every channel keeps the `org.mozilla.fenix` class names, only the package differs.
 */
object Firefox {
    private val PACKAGES = listOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix")
    private const val RECEIVER = "org.mozilla.fenix.IntentReceiverActivity"

    /** First installed Firefox channel, or null. */
    fun installed(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /**
     * Opens Firefox straight to its search bar with the keyboard up, the same intent its widget
     * sends. Without Firefox, the default browser opens instead.
     */
    fun searchIntent(context: Context): Intent {
        val pkg = installed(context)
        val intent = if (pkg != null) {
            Intent()
                .setClassName(pkg, RECEIVER)
                .putExtra("open_to_search", "search_widget")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Searches [query] with Firefox's default engine (or whichever app handles web searches). */
    fun webSearchIntent(context: Context, query: String): Intent =
        Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .apply { installed(context)?.let { setPackage(it) } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
