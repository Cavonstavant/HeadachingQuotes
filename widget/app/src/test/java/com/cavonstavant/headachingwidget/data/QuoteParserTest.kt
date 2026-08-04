package com.cavonstavant.headachingwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteParserTest {

    @Test
    fun `parses the upstream javascript wrapper`() {
        val raw = """
            window.QUOTES_DATA = [{"text":"a quote","song":"Song","artist":"Headache (PLZ)","album":"An Album"}];
        """.trimIndent()

        val quotes = QuoteParser.parse(raw)

        assertEquals(1, quotes.size)
        assertEquals("a quote", quotes[0].text)
        assertEquals("Song", quotes[0].song)
    }

    @Test
    fun `parses bare json so the asset and the download share one path`() {
        val quotes = QuoteParser.parse("""[{"text":"a quote","song":"S","artist":"A","album":"B"}]""")
        assertEquals(1, quotes.size)
    }

    @Test
    fun `skips entries with no text`() {
        val quotes = QuoteParser.parse("""[{"song":"S"},{"text":"kept","song":"S","artist":"A","album":"B"}]""")
        assertEquals(1, quotes.size)
        assertEquals("kept", quotes[0].text)
    }

    @Test
    fun `credit omits the Single album placeholder`() {
        val single = Quote("t", "Song", "Headache (PLZ)", Quote.UNKNOWN_ALBUM)
        assertEquals("Song — Headache (PLZ)", single.credit)

        val onAlbum = Quote("t", "Song", "Headache (PLZ)", "Thank You for Almost Everything")
        assertEquals("Song — Headache (PLZ) (Thank You for Almost Everything)", onAlbum.credit)
    }

    @Test
    fun `display breaks couplets onto their own lines`() {
        val couplet = Quote("first line / second line", "S", "A", "B")
        assertEquals("“first line\nsecond line”", couplet.display)
    }

    @Test
    fun `display leaves single lines intact`() {
        assertEquals("“just one line”", Quote("just one line", "S", "A", "B").display)
    }

    @Test
    fun `rejects a payload with no array`() {
        val failed = runCatching { QuoteParser.parse("window.QUOTES_DATA = undefined;") }.isFailure
        assertTrue("a malformed payload should fail loudly, not parse to nothing", failed)
    }
}
