package hr.divanguz.homepanel.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.calendar.EventRow
import hr.divanguz.homepanel.calendar.GOOGLE_TASKS
import hr.divanguz.homepanel.calendar.newEventIntent
import hr.divanguz.homepanel.calendar.viewIntent
import hr.divanguz.homepanel.ha.HaClient
import hr.divanguz.homepanel.ha.HaFetch
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.ha.TodoTask
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.tasks.AddActivity
import androidx.glance.unit.ColorProvider

/** Open tasks from the to-do lists picked in settings; empty when none are picked. */
internal val LocalTasks = staticCompositionLocalOf { emptyList<TodoTask>() }

/** This widget's list switch: "all", "calendar" or "tasks". */
internal val LocalListFilter = staticCompositionLocalOf { "all" }

internal val FILTERS = listOf("all", "calendar", "tasks")

/** Overdue tasks: their day label turns amber. */
internal val OverdueP = ColorProvider(androidx.compose.ui.graphics.Color(0xFFE5A04F))

/** The tasks to draw: only lists still picked in settings (a list just removed disappears at once). */
internal fun shownTasks(context: Context, all: List<TodoTask>): List<TodoTask> {
    val lists = Settings.get(context).taskLists
    return if (lists.isEmpty()) emptyList() else all.filter { it.entity in lists }
}

/** Tapping a row: the event in the calendar app, a task in Google Tasks (Home Assistant without it). */
internal fun rowTap(context: Context, row: EventRow): Intent? =
    row.event?.viewIntent() ?: OpenAppActivity.intent(context, GOOGLE_TASKS)

/**
 * The plus: a new event when tasks are off or the list shows only the calendar; the task entry when it
 * shows only tasks; otherwise a small chooser.
 */
internal fun plusTap(context: Context, filter: String): Intent = when {
    Settings.get(context).taskLists.isEmpty() || filter == "calendar" -> newEventIntent()
    filter == "tasks" -> AddActivity.intent(context, AddActivity.TASK)
    else -> AddActivity.intent(context, AddActivity.CHOOSE)
}

/** "All · Calendar · Tasks": a pill with the chosen part lit. Shown only when task lists are picked. */
@Composable
internal fun ListSwitch(k: Float) {
    val labels = LocalTexts.current.filters
    val current = LocalListFilter.current
    Row(
        modifier = GlanceModifier.background(Palette.trackP).cornerRadius(dz(16, k)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FILTERS.forEachIndexed { i, f ->
            val on = f == current
            var m = GlanceModifier.height(dz(26, k)).cornerRadius(dz(13, k)).padding(horizontal = dz(11, k))
            // Always set: a launcher reapplying the views keeps a background that is merely left out.
            m = m.background(if (on) Palette.hairOnP else ColorProvider(androidx.compose.ui.graphics.Color.Transparent))
            Box(
                modifier = m.clickable(actionRunCallback<FilterAction>(actionParametersOf(FilterAction.KEY to f))),
                contentAlignment = Alignment.Center,
            ) {
                Label(labels[i], sz(14, k), if (on) Palette.inkP else Palette.ink2P, weight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

/** The hollow ring in front of a task; tapping it marks the task done. */
@Composable
internal fun TaskRing(task: TodoTask, size: Dp, width: Dp, height: Dp) {
    // A larger target than the ring itself.
    Box(
        modifier = GlanceModifier.width(width).height(height).clickable(
            actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.ENTITY to task.entity, CompleteTaskAction.UID to task.uid),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(R.drawable.ic_task_ring, size, Palette.ink2P)
    }
}

class FilterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PanelStore.setFilter(context, glanceId, parameters[KEY] ?: "all")
    }

    companion object {
        val KEY = ActionParameters.Key<String>("filter")
    }
}

/** Ticks a task off: gone from the widget at once, then done in Home Assistant (and so in Google Tasks). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entity = parameters[ENTITY] ?: return
        val uid = parameters[UID] ?: return
        val task = HaRepository.cached(context).tasks.firstOrNull { it.entity == entity && it.uid == uid } ?: return
        PanelStore.pushHa(context, HaRepository.withoutTask(context, task))
        runCatching {
            HaFetch.completeTask(HaClient(Settings.get(context).ha()), task)
            PanelStore.pushHa(context, HaRepository.refreshTasks(context))
        }.onFailure {
            Log.w("HomePanel", "Completing a task failed", it)
            // Put it back: the next refresh would anyway.
            runCatching { PanelStore.pushHa(context, HaRepository.refreshTasks(context)) }
        }
    }

    companion object {
        val ENTITY = ActionParameters.Key<String>("entity")
        val UID = ActionParameters.Key<String>("uid")
    }
}
