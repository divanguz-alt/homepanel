package hr.divanguz.homepanel.tasks

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.divanguz.homepanel.calendar.GOOGLE_TASKS
import hr.divanguz.homepanel.calendar.newEventIntent
import hr.divanguz.homepanel.ha.HaClient
import hr.divanguz.homepanel.ha.HaFetch
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.settings.tr
import hr.divanguz.homepanel.widget.OpenAppActivity
import hr.divanguz.homepanel.widget.PanelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Sheet = Color(0xFF1B1F27)
private val Chip = Color(0xFF262A33)
private val Ink = Color(0xFFECEEF2)
private val Ink2 = Color(0xFFA3A9B5)
private val Ink3 = Color(0xFF8A909C)
private val Accent = Color(0xFFF2A65A)
private val Warn = Color(0xFFE5A04F)

/**
 * The widget's plus when tasks are on: a sheet over the home screen that asks "event or task", or
 * goes straight to a quick task entry. Tasks are added through Home Assistant (todo.add_item), so they
 * land in Google Tasks when that is the list behind the entity.
 */
class AddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val start = intent.getStringExtra(MODE) ?: CHOOSE
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Sheet, background = Sheet)) {
                AddSheet(start, close = ::finish)
            }
        }
    }

    companion object {
        private const val MODE = "mode"
        const val CHOOSE = "choose"
        const val TASK = "task"

        fun intent(context: Context, mode: String): Intent =
            Intent(context, AddActivity::class.java).putExtra(MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@Composable
private fun AddSheet(start: String, close: () -> Unit) {
    var mode by remember { mutableStateOf(start) }
    // A dim veil over the home screen; tapping it closes the sheet.
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = close),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Sheet)
                // Swallow taps so they don't reach the veil behind.
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
        ) {
            if (mode == AddActivity.CHOOSE) Chooser(onTask = { mode = AddActivity.TASK }, close = close)
            else TaskEntry(close)
        }
    }
}

@Composable
private fun Chooser(onTask: () -> Unit, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    T(tr("Add"), 22, Ink, FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigChoice(tr("Event"), tr("In the calendar"), Modifier.weight(1f)) {
            runCatching { context.startActivity(newEventIntent()) }
            close()
        }
        BigChoice(tr("Task"), tr("In your task list"), Modifier.weight(1f), onClick = onTask)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun BigChoice(title: String, summary: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Chip).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        T(title, 18, Ink, FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        T(summary, 13, Ink2)
    }
}

@Composable
private fun TaskEntry(close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext
    val store = remember { Settings.get(app) }
    val lists = remember { store.taskLists }
    var list by remember { mutableStateOf(store.lastTaskList.takeIf { it in lists } ?: lists.firstOrNull().orEmpty()) }
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val today = LocalDate.now()

    fun save() {
        val text = title.trim()
        if (text.isEmpty() || list.isEmpty() || busy) return
        busy = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    HaFetch.addTask(HaClient(store.ha()), list, text, due)
                    store.lastTaskList = list
                    HaRepository.refreshTasks(app)
                }
            }
            result.onSuccess { snapshot ->
                PanelStore.pushHa(app, snapshot)
                close()
            }.onFailure {
                busy = false
                error = tr("Couldn't reach Home Assistant")
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    T(tr("New task"), 22, Ink, FontWeight.SemiBold)
    Spacer(Modifier.height(14.dp))
    if (lists.isEmpty()) {
        T(tr("Pick a task list in Settings → Calendar first."), 15, Warn)
        Spacer(Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        placeholder = { Text(tr("What needs doing?"), color = Ink3) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, color = Ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Chip, cursorColor = Accent,
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(12.dp))
    // When: none, today, tomorrow, or a day from the date picker.
    val pickLabel = due?.takeIf { it != today && it != today.plusDays(1) }
        ?.format(DateTimeFormatter.ofPattern("EEE d MMM")) ?: tr("Pick a day…")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(tr("No date"), due == null) { due = null }
        Pill(tr("Today"), due == today) { due = today }
        Pill(tr("Tomorrow"), due == today.plusDays(1)) { due = today.plusDays(1) }
        Pill(pickLabel, due != null && due != today && due != today.plusDays(1)) {
            val d = due ?: today
            DatePickerDialog(context, { _, y, m, day -> due = LocalDate.of(y, m + 1, day) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }
    }
    if (lists.size > 1) {
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lists.forEach { id -> Pill(listName(id), id == list) { list = id } }
        }
    }
    error?.let {
        Spacer(Modifier.height(10.dp))
        T(it, 14, Warn)
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(
            tr("Open Google Tasks"), 15, Ink2, FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                runCatching { context.startActivity(OpenAppActivity.intent(context, GOOGLE_TASKS)) }
                close()
            }.padding(vertical = 10.dp, horizontal = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        val ready = title.isNotBlank() && list.isNotEmpty() && !busy
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ready) Accent else Chip)
                .clickable(enabled = ready) { save() }.padding(horizontal = 26.dp, vertical = 12.dp),
        ) {
            T(if (busy) tr("Adding…") else tr("Add"), 16, if (ready) Color(0xFF1A1206) else Ink3, FontWeight.SemiBold)
        }
    }
}

/** "todo.my_tasks" → "My tasks": the entity id is all the sheet has without asking HA. */
internal fun listName(id: String): String =
    id.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink.copy(alpha = 0.16f) else Chip)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        T(text, 14, if (on) Ink else Ink2, if (on) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun T(text: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, letterSpacing = 0.sp, lineHeight = (size * 1.3f).sp)
}
