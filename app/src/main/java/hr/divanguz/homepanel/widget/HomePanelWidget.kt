package hr.divanguz.homepanel.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import hr.divanguz.homepanel.calendar.CalendarSnapshot
import hr.divanguz.homepanel.calendar.CalendarView
import hr.divanguz.homepanel.calendar.EventRow
import hr.divanguz.homepanel.calendar.toView
import hr.divanguz.homepanel.calendar.newEventIntent
import hr.divanguz.homepanel.calendar.viewIntent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.claude.ClaudeLoginActivity
import hr.divanguz.homepanel.ha.CarImage
import hr.divanguz.homepanel.ha.CarMap

private val CardRadius = 28.dp
private val RowHeight = 52.dp
/** Fixed so the car cell's map can be sized exactly; fits the Claude column's content. */
private val GridHeight = 164.dp
/** Below these widget heights the calendar, then the car/Claude grid, are left out. */
private val CompactCalendarMin = 380.dp
private val CompactGridMin = 300.dp

class HomePanelWidget : GlanceAppWidget() {

    /** Picker preview (Android 15+): the real widget from cached data, or sample data. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val ha = Preview.ha(context)
        val claude = Preview.claude(context)
        val calendar = Preview.calendar(context)
        provideContent {
            val texts = Texts.of(Settings.get(context).language)
            CompositionLocalProvider(LocalLook provides Settings.get(context).look(), LocalTexts provides texts) {
                Panel(panelState(ha, haConfigured = true, claude = claude, claudeBusy = false, carName = Settings.get(context).carName, texts = texts), calendar)
            }
        }
    }

    // Exact gives us the real width, which the usage bars need. Milestone 6 moves to Responsive.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // A freshly placed widget has no stored data yet; seed it before the first frame.
        val stored = getAppWidgetState<Preferences>(context, id)
        if (PanelStore.calendar(stored) == null || PanelStore.ha(stored) == null) {
            PanelStore.seed(context, id)
        }
        provideContent {
            val prefs = currentState<Preferences>()
            val settings = Settings.get(context)
            // Reading the redraw stamp makes appearance changes recompose even when no data changed.
            PanelStore.redrawStamp(prefs)
            val texts = Texts.of(settings.language)
            CompositionLocalProvider(LocalLook provides settings.look(), LocalTexts provides texts) {
                val state = panelState(
                    PanelStore.ha(prefs), settings.ha().isConfigured,
                    PanelStore.claude(prefs), PanelStore.claudeBusy(prefs),
                    carName = settings.carName,
                    texts = texts,
                )
                Panel(state, PanelStore.calendar(prefs))
            }
        }
    }
}

@Composable
private fun Panel(state: PanelState, calendar: CalendarSnapshot?) {
    val context = LocalContext.current
    // The card fills the whole widget frame; only the calendar grows or shrinks with it.
    Box(modifier = GlanceModifier.fillMaxSize()) {
        // Hairline border faked with an outer box one dp larger than the card.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Palette.hair)
                .cornerRadius(CardRadius)
                .padding(1.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Palette.card)
                    .cornerRadius(CardRadius - 1.dp),
            ) {
                // Smaller widgets drop whole zones rather than squeezing them: the calendar goes first
                // (it is the flexible part), then the car/Claude grid. Weather always stays.
                val height = LocalSize.current.height
                val showGrid = height >= CompactGridMin
                val showCalendar = height >= CompactCalendarMin
                val designB = LocalLook.current.designB
                if (designB) WeatherZoneB(state, context) else WeatherZone(state, context)
                if (showCalendar) {
                    HDivider()
                    if (designB) CalendarZoneB(calendar, context, GlanceModifier.defaultWeight())
                    else CalendarZone(calendar, context, GlanceModifier.defaultWeight())
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                if (showGrid) {
                    HDivider()
                    BottomGrid(state, context)
                }
            }
        }
    }
}

@Composable
private fun WeatherZone(state: PanelState, context: Context) {
    val weather = state.weather
    val tap = if (state.haConfigured) launchIntent(context, Packages.WEATHER) else settingsIntent(context)
    val main = if (weather?.stale == true) Palette.ink3P else Palette.inkP
    val second = if (weather?.stale == true) Palette.ink3P else Palette.ink2P
    // Three forecast days need room beside the temperature; narrower widgets show fewer.
    val width = LocalSize.current.width
    val maxDays = when {
        width >= 340.dp -> 3
        width >= 280.dp -> 2
        else -> 0
    }
    val days = weather?.days.orEmpty().take(maxDays)
    val look = LocalLook.current
    // The refresh button is an overlay in the top-right corner so its large tap area doesn't push
    // the layout around.
    Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp)
                .opens(tap),
        ) {
            // Invisible shortcut: the date and city open HomePanel's settings. The rest of the weather
            // zone still opens Samsung Weather.
            Column(modifier = GlanceModifier.opens(settingsIntent(context))) {
                Label(state.date, sz(19, look.header), Palette.ink2P)
                // Where the forecast is for: the phone's location, or home when that's unknown.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.place != null) Label(state.place, sz(17, look.header), Palette.ink3P)
                    weather?.warning?.let {
                        Spacer(GlanceModifier.width(10.dp))
                        WarningChip(it, sz(15, look.header))
                    }
                    if (weather != null && Settings.get(context).showSource) {
                        Spacer(GlanceModifier.width(10.dp))
                        Label("· " + LocalTexts.current.source(weather.source), sz(13, look.header), Palette.ink3P)
                    }
                }
            }
            // Temperature | conditions now | next three days (fills the rest). Bottom-aligned.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // Glance has no light weight; Normal is the closest it allows with the system font.
                Label(weather?.temperature ?: "–", look.temperatureSp.sp, main)
                Spacer(GlanceModifier.width(16.dp))
                WeatherNow(state, weather, main, second, showTomorrow = days.isEmpty())
                if (days.isNotEmpty()) {
                    Row(
                        modifier = GlanceModifier.defaultWeight().padding(start = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        days.forEach { ForecastDay(it, GlanceModifier.defaultWeight(), main, second) }
                    }
                }
            }
        }
        Box(
            modifier = GlanceModifier
                .width(64.dp).height(60.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(R.drawable.ic_refresh, 22.dp, Palette.ink3P)
        }
    }
}

/** One column per day: name, icon, then high and low side by side. */
@Composable
private fun ForecastDay(day: ForecastDayState, modifier: GlanceModifier, main: ColorProvider, second: ColorProvider) {
    val k = LocalLook.current.forecast
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(day.label, sz(20, k), second)
        Spacer(GlanceModifier.height(4.dp))
        Icon(day.icon, dz(40, k), second)
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Label(day.high, sz(28, k), main)
            if (day.low != null) {
                Spacer(GlanceModifier.width(6.dp))
                Label(day.low, sz(22, k), Palette.ink3P)
            }
        }
    }
}

