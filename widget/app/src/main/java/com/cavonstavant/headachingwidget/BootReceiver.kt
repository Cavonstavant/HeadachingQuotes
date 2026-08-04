package com.cavonstavant.headachingwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cavonstavant.headachingwidget.widget.QuoteWidgetProvider
import com.cavonstavant.headachingwidget.work.WidgetScheduler

/**
 * Re-arms the rollover alarm after a reboot or an app update.
 *
 * This is not optional the way it would be with WorkManager: alarms are dropped
 * on reboot and cleared when the package is replaced, so without this the widget
 * would quietly stop updating until the app was next opened.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                WidgetScheduler.scheduleAll(context)
                // The date may well have moved on while the device was off.
                QuoteWidgetProvider.refreshAll(context)
            }
        }
    }
}
