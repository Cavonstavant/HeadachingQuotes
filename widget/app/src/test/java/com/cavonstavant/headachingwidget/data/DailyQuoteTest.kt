package com.cavonstavant.headachingwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Pins the widget to the website's quote-of-the-day selection.
 *
 * Every expected value below was produced by running the real `hashString` from
 * `js/app.js` at the repository root under node, and cross-checked against the
 * Python mirror in `scripts/generate_daily_svg.py`. If these fail, the widget is showing
 * a different quote than the site and nothing else about it matters.
 */
class DailyQuoteTest {

    /**
     * date, expected hash, expected index over the current quote list.
     *
     * The hashes depend only on the date string, so they never change. The
     * indices are `hash % QUOTE_COUNT`, so every one of them moved when the
     * dataset was pruned from 1590 to 777 records (QUOTE_AUDIT.md sections 9
     * and 10). If the list length changes again, regenerate this whole column —
     * not just the entries that happen to fail.
     */
    private val parity = listOf(
        Triple("2026-07-31", 1161844569L, 354),
        Triple("2026-08-01", 1161874267L, 526),
        Triple("2026-08-02", 1161874268L, 527),
        Triple("2026-12-25", 1162619108L, 224),
        Triple("2025-01-01", 274162049L, 707),
        Triple("1999-12-31", 45774139L, 292),
        Triple("2000-02-29", 940987427L, 23),
        Triple("2026-06-15", 1161814720L, 31),
        Triple("2024-03-08", 613282043L, 605),
        Triple("2030-11-11", 1875344827L, 160),
    )

    @Test
    fun `hash matches the website`() {
        parity.forEach { (date, expected, _) ->
            assertEquals("hash mismatch for $date", expected, DailyQuote.hashString(date))
        }
    }

    @Test
    fun `index matches the website`() {
        parity.forEach { (date, _, expected) ->
            assertEquals("index mismatch for $date", expected, DailyQuote.dailyIndex(QUOTE_COUNT, date))
        }
    }

    /**
     * The reason [DailyQuote.hashString] returns Long. This string hashes to
     * exactly Int.MIN_VALUE, where JS and Python both yield 2147483648 but
     * Kotlin's `abs(Int)` would stay negative and produce a negative index.
     */
    @Test
    fun `hash stays positive at the int32 boundary`() {
        assertEquals(2147483648L, DailyQuote.hashString("e[hU_RV"))
        assertTrue(DailyQuote.dailyIndex(QUOTE_COUNT, "e[hU_RV") >= 0)
    }

    @Test
    fun `index is always in bounds`() {
        val date = "2026-07-31"
        listOf(1, 2, 7, 776, 777, 5000).forEach { count ->
            val index = DailyQuote.dailyIndex(count, date)
            assertTrue("index $index out of bounds for count $count", index in 0 until count)
        }
    }

    /**
     * The rollover skew is intentional: the site keys off UTC, so during the
     * first hours of a French day the widget must still show the previous UTC
     * day's quote rather than jumping ahead of the site.
     */
    @Test
    fun `date comes from UTC not local time`() {
        // 00:30 on 31 July in Paris (CEST, UTC+2) is still 30 July in UTC.
        val beforeUtcMidnight = Clock.fixed(Instant.parse("2026-07-30T22:30:00Z"), ZoneOffset.UTC)
        assertEquals("2026-07-30", DailyQuote.todayStringUtc(beforeUtcMidnight))

        val afterUtcMidnight = Clock.fixed(Instant.parse("2026-07-31T00:30:00Z"), ZoneOffset.UTC)
        assertEquals("2026-07-31", DailyQuote.todayStringUtc(afterUtcMidnight))
    }

    @Test
    fun `rollover lands just after the next UTC midnight`() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T22:30:00Z"), ZoneOffset.UTC)

        val expected = Duration.ofMinutes(90).toMillis() + DailyQuote.ROLLOVER_SLACK_MILLIS
        assertEquals(expected, DailyQuote.millisUntilNextRollover(clock))
    }

    @Test
    fun `rollover is a full day plus slack when it is exactly midnight`() {
        val clock = Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC)

        val expected = Duration.ofDays(1).toMillis() + DailyQuote.ROLLOVER_SLACK_MILLIS
        assertEquals(expected, DailyQuote.millisUntilNextRollover(clock))
    }

    /**
     * The alarm is armed at `now + millisUntilNextRollover`, so that sum has to
     * land on the UTC midnight boundary regardless of when it is computed. This
     * is what the old PeriodicWorkRequest could not guarantee past its first run.
     */
    @Test
    fun `rollover always lands on the next UTC midnight boundary`() {
        val instants = listOf(
            "2026-07-30T22:30:00Z",
            "2026-07-31T00:00:01Z",
            "2026-07-31T12:00:00Z",
            "2026-07-31T23:59:59Z",
            "2026-12-31T23:00:00Z",
        )

        instants.forEach { text ->
            val now = Instant.parse(text)
            val clock = Clock.fixed(now, ZoneOffset.UTC)

            val armedAt = now.toEpochMilli() + DailyQuote.millisUntilNextRollover(clock)
            val target = Instant.ofEpochMilli(armedAt - DailyQuote.ROLLOVER_SLACK_MILLIS)

            assertEquals(
                "alarm for $text should land on a UTC midnight",
                target.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                target,
            )
            assertTrue("alarm for $text must be in the future", armedAt > now.toEpochMilli())
        }
    }

    private companion object {
        /** Length of `data/quotes.js` at the repository root when this table was generated. */
        const val QUOTE_COUNT = 777
    }
}