/** AQI + icon above the condition now, left-aligned next to the temperature. */
@Composable
private fun WeatherNow(
    state: PanelState,
    weather: WeatherState?,
    main: ColorProvider,
    second: ColorProvider,
    showTomorrow: Boolean,
) {
    val k = LocalLook.current.conditions
    Column(modifier = GlanceModifier.padding(bottom = 6.dp)) {
        if (weather == null) {
            Label(
                if (state.haConfigured) LocalTexts.current.waitingHa else LocalTexts.current.setUpHa,
                sz(17, k), Palette.ink2P,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(weather.icon, dz(30, k), second)
                if (weather.aqi != null) {
                    Spacer(GlanceModifier.width(8.dp))
                    Label(weather.aqi, sz(17, k), second)
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Label(weather.conditionNow, sz(19, k), main)
            weather.rain?.takeIf { Settings.get(LocalContext.current).showRain }?.let {
                Spacer(GlanceModifier.height(2.dp))
                Label(it, sz(15, k), second)
            }
            if (showTomorrow && weather.tomorrow != null) {
                Spacer(GlanceModifier.height(4.dp))
                Label(weather.tomorrow, sz(17, k), second)
            }
        }
    }
}

@Composable
private fun CalendarZone(snapshot: CalendarSnapshot?, context: Context, modifier: GlanceModifier) {
    val calendarApp = launchIntent(context, Packages.CALENDAR)
    val view: CalendarView? = snapshot?.takeIf { it.hasPermission }?.toView(locale = LocalTexts.current.locale, allDayLabel = LocalTexts.current.allDay)
    val k = LocalLook.current.calendar
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(dz(32, k)).opens(calendarApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(LocalTexts.current.comingUp, sz(15, k), Palette.ink3P)
            Spacer(GlanceModifier.defaultWeight())
            if (view?.todayEmpty == true) Label(LocalTexts.current.todayNoEvents, sz(15, k), Palette.ink3P)
            // New event in the default calendar app; a wide box so the small plus is easy to hit.
            Box(
                modifier = GlanceModifier.width(dz(44, k)).height(dz(32, k)).opens(newEventIntent()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(R.drawable.ic_add, dz(20, k), Palette.ink2P)
            }
        }
        when {
            snapshot != null && !snapshot.hasPermission -> MessageRow(
                LocalTexts.current.allowCalendar,
                settingsIntent(context),
            )
            view == null -> MessageRow(LocalTexts.current.loading, calendarApp)
            view.rows.isEmpty() -> MessageRow(LocalTexts.current.nothingNext(Settings.get(context).calendarDays), calendarApp)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(view.rows, itemId = { it.id }) { row ->
                    EventItem(row, isLast = row === view.rows.last())
                }
            }
        }
    }
}

@Composable
private fun MessageRow(text: String, tap: Intent?) {
    val k = LocalLook.current.calendar
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(RowHeight * k).opens(tap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, sz(17, k), Palette.ink2P)
    }
}

