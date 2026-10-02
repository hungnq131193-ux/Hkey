package com.hkey.app.engine

/**
 * Parse vi_model.tsv (res/raw) — số liệu thống kê n-gram từ Wikipedia tiếng
 * Việt (CC BY-SA 4.0, xem tools/build_model.py). Thuần JVM để test.
 *
 * Định dạng: u<TAB>từ<TAB>freq | s<BOS>từ<TAB>freq | b<TAB>w-1<TAB>w<TAB>c
 *            | t<TAB>w-2<TAB>w-1<TAB>w<TAB>c
 */
object ViModel {

    class Model(
        val unigrams: Map<String, Int>,
        val bigrams: Map<String, Map<String, Int>>,
        val trigrams: Map<String, Map<String, Int>>,
        val bos: List<String>
    )

    fun parse(lines: Sequence<String>): Model {
        val uni = mutableMapOf<String, Int>()
        val bi = mutableMapOf<String, MutableMap<String, Int>>()
        val tri = mutableMapOf<String, MutableMap<String, Int>>()
        val bos = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') continue
            val f = line.split('\t')
            when (f[0]) {
                "u" -> if (f.size == 3) uni[f[1]] = f[2].toIntOrNull() ?: continue
                "s" -> if (f.size >= 2) bos.add(f[1])
                "b" -> if (f.size == 4) {
                    bi.getOrPut(f[1]) { mutableMapOf() }[f[2]] =
                        f[3].toIntOrNull() ?: continue
                }
                "t" -> if (f.size == 5) {
                    tri.getOrPut(f[1] + "|" + f[2]) { mutableMapOf() }[f[3]] =
                        f[4].toIntOrNull() ?: continue
                }
            }
        }
        return Model(uni, bi, tri, bos)
    }
}
