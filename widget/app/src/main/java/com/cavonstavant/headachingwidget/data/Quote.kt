package com.cavonstavant.headachingwidget.data

/**
 * One lyric quote, mirroring the four fields in `data/quotes.js` at the repository root.
 * There is no id upstream — position in the array is the only identity.
 */
data class Quote(
    val text: String,
    val song: String,
    val artist: String,
    val album: String,
) {

    /**
     * Credit line built the same way `js/app.js:70-74` builds it: song, em dash,
     * artist, then the album in parentheses unless it is the placeholder
     * [UNKNOWN_ALBUM], which upstream uses when Genius had no album.
     */
    val credit: String
        get() = buildString {
            append(song)
            append(" — ")
            append(artist)
            if (album.isNotEmpty() && album != UNKNOWN_ALBUM) {
                append(" (").append(album).append(')')
            }
        }

    /**
     * The quote wrapped in curly quotes and with couplets broken onto their own
     * lines. Upstream joins two consecutive lyric lines with a literal `" / "`
     * (`fetch_lyrics.py:236-247`) and the SVG renderer splits there
     * (`generate_daily_svg.py:88-99`), so a hard break is the faithful rendering.
     */
    val display: String
        get() = "“" + text.replace(COUPLET_SEPARATOR, "\n") + "”"

    companion object {
        const val UNKNOWN_ALBUM = "Single"
        const val COUPLET_SEPARATOR = " / "
    }
}