@Composable
private fun EventItem(row: EventRow, isLast: Boolean) {
    val k = LocalLook.current.calendar
    Column(modifier = GlanceModifier.fillMaxWidth().opens(row.event.viewIntent())) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(if (isLast) RowHeight * k else RowHeight * k - 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(
                row.dayLabel, sz(15, k), Palette.ink2P,
                modifier = GlanceModifier.width(dz(64, k)),
            )
            Spacer(GlanceModifier.width(12.dp))
            Label(row.time, sz(16, k), Palette.ink2P, modifier = GlanceModifier.width(dz(48, k)))
            Spacer(GlanceModifier.width(12.dp))
            // The event's color, as the calendar app shows it.
            row.event.color?.let { argb ->
                Box(
                    modifier = GlanceModifier
                        .width(dz(9, k)).height(dz(9, k))
                        .background(Color(argb or 0xFF000000.toInt()))
                        .cornerRadius(dz(5, k)),
                ) {}
                Spacer(GlanceModifier.width(dz(10, k)))
            }
            Label(row.title, sz(17, k), Palette.inkP, modifier = GlanceModifier.defaultWeight())
        }
        if (!isLast) HDivider()
    }
}

@Composable
private fun BottomGrid(state: PanelState, context: Context) {
    // Card is the widget width minus the two 1dp border insets; the grid splits it around a 1dp hairline.
    val columnWidth = (LocalSize.current.width - 2.dp) / 2
    val gridHeight = GridHeight * LocalLook.current.bottom
    // The vertical hairline is a nine-patch centred in the row's background: a fillMaxHeight child
    // would stretch the row to the whole widget height. Equal-weight columns meet exactly at the centre.
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(gridHeight)
            .background(ImageProvider(R.drawable.grid_divider), ContentScale.FillBounds),
    ) {
        CarZone(state.car, state.haConfigured, context, GlanceModifier.defaultWeight(), width = columnWidth, height = gridHeight)
        ClaudeZone(state.claude, context, GlanceModifier.defaultWeight(), barWidth = columnWidth - 34.dp)
    }
}

