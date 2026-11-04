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
import androidx.compose.foundation.border
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
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import hr.divanguz.homepanel.R
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.statusBarsPadding
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
import hr.divanguz.homepanel.claude.TapLog
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
import hr.divanguz.homepanel.widget.Themes
import hr.divanguz.homepanel.widget.Zones
import hr.divanguz.homepanel.notify.Alerts
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
    Claude("Claude"), Appearance("Appearance"), Layout("Layout"), Notifications("Notifications"), Backup("Backup"), Updates("Updates")
}

/** Icon colours on the main page, one per section, toned to sit on the dark ground. */
private object Hues {
    val ha = Color(0xFF6AAEE8)
    val weather = Color(0xFFF0C25A)
    val calendar = Color(0xFFE8786C)
    val car = Color(0xFF6CC3A0)
    val claude = Color(0xFFE0956A)
    val look = Color(0xFFB693EA)
    val layout = Color(0xFF62C6D4)
    val alerts = Color(0xFFEBA85A)
    val battery = Color(0xFF9CCB6A)
    val updates = Color(0xFF8AA6F2)
    val backup = Color(0xFFA3A9B5)
}

/** A row summary; [warn] tints it when the user should act. */
private data class Summary(val text: String, val warn: Boolean = false)

/**
 * Material 3's text styles track body text 0.5sp wide with a fixed 24sp line height, which made every
 * line look loose and the big titles cramped. System font (it follows the One UI font setting), no extra
 * tracking, line height in proportion to the size.
 */
private val Type: Typography = Typography().let { t ->
    fun TextStyle.tight() = copy(letterSpacing = 0.sp, lineHeight = (fontSize.value * 1.3f).sp)
    Typography(
        displayLarge = t.displayLarge.tight(), displayMedium = t.displayMedium.tight(), displaySmall = t.displaySmall.tight(),
        headlineLarge = t.headlineLarge.tight(), headlineMedium = t.headlineMedium.tight(), headlineSmall = t.headlineSmall.tight(),
        titleLarge = t.titleLarge.tight(), titleMedium = t.titleMedium.tight(), titleSmall = t.titleSmall.tight(),
        bodyLarge = t.bodyLarge.tight(), bodyMedium = t.bodyMedium.tight(), bodySmall = t.bodySmall.tight(),
        labelLarge = t.labelLarge.tight(), labelMedium = t.labelMedium.tight(), labelSmall = t.labelSmall.tight(),
    )
}

/** Plain text in the settings type: no tracking; line height follows [size]. */
@Composable
private fun T(
    text: String,
    size: Int,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: androidx.compose.ui.text.style.TextAlign? = null,
) = Text(
    text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = align,
    lineHeight = (size * 1.3f).sp, letterSpacing = 0.sp,
)

@Composable
fun SettingsApp(goHome: () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ground) { Screens(goHome) }
    }
}

