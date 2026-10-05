package com.hkey.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        for (t in listOf("fn:shift", "fn:del", "fn:space", "fn:enter", "fn:lang", "fn:sym")) {
            assertTrue("thiếu $t", t in tags)
        }
        // 26 chữ cái
        assertEquals(26, tags.count { it.startsWith("ch:") })
        // space vuốt được + rộng ~4.0 sau khi gộp emoji vào phím ,
        assertTrue(keys.first { it.tag == "fn:space" }.swipe)
        assertEquals(4.0f, keys.first { it.tag == "fn:space" }.w, 0.001f)
        assertTrue(keys.first { it.tag == "fn:del" }.repeat)
        // giữ phím , -> trang emoji (kèm icon mini 😊); giữ VI/EN -> đổi IME
        assertFalse("fn:emoji không còn là phím riêng", "fn:emoji" in tags)
        assertEquals("fn:emoji", keys.first { it.tag == "p:," }.longTag)
        assertEquals("😊", keys.first { it.tag == "p:," }.mini)
        assertEquals("fn:ime", keys.first { it.tag == "fn:lang" }.longTag)
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
        // không xẻ đôi surrogate pair: mọi high surrogate đi kèm low surrogate
        assertTrue(emoKeys.filter { it.tag.startsWith("tx:") }.all { k ->
            val s = k.label!!
            s.indices.all { i ->
                when {
                    Character.isHighSurrogate(s[i]) -> i + 1 < s.length && Character.isLowSurrogate(s[i + 1])
                    Character.isLowSurrogate(s[i]) -> i > 0 && Character.isHighSurrogate(s[i - 1])
                    else -> true
                }
            }
        })
    }

    @Test
    fun symbolsHaveFullSet() {
        // 1.2: thiếu "/" (gạch chéo) và nhiều ký hiệu khác ở 1.1.1
        val all = (allKeys(KbLayouts.symbols()) + allKeys(KbLayouts.symbols2())).map { it.tag }
        for (c in listOf("/", "\\", "|", "~", "`", "^", "[", "]", "{", "}", "<", ">",
            "=", "%", "_", "€", "£", "¥", "₫", "°", "•")) {
            assertTrue("thiếu ký hiệu $c", "p:$c" in all)
        }
        assertTrue("fn:sym2" in allKeys(KbLayouts.symbols()).map { it.tag })
        assertTrue("fn:sym" in allKeys(KbLayouts.symbols2()).map { it.tag })
        // phím . có "/" khi nhấn giữ
        assertTrue(allKeys(KbLayouts.letters(false)).first { it.tag == "p:." }.alts.contains("/"))
    }

    @Test
    fun fieldKindChangesCommaKey() {
        val url = allKeys(KbLayouts.letters(false, KbField.URL)).map { it.tag }
        assertTrue("p:/" in url)
        assertFalse("p:," in url)
        val email = allKeys(KbLayouts.letters(false, KbField.EMAIL))
        assertEquals("fn:emoji", email.first { it.tag == "p:@" }.longTag)
    }

    @Test
    fun emojiCategoriesAndTabs() {
        for (cat in 1 until KbLayouts.EMOJI_TABS.size) {
            val list = KbLayouts.emojiList(cat)
            assertTrue("nhóm $cat quá ít", list.size >= 30)
            assertEquals("trùng emoji trong nhóm $cat", list.size, list.toSet().size)
        }
        // tab gần đây rỗng -> chỉ còn hàng tab, không crash
        val empty = KbLayouts.emoji(0, emptyList())
        assertEquals(2, empty.size)
        assertTrue(empty[0].keys.any { it.tag == "fn:ecat:0" })
        assertTrue(empty.last().keys.any { it.tag == "fn:del" })
        // gần đây có dữ liệu
        val rec = KbLayouts.emoji(0, listOf("😀", "❤️"))
        assertEquals(3, rec.size)
        assertEquals("tx:❤️", rec[0].keys[1].tag)
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
