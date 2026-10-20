package hr.divanguz.homepanel.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import hr.divanguz.homepanel.MainActivity

object Packages {
    const val WEATHER = "com.sec.android.daemonapp"
    const val CALENDAR = "com.samsung.android.calendar"
    const val HOME_ASSISTANT = "io.homeassistant.companion.android"
    const val CLAUDE = "com.anthropic.claude"
}

/**
 * Intent that opens [pkg], or null when it isn't installed (the zone then just isn't tappable).
 * Samsung Weather has no launcher icon unless the user enables one, so when there is no launcher
 * entry we fall back to any exported MAIN activity of the package.
 */
fun launchIntent(context: Context, pkg: String): Intent? {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(pkg) ?: run {
        val main = Intent(Intent.ACTION_MAIN).setPackage(pkg)
        @Suppress("DEPRECATION") // the flags overload needs API 33; minSdk is 31
        pm.queryIntentActivities(main, 0)
            .firstOrNull { it.activityInfo.exported }
            ?.let { Intent(Intent.ACTION_MAIN).setClassName(pkg, it.activityInfo.name) }
    }
    return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The app's own settings screen, for "tap to set up" states. */
fun settingsIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun GlanceModifier.opens(intent: Intent?): GlanceModifier =
    if (intent != null) clickable(actionStartActivity(intent)) else this
