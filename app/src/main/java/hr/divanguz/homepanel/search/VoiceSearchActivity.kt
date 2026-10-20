package hr.divanguz.homepanel.search

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible: shows Android's speech prompt and hands the words to Firefox as a web search.
 * Firefox's own voice activity isn't exported, so this stands in for its widget's mic.
 */
class VoiceSearchActivity : ComponentActivity() {

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) start(Firefox.webSearchIntent(this, words))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val prompt = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        try {
            speech.launch(prompt)
        } catch (_: ActivityNotFoundException) {
            // No speech recognizer on the phone: fall back to typing.
            start(Firefox.searchIntent(this))
            finish()
        }
    }

    private fun start(intent: Intent) {
        runCatching { startActivity(intent) }
    }
}
