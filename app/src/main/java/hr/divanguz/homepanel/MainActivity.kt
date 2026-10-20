package hr.divanguz.homepanel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import hr.divanguz.homepanel.settings.SettingsApp
import hr.divanguz.homepanel.widget.Preview
import hr.divanguz.homepanel.work.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Settings (also the launcher icon). The UI lives in [SettingsApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Refresh.ensureScheduled(this)
        // Picker preview with real data; rate-limited by the system, so once per install.
        lifecycleScope.launch(Dispatchers.IO) { Preview.publishOnce(applicationContext) }
        setContent { SettingsApp(goHome = ::goHome) }
    }

    /** Leaves the app for the launcher, where the freshly refreshed widget is. */
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
