package com.hkey.app.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hkey.app.BuildConfig
import com.hkey.app.R
import com.hkey.app.engine.ContextPredictor
import com.hkey.app.engine.EngineOptions
import com.hkey.app.engine.FieldMode
import com.hkey.app.engine.ImeEngine
import com.hkey.app.engine.ImeMethod
import com.hkey.app.engine.LearningStore
import com.hkey.app.engine.TelexEngine
import com.hkey.app.engine.VniEngine
import com.hkey.app.engine.TextContext
import com.hkey.app.engine.ViModelBin
import com.hkey.app.engine.ViSyllable
import com.hkey.app.settings.SettingsKeys
import com.hkey.app.settings.SettingsMigration
import com.hkey.app.ui.KeyboardView
import com.hkey.app.ui.KbKey
import com.hkey.app.ui.KbField
import com.hkey.app.ui.KbThemes
import java.io.File

class HKeyIME : InputMethodService() {

    private var engine: ImeEngine = TelexEngine()
    private var engineSig: List<Any?>? = null // prefs quyết định engine (6f)
    private val predictor = ContextPredictor()

    private val currentComposingWord = StringBuilder()
    private var lastCommittedWord = ""
    private var lastCommitWasRaw = false // 1.4.0: từ vừa chốt là phím thô (tiếng Anh) — chặn resume

    /** Từ vừa bị auto-correct đổi: committed = từ app ghi, typed = từ user gõ,
     *  raw = buffer phím thô để mở lại vùng composing khi hoàn tác. */
    private class AutoFix(val committed: String, val typed: String, val raw: String)
    private var lastAutoFix: AutoFix? = null

    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null

    private var kbView: KeyboardView? = null
    private var shiftOn = false
    private var shiftAuto = false
    private var shiftLocked = false // 2 chạm Shift = caps lock (3.x)
    private var lastShiftTap = 0L
    private var autoCap = false

    private var inputView: View? = null
    private var appliedKbHeight = -1
    private var appliedKbSide = -1
    private var appliedNumRow = false
    private var appliedUiSig = "" // 1.3.2: theme+kiểu phím đang áp — đổi -> inflate lại
    private var optSound = true
    private var optLiveRestore = true
    private var optVibrate = true
    private var optVibrateMs = 20 // P4: VIBRATE_STRENGTH
    private var optSoundVol = 50  // P4: SOUND_VOLUME
    private var optAutoCorrect = true   // U3: AUTO_CORRECT
    private var optSuggestions = true   // U3: SUGGESTIONS
    private var optAutoCap = true       // U3: AUTO_CAP
    private var optSpaceSwipe = true    // U3: SPACE_SWIPE
    private var optLongpressMs = 360    // U3: LONGPRESS_MS
    private var optHwKeyboard = true    // H1: HW_KEYBOARD — cache, khỏi đọc prefs mỗi key event
    private var contextCache: Pair<String, String>? = null // (từ trước, từ trước nữa)
    private val tailTracker = TailTracker(40) // bản sao cục bộ text đã commit — cắt IPC (6b)
    private var currentInputType = 0 // inputType của ô đang focus (cờ CAP_* cho capsWanted)
    private var currentImeOptions = 0 // imeOptions ô đang focus (action Enter — 1.5)
    private var tokenGlued = false // từ đang gõ dính sau . @ / : — url/email/ip (1.1)
    private var rawMode = false // ô nhạy cảm: gõ thẳng, không Telex/gợi ý/học (B1,B2)
    private var noSuggest = false // NO_SUGGESTIONS: Telex vẫn gõ, tắt gợi ý+sửa (1.4)
    private var noLearning = false // NO_PERSONALIZED_LEARNING/NO_SUGGESTIONS: không học (1.4)
    private var vietMode = true // EN/VI qua phím lang (2.x); EN = gõ thẳng
    private var macros = emptyMap<String, String>() // gõ tắt "k=v" mỗi dòng (2.x)
    private var optDoubleSpace = true // 3.x: space-space nhanh -> ". "
    private var lastSpaceTap = 0L
    // 1.2: vùng chọn hiện tại (onUpdateSelection) — dời con trỏ bằng
    // setSelection thay vì phím DPAD (DPAD ở cuối ô làm focus nhảy sang view
    // khác -> bàn phím bị ẩn), và ⌫ xoá đúng vùng đang bôi chọn.
    private var selStart = -1
    private var selEnd = -1
    // 1.3.4: hàng vị trí con trỏ mà các edit của chính ta sẽ tạo — update
    // từ app khớp một phần tử trong hàng là update do ta gây ra, kể cả khi
    // đến TRỄ sau khi đã gõ chữ mới (sửa race commit-trước nuốt từ đang
    // gõ: "d"+"d" không ra "đ"). selStart/selEnd giờ là con trỏ DỰ KIẾN
    // (cập nhật ngay khi edit, không chờ update).
    private val expectedSels = ExpectedSels()
    private var shownComposingLen = 0 // độ dài text đang hiển thị ở vùng composing
    private var currentDisplay = "" // 1.4.0: text đang ở vùng composing (sau live restore có thể = raw)
    private var currentField = KbField.TEXT
    @Volatile private var destroyed = false

    private val repeatHandler = Handler(Looper.getMainLooper())

    // 1.4.0 (P1/C1): mọi thao tác predictor chỉ chạy trên worker này.
    // Tạo trong onCreate qua workerPosterOverride (test inject) hoặc
    // HandlerThread mặc định. Trước onCreate rơi về đồng bộ.
    private var suggestWorker: SuggestWorker? = null
    private val worker: SuggestWorker get() = suggestWorker ?: SYNC_WORKER
    @Volatile private var predictorReady = false // P5: index đã install
    // 1.4.5: tập từ phổ biến cho cổng phím lệ thường Telex — publish trên
    // worker cùng index; đọc trên main thread qua EngineOptions.commonWord.
    // 1.5.3: kèm tần suất để phân giải vần mơ hồ (uo -> uô/ươ).
    @Volatile private var commonFreqs: Map<String, Int> = emptyMap()
    @Volatile private var suggestGen = 0 // số hiệu request gợi ý — kết quả cũ bị bỏ

