package com.hkey.app.engine

import java.io.File

/**
 * Lưu dữ liệu học vào file riêng của app (3.6): có version, ghi file tạm rồi
 * đổi tên (atomic trên POSIX), file hỏng -> bỏ qua dùng mặc định.
 * Thuần java.io — test được trên JVM.
 */
class LearningStore(private val file: File) {

    data class Data(
        val words: List<Triple<String, Int, Long>>,       // word, personal, lastSeen
        val bigrams: List<Triple<String, String, Int>>    // prev, next, count
    )

    @Synchronized // save gọi từ thread nền + lifecycle main — chống ghi chéo .tmp
    fun load(): Data? {
        if (!file.exists()) return null
        return try {
            val words = mutableListOf<Triple<String, Int, Long>>()
            val bigrams = mutableListOf<Triple<String, String, Int>>()
            val lines = file.readLines()
            if (lines.firstOrNull() != VERSION) return null
            for (line in lines.drop(1)) {
                try { // 1.10: dòng hỏng chỉ bỏ dòng đó, không bỏ cả file
                    val f = line.split('\t')
                    when (f.getOrNull(0)) {
                        "w" -> if (f.size == 4) words.add(
                            Triple(f[1], f[2].toInt(), f[3].toLong())
                        )
                        "b" -> if (f.size == 4) bigrams.add(
                            Triple(f[1], f[2], f[3].toInt())
                        )
                    }
                } catch (e: Exception) { /* bỏ dòng hỏng */ }
            }
            Data(words, bigrams)
        } catch (e: Exception) {
            null // file hỏng -> bỏ qua
        }
    }

    @Synchronized
    fun save(data: Data) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val sb = StringBuilder().appendLine(VERSION)
        for ((w, p, t) in data.words) sb.appendLine("w\t$w\t$p\t$t")
        for ((p, n, c) in data.bigrams) sb.appendLine("b\t$p\t$n\t$c")
        tmp.writeText(sb.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    companion object {
        const val VERSION = "hkey-learned-v1"
        const val MAX_LEARNED_WORDS = 5000
        const val MAX_USER_BIGRAMS = 20000
    }
}
