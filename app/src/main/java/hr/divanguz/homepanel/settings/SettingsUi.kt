package hr.divanguz.homepanel.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import hr.divanguz.homepanel.calendar.CalendarSource
import hr.divanguz.homepanel.claude.ClaudeLoginActivity
import hr.divanguz.homepanel.claude.ClaudeUsage
import hr.divanguz.homepanel.ha.CarImage
import hr.divanguz.homepanel.ha.CarMap
import hr.divanguz.homepanel.ha.HaClient
import hr.divanguz.homepanel.ha.HaFetch
import hr.divanguz.homepanel.ha.HaRepository
import hr.divanguz.homepanel.weather.OpenMeteo
import hr.divanguz.homepanel.weather.WeatherProvider
import hr.divanguz.homepanel.widget.Conditions
import hr.divanguz.homepanel.widget.Look
import hr.divanguz.homepanel.widget.PanelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// One UI-like settings: a big title, then grouped rows (title + one-line summary) that open sub-pages.
// No status dots or explanatory paragraphs; colour only appears when something needs attention.

private val Ground = Color(0xFF0B0D12)
private val Group = Color(0xFF16191F)
private val Hair = Color(0xFF242830)
private val Ink = Color(0xFFECEEF2)
private val Ink2 = Color(0xFFA3A9B5)
private val Ink3 = Color(0xFF7E8490)
private val Accent = Color(0xFFF2A65A)
private val Attention = Color(0xFFE5A04F)
private val Danger = Color(0xFFE36A5C)

private val Scheme = darkColorScheme(
    background = Ground, surface = Group, onBackground = Ink, onSurface = Ink, onSurfaceVariant = Ink2,
    primary = Accent, onPrimary = Color(0xFF1A1206), outline = Color(0xFF3A3F4A), outlineVariant = Hair,
)

private enum class Page(val title: String) {
    Main("HomePanel"), HomeAssistant("Home Assistant"), Weather("Weather"), Calendar("Calendar"), Car("Car"),
    Claude("Claude"), Appearance("Appearance"), Updates("Updates")
}

/** A row summary; [warn] tints it when the user should act. */
private data class Summary(val text: String, val warn: Boolean = false)

@Composable
fun SettingsApp(goHome: () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ground) { Screens(goHome) }
    }
}

