package com.focusapp.personal

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Fast, process-safe snapshot of the current focus session (SharedPreferences).
 * The enforcement loops read this every few hundred milliseconds, so it must be cheap.
 *
 * Clock-tamper resistance (SRD 7.2): while the phone has not rebooted, the remaining time is
 * measured with SystemClock.elapsedRealtime(), which ignores changes to the date/time setting.
 * After a reboot the monotonic clock resets, so we fall back to the stored wall-clock end time.
 */
class SessionStore(context: Context) {
    private val ctx = context.applicationContext
    private val p = ctx.getSharedPreferences("focus_state", Context.MODE_PRIVATE)

    val active: Boolean get() = p.getBoolean("active", false)
    val strict: Boolean get() = p.getBoolean("strict", false)
    val tag: String get() = p.getString("tag", "Focus") ?: "Focus"
    val sessionId: Long get() = p.getLong("sessionId", -1L)
    val startWall: Long get() = p.getLong("startWall", 0L)
    val durationMs: Long get() = p.getLong("durationMs", 0L)
    val blocked: Set<String> get() = p.getStringSet("blocked", emptySet()) ?: emptySet()
    val blockReels: Boolean get() = p.getBoolean("reels", true)
    val blockShorts: Boolean get() = p.getBoolean("shorts", true)

    private fun bootCount(): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun remainingMs(): Long {
        if (!active) return 0L
        return if (p.getInt("bootCount", -2) == bootCount()) {
            p.getLong("endElapsed", 0L) - SystemClock.elapsedRealtime()
        } else {
            p.getLong("endWall", 0L) - System.currentTimeMillis()
        }
    }

    fun begin(
        sessionId: Long, durationMs: Long, strict: Boolean, tag: String,
        blocked: Set<String>, reels: Boolean, shorts: Boolean
    ) {
        val wall = System.currentTimeMillis()
        p.edit()
            .putBoolean("active", true)
            .putBoolean("strict", strict)
            .putString("tag", tag)
            .putLong("sessionId", sessionId)
            .putLong("startWall", wall)
            .putLong("durationMs", durationMs)
            .putLong("endWall", wall + durationMs)
            .putLong("endElapsed", SystemClock.elapsedRealtime() + durationMs)
            .putInt("bootCount", bootCount())
            .putStringSet("blocked", HashSet(blocked))
            .putBoolean("reels", reels)
            .putBoolean("shorts", shorts)
            .commit()
    }

    /** Called after a reboot: re-anchor the monotonic clock and optionally add a penalty (SRD 7.3). */
    fun rebaseAfterBoot(penaltyMs: Long) {
        val remaining = (p.getLong("endWall", 0L) - System.currentTimeMillis()) + penaltyMs
        p.edit()
            .putLong("endElapsed", SystemClock.elapsedRealtime() + remaining)
            .putLong("endWall", System.currentTimeMillis() + remaining)
            .putLong("durationMs", p.getLong("durationMs", 0L) + penaltyMs)
            .putInt("bootCount", bootCount())
            .commit()
    }

    fun end() {
        p.edit().putBoolean("active", false).commit()
    }
}
