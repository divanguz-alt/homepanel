package hr.divanguz.homepanel.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible stop between a widget tap and another app. Glance stamps a unique `glance-action:` data URI
 * on every intent it hands out; some apps (Home Assistant, logged in) then don't come up at all. This
 * activity starts the app's own launcher intent instead, untouched, the way the home screen icon does.
 */
class OpenAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val target = (if (pkg.isBlank()) null else launchIntent(this, pkg)) ?: homeAssistantIntent(this)
        target?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(it) }
        }
        finish()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"

        /** Opens [pkg] through this activity; blank opens Home Assistant. */
        fun intent(context: Context, pkg: String): Intent =
            Intent(context, OpenAppActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
