package com.hkey.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private fun allKeys(rows: List<KbRow>) = rows.flatMap { it.keys }

    @Test
    fun tagsWellFormed() {
        for (rows in listOf(KbLayouts.letters(false), KbLayouts.symbols(), KbLayouts.emoji())) {
            assertTrue(rows.isNotEmpty())
            for (k in allKeys(rows)) {
                assertTrue("tag lạ: ${k.tag}", k.tag.matches(Regex("(ch|p|fn|tx):.+")))
                assertTrue("weight: ${k.tag}", k.w > 0f)
            }
        }
    }

    @Test
    fun lettersHasCoreKeys() {
        val keys = allKeys(KbLayouts.letters(false))
        val tags = keys.map { it.tag }
        for (t in listOf("fn:shift", "fn:del", "fn:space", "fn:enter", "fn:lang", "fn:emoji", "fn:sym")) {
            assertTrue("thiếu $t", t in tags)
        }
        // 26 chữ cái
        assertEquals(26, tags.count { it.startsWith("ch:") })
        // space vuốt được, del lặp được
        assertTrue(keys.first { it.tag == "fn:space" }.swipe)
        assertTrue(keys.first { it.tag == "fn:del" }.repeat)
        // giữ phím emoji -> đổi IME
        assertEquals("fn:ime", keys.first { it.tag == "fn:emoji" }.longTag)
    }

    @Test
    fun numberRowOptional() {
        val with = KbLayouts.letters(true)
        assertEquals(5, with.size)
        assertEquals(10, with[0].keys.count { it.tag.startsWith("p:") })
        assertEquals(4, KbLayouts.letters(false).size)
    }

    @Test
    fun symbolsAndEmojiPages() {
        val sym = allKeys(KbLayouts.symbols()).map { it.tag }
        assertTrue("fn:abc" in sym)
        assertTrue("p:1" in sym && "p:@" in sym)

        val emo = KbLayouts.emoji()
        val emoKeys = allKeys(emo)
        assertTrue(emoKeys.count { it.tag.startsWith("tx:") } >= 30)
        assertTrue(emoKeys.any { it.tag == "fn:paste" })
        assertTrue(emoKeys.any { it.tag == "fn:abc" })
        // mọi tx: phải có label (ký tự emoji)
        assertTrue(emoKeys.filter { it.tag.startsWith("tx:") }.all { it.label != null })
    }

    @Test
    fun lettersAltsExist() {
        val keys = allKeys(KbLayouts.letters(false))
        assertNotNull(keys.first { it.tag == "ch:q" }.alts)
        assertTrue(keys.first { it.tag == "ch:e" }.alts.contains("ê"))
        assertTrue(keys.first { it.tag == "ch:o" }.alts.contains("ô"))
        assertTrue(keys.first { it.tag == "p:." }.alts.contains("?"))
    }
}
