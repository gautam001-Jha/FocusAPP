package com.focusapp.personal

import android.content.Context

/**
 * LeetCode-style streak. A day counts when you completed at least [MIN_DAY_MS] of focus sessions
 * on that calendar day. Sessions that cross midnight are split between the two days.
 */
object Streaks {

    const val MIN_DAY_MS = 10 * 60_000L

    data class Result(
        val current: Int,
        val longest: Int,
        val activeDays: Int,
        /** local midnight -> focused milliseconds that day */
        val perDay: Map<Long, Long>
    )

    fun level(ms: Long): Int = when {
        ms <= 0L -> 0
        ms < MIN_DAY_MS -> 1
        ms < 30 * 60_000L -> 2
        ms < 60 * 60_000L -> 3
        else -> 4
    }

    fun compute(ctx: Context): Result {
        val perDay = HashMap<Long, Long>()
        for (s in AppDb.get(ctx).dao().completedSessions()) {
            var cursor = s.startTime
            val end = s.endTime
            while (cursor < end) {
                val dayStart = Dates.startOfDay(cursor)
                val nextDay = Dates.addDays(dayStart, 1)
                val segEnd = minOf(end, nextDay)
                perDay[dayStart] = (perDay[dayStart] ?: 0L) + (segEnd - cursor)
                cursor = segEnd
            }
        }

        val good = perDay.filter { it.value >= MIN_DAY_MS }.keys.sorted()

        var longest = 0
        var run = 0
        var prev = 0L
        for (d in good) {
            run = if (run > 0 && Dates.addDays(prev, 1) == d) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }

        val goodSet = good.toHashSet()
        val now = ScheduleStore(ctx).now()
        val today = Dates.startOfDay(now)
        // If today has not reached the minimum yet, the streak is still alive from yesterday.
        var day = if (today in goodSet) today else Dates.addDays(today, -1)
        var current = 0
        while (day in goodSet) {
            current++
            day = Dates.addDays(day, -1)
        }

        return Result(current, longest, good.size, perDay)
    }
}
