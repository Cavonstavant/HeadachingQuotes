package com.cavonstavant.headachingwidget.data

import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Picks the quote of the day exactly the way the website does.
 *
 * Source of truth is `js/app.js:16-41` at the repository root, already mirrored
 * in Python at `scripts/generate_daily_svg.py:46-71`. This is the
 * third implementation of the same three lines, and all three have to agree —
 * otherwise the widget and the site show different quotes on the same day.
 * `DailyQuoteTest` pins that agreement with values generated from the real JS.
 */
object DailyQuote {

    /**
     * The site's hash: `hash = (hash << 5) - hash + charCode`, with `hash |= 0`
     * applied after every step. Kotlin needs no equivalent of that `|= 0` —
     * `Int` arithmetic already wraps at 32 bits — and `Char.code` is the same
     * UTF-16 code unit `charCodeAt` returns.
     *
     * Returns `Long` on purpose. JS `Math.abs(-2147483648)` gives 2147483648 and
     * Python's `abs()` is unbounded, but Kotlin's `abs(Int.MIN_VALUE)` is still
     * `Int.MIN_VALUE` — negative, which would yield a negative index and crash.
     * Widening before `abs` preserves parity and keeps the modulus non-negative.
     */
    fun hashString(value: String): Long {
        var hash = 0
        for (char in value) {
            hash = (hash shl 5) - hash + char.code
        }
        return abs(hash.toLong())
    }

    /**
     * Today's date as `YYYY-MM-DD` in **UTC**, matching `todayString()` in
     * app.js. Deliberately not the local date: keying off UTC is what keeps the
     * widget in step with the site, at the cost of rolling over at 01:00/02:00
     * local time rather than midnight.
     */
    fun todayStringUtc(clock: Clock = Clock.systemUTC()): String =
        clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString()

    /**
     * Index of the day's quote. [count] is the length of the list actually on
     * this device — if that ever diverges from the list the site is serving, the
     * two disagree, which is why the cache is only ever replaced wholesale.
     */
    fun dailyIndex(count: Int, date: String): Int {
        require(count > 0) { "cannot pick a daily quote from an empty list" }
        return (hashString(date) % count).toInt()
    }

    /**
     * How long until the site's quote next changes — i.e. until the next UTC
     * midnight — plus a little slack so a scheduled refresh never lands a hair
     * early and repaints yesterday's quote.
     */
    fun millisUntilNextRollover(clock: Clock = Clock.systemUTC()): Long {
        val now = clock.instant()
        val nextMidnight = now.atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        return Duration.between(now, nextMidnight).toMillis() + ROLLOVER_SLACK_MILLIS
    }

    const val ROLLOVER_SLACK_MILLIS = 30_000L
}
