package com.focusapp.personal

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object Screen {
    /** True only when the screen is on and the phone is unlocked, i.e. the user is actually using it. */
    fun inUse(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }
}