@Composable
private fun Screens(goHome: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember { Settings.get(app) }

    SettingsLanguage.croatian.value = store.language == "hr"
    var page by remember { mutableStateOf(Page.Main) }
    var busy by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf<String?>(null) }

    var haSummary by remember { mutableStateOf(Summary("…")) }
    var weatherSummary by remember { mutableStateOf(Summary("…")) }
    var calendarSummary by remember { mutableStateOf(Summary("…")) }
    var claudeSummary by remember { mutableStateOf(Summary("…")) }
    var carSummary by remember { mutableStateOf(Summary("…")) }
    var batteryOk by remember { mutableStateOf(true) }
    var freshness by remember { mutableStateOf(Summary("…")) }

    fun reload() {
        batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        scope.launch {
            val cal = withContext(Dispatchers.IO) { CalendarSource.load(app) }
            calendarSummary = if (cal.hasPermission) Summary(trf("%s events in the next %s days", cal.events.size, store.calendarDays))
            else Summary(tr("Tap to allow access"), warn = true)
            val ha = withContext(Dispatchers.IO) { HaRepository.cached(app) }
            haSummary = when {
                !store.ha().isConfigured -> Summary(tr("Not set up"), warn = true)
                ha.error != null -> Summary(ha.error, warn = true)
                else -> Summary(trf("Connected · %s", ago(ha.carAt)))
            }
            weatherSummary = ha.weather?.let { w ->
                Summary(listOfNotNull(w.source, w.place, w.temperature?.let { "${it.roundToInt()}°" }).joinToString(" · "))
            } ?: Summary(store.weatherProvider.label)
            carSummary = Summary(
                listOfNotNull(
                    store.carName.ifBlank { null },
                    ha.car?.locked?.let { if (it) tr("locked") else tr("unlocked") },
                    trf("%s map", tr(store.mapStyle.replaceFirstChar { it.uppercase() })),
                ).joinToString(" · "),
                warn = ha.car?.locked == false,
            )
            val c = withContext(Dispatchers.IO) { ClaudeUsage.cached(app) }
            // When each source last answered; amber once anything is over an hour old (weather: two).
            val now = System.currentTimeMillis()
            val parts = listOfNotNull(
                ha.weatherAt.takeIf { ha.weather != null }?.let { Triple(tr("Weather"), it, 2 * 3_600_000L) },
                ha.carAt.takeIf { ha.car != null }?.let { Triple(tr("Car"), it, 3_600_000L) },
                c.fetchedAt.takeIf { c.hasData }?.let { Triple(tr("Claude"), it, 3_600_000L) },
            )
            freshness = if (parts.isEmpty()) Summary(tr("Nothing fetched yet"))
            else Summary(parts.joinToString(" · ") { (name, at, _) -> "$name ${ago(at)}" }, warn = parts.any { (_, at, limit) -> now - at > limit })
            claudeSummary = when {
                store.claudeSession.isNullOrBlank() -> Summary(tr("Not signed in"), warn = true)
                c.needsLogin -> Summary(tr("Session expired · sign in again"), warn = true)
                c.error != null -> Summary(c.error, warn = true)
                else -> Summary(trf("%s%% now · %s%% this week", c.fiveHour?.roundToInt() ?: "–", c.week?.roundToInt() ?: "–"))
            }
        }
    }

    fun refresh(then: () -> Unit = {}) = scope.launch {
        busy = true
        val n = withContext(Dispatchers.IO) { runCatching { PanelStore.refreshAll(app, forceWeather = true) }.getOrDefault(0) }
        busy = false
        lastRefresh = if (n == 0) tr("Saved · no widget on the home screen yet") else tr("Saved just now")
        reload()
        then()
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    LaunchedEffect(Unit) {
        if (!CalendarSource.hasPermission(context)) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }
    BackHandler(enabled = page != Page.Main) { page = Page.Main; reload() }

    // Each page starts at the top.
    val scroll = remember(page) { androidx.compose.foundation.ScrollState(0) }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp),
    ) {
        if (page == Page.Main) {
            T(
                "HomePanel", 38, Ink, FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp, top = 84.dp, bottom = 6.dp),
            )
            T(
                lastRefresh ?: tr("Weather, calendar, car and Claude"), 15, Ink2,
                modifier = Modifier.padding(start = 10.dp, bottom = 36.dp),
            )
        } else {
            PageHeader(tr(page.title)) { page = Page.Main; reload() }
        }

        when (page) {
            Page.Main -> MainPage(
                busy = busy,
                ha = haSummary, weather = weatherSummary, calendar = calendarSummary, claude = claudeSummary, car = carSummary,
                batteryOk = batteryOk,
                freshness = freshness,
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
            Page.Layout -> LayoutPage(store)
            Page.Notifications -> NotificationsPage(store)
            Page.Backup -> BackupPage(store, onRestored = { refresh() })
            Page.Updates -> UpdatesPage(store)
        }
        Spacer(Modifier.height(40.dp))
    }
    // One UI's collapsing title: once the big title has scrolled away, a small one stays on a bar.
    CollapsedBar(
        title = if (page == Page.Main) "HomePanel" else tr(page.title),
        visible = scroll.value > with(androidx.compose.ui.platform.LocalDensity.current) { (if (page == Page.Main) 150.dp else 110.dp).toPx() },
        back = if (page == Page.Main) null else ({ page = Page.Main; reload() }),
    )
    }
}

@Composable
private fun CollapsedBar(title: String, visible: Boolean, back: (() -> Unit)?) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, label = "bar")
    if (alpha == 0f) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(Ground)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) BackArrow(back) else Spacer(Modifier.width(10.dp))
        T(title, 20, Ink, FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
    }
}

