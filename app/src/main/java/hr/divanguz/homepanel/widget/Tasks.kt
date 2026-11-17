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
import androidx.compose.ui.graphics.Color
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.CalendarView
import hr.divanguz.homepanel.calendar.toView
import hr.divanguz.homepanel.calendar.withTasks
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Open tasks from the to-do lists picked in settings; empty when none are picked. */
internal val LocalTasks = staticCompositionLocalOf { emptyList<TodoTask>() }

/** This widget's list switch: "all", "calendar" or "tasks". */
internal val LocalListFilter = staticCompositionLocalOf { "all" }

internal val FILTERS = listOf("all", "calendar", "tasks")

/** Overdue tasks: their day label turns amber. */
internal val OverdueP = ColorProvider(Color(0xFFE5A04F))

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

/**
 * "All · Calendar · Tasks" as three quiet words: the chosen one in primary text, underlined,
 * the others in tertiary. Shown only when task lists are picked.
 */
@Composable
internal fun ListSwitch(k: Float) {
    val labels = LocalTexts.current.filters
    val current = LocalListFilter.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FILTERS.forEachIndexed { i, f ->
            val on = f == current
            Box(
                modifier = GlanceModifier.height(dz(32, k)).padding(horizontal = dz(7, k))
                    .clickable(actionRunCallback<FilterAction>(actionParametersOf(FilterAction.KEY to f))),
                contentAlignment = Alignment.Center,
            ) {
                Label(labels[i], sz(14, k), if (on) Palette.inkP else Palette.ink3P, underline = on)
            }
        }
    }
}

/** Calendar and tasks side by side: two columns with their own header and plus, no switch. */
internal fun sideBySide(context: Context): Boolean =
    Settings.get(context).let { it.tasksSideBySide && it.taskLists.isNotEmpty() }

@Composable
internal fun SplitZone(snapshot: CalendarSnapshot?, context: Context, modifier: GlanceModifier, top: Dp, bottom: Dp) {
    val texts = LocalTexts.current
    val look = LocalLook.current
    val k = look.calendar
    val locale = texts.locale
    val today = LocalDate.now()
    val calendarApp = launchIntent(context, Packages.CALENDAR)
    val tasksApp = OpenAppActivity.intent(context, GOOGLE_TASKS)
    val events = snapshot?.takeIf { it.hasPermission }?.toView(locale = locale, allDayLabel = texts.allDay)?.rows
    val tasks = CalendarView(false, emptyList())
        .withTasks(shownTasks(context, LocalTasks.current), "tasks", texts.noDate, locale = locale).rows
    val dayLabel = { row: EventRow ->
        when {
            row.date == today -> texts.today
            look.designB -> titleCase(row.dayLabel.lowercase(locale), locale)
            else -> row.dayLabel
        }
    }
    // The hairline between the columns is a nine-patch centred in the background (a fillMaxHeight
    // child would stretch the zone); equal columns with mirrored padding meet at the centre.
    Row(
        modifier = modifier.fillMaxWidth()
            .background(ImageProvider(R.drawable.grid_divider), ContentScale.FillBounds, ColorFilter.tint(Palette.hairP)),
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 20.dp, top = top, end = 12.dp, bottom = bottom)) {
            val title = titleCase(today.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), locale)
            SplitHeader(title, null, calendarApp, newEventIntent(), k)
            when {
                snapshot != null && !snapshot.hasPermission -> SplitNote(texts.allowCalendar, settingsIntent(context), k)
                events == null -> SplitNote(texts.loading, calendarApp, k)
                events.isEmpty() -> SplitNote(texts.noEvents, calendarApp, k)
                else -> SplitList(events, k) { row, last ->
                    val first = events.indexOf(row).let { it == 0 || events[it - 1].date != row.date }
                    SplitRow(row, if (first) dayLabel(row) else "", last, k)
                }
            }
        }
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 12.dp, top = top, end = 20.dp, bottom = bottom)) {
            SplitHeader(texts.filters[2], tasks.size.takeIf { it > 0 }?.toString(), tasksApp, AddActivity.intent(context, AddActivity.TASK), k)
            if (tasks.isEmpty()) SplitNote(texts.noTasks, tasksApp, k)
            else SplitList(tasks, k) { row, last -> SplitRow(row, if (row.overdue) dayLabel(row) else "", last, k) }
        }
    }
}

@Composable
private fun SplitHeader(title: String, count: String?, tap: Intent?, plus: Intent, k: Float) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(dz(36, k)).opens(tap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, sz(15, k), Palette.ink2P)
        if (count != null) {
            Spacer(GlanceModifier.width(6.dp))
            Label(count, sz(14, k), Palette.ink3P)
        }
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier.width(dz(40, k)).height(dz(36, k)).opens(plus),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(R.drawable.ic_add, dz(20, k), Palette.ink2P)
        }
    }
}

@Composable
private fun SplitNote(text: String, tap: Intent?, k: Float) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(dz(40, k)).opens(tap), verticalAlignment = Alignment.CenterVertically) {
        Label(text, sz(15, k), Palette.ink3P)
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.SplitList(rows: List<EventRow>, k: Float, item: @Composable (EventRow, Boolean) -> Unit) {
    if (LocalStaticList.current) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            val shown = rows.take(STATIC_ROWS)
            shown.forEach { item(it, it === shown.last()) }
        }
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(rows, itemId = { it.id }) { item(it, it === rows.last()) }
        }
    }
}

/**
 * One line of a column. Events: day (first of the day only), colour dot, title. Tasks: ring and title, plus
 * the due day in amber when overdue (the column is too narrow to date every task).
 */
@Composable
private fun SplitRow(row: EventRow, day: String, isLast: Boolean, k: Float) {
    val designB = LocalLook.current.designB
    val height = if (designB) dz(32, k) else dz(44, k)
    Column(modifier = GlanceModifier.fillMaxWidth().opens(rowTap(LocalContext.current, row))) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(if (isLast || designB) height else height - 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.task != null) {
                // A target a little wider than the ring.
                TaskRing(row.task, dz(10, k), dz(10, k) + 8.dp, height - 1.dp)
                Spacer(GlanceModifier.width(4.dp))
                Label(row.title, sz(16, k), Palette.inkP, modifier = GlanceModifier.defaultWeight())
                if (day.isNotEmpty()) {
                    Spacer(GlanceModifier.width(6.dp))
                    Label(day, sz(14, k), OverdueP)
                }
            } else {
                Label(day, sz(15, k), Palette.ink2P, modifier = GlanceModifier.width(dz(58, k)))
                row.event?.color?.let { argb ->
                    Box(
                        modifier = GlanceModifier.width(dz(8, k)).height(dz(8, k))
                            .background(Color(argb or 0xFF000000.toInt())).cornerRadius(dz(4, k)),
                    ) {}
                    Spacer(GlanceModifier.width(dz(8, k)))
                }
                Label(row.title, sz(16, k), Palette.inkP, modifier = GlanceModifier.defaultWeight())
            }
        }
        if (!isLast && !designB) HDivider()
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
