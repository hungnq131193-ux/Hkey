package com.hkey.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestWorkerPostLatestTest {

    private class QueuePoster {
        val q = ArrayDeque<Runnable>()
        val post: (Runnable) -> Unit = { q.addLast(it) }
        fun pump(n: Int = Int.MAX_VALUE) {
            var k = n
            while (k-- > 0 && q.isNotEmpty()) q.removeFirst().run()
        }
    }

    @Test
    fun hundredPostLatest_runsOnlyLast() {
        val qp = QueuePoster()
        val w = SuggestWorker(qp.post) {}
        val ran = mutableListOf<Int>()
        repeat(100) { i -> w.postLatest(Runnable { ran += i }) }
        assertEquals("100 postLatest chỉ xếp 1 drain", 1, qp.q.size)
        qp.pump()
        assertEquals(listOf(99), ran)
        assertTrue(qp.q.isEmpty())
    }

    @Test
    fun postLatestDuringRun_schedulesNextDrain() {
        val qp = QueuePoster()
        val w = SuggestWorker(qp.post) {}
        val ran = mutableListOf<String>()
        w.postLatest(Runnable {
            ran += "first"
            w.postLatest(Runnable { ran += "second" })
        })
        assertEquals(1, qp.q.size)
        qp.pump(1)
        assertEquals(listOf("first"), ran)
        assertEquals(1, qp.q.size)
        qp.pump()
        assertEquals(listOf("first", "second"), ran)
    }

    @Test
    fun ordinaryPost_fifoUnchanged() {
        val qp = QueuePoster()
        val w = SuggestWorker(qp.post) {}
        val ran = mutableListOf<String>()
        w.post(Runnable { ran += "learn1" })
        w.postLatest(Runnable { ran += "suggest-old" })
        w.postLatest(Runnable { ran += "suggest-new" })
        w.post(Runnable { ran += "persist" })
        qp.pump()
        assertEquals(listOf("learn1", "suggest-new", "persist"), ran)
    }

    @Test
    fun quitClearsPendingLatest() {
        val qp = QueuePoster()
        var quit = false
        val w = SuggestWorker(qp.post) { quit = true }
        val ran = mutableListOf<String>()
        w.post(Runnable { ran += "persist" })
        w.postLatest(Runnable { ran += "suggestion" })
        w.quitSafely()
        assertTrue(quit)
        qp.pump()
        assertEquals(listOf("persist"), ran)
        w.postLatest(Runnable { ran += "after-quit" })
        w.post(Runnable { ran += "persist2" })
        qp.pump()
        assertEquals(listOf("persist", "persist2"), ran)
    }
}
