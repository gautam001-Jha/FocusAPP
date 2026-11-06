package com.focusapp.personal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Tab 1: start a session, today's focus, streak badge, schedule summary and screen time. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.startButton).setOnClickListener {
            startActivity(Intent(requireContext(), ConfigActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val v = view ?: return
        renderHeader(v)
        renderSchedule(v)
        renderStats(v)
    }

    private fun renderHeader(v: View) {
        val ctx = requireContext()
        v.findViewById<TextView>(R.id.focusToday).text =
            "Focused today: ${Stats.format(Stats.focusedToday(ctx))}"
        val n = Streaks.compute(ctx).current
        v.findViewById<TextView>(R.id.homeStreak).text = if (n == 1) "1 day streak" else "$n day streak"
    }

    private fun renderSchedule(v: View) {
        val ctx = requireContext()
        val ss = ScheduleStore(ctx)
        val title = v.findViewById<TextView>(R.id.homeScheduleTitle)
        val sub = v.findViewById<TextView>(R.id.homeScheduleSub)
        if (ss.frozen()) {
            title.text = "Schedule locked"
            val next = ScheduleEngine.nextSlot(ctx)
            val until = "Locked through ${Dates.pretty(Dates.addDays(ss.lockEnd, -1))}"
            sub.text = if (next != null) {
                "Next: ${Dates.pretty(next.startAt)}, ${Dates.hhmm(next.slot.startMin)} to " +
                    "${Dates.hhmm(next.slot.endMin)} (${next.slot.tag})\n$until"
            } else {
                "No more slots in the locked period.\n$until"
            }
        } else if (ss.lockEnd > 0L) {
            title.text = "Fresh start"
            sub.text = "Last lock has ended. Check Setup, review your plan and lock again."
        } else {
            title.text = "Weekly schedule"
            sub.text = "No week locked yet. Plan your week in the Schedule tab."
        }
    }

    private fun renderStats(v: View) {
        val ctx = requireContext()
        val todayLabel = v.findViewById<TextView>(R.id.todayLabel)
        val yesterdayLabel = v.findViewById<TextView>(R.id.yesterdayLabel)
        val todayBar = v.findViewById<ProgressBar>(R.id.todayBar)
        val yesterdayBar = v.findViewById<ProgressBar>(R.id.yesterdayBar)
        val topApps = v.findViewById<LinearLayout>(R.id.topApps)
        topApps.removeAllViews()

        if (!Perms.usage(ctx)) {
            todayLabel.text = "Today: grant Usage access in Setup to see screen time"
            yesterdayLabel.text = "Yesterday: n/a"
            todayBar.progress = 0
            yesterdayBar.progress = 0
            return
        }

        val now = System.currentTimeMillis()
        val today = Stats.perApp(ctx, Stats.startOfDay(), now)
        val yesterday = Stats.perApp(ctx, Stats.startOfDay(-1), Stats.startOfDay())
        val todayMs = Stats.total(today)
        val yesterdayMs = Stats.total(yesterday)
        val scale = maxOf(todayMs, yesterdayMs, 1L)

        todayLabel.text = "Today: ${Stats.format(todayMs)}"
        yesterdayLabel.text = "Yesterday: ${Stats.format(yesterdayMs)}"
        todayBar.progress = (todayMs * 1000 / scale).toInt()
        yesterdayBar.progress = (yesterdayMs * 1000 / scale).toInt()

        val top = today.take(5)
        val topMax = maxOf(top.firstOrNull()?.ms ?: 1L, 1L)
        for (app in top) {
            val row = layoutInflater.inflate(R.layout.row_stat, topApps, false)
            row.findViewById<TextView>(R.id.statName).text = app.label
            row.findViewById<TextView>(R.id.statTime).text = Stats.format(app.ms)
            row.findViewById<ProgressBar>(R.id.statBar).progress = (app.ms * 1000 / topMax).toInt()
            topApps.addView(row)
        }
        if (top.isEmpty()) {
            topApps.addView(TextView(ctx).apply {
                text = "No usage recorded yet"
                setTextColor(ctx.getColor(R.color.subtext))
            })
        }
    }
}