    private val prefs get() = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
    // 1.4.0 (S2): đổi cài đặt áp ngay không cần restart — giữ field chống GC,
    // đăng ký ở onCreate / huỷ ở onDestroy.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        onPrefChanged(key)
    }
    /** Key ảnh hưởng giao diện bàn phím -> inflate lại ngay nếu đang hiện. */
    private val uiPrefKeys = setOf(
        SettingsKeys.KB_THEME, SettingsKeys.DARK_THEME_LEGACY,
        SettingsKeys.KEY_SHAPE, SettingsKeys.KB_HEIGHT, SettingsKeys.KB_SIDE,
        SettingsKeys.NUMBER_ROW
    )
    private val learnedFile get() = File(filesDir, "learned_data.tsv")
    private val learnedStore get() = LearningStore(learnedFile)
    private var learnedDirty = false
    @Volatile private var learnedGen = 0 // tăng khi "xóa dữ liệu học" — hủy ghi đang bay (1.10)
    private val saveLearned = Runnable { persistLearned() }

    override fun onCreate() {
        super.onCreate()
        SettingsMigration.run(prefs) // 1.4.0: kb_theme từ dark_theme cũ
        prefs.registerOnSharedPreferenceChangeListener(prefListener) // S2
        suggestWorker = workerPosterOverride?.let { SuggestWorker(it, {}) }
            ?: SuggestWorker.handlerThread()
        // Nạp từ điển + mô hình n-gram + dữ liệu học rồi dựng chỉ mục —
        // toàn bộ trên worker: predictor không bị chạm từ luồng khác (P1).
        worker.post {
            val words = resources.openRawResource(R.raw.vi_dict)
                .bufferedReader().use { it.lineSequence().toList() }
            // 4.x: model nhị phân (mmap nếu asset không nén) — không parse
            // string, không phình heap; thiếu/hỏng thì chạy lớp seed.
            val packed = loadModelPacked()
            val phrases = try {
                resources.openRawResource(R.raw.vi_phrases)
                    .bufferedReader().use { it.lineSequence().toList() }
            } catch (e: Exception) { emptyList() }
            val learned = learnedStore.load()
            if (destroyed) return@post
            predictor.addWords(words)
            if (packed != null) predictor.loadPacked(packed)
            predictor.addPhrases(phrases.mapNotNull { l ->
                val t = l.trim()
                val i = t.indexOf(' ')
                if (i <= 0) null else t.substring(0, i) to t.substring(i + 1).trim()
            })
            if (learned != null) {
                predictor.importLearned(learned.words, learned.bigrams, learned.trigrams)
            }
            val (snapshot, version) = predictor.snapshotForIndex()
            val idx = predictor.buildIndexFrom(snapshot)
            if (destroyed) return@post
            predictor.installIndex(idx, version)
            commonFreqs = predictor.commonWordFreqs() // 1.4.5/1.5.3: cổng phím lệ thường + rank
            repeatHandler.post { predictorReady = true }
        }
    }

    override fun onDestroy() {
        destroyed = true
        suggestGen++
        applyCandidates(CandidateSet("", "", ""))
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener) // S2
        kbView?.release() // dọn repeat/popup của view
        repeatHandler.removeCallbacks(saveLearned)
        persistLearned() // post ghi theo lô lên worker trước khi quit (3.6)
        suggestWorker?.quitSafely() // P1: xử lý hết hàng đợi rồi thoát
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finalizeGuarded(currentInputConnection) // 1.4.0: chốt từ đang gõ dở
        kbView?.release() // 1.10: ẩn phím -> dừng nhấn giữ ⌫, đóng popup
        persistLearned() // mất focus -> ghi luôn nếu bẩn
        super.onFinishInputView(finishingInput)
    }

    private fun finalizeGuarded(ic: android.view.inputmethod.InputConnection?) {
        if (currentComposingWord.isEmpty() || ic == null) {
            finalizeWord(ic)
            return
        }
        val et = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(), 0
        )
        val t = et?.text
        val end = selEnd - (et?.startOffset ?: 0)
        val start = end - shownComposingLen
        if (t != null && shownComposingLen > 0 && start >= 0 && end <= t.length &&
            t.regionMatches(start, currentDisplay, 0, shownComposingLen)
        ) {
            finalizeWord(ic)
        } else {
            discardComposing()
            tailTracker.invalidate()
        }
    }

    // -------------------------------------------------- H1: phím cứng

    /** keyCode đã tiêu thụ ở onKeyDown — nuốt luôn onKeyUp tương ứng để
     *  app không nhận nửa cặp phím. */
    private val hwConsumed = mutableSetOf<Int>()

    /** Điều kiện chung xử lý phím cứng: bật trong settings, có InputConnection,
     *  không rawMode, sự kiện từ phím vật lý thật (không phím hệ thống/ảo). */
    private fun hwGate(event: KeyEvent): Boolean =
        optHwKeyboard &&
            currentInputConnection != null && !rawMode &&
            HardwareKeys.usable(event)

    override fun onEvaluateInputViewShown(): Boolean =
        // Framework tự ẩn bàn phím mềm khi Configuration.keyboard khác
        // NOKEYS và người dùng tắt "hiện bàn phím ảo" — giữ nguyên super.
        super.onEvaluateInputViewShown()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!hwGate(event)) return super.onKeyDown(keyCode, event)
        // Đổi ngôn ngữ EN/VI: Ctrl+Space hoặc Shift+Space (kiểm trước meta-
        // skip vì Ctrl+Space mang META_CTRL).
        if (keyCode == KeyEvent.KEYCODE_SPACE &&
            (event.isCtrlPressed || event.isShiftPressed)
        ) {
            if (event.repeatCount == 0) toggleLang()
            hwConsumed += keyCode
            return true
        }
        if (event.metaState and HardwareKeys.SKIP_META != 0) {
            return super.onKeyDown(keyCode, event) // Ctrl+C/V/A, Alt+Tab...
        }
        if (event.repeatCount > 0) {
            // Auto-repeat: chỉ lặp lại hành động cho Del và chữ; phím đã
            // nuốt khác nuốt yên; phím chưa nuốt trả app.
            if (keyCode !in hwConsumed) return super.onKeyDown(keyCode, event)
            when (val k = HardwareKeys.classify(event)) {
                is HardwareKeys.Key.Char -> handleCharacter(k.c, fromHardware = true)
                HardwareKeys.Key.Del -> handleDelete()
                else -> {}
            }
            return true
        }
        val consumed = when (val k = HardwareKeys.classify(event)) {
            is HardwareKeys.Key.Char -> {
                handleCharacter(k.c, fromHardware = true); true
            }
            is HardwareKeys.Key.Digit -> { handlePunct(k.c); true }
            is HardwareKeys.Key.Punct -> { handlePunct(k.s); true }
            HardwareKeys.Key.Space -> { handleSpace(); true }
            HardwareKeys.Key.Enter -> { handleEnter(); true }
            HardwareKeys.Key.Del -> { handleDelete(); true }
            HardwareKeys.Key.ForwardDel, HardwareKeys.Key.Arrow -> {
                // Chốt từ rồi trả app di chuyển/xoá tới; con trỏ đã đổi chỗ
                commitComposing(); cursorUnknown(); false
            }
            HardwareKeys.Key.Tab, HardwareKeys.Key.Esc,
            HardwareKeys.Key.Other -> { commitComposing(); false }
        }
        if (consumed) {
            hwConsumed += keyCode
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (hwConsumed.remove(keyCode)) true else super.onKeyUp(keyCode, event)

    override fun onFinishInput() {
        // 1.4.0: đổi ô nhập khi đang gõ dở -> chốt từ tại chỗ cũ trước
        if (currentComposingWord.isNotEmpty()) finalizeGuarded(currentInputConnection)
        super.onFinishInput()
    }

    /** 1.10: không cho phép fullscreen/extract mode — bàn phím luôn ở đáy
     *  màn hình kể cả landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** S2: cài đặt vừa đổi — đánh dấu bẩn chữ ký (engine/UI dựng lại ở
     *  onStartInputView tới), đọc lại cờ rẻ ngay, và inflate lại view luôn
     *  khi key thuộc nhóm giao diện còn bàn phím đang hiện. Không inflate
     *  trong listener cho key khác — inflate nặng, chờ onStartInputView. */
    private fun onPrefChanged(key: String?) {
        engineSig = null
        appliedUiSig = ""
        optSound = prefs.getBoolean(SettingsKeys.SOUND, true)
        optLiveRestore = prefs.getBoolean(SettingsKeys.LIVE_RESTORE, true)
        optVibrate = prefs.getBoolean(SettingsKeys.VIBRATE, true)
        optDoubleSpace = prefs.getBoolean(SettingsKeys.DOUBLE_SPACE, true)
        optVibrateMs = prefs.getInt(SettingsKeys.VIBRATE_STRENGTH, 20)
        optSoundVol = prefs.getInt(SettingsKeys.SOUND_VOLUME, 50)
        optAutoCorrect = prefs.getBoolean(SettingsKeys.AUTO_CORRECT, true)
        optSuggestions = prefs.getBoolean(SettingsKeys.SUGGESTIONS, true)
        optAutoCap = prefs.getBoolean(SettingsKeys.AUTO_CAP, true)
        optSpaceSwipe = prefs.getBoolean(SettingsKeys.SPACE_SWIPE, true)
        optLongpressMs = prefs.getInt(SettingsKeys.LONGPRESS_MS, 360)
        optHwKeyboard = prefs.getBoolean(SettingsKeys.HW_KEYBOARD, true)
        kbView?.let {
            it.soundEnabled = optSound
            it.vibrateEnabled = optVibrate
            it.hapticMs = optVibrateMs
            it.soundVolume = optSoundVol
            it.longPressMs = optLongpressMs.toLong()
        }
        if (key in uiPrefKeys && (isInputViewShown || inputView != null)) {
            repeatHandler.post { if (!destroyed) setInputView(onCreateInputView()) }
        }
    }

    /** mmap vi_model.bin nếu asset được lưu không nén (noCompress), fallback
     *  đọc stream thường; null nếu file thiếu/hỏng (4.x). */
    private fun loadModelPacked(): ViModelBin.Packed? {
        try {
            resources.openRawResourceFd(R.raw.vi_model)?.use { afd ->
                afd.createInputStream().channel.use { ch ->
                    val buf = ch.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        afd.startOffset, afd.declaredLength
                    )
                    ViModelBin.read(buf)?.let { return it }
                }
            }
        } catch (e: Exception) { /* fd không parcelable (asset nén) -> stream */ }
        return try {
            resources.openRawResource(R.raw.vi_model).use {
                ViModelBin.read(java.nio.ByteBuffer.wrap(it.readBytes()))
            }
        } catch (e: Exception) { null }
    }

    /** Đánh dấu dữ liệu học bẩn + hẹn ghi sau 15s (batch, không ghi mỗi phím). */
    private fun markLearnedDirty() {
        learnedDirty = true
        repeatHandler.removeCallbacks(saveLearned)
        repeatHandler.postDelayed(saveLearned, 15_000)
    }

    private fun persistLearned() {
        if (!learnedDirty) return
        learnedDirty = false
        val gen = learnedGen
        val store = learnedStore
        // 1.10: "xóa dữ liệu học" xảy ra giữa chừng -> hủy ghi snapshot cũ
        // P1: export + sort + ghi file đều trên worker — một luồng chạm
        // predictor, hàng đợi FIFO giữ thứ tự với clearLearned.
        worker.post {
            predictor.boundLearned(LearningStore.MAX_LEARNED_WORDS)
            val (words, bis, tris) = predictor.exportLearned()
            val cappedBis = if (bis.size > LearningStore.MAX_USER_BIGRAMS)
                bis.sortedByDescending { it.third }
                    .take(LearningStore.MAX_USER_BIGRAMS)
            else bis
            val cappedTris = if (tris.size > LearningStore.MAX_USER_TRIGRAMS)
                tris.sortedByDescending { it.count }
                    .take(LearningStore.MAX_USER_TRIGRAMS)
            else tris
            if (gen == learnedGen) {
                store.save(LearningStore.Data(words, cappedBis, cappedTris))
            }
        }
    }

    /** Nút "Xóa dữ liệu học" ở MainActivity đặt cờ; IME tiêu thụ ở lần focus
     *  ô / hiện bàn phím kế tiếp (3.6). */
    private fun consumeLearningCleared() {
        if (prefs.getBoolean(SettingsKeys.LEARNING_CLEARED, false)) {
            prefs.edit().remove(SettingsKeys.LEARNING_CLEARED).apply()
            learnedGen++ // 1.10: vô hiệu mọi ghi learned đang chạy nền
            learnedDirty = false
            repeatHandler.removeCallbacks(saveLearned)
            val store = learnedStore
            worker.post {
                predictor.clearLearned()
                store.clear()
            }
        }
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        fixGen++ // 1.5.0: đổi ô -> huỷ bản sửa nền đang bay, không sửa lùi ô mới
        rawMode = FieldMode.isRaw(info.inputType)
        noSuggest = FieldMode.noSuggestions(info.inputType)
        noLearning = noSuggest ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        currentInputType = info.inputType
        currentImeOptions = info.imeOptions
        var resumeLen = 0
        if (restarting && currentComposingWord.isNotEmpty()) {
            val display = currentDisplay
            val before = if (display.isEmpty()) null else
                currentInputConnection?.getTextBeforeCursor(display.length, 0)
            if (info.initialSelStart == selEnd && info.initialSelEnd == selEnd &&
                before?.endsWith(display) == true
            ) {
                resumeLen = display.length
            } else {
                discardComposing()
                tailTracker.invalidate()
            }
        } else if (!restarting) {
            discardComposing()
            tailTracker.invalidate()
        }
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        expectedSels.clear()
        shownComposingLen = resumeLen
        currentField = fieldOf(info.inputType)
        computeAutoCap(info)
        consumeLearningCleared() // tiêu thụ sớm ngay khi focus ô, không chờ view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        val t0 = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        super.onStartInputView(info, restarting)
        currentInputType = info.inputType
        currentImeOptions = info.imeOptions
        // 2.x: dựng lại engine theo prefs (kiểu gõ + kiểu dấu + spell-check)
        // 6f: chỉ dựng lại khi chữ ký prefs đổi — không phải mỗi lần focus.
        val opts = EngineOptions(
            method = ImeMethod.fromPref(prefs.getString(SettingsKeys.METHOD, null)),
            newToneStyle = prefs.getBoolean(SettingsKeys.TONE_NEW, true),
            spellCheckTone = prefs.getBoolean(SettingsKeys.SPELL_CHECK, true),
            // 1.4.5: đọc live — set rỗng trước khi model nạp xong thì mọi
            // phím lệ thường đều giữ nguyên (hành vi cũ, an toàn).
            commonWord = { w -> commonFreqs.containsKey(w) },
            commonRank = { w -> commonFreqs[w] ?: 0 }
        )
        val macroStr = prefs.getString(SettingsKeys.MACROS, "") ?: ""
        val sig = listOf(opts.method, opts.newToneStyle, opts.spellCheckTone, macroStr)
        if (sig != engineSig) {
            engineSig = sig
            engine = if (opts.method == ImeMethod.VNI) VniEngine(opts) else TelexEngine(opts)
            macros = parseMacros(macroStr)
        }
        if (!restarting) {
            currentComposingWord.clear()
            lastCommittedWord = ""
            lastAutoFix = null
            contextCache = null
            tokenGlued = false
            tailTracker.invalidate()
            expectedSels.clear()
            shownComposingLen = 0
        }
        computeAutoCap(info)
        shiftOn = false
        shiftAuto = false
        shiftLocked = false
        consumeLearningCleared()
        optSound = prefs.getBoolean(SettingsKeys.SOUND, true)
        optLiveRestore = prefs.getBoolean(SettingsKeys.LIVE_RESTORE, true)
        optVibrate = prefs.getBoolean(SettingsKeys.VIBRATE, true)
        optDoubleSpace = prefs.getBoolean(SettingsKeys.DOUBLE_SPACE, true)
        optVibrateMs = prefs.getInt(SettingsKeys.VIBRATE_STRENGTH, 20)
        optSoundVol = prefs.getInt(SettingsKeys.SOUND_VOLUME, 50)
        optAutoCorrect = prefs.getBoolean(SettingsKeys.AUTO_CORRECT, true)
        optSuggestions = prefs.getBoolean(SettingsKeys.SUGGESTIONS, true)
        optAutoCap = prefs.getBoolean(SettingsKeys.AUTO_CAP, true)
        optSpaceSwipe = prefs.getBoolean(SettingsKeys.SPACE_SWIPE, true)
        optLongpressMs = prefs.getInt(SettingsKeys.LONGPRESS_MS, 360)
        optHwKeyboard = prefs.getBoolean(SettingsKeys.HW_KEYBOARD, true)
        // Chỉ inflate lại khi đổi settings hoặc chưa có view — S2.
        val kh = prefs.getInt(SettingsKeys.KB_HEIGHT, 100)
        val ks = prefs.getInt(SettingsKeys.KB_SIDE, 0)
        val kn = prefs.getBoolean(SettingsKeys.NUMBER_ROW, false)
        val uiSig = uiSignature()
        currentField = fieldOf(info.inputType)
        if (inputView == null || kh != appliedKbHeight || ks != appliedKbSide ||
            kn != appliedNumRow || uiSig != appliedUiSig
        ) {
            appliedNumRow = kn
            appliedUiSig = uiSig
            setInputView(onCreateInputView())
            kbView?.showPage(startPage(info.inputType))
        } else {
            kbView?.fieldKind = currentField
            kbView?.showPage(startPage(info.inputType))
            updateSuggestions()
        }
        // View tái sử dụng không qua onCreateInputView -> đẩy cờ mới xuống
        // mỗi lần hiện (sửa: tắt rung/âm vẫn còn hiệu lực tới khi inflate lại).
        kbView?.let {
            it.soundEnabled = optSound
            it.vibrateEnabled = optVibrate
            it.hapticMs = optVibrateMs
            it.soundVolume = optSoundVol
            it.longPressMs = optLongpressMs.toLong()
            it.langVi = vietMode
            it.enterAction = enterActionOf(info.inputType, info.imeOptions)
        }
        updateAutoShift()
        if (BuildConfig.DEBUG) Log.d("HKeyIME", "onStartInputView ${(System.nanoTime() - t0) / 1_000_000.0} ms")
    }

    /** 1.2: ô số/điện thoại/ngày giờ mở thẳng trang số-ký hiệu. */
    private fun startPage(inputType: Int): KeyboardView.Page =
        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> KeyboardView.Page.SYMBOLS
            else -> KeyboardView.Page.LETTERS
        }

    /** 1.2: loại ô -> phím cạnh ?123 ("/" cho URL, "@" cho email). */
    private fun fieldOf(inputType: Int): KbField {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return KbField.TEXT
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_URI -> KbField.URL
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> KbField.EMAIL
            else -> KbField.TEXT
        }
    }

    private fun isMultiLine(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and (InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0

    /** 1.2: action Enter hiệu lực — dùng chung cho icon và hành vi phím.
     *  NONE = xuống dòng. Ô nhiều dòng: chỉ gọi action khi app yêu cầu rõ
     *  (send/search/go/next/previous); DONE/UNSPECIFIED ở ô nhiều dòng là
     *  mặc định của framework -> xuống dòng, không đóng bàn phím. */
    private fun enterActionOf(inputType: Int, imeOptions: Int): Int {
        if (inputType == InputType.TYPE_NULL) return EditorInfo.IME_ACTION_NONE
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            return EditorInfo.IME_ACTION_NONE
        }
        if (isMultiLine(inputType) && action == EditorInfo.IME_ACTION_DONE) {
            return EditorInfo.IME_ACTION_NONE
        }
        return action
    }

    // Tự viết hoa chỉ bật ở ô text thường — không bật ở mật khẩu/email/url
    private fun computeAutoCap(info: EditorInfo) {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        autoCap = optAutoCap &&
            info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI
    }

    /** Học chuỗi từ — bỏ qua khi app đặt NO_PERSONALIZED_LEARNING hoặc
     *  NO_SUGGESTIONS (1.4). 4.x: không học từ trông như typo (lệch 1 ký tự
     *  so với từ điển, không phải âm tiết VN, chưa từng biết).
     *  1.3: truyền thêm từ trước nữa (prev2) để học trigram cá nhân. */
    /** [force]=true: user chủ động xác nhận từ (hoàn tác auto-fix) — bỏ
     *  qua bộ lọc looksLikeTypo để từ được học thật, không bị sửa lại. */
    private fun learn(prev: String, word: String, prev2: String = "", force: Boolean = false) {
        // P1: recordSequence chạy trên worker — main chỉ gửi việc.
        worker.post {
            if (noLearning || (!force && predictor.looksLikeTypo(word))) return@post
            predictor.recordSequence(prev, word, prev2 = prev2)
            // 1.5.1: từ vừa học phải mở cổng phím lệ thường ngay — commonSet
            // chỉ dựng lúc nạp dict; thiếu bước này thì "max" vừa chạm vẫn
            // bị bẻ thành "mã" ở lần gõ sau (điều kiện recordSequence giống
            // nhau: chỉ chữ cái, độ dài hợp lý).
            val w = word.lowercase().trim()
            if (w.isNotEmpty() && w.length <= 24 && w.all { it.isLetter() }) {
                repeatHandler.post { commonFreqs = commonFreqs + (w to Int.MAX_VALUE) }
            }
            markLearnedDirty()
        }
    }

    /** Ghi nhận con trỏ dự kiến sau một edit của chính ta: thu vùng chọn về
     *  1 điểm và xếp pos vào hàng expected (update tới khớp = do ta gây ra). */
    private fun noteCursor(pos: Int) {
        selStart = pos
        selEnd = pos
        expectedSels.push(pos)
    }

    /** 1.4.0 (E2): text hiển thị vùng composing — live restore giữ phím thô
     *  khi transform ra chuỗi có glyph VN mà không phải âm tiết hợp lệ
     *  ("window" gõ tiếp không hiện "windoư"); tokenGlued (url/email) không
     *  áp. Mọi điểm hiển thị đi qua đây để đồng nhất. */
    private fun renderComposing(): String {
        val raw = currentComposingWord.toString()
        val t = engine.transform(raw)
        return if (optLiveRestore && !tokenGlued &&
            ViSyllable.liveRestorable(raw, t)
        ) raw else t
    }

    /** Điểm bắt đầu vùng text mới sẽ thay: có composing thì là đầu vùng
     *  composing, không thì là đầu vùng chọn. */
    private fun replaceBase() =
        if (shownComposingLen > 0) selEnd - shownComposingLen else selStart

    private fun icCommit(ic: android.view.inputmethod.InputConnection, t: String) {
        val base = replaceBase()
        ic.commitText(t, 1)
        shownComposingLen = 0
        currentDisplay = ""
        noteCursor(base + t.length)
    }

    private fun icComposing(ic: android.view.inputmethod.InputConnection, t: String) {
        val base = replaceBase()
        ic.setComposingText(t, 1)
        shownComposingLen = t.length
        currentDisplay = t
        noteCursor(base + t.length)
    }

    /** Xoá n ký tự trước con trỏ (chỉ gọi khi không còn vùng chọn/composing). */
    private fun icDeleteBack(ic: android.view.inputmethod.InputConnection, n: Int) {
        ic.deleteSurroundingText(n, 0)
        noteCursor(selStart - n)
    }

    /** Edit không đoán được con trỏ (phím cứng/action app): hàng expected
     *  không còn tin được — update kế tiếp coi như thật. */
    private fun cursorUnknown() {
        expectedSels.clear()
        selStart = -1
        selEnd = -1
        shownComposingLen = 0
    }

    private fun discardComposing() {
        currentComposingWord.clear()
        lastCommittedWord = ""
        lastCommitWasRaw = false
        lastAutoFix = null
        tokenGlued = false
        contextCache = null
        tailTracker.invalidate()
        shownComposingLen = 0
        currentDisplay = ""
        fixGen++
        suggestGen++
        applyCandidates(CandidateSet("", "", ""))
    }

    /** Con trỏ bị đổi chỗ (chạm chỗ khác/bôi chọn): chốt từ đang gõ, xoá buffer
     *  để phím xoá và ký tự tiếp theo tác động đúng vị trí mới. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // 1.3.4: update khớp một vị trí edit của chính ta (kể cả đến trễ,
        // lệch thứ tự) hoặc không đổi gì -> bỏ qua; chỉ lệch thật mới là
        // con trỏ bị dời.
        val expected = expectedSels.isSelf(newSelStart, newSelEnd, selStart, selEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        // 1.6: update do chính setComposingText của ta gây ra (con trỏ nằm đúng
        // cuối vùng composing) -> ngữ cảnh trước từ không đổi, giữ cache.
        val selfEdit = expected || (currentComposingWord.isNotEmpty() &&
            newSelStart == newSelEnd && candidatesStart >= 0 && newSelStart == candidatesEnd)
        if (currentComposingWord.isNotEmpty()) {
            val d = currentDisplay
            val textMismatch = candidatesStart < 0 && d.isNotEmpty() &&
                currentInputConnection?.getTextBeforeCursor(d.length, 0)
                    ?.endsWith(d) == false
            if (!selfEdit || textMismatch) {
                if (candidatesStart < 0) {
                    discardComposing()
                } else {
                    finalizeWord(currentInputConnection) // 1.4.0: chốt từ tại chỗ cũ, không sửa
                    requestSuggestions()
                }
            }
        }
        if (!selfEdit) { // con trỏ/ngữ cảnh đổi thật mới vô hiệu cả tail-cache
            contextCache = null
            tailTracker.invalidate()
            fixGen++
            expectedSels.clear()
            shownComposingLen = 0
            clearStaleCandidates() // 1.5.1: ngữ cảnh đổi -> xoá ứng viên cũ ngay
        }
        updateAutoShift()
    }

    /** Viết hoa theo cờ CAP_* của ô — tính từ tail-cache cục bộ, không còn
     *  gọi getCursorCapsMode/getTextBeforeCursor (IPC) mỗi phím (6b). */
    private fun capsWanted(t: CharSequence?): Boolean {
        val flags = currentInputType and InputType.TYPE_MASK_FLAGS
        return when {
            flags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> true
            flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 ->
                t.isNullOrEmpty() || t.last().isWhitespace()
            // CAP_SENTENCES hoặc ô không đặt cờ: tự soi ranh giới câu
            else -> TextContext.sentenceBoundary(t)
        }
    }

    /** Viết hoa đầu câu theo cờ CAP_* của ô (1.1). Ô không đặt CAP_SENTENCES
     *  (text trơn): tự soi — dấu . ! ? … phải có khoảng trắng theo sau mới là
     *  kết câu ("hu.io.vn" không bật hoa). Bật/tắt hai chiều: shift tự động
     *  tắt khi ngữ cảnh hết cần hoa; shift do người dùng bấm tay không bị
     *  ghi đè. */
    private fun updateAutoShift() {
        if (!autoCap || currentComposingWord.isNotEmpty() || shiftLocked) return
        val wantCap = capsWanted(tailNow())
        val newShift = wantCap || (shiftOn && !shiftAuto)
        if (newShift != shiftOn || wantCap != shiftAuto) {
            shiftOn = newShift
            shiftAuto = wantCap
            updateShiftUI()
        }
    }

    private fun isNight() = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** 1.3.2: theme đã chọn ("system" = theo máy); bản cũ chỉ có dark_theme
     *  -> prefId chuyển cờ cũ thành "dark"/"light". */
    private fun themeId() = KbThemes.prefId(
        prefs.getString(SettingsKeys.KB_THEME, null),
        prefs.getBoolean(SettingsKeys.DARK_THEME_LEGACY, true))

    private fun currentPalette() = KbThemes.palette(themeId(), isNight())

    private fun currentCornerDp() =
        KbThemes.cornerDp(prefs.getString(SettingsKeys.KEY_SHAPE, "medium"))

    /** Chữ ký giao diện đang áp (theme đã resolve + kiểu phím). */
    private fun uiSignature() =
        KbThemes.resolveId(themeId(), isNight()) + "|" +
            (prefs.getString(SettingsKeys.KEY_SHAPE, "medium") ?: "medium")

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        // 1.10: targetSdk 35 ép edge-to-edge — độn đáy bằng thanh điều hướng
        // gesture/3-button để hàng phím cuối không bị che.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            if (v.paddingBottom != nav.bottom) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav.bottom)
            }
            insets
        }

        candidate1 = root.findViewById(R.id.candidate1)
        candidate2 = root.findViewById(R.id.candidate2)
        candidate3 = root.findViewById(R.id.candidate3)

        candidate1?.setOnClickListener { onCandidateTap(candidate1) }
        candidate2?.setOnClickListener { onCandidateTap(candidate2) }
        candidate3?.setOnClickListener { onCandidateTap(candidate3) }

        // 3.x: bàn phím tự vẽ — hit theo ô không rớt khe, trượt/đa chạm,
        // nhấn giữ ra phụ, giữ ⌫ lặp, vuốt space dời con trỏ.
        val kh = prefs.getInt(SettingsKeys.KB_HEIGHT, 100)
        val ks = prefs.getInt(SettingsKeys.KB_SIDE, 0)
        appliedKbHeight = kh
        appliedKbSide = ks
        // 1.5.1: đọc lại pref — onPrefChanged inflate lại khi bàn phím đang
        // hiện mà không qua onStartInputView, appliedNumRow cũ làm hàng số
        // không đổi dù đã bật/tắt trong cài đặt.
        appliedNumRow = prefs.getBoolean(SettingsKeys.NUMBER_ROW, false)
        val kb = KeyboardView(this).apply {
            fieldKind = currentField
            recentEmoji = (prefs.getString(SettingsKeys.RECENT_EMOJI, "") ?: "")
                .split('\n').filter { it.isNotEmpty() }
            configure(kh, ks, currentPalette(), appliedNumRow, currentCornerDp())
            soundEnabled = optSound
            vibrateEnabled = optVibrate
            hapticMs = optVibrateMs
            soundVolume = optSoundVol
            longPressMs = optLongpressMs.toLong()
            langVi = vietMode
            shifted = shiftOn
            capsLocked = shiftLocked
            onKey = { dispatchKey(it) }
            onSpaceSwipe = { if (optSpaceSwipe) swipeCursor(it) }
            onRecentEmoji = { list ->
                prefs.edit()
                    .putString(SettingsKeys.RECENT_EMOJI, list.joinToString("\n")).apply()
            }
        }
        kbView = kb
        root.findViewById<FrameLayout>(R.id.kb_pages).addView(kb)

        // 3.x: thanh candidate + nền theo cùng palette với phím (sáng/tối)
        // 1.3.2: theme nền gradient thì vẽ GradientDrawable thay màu phẳng.
        val p = kb.palette
        val rootBg = root.findViewById<View>(R.id.kb_root)
        if (p.gradient) {
            rootBg.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(p.bgTop, p.bgBottom)
            )
        } else {
            rootBg.setBackgroundColor(p.bg)
        }
        root.findViewById<View>(R.id.cand_bar).setBackgroundColor(p.bar)
        root.findViewById<View>(R.id.cand_div1).setBackgroundColor(p.divider)
        root.findViewById<View>(R.id.cand_div2).setBackgroundColor(p.divider)
        candidate1?.setTextColor(p.text)
        candidate2?.setTextColor(p.accent)
        candidate3?.setTextColor(p.text)

        updateSuggestions()
        inputView = root
        return root
    }

    /** Điều phối mọi phím từ KeyboardView theo tag. */
    @androidx.annotation.VisibleForTesting
    internal fun dispatchKey(k: KbKey) {
        when {
            k.tag.startsWith("ch:") -> handleCharacter(k.tag.removePrefix("ch:"))
            k.tag.startsWith("p:") -> handlePunct(k.tag.removePrefix("p:"))
            k.tag.startsWith("tx:") -> {
                commitLiteral(k.tag.substring(3))
                // Chọn emoji/phụ xong tự về bàn phím chữ (1.1.1)
                if (kbView?.page == KeyboardView.Page.EMOJI) {
                    kbView?.showPage(KeyboardView.Page.LETTERS)
                }
            }
            k.tag == "fn:space" -> handleSpace()
            k.tag == "fn:enter" -> handleEnter()
            k.tag == "fn:del" -> handleDelete()
            k.tag == "fn:shift" -> toggleShift()
            k.tag == "fn:sym" -> kbView?.showPage(KeyboardView.Page.SYMBOLS)
            k.tag == "fn:sym2" -> kbView?.showPage(KeyboardView.Page.SYMBOLS2)
            k.tag == "fn:abc" -> kbView?.showPage(KeyboardView.Page.LETTERS)
            k.tag == "fn:emoji" -> {
                commitComposing() // chốt từ đang gõ trước khi sang trang emoji
                requestSuggestions()
                kbView?.showPage(KeyboardView.Page.EMOJI)
            }
            k.tag == "fn:lang" -> toggleLang()
            k.tag == "fn:ime" -> { // 3.x; 1.2: không có IME kế -> mở bảng chọn
                // switchToNextInputMethod cần API 28+
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                    !switchToNextInputMethod(false)
                ) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager)?.showInputMethodPicker()
                }
            }
            k.tag == "fn:paste" -> pasteClipboard() // 3.x
        }
    }

    /** Vuốt trên phím cách -> dời con trỏ (3.x). Đang gõ dở thì chốt trước.
     *  1.2: dùng setSelection thay cho phím DPAD — DPAD_RIGHT ở cuối ô (hoặc
     *  LEFT ở đầu ô) khiến app chuyển focus sang view khác và BÀN PHÍM BỊ ẨN
     *  (lỗi "thi thoảng bấm space thì mất bàn phím"). */
    private fun swipeCursor(dir: Int) {
        if (currentComposingWord.isNotEmpty()) commitComposing()
        val ic = currentInputConnection ?: return
        val pos = currentCursor(ic, dir)
        if (pos < 0) return // không biết vị trí -> bỏ qua, tuyệt đối không gửi DPAD
        var step = 0
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            step = 0 // đang bôi chọn: vuốt chỉ thu vùng chọn về một đầu
        } else if (dir > 0) {
            val after = ic.getTextAfterCursor(256, 0) ?: return
            if (after.isEmpty()) return // đã ở cuối ô
            step = firstGraphemeLength(after.toString())
        } else {
            if (pos <= 0) return // đã ở đầu ô
            val before = ic.getTextBeforeCursor(256, 0) ?: return
            if (before.isEmpty()) return
            step = -lastGraphemeLength(before.toString())
        }
        val np = (pos + step).coerceAtLeast(0)
        ic.setSelection(np, np)
        noteCursor(np)
        contextCache = null
        tailTracker.invalidate() // con trỏ dời khỏi vùng đã biết
        updateAutoShift()
    }

    /** Vị trí con trỏ (đầu dời): hỏi app qua ExtractedText (đồng bộ, luôn
     *  đúng kể cả khi onUpdateSelection chưa tới), không được thì dùng vùng
     *  chọn đã theo dõi. -1 = không xác định được. */
    private fun currentCursor(ic: android.view.inputmethod.InputConnection, dir: Int): Int {
        val et = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest().apply { hintMaxChars = 1 }, 0
        )
        if (et != null && et.selectionStart >= 0) {
            selStart = et.startOffset + et.selectionStart
            selEnd = et.startOffset + et.selectionEnd
        }
        if (selStart < 0 || selEnd < 0) return -1
        return if (dir > 0) maxOf(selStart, selEnd) else minOf(selStart, selEnd)
    }

    /** 3.x: phím 📋 dán nội dung clipboard (nếu là text). */
    private fun pasteClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val t = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!t.isNullOrEmpty()) {
                commitComposing()
                currentInputConnection?.let { icCommit(it, t) }
                tailTracker.append(t)
                contextCache = null
                requestSuggestions()
                updateAutoShift()
            }
        }
    }

    /** tx:/emoji/ký tự phụ: commit nguyên văn, không qua engine (3.x). */
    private fun commitLiteral(s: String) {
        if (shiftOn && !shiftLocked) {
            shiftOn = false
            shiftAuto = false
            updateShiftUI()
        }
        commitComposing()
        currentInputConnection?.let { icCommit(it, s) }
        tailTracker.append(s)
        contextCache = null
        requestSuggestions()
        updateAutoShift()
    }

    /** Shift: chạm 1 = hoa một chữ; chạm nhanh lần 2 = caps lock; chạm khi
     *  đang lock = tắt hẳn (3.x). */
    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        when {
            shiftLocked -> {
                shiftOn = false
                shiftLocked = false
            }
            shiftOn && now - lastShiftTap < 400 -> shiftLocked = true
            else -> shiftOn = !shiftOn
        }
        lastShiftTap = now
        shiftAuto = false
        updateShiftUI()
    }

    /** Phím EN/VI: EN gõ thẳng không Telex/gợi ý; VI như thường (2.x). */
    private fun toggleLang() {
        commitComposing()
        vietMode = !vietMode
        kbView?.langVi = vietMode
        requestSuggestions()
    }

    /** "k=v" mỗi dòng -> map gõ tắt; khóa khớp phím thô, không phân biệt hoa. */
    private fun parseMacros(s: String): Map<String, String> =
        com.hkey.app.settings.MacroCodec.parse(s)

    private fun updateShiftUI() {
        kbView?.shifted = shiftOn
        kbView?.capsLocked = shiftLocked
    }

    private fun handleCharacter(char: String, fromHardware: Boolean = false) {
        val t0 = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        lastAutoFix = null // gõ ký tự mới = chấp nhận bản sửa, hết hoàn tác
        // H1: phím cứng mang hoa/thường sẵn trong event — bỏ qua shiftOn
        // phần mềm và không tiêu thụ nó (shift mềm chỉ cho phím trên màn).
        val c = if (shiftOn && !fromHardware) char.uppercase() else char
        if (rawMode || !vietMode) { // EN mode: gõ thẳng, không Telex/gợi ý (2.x)
            if (!fromHardware) consumeShift()
            currentInputConnection?.let { icCommit(it, c) }
            tailTracker.append(c)
            return
        }
        if (!fromHardware) consumeShift() // caps lock (shiftLocked) không tự tắt
        // Con trỏ sát một từ đã gõ (không có space) -> nối phím vào từ đó,
        // kiểu Unikey "bỏ dấu tự do": "hoan" + s -> "hoán", "hon" + w -> "hơn".
        // 1.4.0: chỉ chặn caps lock; shift 1 lần vẫn cho resume.
        if (currentComposingWord.isEmpty() && !shiftLocked && resumeWord(c)) return
        currentComposingWord.append(c)
        val transformed = renderComposing()
        currentInputConnection?.let { icComposing(it, transformed) }
        requestSuggestions()
        if (BuildConfig.DEBUG) Log.d("HKeyIME", "handleCharacter ${(System.nanoTime() - t0) / 1_000} us")
    }

    /** Tắt shift 1 lần sau khi gõ ký tự; caps lock thì giữ nguyên (3.x). */
    private fun consumeShift() {
        if (shiftOn && !shiftLocked) {
            shiftOn = false
            shiftAuto = false
            updateShiftUI()
        }
    }

    /** ~40 ký tự cuối ĐÃ COMMIT trước con trỏ (không gồm vùng composing).
     *  Cache cục bộ — chỉ gọi getTextBeforeCursor khi chưa biết (con trỏ đổi
     *  từ ngoài / ô mới) thay vì mỗi phím (6b). */
    private fun tailNow(): String? =
        tailTracker.tail ?: currentInputConnection
            ?.getTextBeforeCursor(40, 0)?.toString()?.also { tailTracker.seed(it) }

    /** Chuỗi chữ cái liền trước con trỏ (rỗng nếu trước con trỏ là space/số). */
    private fun adjacentWordBeforeCursor(): String {
        val before = tailNow()?.takeLast(20) ?: return ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        return before.substring(i)
    }

    /** Ngữ cảnh không đổi trong lúc gõ một từ — cache để không gọi
     *  getTextBeforeCursor (liên tiến trình) mỗi ký tự (S3). */
    private fun contextWordBeforeCursor(): String = contextPairBeforeCursor().first

    private fun contextPairBeforeCursor(): Pair<String, String> =
        contextCache ?: computeContextPair().also { contextCache = it }

    private fun computeContextPair(): Pair<String, String> {
        val fresh = tailTracker.tail == null
        var before = tailNow()
        if (before == null) {
            tokenGlued = false
            return lastCommittedWord to ""
        }
        // Fetch mới lúc đang composing có thể dính vùng composing — cắt ra để
        // tail chỉ giữ phần đã commit (tail theo dõi sẵn đã sạch).
        if (fresh && currentComposingWord.isNotEmpty()) {
            // 1.4.0: cắt đúng phần đang hiển thị (live restore có thể = phím thô)
            val comp = currentDisplay
            if (comp.isNotEmpty() && before.endsWith(comp)) {
                before = before.dropLast(comp.length)
                tailTracker.seed(before)
            }
        }
        tokenGlued = TextContext.gluedToken(before)
        return TextContext.lastTwo(before, lastCommittedWord)
    }

    /** 'w'/'z' áp thẳng lên từ đã commit; các phím khác kéo từ về vùng
     *  composing (bỏ tone khỏi buffer) rồi gõ tiếp như thường.
     *  1.4.0 (E3): không resume trên token dính url/email, không resume từ
     *  vừa commit dạng phím thô (tiếng Anh — phím mới là chữ mới), và chỉ
     *  resume khi từ kề là âm tiết VN hợp lệ (non-strict). */
    private fun resumeWord(c: String): Boolean {
        val word = adjacentWordBeforeCursor()
        if (word.isEmpty()) return false
        val freshTail = currentInputConnection
            ?.getTextBeforeCursor(word.length, 0)?.toString()
        if (freshTail != word) {
            tailTracker.invalidate()
            contextCache = null
            lastCommittedWord = ""
            lastCommitWasRaw = false
            lastAutoFix = null
            return false
        }
        if (TextContext.gluedToken(tailNow())) return false
        if (word == lastCommittedWord && lastCommitWasRaw) return false
        if (!ViSyllable.isValid(word.lowercase(), strict = false)) return false
        when (c[0]) {
            // 1.3.4: chỉ áp 'w' khi ra âm tiết VN hợp lệ — "sho"+w ra "shơ"
            // (không phải âm tiết) phải rơi về đường buffer để commit trả
            // lại phím thô "show", trước đây áp thẳng kẹt thành "shơ".
            'w' -> engine.applyW(word)
                ?.takeIf { ViSyllable.isValid(it.lowercase()) }
                ?.let { return replaceAdjacentWord(word, it) }
            'z' -> engine.stripTones(word).let {
                if (it != word) return replaceAdjacentWord(word, it)
            }
        }
        // 1.8: giữ dấu sẵn có của từ cũ khi gõ tiếp; chỉ bóc tone khi phím
        // mới chính là phím dấu (gõ 's' sau "việt" -> "viết" vẫn đè tone được).
        val base = if (c[0].lowercaseChar() in "sfrxj") engine.stripTones(word) else word
        currentComposingWord.append(base).append(c)
        val transformed = renderComposing()
        contextCache = null // từ kề vừa vào buffer — ngữ cảnh phải dời lên trước nó
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            icDeleteBack(ic, word.length)
            tailTracker.drop(word.length)
            icComposing(ic, transformed)
        } finally {
            ic.endBatchEdit()
        }
        requestSuggestions()
        return true
    }

    private fun replaceAdjacentWord(old: String, new: String): Boolean {
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            icDeleteBack(ic, old.length)
            tailTracker.drop(old.length)
            icCommit(ic, new)
            tailTracker.append(new)
        } finally {
            ic.endBatchEdit()
        }
        lastCommittedWord = new
        contextCache = null
        requestSuggestions()
        return true
    }

    private fun handlePunct(p: String) {
        // VNI: chữ số trên trang symbols là phím dấu -> vào buffer (2.x)
        if (vietMode && engine is VniEngine && p.length == 1 && p[0].isDigit()) {
            handleCharacter(p)
            return
        }
        commitComposing()
        currentInputConnection?.let { icCommit(it, p) }
        tailTracker.append(p)
        contextCache = null
        requestSuggestions()
        updateAutoShift()
    }

    /** Chốt từ đang gõ; nếu từ sai chính tả và có phương án sửa đủ gần
     *  (lệch đúng 1 ký tự, nằm trong từ điển) thì tự thay bằng từ đúng.
     *  Bản tự sửa được đánh dấu để ⌫/chạm candidate hoàn tác lại chữ đã gõ.
     *  P2: [asyncFix]=true (space) — commit ngay rồi sửa nền; các đường
     *  khác (enter/dấu câu) giữ đồng bộ <=30 ms. */
    private fun commitComposing(asyncFix: Boolean = false) =
        commitWord(true, currentInputConnection, asyncFix)

    /** 1.4.0: chốt từ khi người dùng RỜI từ (chạm chỗ khác/đổi ô/ẩn phím):
     *  áp restore phím thô + macro, KHÔNG auto-correct, có học. */
    private fun finalizeWord(ic: android.view.inputmethod.InputConnection?) =
        commitWord(false, ic)

    private var fixGen = 0 // P2: số hiệu bản sửa nền — lệch thì bỏ

    private fun commitWord(
        allowCorrection: Boolean,
        ic: android.view.inputmethod.InputConnection?,
        asyncFix: Boolean = false
    ) {
        if (currentComposingWord.isEmpty()) return
        fixGen++ // mọi lần chốt mới vô hiệu bản sửa nền đang bay
        val (prev, prev2) = contextPairBeforeCursor()
        val raw = currentComposingWord.toString()
        val typed = engine.transform(raw)
        // 3.5: không sửa từ viết hoa giữa câu (tên riêng); từ có số và từ
        // ngắn đã chặn trong correction().
        val isProperNoun = prev.isNotEmpty() && typed.any { it.isUpperCase() }
        // 1.3: kết quả không phải âm tiết VN (tiếng Anh/mã/URL) -> về phím thô
        val typedWord = if (ViSyllable.restorable(raw, typed)) raw else typed
        // 2.x: macro gõ tắt khớp phím thô -> bung trực tiếp, không qua sửa
        val expanded = macros[raw.lowercase()]
        // 1.1: mảng trong url/email/ip ("io" trong "hu.io.vn") không sửa, không học.
        // 1.4: ô NO_SUGGESTIONS không tự sửa.
        val canFix = allowCorrection && expanded == null && !isProperNoun &&
            !tokenGlued && !noSuggest && optAutoCorrect // U3: AUTO_CORRECT
        // P2: đường async được sửa cả khi chốt phím thô (typed vẫn có glyph
        // để typoFix bắt — "khoogn" hiển thị thô vẫn thành "không"); đường
        // đồng bộ giữ guard cũ typedWord !== raw.
        val fixed = if (!canFix || asyncFix) null
            else computeCorrection(
                typed, prev, prev2, raw, engine, rawOnly = typedWord === raw
            )
            // 1.2: bản sửa trả chữ thường — áp lại kiểu hoa của từ đã gõ
            ?.let { TextContext.matchCase(typed, it) }
        val word = expanded ?: fixed ?: typedWord
        if (ic != null) {
            if (allowCorrection) {
                icCommit(ic, word)
            } else {
                // 1.4.0: ghi từ vào đúng vùng composing gốc bằng
                // setComposingText + finish (không commitText) — không đẩy
                // con trỏ người dùng; không noteCursor. Trùng hiển thị thì
                // chỉ finish như cũ.
                ic.beginBatchEdit()
                try {
                    if (word != currentDisplay) ic.setComposingText(word, 1)
                    ic.finishComposingText()
                } finally {
                    ic.endBatchEdit()
                }
                shownComposingLen = 0
                currentDisplay = ""
            }
        }
        if (allowCorrection) tailTracker.append(word) else tailTracker.invalidate()
        val pendingFix = asyncFix && canFix
        if (!tokenGlued && !pendingFix) learn(prev, word, prev2)
        lastCommittedWord = word
        // 1.4.0: từ chốt là chính phím thô (restorable) -> đánh dấu để
        // resumeWord không kéo tiếng Anh về buffer biến dạng
        lastCommitWasRaw = word === raw
        lastAutoFix = if (fixed != null) AutoFix(word, typed, raw) else null
        currentComposingWord.clear()
        contextCache = null
        if (pendingFix) {
            val gen = fixGen
            val p = prev; val p2 = prev2; val ty = typed
            val eng = engine
            worker.post {
                // P5: từ điển chưa sẵn sàng -> f=null, chỉ học từ đã chốt
                // 1.5.0: chữ ĐÃ COMMIT là từ user xác nhận (vừa hoàn tác ->
                // học "khoogn") thì bỏ fix — cổng này phải so trên typedWord,
                // không phải bản transform "khôgn" (đó là bug sửa oan lặp).
                val rawCands = if (predictorReady &&
                    !predictor.knowsWord(typedWord)
                ) predictor.rawTelexCandidates(raw, eng) else emptyList()
                val f = if (!predictorReady || predictor.knowsWord(typedWord) ||
                    rawCands.size > 1
                ) null
                else (rawCands.singleOrNull()
                    ?: predictor.typoFix(ty, p, p2)
                    ?: predictor.correction(ty, p, p2))
                    ?.takeIf { typedWord !== raw || it.any { c -> c.code > 127 } }
                    ?.let { TextContext.matchCase(ty, it) }
                repeatHandler.post {
                    applyPendingFix(gen, typedWord, raw, f, p, p2)
                }
            }
        }
    }

    /** P2: bản sửa nền sau space — chỉ áp khi gen còn khớp, không có từ
     *  đang gõ mới, và con trỏ vẫn đứng ngay sau "typed + ' '" (user chưa
     *  gõ/xoá gì). Không đủ điều kiện -> bỏ, không sửa lùi giữa câu. */
    private fun applyPendingFix(
        gen: Int, typedWord: String, raw: String,
        fixed: String?, prev: String, prev2: String
    ) {
        val ic = currentInputConnection
        if (gen == fixGen && currentComposingWord.isEmpty() && fixed != null) {
            val before =
                ic?.getTextBeforeCursor(typedWord.length + 1, 0)?.toString()
            if (ic != null && before == "$typedWord ") {
                val tail = typedWord.length + 1
                ic.beginBatchEdit()
                try {
                    icDeleteBack(ic, tail)
                    tailTracker.drop(tail)
                    icCommit(ic, "$fixed ")
                    tailTracker.append("$fixed ")
                } finally {
                    ic.endBatchEdit()
                }
                lastAutoFix = AutoFix(fixed, typedWord, raw)
                lastCommittedWord = fixed
                contextCache = null
                learn(prev, fixed, prev2)
                requestSuggestions()
                return
            }
            tailTracker.invalidate()
            contextCache = null
            return
        }
        // Không áp được -> học từ đã chốt thay (giữ hành vi cũ)
        learn(prev, typedWord, prev2)
    }

    private fun handleSpace() {
        commitComposing(asyncFix = true) // P2: space không chờ sửa
        val ic = currentInputConnection
        val now = android.os.SystemClock.uptimeMillis()
        // 3.x: 2 lần space nhanh sau một từ -> ". " + bật viết hoa đầu câu
        if (optDoubleSpace && now - lastSpaceTap < 600 && ic != null) {
            val before = tailNow()?.takeLast(2) // cache cục bộ, không IPC (6b)
            if (before != null && before.length == 2 &&
                before[0].isLetter() && before[1] == ' '
            ) {
                ic.beginBatchEdit()
                try {
                    icDeleteBack(ic, 1)
                    tailTracker.drop(1)
                    icCommit(ic, ". ")
                    tailTracker.append(". ")
                } finally {
                    ic.endBatchEdit()
                }
                lastSpaceTap = 0L
                contextCache = null
                requestSuggestions()
                updateAutoShift()
                return
            }
        }
        lastSpaceTap = now
        ic?.let { icCommit(it, " ") }
        tailTracker.append(" ")
        contextCache = null
        requestSuggestions()
        updateAutoShift()
    }

    private fun handleDelete() {
        if (rawMode) {
            deleteBackward()
            return
        }
        if (currentComposingWord.isNotEmpty()) {
            // 1.7: xóa 1 ký tự HIỂN THỊ ("việt"⌫="việ"), không phải phím thô cuối
            // 1.4.0: đang live restore (hiển thị = phím thô) -> xoá phím thô cuối
            val raw = currentComposingWord.toString()
            val r = if (optLiveRestore && currentDisplay == raw) raw.dropLast(1)
                else engine.dropLastDisplayChar(raw)
            currentComposingWord.clear()
            currentComposingWord.append(r)
            val transformed = renderComposing()
            if (transformed.isEmpty()) {
                currentInputConnection?.let { icCommit(it, "") }
            } else {
                currentInputConnection?.let { icComposing(it, transformed) }
            }
        } else if (!revertAutoFix()) {
            deleteBackward()
            contextCache = null
            updateAutoShift()
        }
        requestSuggestions()
    }

    /** 1.2: xoá lùi đúng nghĩa —
     *  - đang bôi chọn: xoá cả vùng chọn (trước đây xoá ký tự trước vùng chọn);
     *  - emoji/ký tự ghép: xoá trọn 1 grapheme (trước đây xẻ đôi surrogate
     *    pair để lại ký tự lỗi "�");
     *  - ô TYPE_NULL (terminal): gửi phím DEL thật. */
    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        if (currentInputType == InputType.TYPE_NULL) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            tailTracker.invalidate()
            cursorUnknown()
            return
        }
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            icCommit(ic, "")
            tailTracker.invalidate()
            return
        }
        val n = lastGraphemeLength(tailNow())
        icDeleteBack(ic, n)
        tailTracker.drop(n)
    }

    private var graphemeIt: android.icu.text.BreakIterator? = null

    private fun graphemeIter(): android.icu.text.BreakIterator = graphemeIt
        ?: android.icu.text.BreakIterator.getCharacterInstance().also { graphemeIt = it }

    /** Số UTF-16 unit của grapheme cuối (emoji + FE0F, cờ, chữ + dấu tổ hợp). */
    private fun lastGraphemeLength(t: String?): Int {
        if (t.isNullOrEmpty()) return 1
        return try {
            val bi = graphemeIter()
            bi.setText(t)
            val start = bi.preceding(t.length)
            if (start == android.icu.text.BreakIterator.DONE) 1
            else (t.length - start).coerceIn(1, t.length)
        } catch (e: Exception) {
            if (t.length >= 2 && Character.isLowSurrogate(t.last()) &&
                Character.isHighSurrogate(t[t.length - 2])
            ) 2 else 1
        }
    }

    private fun firstGraphemeLength(t: String): Int {
        if (t.isEmpty()) return 0
        return try {
            val bi = graphemeIter()
            bi.setText(t)
            bi.first()
            val end = bi.next()
            if (end == android.icu.text.BreakIterator.DONE) t.length
            else end.coerceIn(1, t.length)
        } catch (e: Exception) {
            if (t.length >= 2 && Character.isHighSurrogate(t[0]) &&
                Character.isLowSurrogate(t[1])
            ) 2 else 1
        }
    }

    /** Số ký tự trước con trỏ thuộc về từ vừa bị sửa (kể cả space theo sau);
     *  0 = không đứng ngay sau từ đó -> không hoàn tác được. */
    private fun autoFixTail(fix: AutoFix) = autoFixTail(fix.committed)

    private fun autoFixTail(committed: String): Int {
        val before = tailNow()?.takeLast(committed.length + 1) ?: return 0
        return when {
            before.endsWith("$committed ") -> committed.length + 1
            before.endsWith(committed) -> committed.length
            else -> 0
        }
    }

    /** ⌫ ngay sau auto-correct = hoàn tác: xóa từ đã sửa (và space vừa gõ),
     *  trả buffer phím thô về vùng composing để user tự sửa tiếp. Từ đã gõ
     *  được học vào từ điển nên app không sửa lại lần sau. */
    private fun revertAutoFix(): Boolean {
        val fix = lastAutoFix ?: return false
        lastAutoFix = null
        val tail = autoFixTail(fix)
        if (tail == 0) return false
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            icDeleteBack(ic, tail)
            tailTracker.drop(tail)
            currentComposingWord.append(fix.raw)
            icComposing(ic, renderComposing())
        } finally {
            ic.endBatchEdit()
        }
        contextCache = null
        // 1.5.0: hoàn tác = xác nhận chủ động — học bắt buộc kể cả khi từ
        // "trông như typo", không thì lần gõ sau bị sửa oan y hệt.
        contextPairBeforeCursor().let { learn(it.first, fix.typed, it.second, force = true) }
        return true
    }

    /** Chạm candidate "từ đã gõ" ngay sau auto-correct = đổi lại nguyên văn
     *  (giữ space sau từ), học từ đó để không sửa oan nữa. */
    private fun revertCommittedFix() {
        val fix = lastAutoFix ?: return
        lastAutoFix = null
        val tail = autoFixTail(fix)
        if (tail == 0) return
        val ic = currentInputConnection ?: return
        val reverted = if (tail > fix.committed.length) fix.typed + " " else fix.typed
        ic.beginBatchEdit()
        try {
            icDeleteBack(ic, tail)
            tailTracker.drop(tail)
            icCommit(ic, reverted)
            tailTracker.append(reverted)
        } finally {
            ic.endBatchEdit()
        }
        contextCache = null
        contextPairBeforeCursor().let { learn(it.first, fix.typed, it.second, force = true) }
        lastCommittedWord = fix.typed
        requestSuggestions()
    }

    private fun handleEnter() {
        fixGen++
        commitComposing()
        val ic = currentInputConnection ?: return
        // 1.5: ô đặt action (send/search/go/done) -> gọi action app.
        // 1.2: action hiệu lực tính chung với icon (enterActionOf): ô nhiều
        // dòng có DONE mặc định -> xuống dòng thay vì đóng bàn phím.
        val action = enterActionOf(currentInputType, currentImeOptions)
        if (action != EditorInfo.IME_ACTION_NONE && ic.performEditorAction(action)) {
            contextCache = null
            tailTracker.invalidate() // app tự xử lý action — text đổi ngoài tầm
            cursorUnknown()
            updateAutoShift()
            tailTracker.invalidate()
            contextCache = null
            lastAutoFix = null
            lastCommittedWord = ""
            lastCommitWasRaw = false
            clearStaleCandidates()
            return
        }
        if (currentInputType != InputType.TYPE_NULL && isMultiLine(currentInputType)) {
            // 1.2: ô nhiều dòng -> chèn "\n" trực tiếp. Không mô phỏng phím
            // ENTER: KeyEvent thiếu cờ FLAG_SOFT_KEYBOARD/KEEP_TOUCH_MODE làm
            // app thoát touch-mode / chuyển focus -> bàn phím bị ẩn.
            icCommit(ic, "\n")
            tailTracker.append("\n")
            contextCache = null
            updateAutoShift()
            clearStaleCandidates()
            return
        }
        // ô một dòng không action / terminal (TYPE_NULL): phím ENTER thật,
        // gửi qua sendDownUpKeyEvents để có đủ cờ bàn phím mềm.
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        tailTracker.invalidate() // app có thể đã thay đổi — đọc lại lần sau
        cursorUnknown()
        contextCache = null
        updateAutoShift()
        tailTracker.invalidate()
        contextCache = null
        lastAutoFix = null
        lastCommittedWord = ""
        lastCommitWasRaw = false
        clearStaleCandidates()
    }

    /** 1.5.1: ENTER/xuống dòng xoá ứng viên dòng cũ ngay (đồng bộ, không chờ
     *  frame sau) rồi mới request làm mới — trước đây thiếu bước này nên
     *  thanh giữ nguyên từ cũ khi user bắt đầu gõ dòng mới. */
    private fun clearStaleCandidates() {
        suggestGen++ // vô hiệu mọi request gợi ý đang bay
        applyCandidates(CandidateSet("", "", ""))
        requestSuggestions()
    }

    /** Đang hiện phương án hoàn tác auto-fix thì chạm candidate = hoàn tác. */
    private fun onCandidateTap(tv: TextView?) {
        if (tv == null) return
        val fix = lastAutoFix
        if (fix != null && tv === candidate2 && tv.text == fix.typed) {
            revertCommittedFix()
        } else {
            acceptSuggestion(tv.text.toString())
        }
    }

    private fun acceptSuggestion(word: String) {
        if (word.isEmpty()) return
        lastAutoFix = null
        val ctxPair = contextPairBeforeCursor()
        // 1.2: gợi ý trả chữ thường — áp lại kiểu hoa đang gõ / shift đầu câu
        val cased = when {
            // 1.4.0: áp kiểu hoa theo đúng chữ trên màn hình (live restore = phím thô)
            currentComposingWord.isNotEmpty() ->
                TextContext.matchCase(currentDisplay, word)
            shiftOn -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
        // commitText tự thay thế vùng composing nếu đang gõ dở
        currentInputConnection?.let { icCommit(it, "$cased ") }
        tailTracker.append("$cased ")
        // 1.5.1: chạm ứng viên = xác nhận chủ động (như hoàn tác auto-fix)
        // — force để ô phím thô "max" trông-như-typo vẫn được học.
        learn(ctxPair.first, word, ctxPair.second, force = true)
        lastCommittedWord = cased
        currentComposingWord.clear()
        consumeShift()
        contextCache = null
        requestSuggestions()
        updateAutoShift()
    }

    private var suggPending = false

    /** 6e: gom updateSuggestions về một lần mỗi frame khi gõ nhanh — nhiều
     *  phím trong cùng frame chỉ tính gợi ý 1 lần. */
    private fun requestSuggestions() {
        suggestGen++
        showPendingCandidates()
        val v = kbView
        if (v == null) {
            updateSuggestions()
            return
        }
        if (suggPending) return
        suggPending = true
        v.postOnAnimation {
            suggPending = false
            updateSuggestions()
        }
    }

    private fun showPendingCandidates() {
        if (rawMode || noSuggest || !optSuggestions || !vietMode) {
            applyCandidates(CandidateSet("", "", ""))
            return
        }
        if (currentComposingWord.isNotEmpty()) {
            applyCandidates(CandidateSet("", currentDisplay, ""))
            return
        }
        val fix = lastAutoFix
        if (fix != null && autoFixTail(fix) > 0) {
            applyCandidates(CandidateSet("", fix.typed, ""))
        } else {
            applyCandidates(CandidateSet("", "", ""))
        }
    }

    /** P1: đầu vào gợi ý bất biến, chụp trên main rồi chuyển sang worker. */
    internal data class SuggestRequest(
        val gen: Int,
        val current: String, // chữ đang ở vùng composing (sau live restore)
        val restore: String?, // bản đề nghị đã tính sẵn (VN khi live restore / raw)
        val ctx: String,
        val ctx2: String,
        val tokenGlued: Boolean,
        val raw: String = current, // 1.5.1: buffer phím thô — ô cuối là lối thoát
        val engine: ImeEngine? = null
    )

    internal data class CandidateSet(val c1: String, val c2: String, val c3: String)

    /** Tính 3 ô gợi ý khi đang composing — thuần, chỉ gọi predictor; chạy
     *  trên worker. Không đụng InputConnection/UI. */
    internal fun computeCandidates(req: SuggestRequest): CandidateSet {
        // 1.1: mảng trong url/email/ip -> không gợi ý, không sửa
        // 1.5.0: lấy dư ứng viên (limit 4) để lọc trùng với ô giữa vẫn
        // còn đủ 2 ô bên — trước đây hai bên lấy thẳng comp[0]/comp[1]
        // nên đôi khi trùng với bản sửa ("duoc" -> cả 3 ô đều "được"-ish).
        val completions = if (req.tokenGlued) emptyList()
            else predictor.completions(req.current, req.ctx, req.ctx2, limit = 4)
        // 3.4: chỉ đề nghị sửa khi từ đang gõ không phải tiền tố hợp lệ
        val rawCands = if (!req.tokenGlued && req.engine != null)
            predictor.rawTelexCandidates(req.raw, req.engine)
                .map { TextContext.matchCase(req.current, it) }
        else emptyList()
        val fix = when {
            rawCands.size > 1 -> null
            req.restore != null &&
                (ViSyllable.isValid(req.restore.lowercase()) ||
                    rawCands.isEmpty()) -> req.restore
            rawCands.size == 1 -> rawCands[0]
            req.tokenGlued || predictor.isPrefixOfKnownWord(req.current) -> null
            else -> predictor.typoFix(req.current, req.ctx, req.ctx2)
                ?: predictor.correction(req.current, req.ctx, req.ctx2)
        }
        val fixCased = fix?.let { TextContext.matchCase(req.current, it) }
        // 1.5.1: transform bẻ phím thô ("max"->"mã", "ddc"->"đc") -> ô cuối
        // luôn là chữ đã gõ để user chọn giữ nguyên; chạm = chốt + học.
        val rawSide = req.raw.takeIf { it != req.current && it != fixCased }
        val sides = (completions.asSequence()
            .map { TextContext.matchCase(req.current, it) } + rawCands)
            .filter { it != fixCased && it != req.current && it != rawSide }
            .distinct()
            .take(2)
            .toList()
        // 1.2: hiện gợi ý đúng kiểu hoa để chạm vào ăn ngay
        return CandidateSet(
            sides.getOrNull(0) ?: "",
            fixCased ?: req.current,
            rawSide ?: sides.getOrNull(1) ?: ""
        )
    }

    private fun applyCandidates(c: CandidateSet) {
        candidate1?.text = c.c1
        candidate2?.text = c.c2
        candidate3?.text = c.c3
    }

    /** P1/P2: correction+typoFix chạy trên worker; main chờ tối đa 30 ms
     *  (đường đồng bộ cho Enter/dấu câu — quá hạn thì không sửa). */
    internal fun computeCorrection(
        typed: String, prev: String, prev2: String,
        raw: String = typed, eng: ImeEngine? = null, rawOnly: Boolean = false
    ): String? {
        if (!predictorReady) return null // P5: chưa có chỉ mục -> không sửa
        val task = java.util.concurrent.FutureTask<String?> {
            // 1.4.2: typoFix trước — lỗi đảo/chèn ký tự là bằng chứng mạnh
            // hơn repair-xoá của correction ("khôgn" -> "không", không phải
            // "khôn"); correction bắt lệch dấu/phím thừa còn lại.
            val rawCands = if (eng != null)
                predictor.rawTelexCandidates(raw, eng) else emptyList()
            if (rawCands.size > 1) null
            else if (rawCands.size == 1) rawCands[0]
            else if (rawOnly) null
            else predictor.typoFix(typed, prev, prev2)
                ?: predictor.correction(typed, prev, prev2)
        }
        worker.post(task)
        return try {
            task.get(30, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            task.cancel(false)
            null
        } catch (e: InterruptedException) {
            task.cancel(false)
            Thread.currentThread().interrupt()
            null
        } catch (e: java.util.concurrent.ExecutionException) {
            null
        }
    }

    private fun updateSuggestions() {
        if (rawMode || noSuggest || !optSuggestions || !vietMode) {
            suggestGen++ // hủy mọi request đang bay
            applyCandidates(CandidateSet("", "", ""))
            return
        }
        // P5: từ điển chưa sẵn sàng -> ô giữa = chữ đang gõ, hai bên trống;
        // không gọi predictor trước khi installIndex xong.
        if (!predictorReady) {
            suggestGen++
            applyCandidates(CandidateSet("", currentDisplay, ""))
            return
        }
        val (ctx, ctx2) = contextPairBeforeCursor()
        if (currentComposingWord.isNotEmpty()) {
            // Đang gõ: giữa = bản sửa (nếu sai chính tả) hoặc từ hiện tại,
            // 2 bên = gợi ý hoàn thành (ưu tiên từ hay đi sau từ trước)
            // 1.4.0: "current" là chữ trên màn hình — live restore thì = phím thô
            val raw = currentComposingWord.toString()
            val transformed = engine.transform(raw)
            val current = currentDisplay.ifEmpty { transformed }
            val liveRestored = optLiveRestore && !tokenGlued &&
                ViSyllable.liveRestorable(raw, transformed) && current == raw
            // 1.4.0: đang live restore -> ô giữa đề nghị bản VN (chạm = chốt VN
            // + học); còn lại giữ 1.3: kết quả không phải âm tiết VN -> đề
            // nghị phím thô ("text")
            val restore = when {
                tokenGlued -> null
                liveRestored -> transformed
                else -> raw.takeIf { ViSyllable.restorable(it, current) }
            }
            val req = SuggestRequest(
                ++suggestGen, current, restore, ctx, ctx2, tokenGlued, raw, engine
            )
            worker.postLatest(Runnable {
                if (req.gen != suggestGen || destroyed) return@Runnable
                val c = computeCandidates(req)
                repeatHandler.post {
                    if (req.gen == suggestGen && !destroyed) applyCandidates(c)
                }
            })
        } else {
            // Từ vừa bị auto-correct: ô giữa hiện đúng từ user đã gõ,
            // chạm vào để khôi phục (hoặc bấm ⌫).
            val fix = lastAutoFix
            if (fix != null && autoFixTail(fix) > 0) {
                suggestGen++
                applyCandidates(CandidateSet("", fix.typed, ""))
                return
            }
            // 1.1: đang đứng giữa url/email/ip -> không gợi ý từ tiếp theo
            if (tokenGlued) {
                suggestGen++
                applyCandidates(CandidateSet("", "", ""))
                return
            }
            // Đã chốt từ: gợi ý từ tiếp theo theo ngữ cảnh 2 từ (3.3);
            // shift đang bật (đầu câu) thì hiện hoa luôn (1.2)
            val gen = ++suggestGen
            val up = shiftOn
            worker.postLatest(Runnable {
                if (gen != suggestGen || destroyed) return@Runnable
                val next = predictor.predictNext(ctx, ctx2)
                repeatHandler.post {
                    if (gen != suggestGen || destroyed) return@post
                    val l = next.map { if (up) it.replaceFirstChar(Char::uppercase) else it }
                    applyCandidates(
                        CandidateSet(l.getOrNull(1) ?: "", l.getOrNull(0) ?: "", l.getOrNull(2) ?: "")
                    )
                }
            })
        }
    }

    companion object {
        private val SYNC_WORKER = SuggestWorker.synchronous()

        /** Test inject poster worker trước khi service onCreate (hàng đợi
         *  tay/đồng bộ); production giữ null -> HandlerThread. */
        @androidx.annotation.VisibleForTesting
        var workerPosterOverride: ((Runnable) -> Unit)? = null
    }
}