@Composable
private fun Screens(goHome: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember { Settings.get(app) }

    var page by remember { mutableStateOf(Page.Main) }
    var busy by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf<String?>(null) }

    var haSummary by remember { mutableStateOf(Summary("…")) }
    var weatherSummary by remember { mutableStateOf(Summary("…")) }
    var calendarSummary by remember { mutableStateOf(Summary("…")) }
    var claudeSummary by remember { mutableStateOf(Summary("…")) }
    var carSummary by remember { mutableStateOf(Summary("…")) }
    var batteryOk by remember { mutableStateOf(true) }

    fun reload() {
        batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        scope.launch {
            val cal = withContext(Dispatchers.IO) { CalendarSource.load(app) }
            calendarSummary = if (cal.hasPermission) Summary("${cal.events.size} events in the next ${store.calendarDays} days")
            else Summary("Tap to allow access", warn = true)
            val ha = withContext(Dispatchers.IO) { HaRepository.cached(app) }
            haSummary = when {
                !store.ha().isConfigured -> Summary("Not set up", warn = true)
                ha.error != null -> Summary(ha.error, warn = true)
                else -> Summary("Connected · ${ago(ha.carAt)}")
            }
            weatherSummary = ha.weather?.let { w ->
                Summary(listOfNotNull(w.source, w.place, w.temperature?.let { "${it.roundToInt()}°" }).joinToString(" · "))
            } ?: Summary(store.weatherProvider.label)
            carSummary = Summary(
                listOfNotNull(
                    store.carName.ifBlank { null },
                    ha.car?.locked?.let { if (it) "locked" else "unlocked" },
                    "${store.mapStyle.replaceFirstChar { it.uppercase() }} map",
                ).joinToString(" · "),
                warn = ha.car?.locked == false,
            )
            val c = withContext(Dispatchers.IO) { ClaudeUsage.cached(app) }
            claudeSummary = when {
                store.claudeSession.isNullOrBlank() -> Summary("Not signed in", warn = true)
                c.needsLogin -> Summary("Session expired · sign in again", warn = true)
                c.error != null -> Summary(c.error, warn = true)
                else -> Summary("${c.fiveHour?.roundToInt() ?: "–"}% now · ${c.week?.roundToInt() ?: "–"}% this week")
            }
        }
    }

    fun refresh(then: () -> Unit = {}) = scope.launch {
        busy = true
        val n = withContext(Dispatchers.IO) { runCatching { PanelStore.refreshAll(app, forceWeather = true) }.getOrDefault(0) }
        busy = false
        lastRefresh = if (n == 0) "Saved · no widget on the home screen yet" else "Saved just now"
        reload()
        then()
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    LaunchedEffect(Unit) {
        if (!CalendarSource.hasPermission(context)) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }
    BackHandler(enabled = page != Page.Main) { page = Page.Main; reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        if (page == Page.Main) {
            Text(
                "HomePanel", fontSize = 34.sp, fontWeight = FontWeight.Normal, color = Ink,
                modifier = Modifier.padding(start = 10.dp, top = 72.dp, bottom = 4.dp),
            )
            Text(
                lastRefresh ?: "Weather, calendar, car and Claude", fontSize = 14.sp, color = Ink3,
                modifier = Modifier.padding(start = 10.dp, bottom = 28.dp),
            )
        } else {
            PageHeader(page.title) { page = Page.Main; reload() }
        }

        when (page) {
            Page.Main -> MainPage(
                busy = busy,
                ha = haSummary, weather = weatherSummary, calendar = calendarSummary, claude = claudeSummary, car = carSummary,
                batteryOk = batteryOk,
                onRefresh = { refresh() },
                onRefreshAndLeave = { refresh(goHome) },
                open = { page = it },
                onCalendar = {
                    if (CalendarSource.hasPermission(context)) page = Page.Calendar
                    else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                },
                onBattery = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            Page.HomeAssistant -> HomeAssistantPage(store, onChanged = { refresh() })
            Page.Weather -> WeatherPage(store, onChanged = { refresh() })
            Page.Calendar -> CalendarPage(store, onChanged = { refresh() })
            Page.Car -> CarPage(store, onChanged = { refresh() })
            Page.Claude -> ClaudePage(store, onChanged = { reload() })
            Page.Appearance -> AppearancePage(store)
            Page.Updates -> UpdatesPage(store)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MainPage(
    busy: Boolean,
    ha: Summary,
    weather: Summary,
    calendar: Summary,
    claude: Summary,
    car: Summary,
    batteryOk: Boolean,
    onRefresh: () -> Unit,
    onRefreshAndLeave: () -> Unit,
    open: (Page) -> Unit,
    onCalendar: () -> Unit,
    onBattery: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        // "Save" stores everything and refreshes the widget; the second one also returns to the home screen.
        Pill("Save", filled = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = onRefresh)
        Pill(if (busy) "Saving…" else "Save and go home", filled = true, enabled = !busy, modifier = Modifier.weight(1.4f), onClick = onRefreshAndLeave)
    }
    Spacer(Modifier.height(24.dp))
    Grouped {
        Item("Home Assistant", ha, onClick = { open(Page.HomeAssistant) })
        Item("Weather", weather, onClick = { open(Page.Weather) })
        Item("Calendar", calendar, onClick = onCalendar)
        Item("Car", car, onClick = { open(Page.Car) })
        Item("Claude", claude, onClick = { open(Page.Claude) }, last = true)
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        Item("Appearance", Summary(if (Settings.get(LocalContext.current).look().designB) "Design B · sizes" else "Design A · sizes"), onClick = { open(Page.Appearance) })
        Item(
            "Background refresh",
            if (batteryOk) Summary("Unrestricted") else Summary("Optimized by the system · tap to allow", warn = true),
            onClick = if (batteryOk) null else onBattery,
            last = true,
        )
    }
    Spacer(Modifier.height(16.dp))
    // Footer: version and the source repository (private; opens in the browser / GitHub app).
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            ?.let { "${it.versionName} (${it.longVersionCode})" }.orEmpty()
    }
    Grouped {
        Item("Updates", Summary("Version $version · check GitHub for a newer one"), onClick = { open(Page.Updates) })
        Item("Source on GitHub", Summary("divanguz-alt/homepanel"), onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, last = true)
    }
    Text(
        "HomePanel $version", fontSize = 13.sp, color = Ink3,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private const val REPO_URL = "https://github.com/divanguz-alt/homepanel"

/** Check GitHub Releases with the user's read-only token; download and hand the APK to the installer. */
@Composable
private fun UpdatesPage(store: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val installed = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).let { it.longVersionCode.toInt() to (it.versionName ?: "") }
    }
    var token by remember { mutableStateOf(store.githubToken.orEmpty()) }
    var status by remember { mutableStateOf<Summary?>(null) }
    var release by remember { mutableStateOf<hr.divanguz.homepanel.updates.Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showHelp by remember { mutableStateOf(store.githubToken.isNullOrBlank()) }
    val updater = hr.divanguz.homepanel.updates.Updater

    fun check() {
        busy = true
        status = Summary("Checking…")
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { updater.latest(store.githubToken.orEmpty()) } }
            r.onSuccess {
                release = it.takeIf { rel -> rel.versionCode > installed.first }
                status = if (release != null) Summary("Version ${it.name} is available (%.1f MB)".format(it.size / 1_048_576f))
                else Summary("You have the latest version (${installed.second})")
            }.onFailure { status = Summary(it.message ?: "Check failed", warn = true) }
            busy = false
        }
    }

    fun downloadAndInstall(rel: hr.divanguz.homepanel.updates.Updater.Release) {
        if (updater.needsInstallPermission(context)) {
            status = Summary("Allow HomePanel to install apps, then tap again", warn = true)
            context.startActivity(updater.installPermissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy = true
        progress = 0f
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { updater.download(app, rel, store.githubToken.orEmpty()) { p -> progress = p } }
            }
            progress = null
            busy = false
            r.onSuccess { updater.install(context, it) }
                .onFailure { status = Summary(it.message ?: "Download failed", warn = true) }
        }
    }

    Grouped {
        Item("Installed", Summary(installed.second), onClick = null)
        Item(
            when {
                progress != null -> "Downloading… ${((progress ?: 0f) * 100).roundToInt()}%"
                release != null -> "Download and install ${release!!.name}"
                else -> "Check for updates"
            },
            status,
            onClick = when {
                busy || store.githubToken.isNullOrBlank() -> null
                release != null -> ({ downloadAndInstall(release!!) })
                else -> ({ check() })
            },
            last = true,
        )
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("GitHub token")
    Grouped(padded = true) {
        Field("Token (read-only)", token, KeyboardType.Password, secret = true, hint = "github_pat_…") { token = it.trim() }
        Pill("Save token", filled = false, enabled = token.isNotBlank() && token != store.githubToken, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            store.githubToken = token
            status = null
            check()
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (showHelp) "Hide how to make a token" else "How to make a token",
        color = Accent, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showHelp = !showHelp }.padding(10.dp),
    )
    if (showHelp) {
        Text(
            "On github.com, signed in as divanguz-alt: Settings → Developer settings → Personal access tokens → " +
                "Fine-grained tokens → Generate new token. Repository access: Only select repositories → homepanel. " +
                "Permissions → Repository permissions → Contents: Read-only. Generate, copy the github_pat_… token and paste it above.",
            fontSize = 14.sp, color = Ink2, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun HomeAssistantPage(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var ha by remember { mutableStateOf(store.ha()) }
    var showEntities by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    SectionLabel("Connection")
    Grouped(padded = true) {
        Field("Local address", ha.internalUrl, KeyboardType.Uri, hint = "http:// works only on the home network") { ha = ha.copy(internalUrl = it) }
        Field("Remote address", ha.externalUrl, KeyboardType.Uri, hint = "Tailscale or Nabu Casa") { ha = ha.copy(externalUrl = it) }
        Field("Access token", ha.token, KeyboardType.Password, secret = true) { ha = ha.copy(token = it) }
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        Item("Entities", Summary(if (showEntities) "Hide" else "Weather, car, location and more"), onClick = { showEntities = !showEntities }, last = !showEntities)
        if (showEntities) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Field("Weather (fallback)", ha.weatherEntity) { ha = ha.copy(weatherEntity = it) }
                Field("Air quality", ha.aqiEntity, hint = "found by Test") { ha = ha.copy(aqiEntity = it) }
                Field("Phone location", ha.phoneEntity, hint = "found by Test") { ha = ha.copy(phoneEntity = it) }
                Field("Car address", ha.carAddressEntity) { ha = ha.copy(carAddressEntity = it) }
                Field("Range", ha.rangeEntity) { ha = ha.copy(rangeEntity = it) }
                Field("Range (fallback)", ha.rangeFallbackEntity) { ha = ha.copy(rangeFallbackEntity = it) }
                Field("Car picture", ha.carImageEntity) { ha = ha.copy(carImageEntity = it) }
                Field("Car location", ha.locationEntity) { ha = ha.copy(locationEntity = it) }
                Field("Car lock", ha.lockEntity, hint = "found by Test") { ha = ha.copy(lockEntity = it) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Pill("Save", filled = false, enabled = !testing, modifier = Modifier.weight(1f)) {
            store.saveHa(ha)
            ha = store.ha()
            report = null
            onChanged()
        }
        Pill(if (testing) "Testing…" else "Save and test", filled = true, enabled = !testing, modifier = Modifier.weight(1.4f)) {
            store.saveHa(ha)
            val saved = store.ha()
            testing = true
            scope.launch {
                val (text, updated) = withContext(Dispatchers.IO) { testHa(app, saved) }
                if (updated != saved) store.saveHa(updated)
                ha = store.ha()
                report = text
                testing = false
                onChanged()
            }
        }
    }
    report?.let {
        Spacer(Modifier.height(16.dp))
        Text(
            it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Ink2, lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Group).padding(16.dp),
        )
    }
}

@Composable
private fun WeatherPage(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(store.weatherProvider) }
    var comparing by remember { mutableStateOf(false) }
    var where by remember { mutableStateOf<String?>(null) }
    var readings by remember { mutableStateOf<Map<WeatherProvider, Summary>>(emptyMap()) }

    fun compare() {
        comparing = true
        scope.launch {
            val (place, rows) = withContext(Dispatchers.IO) {
                val s = store.ha()
                val client = if (s.isConfigured) HaClient(s) else null
                val at = client?.let { HaRepository.comparePlace(it, s) }
                    ?: return@withContext "Needs Home Assistant for your location" to emptyMap()
                val rows = HaRepository.compare(app, client, s, at.first, at.second).associate { (p, r) ->
                    p to r.fold(
                        { w ->
                            val tomorrow = w.days.getOrNull(1)?.let { d -> " · tomorrow ${d.high?.roundToInt() ?: "–"}°" }.orEmpty()
                            Summary("${w.temperature?.roundToInt()?.let { "$it°" } ?: "–"} ${Conditions.label(w.condition).lowercase()}$tomorrow")
                        },
                        { e -> Summary(if (p == WeatherProvider.DHMZ) "Only available in Croatia" else (e.message ?: "Unavailable"), warn = true) },
                    )
                }
                val name = HaRepository.cached(app).weather?.place
                (name?.let { "Now, in $it" } ?: "Now, at your location") to rows
            }
            where = place
            readings = rows
            comparing = false
        }
    }
    LaunchedEffect(Unit) { compare() }

    PhoneLocationSection(store, onChanged = { onChanged(); compare() })
    Spacer(Modifier.height(16.dp))
    SectionLabel(where ?: "Source")
    Grouped {
        WeatherProvider.entries.forEachIndexed { i, p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        provider = p
                        store.weatherProvider = p
                        onChanged()
                    }
                    .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.label, fontSize = 17.sp, color = Ink)
                    val r = readings[p]
                    Text(
                        r?.text ?: if (comparing) "…" else p.note,
                        fontSize = 14.sp,
                        color = if (r?.warn == true) Ink3 else Ink2,
                    )
                }
                RadioButton(
                    selected = provider == p, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Ink3),
                )
            }
            if (i < WeatherProvider.entries.lastIndex) Divider()
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (comparing) "Checking every source…" else "Check again",
        color = if (comparing) Ink3 else Accent, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !comparing) { compare() }.padding(10.dp),
    )

    var every by remember { mutableStateOf(store.weatherEveryMin) }
    var moveKm by remember { mutableStateOf(store.weatherMoveKm) }
    var european by remember { mutableStateOf(store.aqiEuropean) }
    var warnings by remember { mutableStateOf(store.warnings) }
    var showSource by remember { mutableStateOf(store.showSource) }
    var showRain by remember { mutableStateOf(store.showRain) }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Updates")
    Grouped(padded = true) {
        Choice("Refresh every", listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h"), every) {
            every = it; store.weatherEveryMin = it
        }
        Choice("New forecast after moving", listOf(1 to "1 km", 3 to "3 km", 10 to "10 km"), moveKm) {
            moveKm = it; store.weatherMoveKm = it
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Air quality and warnings")
    Grouped(padded = true) {
        Choice("Air quality index", listOf(false to "US AQI", true to "European"), european) {
            european = it; store.aqiEuropean = it; onChanged()
        }
        ToggleRow(
            "Official warnings",
            if (provider == WeatherProvider.DHMZ) "From DHMZ for your county"
            else "Only with DHMZ as the source (Croatia). Other sources don't publish warnings.",
            warnings, warn = warnings && provider != WeatherProvider.DHMZ,
        ) { warnings = it; store.warnings = it; onChanged() }
        ToggleRow("Rain coming soon", "\"Rain from 14:20\" / \"Rain until 15:10\" under the conditions, only when it rains", showRain) {
            showRain = it; store.showRain = it; onChanged()
        }
        ToggleRow("Show source on the widget", "Small grey \"Source: …\" in the weather zone", showSource) {
            showSource = it; store.showSource = it; onChanged()
        }
    }
}

@Composable
private fun CalendarPage(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    var days by remember { mutableStateOf(store.calendarDays) }
    Grouped(padded = true) {
        Choice("Show events for", listOf(7 to "7 days", 14 to "14 days", 30 to "30 days"), days) {
            days = it; store.calendarDays = it; onChanged()
        }
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        Item("Open calendar", Summary("Your default calendar app"), onClick = {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, last = true)
    }
}

@Composable
private fun CarPage(store: Settings, onChanged: () -> Unit) {
    val app = LocalContext.current.applicationContext
    var name by remember { mutableStateOf(store.carName) }
    var style by remember { mutableStateOf(store.mapStyle) }
    var zoom by remember { mutableStateOf(store.mapZoom) }
    var ha by remember { mutableStateOf(store.ha()) }
    fun redrawMap() {
        hr.divanguz.homepanel.ha.CarMap.invalidate(app)
        onChanged()
    }
    fun saveHa(next: HaSettings) {
        store.saveHa(next)
        ha = store.ha()
        onChanged()
    }
    Grouped(padded = true) {
        Field("Name on the widget", name, hint = "e.g. the model; blank shows \"Car\"") { name = it }
        Pill("Save name", filled = false, enabled = name != store.carName, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            store.carName = name
            onChanged()
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("From Home Assistant")
    Grouped {
        EntityChooser(store, "Parked address", ha.carAddressEntity, listOf("sensor")) { saveHa(ha.copy(carAddressEntity = it)) }
        EntityChooser(store, "Range", ha.rangeEntity, listOf("sensor")) { saveHa(ha.copy(rangeEntity = it)) }
        EntityChooser(store, "Range (backup)", ha.rangeFallbackEntity, listOf("sensor")) { saveHa(ha.copy(rangeFallbackEntity = it)) }
        EntityChooser(store, "Picture", ha.carImageEntity, listOf("image")) { saveHa(ha.copy(carImageEntity = it)) }
        EntityChooser(store, "Location (for the map)", ha.locationEntity, listOf("device_tracker", "sensor"), last = true) {
            saveHa(ha.copy(locationEntity = it)); redrawMap()
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Tapping the car opens")
    CarAppChooser(store, onChanged)
    Spacer(Modifier.height(16.dp))
    SectionLabel("Map")
    Grouped(padded = true) {
        Choice("Style", listOf("fiord" to "Fiord", "dark" to "Dark", "positron" to "Light"), style) {
            style = it; store.mapStyle = it; redrawMap()
        }
        SizeRow("Zoom", zoom, 14f..17.5f, { "%.1f".format(it) }) {
            zoom = it; store.mapZoom = it; redrawMap()
        }
    }
    Spacer(Modifier.height(16.dp))
    LockSection(store, onChanged)
}

/** A row showing one entity setting; tapping it lists matching HA entities with a search box. */
@Composable
private fun EntityChooser(
    store: Settings,
    title: String,
    current: String,
    domains: List<String>,
    last: Boolean = false,
    onPick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<List<HaFetch.EntityOption>?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Item(
        title,
        error?.let { Summary(it, warn = true) } ?: Summary(current.ifBlank { "Not set · tap to choose" }, warn = current.isBlank()),
        onClick = {
            open = !open
            if (open && options == null) scope.launch {
                val s = store.ha()
                options = withContext(Dispatchers.IO) {
                    if (!s.isConfigured) null else runCatching { HaFetch.entities(HaClient(s), domains) }.getOrNull()
                }
                error = if (options == null) "Couldn't reach Home Assistant" else null
            }
        },
        last = last && !open,
    )
    if (open) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Field("Search", query) { query = it }
        }
        val shown = options.orEmpty()
            .filter { query.isBlank() || it.entityId.contains(query, true) || it.name.contains(query, true) }
            .take(40)
        shown.forEach { o ->
            Item(o.name, Summary("${o.entityId} · ${o.state.take(40)}"), onClick = {
                onPick(o.entityId)
                open = false
            })
        }
        if (current.isNotBlank()) Item("Clear", null, onClick = { onPick(""); open = false }, danger = true, last = last)
        else if (last) Spacer(Modifier.height(4.dp))
    }
}

/** Which installed app the car tap opens; Home Assistant when none is chosen. */
@Composable
private fun CarAppChooser(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var chosen by remember { mutableStateOf(store.carApp) }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val apps = remember(open) {
        if (!open) emptyList()
        else pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
    fun label(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    Grouped {
        Item(
            if (chosen.isBlank()) "Home Assistant" else label(chosen),
            Summary(if (open) "Pick an app below" else "Tap to change"),
            onClick = { open = !open },
            last = !open,
        )
        if (open) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Field("Search apps", query) { query = it }
            }
            Item("Home Assistant (default)", null, onClick = {
                store.carApp = ""; chosen = ""; open = false; onChanged()
            })
            apps.filter { query.isBlank() || it.first.contains(query, true) }.take(40).forEachIndexed { i, (name, pkg) ->
                Item(name, Summary(pkg), onClick = {
                    store.carApp = pkg; chosen = pkg; open = false; onChanged()
                }, last = i == 39)
            }
        }
    }
}

/** Which device_tracker is "where I am" for the weather: a picker of everything with coordinates. */
@Composable
private fun PhoneLocationSection(store: Settings, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entity by remember { mutableStateOf(store.ha().phoneEntity) }
    var candidates by remember { mutableStateOf<List<HaFetch.TrackerCandidate>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    SectionLabel("Your location")
    Grouped {
        Item(
            entity.ifBlank { "Not set" },
            Summary(if (entity.isBlank()) "Forecast is for home until you pick your phone" else "Forecast follows this tracker", warn = entity.isBlank()),
            onClick = null,
        )
        Item(
            if (searching) "Searching…" else "Choose from Home Assistant",
            note?.let { Summary(it, warn = true) } ?: Summary("Trackers that report coordinates"),
            onClick = if (searching) null else ({
                searching = true
                note = null
                scope.launch {
                    val found = withContext(Dispatchers.IO) {
                        val s = store.ha()
                        if (!s.isConfigured) null else runCatching { HaFetch.trackerCandidates(HaClient(s), s) }.getOrNull()
                    }
                    candidates = found
                    note = when {
                        found == null -> "Couldn't reach Home Assistant"
                        found.isEmpty() -> "No tracker with coordinates. In the HA app, turn on location sending."
                        else -> null
                    }
                    searching = false
                }
            }),
            last = candidates.isNullOrEmpty(),
        )
        candidates?.forEachIndexed { i, c ->
            Item(
                c.name,
                Summary("${c.entityId} · ${if (c.gps) "GPS" else "not GPS"} · ${c.lastUpdated.take(16).replace('T', ' ')}"),
                onClick = {
                    store.saveHa(store.ha().copy(phoneEntity = c.entityId))
                    entity = c.entityId
                    candidates = null
                    onChanged()
                },
                last = i == candidates!!.lastIndex,
            )
        }
    }
}

/** Which HA entity drives the padlock next to the car's name, with a picker of everything lock-like. */
@Composable
private fun LockSection(store: Settings, onChanged: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var entity by remember { mutableStateOf(store.ha().lockEntity) }
    var candidates by remember { mutableStateOf<List<HaFetch.LockCandidate>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val locked = remember(entity) { HaRepository.cached(app).car?.locked }

    fun choose(id: String) {
        store.saveHa(store.ha().copy(lockEntity = id))
        entity = id
        candidates = null
        onChanged()
    }

    SectionLabel("Lock")
    Grouped {
        Item(
            when (entity) { "" -> "No lock entity"; "none" -> "Padlock hidden"; else -> entity },
            when {
                entity == "none" -> Summary("Pick an entity below to show it again")
                entity.isBlank() -> Summary("The padlock appears once one is chosen", warn = true)
                locked == true -> Summary("Locked")
                locked == false -> Summary("Unlocked", warn = true)
                else -> Summary("State unknown until the next refresh")
            },
            onClick = null,
        )
        Item(
            if (searching) "Searching…" else "Find locks in Home Assistant",
            note?.let { Summary(it, warn = true) },
            onClick = if (searching) null else ({
                searching = true
                note = null
                scope.launch {
                    val found = withContext(Dispatchers.IO) {
                        val s = store.ha()
                        if (!s.isConfigured) null else runCatching { HaFetch.lockCandidates(HaClient(s)) }.getOrNull()
                    }
                    candidates = found
                    note = when {
                        found == null -> "Couldn't reach Home Assistant"
                        found.isEmpty() -> "Nothing lock-like in Home Assistant"
                        else -> null
                    }
                    searching = false
                }
            }),
            last = candidates.isNullOrEmpty() && entity.isBlank(),
        )
        candidates?.forEach { c ->
            Item(c.name, Summary("${c.entityId} · ${c.state}"), onClick = { choose(c.entityId) })
        }
        if (entity.isNotBlank() && entity != "none") {
            // "none" (not blank) so automatic detection doesn't bring it back.
            Item("Don't show a padlock", null, onClick = { choose("none") }, danger = true, last = true)
        }
    }
}

@Composable
private fun ClaudePage(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(!store.claudeSession.isNullOrBlank()) }
    var status by remember { mutableStateOf<Summary?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    fun fetch(prefix: String = "") {
        working = true
        scope.launch {
            val c = withContext(Dispatchers.IO) { runCatching { PanelStore.refreshClaude(app) }; ClaudeUsage.cached(app) }
            signedIn = !store.claudeSession.isNullOrBlank()
            status = when {
                c.needsLogin -> Summary("${prefix}Not signed in", warn = true)
                c.error != null -> Summary("$prefix${c.error}", warn = true)
                else -> Summary("$prefix${c.fiveHour?.roundToInt()}% of the 5-hour limit · ${c.week?.roundToInt()}% of the week")
            }
            working = false
            onChanged()
        }
    }
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        signedIn = !store.claudeSession.isNullOrBlank()
        if (signedIn) fetch()
    }

    Grouped {
        Item(
            if (signedIn) "Signed in" else "Sign in",
            status ?: Summary(if (signedIn) "claude.ai" else "Opens claude.ai", warn = false),
            onClick = { signIn.launch(Intent(context, ClaudeLoginActivity::class.java)) },
        )
        Item("Fetch usage now", Summary(if (working) "Fetching…" else "Same as tapping the bars on the widget"), onClick = if (working) null else ({ fetch() }))
        Item("Use a session key", Summary("If signing in here doesn't work"), onClick = { pasting = !pasting }, last = !pasting && !signedIn)
        if (pasting) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "On a computer, sign in at claude.ai and copy the sessionKey cookie (DevTools → Application → Cookies).",
                    fontSize = 14.sp, color = Ink2,
                )
                Field("sessionKey", key, KeyboardType.Password, secret = true) { key = it.trim() }
                Pill("Use this key", filled = true, enabled = key.startsWith("sk-ant-") && !working, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    store.clearClaude()
                    store.claudeSession = key
                    key = ""
                    pasting = false
                    fetch()
                }
            }
        }
        if (signedIn) {
            Item("Sign out", null, onClick = {
                store.clearClaude()
                CookieManager.getInstance().removeAllCookies(null)
                signedIn = false
                status = null
                fetch()
            }, danger = true, last = true)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Usage comes from the page claude.ai uses for its own usage screen. It isn't an official API and may stop working.",
        fontSize = 13.sp, color = Ink3, modifier = Modifier.padding(horizontal = 10.dp),
    )
}

@Composable
private fun AppearancePage(store: Settings) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var look by remember { mutableStateOf(store.look()) }
    fun save(next: Look) {
        look = next
        store.saveLook(next)
        scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
    }
    fun pct(v: Float) = "${(v * 100).roundToInt()}%"

    var language by remember { mutableStateOf(store.language) }
    Grouped(padded = true) {
        Choice("Widget language", listOf("en" to "English", "hr" to "Hrvatski"), language) {
            language = it
            store.language = it
            scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Design")
    Grouped {
        listOf(false to ("A" to "Date line, refresh button, rows with dividers"), true to ("B" to "Bigger weather, grouped calendar, grey map")).forEachIndexed { i, (b, t) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { save(look.copy(designB = b)) }.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t.first, fontSize = 17.sp, color = Ink)
                    Text(t.second, fontSize = 14.sp, color = Ink2)
                }
                RadioButton(selected = look.designB == b, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Ink3))
            }
            if (i == 0) Divider()
        }
    }
    if (look.designB) {
        var showDate by remember { mutableStateOf(store.showDate) }
        Spacer(Modifier.height(16.dp))
        Grouped(padded = true) {
            ToggleRow("Show date", "Next to the city: \"Split • Tue 20 Oct\"", showDate) {
                showDate = it
                store.showDate = it
                scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Sizes")
    Grouped(padded = true) {
        SizeRow("Date and city", look.header, 0.8f..1.6f, ::pct) { save(look.copy(header = it)) }
        // Stored in sp (84 = 100%); shown as a percentage like the others.
        SizeRow("Temperature", look.temperatureSp / 84f, 0.7f..1.7f, ::pct) { save(look.copy(temperatureSp = (it * 84).roundToInt())) }
        SizeRow("Conditions and AQI", look.conditions, 0.8f..1.6f, ::pct) { save(look.copy(conditions = it)) }
        SizeRow("Forecast", look.forecast, 0.7f..1.6f, ::pct) { save(look.copy(forecast = it)) }
        SizeRow("Calendar", look.calendar, 0.8f..1.5f, ::pct) { save(look.copy(calendar = it)) }
        SizeRow("Car and Claude", look.bottom, 0.8f..1.5f, ::pct) { save(look.copy(bottom = it)) }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Reset sizes", color = Accent, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { save(Look(designB = look.designB)) }.padding(10.dp),
    )
}

// ---- building blocks -------------------------------------------------------------------------

@Composable
private fun PageHeader(title: String, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = back),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val stroke = 2.dp.toPx()
                drawLine(Ink, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.3f, w * 0.5f), stroke, StrokeCap.Round)
                drawLine(Ink, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.62f, w * 0.82f), stroke, StrokeCap.Round)
            }
        }
        Text(title, fontSize = 26.sp, color = Ink, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, color = Ink3, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
}