@Composable
private fun BackArrow(back: () -> Unit) {
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
    freshness: Summary,
    onRefresh: () -> Unit,
    onRefreshAndLeave: () -> Unit,
    open: (Page) -> Unit,
    onCalendar: () -> Unit,
    onBattery: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        // "Save" stores everything and refreshes the widget; the second one also returns to the home screen.
        Pill(tr("Save"), filled = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = onRefresh)
        Pill(if (busy) tr("Saving…") else tr("Save and go home"), filled = true, enabled = !busy, modifier = Modifier.weight(1.4f), onClick = onRefreshAndLeave)
    }
    Spacer(Modifier.height(24.dp))
    Grouped {
        Item(tr("Home Assistant"), ha, onClick = { open(Page.HomeAssistant) }, icon = R.drawable.ic_home, hue = Hues.ha)
        Item(tr("Weather"), weather, onClick = { open(Page.Weather) }, icon = R.drawable.ic_wx_partly, hue = Hues.weather)
        Item(tr("Calendar"), calendar, onClick = onCalendar, icon = R.drawable.ic_calendar, hue = Hues.calendar)
        Item(tr("Car"), car, onClick = { open(Page.Car) }, icon = R.drawable.ic_car, hue = Hues.car)
        Item(tr("Claude"), claude, onClick = { open(Page.Claude) }, last = true, icon = R.drawable.ic_spark, hue = Hues.claude)
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        Item(tr("Appearance"), Settings.get(LocalContext.current).look().let { l -> Summary((if (l.designB) tr("Design B") else tr("Design A")) + " · " + themeText(l.theme).first + " · " + tr("sizes")) }, onClick = { open(Page.Appearance) }, icon = R.drawable.ic_palette, hue = Hues.look)
        Item(tr("Layout"), layoutSummary(Settings.get(LocalContext.current).look()), onClick = { open(Page.Layout) }, icon = R.drawable.ic_layout, hue = Hues.layout)
        Item(
            tr("Notifications"),
            Settings.get(LocalContext.current).let { st ->
                Summary(listOfNotNull(tr("Temperature").takeIf { st.tempNotification }, tr("Claude limits").takeIf { st.claudeAlerts })
                    .joinToString(" · ").ifEmpty { tr("Off") })
            },
            onClick = { open(Page.Notifications) }, icon = R.drawable.ic_bell, hue = Hues.alerts,
        )
        Item(
            tr("Background refresh"),
            if (batteryOk) Summary(tr("Unrestricted")) else Summary(tr("Optimized by the system · tap to allow"), warn = true),
            onClick = if (batteryOk) null else onBattery,
            icon = R.drawable.ic_battery, hue = Hues.battery,
        )
        Item(
            tr("Last updated"), freshness, onClick = onRefresh, last = true,
            icon = R.drawable.ic_refresh, hue = Hues.layout,
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
        Item(tr("Updates"), Summary(trf("Version %s · check GitHub for a newer one", version)), onClick = { open(Page.Updates) }, icon = R.drawable.ic_download, hue = Hues.updates)
        Item(tr("Backup"), Summary(tr("Save settings to a file, or restore them")), onClick = { open(Page.Backup) }, icon = R.drawable.ic_backup, hue = Hues.backup)
        Item(tr("Source on GitHub"), Summary("divanguz-alt/homepanel"), onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, last = true, icon = R.drawable.ic_code, hue = Ink2)
    }
    Text(
        "HomePanel $version", fontSize = 13.sp, color = Ink3,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private const val REPO_URL = "https://github.com/divanguz-alt/homepanel"

/** Check the public repo's GitHub Releases; download and hand the APK to the installer. */
@Composable
private fun UpdatesPage(store: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val installed = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).let { it.longVersionCode.toInt() to (it.versionName ?: "") }
    }
    var status by remember { mutableStateOf<Summary?>(null) }
    var release by remember { mutableStateOf<hr.divanguz.homepanel.updates.Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val updater = hr.divanguz.homepanel.updates.Updater

    fun check() {
        busy = true
        status = Summary(tr("Checking…"))
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { updater.latest() } }
            r.onSuccess {
                release = it.takeIf { rel -> rel.versionCode > installed.first }
                status = if (release != null) Summary(trf("Version %s is available (%s MB)", it.name, "%.1f".format(it.size / 1_048_576f)))
                else Summary(trf("You have the latest version (%s)", installed.second))
            }.onFailure { status = Summary(it.message ?: "Check failed", warn = true) }
            busy = false
        }
    }

    fun downloadAndInstall(rel: hr.divanguz.homepanel.updates.Updater.Release) {
        if (updater.needsInstallPermission(context)) {
            status = Summary(tr("Allow HomePanel to install apps, then tap again"), warn = true)
            context.startActivity(updater.installPermissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy = true
        progress = 0f
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { updater.download(app, rel) { p -> progress = p } }
            }
            progress = null
            busy = false
            r.onSuccess { updater.install(context, it) }
                .onFailure { status = Summary(it.message ?: tr("Download failed"), warn = true) }
        }
    }

    Grouped {
        Item(tr("Installed"), Summary(installed.second), onClick = null)
        Item(
            when {
                progress != null -> trf("Downloading… %s%%", ((progress ?: 0f) * 100).roundToInt())
                release != null -> trf("Download and install %s", release!!.name)
                else -> tr("Check for updates")
            },
            status,
            onClick = when {
                busy -> null
                release != null -> ({ downloadAndInstall(release!!) })
                else -> ({ check() })
            },
            last = true,
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

    SectionLabel(tr("Connection"))
    Grouped(padded = true) {
        Field(tr("Local address"), ha.internalUrl, KeyboardType.Uri, hint = tr("http:// works only on the home network")) { ha = ha.copy(internalUrl = it) }
        Field(tr("Remote address"), ha.externalUrl, KeyboardType.Uri, hint = tr("Tailscale or Nabu Casa")) { ha = ha.copy(externalUrl = it) }
        Field(tr("Access token"), ha.token, KeyboardType.Password, secret = true) { ha = ha.copy(token = it) }
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        Item(tr("Entities"), Summary(if (showEntities) tr("Hide") else tr("Weather, car, location and more")), onClick = { showEntities = !showEntities }, last = !showEntities)
        if (showEntities) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Field(tr("Weather (fallback)"), ha.weatherEntity) { ha = ha.copy(weatherEntity = it) }
                Field(tr("Air quality"), ha.aqiEntity, hint = tr("found by Test")) { ha = ha.copy(aqiEntity = it) }
                Field(tr("Phone location"), ha.phoneEntity, hint = tr("found by Test")) { ha = ha.copy(phoneEntity = it) }
                Field(tr("Car address"), ha.carAddressEntity) { ha = ha.copy(carAddressEntity = it) }
                Field(tr("Range"), ha.rangeEntity) { ha = ha.copy(rangeEntity = it) }
                Field(tr("Range (fallback)"), ha.rangeFallbackEntity) { ha = ha.copy(rangeFallbackEntity = it) }
                Field(tr("Car picture"), ha.carImageEntity) { ha = ha.copy(carImageEntity = it) }
                Field(tr("Car location"), ha.locationEntity) { ha = ha.copy(locationEntity = it) }
                Field(tr("Car lock"), ha.lockEntity, hint = tr("found by Test")) { ha = ha.copy(lockEntity = it) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Pill(tr("Save"), filled = false, enabled = !testing, modifier = Modifier.weight(1f)) {
            store.saveHa(ha)
            ha = store.ha()
            report = null
            onChanged()
        }
        Pill(if (testing) tr("Testing…") else tr("Save and test"), filled = true, enabled = !testing, modifier = Modifier.weight(1.4f)) {
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
                    ?: return@withContext tr("Needs Home Assistant for your location") to emptyMap()
                val rows = HaRepository.compare(app, client, s, at.first, at.second).associate { (p, r) ->
                    p to r.fold(
                        { w ->
                            val tomorrow = w.days.getOrNull(1)?.let { d -> " · tomorrow ${d.high?.roundToInt() ?: "–"}°" }.orEmpty()
                            Summary("${w.temperature?.roundToInt()?.let { "$it°" } ?: "–"} ${Conditions.label(w.condition).lowercase()}$tomorrow")
                        },
                        { e -> Summary(if (p == WeatherProvider.DHMZ) tr("Only available in Croatia") else (e.message ?: tr("Unavailable")), warn = true) },
                    )
                }
                val name = HaRepository.cached(app).weather?.place
                (name?.let { trf("Now, in %s", it) } ?: tr("Now, at your location")) to rows
            }
            where = place
            readings = rows
            comparing = false
        }
    }
    LaunchedEffect(Unit) { compare() }

    PhoneLocationSection(store, onChanged = { onChanged(); compare() })
    Spacer(Modifier.height(16.dp))
    SectionLabel(where ?: tr("Source"))
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
                    T(p.label, 17, Ink, FontWeight.Medium)
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
        if (comparing) tr("Checking every source…") else tr("Check again"),
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
    SectionLabel(tr("Updates"))
    Grouped(padded = true) {
        Choice(tr("Refresh every"), listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h"), every) {
            every = it; store.weatherEveryMin = it
        }
        Choice(tr("New forecast after moving"), listOf(1 to "1 km", 3 to "3 km", 10 to "10 km"), moveKm) {
            moveKm = it; store.weatherMoveKm = it
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Air quality and warnings"))
    Grouped(padded = true) {
        Choice(tr("Air quality index"), listOf(false to tr("US AQI"), true to tr("European")), european) {
            european = it; store.aqiEuropean = it; onChanged()
        }
        ToggleRow(
            tr("Official warnings"),
            if (provider == WeatherProvider.DHMZ) tr("From DHMZ for your county")
            else tr("Only with DHMZ as the source (Croatia). Other sources don't publish warnings."),
            warnings, warn = warnings && provider != WeatherProvider.DHMZ,
        ) { warnings = it; store.warnings = it; onChanged() }
        ToggleRow(tr("Rain coming soon"), tr("\"Rain from 14:20\" / \"Rain until 15:10\" under the conditions, only when it rains"), showRain) {
            showRain = it; store.showRain = it; onChanged()
        }
        var hourly by remember { mutableStateOf(store.showHourly) }
        ToggleRow(tr("Next hours"), tr("Six hours under the weather: temperature, and the chance of rain from 20%"), hourly) {
            hourly = it; store.showHourly = it; onChanged()
        }
        ToggleRow(tr("Show source on the widget"), tr("Small grey \"Source: …\" in the weather zone"), showSource) {
            showSource = it; store.showSource = it; onChanged()
        }
    }
}

@Composable
private fun CalendarPage(store: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    var days by remember { mutableStateOf(store.calendarDays) }
    Grouped(padded = true) {
        Choice(tr("Show events for"), listOf(7 to tr("7 days"), 14 to tr("14 days"), 30 to tr("30 days")), days) {
            days = it; store.calendarDays = it; onChanged()
        }
    }
    Spacer(Modifier.height(16.dp))
    val calendars = remember { CalendarSource.calendars(context) }
    var hidden by remember { mutableStateOf(store.hiddenCalendars) }
    if (calendars.isNotEmpty()) {
        SectionLabel(tr("Shown on the widget"))
        Grouped {
            calendars.forEachIndexed { i, cal ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        hidden = if (cal.id in hidden) hidden - cal.id else hidden + cal.id
                        store.hiddenCalendars = hidden
                        onChanged()
                    }.heightIn(min = 64.dp).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(cal.color?.let { Color(it or 0xFF000000.toInt()) } ?: Ink3))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        T(cal.name.ifBlank { tr("Calendar") }, 17, if (cal.id in hidden) Ink3 else Ink, FontWeight.Medium, maxLines = 1)
                        if (cal.account.isNotBlank() && cal.account != cal.name) T(cal.account, 13, Ink3, maxLines = 1)
                    }
                    Switch(
                        checked = cal.id !in hidden, onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF1A1206), uncheckedTrackColor = Hair, uncheckedThumbColor = Ink3, uncheckedBorderColor = Hair),
                    )
                }
                if (i < calendars.lastIndex) Divider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    Grouped {
        Item(tr("Open calendar"), Summary(tr("Your default calendar app")), onClick = {
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
        Field(tr("Name on the widget"), name, hint = tr("e.g. the model; blank shows \"Car\"")) { name = it }
        Pill(tr("Save name"), filled = false, enabled = name != store.carName, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            store.carName = name
            onChanged()
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("From Home Assistant"))
    Grouped {
        EntityChooser(store, tr("Parked address"), ha.carAddressEntity, listOf("sensor")) { saveHa(ha.copy(carAddressEntity = it)) }
        EntityChooser(store, tr("Range"), ha.rangeEntity, listOf("sensor")) { saveHa(ha.copy(rangeEntity = it)) }
        EntityChooser(store, tr("Range (backup)"), ha.rangeFallbackEntity, listOf("sensor")) { saveHa(ha.copy(rangeFallbackEntity = it)) }
        EntityChooser(store, tr("Picture"), ha.carImageEntity, listOf("image")) { saveHa(ha.copy(carImageEntity = it)) }
        EntityChooser(store, tr("Location (for the map)"), ha.locationEntity, listOf("device_tracker", "sensor"), last = true) {
            saveHa(ha.copy(locationEntity = it)); redrawMap()
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Tapping the car opens"))
    CarAppChooser(store, onChanged)
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Map"))
    Grouped(padded = true) {
        Choice(tr("Style"), listOf("fiord" to tr("Fiord"), "dark" to tr("Dark"), "positron" to tr("Light")), style) {
            style = it; store.mapStyle = it; redrawMap()
        }
        SizeRow(tr("Zoom"), zoom, 14f..17.5f, { "%.1f".format(it) }) {
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
        error?.let { Summary(it, warn = true) } ?: Summary(current.ifBlank { tr("Not set · tap to choose") }, warn = current.isBlank()),
        onClick = {
            open = !open
            if (open && options == null) scope.launch {
                val s = store.ha()
                options = withContext(Dispatchers.IO) {
                    if (!s.isConfigured) null else runCatching { HaFetch.entities(HaClient(s), domains) }.getOrNull()
                }
                error = if (options == null) tr("Couldn't reach Home Assistant") else null
            }
        },
        last = last && !open,
    )
    if (open) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Field(tr("Search"), query) { query = it }
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
        if (current.isNotBlank()) Item(tr("Clear"), null, onClick = { onPick(""); open = false }, danger = true, last = last)
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
            Summary(if (open) tr("Pick an app below") else tr("Tap to change")),
            onClick = { open = !open },
            last = !open,
        )
        if (open) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Field(tr("Search apps"), query) { query = it }
            }
            Item(tr("Home Assistant (default)"), null, onClick = {
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

    SectionLabel(tr("Your location"))
    Grouped {
        Item(
            entity.ifBlank { tr("Not set") },
            Summary(if (entity.isBlank()) tr("Forecast is for home until you pick your phone") else tr("Forecast follows this tracker"), warn = entity.isBlank()),
            onClick = null,
        )
        Item(
            if (searching) tr("Searching…") else tr("Choose from Home Assistant"),
            note?.let { Summary(it, warn = true) } ?: Summary(tr("Trackers that report coordinates")),
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
                        found == null -> tr("Couldn't reach Home Assistant")
                        found.isEmpty() -> tr("No tracker with coordinates. In the HA app, turn on location sending.")
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

    SectionLabel(tr("Lock"))
    Grouped {
        Item(
            when (entity) { "" -> tr("No lock entity"); "none" -> tr("Padlock hidden"); else -> entity },
            when {
                entity == "none" -> Summary(tr("Pick an entity below to show it again"))
                entity.isBlank() -> Summary(tr("The padlock appears once one is chosen"), warn = true)
                locked == true -> Summary(tr("Locked"))
                locked == false -> Summary(tr("Unlocked"), warn = true)
                else -> Summary(tr("State unknown until the next refresh"))
            },
            onClick = null,
        )
        Item(
            if (searching) tr("Searching…") else tr("Find locks in Home Assistant"),
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
                        found == null -> tr("Couldn't reach Home Assistant")
                        found.isEmpty() -> tr("Nothing lock-like in Home Assistant")
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
            Item(tr("Don't show a padlock"), null, onClick = { choose("none") }, danger = true, last = true)
        }
    }
}

/** "14:02 tap · started 14:02 · done 14:03: ok", or where it stopped. */
private fun tapSummary(e: TapLog.Entry): Summary {
    val f = java.time.format.DateTimeFormatter.ofPattern("d.M. HH:mm:ss")
    fun t(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(f)
    val parts = mutableListOf(trf("tapped %s", t(e.tapped)))
    parts += when {
        e.finished != 0L -> trf("done %s: %s", t(e.finished), e.result ?: "?")
        e.started != 0L -> trf("started %s, not finished", t(e.started))
        e.queued != 0L -> tr("queued, not started yet")
        else -> tr("not queued")
    }
    return Summary(parts.joinToString(" · "), warn = e.finished == 0L || e.result != "ok")
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
                c.needsLogin -> Summary(prefix + tr("Not signed in"), warn = true)
                c.error != null -> Summary("$prefix${c.error}", warn = true)
                else -> Summary(prefix + trf("%s%% of the 5-hour limit · %s%% of the week", c.fiveHour?.roundToInt(), c.week?.roundToInt()))
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
            if (signedIn) tr("Signed in") else tr("Sign in"),
            status ?: Summary(if (signedIn) "claude.ai" else tr("Opens claude.ai"), warn = false),
            onClick = { signIn.launch(Intent(context, ClaudeLoginActivity::class.java)) },
        )
        Item(tr("Fetch usage now"), Summary(if (working) tr("Fetching…") else tr("Same as tapping the bars on the widget")), onClick = if (working) null else ({ fetch() }))
        TapLog.read(app)?.let { Item(tr("Last tap on the widget"), tapSummary(it), onClick = null) }
        Item(tr("Use a session key"), Summary(tr("If signing in here doesn't work")), onClick = { pasting = !pasting }, last = !pasting && !signedIn)
        if (pasting) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    tr("On a computer, sign in at claude.ai and copy the sessionKey cookie (DevTools → Application → Cookies)."),
                    fontSize = 14.sp, color = Ink2,
                )
                Field("sessionKey", key, KeyboardType.Password, secret = true) { key = it.trim() }
                Pill(tr("Use this key"), filled = true, enabled = key.startsWith("sk-ant-") && !working, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    store.clearClaude()
                    store.claudeSession = key
                    key = ""
                    pasting = false
                    fetch()
                }
            }
        }
        if (signedIn) {
            Item(tr("Sign out"), null, onClick = {
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
        tr("Usage comes from the page claude.ai uses for its own usage screen. It isn't an official API and may stop working."),
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

    WidgetPreview(look)
    var language by remember { mutableStateOf(store.language) }
    Grouped(padded = true) {
        Choice(tr("Widget language"), listOf("en" to "English", "hr" to "Hrvatski"), language) {
            language = it
            store.language = it
            SettingsLanguage.croatian.value = it == "hr"
            scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Design"))
    Grouped {
        listOf(false to ("A" to tr("Date line, refresh button, rows with dividers")), true to ("B" to tr("Bigger weather, grouped calendar, grey map"))).forEachIndexed { i, (b, t) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { save(look.copy(designB = b)) }.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    T(t.first, 17, Ink, FontWeight.Medium)
                    T(t.second, 14, Ink2)
                }
                RadioButton(selected = look.designB == b, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Ink3))
            }
            if (i == 0) Divider()
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Theme"))
    ThemeChooser(look.theme) { save(look.copy(theme = it)) }
    if (look.designB) {
        var showDate by remember { mutableStateOf(store.showDate) }
        Spacer(Modifier.height(16.dp))
        Grouped(padded = true) {
            ToggleRow(tr("Show date"), tr("Next to the city: \"City • Tue 20 Oct\""), showDate) {
                showDate = it
                store.showDate = it
                scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(tr("Sizes"))
    Grouped(padded = true) {
        SizeRow(tr("Date and city"), look.header, 0.8f..1.6f, ::pct) { save(look.copy(header = it)) }
        // Stored in sp (84 = 100%); shown as a percentage like the others.
        SizeRow(tr("Temperature"), look.temperatureSp / 84f, 0.7f..1.7f, ::pct) { save(look.copy(temperatureSp = (it * 84).roundToInt())) }
        SizeRow(tr("Conditions and AQI"), look.conditions, 0.8f..1.6f, ::pct) { save(look.copy(conditions = it)) }
        SizeRow(tr("Forecast"), look.forecast, 0.7f..1.6f, ::pct) { save(look.copy(forecast = it)) }
        SizeRow(tr("Calendar"), look.calendar, 0.8f..1.5f, ::pct) { save(look.copy(calendar = it)) }
        SizeRow(tr("Car and Claude"), look.bottom, 0.8f..1.5f, ::pct) { save(look.copy(bottom = it)) }
    }
    Spacer(Modifier.height(12.dp))
    T(
        trf("Corners: %s dp, the phone's own widget radius (both widgets use it)", widgetRadiusText(LocalContext.current)), 13, Ink3,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        tr("Reset sizes"), color = Accent, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { save(look.resetSizes()) }.padding(10.dp),
    )
}

private fun widgetRadiusText(context: Context) =
    "%.0f".format(hr.divanguz.homepanel.widget.widgetRadius(context).value)

private fun zoneName(id: String) = when (id) {
    Zones.WEATHER -> tr("Weather")
    Zones.CALENDAR -> tr("Calendar")
    Zones.GRID -> tr("Car and Claude")
    Zones.CAR -> tr("Car")
    else -> tr("Claude")
}

private fun layoutSummary(look: Look): Summary =
    Summary(look.order.filter(look::shows).joinToString(" · ") { id ->
        if (id != Zones.GRID) zoneName(id)
        else listOf(Zones.CAR, Zones.CLAUDE).filter(look::shows).let { if (look.swapGrid) it.reversed() else it }
            .joinToString(" + ") { zoneName(it) }.ifEmpty { zoneName(id) }
    }.ifEmpty { tr("Everything hidden") })

/** Order of the panel's zones (arrows), which ones show (switch), and the car/Claude halves. */
@Composable
private fun LayoutPage(store: Settings) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var look by remember { mutableStateOf(store.look()) }
    fun save(next: Look) {
        look = next
        store.saveLook(next)
        scope.launch { withContext(Dispatchers.IO) { runCatching { PanelStore.redraw(app) } } }
    }
    fun move(i: Int, by: Int) {
        val order = look.order.toMutableList()
        val j = i + by
        if (j !in order.indices) return
        order[i] = order[j].also { order[j] = order[i] }
        save(look.copy(order = order))
    }
    fun show(id: String, on: Boolean) = save(look.copy(hidden = if (on) look.hidden - id else look.hidden + id))

    WidgetPreview(look)
    SectionLabel(tr("From top to bottom"))
    Grouped {
        look.order.forEachIndexed { i, id ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(start = 18.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    T(zoneName(id), 17, if (look.shows(id)) Ink else Ink3, FontWeight.Medium)
                    if (id == Zones.WEATHER && !look.shows(id)) {
                        T(tr("Also hides the refresh button and the settings shortcut"), 13, Attention)
                    }
                }
                Arrow(R.drawable.ic_arrow_up, enabled = i > 0) { move(i, -1) }
                Arrow(R.drawable.ic_arrow_down, enabled = i < look.order.lastIndex) { move(i, 1) }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = look.shows(id), onCheckedChange = { show(id, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF1A1206), uncheckedTrackColor = Hair, uncheckedThumbColor = Ink3, uncheckedBorderColor = Hair),
                )
            }
            if (i < look.order.lastIndex) Divider()
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel(tr("Car and Claude"))
    Grouped(padded = true) {
        ToggleRow(tr("Car"), tr("Address, map and range"), look.shows(Zones.CAR)) { show(Zones.CAR, it) }
        ToggleRow(tr("Claude"), tr("Usage bars"), look.shows(Zones.CLAUDE)) { show(Zones.CLAUDE, it) }
        ToggleRow(
            tr("Claude on the left"),
            if (look.shows(Zones.CAR) && look.shows(Zones.CLAUDE)) tr("Swap the two halves") else tr("With one of them hidden, the other takes the whole width"),
            look.swapGrid,
        ) { save(look.copy(swapGrid = it)) }
    }
    Spacer(Modifier.height(12.dp))
    T(
        tr("A widget on the home screen can't be rearranged by dragging, so the order is set here. Small widgets with a single zone are in the widget list too."),
        13, Ink3, modifier = Modifier.padding(horizontal = 18.dp),
    )
    Spacer(Modifier.height(8.dp))
    T(
        tr("Default layout"), 15, Accent,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { save(look.copy(order = Zones.DEFAULT_ORDER, hidden = emptySet(), swapGrid = false)) }.padding(10.dp),
    )
}

@Composable
private fun Arrow(res: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(res), null, colorFilter = ColorFilter.tint(if (enabled) Ink2 else Hair), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NotificationsPage(store: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var temp by remember { mutableStateOf(store.tempNotification) }
    var claude by remember { mutableStateOf(store.claudeAlerts) }
    var threshold by remember { mutableStateOf(store.claudeThreshold) }
    var denied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        if (ok) pending?.invoke()
        pending = null
    }
    fun withPermission(then: () -> Unit) {
        if (Alerts.canPost(context)) then() else { pending = then; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    fun showTemperatureNow() = scope.launch {
        withContext(Dispatchers.IO) { Alerts.weatherChanged(app, HaRepository.cached(app)) }
    }

    Grouped(padded = true) {
        ToggleRow(
            tr("Temperature in the status bar"),
            tr("A quiet notification whose icon is the temperature, also on the lock screen. Always On Display shows only the icon."),
            temp,
        ) { on ->
            if (on) withPermission {
                temp = true; store.tempNotification = true
                hr.divanguz.homepanel.work.Refresh.ensureScheduled(app)
                showTemperatureNow()
            } else {
                temp = false; store.tempNotification = false
                Alerts.clearWeather(app)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Grouped(padded = true) {
        ToggleRow(
            tr("Claude limit warnings"),
            tr("Once per window when usage passes the limit below, and again when it resets"),
            claude,
        ) { on ->
            if (on) withPermission {
                claude = true; store.claudeAlerts = true
                hr.divanguz.homepanel.work.Refresh.ensureScheduled(app)
            } else { claude = false; store.claudeAlerts = false }
        }
        if (claude) {
            Choice(tr("Warn at"), listOf(50 to "50%", 70 to "70%", 80 to "80%", 90 to "90%"), threshold) {
                threshold = it; store.claudeThreshold = it
            }
        }
    }
    if (denied) {
        Spacer(Modifier.height(12.dp))
        T(
            tr("Notifications are blocked for HomePanel. Allow them in Android settings."), 14, Attention,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.padding(10.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    T(
        tr("Checked with the background refresh, about every 15 minutes."), 13, Ink3,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

/** Settings to a JSON file through the system file picker, and back. The token and Claude session are left out. */
@Composable
private fun BackupPage(store: Settings, onRestored: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    var status by remember { mutableStateOf<Summary?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.exportJson().toByteArray()) }
            Summary(tr("Saved"))
        }.getOrElse { Summary(it.message ?: tr("Couldn't save"), warn = true) }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val n = store.importJson(text)
            SettingsLanguage.croatian.value = store.language == "hr"
            CarMap.invalidate(app)
            onRestored()
            Summary(trf("Restored %s settings. Enter the Home Assistant token again and sign in to Claude.", n))
        }.getOrElse { Summary(it.message ?: tr("Couldn't read the file"), warn = true) }
    }
    Grouped {
        Item(tr("Save to a file"), Summary(tr("Everything except the token and the Claude sign-in")), onClick = { save.launch("homepanel-settings.json") })
        Item(tr("Restore from a file"), Summary(tr("Replaces the current settings")), onClick = { load.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, last = status == null)
        status?.let { Item(tr("Result"), it, onClick = null, last = true) }
    }
    Spacer(Modifier.height(12.dp))
    T(
        tr("The file holds your Home Assistant addresses and entity names, so keep it somewhere private."), 13, Ink3,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

/**
 * The real widget, drawn by Glance off-screen with [look] and shown here; redrawn whenever [look]
 * changes. Taps on it do nothing (it is only a picture of the panel).
 */
@Composable
private fun WidgetPreview(look: Look) {
    val context = LocalContext.current
    val app = context.applicationContext
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val size = androidx.compose.ui.unit.DpSize(maxWidth, 560.dp)
        var views by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
        LaunchedEffect(look, size) {
            views = withContext(Dispatchers.Default) {
                runCatching { hr.divanguz.homepanel.widget.previewViews(app, look, size) }
                    .onFailure { android.util.Log.w("HomePanel", "Preview failed", it) }
                    .getOrNull()
            } ?: views
        }
        Box(modifier = Modifier.fillMaxWidth().height(size.height)) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { android.widget.FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    views?.let { rv -> runCatching { rv.apply(frame.context, frame) }.getOrNull()?.let(frame::addView) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {},
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

/** Name and one line for each theme in [Themes.ids]. */
private fun themeText(id: String): Pair<String, String> = when (id) {
    Themes.MIDNIGHT -> tr("Midnight") to tr("Blue-grey, the original")
    Themes.AUTO -> tr("Day and night") to tr("Light by day, Midnight at night, with the system's dark mode")
    Themes.LIGHT -> tr("Light") to tr("Light grey card with dark text")
    Themes.MATERIAL_YOU -> "Material You" to tr("Dark tones from your wallpaper")
    "graphite" -> tr("Graphite") to tr("Neutral grey, no tint")
    "amoled" -> tr("AMOLED black") to tr("Pure black, blends into a black wallpaper")
    Themes.GLASS -> tr("Glass") to tr("See-through, the wallpaper shows behind")
    "moss" -> tr("Moss") to tr("Deep forest green")
    "espresso" -> tr("Espresso") to tr("Warm brown with cream text")
    "indigo" -> tr("Indigo") to tr("Deep night blue")
    "wine" -> tr("Wine") to tr("Dark burgundy")
    "phosphor" -> tr("Phosphor") to tr("Black with green text, like an old terminal")
    else -> id to ""
}

@Composable
private fun ThemeChooser(selected: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Grouped {
        Themes.ids.forEachIndexed { i, id ->
            val t = Themes.resolve(context, id)
            val (name, line) = themeText(id)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A tiny card in the theme's own colours.
                Box(
                    modifier = Modifier.size(width = 52.dp, height = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(t.card)
                        .border(1.dp, t.hair, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Text("16°", fontSize = 15.sp, color = t.ink, modifier = Modifier.align(Alignment.TopStart))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(t.track)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(3.dp).background(t.ink2))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    T(name, 17, Ink, FontWeight.Medium)
                    T(line, 14, Ink2)
                }
                RadioButton(selected = selected == id, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Ink3))
            }
            if (i < Themes.ids.lastIndex) Divider()
        }
    }
}

// ---- building blocks -------------------------------------------------------------------------

@Composable
private fun PageHeader(title: String, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        BackArrow(back)
    }
    // One UI puts the page name large under the back arrow rather than beside it.
    T(title, 32, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 24.dp))
}

@Composable
private fun SectionLabel(text: String) {
    T(text, 14, Ink2, FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 8.dp))
}

@Composable
private fun Grouped(padded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Group)
            .then(if (padded) Modifier.padding(horizontal = 18.dp, vertical = 12.dp) else Modifier),
        content = content,
    )
}

/** A row's icon: a line drawing in its own colour on a faint disc of the same colour. */
@Composable
private fun RowIcon(res: Int, hue: Color) {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(hue.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(res), null, colorFilter = ColorFilter.tint(hue), modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun Item(
    title: String,
    summary: Summary?,
    onClick: (() -> Unit)?,
    last: Boolean = false,
    danger: Boolean = false,
    icon: Int? = null,
    hue: Color = Ink2,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (icon != null) 16.dp else 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon, hue)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            T(title, 17, if (danger) Danger else Ink, FontWeight.Medium)
            summary?.let {
                Spacer(Modifier.height(2.dp))
                T(it.text, 14, if (it.warn) Attention else Ink2, maxLines = 2)
            }
        }
    }
    if (!last) Divider(indent = if (icon != null) 70.dp else 18.dp)
}

@Composable
private fun Divider(indent: androidx.compose.ui.unit.Dp = 18.dp) {
    Box(modifier = Modifier.padding(start = indent, end = 18.dp).fillMaxWidth().height(1.dp).background(Hair))
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
        T(text, 16, if (filled) Color(0xFF1A1206) else Ink, FontWeight.SemiBold)
    }
}

/** A label above a row of pills; the selected one is filled. */
@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        T(label, 17, Ink, FontWeight.Medium)
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
                    T(text, 14, if (on) Color(0xFF1A1206) else Ink2, if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
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
            T(title, 17, Ink, FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            T(summary, 14, if (warn) Attention else Ink2)
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
            T(label, 17, Ink, FontWeight.Medium, modifier = Modifier.weight(1f))
            T(format(current), 15, Ink2)
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
    if (at <= 0) return tr("never updated")
    val min = (System.currentTimeMillis() - at) / 60_000
    return when {
        min < 1 -> tr("just now")
        min < 60 -> trf("%s min ago", min)
        min < 48 * 60 -> trf("%s h ago", min / 60)
        else -> trf("%s days ago", min / (24 * 60))
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

