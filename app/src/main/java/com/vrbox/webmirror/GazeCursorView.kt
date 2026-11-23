package com.vrbox.webmirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Con trỏ "nhìn để bấm" (gaze cursor).
 * - Vẽ một vòng tròn trắng + chấm đỏ tại vị trí (cx, cy).
 * - Khi đang đếm giờ để bấm, vẽ thêm vòng cung màu xanh lá thể hiện tiến độ (progress 0..1).
 * View này nằm TRONG khung mắt trái nên sẽ được sao chép sang mắt phải cùng với trang web.
 */
class GazeCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var cx = -1f
    private var cy = -1f
    private val density = resources.displayMetrics.density

    /** Tiến độ đếm giờ bấm, từ 0 đến 1. */
    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        color = 0x99000000.toInt()
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF3B30.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF34C759.toInt()
    }

    private val arcRect = RectF()

    /** Đặt vị trí con trỏ (toạ độ tính theo View này). */
    fun setPosition(x: Float, y: Float) {
        cx = x
        cy = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (cx < 0f || cy < 0f) return
        val r = 14f * density
        canvas.drawCircle(cx, cy, r, shadowPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(cx, cy, 3f * density, dotPaint)
        if (progress > 0f) {
            arcRect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arcRect, -90f, 360f * progress, false, progressPaint)
        }
    }
}
