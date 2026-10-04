package com.hkey.app.settings.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hkey.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class SettingsAppearanceTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().coerceAtLeast(b.luminance())
        val l2 = a.luminance().coerceAtMost(b.luminance())
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test
    fun lightSchemeUsesBrandColors() {
        assertEquals(Color(0xFF007F79), HKeyLightColorScheme.primary)
        assertEquals(Color(0xFFF4F8F8), HKeyLightColorScheme.background)
        assertEquals(Color(0xFFFFFFFF), HKeyLightColorScheme.surface)
    }

    @Test
    fun darkSchemeUsesBrandColors() {
        assertEquals(Color(0xFF70DBCE), HKeyDarkColorScheme.primary)
        assertEquals(Color(0xFF0C191E), HKeyDarkColorScheme.background)
        assertEquals(Color(0xFF14262C), HKeyDarkColorScheme.surface)
    }

    @Test
    fun lightSchemeContrastPassesWcag() {
        assertTrue(
            "primary/onPrimary ${contrast(HKeyLightColorScheme.primary, HKeyLightColorScheme.onPrimary)}",
            contrast(HKeyLightColorScheme.primary, HKeyLightColorScheme.onPrimary) >= 4.5
        )
        assertTrue(
            "surface/onSurface ${contrast(HKeyLightColorScheme.surface, HKeyLightColorScheme.onSurface)}",
            contrast(HKeyLightColorScheme.surface, HKeyLightColorScheme.onSurface) >= 4.5
        )
    }

    @Test
    fun darkSchemeContrastPassesWcag() {
        assertTrue(
            "primary/onPrimary ${contrast(HKeyDarkColorScheme.primary, HKeyDarkColorScheme.onPrimary)}",
            contrast(HKeyDarkColorScheme.primary, HKeyDarkColorScheme.onPrimary) >= 4.5
        )
        assertTrue(
            "surface/onSurface ${contrast(HKeyDarkColorScheme.surface, HKeyDarkColorScheme.onSurface)}",
            contrast(HKeyDarkColorScheme.surface, HKeyDarkColorScheme.onSurface) >= 4.5
        )
    }

    private fun outputFile(name: String): File? {
        val dir = System.getProperty("hkey.appearanceOutputDir") ?: return null
        return File(dir, name)
    }

    private fun render(resId: Int, outName: String? = null): Bitmap {
        val ctx = RuntimeEnvironment.getApplication()
        val d = ctx.getDrawable(resId)
        assertNotNull("drawable $resId", d)
        val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d!!.setBounds(0, 0, 108, 108)
        d.draw(c)
        var opaque = 0
        for (y in 0 until 108) for (x in 0 until 108) {
            if ((bmp.getPixel(x, y) ushr 24) > 0) opaque++
        }
        assertTrue("drawable $resId renders no pixels", opaque > 0)
        if (outName != null) {
            outputFile(outName)?.let { f ->
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return bmp
    }

    @Test
    fun launcherDrawablesRenderNonTransparent() {
        render(R.drawable.ic_launcher_background, "icon-background.png")
        render(R.drawable.ic_launcher_foreground, "icon-foreground.png")
        render(R.drawable.ic_launcher_monochrome, "icon-monochrome.png")
    }

    @Test
    fun adaptiveLauncherIconsResolve() {
        val ctx = RuntimeEnvironment.getApplication()
        val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for ((id, name) in listOf(
            R.mipmap.ic_launcher to "ic_launcher",
            R.mipmap.ic_launcher_round to "ic_launcher_round"
        )) {
            val d = ctx.getDrawable(id)
            assertNotNull(name, d)
            bmp.eraseColor(0)
            d!!.setBounds(0, 0, 108, 108)
            d.draw(c)
            var opaque = 0
            for (y in 0 until 108) for (x in 0 until 108) {
                if ((bmp.getPixel(x, y) ushr 24) > 0) opaque++
            }
            assertTrue("$name renders empty", opaque > 0)
            outputFile("$name.png")?.let { f ->
                FileOutputStream(f).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
