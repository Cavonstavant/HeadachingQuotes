package com.cavonstavant.headachingwidget.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock

/**
 * Supplies quotes to the widget: a snapshot bundled in the APK for first run and
 * offline use, overlaid by a cached copy pulled from GitHub Pages so new quotes
 * arrive without an app update.
 */
class QuoteRepository private constructor(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_NAME)

    private val prefs
        get() = AppPrefs.of(context)

    @Volatile
    private var cached: List<Quote>? = null

    /** The quote the website is showing right now, or null if nothing loaded. */
    fun today(clock: Clock = Clock.systemUTC()): Quote? {
        val quotes = quotes()
        if (quotes.isEmpty()) return null
        return quotes[DailyQuote.dailyIndex(quotes.size, DailyQuote.todayStringUtc(clock))]
    }

    /** Held in memory so a rollover does not re-parse ~290 KB of JSON. */
    fun quotes(): List<Quote> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load().also { cached = it }
        }
    }

    private fun load(): List<Quote> {
        if (cacheFile.exists()) {
            runCatching { QuoteParser.parse(cacheFile.readText()) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { QuoteParser.parse(it.readText()) }
        }.getOrDefault(emptyList())
    }

    /**
     * Pulls a fresh copy of the upstream list, replacing the cache wholesale.
     *
     * Writes only after the payload has parsed to a non-empty list, and swaps it
     * in via a temp-file rename, so a truncated or mangled response can never
     * leave the widget with nothing to show.
     */
    fun refresh(): Boolean {
        val body = runCatching { download() }.getOrNull() ?: return false
        val parsed = runCatching { QuoteParser.parse(body) }.getOrNull() ?: return false
        if (parsed.isEmpty()) return false

        val temp = File(context.filesDir, "$CACHE_NAME.tmp")
        return runCatching {
            temp.writeText(body)
            check(temp.renameTo(cacheFile)) { "could not swap in the new quote cache" }
            synchronized(this) { cached = parsed }
            prefs.edit { putLong(AppPrefs.KEY_LAST_REFRESH, System.currentTimeMillis()) }
            true
        }.getOrElse {
            temp.delete()
            false
        }
    }

    /** Epoch millis of the last successful refresh, or 0 if never. */
    fun lastRefreshMillis(): Long = prefs.getLong(AppPrefs.KEY_LAST_REFRESH, 0L)

    private fun download(): String {
        val connection = (URL(QUOTES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "unexpected response ${connection.responseCode} from $QUOTES_URL"
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SITE_URL = "https://cavonstavant.github.io/HeadachingQuotes/"
        const val QUOTES_URL = "https://cavonstavant.github.io/HeadachingQuotes/data/quotes.js"

        private const val ASSET_NAME = "quotes.json"
        private const val CACHE_NAME = "quotes.json"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000

        // Not the leak lint infers: the constructor is private and [get] only
        // ever passes applicationContext, which outlives the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: QuoteRepository? = null

        fun get(context: Context): QuoteRepository =
            instance ?: synchronized(this) {
                instance ?: QuoteRepository(context.applicationContext).also { instance = it }
            }
    }
}
