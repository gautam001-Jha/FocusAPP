package com.focusapp.personal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

/** Tab 4: permission checklist. Come back here at the start of every week. */
class SetupFragment : Fragment(R.layout.fragment_setup) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.autoStartButton).setOnClickListener { Perms.openAutoStart(requireContext()) }
    }

    override fun onResume() {
        super.onResume()
        val v = view ?: return
        val ctx = requireContext()
        val list = v.findViewById<LinearLayout>(R.id.permissionList)
        list.removeAllViews()

        row(list, "Usage access", Perms.usage(ctx),
            "Needed to see which app is open", Perms.usageIntent())
        row(list, "Draw over other apps", Perms.overlay(ctx),
            "Needed to show the block screen", Perms.overlayIntent(ctx))
        row(list, "Accessibility service", Perms.accessibility(ctx),
            "Reels/Shorts blocking and week guard. On Android 13+, first allow restricted settings",
            Perms.accessibilityIntent())
        row(list, "Notification access", Perms.notificationListener(ctx),
            "Hides notifications of blocked apps (same restricted-settings step applies)",
            Perms.notificationListenerIntent())
        row(list, "Device admin", Perms.deviceAdmin(ctx),
            "Prevents uninstalling FocusApp", Perms.deviceAdminIntent(ctx))
        row(list, "Ignore battery optimization", Perms.batteryExempt(ctx),
            "Keeps the timer and blocker alive in the background", Perms.batteryIntent(ctx))
    }

    private fun row(parent: LinearLayout, title: String, ok: Boolean, hint: String, intent: Intent) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.row_permission, parent, false)
        v.findViewById<TextView>(R.id.rowTitle).text = title
        val status = v.findViewById<TextView>(R.id.rowStatus)
        val button = v.findViewById<Button>(R.id.rowButton)
        if (ok) {
            status.text = "Granted"
            status.setTextColor(ctx.getColor(R.color.success))
            button.text = "Review"
        } else {
            status.text = hint
            button.text = "Grant"
        }
        button.setOnClickListener {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not open this settings page", Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Perms.appDetailsIntent(ctx))
                } catch (_: Exception) {
                }
            }
        }
        parent.addView(v)
    }
}
