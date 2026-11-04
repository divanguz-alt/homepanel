package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.CalendarSource
import hr.divanguz.homepanel.claude.ClaudeSnapshot
import hr.divanguz.homepanel.claude.ClaudeUsage
import hr.divanguz.homepanel.search.SearchWidget
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.ha.HaSnapshot
import hr.divanguz.homepanel.notify.Alerts
import kotlinx.serialization.json.Json

/** Data for the widget lives in each instance's Glance state, so writes trigger recomposition. */
object PanelStore {
    private val CALENDAR = stringPreferencesKey("calendar")
    private val HA = stringPreferencesKey("ha")
    private val REDRAW = longPreferencesKey("redraw")
    private val CLAUDE = stringPreferencesKey("claude")
    private val CLAUDE_BUSY = booleanPreferencesKey("claude_busy")
    private val json = Json { ignoreUnknownKeys = true }

    /** Every widget that shows panel data: the panel and the small one-zone widgets. */
    private val kinds: List<() -> GlanceAppWidget> = listOf(::HomePanelWidget, ::ClaudeWidget, ::CarWidget, ::WeatherWidget)

    /** All placed data widgets, each with an instance of its own class (update needs the matching one). */
    private suspend fun placed(context: Context): List<Pair<GlanceAppWidget, GlanceId>> {
        val manager = GlanceAppWidgetManager(context)
        return kinds.flatMap { make ->
            val widget = make()
            manager.getGlanceIds(widget.javaClass).map { widget to it }
        }
    }

    fun calendar(prefs: Preferences): CalendarSnapshot? =
        prefs[CALENDAR]?.let { runCatching { json.decodeFromString<CalendarSnapshot>(it) }.getOrNull() }

    fun ha(prefs: Preferences): HaSnapshot? =
        prefs[HA]?.let { runCatching { json.decodeFromString<HaSnapshot>(it) }.getOrNull() }

    fun claude(prefs: Preferences): ClaudeSnapshot? =
        prefs[CLAUDE]?.let { runCatching { json.decodeFromString<ClaudeSnapshot>(it) }.getOrNull() }

    /** True while a tap-triggered usage refresh is running. */
    fun claudeBusy(prefs: Preferences): Boolean = prefs[CLAUDE_BUSY] == true

    private fun claudeJson(s: ClaudeSnapshot) = json.encodeToString(ClaudeSnapshot.serializer(), s)

    /** Shows "updating…" on every widget right away, before the fetch starts. */
    suspend fun markClaudeBusy(context: Context) {
        for ((widget, id) in placed(context)) {
            updateAppWidgetState(context, id) { it[CLAUDE_BUSY] = true }
            widget.update(context, id)
        }
    }

    /** Refreshes only Claude usage, showing "updating…" on the widget meanwhile. */
    suspend fun refreshClaude(context: Context) {
        markClaudeBusy(context)
        val all = placed(context)
        try {
            val snapshot = runCatching { ClaudeUsage.refresh(context) }
                .onFailure { Log.e("HomePanel", "Claude refresh failed", it) }
                .getOrElse { ClaudeUsage.cached(context).copy(error = it.message ?: "failed") }
            for ((_, id) in all) updateAppWidgetState(context, id) { it[CLAUDE] = claudeJson(snapshot) }
            Alerts.claudeChecked(context, snapshot)
        } finally {
            // Always clear the spinner, or a killed fetch would leave it on for good.
            for ((widget, id) in all) {
                updateAppWidgetState(context, id) { it[CLAUDE_BUSY] = false }
                widget.update(context, id)
            }
        }
    }

    fun putCalendar(prefs: MutablePreferences, snapshot: CalendarSnapshot) {
        prefs[CALENDAR] = json.encodeToString(CalendarSnapshot.serializer(), snapshot)
    }

    fun redrawStamp(prefs: Preferences): Long? = prefs[REDRAW]

    /** Redraws every widget without fetching anything, e.g. after appearance settings change. */
    suspend fun redraw(context: Context) {
        val now = System.currentTimeMillis()
        for ((widget, id) in placed(context)) {
            updateAppWidgetState(context, id) { it[REDRAW] = now }
            widget.update(context, id)
        }
        // The search bar has no data, only the language and theme can change.
        val search = SearchWidget()
        for (id in GlanceAppWidgetManager(context).getGlanceIds(SearchWidget::class.java)) {
            updateAppWidgetState(context, id) { it[REDRAW] = now }
            search.update(context, id)
        }
    }

    /** Seeds a freshly placed widget from the calendar and the last cached HA values. */
    suspend fun seed(context: Context, id: GlanceId) {
        val snapshot = CalendarSource.load(context)
        val ha = HaRepository.cached(context)
        updateAppWidgetState(context, id) {
            putCalendar(it, snapshot)
            it[HA] = json.encodeToString(HaSnapshot.serializer(), ha)
            it[CLAUDE] = claudeJson(ClaudeUsage.cached(context))
        }
    }

    /**
     * Reloads the calendar (and Home Assistant when [includeHa]) into every placed widget and redraws them.
     * Returns how many widgets were updated.
     */
    suspend fun refreshAll(context: Context, includeHa: Boolean = true, forceWeather: Boolean = false): Int {
        val all = placed(context)
        // Nothing to draw and nothing to notify: don't touch the network.
        val settings = hr.divanguz.homepanel.settings.Settings.get(context)
        if (all.isEmpty() && !settings.tempNotification && !settings.claudeAlerts) return 0
        val snapshot = CalendarSource.load(context)
        val ha = if (includeHa) HaRepository.refresh(context, forceWeather) else HaRepository.cached(context)
        val haJson = json.encodeToString(HaSnapshot.serializer(), ha)
        // Claude rides along with the Home Assistant refresh (periodic, manual), not calendar edits.
        val claude = if (includeHa) runCatching { ClaudeUsage.refresh(context) }.getOrElse { ClaudeUsage.cached(context) }
        else ClaudeUsage.cached(context)
        // Notifications work without any widget placed.
        if (includeHa) {
            Alerts.weatherChanged(context, ha)
            Alerts.claudeChecked(context, claude)
        }
        for ((widget, id) in all) {
            updateAppWidgetState(context, id) {
                putCalendar(it, snapshot)
                it[HA] = haJson
                it[CLAUDE] = claudeJson(claude)
            }
            widget.update(context, id)
        }
        Log.i("HomePanel", "Calendar: ${snapshot.events.size} events, permission=${snapshot.hasPermission}, widgets=${all.size}")
        return all.size
    }
}