@Composable
private fun Grouped(padded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Group)
            .then(if (padded) Modifier.padding(horizontal = 16.dp, vertical = 12.dp) else Modifier),
        content = content,
    )
}

@Composable
private fun Item(title: String, summary: Summary?, onClick: (() -> Unit)?, last: Boolean = false, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = if (danger) Danger else Ink)
            summary?.let { Text(it.text, fontSize = 14.sp, color = if (it.warn) Attention else Ink2, maxLines = 2) }
        }
    }
    if (!last) Divider()
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.padding(start = 18.dp, end = 18.dp).fillMaxWidth().height(1.dp).background(Hair))
}

@Composable
private fun Pill(text: String, filled: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (filled) Accent.copy(alpha = if (enabled) 1f else 0.5f) else Group)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (filled) Color(0xFF1A1206) else Ink)
    }
}

/** A label above a row of pills; the selected one is filled. */
@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 17.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, text) ->
                val on = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) Accent else Hair)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, fontSize = 14.sp, color = if (on) Color(0xFF1A1206) else Ink2, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, summary: String, checked: Boolean, warn: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Ink)
            Text(summary, fontSize = 14.sp, color = if (warn) Attention else Ink2)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF1A1206), uncheckedTrackColor = Hair, uncheckedThumbColor = Ink3, uncheckedBorderColor = Hair),
        )
    }
}

