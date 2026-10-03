package com.hkey.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.hkey.app.R

/**
 * Adapter cho Spinner chọn giao diện, hiển thị ảnh mô tả thumbnail trực quan kèm tên giao diện.
 */
class ThemeSpinnerAdapter(
    private val context: Context,
    private val themes: List<KbTheme>
) : BaseAdapter() {

    private val density = context.resources.displayMetrics.density
    private val thumbCache = mutableMapOf<String, Bitmap>()
    private val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun getCount(): Int = themes.size
    override fun getItem(position: Int): KbTheme = themes[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_theme_spinner, parent, false)
        bindView(view, getItem(position))
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_theme_dropdown, parent, false)
        bindView(view, getItem(position))
        return view
    }

    private fun bindView(view: View, theme: KbTheme) {
        val iv = view.findViewById<ImageView>(R.id.iv_theme_thumb)
        val tv = view.findViewById<TextView>(R.id.tv_theme_name)
        tv?.text = theme.name
        iv?.setImageBitmap(getThumbnail(theme))
    }

    private fun getThumbnail(theme: KbTheme): Bitmap {
        return thumbCache.getOrPut(theme.id) {
            createThumbnail(theme)
        }
    }

    private fun createThumbnail(theme: KbTheme): Bitmap {
        val w = (48 * density).toInt().coerceAtLeast(1)
        val h = (30 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val wf = w.toFloat()
        val hf = h.toFloat()
        val corner = 4f * density
        val outerRect = RectF(0f, 0f, wf, hf)

        val clipPath = Path()
        clipPath.addRoundRect(outerRect, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val p = if (theme.id == KbThemes.SYSTEM) {
            if (isNight) KbPalette.DARK else KbPalette.LIGHT
        } else {
            theme.palette
        }

        if (theme.id == KbThemes.SYSTEM) {
            // Nửa trái sáng, nửa phải tối
            bgPaint.color = KbPalette.LIGHT.bg
            canvas.drawRect(0f, 0f, wf / 2f, hf, bgPaint)
            bgPaint.color = KbPalette.DARK.bg
            canvas.drawRect(wf / 2f, 0f, wf, hf, bgPaint)

            // Vạch chia
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x4080868B.toInt()
                strokeWidth = 1f * density
            }
            canvas.drawLine(wf / 2f, 0f, wf / 2f, hf, divPaint)

            // Phím mẫu bên trái (sáng)
            val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            keyPaint.color = KbPalette.LIGHT.key
            val kCorner = 2f * density
            canvas.drawRoundRect(RectF(3f * density, 8f * density, wf / 2f - 3f * density, 16f * density), kCorner, kCorner, keyPaint)
            keyPaint.color = KbPalette.LIGHT.enter
            canvas.drawRoundRect(RectF(3f * density, 19f * density, wf / 2f - 3f * density, 26f * density), kCorner, kCorner, keyPaint)

            // Phím mẫu bên phải (tối)
            keyPaint.color = KbPalette.DARK.key
            canvas.drawRoundRect(RectF(wf / 2f + 3f * density, 8f * density, wf - 3f * density, 16f * density), kCorner, kCorner, keyPaint)
            keyPaint.color = KbPalette.DARK.enter
            canvas.drawRoundRect(RectF(wf / 2f + 3f * density, 19f * density, wf - 3f * density, 26f * density), kCorner, kCorner, keyPaint)
        } else {
            // Nền theme
            if (p.gradient) {
                bgPaint.shader = LinearGradient(0f, 0f, 0f, hf, p.bgTop, p.bgBottom, Shader.TileMode.CLAMP)
            } else {
                bgPaint.shader = null
                bgPaint.color = p.bg
            }
            canvas.drawRect(outerRect, bgPaint)

            // Thanh gợi ý nhỏ
            val candPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (p.bar != 0) {
                candPaint.color = p.bar
                canvas.drawRect(0f, 0f, wf, 6f * density, candPaint)
            }
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.divider
                strokeWidth = 0.8f * density
            }
            canvas.drawLine(0f, 6f * density, wf, 6f * density, divPaint)

            // Phím mẫu
            val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f * density
                color = p.keyStroke
            }
            val kCorner = 2f * density

            // Hàng 1: 4 ô phím nhỏ
            val padX = 3f * density
            val keyGap = 2f * density
            val keyW = (wf - 2 * padX - 3 * keyGap) / 4f
            val r1Y = 9f * density
            val r1H = 8f * density
            for (i in 0..3) {
                val kx = padX + i * (keyW + keyGap)
                val rect = RectF(kx, r1Y, kx + keyW, r1Y + r1H)
                keyPaint.color = p.key
                canvas.drawRoundRect(rect, kCorner, kCorner, keyPaint)
                if (p.keyStroke != 0) {
                    canvas.drawRoundRect(rect, kCorner, kCorner, strokePaint)
                }
            }

            // Hàng 2: phím func, space, enter
            val r2Y = 19f * density
            val r2H = 8f * density
            val fnW = keyW * 0.9f
            val enterW = keyW * 1.3f
            val spaceW = wf - 2 * padX - 2 * keyGap - fnW - enterW

            var cx = padX
            // Func
            val fnRect = RectF(cx, r2Y, cx + fnW, r2Y + r2H)
            keyPaint.color = p.func
            canvas.drawRoundRect(fnRect, kCorner, kCorner, keyPaint)
            cx += fnW + keyGap

            // Space
            val spRect = RectF(cx, r2Y, cx + spaceW, r2Y + r2H)
            keyPaint.color = p.key
            canvas.drawRoundRect(spRect, kCorner, kCorner, keyPaint)
            if (p.keyStroke != 0) {
                canvas.drawRoundRect(spRect, kCorner, kCorner, strokePaint)
            }
            cx += spaceW + keyGap

            // Enter
            val entRect = RectF(cx, r2Y, cx + enterW, r2Y + r2H)
            keyPaint.color = p.enter
            canvas.drawRoundRect(entRect, kCorner, kCorner, keyPaint)
        }

        canvas.restore()

        // Viền ngoài thumbnail
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
            color = 0x4080868B.toInt()
        }
        canvas.drawRoundRect(outerRect, corner, corner, borderPaint)

        return bmp
    }
}
