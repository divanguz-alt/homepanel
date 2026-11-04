package hr.divanguz.homepanel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import hr.divanguz.homepanel.work.Refresh

class HomePanelReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomePanelWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Refresh.ensureScheduled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                Refresh.rescheduleMidnight(context)
                Refresh.now(context)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Small widgets and notifications still need the background refresh.
        val manager = AppWidgetManager.getInstance(context)
        val others = listOf(ClaudeWidgetReceiver::class.java, CarWidgetReceiver::class.java, WeatherWidgetReceiver::class.java)
            .any { manager.getAppWidgetIds(android.content.ComponentName(context, it)).isNotEmpty() }
        val settings = hr.divanguz.homepanel.settings.Settings.get(context)
        if (!others && !settings.tempNotification && !settings.claudeAlerts) Refresh.cancelAll(context)
    }
}
