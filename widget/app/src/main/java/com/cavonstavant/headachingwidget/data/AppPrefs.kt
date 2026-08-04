package com.cavonstavant.headachingwidget.data

import android.content.Context
import android.content.SharedPreferences

/** The app's one preferences file, so the key names live in a single place. */
internal object AppPrefs {

    private const val NAME = "headaching"

    /** Epoch millis of the last successful quote-list download. */
    const val KEY_LAST_REFRESH = "last_refresh"

    /** Epoch millis the rollover alarm is currently armed for. */
    const val KEY_NEXT_ROLLOVER = "next_rollover"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