@Composable
private fun CarZone(
    car: CarState?,
    haConfigured: Boolean,
    context: Context,
    modifier: GlanceModifier,
    width: Dp,
    height: Dp,
) {
    val designB = LocalLook.current.designB
    val map = CarMap.load(context, grey = designB)
    val k = LocalLook.current.bottom
    // Design B: muted text on the grey map; address and range stay primary.
    val onMap = if (designB) OnMapB else null
    if (map == null) {
        CarZoneRender(car, haConfigured, context, modifier, width)
        return
    }
    val tap = carTap(context, haConfigured)
    val main = if (car == null || car.stale) Palette.ink3P else Palette.inkP
    // The map is pre-blurred and darkened on the left, sharp around the pin on the right.
    Box(modifier = modifier.fillMaxHeight().opens(tap)) {
        Image(
            provider = ImageProvider(map),
            contentDescription = "Map of where the car is parked",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.width(width).height(height),
        )
        Column(modifier = GlanceModifier.fillMaxSize().padding(start = 20.dp, top = 10.dp, end = 18.dp, bottom = 10.dp)) {
            CarHeader(car, onMap)
            Spacer(GlanceModifier.height(2.dp))
            Label(car?.address ?: "–", sz(22, k), main, weight = FontWeight.Medium)
            Label(car?.city ?: LocalTexts.current.waitingHa, sz(16, k), onMap ?: Palette.ink2P)
            Spacer(GlanceModifier.defaultWeight())
            CarRange(car, main, onMap)
        }
    }
}

private val UnlockedTint = ColorProvider(Color(0xFFE5A04F))

/** Design B's text and icon colour on the grey map. */
private val OnMapB = ColorProvider(Color(0xFFA2A4AB))

/** The car app chosen in settings; Home Assistant without one; settings until HA is set up. */
private fun carTap(context: Context, haConfigured: Boolean): Intent? =
    if (!haConfigured) settingsIntent(context)
    else Settings.get(context).carApp.takeIf { it.isNotBlank() }?.let { launchIntent(context, it) }
        ?: launchIntent(context, Packages.HOME_ASSISTANT)

@Composable
private fun CarHeader(car: CarState?, tint: ColorProvider? = null) {
    val k = LocalLook.current.bottom
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(R.drawable.ic_car, dz(18, k), tint ?: Palette.ink3P)
        Spacer(GlanceModifier.width(6.dp))
        Label(car?.name ?: Settings.get(LocalContext.current).carName.ifBlank { LocalTexts.current.car }, sz(15, k), tint ?: Palette.ink3P)
        // Padlock: closed and quiet when locked, open and amber when not.
        car?.locked?.let { locked ->
            Spacer(GlanceModifier.width(6.dp))
            if (locked) Icon(R.drawable.ic_lock, dz(15, k), tint ?: Palette.ink3P)
            else Icon(R.drawable.ic_lock_open, dz(15, k), UnlockedTint)
        }
        val area = car?.area?.takeIf { it.isNotBlank() }
        if (area != null) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(Palette.track)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Label(area, sz(13, k), Palette.ink2P)
            }
        }
    }
}

@Composable
private fun CarRange(car: CarState?, main: ColorProvider, tint: ColorProvider? = null) {
    val k = LocalLook.current.bottom
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(R.drawable.ic_fuel, dz(18, k), tint ?: Palette.ink2P)
        Spacer(GlanceModifier.width(8.dp))
        Label(car?.range ?: "–", sz(18, k), main)
    }
}

/** Fallback before a map exists: text on the left, the car render in the bottom-right corner. */
@Composable
private fun CarZoneRender(car: CarState?, haConfigured: Boolean, context: Context, modifier: GlanceModifier, width: Dp) {
    val tap = carTap(context, haConfigured)
    val main = if (car == null || car.stale) Palette.ink3P else Palette.inkP
    val k = LocalLook.current.bottom
    // The render sits in the bottom-right corner, half the column wide, below the address line;
    // the range (pump icon + value) stays on the left so it doesn't run into it.
    val inner = width - 38.dp
    val imageWidth = inner * 0.5f
    val imageHeight = imageWidth * 0.6f
    // Push the range row down far enough that the image's top stays under the address.
    val gap = (imageHeight - 40.dp).coerceAtLeast(4.dp)
    Box(
        modifier = modifier
            .padding(start = 20.dp, top = 10.dp, end = 18.dp, bottom = 10.dp)
            .opens(tap),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // The cached Home Assistant render; the outline drawing until one has been downloaded.
        val render = CarImage.load(context)
        Image(
            provider = render?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.car_placeholder),
            contentDescription = car?.name ?: "Car",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.width(imageWidth).height(imageHeight),
        )
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            CarHeader(car)
            Spacer(GlanceModifier.height(2.dp))
            Label(car?.address ?: "–", sz(22, k), main, weight = FontWeight.Medium)
            Label(
                car?.city ?: if (haConfigured) LocalTexts.current.waitingHa else LocalTexts.current.tapToSetUp,
                sz(16, k), Palette.ink2P,
            )
            Spacer(GlanceModifier.height(gap))
            CarRange(car, main)
        }
    }
}

