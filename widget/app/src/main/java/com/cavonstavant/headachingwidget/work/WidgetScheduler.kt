package com.cavonstavant.headachingwidget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cavonstavant.headachingwidget.data.AppPrefs
import com.cavonstavant.headachingwidget.data.DailyQuote
import com.cavonstavant.headachingwidget.widget.QuoteWidgetProvider
import java.time.Clock
import java.util.concurrent.TimeUnit

object WidgetScheduler {

    private const val SYNC_WORK = "headaching-sync"
    private const val SYNC_INTERVAL_DAYS = 7L
    private const val ROLLOVER_REQUEST_CODE = 1

    fun scheduleAll(context: Context) {
        scheduleRollover(context)
        scheduleSync(context)
    }

    /**
     * Arms a one-shot alarm for the next UTC midnight, when the site's quote
     * changes. [QuoteWidgetProvider] re-arms the next one when this fires.
     *
     * This used to be a `PeriodicWorkRequest(1, DAYS)` and that was wrong. With
     * no flex interval, WorkManager defaults the flex window to the whole repeat
     * interval, so only the first run was anchored to midnight — every one after
     * it could fire anywhere inside its 24-hour window. WorkManager also makes
     * no time-of-day guarantee at all, and a widget-only app the user never
     * opens sinks into a restricted standby bucket where jobs are deferred hard.
     * The result was a widget that changed its quote at an arbitrary time, or
     * not at all on a given day.
     *
     * `setAndAllowWhileIdle` is inexact — it can slip by minutes — but it fires
     * during Doze and needs no permission, unlike `setExactAndAllowWhileIdle`
     * with its `SCHEDULE_EXACT_ALARM` grant. Minutes of slip is invisible here;
     * hours of drift was not.
     */
    fun scheduleRollover(context: Context, clock: Clock = Clock.systemUTC()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = clock.millis() + DailyQuote.millisUntilNextRollover(clock)

        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            rolloverIntent(context),
        )
        AppPrefs.of(context).edit { putLong(AppPrefs.KEY_NEXT_ROLLOVER, triggerAt) }
    }

    /**
     * Picks up new quotes without an app update. Genuinely deferrable and
     * network-bound, which is what WorkManager is actually good at.
     */
    fun scheduleSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<QuotesSyncWorker>(SYNC_INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(rolloverIntent(context))
        AppPrefs.of(context).edit { remove(AppPrefs.KEY_NEXT_ROLLOVER) }
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK)
    }

    /** Epoch millis the rollover is armed for, or 0 if nothing is armed. */
    fun nextRolloverMillis(context: Context): Long =
        AppPrefs.of(context).getLong(AppPrefs.KEY_NEXT_ROLLOVER, 0L)

    private fun rolloverIntent(context: Context): PendingIntent {
        val intent = Intent(context, QuoteWidgetProvider::class.java)
            .setAction(QuoteWidgetProvider.ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            context,
            ROLLOVER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
