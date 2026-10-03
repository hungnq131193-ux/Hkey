package com.hkey.app.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Đọc vi_model.bin — model n-gram dạng nhị phân phẳng (tools/tsv_to_bin.py).
 * Đọc được từ ByteBuffer thường lẫn buffer mmap (openRawResourceFd +
 * FileChannel.map, cần noCompress "bin" trong gradle). Thuần JVM để test.
 *
 * Layout (little-endian): magic u32 | ver u16 | rsv u16 | nWords u32 |
 * nBos u32 | nBi u32 | nTri u32 | words[u16 len + utf8 + i32 freq] |
 * bos[u32] × nBos | bi[u32 p, u32 n, u32 c] × nBi |
 * tri[u32 p2, u32 p1, u32 n, u32 c] × nTri.
 */
object ViModelBin {

    const val MAGIC = 0x314D4B48

    /** Model đã bung thành mảng phẳng — predictor tự gán mid riêng rồi
     *  remap index file -> mid. */
    class Packed(
        val words: Array<String>, val freqs: IntArray,
        val bosIdx: IntArray,
        val biP: IntArray, val biN: IntArray, val biC: IntArray,
        val triP2: IntArray, val triP1: IntArray, val triN: IntArray,
        val triC: IntArray
    )

    /** null nếu buffer không phải model hợp lệ (magic/ver sai). */
    fun read(buf0: ByteBuffer): Packed? {
        val buf = buf0.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        try {
            if (buf.int != MAGIC.toInt()) return null
            if (buf.short.toInt() != 1) return null
            buf.short // reserved
            val nw = buf.int
            val nb = buf.int
            val nbi = buf.int
            val ntri = buf.int
            if (nw < 0 || nb < 0 || nbi < 0 || ntri < 0 ||
                nw > 1_000_000 || nbi > 5_000_000 || ntri > 5_000_000
            ) return null
            val words = Array(nw) { "" }
            val freqs = IntArray(nw)
            for (i in 0 until nw) {
                val len = buf.short.toInt() and 0xFFFF
                val b = ByteArray(len)
                buf.get(b)
                words[i] = String(b, Charsets.UTF_8)
                freqs[i] = buf.int
            }
            val bos = IntArray(nb) { buf.int }
            val biP = IntArray(nbi); val biN = IntArray(nbi); val biC = IntArray(nbi)
            for (i in 0 until nbi) {
                biP[i] = buf.int; biN[i] = buf.int; biC[i] = buf.int
            }
            val tP2 = IntArray(ntri); val tP1 = IntArray(ntri)
            val tN = IntArray(ntri); val tC = IntArray(ntri)
            for (i in 0 until ntri) {
                tP2[i] = buf.int; tP1[i] = buf.int; tN[i] = buf.int; tC[i] = buf.int
            }
            return Packed(words, freqs, bos, biP, biN, biC, tP2, tP1, tN, tC)
        } catch (e: Exception) {
            return null // buffer ngắn/hỏng
        }
    }
}
