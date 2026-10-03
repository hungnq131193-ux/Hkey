package com.hkey.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * View hiển thị ảnh mô tả / xem trước trực quan của giao diện bàn phím HKey.
 * Cập nhật động theo palette của giao diện và kiểu bo góc phím đã chọn.
 */
class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private var palette: KbPalette = KbPalette.DARK
    private var cornerDp: Float = 7f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val clipPath = Path()
    private val outerRect = RectF()
    private val tempRect = RectF()
    private val shadowRect = RectF()

    init {
        // Cấu hình ban đầu theo chế độ máy
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = if (isNight) KbPalette.DARK else KbPalette.LIGHT
    }

    fun setTheme(themeId: String?, shapeId: String?) {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = KbThemes.palette(themeId, isNight)
        cornerDp = KbThemes.cornerDp(shapeId)
        invalidate()
    }

    fun update(palette: KbPalette, cornerDp: Float) {
        this.palette = palette
        this.cornerDp = cornerDp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val outerCorner = 12f * density
        outerRect.set(0f, 0f, w, h)

        clipPath.reset()
        clipPath.addRoundRect(outerRect, outerCorner, outerCorner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Nền bàn phím: gradient hoặc màu đơn sắc
        if (palette.gradient) {
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                palette.bgTop, palette.bgBottom,
                Shader.TileMode.CLAMP
            )
        } else {
            bgPaint.shader = null
            bgPaint.color = palette.bg
        }
        canvas.drawRect(outerRect, bgPaint)

        // 2. Thanh gợi ý (Candidate Bar)
        val candH = 34f * density
        if (palette.bar != 0) {
            barPaint.color = palette.bar
            canvas.drawRect(0f, 0f, w, candH, barPaint)
        }

        // Vạch ngang đáy candidate bar
        divPaint.color = palette.divider
        divPaint.strokeWidth = 1f * density
        canvas.drawLine(0f, candH, w, candH, divPaint)

        // Vạch phân cách các từ gợi ý
        val colW = w / 3f
        val divPadY = 8f * density
        canvas.drawLine(colW, divPadY, colW, candH - divPadY, divPaint)
        canvas.drawLine(colW * 2f, divPadY, colW * 2f, candH - divPadY, divPaint)

        // Chữ gợi ý trên thanh
        val candTextY = candH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 12f * density
        textPaint.color = palette.text
        canvas.drawText("HKey", colW * 0.5f, candTextY, textPaint)

        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 12.5f * density
        textPaint.color = palette.accent
        canvas.drawText("Tiếng Việt", colW * 1.5f, candTextY, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 12f * density
        textPaint.color = palette.text
        canvas.drawText("Thông minh", colW * 2.5f, candTextY, textPaint)

        // 3. Các hàng phím thu nhỏ
        val kbTop = candH
        val kbH = h - kbTop
        val padX = 6f * density
        val padTop = 6f * density
        val padBottom = 8f * density
        val rowGap = 4f * density
        val keyGap = 3.5f * density

        val availH = kbH - padTop - padBottom - 3f * rowGap
        val rowH = (availH / 4f).coerceAtLeast(14f * density)

        // Tỷ lệ bo góc theo kích thước phím thu nhỏ
        val keyCorner = (cornerDp * density * (rowH / (38f * density))).coerceIn(2f * density, 12f * density)
        val shadowH = if (palette.shadowDp > 0f) (1.2f * density).coerceAtMost(rowH * 0.12f) else 0f

        // Đơn vị chiều rộng phím chữ chuẩn (10 phím hàng 1)
        val unitW = (w - 2f * padX - 9f * keyGap) / 10f

        // Hàng 1: q w e r t y u i o p (10 phím)
        val r1Y = kbTop + padTop
        val r1Chars = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        for (i in r1Chars.indices) {
            val kx = padX + i * (unitW + keyGap)
            tempRect.set(kx, r1Y, kx + unitW, r1Y + rowH)
            drawKey(canvas, tempRect, r1Chars[i], palette.key, palette.text, keyCorner, shadowH)
        }

        // Hàng 2: a s d f g h j k l (9 phím thụt lề)
        val r2Y = r1Y + rowH + rowGap
        val r2PadX = padX + (unitW + keyGap) * 0.5f
        val r2Chars = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        for (i in r2Chars.indices) {
            val kx = r2PadX + i * (unitW + keyGap)
            tempRect.set(kx, r2Y, kx + unitW, r2Y + rowH)
            drawKey(canvas, tempRect, r2Chars[i], palette.key, palette.text, keyCorner, shadowH)
        }

        // Hàng 3: ⇧, z, x, c, v, b, n, m, ⌫
        val r3Y = r2Y + rowH + rowGap
        val shiftW = unitW * 1.4f
        val r3MidAvail = w - 2f * padX - 2f * shiftW - 8f * keyGap
        val r3MidUnit = r3MidAvail / 7f
        val r3MidChars = listOf("z", "x", "c", "v", "b", "n", "m")

        // Phím ⇧ Shift
        tempRect.set(padX, r3Y, padX + shiftW, r3Y + rowH)
        drawKey(canvas, tempRect, "⇧", palette.func, palette.funcText, keyCorner, shadowH)

        // 7 phím chữ z..m
        var curX = padX + shiftW + keyGap
        for (ch in r3MidChars) {
            tempRect.set(curX, r3Y, curX + r3MidUnit, r3Y + rowH)
            drawKey(canvas, tempRect, ch, palette.key, palette.text, keyCorner, shadowH)
            curX += r3MidUnit + keyGap
        }

        // Phím ⌫ Del
        tempRect.set(curX, r3Y, padX + w - 2f * padX, r3Y + rowH)
        drawKey(canvas, tempRect, "⌫", palette.func, palette.funcText, keyCorner, shadowH)

        // Hàng 4: ?123, ,, VI, [ Space / HKey ], ., ↵
        val r4Y = r3Y + rowH + rowGap
        val fn123W = unitW * 1.35f
        val punctCommaW = unitW * 0.95f
        val viW = unitW * 0.95f
        val punctDotW = unitW * 0.95f
        val enterW = unitW * 1.45f

        val spaceW = w - 2f * padX - 5f * keyGap - (fn123W + punctCommaW + viW + punctDotW + enterW)

        var x4 = padX
        // Phím ?123
        tempRect.set(x4, r4Y, x4 + fn123W, r4Y + rowH)
        drawKey(canvas, tempRect, "?123", palette.func, palette.funcText, keyCorner, shadowH)
        x4 += fn123W + keyGap

        // Phím ,
        tempRect.set(x4, r4Y, x4 + punctCommaW, r4Y + rowH)
        drawKey(canvas, tempRect, ",", palette.key, palette.text, keyCorner, shadowH)
        x4 += punctCommaW + keyGap

        // Phím VI
        tempRect.set(x4, r4Y, x4 + viW, r4Y + rowH)
        drawKey(canvas, tempRect, "VI", palette.func, palette.funcText, keyCorner, shadowH)
        x4 += viW + keyGap

        // Phím Space (HKey)
        tempRect.set(x4, r4Y, x4 + spaceW, r4Y + rowH)
        drawKey(canvas, tempRect, "HKey", palette.key, palette.dim, keyCorner, shadowH, isSpace = true)
        x4 += spaceW + keyGap

        // Phím .
        tempRect.set(x4, r4Y, x4 + punctDotW, r4Y + rowH)
        drawKey(canvas, tempRect, ".", palette.key, palette.text, keyCorner, shadowH)
        x4 += punctDotW + keyGap

        // Phím ↵ Enter
        tempRect.set(x4, r4Y, w - padX, r4Y + rowH)
        drawKey(canvas, tempRect, "↵", palette.enter, palette.onAccent, keyCorner, shadowH)

        canvas.restore()

        // 4. Viền khung ngoài mảnh tinh tế
        borderPaint.color = 0x3380868B.toInt()
        borderPaint.strokeWidth = 1f * density
        canvas.drawRoundRect(outerRect, outerCorner, outerCorner, borderPaint)
    }

    private fun drawKey(
        canvas: Canvas,
        rect: RectF,
        label: String,
        bg: Int,
        fg: Int,
        corner: Float,
        shadowH: Float,
        isSpace: Boolean = false
    ) {
        // Đổ bóng phím
        if (shadowH > 0f && palette.shadow != 0) {
            keyPaint.color = palette.shadow
            shadowRect.set(rect.left, rect.top + shadowH, rect.right, rect.bottom + shadowH)
            canvas.drawRoundRect(shadowRect, corner, corner, keyPaint)
        }

        // Nền phím
        keyPaint.color = bg
        canvas.drawRoundRect(rect, corner, corner, keyPaint)

        // Viền phím (stroke cho theme có keyStroke như Sunset, Galaxy)
        if (palette.keyStroke != 0) {
            keyStrokePaint.color = palette.keyStroke
            keyStrokePaint.strokeWidth = 1f * density
            canvas.drawRoundRect(rect, corner, corner, keyStrokePaint)
        }

        // Ký tự / nhãn phím
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = fg
        textPaint.textSize = if (isSpace) 9.5f * density else (rect.height() * 0.42f).coerceIn(10f * density, 13.5f * density)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), textY, textPaint)
    }
}
