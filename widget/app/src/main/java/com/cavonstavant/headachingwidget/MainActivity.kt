package com.cavonstavant.headachingwidget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.cavonstavant.headachingwidget.data.QuoteRepository
import com.cavonstavant.headachingwidget.widget.QuoteWidgetProvider
import com.cavonstavant.headachingwidget.work.WidgetScheduler

/**
 * A one-screen companion to the widget: today's quote, how to place the widget,
 * and a manual refresh. The refresh button is the debugging affordance — it is
 * the quickest way to prove the network path and repaint every placed widget.
 */
class MainActivity : Activity() {

    private val quoteView: TextView by lazy { findViewById(R.id.quote) }
    private val creditView: TextView by lazy { findViewById(R.id.credit) }
    private val statusView: TextView by lazy { findViewById(R.id.status) }

    private companion object {
        val ROLLOVER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        // Cheap and idempotent (KEEP), so a widget placed before an app update
        // still ends up with its schedule in place.
        WidgetScheduler.scheduleAll(this)

        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        showToday()
        showNextRollover()
        // Opening the app is a cheap chance to correct a widget whose alarm was
        // dropped, and it closes the gap if the app is launched inside the short
        // window between a rollover and its alarm firing.
        QuoteWidgetProvider.refreshAll(this)
    }

    /** Surfaces the armed rollover so a silent scheduling failure is visible. */
    private fun showNextRollover() {
        val armed = WidgetScheduler.nextRolloverMillis(this)
        findViewById<TextView>(R.id.next_update).text = if (armed <= 0L) {
            getString(R.string.next_update_unscheduled)
        } else {
            val local = Instant.ofEpochMilli(armed).atZone(ZoneId.systemDefault())
            getString(R.string.next_update, ROLLOVER_FORMAT.format(local))
        }
    }

    /**
     * Android 15 enforces edge-to-edge, so the status and navigation bars would
     * sit on top of the content. Insets are *added* to the base padding —
     * fitsSystemWindows would replace it and lose the horizontal margin.
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        val base = resources.getDimensionPixelSize(R.dimen.screen_padding)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                base + bars.left,
                base + bars.top,
                base + bars.right,
                base + bars.bottom,
            )
            insets
        }
    }

    /** Off the main thread: the first read parses ~290 KB of JSON. */
    private fun showToday() {
        Thread {
            val quote = QuoteRepository.get(this).today()
            runOnUiThread {
                quoteView.text = quote?.display ?: getString(R.string.no_quotes)
                creditView.text = quote?.credit.orEmpty()
            }
        }.start()
    }

    private fun refresh() {
        statusView.setText(R.string.refreshing)
        Thread {
            val refreshed = QuoteRepository.get(this).refresh()
            runOnUiThread {
                statusView.setText(
                    if (refreshed) R.string.refresh_done else R.string.refresh_failed
                )
                showToday()
                QuoteWidgetProvider.refreshAll(this)
            }
        }.start()
    }
}
