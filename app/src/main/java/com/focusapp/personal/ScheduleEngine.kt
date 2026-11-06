package com.focusapp.personal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Turns the saved weekly plan into real strict focus sessions.
 *
 * - [sync] starts a strict session if "now" is inside a slot of a running locked week.
 * - [rearm] sets one exact alarm for the start of the next slot.
 * Both are cheap and safe to call as often as needed (app open, boot, alarm, accessibility events).
 */
object ScheduleEngine {

    private const val REQUEST = 4343

    data class Upcoming(val slot: ScheduleSlot, val startAt: Long)

    fun slots(ctx: Context): List<ScheduleSlot> = AppDb.get(ctx).dao().slots()

    /** The slot that is happening right now (trusted clock), with the milliseconds left in it. */
    fun currentSlot(ctx: Context, all: List<ScheduleSlot> = slots(ctx)): Pair<ScheduleSlot, Long>? {
        val now = ScheduleStore(ctx).now()
        val day = Dates.isoDay(now)
        val into = Dates.msIntoDay(now)
        for (s in all) {
            if (s.dayOfWeek != day) continue
            if (into >= s.startMin * 60_000L && into < s.endMin * 60_000L) {
                return s to (s.endMin * 60_000L - into)
            }
        }
        return null
    }

    /** The next slot that starts after now, within the locked period. */
    fun nextSlot(ctx: Context, all: List<ScheduleSlot> = slots(ctx)): Upcoming? {
        val ss = ScheduleStore(ctx)
        if (ss.lockEnd == 0L) return null
        val now = ss.now()
        val today = Dates.startOfDay(now)
        var best: Upcoming? = null
        for (offset in 0..(ScheduleStore.MAX_LOCK_DAYS + 7)) {
            val dayStart = Dates.addDays(today, offset)
            if (dayStart >= ss.lockEnd) break
            val iso = Dates.isoDay(dayStart)
            for (s in all) {
                if (s.dayOfWeek != iso) continue
                val startAt = dayStart + s.startMin * 60_000L
                if (startAt <= now || startAt < ss.lockStart || startAt >= ss.lockEnd) continue
                if (best == null || startAt < best.startAt) best = Upcoming(s, startAt)
            }
            if (best != null) break
        }
        return best
    }

    /** Starts the strict session for the current slot if one should be running. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val ss = ScheduleStore(app)
            if (ss.running() && !SessionStore(app).active) {
                val cur = currentSlot(app)
                if (cur != null) {
                    val (slot, left) = cur
                    if (left >= 5_000L) {
                        SessionManager.startForMs(
                            app, left, true, slot.tag, ss.blocked, ss.blockReels, ss.blockShorts
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        rearm(app)
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST, Intent(ctx, ScheduleReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Sets (or clears) the alarm for the start of the next slot. */
    fun rearm(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val ss = ScheduleStore(app)
        val next = nextSlot(app)
        if (next == null || !ss.frozen()) {
            am.cancel(pending(app))
            return
        }
        // The alarm clock runs on wall time, so convert the trusted distance into a wall-clock target.
        val at = System.currentTimeMillis() + (next.startAt - ss.now())
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app))
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app))
        }
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScheduleEngine.sync(context)
    }
}
