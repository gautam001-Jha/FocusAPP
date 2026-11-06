package com.focusapp.personal

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Deep blocking (SRD 3.3, 3.2, 7.4):
 *  - second line of defence for fully blocked apps,
 *  - Reels (Instagram) and Shorts (YouTube) micro-blocking,
 *  - strict-mode guard that keeps you out of Settings / Play Store pages for this app.
 *
 * It only inspects screen content while a session is active, and tree scans are limited
 * to the few packages that matter and throttled.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        /** Package of the last real app window that came to the front (ignores the status bar and keyboards). */
        @Volatile
        var windowPkg: String? = null
    }

    private val store by lazy { SessionStore(this) }
    private var lastScan = 0L
    private var lastAction = 0L
    private var lastBlockShown = 0L
    private var backStreak = 0
    private var lastSync = 0L
    private val sched by lazy { ScheduleStore(this) }
    private val homePkg by lazy { AppPicker.homePackage(this) }

    private val guardPackages = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (store.active) TrackerService.start(this)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString()
            if (p != null && p != "com.android.systemui" && !p.contains("inputmethod")) windowPkg = p
        }
        if (!store.active) {
            idleEvent(event)
            return
        }
        if (store.remainingMs() <= 0) {
            SessionManager.checkExpiry(this)
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        when {
            pkg != homePkg && pkg in store.blocked -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) showBlock(pkg)
            }
            pkg == SessionManager.YOUTUBE && store.blockShorts ->
                scan(pkg) { root -> if (isShortsScreen(root)) onMicroHit(pkg, "Shorts are blocked!") }
            pkg == SessionManager.INSTAGRAM && store.blockReels ->
                scan(pkg) { root -> if (isReelsScreen(root)) onMicroHit(pkg, "Reels are blocked!") }
            (store.strict || sched.frozen()) && pkg in guardPackages ->
                scan(pkg) { root -> guardScreen(root) }
        }
    }

    /** No session running: start a scheduled slot if it is time, and guard a locked week. */
    private fun idleEvent(event: AccessibilityEvent) {
        val nowUp = SystemClock.uptimeMillis()
        if (nowUp - lastSync > 15_000) {
            lastSync = nowUp
            ScheduleEngine.sync(this)
            if (store.active) return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName && pkg in guardPackages && sched.frozen()) {
            scan(pkg) { root -> guardScreen(root) }
        }
    }

    // ------------------------------------------------------------- helpers

    @Suppress("DEPRECATION")
    private fun scan(pkg: String, block: (AccessibilityNodeInfo) -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 250) return
        lastScan = now
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() == pkg) block(root)
        } finally {
            root.recycle()
        }
    }

    /** Depth-first walk with a node budget so a huge tree can never lag the phone. */
    private fun walk(
        node: AccessibilityNodeInfo?, budget: IntArray,
        visit: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (node == null || budget[0] <= 0) return false
        budget[0]--
        if (visit(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (walk(child, budget, visit)) return true
        }
        return false
    }

    private fun labelOf(node: AccessibilityNodeInfo): String =
        listOfNotNull(node.text, node.contentDescription).joinToString(" ")

    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean =
        walk(root, intArrayOf(600)) { n ->
            val id = n.viewIdResourceName ?: ""
            id.contains("reel_player") ||
                id.contains("shorts_player") ||
                id.contains("reel_recycler") ||
                (n.isSelected && labelOf(n).startsWith("Shorts", ignoreCase = true))
        }

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean =
        walk(root, intArrayOf(600)) { n ->
            val id = n.viewIdResourceName ?: ""
            (id.endsWith(":id/clips_tab") && n.isSelected) ||
                id.contains("clips_viewer") ||
                (id.contains("tab") && n.isSelected && labelOf(n).startsWith("Reels", ignoreCase = true))
        }

    /** Strict mode: kick the user out of pages that expose this app's uninstall / admin / accessibility toggles. */
    private fun guardScreen(root: AccessibilityNodeInfo) {
        val text = StringBuilder()
        walk(root, intArrayOf(500)) { n ->
            n.text?.let { text.append(it).append('|') }
            n.contentDescription?.let { text.append(it).append('|') }
            false
        }
        val t = text.toString().lowercase()
        if (t.contains("focusapp")) {
            val now = SystemClock.uptimeMillis()
            if (now - lastAction < 900) return
            lastAction = now
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "Strict Mode is on", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onMicroHit(pkg: String, message: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAction < 900) return
        backStreak = if (now - lastAction < 4000) backStreak + 1 else 1
        lastAction = now
        Haptics.buzz(this)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (backStreak >= 3) {
            backStreak = 0
            showBlock(pkg)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun showBlock(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockShown < 700) return
        lastBlockShown = now
        BlockActivity.show(this, pkg)
    }
}
