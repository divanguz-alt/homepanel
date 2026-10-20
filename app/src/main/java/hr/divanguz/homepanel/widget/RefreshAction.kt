package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** The widget's refresh button: reloads everything now, weather included. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { PanelStore.refreshAll(context, forceWeather = true) }
            .onFailure { Log.e("HomePanel", "Manual refresh failed", it) }
    }
}
