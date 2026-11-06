package com.focusapp.personal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Screen 4: Block overlay (SRD 6.5). Opaque, full screen, one button: Go Home. */
class BlockActivity : AppCompatActivity() {

    companion object {
        @Volatile private var resumed = false
        @Volatile private var lastShow = 0L

        fun show(context: Context, pkg: String?) {
            // Never pop up over the lock screen or a dark screen.
            if (!Screen.inUse(context)) return
            val t = android.os.SystemClock.elapsedRealtime()
            if (resumed || t - lastShow < 1500) return
            lastShow = t
            val intent = Intent(context.applicationContext, BlockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                .putExtra("pkg", pkg)
            try {
                context.applicationContext.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        val store = SessionStore(this)
        if (!store.active) {
            finish()
            return
        }

        val minutes = (store.remainingMs().coerceAtLeast(0) / 60_000L) + 1
        findViewById<TextView>(R.id.remainingText).text = "About $minutes min of focus left"

        findViewById<android.view.View>(R.id.goHome).setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
