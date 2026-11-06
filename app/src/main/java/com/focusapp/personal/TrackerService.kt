package com.focusapp.personal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * The Interceptor Engine (SRD 3.1): a foreground service that polls UsageEvents about every
 * 400 ms, finds the foreground app, and launches the block screen when it is blacklisted.
 */
class TrackerService : Service() {

    companion object {
        private const val TAG = "TrackerService"
        private const val CHANNEL_RUN = "focus_running_quiet"
        private const val CHANNEL_DONE = "focus_done_quiet"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, TrackerService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start tracker service", e)
            }
        }
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var store: SessionStore
    private lateinit var usm: UsageStatsManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var since = 0L
    private var firstPoll = true
    private var current: String? = null
    private var lastPkg: String? = null
    private var lastPkgStart = 0L
    private var lastBlock = 0L
    private var lastNotif = 0L
    private val homePkg by lazy { AppPicker.homePackage(this) }

    private val tick = object : Runnable {
        override fun run() {
            try {
                loop()
            } catch (e: Exception) {
                Log.e(TAG, "loop error", e)
            }
            if (store.active) handler.postDelayed(this, 400)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        thread = HandlerThread("focus-tracker").also { it.start() }
        handler = Handler(thread.looper)
        createChannels()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!store.active) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (store.active) Watchdog.schedule(this, 2_000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- loop

    private fun loop() {
        if (SessionManager.checkExpiry(this)) {
            notifyDone()
            // A scheduled slot may have started a new session right away; keep running then.
            if (!store.active) stopSelf()
            return
        }
        pollForeground()
        val pkg = current ?: return
        trackUsage(pkg)

        // The accessibility service sees the real top window; if it says "home screen", trust it.
        val onHome = homePkg != null && FocusAccessibilityService.windowPkg == homePkg
        if (pkg != packageName && pkg != homePkg && pkg in store.blocked && !onHome && Screen.inUse(this)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBlock > 700) {
                lastBlock = now
                BlockActivity.show(this, pkg)
            }
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastNotif > 30_000) {
            lastNotif = now
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun pollForeground() {
        val now = System.currentTimeMillis()
        // First poll looks back a few hours so we learn which app is already on screen.
        val from = if (firstPoll) now - 3L * 60 * 60 * 1000 else since
        firstPoll = false
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName
                // The app left the screen (for example you pressed Home): stop treating it as foreground.
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (e.packageName == current) current = null
                // Screen turned off or the lock screen appeared: nothing is in the foreground for the user.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> current = null
            }
        }
        since = now - 1000
    }

    private fun trackUsage(pkg: String) {
        if (pkg == lastPkg) return
        val now = System.currentTimeMillis()
        val prev = lastPkg
        if (prev != null) {
            val seconds = ((now - lastPkgStart) / 1000).toInt()
            if (seconds >= 1) {
                AppDb.get(this).dao().insertLog(UsageLog(packageName = prev, timestamp = lastPkgStart, durationSeconds = seconds))
            }
        }
        lastPkg = pkg
        lastPkgStart = now
    }

    // ------------------------------------------------------------ plumbing

    private fun acquireWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val ms = maxOf(store.remainingMs(), 1000L) + 60_000L
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "focusapp:tracker").also {
                it.acquire(ms)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake lock failed", e)
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("focus_running") // old channel
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        nm.deleteNotificationChannel("focus_done") // old channel made a sound
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Session complete", NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, ActiveFocusActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val minutes = maxOf(store.remainingMs(), 0L) / 60_000L
        return NotificationCompat.Builder(this, CHANNEL_RUN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus session active")
            .setContentText("${store.tag}: about $minutes min left")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(open)
            .build()
    }

    private fun notifyDone() {
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus session complete")
            .setContentText("Nice work. Your apps are unlocked.")
            .setAutoCancel(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
    }
}
