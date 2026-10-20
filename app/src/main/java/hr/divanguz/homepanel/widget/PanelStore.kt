package hr.divanguz.homepanel.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.CalendarSource
import hr.divanguz.homepanel.claude.ClaudeSnapshot
import hr.divanguz.homepanel.claude.ClaudeUsage
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.ha.HaSnapshot
import kotlinx.serialization.json.Json

/** Data for the widget lives in each instance's Glance state, so writes trigger recomposition. */
object PanelStore {
    private val CALENDAR = stringPreferencesKey("calendar")
    private val HA = stringPreferencesKey("ha")
    private val REDRAW = longPreferencesKey("redraw")
    private val CLAUDE = stringPreferencesKey("claude")
    private val CLAUDE_BUSY = booleanPreferencesKey("claude_busy")
    private val json = Json { ignoreUnknownKeys = true }

    fun calendar(prefs: Preferences): CalendarSnapshot? =
        prefs[CALENDAR]?.let { runCatching { json.decodeFromString<CalendarSnapshot>(it) }.getOrNull() }

    fun ha(prefs: Preferences): HaSnapshot? =
        prefs[HA]?.let { runCatching { json.decodeFromString<HaSnapshot>(it) }.getOrNull() }

    fun claude(prefs: Preferences): ClaudeSnapshot? =
        prefs[CLAUDE]?.let { runCatching { json.decodeFromString<ClaudeSnapshot>(it) }.getOrNull() }

    /** True while a tap-triggered usage refresh is running. */
    fun claudeBusy(prefs: Preferences): Boolean = prefs[CLAUDE_BUSY] == true

    private fun claudeJson(s: ClaudeSnapshot) = json.encodeToString(ClaudeSnapshot.serializer(), s)

    /** Refreshes only Claude usage, showing "updating…" on the widget meanwhile. */
    suspend fun refreshClaude(context: Context) {
        val widget = HomePanelWidget()
        val ids = GlanceAppWidgetManager(context).getGlanceIds(HomePanelWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(context, id) { it[CLAUDE_BUSY] = true }
            widget.update(context, id)
        }
        val snapshot = runCatching { ClaudeUsage.refresh(context) }.getOrElse { ClaudeUsage.cached(context) }
        for (id in ids) {
            updateAppWidgetState(context, id) {
                it[CLAUDE] = claudeJson(snapshot)
                it[CLAUDE_BUSY] = false
            }
            widget.update(context, id)
        }
    }

    fun putCalendar(prefs: MutablePreferences, snapshot: CalendarSnapshot) {
        prefs[CALENDAR] = json.encodeToString(CalendarSnapshot.serializer(), snapshot)
    }

    fun redrawStamp(prefs: Preferences): Long? = prefs[REDRAW]

    /** Redraws every widget without fetching anything, e.g. after appearance settings change. */
    suspend fun redraw(context: Context) {
        val widget = HomePanelWidget()
        val now = System.currentTimeMillis()
        for (id in GlanceAppWidgetManager(context).getGlanceIds(HomePanelWidget::class.java)) {
            updateAppWidgetState(context, id) { it[REDRAW] = now }
            widget.update(context, id)
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
        val widget = HomePanelWidget()
        val ids = GlanceAppWidgetManager(context).getGlanceIds(HomePanelWidget::class.java)
        if (ids.isEmpty()) return 0
        val snapshot = CalendarSource.load(context)
        val ha = if (includeHa) HaRepository.refresh(context, forceWeather) else HaRepository.cached(context)
        val haJson = json.encodeToString(HaSnapshot.serializer(), ha)
        // Claude rides along with the Home Assistant refresh (periodic, manual), not calendar edits.
        val claude = if (includeHa) runCatching { ClaudeUsage.refresh(context) }.getOrElse { ClaudeUsage.cached(context) }
        else ClaudeUsage.cached(context)
        for (id in ids) {
            updateAppWidgetState(context, id) {
                putCalendar(it, snapshot)
                it[HA] = haJson
                it[CLAUDE] = claudeJson(claude)
            }
            widget.update(context, id)
        }
        Log.i("HomePanel", "Calendar: ${snapshot.events.size} events, permission=${snapshot.hasPermission}, widgets=${ids.size}")
        return ids.size
    }
}
