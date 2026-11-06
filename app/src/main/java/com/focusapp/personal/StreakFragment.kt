package com.focusapp.personal

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Tab 3: LeetCode-style streak with a 17 week heatmap and this week's days. */
class StreakFragment : Fragment(R.layout.fragment_streak) {

    override fun onResume() {
        super.onResume()
        val v = view ?: return
        render(v)
    }

    private fun render(v: View) {
        val ctx = requireContext()
        val r = Streaks.compute(ctx)
        val now = ScheduleStore(ctx).now()

        v.findViewById<TextView>(R.id.currentStreak).text = r.current.toString()
        v.findViewById<TextView>(R.id.longestStreak).text = r.longest.toString()
        v.findViewById<TextView>(R.id.totalDays).text = r.activeDays.toString()
        v.findViewById<HeatmapView>(R.id.heatmap).setData(r.perDay, now)

        val today = Dates.startOfDay(now)
        val monday = Dates.mondayOf(now)
        val row = v.findViewById<LinearLayout>(R.id.weekRow)
        row.removeAllViews()
        val size = Ui.dp(ctx, 36)
        val margin = Ui.dp(ctx, 4)
        for (i in 0 until 7) {
            val day = Dates.addDays(monday, i)
            val done = (r.perDay[day] ?: 0L) >= Streaks.MIN_DAY_MS
            val cell = TextView(ctx).apply {
                text = Dates.dayName(i + 1).take(1)
                gravity = Gravity.CENTER
                textSize = 13f
                setBackgroundResource(if (done) R.drawable.bg_day_done else R.drawable.bg_day)
                setTextColor(ctx.getColor(if (done) R.color.white else R.color.subtext))
                if (day == today) setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.setMargins(margin, 0, margin, 0) }
            }
            row.addView(cell)
        }

        val todayMs = r.perDay[today] ?: 0L
        v.findViewById<TextView>(R.id.todayProgress).text =
            if (todayMs >= Streaks.MIN_DAY_MS) {
                "Today: ${Stats.format(todayMs)}. Streak secured for today."
            } else {
                "Today: ${Stats.format(todayMs)}. Complete 10 minutes of focus to keep your streak."
            }
    }
}