@Composable
private fun SizeRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onDone: (Float) -> Unit,
) {
    // Local while dragging; saved (and the widget redrawn) when the finger lifts.
    var current by remember(value) { mutableStateOf(value) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
            Text(format(current), fontSize = 15.sp, color = Ink2)
        }
        Slider(
            value = current, onValueChange = { current = it }, valueRange = range, onValueChangeFinished = { onDone(current) },
            colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Accent, inactiveTrackColor = Hair),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Ascii,
    secret: Boolean = false,
    hint: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it, color = Ink3) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Hair, focusedLabelColor = Accent, unfocusedLabelColor = Ink3,
            cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private fun ago(at: Long): String {
    if (at <= 0) return "never updated"
    val min = (System.currentTimeMillis() - at) / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 48 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} days ago"
    }
}

/**
 * Runs the same fetches as the widget and describes the result. Also fills in the AQI and phone
 * tracker entities when they're blank; returns the possibly updated settings.
 */
private fun testHa(context: Context, input: HaSettings): Pair<String, HaSettings> {
    if (!input.isConfigured) return "Fill in a URL and the token first." to input
    val client = HaClient(input)
    var aqiNote = ""
    var settings = if (input.aqiEntity.isBlank()) {
        val found = runCatching { HaFetch.detectAqi(client) }.getOrNull()
        aqiNote = if (found != null) "AQI: found $found\n" else "AQI: no sensor with device_class aqi found\n"
        found?.let { input.copy(aqiEntity = it) } ?: input
    } else input
    var phoneNote = ""
    if (settings.phoneEntity.isBlank()) {
        val found = runCatching { HaFetch.detectPhone(client, settings) }.getOrNull()
        phoneNote = if (found != null) "Phone location: found $found\n" else "Phone location: no GPS tracker found\n"
        if (found != null) settings = settings.copy(phoneEntity = found)
    }
    val phone = runCatching { HaFetch.coordinates(client, settings.phoneEntity) }.getOrNull()
    val weather = runCatching { HaFetch.weather(client, settings) }
    val local = phone?.let { (lat, lon) -> runCatching { OpenMeteo.fetch(lat, lon) } }
    val car = runCatching { HaFetch.car(client, settings) }
    val image = runCatching { CarImage.refresh(context, client, settings) }.getOrElse { "failed (${it.message})" }
    val map = runCatching { CarMap.refresh(context, client, settings, car.getOrNull()?.address) }
        .getOrElse { "failed (${it.message})" }
    val report = buildString {
        append("Connected via: ${client.usedBaseUrl ?: "nothing answered"}\n")
        append(aqiNote)
        append(phoneNote)
        weather.fold(
            {
                append("HA weather (home): ${it.condition}, ${it.temperature ?: "?"}° · tomorrow ${it.tomorrowCondition ?: "?"} ${it.tomorrowHigh ?: ""}")
                append(" · AQI ${it.aqi ?: "–"}\n")
            },
            { append("HA weather (home): failed (${it.message})\n") },
        )
        when {
            phone == null -> append("Weather here: ${settings.phoneEntity.ifBlank { "no phone tracker set" }} has no coordinates; the widget uses HA's home location\n")
            else -> local?.fold(
                { append("Weather here (Open-Meteo, %.3f, %.3f): ${it.condition}, ${it.temperature ?: "?"}° · AQI ${it.aqi ?: "–"}\n".format(phone.first, phone.second)) },
                { append("Weather here: Open-Meteo failed (${it.message})\n") },
            )
        }
        car.fold(
            { append("Car: ${it.address ?: "?"}${it.area?.let { a -> " [$a]" }.orEmpty()} · range ${it.range ?: "?"} · ${when (it.locked) { true -> "locked"; false -> "UNLOCKED"; null -> "lock unknown" }}") },
            { append("Car: failed (${it.message})") },
        )
        append("\nCar image: $image")
        append("\nCar map: $map")
    }
    return report to settings
}

