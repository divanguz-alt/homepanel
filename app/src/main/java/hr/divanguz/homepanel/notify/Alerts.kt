package hr.divanguz.homepanel.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import hr.divanguz.homepanel.MainActivity
import hr.divanguz.homepanel.claude.ClaudeSnapshot
import hr.divanguz.homepanel.ha.HaSnapshot
import hr.divanguz.homepanel.settings.Settings
import hr.divanguz.homepanel.widget.Packages
import hr.divanguz.homepanel.widget.Texts
import hr.divanguz.homepanel.widget.launchIntent
import kotlin.math.roundToInt

/**
 * Optional notifications (Settings → Notifications), all off until switched on:
 * - the temperature as a quiet, ongoing notification whose status-bar icon *is* the number, so it shows
 *   on the lock screen and in the status bar (Samsung's AOD shows notification icons, not their text);
 * - a Claude warning when a limit passes the chosen percentage, and a note when that window resets.
 */
object Alerts {
    // Default importance but silent: "low" channels are hidden from the status bar (Pixel and One UI hide
    // silent notification icons), which is the whole point of this one.
    private const val CH_WEATHER = "weather_now_icon"
    private const val CH_CLAUDE = "claude_limits"
    private const val ID_WEATHER = 1
    private const val ID_CLAUDE_5H = 2
    private const val ID_CLAUDE_WEEK = 3
    private const val PREFS = "homepanel_cache"

    fun canPost(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context): NotificationManager {
        val m = context.getSystemService(NotificationManager::class.java)
        m.deleteNotificationChannel("weather_now")
        val hr = Settings.get(context).language == "hr"
        m.createNotificationChannel(
            NotificationChannel(CH_WEATHER, if (hr) "Temperatura" else "Temperature", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_CLAUDE, if (hr) "Claude limiti" else "Claude limits", NotificationManager.IMPORTANCE_DEFAULT),
        )
        return m
    }

    private fun open(context: Context, intent: Intent?, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        (intent ?: Intent(context, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ---- temperature -----------------------------------------------------------------------

    /** Posts (or updates) the temperature; removes it when switched off or there is nothing to show. */
    fun weatherChanged(context: Context, ha: HaSnapshot) {
        val settings = Settings.get(context)
        val w = ha.weather
        val t = w?.temperature
        if (!settings.tempNotification || !canPost(context) || t == null) {
            if (!settings.tempNotification) clearWeather(context)
            return
        }
        val texts = Texts.of(settings.language)
        val degrees = "${t.roundToInt()}°"
        val tomorrowDate = java.time.LocalDate.now().plusDays(1).toString()
        val tomorrow = w.days.firstOrNull { it.date == tomorrowDate }?.let { d ->
            texts.tomorrow(texts.condition(d.condition.orEmpty()), d.high?.let { ", ${it.roundToInt()}°" }.orEmpty())
        }
        val n = Notification.Builder(context, CH_WEATHER)
            .setSmallIcon(Icon.createWithBitmap(numberIcon(context, degrees)))
            .setContentTitle("$degrees · ${texts.condition(w.condition)}")
            .setContentText(listOfNotNull(w.place, tomorrow).joinToString(" · "))
            .setContentIntent(open(context, launchIntent(context, Packages.WEATHER), ID_WEATHER))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        manager(context).notify(ID_WEATHER, n)
    }

    fun clearWeather(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID_WEATHER)

    /** The temperature drawn as a white status-bar icon, as large as it fits. */
    private fun numberIcon(context: Context, text: String): Bitmap {
        val px = (24 * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textAlign = Paint.Align.CENTER
            textSize = px.toFloat()
        }
        // Shrink until it fits the width ("-12°" is the widest case).
        while (paint.measureText(text) > px * 1.02f && paint.textSize > 6f) paint.textSize -= 1f
        val y = px / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(text, px / 2f, y, paint)
        return bitmap
    }

    // ---- Claude ----------------------------------------------------------------------------

    /**
     * Warns once per usage window: the window is identified by its reset time, stored when warned. When a
     * check finds that stored reset time has passed, the window has reset and that's announced once.
     */
    fun claudeChecked(context: Context, s: ClaudeSnapshot) {
        val settings = Settings.get(context)
        if (!settings.claudeAlerts || !canPost(context) || !s.hasData || s.needsLogin) return
        val hr = settings.language == "hr"
        val now = System.currentTimeMillis()
        window(context, "5h", ID_CLAUDE_5H, s.fiveHour, s.fiveHourResetsAt, now, settings.claudeThreshold,
            if (hr) "5-satni limit" else "5-hour limit", hr)
        window(context, "week", ID_CLAUDE_WEEK, s.week, s.weekResetsAt, now, settings.claudeThreshold,
            if (hr) "Tjedni limit" else "Weekly limit", hr)
    }

    private fun window(
        context: Context, key: String, id: Int, percent: Float?, resetsAt: Long, now: Long, threshold: Int,
        label: String, hr: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val warnedFor = prefs.getLong("alert_claude_$key", 0L)
        val claude = launchIntent(context, Packages.CLAUDE)
        when (decide(percent?.roundToInt(), resetsAt, warnedFor, now, threshold)) {
            Step.RESET -> {
                prefs.edit().remove("alert_claude_$key").apply()
                post(context, id, if (hr) "Claude · $label se resetirao" else "Claude · $label has reset",
                    if (hr) "Potrošnja za ovaj limit kreće od nule." else "Usage for this limit starts from zero again.", claude)
            }
            Step.WARN -> {
                prefs.edit().putLong("alert_claude_$key", resetsAt).apply()
                val left = resetsAt - now
                val h = left / 3_600_000
                val m = (left / 60_000) % 60
                val span = if (h >= 24) "${h / 24}d ${h % 24}h" else if (h > 0) "${h}h ${m}m" else "${m}m"
                post(context, id, "Claude · $label ${percent?.roundToInt()}%",
                    if (hr) "Resetira se za $span" else "Resets in $span", claude)
            }
            Step.NONE -> Unit
        }
    }

    internal enum class Step { NONE, WARN, RESET }

    /** Reset times from claude.ai wobble a little between fetches; windows are hours apart. */
    private const val SAME_WINDOW_MS = 30 * 60_000L

    /**
     * What to do for one usage window. [warnedFor] is the reset time of the window last warned about
     * (0 = none): once that time has passed the window has reset, which is announced once.
     */
    internal fun decide(percent: Int?, resetsAt: Long, warnedFor: Long, now: Long, threshold: Int): Step = when {
        warnedFor > 0 && now >= warnedFor -> Step.RESET
        percent == null || percent < threshold || resetsAt <= now -> Step.NONE
        warnedFor > 0 && kotlin.math.abs(warnedFor - resetsAt) < SAME_WINDOW_MS -> Step.NONE
        else -> Step.WARN
    }

    private fun post(context: Context, id: Int, title: String, text: String, intent: Intent?) {
        val n = Notification.Builder(context, CH_CLAUDE)
            .setSmallIcon(hr.divanguz.homepanel.R.drawable.ic_spark)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(context, intent, id))
            .setAutoCancel(true)
            .build()
        manager(context).notify(id, n)
    }
}
