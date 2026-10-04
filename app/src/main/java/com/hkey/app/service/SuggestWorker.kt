package com.hkey.app.service

import android.os.Handler
import android.os.HandlerThread

/** 1.4.0 (P1/C1): hàng đợi duy nhất cho mọi thao tác ContextPredictor —
 *  không luồng nào khác được động vào predictor. Mặc định là
 *  HandlerThread("hkey-suggest"); test inject poster đồng bộ/hàng đợi tay
 *  qua [HKeyIME.workerPosterOverride]. */
class SuggestWorker(
    private val poster: (Runnable) -> Unit,
    private val quitter: () -> Unit
) {
    fun post(r: Runnable) = poster(r)
    fun quitSafely() = quitter()

    companion object {
        fun handlerThread(name: String = "hkey-suggest"): SuggestWorker {
            val t = HandlerThread(name).apply { start() }
            val h = Handler(t.looper)
            return SuggestWorker({ h.post(it) }, { t.quitSafely() })
        }

        /** Poster chạy ngay trên luồng gọi — dùng khi worker chưa sẵn sàng
         *  (trước onCreate) hoặc trong test đơn giản. */
        fun synchronous() = SuggestWorker({ it.run() }, {})
    }
}
