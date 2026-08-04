package com.cavonstavant.headachingwidget.data

import org.json.JSONArray

/**
 * Reads the upstream quote list.
 *
 * The file served by GitHub Pages is JavaScript rather than JSON — it assigns
 * `window.QUOTES_DATA = [ ... ];` so the site works over `file://` — so the
 * assignment has to come off first, the same thing `generate_daily_svg.py:184`
 * does. Bare JSON is accepted too, which lets the bundled asset and the network
 * response share this one path.
 */
object QuoteParser {

    fun parse(raw: String): List<Quote> {
        val array = JSONArray(unwrap(raw))
        val quotes = ArrayList<Quote>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val text = entry.optString("text")
            if (text.isEmpty()) continue
            quotes += Quote(
                text = text,
                song = entry.optString("song"),
                artist = entry.optString("artist"),
                album = entry.optString("album"),
            )
        }
        return quotes
    }

    /**
     * Strips the `window.QUOTES_DATA = ` wrapper by locating the array bounds
     * rather than matching the assignment text, so whitespace or a renamed
     * global upstream does not break parsing.
     */
    private fun unwrap(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return trimmed

        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        require(start >= 0 && end > start) { "no JSON array found in quote payload" }
        return trimmed.substring(start, end + 1)
    }
}
