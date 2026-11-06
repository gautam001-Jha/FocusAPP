package com.focusapp.personal

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

object Stats {

    data class AppTime(val pkg: String, val label: String, val ms: Long)

    fun startOfDay(offsetDays: Int = 0): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.timeInMillis
    }

    fun perApp(ctx: Context, from: Long, to: Long): List<AppTime> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        val skip = setOfNotNull(ctx.packageName, "com.android.systemui", home)

        return usm.queryAndAggregateUsageStats(from, to).values
            .filter { it.totalTimeInForeground > 0 && it.packageName !in skip }
            .map { AppTime(it.packageName, labelOf(pm, it.packageName), it.totalTimeInForeground) }
            .sortedByDescending { it.ms }
    }

    fun total(list: List<AppTime>): Long = list.sumOf { it.ms }

    private fun labelOf(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    /** Total focused time today from completed and running sessions. */
    fun focusedToday(ctx: Context): Long {
        val now = System.currentTimeMillis()
        return AppDb.get(ctx).dao().sessionsSince(startOfDay()).sumOf {
            if (it.status == "Active") now - it.startTime else (it.endTime - it.startTime).coerceAtLeast(0)
        }
    }
}
