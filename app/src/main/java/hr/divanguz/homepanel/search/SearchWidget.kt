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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import hr.divanguz.homepanel.R
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.widget.Palette
import hr.divanguz.homepanel.widget.Texts

private val Radius = 28.dp

/** Secondary widget: a search bar in the panel's colours that opens Firefox's search. */
class SearchWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { SearchBar(context) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { SearchBar(context) }
    }
}

class SearchReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchWidget()
}

@androidx.compose.runtime.Composable
private fun SearchBar(context: Context) {
    val texts = Texts.of(Settings.get(context).language)
    val tint = ColorFilter.tint(Palette.ink2P)
    // Hairline border faked with an outer box, as on the panel.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Palette.hair)
            .cornerRadius(Radius)
            .padding(1.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Palette.card)
                .cornerRadius(Radius - 1.dp)
                .clickable(actionStartActivity(Firefox.searchIntent(context)))
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_search), null, GlanceModifier.size(20.dp), colorFilter = tint)
            Spacer(GlanceModifier.width(14.dp))
            Text(
                texts.searchWeb,
                style = TextStyle(color = Palette.ink2P, fontSize = 15.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .clickable(actionStartActivity(Intent(context, VoiceSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_mic), null, GlanceModifier.size(20.dp), colorFilter = tint)
            }
        }
    }
}
