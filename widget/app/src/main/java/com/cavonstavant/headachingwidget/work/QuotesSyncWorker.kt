package com.cavonstavant.headachingwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cavonstavant.headachingwidget.data.QuoteRepository
import com.cavonstavant.headachingwidget.widget.QuoteWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pulls a fresh copy of the upstream quote list so new quotes need no app update. */
class QuotesSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!QuoteRepository.get(applicationContext).refresh()) {
            return@withContext Result.retry()
        }
        // The list length feeds the daily index, so a new list can mean a new
        // quote for today — repaint rather than wait for the next rollover.
        QuoteWidgetProvider.refreshAll(applicationContext)
        Result.success()
    }
}
