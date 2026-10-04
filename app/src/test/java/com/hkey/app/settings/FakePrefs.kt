package com.hkey.app.settings

import android.content.SharedPreferences

/** 1.4.0: SharedPreferences in-memory cho unit test JVM — hỗ trợ mọi kiểu
 *  get/put/remove/clear/listener, apply() ghi đồng bộ. */
class FakePrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    private inner class Ed : SharedPreferences.Editor {
        private val ops = mutableListOf<() -> Unit>()
        private var clearFlag = false

        override fun putString(k: String, v: String?) = apply { ops += { map[k] = v } }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { ops += { map[k] = v } }
        override fun putInt(k: String, v: Int) = apply { ops += { map[k] = v } }
        override fun putLong(k: String, v: Long) = apply { ops += { map[k] = v } }
        override fun putFloat(k: String, v: Float) = apply { ops += { map[k] = v } }
        override fun putBoolean(k: String, v: Boolean) = apply { ops += { map[k] = v } }
        override fun remove(k: String) = apply { ops += { map.remove(k) } }
        override fun clear() = apply { clearFlag = true }

        private fun flush(): Boolean {
            val changed = mutableSetOf<String>()
            if (clearFlag) { changed += map.keys; map.clear(); clearFlag = false }
            for (op in ops) {
                val before = HashMap(map)
                op()
                for ((k, v) in map) if (before[k] != v) changed += k
                for (k in before.keys) if (k !in map) changed += k
            }
            ops.clear()
            for (l in listeners) for (k in changed) l.onSharedPreferenceChanged(this@FakePrefs, k)
            return true
        }

        override fun commit() = flush()
        override fun apply() { flush() }
    }

    override fun edit(): SharedPreferences.Editor = Ed()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(k: String, d: String?): String? = map[k] as? String ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (map[k] as? MutableSet<String>) ?: d
    override fun getInt(k: String, d: Int): Int = map[k] as? Int ?: d
    override fun getLong(k: String, d: Long): Long = map[k] as? Long ?: d
    override fun getFloat(k: String, d: Float): Float = map[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean): Boolean = map[k] as? Boolean ?: d
    override fun contains(k: String): Boolean = map.containsKey(k)

    override fun registerOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener
    ) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener
    ) { listeners -= l }
}
