package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** Tap on the Claude bars: fetch usage right now. */
class ClaudeRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { PanelStore.refreshClaude(context) }
            .onFailure { Log.e("HomePanel", "Claude refresh failed", it) }
    }
}
