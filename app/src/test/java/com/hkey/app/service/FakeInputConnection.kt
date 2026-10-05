package com.hkey.app.service

import android.os.Bundle
import android.os.Handler
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

/** 1.4.0 (T0): InputConnection mô phỏng semantics của BaseInputConnection —
 *  giữ Editable, vùng composing, selection; đủ để test HKeyIME trên
 *  Robolectric mà không cần app thật.
 *
 *  Khác biệt có chủ đích: KHÔNG tự gọi IME.onUpdateSelection — test điều
 *  khiển thời điểm callback (mô phỏng update đến trễ/lệch thứ tự). */
class FakeInputConnection : InputConnection {
    val text = SpannableStringBuilder()
    var selStart = 0
    var selEnd = 0
    var composingStart = -1
        private set
    var composingEnd = -1
        private set
    var batchDepth = 0
        private set
    val editorActions = mutableListOf<Int>()
    val keyEvents = mutableListOf<KeyEvent>()

    /** Cursor mới sau commit/composing: ncp > 0 -> sau text chèn; ncp <= 0 ->
     *  trước text chèn (theo AOSP). */
    private fun newSel(start: Int, len: Int, ncp: Int): Int =
        if (ncp > 0) start + len + ncp - 1 else start + ncp

    private fun replaceRange(t: CharSequence, ncp: Int, composing: Boolean): Boolean {
        val s: Int
        val e: Int
        if (composingStart >= 0) {
            s = composingStart; e = composingEnd
        } else {
            s = minOf(selStart, selEnd).coerceIn(0, text.length)
            e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        }
        text.replace(s, e, t)
        val ns = newSel(s, t.length, ncp).coerceIn(0, text.length)
        selStart = ns; selEnd = ns
        if (composing) {
            composingStart = s; composingEnd = s + t.length
        } else {
            composingStart = -1; composingEnd = -1
        }
        return true
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val end = selEnd.coerceIn(0, text.length)
        return text.substring(maxOf(0, end - n), end)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        val end = selEnd.coerceIn(0, text.length)
        return text.substring(end, minOf(text.length, end + n))
    }

    override fun getSelectedText(flags: Int): CharSequence? =
        if (selStart < selEnd) text.subSequence(selStart, selEnd) else null

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    var extractedOverride: (() -> ExtractedText?)? = null

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        extractedOverride?.let { return it() }
        return ExtractedText().apply {
            text = this@FakeInputConnection.text
            startOffset = 0
            partialStartOffset = -1
            partialEndOffset = -1
            selectionStart = selStart
            selectionEnd = selEnd
        }
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val aEnd = minOf(text.length, e + afterLength)
        if (aEnd > e) text.delete(e, aEnd)
        val bStart = maxOf(0, s - beforeLength)
        if (bStart < s) text.delete(bStart, s)
        // vùng composing chạm vùng xoá -> bỏ (đơn giản hoá: hiếm gặp trong test)
        if (composingStart >= 0 && (composingStart < bStart || composingEnd > s)) {
            composingStart = -1; composingEnd = -1
        }
        val ns = bStart.coerceAtLeast(0)
        selStart = ns; selEnd = ns
        return true
    }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int, afterLength: Int
    ): Boolean = deleteSurroundingText(beforeLength, afterLength)

    override fun setComposingText(t: CharSequence, ncp: Int): Boolean =
        replaceRange(t, ncp, composing = true)

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        if (s < e) { composingStart = s; composingEnd = e }
        else { composingStart = -1; composingEnd = -1 }
        return true
    }

    override fun finishComposingText(): Boolean {
        composingStart = -1; composingEnd = -1
        return true
    }

    override fun commitText(t: CharSequence, ncp: Int): Boolean =
        replaceRange(t, ncp, composing = false)

    override fun commitCompletion(text: CompletionInfo?): Boolean = false
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

    override fun setSelection(start: Int, end: Int): Boolean {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        selStart = s; selEnd = e
        return true
    }

    /** App xử lý action (send/search/done) — test gắn callback mô phỏng
     *  app sửa text ngay trong khi action đang chạy (vd ô chat xoá trắng
     *  sau khi gửi). */
    var onEditorAction: ((Int) -> Unit)? = null

    override fun performEditorAction(editorAction: Int): Boolean {
        editorActions += editorAction
        onEditorAction?.invoke(editorAction)
        return true
    }

    override fun performContextMenuAction(id: Int): Boolean = true
    override fun beginBatchEdit(): Boolean { batchDepth++; return true }
    override fun endBatchEdit(): Boolean { batchDepth = maxOf(0, batchDepth - 1); return true }
    override fun sendKeyEvent(event: KeyEvent): Boolean { keyEvents += event; return true }
    override fun clearMetaKeyStates(states: Int): Boolean = true
    override fun reportFullscreenMode(enabled: Boolean): Boolean = true
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
    override fun getHandler(): Handler? = null
    override fun closeConnection() {}
    override fun commitContent(
        inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?
    ): Boolean = false
}
