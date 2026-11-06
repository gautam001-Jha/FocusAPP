package com.focusapp.personal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator

/** Screen 3: Active focus screen (SRD 6.4). No exit buttons in Strict Mode. */
class ActiveFocusActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SessionStore
    private lateinit var countdown: TextView
    private lateinit var quote: TextView
    private lateinit var ring: CircularProgressIndicator

    private val quotes = listOf(
        "Deep work beats shallow scrolling.",
        "One focused hour is worth three distracted ones.",
        "You can check the feed later. This time is yours.",
        "Small sessions, stacked daily, change everything.",
        "Discipline is choosing what you want most over what you want now.",
        "Stay with the task. The urge will pass."
    )

    private val tick = object : Runnable {
        override fun run() {
            if (!store.active || SessionManager.checkExpiry(this@ActiveFocusActivity)) {
                Toast.makeText(this@ActiveFocusActivity, "Session complete", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ActiveFocusActivity, MainActivity::class.java))
                finish()
                return
            }
            val remaining = store.remainingMs().coerceAtLeast(0)
            val total = maxOf(store.durationMs, 1L)
            val elapsed = (total - remaining).coerceAtLeast(0)
            countdown.text = formatClock(remaining)
            ring.progress = (elapsed * 1000 / total).toInt().coerceIn(0, 1000)
            // New quote every 5 minutes.
            quote.text = quotes[((elapsed / 300_000L).toInt()) % quotes.size]
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active)
        store = SessionStore(this)

        countdown = findViewById(R.id.countdown)
        quote = findViewById(R.id.quote)
        ring = findViewById(R.id.ring)
        findViewById<TextView>(R.id.tagLabel).text =
            if (store.strict) "${store.tag}  |  Strict Mode" else store.tag

        val end = findViewById<View>(R.id.endSession)
        if (store.strict) {
            end.visibility = View.GONE
        } else {
            end.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("End session early?")
                    .setPositiveButton("End session") { _, _ ->
                        SessionManager.finish(this, "Failed")
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("Keep going", null)
                    .show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (store.strict) {
                    Toast.makeText(this@ActiveFocusActivity, "Strict Mode is on", Toast.LENGTH_SHORT).show()
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (store.active) TrackerService.start(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun formatClock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
