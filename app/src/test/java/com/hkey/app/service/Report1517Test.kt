package com.hkey.app.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.hkey.app.ui.KeyboardView
import com.hkey.app.ui.KbRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Report1517Test {
    @Test fun explicitHornsStayVietnameseWhileTyping() {
        val h = ImeHarness()
        val expected = listOf("d", "đ", "đu", "đư", "đưo", "đươ", "đươc", "được")
        "dduwowcj".forEachIndexed { i, c ->
            h.type(c.toString())
            h.idle()
            assertEquals("prefix ${"dduwowcj".take(i + 1)}", expected[i], h.composing())
        }
        h.type(" ")
        assertTrue(h.text().endsWith("được "))
    }

    @Test fun learnedRawHornsDoNotOverrideVietnamese() {
        val engine = com.hkey.app.engine.TelexEngine(com.hkey.app.engine.EngineOptions(
            commonWord = { it in setOf("dduwow", "dduwowcj", "được") }
        ))
        assertEquals("đươ", engine.transform("dduwow"))
        assertEquals("được", engine.transform("dduwowcj"))
    }

    private fun keyboard(h: ImeHarness): KeyboardView =
        HKeyIME::class.java.getDeclaredField("kbView").apply { isAccessible = true }.get(h.ime) as KeyboardView

    @Suppress("UNCHECKED_CAST")
    private fun rows(kb: KeyboardView): List<KbRow> =
        KeyboardView::class.java.getDeclaredField("rows").apply { isAccessible = true }.get(kb) as List<KbRow>

    @Test fun numericFieldsUseLargeDigitsAndRespectFlags() {
        val h = ImeHarness(inputType = InputType.TYPE_CLASS_NUMBER)
        val kb = keyboard(h)
        for (flags in listOf(0, InputType.TYPE_NUMBER_FLAG_DECIMAL, InputType.TYPE_NUMBER_FLAG_SIGNED,
            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)) {
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER or flags }
            h.ime.onStartInput(info, false)
            h.ime.onStartInputView(info, false)
            val layout = rows(kb)
            assertEquals(listOf("p:1", "p:2", "p:3"), layout[0].keys.take(3).map { it.tag })
            assertEquals(listOf("p:4", "p:5", "p:6"), layout[1].keys.take(3).map { it.tag })
            val tags = layout.flatMap { it.keys }.map { it.tag }
            assertEquals(flags and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0, "p:." in tags)
            assertEquals(flags and InputType.TYPE_NUMBER_FLAG_SIGNED != 0, "p:-" in tags)
            assertTrue((0..9).all { "p:$it" in tags })
            assertFalse("fn:abc" in tags)
            assertTrue("fn:del" in tags && "fn:enter" in tags)
        }
        h.type("123")
        assertTrue(h.text().endsWith("123"))
        h.ime.onCreateInputView()
        assertEquals(KeyboardView.Page.NUMBERS, keyboard(h).page)
        val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        h.ime.onStartInput(text, false)
        h.ime.onStartInputView(text, false)
        assertEquals(KeyboardView.Page.LETTERS, keyboard(h).page)
    }
}
