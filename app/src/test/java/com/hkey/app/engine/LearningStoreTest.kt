package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** 1.10 — file học hỏng một dòng chỉ mất dòng đó. */
class LearningStoreTest {

    @Test
    fun malformedLineDoesNotDiscardFile() {
        val f = File.createTempFile("hkey-learned", ".tsv")
        try {
            f.writeText(
                LearningStore.VERSION + "\n" +
                    "w\ttừtốt\t2\t123\n" +
                    "w\thỏng\tkhông-số\tNaN\n" + // dòng hỏng
                    "b\ttôi\tlà\t3\n"
            )
            val data = LearningStore(f).load()
            assertNotNull(data)
            assertEquals(1, data!!.words.size)
            assertEquals("từtốt", data.words[0].first)
            assertEquals(1, data.bigrams.size)
        } finally {
            f.delete()
        }
    }
}
