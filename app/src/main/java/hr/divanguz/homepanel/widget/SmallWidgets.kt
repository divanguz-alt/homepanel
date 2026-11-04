package hr.divanguz.homepanel.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import hr.divanguz.homepanel.settings.Settings

/**
 * Small widgets with one zone of the panel each, so zones can be placed anywhere on the home screen.
 * They keep their data in the same Glance state as the panel and [PanelStore] refreshes them with it.
 */
abstract class ZoneWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    abstract fun Content(state: PanelState)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (PanelStore.ha(getAppWidgetState<Preferences>(context, id)) == null) PanelStore.seed(context, id)
        provideContent {
            val prefs = currentState<Preferences>()
            PanelStore.redrawStamp(prefs)
            val settings = Settings.get(context)
            val texts = Texts.of(settings.language)
            val look = settings.look()
            Palette.use(context, look.theme)
            CompositionLocalProvider(LocalLook provides look, LocalTexts provides texts) {
                Card {
                    Content(
                        panelState(
                            PanelStore.ha(prefs), settings.ha().isConfigured,
                            PanelStore.claude(prefs), PanelStore.claudeBusy(prefs),
                            carName = settings.carName, texts = texts,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            val settings = Settings.get(context)
            val texts = Texts.of(settings.language)
            val look = settings.look()
            Palette.use(context, look.theme)
            CompositionLocalProvider(LocalLook provides look, LocalTexts provides texts) {
                Card {
                    Content(panelState(Preview.ha(context), true, Preview.claude(context), false, settings.carName, texts))
                }
            }
        }
    }
}

class ClaudeWidget : ZoneWidget() {
    @Composable
    override fun Content(state: PanelState) {
        val width = LocalSize.current.width - 2.dp
        ClaudeZone(state.claude, LocalContext.current, GlanceModifier.fillMaxSize(), barWidth = width - 34.dp, atLeftEdge = true, centered = true)
    }
}

class CarWidget : ZoneWidget() {
    @Composable
    override fun Content(state: PanelState) {
        val size = LocalSize.current
        CarZone(state.car, state.haConfigured, LocalContext.current, GlanceModifier.fillMaxSize(), width = size.width - 2.dp, height = size.height - 2.dp)
    }
}

/** Temperature, condition and today's place; the next days when there is room. */
class WeatherWidget : ZoneWidget() {
    @Composable
    override fun Content(state: PanelState) {
        val context = LocalContext.current
        val w = state.weather
        val size = LocalSize.current
        val main = if (w?.stale == true) Palette.ink3P else Palette.inkP
        val second = if (w?.stale == true) Palette.ink3P else Palette.ink2P
        val tap = if (state.haConfigured) launchIntent(context, Packages.WEATHER) else settingsIntent(context)
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).opens(tap)) {
            Label(state.place ?: state.date, 14.sp, Palette.ink3P)
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(w?.temperature ?: "–", (if (size.height < 120.dp) 40 else 52).sp, main)
                if (w != null) {
                    Spacer(GlanceModifier.width(10.dp))
                    Icon(w.icon, 30.dp, second)
                }
            }
            Label(w?.condition ?: LocalTexts.current.waitingHa, 15.sp, second)
            // Room for the next days (two cells wide or more).
            val days = w?.days.orEmpty().take(if (size.width >= 250.dp) 3 else 0)
            if (days.isNotEmpty() && size.height >= 150.dp) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    days.forEach { d ->
                        Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                            Label(d.label, 13.sp, Palette.ink3P)
                            Spacer(GlanceModifier.width(4.dp))
                            Icon(d.icon, 16.dp, second)
                            Spacer(GlanceModifier.width(4.dp))
                            Label(d.high, 14.sp, main)
                        }
                    }
                }
            }
        }
    }
}

class ClaudeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClaudeWidget()
    override fun onUpdate(context: Context, manager: android.appwidget.AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        hr.divanguz.homepanel.work.Refresh.ensureScheduled(context)
    }
}

class CarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarWidget()
    override fun onUpdate(context: Context, manager: android.appwidget.AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        hr.divanguz.homepanel.work.Refresh.ensureScheduled(context)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
    override fun onUpdate(context: Context, manager: android.appwidget.AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        hr.divanguz.homepanel.work.Refresh.ensureScheduled(context)
    }
}
