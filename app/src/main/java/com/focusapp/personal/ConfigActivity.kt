package com.focusapp.personal

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Screen 2: Session configuration (SRD 6.3). */
class ConfigActivity : AppCompatActivity() {

    private var selected: MutableSet<String> = HashSet()
    private lateinit var hours: NumberPicker
    private lateinit var minutes: NumberPicker
    private lateinit var tagSpinner: Spinner
    private lateinit var strictSwitch: MaterialSwitch
    private lateinit var reelsSwitch: MaterialSwitch
    private lateinit var shortsSwitch: MaterialSwitch
    private lateinit var pickBtn: MaterialButton
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        hours = findViewById(R.id.hoursPicker)
        minutes = findViewById(R.id.minutesPicker)
        tagSpinner = findViewById(R.id.tagSpinner)
        strictSwitch = findViewById(R.id.strictSwitch)
        reelsSwitch = findViewById(R.id.reelsSwitch)
        shortsSwitch = findViewById(R.id.shortsSwitch)
        pickBtn = findViewById(R.id.pickAppsBtn)
        summary = findViewById(R.id.appsSummary)

        hours.minValue = 0
        hours.maxValue = 12
        hours.value = 0
        minutes.minValue = 0
        minutes.maxValue = 59
        minutes.value = 25

        Tags.attach(this, tagSpinner)

        val store = SessionStore(this)
        reelsSwitch.isChecked = store.blockReels
        shortsSwitch.isChecked = store.blockShorts

        selected = store.blocked.toMutableSet()
        updateAppsText()
        pickBtn.setOnClickListener {
            AppPicker.show(this, selected) {
                selected = it.toMutableSet()
                updateAppsText()
            }
        }

        findViewById<android.view.View>(R.id.startSession).setOnClickListener { onStartClicked() }
    }

    private fun updateAppsText() {
        pickBtn.text = if (selected.isEmpty()) "Choose apps" else "Change apps (${selected.size})"
        val names = AppPicker.installed(this).filter { it.pkg in selected }.map { it.label }
        summary.text = if (names.isEmpty()) {
            "No apps chosen yet."
        } else {
            names.take(6).joinToString(", ") + if (names.size > 6) " and ${names.size - 6} more" else ""
        }
    }

    /** Keep the SRD's App_Directory table in sync with what is installed. */
    private fun syncDirectory() {
        val apps = AppPicker.installed(this)
        AppDb.get(this).dao().apply {
            clearApps()
            insertApps(apps.map {
                AppEntry(packageName = it.pkg, appName = it.label,
                    category = if (it.pkg in selected) "Distracting" else "Neutral")
            })
        }
    }

    private fun onStartClicked() {
        val total = hours.value * 60 + minutes.value
        if (total < 1) {
            Toast.makeText(this, "Choose a duration of at least 1 minute", Toast.LENGTH_SHORT).show()
            return
        }

        val missing = mutableListOf<String>()
        if (!Perms.usage(this)) missing.add("Usage access")
        if (!Perms.overlay(this)) missing.add("Draw over other apps")
        if (!Perms.accessibility(this)) missing.add("Accessibility service")
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Setup needed")
                .setMessage("Open the Setup tab and grant these first:\n\n" + missing.joinToString("\n") { "- $it" })
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
            return
        }

        if (selected.isEmpty() && !reelsSwitch.isChecked && !shortsSwitch.isChecked) {
            Toast.makeText(this, "Pick at least one app, or turn on Reels/Shorts blocking", Toast.LENGTH_LONG).show()
            return
        }

        if (strictSwitch.isChecked) {
            AlertDialog.Builder(this)
                .setTitle("Strict Mode")
                .setMessage("You will not be able to cancel this!")
                .setNegativeButton("Back", null)
                .setPositiveButton("Start strict session") { _, _ -> launch(total) }
                .show()
        } else {
            launch(total)
        }
    }

    private fun launch(totalMinutes: Int) {
        syncDirectory()
        SessionManager.start(
            this, totalMinutes, strictSwitch.isChecked,
            Tags.selected(tagSpinner), selected,
            reelsSwitch.isChecked, shortsSwitch.isChecked
        )
        startActivity(Intent(this, ActiveFocusActivity::class.java))
        finish()
    }
}
