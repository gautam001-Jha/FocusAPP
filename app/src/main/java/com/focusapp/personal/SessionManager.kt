package com.focusapp.personal

import android.content.Context
import android.content.Intent

object SessionManager {

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    fun start(
        context: Context, minutes: Int, strict: Boolean, tag: String,
        blocked: Set<String>, reels: Boolean, shorts: Boolean
    ) = startForMs(context, minutes * 60_000L, strict, tag, blocked, reels, shorts)

    fun startForMs(
        context: Context, duration: Long, strict: Boolean, tag: String,
        blockedIn: Set<String>, reels: Boolean, shorts: Boolean
    ) {
        val app = context.applicationContext
        // Never block the phone's home screen, otherwise "Go Home" would trigger the block again.
        val blocked = blockedIn - setOfNotNull(AppPicker.homePackage(app))
        val dao = AppDb.get(app).dao()
        val now = System.currentTimeMillis()

        val id = dao.insertSession(
            FocusSession(
                startTime = now, endTime = now + duration,
                isStrictMode = strict, status = "Active", tag = tag
            )
        )

        val rules = blocked.map { BlacklistRule(packageName = it, blockType = "FullApp") }.toMutableList()
        if (reels) rules.add(BlacklistRule(packageName = INSTAGRAM, blockType = "ReelsOnly"))
        if (shorts) rules.add(BlacklistRule(packageName = YOUTUBE, blockType = "ShortsOnly"))
        dao.clearRules()
        dao.insertRules(rules)

        SessionStore(app).begin(id, duration, strict, tag, blocked, reels, shorts)
        TrackerService.start(app)
        Watchdog.schedule(app)
    }

    /** status: "Completed" or "Failed". */
    fun finish(context: Context, status: String) {
        val app = context.applicationContext
        val store = SessionStore(app)
        if (!store.active) return
        AppDb.get(app).dao().finishSession(store.sessionId, status, System.currentTimeMillis())
        store.end()
        Watchdog.cancel(app)
        app.stopService(Intent(app, TrackerService::class.java))
        // A scheduled slot may be waiting (back to back slots, or a manual session ended early).
        ScheduleEngine.sync(app)
    }

    /** Ends the session if its time is up. Returns true when it did. */
    fun checkExpiry(context: Context): Boolean {
        val store = SessionStore(context)
        if (store.active && store.remainingMs() <= 0) {
            finish(context, "Completed")
            return true
        }
        return false
    }
}
