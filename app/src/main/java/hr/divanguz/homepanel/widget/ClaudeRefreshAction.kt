package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import hr.divanguz.homepanel.work.Refresh

/**
 * Tap on the Claude bars: fetch usage right now. The fetch runs in WorkManager, not here: a widget
 * action only gets about ten seconds before Android stops it, and a Cloudflare check alone can take
 * longer, so the result never reached the widget.
 */
class ClaudeRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { PanelStore.markClaudeBusy(context) }
            .onFailure { Log.e("HomePanel", "Claude busy flag failed", it) }
        Refresh.claudeNow(context)
    }
}
