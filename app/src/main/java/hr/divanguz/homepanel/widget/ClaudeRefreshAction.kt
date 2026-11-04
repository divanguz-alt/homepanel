package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import hr.divanguz.homepanel.claude.TapLog
import hr.divanguz.homepanel.work.Refresh

/**
 * Tap on the Claude bars: fetch usage right now. The fetch runs in WorkManager, not here: a widget
 * action only gets about ten seconds before Android stops it, and a Cloudflare check alone can take
 * longer. Each step goes to [TapLog] so Settings → Claude shows where a tap stopped.
 */
class ClaudeRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.i("HomePanel", "Claude bars tapped")
        TapLog.tapped(context)
        runCatching { PanelStore.markClaudeBusy(context) }
            .onFailure { Log.e("HomePanel", "Claude busy flag failed", it) }
        Refresh.claudeNow(context)
        TapLog.mark(context, TapLog.QUEUED)
    }
}
