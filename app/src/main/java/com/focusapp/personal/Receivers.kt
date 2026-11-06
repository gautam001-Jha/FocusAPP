package com.focusapp.personal

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Device Admin (SRD 4.4): while this admin is active, Android refuses to uninstall the app.
 */
class FocusAdminReceiver : DeviceAdminReceiver()

/**
 * Reboot survival (SRD 7.1) and abnormal-exit penalty (SRD 7.3).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = SessionStore(context)
        if (store.active) {
            // Rebooting during a strict session costs 5 extra minutes.
            store.rebaseAfterBoot(if (store.strict) 5 * 60_000L else 0L)
            if (!SessionManager.checkExpiry(context)) {
                TrackerService.start(context)
                Watchdog.schedule(context)
            }
        }
        // Resume the weekly schedule: start the current slot if any and re-arm the next alarm.
        ScheduleEngine.sync(context)
    }
}

/**
 * Notification filter (SRD 4.6): dismisses notifications from blocked apps during a session.
 */
class BlockedNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = SessionStore(this)
        if (store.active && sbn.packageName in store.blocked) {
            cancelNotification(sbn.key)
        }
    }
}
