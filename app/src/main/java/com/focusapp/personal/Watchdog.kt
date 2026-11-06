package com.focusapp.personal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Restarts the tracker service every minute while a session is active, in case the OS
 * (or an OEM task killer) stopped it. Uses exact, doze-friendly alarms.
 */
object Watchdog {
    private const val REQUEST = 4242

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context, delayMs: Long = 60_000L) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + delayMs
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(app))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(app))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = SessionStore(context)
        if (!store.active) return
        if (SessionManager.checkExpiry(context)) return
        TrackerService.start(context)
        Watchdog.schedule(context)
    }
}
