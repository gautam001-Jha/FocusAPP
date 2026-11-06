package com.focusapp.personal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** GitHub / LeetCode style grid: one column per week, rows Monday to Sunday, newest week on the right. */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val weeks = 17
    private var perDay: Map<Long, Long> = emptyMap()
    private var thisMonday = 0L
    private var today = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gap = Ui.dp(context, 3).toFloat()
    private val colors = intArrayOf(
        context.getColor(R.color.heat_0),
        context.getColor(R.color.heat_1),
        context.getColor(R.color.heat_2),
        context.getColor(R.color.heat_3),
        context.getColor(R.color.heat_4)
    )

    fun setData(perDay: Map<Long, Long>, now: Long) {
        this.perDay = perDay
        this.today = Dates.startOfDay(now)
        this.thisMonday = Dates.mondayOf(now)
        invalidate()
    }

    private fun cell(width: Int): Float = (width - gap * (weeks - 1)) / weeks

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val c = cell(w)
        val h = (c * 7 + gap * 6).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (thisMonday == 0L) return
        val c = cell(width)
        val radius = c / 4f
        for (col in 0 until weeks) {
            val monday = Dates.addDays(thisMonday, -(weeks - 1 - col) * 7)
            for (row in 0 until 7) {
                val day = Dates.addDays(monday, row)
                if (day > today) continue // future days stay empty
                paint.color = colors[Streaks.level(perDay[day] ?: 0L)]
                val left = col * (c + gap)
                val top = row * (c + gap)
                rect.set(left, top, left + c, top + c)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }
}
