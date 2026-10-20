package hr.divanguz.homepanel.work

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.divanguz.homepanel.widget.PanelStore
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Reloads widget data and redraws. Every job below runs this worker. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            // Calendar edits only need the calendar; everything else also refreshes Home Assistant.
            val kind = inputData.getString(KEY_KIND)
            PanelStore.refreshAll(applicationContext, includeHa = kind != KIND_CALENDAR)
        } catch (t: Throwable) {
            android.util.Log.e("HomePanel", "Refresh failed", t)
        }
        // Content-URI and midnight jobs are one-shot; queue the next one behind this run.
        when (inputData.getString(KEY_KIND)) {
            KIND_CALENDAR -> Refresh.enqueueCalendarObserver(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            KIND_MIDNIGHT -> Refresh.enqueueMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_CALENDAR = "calendar"
        const val KIND_MIDNIGHT = "midnight"
    }
}

/** Claude usage only, started by a tap on the widget's bars. */
class ClaudeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PanelStore.refreshClaude(applicationContext)
        return Result.success()
    }
}

object Refresh {
    private const val PERIODIC = "panel-periodic"
    private const val CALENDAR = "panel-calendar-observer"
    private const val MIDNIGHT = "panel-midnight"
    private const val NOW = "panel-now"
    private const val CLAUDE_NOW = "panel-claude-now"

    /** Idempotent: safe to call from the receiver, the app and after permission changes. */
    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
        enqueueCalendarObserver(context, ExistingWorkPolicy.KEEP)
        enqueueMidnight(context, ExistingWorkPolicy.KEEP)
    }

    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build(),
        )
    }

    /** Expedited so it starts at once; falls back to normal work when the quota is used up. */
    fun claudeNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CLAUDE_NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ClaudeWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    /** The clock or zone moved, so the pending midnight job points at the wrong time. */
    fun rescheduleMidnight(context: Context) = enqueueMidnight(context, ExistingWorkPolicy.REPLACE)

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        listOf(PERIODIC, CALENDAR, MIDNIGHT, NOW, CLAUDE_NOW).forEach(wm::cancelUniqueWork)
    }

    internal fun enqueueCalendarObserver(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
            .setTriggerContentMaxDelay(Duration.ofSeconds(10))
            .build()
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setInputData(androidx.work.workDataOf(RefreshWorker.KEY_KIND to RefreshWorker.KIND_CALENDAR))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CALENDAR, policy, request)
    }

    internal fun enqueueMidnight(context: Context, policy: ExistingWorkPolicy) {
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusSeconds(5)
        val delay = Duration.between(ZonedDateTime.now(zone), next)
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delay)
            .setInputData(androidx.work.workDataOf(RefreshWorker.KEY_KIND to RefreshWorker.KIND_MIDNIGHT))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT, policy, request)
    }
}
