package com.focusapp.personal

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * State of the schedule lock (a range of calendar days chosen by the user).
 *
 * "Frozen" means: a week has been locked and its end time has not been reached yet.
 * While frozen the plan cannot be edited and the accessibility service keeps you out of the
 * Settings / installer pages that mention this app.
 *
 * Time is measured with a "trusted clock": wall time at the moment of locking plus the monotonic
 * SystemClock.elapsedRealtime() since then, so changing the phone's date does not shorten the lock.
 * After a reboot the monotonic clock restarts, so the anchor is refreshed from the wall clock.
 */
class ScheduleStore(context: Context) {

    companion object {
        /**
         * Safety hatch for the first versions. When true, the Schedule tab shows "Emergency unlock",
         * which needs a 10 minute wait and a typed confirmation, and can only be used between slots.
         * Set to false for a truly unbreakable week.
         */
        const val ALLOW_EMERGENCY_UNLOCK = true
        /** Longest lock you can pick on the calendar. */
        const val MAX_LOCK_DAYS = 31
        const val UNLOCK_WAIT_MS = 10 * 60_000L
        const val UNLOCK_WINDOW_MS = 60 * 60_000L
    }

    private val ctx = context.applicationContext
    private val p = ctx.getSharedPreferences("schedule_state", Context.MODE_PRIVATE)

    private fun bootCount(): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

    // ---------------------------------------------------------- trusted clock

    fun now(): Long {
        val boot = bootCount()
        if (p.getInt("anchorBoot", -2) != boot || !p.contains("anchorWall")) {
            p.edit()
                .putLong("anchorWall", System.currentTimeMillis())
                .putLong("anchorElapsed", SystemClock.elapsedRealtime())
                .putInt("anchorBoot", boot)
                .commit()
        }
        return p.getLong("anchorWall", 0L) + (SystemClock.elapsedRealtime() - p.getLong("anchorElapsed", 0L))
    }

    // ---------------------------------------------------------- lock state

    val lockStart: Long get() = p.getLong("lockStart", 0L)
    val lockEnd: Long get() = p.getLong("lockEnd", 0L)

    fun frozen(): Boolean = lockEnd > 0L && now() < lockEnd

    /** True while the locked period has actually begun (a lock that starts on a later date begins that day). */
    fun running(): Boolean {
        val n = now()
        return lockEnd > 0L && n >= lockStart && n < lockEnd
    }

    /**
     * Locks the calendar days from [startDay] to [endDay] inclusive (both local midnights).
     * If the first day is today, the lock starts immediately.
     */
    fun lockRange(startDay: Long, endDay: Long) {
        // Re-anchor on a fresh wall-clock reading at the moment of locking.
        p.edit()
            .putLong("anchorWall", System.currentTimeMillis())
            .putLong("anchorElapsed", SystemClock.elapsedRealtime())
            .putInt("anchorBoot", bootCount())
            .commit()
        val n = now()
        p.edit()
            .putLong("lockStart", maxOf(n, startDay))
            .putLong("lockEnd", Dates.addDays(endDay, 1))
            .putLong("unlockRequestAt", 0L)
            .commit()
    }

    // ---------------------------------------------------------- what to block during slots

    var blocked: Set<String>
        get() = p.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) {
            p.edit().putStringSet("blocked", HashSet(v)).commit()
        }

    var blockReels: Boolean
        get() = p.getBoolean("reels", true)
        set(v) {
            p.edit().putBoolean("reels", v).commit()
        }

    var blockShorts: Boolean
        get() = p.getBoolean("shorts", true)
        set(v) {
            p.edit().putBoolean("shorts", v).commit()
        }

    // ---------------------------------------------------------- emergency unlock

    /** 0 = no request, otherwise the trusted time when the wait started. Old requests expire. */
    fun unlockRequestAt(): Long {
        val at = p.getLong("unlockRequestAt", 0L)
        if (at != 0L && now() - at > UNLOCK_WINDOW_MS) {
            p.edit().putLong("unlockRequestAt", 0L).commit()
            return 0L
        }
        return at
    }

    fun requestUnlock() {
        p.edit().putLong("unlockRequestAt", now()).commit()
    }

    fun unlockWaitLeftMs(): Long {
        val at = unlockRequestAt()
        if (at == 0L) return UNLOCK_WAIT_MS
        return (UNLOCK_WAIT_MS - (now() - at)).coerceAtLeast(0L)
    }

    fun unlockNow() {
        p.edit().putLong("lockStart", 0L).putLong("lockEnd", 0L).putLong("unlockRequestAt", 0L).commit()
    }
}
