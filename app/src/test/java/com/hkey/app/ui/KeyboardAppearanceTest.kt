package com.hkey.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardAppearanceTest {

    private fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(c shr 16 and 0xFF) +
            0.7152 * lin(c shr 8 and 0xFF) +
            0.0722 * lin(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val l1 = maxOf(luminance(a), luminance(b))
        val l2 = minOf(luminance(a), luminance(b))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test
    fun darkPaletteMatchesBrand() {
        val p = KbPalette.DARK
        assertEquals(0xFF0C191E.toInt(), p.bg)
        assertEquals(0xFF24383F.toInt(), p.key)
        assertEquals(0xFF172A31.toInt(), p.func)
        assertEquals(0xFFF0F7F8.toInt(), p.text)
        assertEquals(0xFFA6BFC5.toInt(), p.dim)
        assertEquals(0xFF70DBCE.toInt(), p.accent)
        assertEquals(0xFF007F79.toInt(), p.enter)
        assertEquals(0xFFFFFFFF.toInt(), p.onAccent)
    }

    @Test
    fun lightPaletteMatchesBrand() {
        val p = KbPalette.LIGHT
        assertEquals(0xFFF4F8F8.toInt(), p.bg)
        assertEquals(0xFFFFFFFF.toInt(), p.key)
        assertEquals(0xFF007F79.toInt(), p.enter)
        assertEquals(0xFFFFFFFF.toInt(), p.onAccent)
        assertTrue(p.light)
    }

    @Test
    fun defaultPalettesContrastPassWcag() {
        for ((name, p) in listOf("dark" to KbPalette.DARK, "light" to KbPalette.LIGHT)) {
            assertTrue(
                "$name text/key ${contrast(p.text, p.key)}",
                contrast(p.text, p.key) >= 4.5
            )
            assertTrue(
                "$name onAccent/enter ${contrast(p.onAccent, p.enter)}",
                contrast(p.onAccent, p.enter) >= 4.5
            )
        }
    }

    private fun outputFile(name: String): File? {
        val dir = System.getProperty("hkey.appearanceOutputDir") ?: return null
        File(dir).mkdirs()
        return File(dir, name)
    }

    private fun render(
        palette: KbPalette, page: KeyboardView.Page, widthDp: Int,
        outName: String? = null
    ): Bitmap {
        val ctx = RuntimeEnvironment.getApplication()
        val density = ctx.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val v = KeyboardView(ctx)
        v.soundEnabled = false
        v.vibrateEnabled = false
        v.recentEmoji = listOf("😀", "❤️")
        v.configure(100, 0, palette, numRow = false)
        v.showPage(page)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )
        v.layout(0, 0, w, v.measuredHeight)
        val bmp = Bitmap.createBitmap(w, v.measuredHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(palette.bg)
        v.draw(Canvas(bmp))
        var keyPx = 0
        for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
            if (bmp.getPixel(x, y) == palette.key) keyPx++
        }
        assertTrue("$page không vẽ nền phím thường", keyPx > 0 || page == KeyboardView.Page.EMOJI)
        if (outName != null) {
            outputFile(outName)?.let { f ->
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return bmp
    }

    @Test
    fun renderLettersAndEmojiAtPhoneWidths() {
        render(KbPalette.DARK, KeyboardView.Page.LETTERS, 320, "letters-dark-320.png")
        render(KbPalette.DARK, KeyboardView.Page.LETTERS, 360, "letters-dark-360.png")
        render(KbPalette.LIGHT, KeyboardView.Page.LETTERS, 320, "letters-light-320.png")
        render(KbPalette.LIGHT, KeyboardView.Page.LETTERS, 360, "letters-light-360.png")
        render(KbPalette.DARK, KeyboardView.Page.SYMBOLS, 360, "symbols-dark-360.png")
        render(KbPalette.DARK, KeyboardView.Page.EMOJI, 360, "emoji-dark-360.png")
        render(KbPalette.LIGHT, KeyboardView.Page.EMOJI, 320, "emoji-light-320.png")
    }

    @Test
    fun spaceLabelFitsNarrowWidth() {
        val ctx = RuntimeEnvironment.getApplication()
        val density = ctx.resources.displayMetrics.density
        val w = (320 * density).toInt()
        val v = KeyboardView(ctx)
        v.soundEnabled = false
        v.vibrateEnabled = false
        v.configure(100, 0, KbPalette.DARK, numRow = false)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        )
        v.layout(0, 0, w, v.measuredHeight)
        val bmp = Bitmap.createBitmap(w, v.measuredHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(KbPalette.DARK.bg)
        v.draw(Canvas(bmp))
        val hit = v.testHitArea("fn:space")!!
        val dimRgb = KbPalette.DARK.dim and 0xFFFFFF
        var leaks = 0
        val rowTop = hit.top.toInt()
        val rowBottom = hit.bottom.toInt()
        for (y in rowTop until rowBottom) {
            for (x in (hit.right + 2 * density).toInt() until w) {
                if (bmp.getPixel(x, y) and 0xFFFFFF == dimRgb) leaks++
            }
        }
        assertTrue("nhãn space tràn khỏi ô: $leaks px", leaks <= 2)
    }
}
