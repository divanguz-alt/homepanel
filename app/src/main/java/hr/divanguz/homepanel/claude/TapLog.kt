package hr.divanguz.homepanel.claude

import android.content.Context

/**
 * What happened to the last tap on the widget's Claude bars, step by step, so Settings can show
 * where it stopped on a phone without adb. Times are epoch millis, 0 = didn't happen.
 */
object TapLog {
    private const val PREFS = "homepanel_cache"
    const val TAPPED = "tap_tapped"
    const val QUEUED = "tap_queued"
    const val STARTED = "tap_started"
    const val FINISHED = "tap_finished"
    private const val RESULT = "tap_result"

    data class Entry(val tapped: Long, val queued: Long, val started: Long, val finished: Long, val result: String?)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A new tap: clears the later steps of the previous one. */
    fun tapped(c: Context) {
        prefs(c).edit().putLong(TAPPED, System.currentTimeMillis())
            .remove(QUEUED).remove(STARTED).remove(FINISHED).remove(RESULT).commit()
    }

    fun mark(c: Context, step: String) {
        prefs(c).edit().putLong(step, System.currentTimeMillis()).commit()
    }

    fun finished(c: Context, result: String) {
        prefs(c).edit().putLong(FINISHED, System.currentTimeMillis()).putString(RESULT, result).commit()
    }

    fun read(c: Context): Entry? = prefs(c).let {
        val tapped = it.getLong(TAPPED, 0)
        if (tapped == 0L) null
        else Entry(tapped, it.getLong(QUEUED, 0), it.getLong(STARTED, 0), it.getLong(FINISHED, 0), it.getString(RESULT, null))
    }
}
