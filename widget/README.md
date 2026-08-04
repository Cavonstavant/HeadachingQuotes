# Headaching Widget

A home-screen widget for Nothing Phone (2a) that shows the daily quote from
[HeadachingQuotes](https://cavonstavant.github.io/HeadachingQuotes/), presented the way
Nothing Fortunes presents fortunes: black card, white pixel type, one quote, a terse credit
line, and tap-to-share.

The widget lives in `widget/` inside the site's own repository. **Paths in this document are
relative to the repository root**; the shell commands are run from `widget/`.

## The parity contract

The widget must show **the same quote as the website on any given day**. The selection is:

```
hash = 0
for each UTF-16 code unit c of the UTC date "YYYY-MM-DD":
    hash = (hash << 5) - hash + c      // wraps at 32 bits
index = abs(hash) % quotes.length
```

Source of truth is `js/app.js:16-41`, mirrored in Python at
`scripts/generate_daily_svg.py:46-71`. `DailyQuote.kt` is the third implementation and
`DailyQuoteTest` pins it to values generated from the real JavaScript.

Two things to know before touching `DailyQuote.hashString`:

- It returns `Long`, not `Int`. JS `Math.abs(-2147483648)` gives `2147483648` and Python's
  `abs()` is unbounded, but Kotlin's `abs(Int.MIN_VALUE)` stays negative and would produce a
  negative index. `"e[hU_RV"` hashes to exactly `Int.MIN_VALUE` and is a regression test.
- Selection keys off **UTC**, so the quote rolls over at 01:00/02:00 French time rather than
  local midnight. That is deliberate — it is what keeps the widget in step with the site.

The modulus is the length of the list *on the device*. If that ever diverges from what the
site serves, the two disagree, which is why `QuoteRepository.refresh()` replaces the cache
wholesale and only after the payload parses to a non-empty list.

## Data flow

| When | What |
| --- | --- |
| First run / offline | `widget/app/src/main/assets/quotes.json`, a snapshot of `data/quotes.js` (777 quotes) |
| Weekly (`QuotesSyncWorker`) | GET `.../data/quotes.js`, parse, atomically replace `filesDir/quotes.json` |
| Each UTC midnight (alarm) | `AlarmManager` fires `ACTION_ROLLOVER`; the provider re-arms, then repaints |
| Every 6h (`updatePeriodMillis`) | Coarse backstop — any repaint self-corrects a missed alarm |
| Boot / app update | `BootReceiver` re-arms the alarm |
| Local midnight | `ACTION_DATE_CHANGED` on the provider |

### Why an alarm and not WorkManager

The daily repaint was originally a `PeriodicWorkRequest(1, DAYS)` and it did not work. With
no flex interval, WorkManager defaults the flex window to the **whole repeat interval**, so only
the first run was anchored to midnight by `setInitialDelay` — every run after it could fire
anywhere inside its 24-hour window. WorkManager also makes no time-of-day guarantee, and a
widget-only app the user never opens sinks into a restricted app-standby bucket where jobs are
deferred hard. Symptom: the quote changed at an arbitrary time, or not at all on a given day.

`setAndAllowWhileIdle` is the replacement. It is inexact — `dumpsys alarm` shows a one-hour
delivery window, so the quote can change up to an hour after UTC midnight — but it fires during
Doze and needs no permission. `setExactAndAllowWhileIdle` would need the `SCHEDULE_EXACT_ALARM`
grant, and `setWindow` is *not* delivered during Doze, which would be worse than the hour.
An hour of slip at 02:00 local is invisible; a day of drift was not.

Because alarms do not survive a reboot (WorkManager did), `BootReceiver` is now load-bearing
rather than optional. WorkManager is kept only for the weekly sync, which is genuinely
deferrable and network-bound — what it is actually good at.

The widget has never consumed `quotes/daily.svg`, and that file no longer exists: nothing on the
site read it either, so the artifact and its midnight-UTC cron were removed. `scripts/generate_daily_svg.py`
is kept because it is the Python mirror of the daily-index maths that this widget is pinned to;
run it by hand if you want a rendered card.

## Build

Needs JDK 17 and the Android SDK (`ANDROID_HOME`). Everything runs from the CLI, from
`widget/` — the Gradle build is rooted there, not at the repository root.

```sh
./gradlew :app:testDebugUnitTest      # parity tests — if these fail, nothing else matters
./gradlew :app:assembleDebug
./gradlew :app:installDebug           # with the 2a connected, USB debugging on
```

Note on versions: AGP is pinned to **8.13.2** and `core-ktx` to **1.17.0** on purpose.
`core-ktx` 1.18+ requires AGP 9.1+ and `compileSdk 37`, and AGP 9.x needs a newer JDK than the
17 installed here. The Gradle wrapper is pinned to 8.14.5 because Gradle 9.6+ removed an
internal API AGP 8.x still uses — a system `gradle` 9.6 cannot even configure this build, so
always use `./gradlew`.

## Verification

`./gradlew :app:connectedDebugAndroidTest` covers the parts only a real device can settle:
that `res/font` resolves inside `RemoteViews`, that `autoSizeTextType` actually shrinks text
there, that quotes are not clipped, and that the provider's own multi-size `RemoteViews`
inflate. It writes renders to `filesDir/render/`; to look at them, run the instrumentation
directly so Gradle does not uninstall the app afterwards:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.cavonstavant.headachingwidget.WidgetRenderTest \
    com.cavonstavant.headachingwidget.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out 'run-as com.cavonstavant.headachingwidget cat files/render/widget-4x2.png' > widget-4x2.png
```

On-device checks worth doing by hand:

- **Parity** — open the site in the phone's browser and confirm the text matches character for
  character.
- **Rollover** — fire the alarm's own action, which *is* sendable from adb (unlike
  `android.intent.action.DATE_CHANGED`, a protected broadcast that fails with a
  `SecurityException` — only the system can send that one):

  ```sh
  adb shell am broadcast -a com.cavonstavant.headachingwidget.ROLLOVER \
      -n com.cavonstavant.headachingwidget/.widget.QuoteWidgetProvider
  ```

  To prove the re-arm really ran rather than no-opping, wipe the record first and check it
  comes back — force-stop, `run-as … rm -f shared_prefs/headaching.xml`, broadcast, then
  `run-as … cat shared_prefs/headaching.xml` and look for `next_rollover`.
- **Scheduling** — `adb shell dumpsys alarm | grep -A6 headachingwidget.ROLLOVER` should show
  an `RTC_WAKEUP` alarm whose `origWhen` is the next UTC midnight in local time.
  `dumpsys jobscheduler | grep headachingwidget` covers the weekly sync job.
  The app also prints **"Next quote change: …"** on its own screen, so a silent scheduling
  failure is visible without adb.
- There is no `adb` way to *place* a widget — `cmd appwidget` has no shell implementation — so
  a launcher has to do it.
- **Offline** — airplane mode plus cleared app data should still render from the bundled asset.

## Known limitations

- **Home screen only.** Nothing OS reserves the lock screen and AOD for its own first-party
  and Community Widgets, so a third-party `AppWidget` cannot appear there. Fortunes gets AOD
  because it ships inside Nothing's own Community Widgets app.
- **Not Ndot.** Nothing's dot-matrix typeface is licensed for Nothing brand materials only.
  This uses [Departure Mono](https://github.com/rektdeckard/departure-mono) (SIL OFL 1.1, see
  `widget/OFL.txt`), the pixel monospace the website already uses for its UI type.
- The website sets quote bodies in Helvetica Neue and uses Departure Mono only for labels.
  The widget uses Departure Mono for the quote too — that pixel treatment is the Fortunes look.
- **Battery restrictions can still suppress the alarm.** Nothing OS is aggressive with apps it
  considers idle. If the quote stops changing, check Settings → Apps → Headaching → App battery
  usage and set it to **Unrestricted**. The 6-hour `updatePeriodMillis` backstop limits the
  damage, but it is a backstop, not a guarantee.
