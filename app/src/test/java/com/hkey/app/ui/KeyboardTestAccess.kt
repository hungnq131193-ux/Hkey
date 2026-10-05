package com.hkey.app.ui

import android.graphics.Rect
import android.graphics.RectF
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.lang.reflect.Method

private fun Any.field(name: String): Any? {
    var c: Class<*>? = javaClass
    while (c != null) {
        try {
            val f = c.getDeclaredField(name)
            f.isAccessible = true
            return f.get(this)
        } catch (e: NoSuchFieldException) {
            c = c.superclass
        }
    }
    throw NoSuchFieldException("$name on $javaClass")
}

private fun Any.method(name: String, vararg types: Class<*>): Method {
    var c: Class<*>? = javaClass
    while (c != null) {
        try {
            val m = c.getDeclaredMethod(name, *types)
            m.isAccessible = true
            return m
        } catch (e: NoSuchMethodException) {
            c = c.superclass
        }
    }
    throw NoSuchMethodException("$name on $javaClass")
}

private fun KeyboardView.areas(): List<Any> =
    @Suppress("UNCHECKED_CAST") (field("areas") as List<Any>)

private fun Any.areaKey(): KbKey = field("key") as KbKey
private fun Any.areaHit(): RectF = RectF(field("hit") as RectF)

internal fun KeyboardView.testHitArea(tag: String): RectF? =
    areas().firstOrNull { it.areaKey().tag == tag }?.areaHit()

internal fun KeyboardView.testKeyAt(x: Float, y: Float): KbKey? =
    (method("areaAt", Float::class.java, Float::class.java)
        .invoke(this, x, y))?.let { (it as Any).areaKey() }

internal fun KeyboardView.testGridClip(): RectF =
    RectF(0f, field("gridTop") as Float, width.toFloat(), field("gridBottom") as Float)

internal fun KeyboardView.testScrollGridTo(v: Float) {
    val max = field("gridMax") as Float
    val f = javaClass.getDeclaredField("gridScroll")
    f.isAccessible = true
    f.setFloat(this, v.coerceIn(0f, max))
    invalidate()
}

internal fun KeyboardView.testSetEmojiCategory(cat: Int) {
    method("setEmojiCategory", Int::class.java).invoke(this, cat)
}

private fun KeyboardView.helper() = field("touchHelper")!!

internal fun KeyboardView.testVisibleNodeIds(): List<Int> {
    val ids = mutableListOf<Int>()
    helper().method("getVisibleVirtualViews", MutableList::class.java)
        .invoke(helper(), ids)
    return ids
}

internal fun KeyboardView.testNodeBounds(id: Int): Rect {
    val node = AccessibilityNodeInfoCompat.obtain()
    helper().method(
        "onPopulateNodeForVirtualView",
        Int::class.java, AccessibilityNodeInfoCompat::class.java
    ).invoke(helper(), id, node)
    val r = Rect()
    node.getBoundsInParent(r)
    return r
}

internal fun KeyboardView.testNodeTag(id: Int): String? =
    areas().getOrNull(id)?.areaKey()?.tag

internal fun KeyboardView.testNodeClick(id: Int): Boolean =
    helper().method(
        "onPerformActionForVirtualView",
        Int::class.java, Int::class.java, android.os.Bundle::class.java
    ).invoke(helper(), id, AccessibilityNodeInfoCompat.ACTION_CLICK, null) as Boolean
