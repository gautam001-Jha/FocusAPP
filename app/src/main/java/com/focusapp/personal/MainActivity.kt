package com.focusapp.personal

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Host for the four tabs: Home, Schedule, Streak, Setup. */
class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_schedule -> ScheduleFragment()
                R.id.nav_streak -> StreakFragment()
                R.id.nav_setup -> SetupFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_home

        if (Build.VERSION.SDK_INT >= 33 && !Perms.postNotifications(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // If it is time for a scheduled slot, start it now.
        ScheduleEngine.sync(this)
        val store = SessionStore(this)
        if (store.active && !SessionManager.checkExpiry(this)) {
            TrackerService.start(this) // make sure the engine is alive
            startActivity(Intent(this, ActiveFocusActivity::class.java))
        }
    }
}
