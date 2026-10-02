package com.hkey.app.service

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.hkey.app.R
import com.hkey.app.engine.ContextPredictor
import com.hkey.app.engine.TelexEngine

class HKeyIME : InputMethodService() {

    private val telexEngine = TelexEngine()
    private val predictor = ContextPredictor()

    private val currentComposingWord = StringBuilder()
    private var lastCommittedWord = ""

    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null

    private var lettersPage: View? = null
    private var symbolsPage: View? = null
    private var shiftOn = false
    private val letterKeys = mutableListOf<TextView>()
    private var shiftKey: TextView? = null

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val deleteRepeat = object : Runnable {
        override fun run() {
            handleDelete()
            repeatHandler.postDelayed(this, 60)
        }
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        letterKeys.clear()
        shiftKey = null

        candidate1 = root.findViewById(R.id.candidate1)
        candidate2 = root.findViewById(R.id.candidate2)
        candidate3 = root.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { acceptSuggestion(candidate1?.text.toString()) }
        candidate2?.setOnClickListener { acceptSuggestion(candidate2?.text.toString()) }
        candidate3?.setOnClickListener { acceptSuggestion(candidate3?.text.toString()) }

        val pages = root.findViewById<FrameLayout>(R.id.kb_pages)
        lettersPage = layoutInflater.inflate(R.layout.keyboard_letters, pages, false)
        symbolsPage = layoutInflater.inflate(R.layout.keyboard_symbols, pages, false)
        pages.addView(lettersPage)
        pages.addView(symbolsPage)
        symbolsPage?.visibility = View.GONE

        bindKeys(lettersPage)
        bindKeys(symbolsPage)
        updateSuggestions()

        return root
    }

    private fun pressFeedback(v: View) {
        v.playSoundEffect(SoundEffectConstants.CLICK)
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun bindKeys(root: View?) {
        forEachView(root) { view ->
            val tag = view.tag as? String ?: return@forEachView
            val tv = view as? TextView ?: return@forEachView
            when {
                tag.startsWith("ch:") -> {
                    letterKeys.add(tv)
                    tv.setOnClickListener { pressFeedback(tv); handleCharacter(tag.removePrefix("ch:")) }
                }
                tag.startsWith("p:") -> {
                    tv.setOnClickListener { pressFeedback(tv); handlePunct(tag.removePrefix("p:")) }
                }
                tag == "fn:space" -> tv.setOnClickListener { pressFeedback(tv); handleSpace() }
                tag == "fn:enter" -> tv.setOnClickListener { pressFeedback(tv); handleEnter() }
                tag == "fn:del" -> bindDeleteKey(tv)
                tag == "fn:shift" -> {
                    shiftKey = tv
                    tv.setOnClickListener { pressFeedback(tv); toggleShift() }
                }
                tag == "fn:sym" -> tv.setOnClickListener { pressFeedback(tv); showPage(symbolsPage) }
                tag == "fn:abc" -> tv.setOnClickListener { pressFeedback(tv); showPage(lettersPage) }
            }
        }
    }

    private fun forEachView(view: View?, block: (View) -> Unit) {
        if (view == null) return
        block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forEachView(view.getChildAt(i), block)
        }
    }

    private fun bindDeleteKey(tv: TextView) {
        tv.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressFeedback(v)
                    handleDelete()
                    repeatHandler.postDelayed(deleteRepeat, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatHandler.removeCallbacks(deleteRepeat)
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun showPage(page: View?) {
        lettersPage?.visibility = if (page === lettersPage) View.VISIBLE else View.GONE
        symbolsPage?.visibility = if (page === symbolsPage) View.VISIBLE else View.GONE
    }

    private fun toggleShift() {
        shiftOn = !shiftOn
        updateShiftUI()
    }

    private fun updateShiftUI() {
        for (key in letterKeys) {
            val base = (key.tag as String).removePrefix("ch:")
            key.text = if (shiftOn) base.uppercase() else base
        }
        shiftKey?.setTextColor(
            if (shiftOn) resources.getColor(R.color.kb_accent, theme)
            else resources.getColor(R.color.kb_text, theme)
        )
    }

    private fun handleCharacter(char: String) {
        val c = if (shiftOn) char.uppercase() else char
        currentComposingWord.append(c)
        if (shiftOn) {
            shiftOn = false
            updateShiftUI()
        }
        val transformed = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.setComposingText(transformed, 1)
        updateSuggestions()
    }

    private fun handlePunct(p: String) {
        commitComposing()
        currentInputConnection?.commitText(p, 1)
        updateSuggestions()
    }

    /** Chốt từ đang gõ nguyên trạng — không bao giờ tự thay bằng từ khác. */
    private fun commitComposing() {
        if (currentComposingWord.isEmpty()) return
        val word = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.commitText(word, 1)
        predictor.recordSequence(lastCommittedWord, word)
        lastCommittedWord = word
        currentComposingWord.clear()
    }

    private fun handleSpace() {
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
    }

    private fun handleDelete() {
        if (currentComposingWord.isNotEmpty()) {
            currentComposingWord.deleteCharAt(currentComposingWord.length - 1)
            val transformed = telexEngine.transform(currentComposingWord.toString())
            if (transformed.isEmpty()) {
                currentInputConnection?.commitText("", 1)
            } else {
                currentInputConnection?.setComposingText(transformed, 1)
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        updateSuggestions()
    }

    private fun handleEnter() {
        commitComposing()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
    }

    private fun acceptSuggestion(word: String) {
        if (word.isEmpty()) return
        // commitText tự thay thế vùng composing nếu đang gõ dở
        currentInputConnection?.commitText("$word ", 1)
        predictor.recordSequence(lastCommittedWord, word)
        lastCommittedWord = word
        currentComposingWord.clear()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (currentComposingWord.isNotEmpty()) {
            // Đang gõ: giữa = từ hiện tại, 2 bên = gợi ý hoàn thành
            val current = telexEngine.transform(currentComposingWord.toString())
            val completions = predictor.completions(current)
            candidate1?.text = completions.getOrNull(1) ?: ""
            candidate2?.text = completions.getOrNull(0) ?: current
            candidate3?.text = completions.getOrNull(2) ?: ""
        } else {
            // Đã chốt từ: giữa = bản sửa (nếu từ có vẻ sai), 2 bên = từ tiếp theo
            val next = predictor.predictNext(lastCommittedWord)
            val fix = predictor.correction(lastCommittedWord, null)
            candidate1?.text = next.getOrNull(1) ?: ""
            candidate2?.text = fix ?: next.getOrNull(0) ?: ""
            candidate3?.text = next.getOrNull(2) ?: ""
        }
    }
}
