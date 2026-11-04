package hr.divanguz.homepanel.search

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.widget.Palette
import hr.divanguz.homepanel.widget.Texts

private val BarHeight = 50.dp

/** Secondary widget: a search bar in the panel's colours that opens Firefox's search. */
class SearchWidget : GlanceAppWidget() {
    // The real cell height, so the tall bar can shrink to fit a one-row widget.
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Reading the stamp makes a theme or language change recompose a running session.
            hr.divanguz.homepanel.widget.PanelStore.redrawStamp(androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>())
            Palette.use(context, hr.divanguz.homepanel.settings.Settings.get(context).look().theme)
            SearchBar(context)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            Palette.use(context, hr.divanguz.homepanel.settings.Settings.get(context).look().theme)
            SearchBar(context)
        }
    }
}

class SearchReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchWidget()
}

@androidx.compose.runtime.Composable
private fun SearchBar(context: Context) {
    val texts = Texts.of(Settings.get(context).language)
    val tint = ColorFilter.tint(Palette.ink2P)
    // Tall (Settings → Appearance): twice Firefox's height, as much of it as the cell allows.
    val tall = Settings.get(context).searchTall
    val barHeight = if (tall) minOf(BarHeight * 2, androidx.glance.LocalSize.current.height.coerceAtLeast(BarHeight)) else BarHeight
    // The same corners as the panel and the phone's other widgets; at most a pill on a short bar.
    val radius = minOf(hr.divanguz.homepanel.widget.widgetRadius(context), barHeight / 2)
    val iconSize = if (tall) 24.dp else 20.dp
    val textSize = if (tall) 17.sp else 15.sp
    // Same size as Firefox's own bar: 50dp high, centred in the cell, full width.
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Hairline border faked with an outer box, as on the panel.
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Palette.borderP)
                .cornerRadius(radius)
                .padding(1.dp),
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Palette.cardP)
                    .cornerRadius(radius - 1.dp)
                    .clickable(actionStartActivity(Firefox.searchIntent(context)))
                    .padding(start = if (tall) 24.dp else 18.dp, end = if (tall) 8.dp else 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.ic_search), null, GlanceModifier.size(iconSize), colorFilter = tint)
                Spacer(GlanceModifier.width(14.dp))
                Text(
                    texts.searchWeb,
                    style = TextStyle(color = Palette.ink2P, fontSize = textSize),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .clickable(actionStartActivity(Intent(context, VoiceSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_mic), null, GlanceModifier.size(iconSize), colorFilter = tint)
                }
            }
        }
    }
}
