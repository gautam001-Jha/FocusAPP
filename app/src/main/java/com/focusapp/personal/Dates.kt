package com.focusapp.personal

import android.content.Context
import java.util.Calendar

/** Small calendar helpers shared by the schedule and the streak code. All in the phone's local time zone. */
object Dates {

    private fun cal(ms: Long): Calendar = Calendar.getInstance().also { it.timeInMillis = ms }

    fun startOfDay(ms: Long): Long {
        val c = cal(ms)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun addDays(ms: Long, days: Int): Long {
        val c = cal(ms)
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    /** ISO day number: Monday = 1 ... Sunday = 7. */
    fun isoDay(ms: Long): Int = (cal(ms).get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    /** Milliseconds elapsed since local midnight. */
    fun msIntoDay(ms: Long): Long {
        val c = cal(ms)
        return c.get(Calendar.HOUR_OF_DAY) * 3_600_000L +
            c.get(Calendar.MINUTE) * 60_000L +
            c.get(Calendar.SECOND) * 1_000L +
            c.get(Calendar.MILLISECOND)
    }

    /** Local midnight of the Monday that starts the week containing [ms]. */
    fun mondayOf(ms: Long): Long = addDays(startOfDay(ms), -(isoDay(ms) - 1))

    fun daysBetween(fromMidnight: Long, toMidnight: Long): Int =
        Math.round((toMidnight - fromMidnight) / 86_400_000.0).toInt()

    /** The date picker speaks UTC midnight; convert a local midnight into that form. */
    fun toPickerUtc(localMs: Long): Long {
        val c = cal(localMs)
        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        u.clear()
        u.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        return u.timeInMillis
    }

    /** Convert the date picker's UTC midnight back into local midnight of the same calendar date. */
    fun fromPickerUtc(utcMs: Long): Long {
        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        u.timeInMillis = utcMs
        val c = Calendar.getInstance()
        c.clear()
        c.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        return c.timeInMillis
    }

    fun hhmm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun dayName(iso: Int): String = dayNames[(iso - 1).coerceIn(0, 6)]

    /** e.g. "Mon 10 Nov" */
    fun pretty(ms: Long): String {
        val c = cal(ms)
        return "${dayName(isoDay(ms))} ${c.get(Calendar.DAY_OF_MONTH)} ${monthNames[c.get(Calendar.MONTH)]}"
    }
}

object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
}