@Composable
private fun ClaudeZone(claude: ClaudeState, context: Context, modifier: GlanceModifier, barWidth: Dp) {
    val k = LocalLook.current.bottom
    val signIn = Intent(context, ClaudeLoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Column(modifier = modifier.padding(start = 14.dp, top = 10.dp, end = 20.dp, bottom = 10.dp)) {
        // The title opens the Claude app; the bars below refresh usage.
        Label(
            LocalTexts.current.claudeTitle, sz(15, k), Palette.ink3P,
            modifier = GlanceModifier.fillMaxWidth().opens(launchIntent(context, Packages.CLAUDE)),
        )
        Spacer(GlanceModifier.height(dz(6, k)))
        when {
            claude.needsLogin && claude.fiveHour == null -> Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().opens(signIn)) {
                Label(LocalTexts.current.tapToSignIn, sz(18, k), Palette.inkP)
                Label(LocalTexts.current.toShowUsage, sz(13, k), Palette.ink3P)
            }
            else -> Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().let {
                    if (claude.needsLogin) it.opens(signIn) else it.clickable(actionRunCallback<ClaudeRefreshAction>())
                },
            ) {
                Usage(claude.fiveHour, LocalTexts.current.fiveHours, barWidth, claude, showFailure = true)
                Spacer(GlanceModifier.height(dz(8, k)))
                Usage(claude.week, LocalTexts.current.week, barWidth, claude)
            }
        }
    }
}

// Its own Column: Glance renders at most 10 children per Row/Column and drops the rest.
@Composable
private fun Usage(line: UsageLine?, label: String, barWidth: Dp, claude: ClaudeState, showFailure: Boolean = false) = Column(modifier = GlanceModifier.fillMaxWidth()) {
    val k = LocalLook.current.bottom
    val percent = line?.percent
    val value = if (claude.stale) Palette.ink3P else Palette.inkP
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, sz(16, k), Palette.ink2P)
        Spacer(GlanceModifier.defaultWeight())
        Label(percent?.let { "$it%" } ?: "–", sz(18, k), value)
    }
    Spacer(GlanceModifier.height(dz(4, k)))
    val track = barWidth.coerceAtLeast(0.dp)
    val fill = track * ((percent ?: 0).coerceIn(0, 100) / 100f)
    // Green → yellow → red across the whole track; the unused part is covered from the right, so the
    // tip's color shows how close the limit is.
    Box(
        modifier = GlanceModifier
            .width(track).height(dz(6, k))
            .background(ImageProvider(R.drawable.usage_gradient), ContentScale.FillBounds)
            .cornerRadius(3.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(fill))
            // Weight, not an exact width: the two dp widths round to pixels separately and could
            // leave a sliver of red showing at the end.
            if (track - fill > 0.dp) {
                Box(modifier = GlanceModifier.defaultWeight().height(dz(6, k)).background(Palette.track)) {}
            }
        }
    }
    Spacer(GlanceModifier.height(dz(4, k)))
    val note = when {
        claude.busy -> LocalTexts.current.updating
        claude.needsLogin -> LocalTexts.current.signedOut
        showFailure && claude.failed -> LocalTexts.current.notUpdated
        else -> line?.resets.orEmpty()
    }
    Label(note, sz(13, k), Palette.ink3P)
}

@Composable
internal fun HDivider() {
    Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Palette.hair)) {}
}

@Composable
internal fun Icon(res: Int, size: Dp, tint: ColorProvider) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.width(size).height(size),
    )
}

/** All text goes through here: system typeface, weight via FontWeight only, single line. */
@Composable
internal fun Label(
    text: String,
    size: TextUnit,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align),
    )
}
