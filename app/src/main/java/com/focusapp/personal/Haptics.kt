package com.focusapp.personal

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

object Haptics {
    private var last = 0L

    @Suppress("DEPRECATION")
    fun buzz(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 4000) return // never buzz in a loop
        last = now
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
}
