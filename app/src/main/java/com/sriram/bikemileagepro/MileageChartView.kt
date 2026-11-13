package com.sriram.bikemileagepro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

class MileageChartView(context: Context) : View(context) {
    var values: List<Pair<String, Double>> = emptyList()
        set(value) { field = value; invalidate() }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5EAF0.toInt(); strokeWidth = 1.5f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF176B87.toInt(); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF176B87.toInt(); style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt(); textSize = 27f }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22176B87; style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 64f; val right = width - 28f; val top = 28f; val bottom = height - 58f
        if (right <= left || bottom <= top) return
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        if (values.isEmpty()) {
            canvas.drawText("Add two full-tank entries to see mileage", left, height / 2f, textPaint)
            return
        }
        val maxValue = max(10.0, values.maxOf { it.second } * 1.2).toFloat()
        val points = values.mapIndexed { index, pair ->
            val x = if (values.size == 1) (left + right) / 2f else left + (right - left) * index / (values.size - 1).toFloat()
            val y = bottom - (pair.second.toFloat() / maxValue) * (bottom - top)
            x to y
        }
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second) }
        }
        val area = Path(path).apply { lineTo(points.last().first, bottom); lineTo(points.first().first, bottom); close() }
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(path, linePaint)
        points.forEach { canvas.drawCircle(it.first, it.second, 7f, dotPaint) }
        canvas.drawText(String.format(java.util.Locale.US, "%.0f", maxValue), 8f, top + 8f, textPaint)
        canvas.drawText("0", 28f, bottom, textPaint)
        values.takeLast(4).forEachIndexed { i, pair ->
            val x = if (values.size <= 4) points[i].first else left + (right - left) * i / 3f
            canvas.drawText(pair.first.takeLast(5), x - 24f, height - 18f, textPaint)
        }
    }
}
