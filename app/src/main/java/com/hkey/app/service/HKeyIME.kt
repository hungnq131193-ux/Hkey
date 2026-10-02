package com.hkey.app.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
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

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

        candidate1 = keyboardView.findViewById(R.id.candidate1)
        candidate2 = keyboardView.findViewById(R.id.candidate2)
        candidate3 = keyboardView.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { acceptSuggestion(candidate1?.text.toString()) }
        candidate2?.setOnClickListener { acceptSuggestion(candidate2?.text.toString()) }
        candidate3?.setOnClickListener { acceptSuggestion(candidate3?.text.toString()) }

        setupKeyClicks(keyboardView)
        updateSuggestions()

        return keyboardView
    }

    private fun setupKeyClicks(root: View) {
        val keys = listOf(
            R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e", R.id.key_r to "r",
            R.id.key_t to "t", R.id.key_y to "y", R.id.key_u to "u", R.id.key_i to "i",
            R.id.key_o to "o", R.id.key_p to "p", R.id.key_a to "a", R.id.key_s to "s",
            R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g", R.id.key_h to "h",
            R.id.key_j to "j", R.id.key_k to "k", R.id.key_l to "l", R.id.key_z to "z",
            R.id.key_x to "x", R.id.key_c to "c", R.id.key_v to "v", R.id.key_b to "b",
            R.id.key_n to "n", R.id.key_m to "m"
        )

        for ((id, char) in keys) {
            root.findViewById<Button>(id)?.setOnClickListener { handleCharacter(char) }
        }

        root.findViewById<Button>(R.id.key_space)?.setOnClickListener { handleSpace() }
        root.findViewById<Button>(R.id.key_delete)?.setOnClickListener { handleDelete() }
        root.findViewById<Button>(R.id.key_enter)?.setOnClickListener { handleEnter() }
    }

    private fun handleCharacter(char: String) {
        currentComposingWord.append(char)
        val transformed = telexEngine.transform(currentComposingWord.toString())
        currentInputConnection?.setComposingText(transformed, 1)
        updateSuggestions()
    }

    private fun handleSpace() {
        if (currentComposingWord.isNotEmpty()) {
            val rawWord = telexEngine.transform(currentComposingWord.toString())
            // Sửa lỗi ngữ cảnh tự động
            val corrected = predictor.autoCorrect(rawWord, lastCommittedWord)
            currentInputConnection?.commitText("$corrected ", 1)

            // Dynamic learning: Ghi nhận chuỗi từ người dùng gõ
            predictor.recordSequence(lastCommittedWord, corrected)
            lastCommittedWord = corrected
            currentComposingWord.clear()
        } else {
            currentInputConnection?.commitText(" ", 1)
        }
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
        if (currentComposingWord.isNotEmpty()) {
            val rawWord = telexEngine.transform(currentComposingWord.toString())
            currentInputConnection?.commitText(rawWord, 1)
            currentComposingWord.clear()
        }
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
        )
    }

    private fun acceptSuggestion(word: String) {
        if (word.isEmpty()) return
        currentInputConnection?.commitText("$word ", 1)
        predictor.recordSequence(lastCommittedWord, word)
        lastCommittedWord = word
        currentComposingWord.clear()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val suggestions = predictor.predictNext(lastCommittedWord)
        candidate1?.text = suggestions.getOrNull(0) ?: ""
        candidate2?.text = suggestions.getOrNull(1) ?: ""
        candidate3?.text = suggestions.getOrNull(2) ?: ""
    }
}
