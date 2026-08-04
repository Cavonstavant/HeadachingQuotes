package com.cavonstavant.headachingwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import com.cavonstavant.headachingwidget.R
import com.cavonstavant.headachingwidget.ShareActivity
import com.cavonstavant.headachingwidget.data.Quote
import com.cavonstavant.headachingwidget.data.QuoteRepository
import com.cavonstavant.headachingwidget.work.WidgetScheduler

class QuoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = buildViews(context, QuoteRepository.get(context).today())
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }

        // Reached on the coarse updatePeriodMillis tick as well as on placement,
        // so a dropped alarm re-arms itself here instead of staying lost.
        WidgetScheduler.scheduleRollover(context)
    }

    override fun onEnabled(context: Context) = WidgetScheduler.scheduleAll(context)

    /** Widgets restored from a backup have no alarm armed for them yet. */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetScheduler.scheduleAll(context)
    }

    override fun onDisabled(context: Context) = WidgetScheduler.cancelAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // The daily alarm. Re-arm before repainting so a failure to render
            // can never leave the widget with no future update scheduled.
            ACTION_ROLLOVER -> {
                WidgetScheduler.scheduleRollover(context)
                refreshAll(context)
            }
            // Fires at *local* midnight, so in CEST it lands two hours before the
            // UTC rollover and repaints the same quote. Harmless, and it earns its
            // keep on timezone changes and in zones at or behind UTC.
            Intent.ACTION_DATE_CHANGED -> refreshAll(context)
        }
    }

    companion object {

        /** Sent by the alarm in [WidgetScheduler]; internal to this app. */
        const val ACTION_ROLLOVER = "com.cavonstavant.headachingwidget.ROLLOVER"

        /** Repaints every placed widget with the current day's quote. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, QuoteWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val views = buildViews(context, QuoteRepository.get(context).today())
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /**
         * One RemoteViews carrying both layouts. The launcher picks whichever
         * fits the placed size, so there is no size bookkeeping on our side and
         * no need to react to [onAppWidgetOptionsChanged].
         */
        @VisibleForTesting
        internal fun buildViews(context: Context, quote: Quote?): RemoteViews = RemoteViews(
            mapOf(
                SizeF(110f, 110f) to
                    views(context, quote, R.layout.widget_quote_small, withCredit = false),
                SizeF(180f, 110f) to
                    views(context, quote, R.layout.widget_quote, withCredit = true),
            )
        )

        private fun views(
            context: Context,
            quote: Quote?,
            layoutId: Int,
            withCredit: Boolean,
        ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(
                R.id.widget_quote,
                quote?.display ?: context.getString(R.string.no_quotes),
            )
            // widget_quote_small has no credit view; setting it there would throw.
            if (withCredit) {
                setTextViewText(R.id.widget_credit, quote?.song.orEmpty())
            }
            setOnClickPendingIntent(R.id.widget_root, sharePendingIntent(context))
        }

        private fun sharePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ShareActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
