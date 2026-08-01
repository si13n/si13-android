package com.si13.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.util.Locale

class WeeklyActivityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var values: List<Int> = List(7) { 0 }
        set(value) {
            field = value.takeLast(7).let { List(7 - it.size) { 0 } + it }
            contentDescription = context.getString(R.string.weekly_activity_values, field.joinToString())
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.forgetty_primary) }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.forgetty_surface_container) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.forgetty_text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = 11 * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val left = paddingLeft + 12 * density
        val right = width - paddingRight - 12 * density
        val top = paddingTop + 14 * density
        val bottom = height - paddingBottom - 28 * density
        val slot = (right - left) / 7f
        val barWidth = slot * .44f
        val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val labels = DateFormatSymbols.getInstance(locale).shortWeekdays
        values.forEachIndexed { index, value ->
            val center = left + slot * (index + .5f)
            canvas.drawRoundRect(center - barWidth / 2, top, center + barWidth / 2, bottom, 8 * density, 8 * density, trackPaint)
            val barTop = bottom - (bottom - top) * value / maximum
            canvas.drawRoundRect(center - barWidth / 2, barTop, center + barWidth / 2, bottom, 8 * density, 8 * density, barPaint)
            val date = LocalDate.now().minusDays((6 - index).toLong())
            canvas.drawText(labels[date.dayOfWeek.value % 7 + 1].take(2), center, height - 9 * density, textPaint)
        }
    }
}
