package com.hkey.app.update

import android.app.Application
import android.content.ActivityNotFoundException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hkey.app.BuildConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateViewModel @JvmOverloads constructor(
    app: Application,
    private val fetchJson: (String) -> String = { AppUpdater.fetchText(it) },
    private val openStream: StreamOpener = { AppUpdater.openHttps(it) }
) : AndroidViewModel(app) {

    enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, ERROR, DOWNLOADING, READY }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val release: Release? = null,
        val error: String? = null,
        val downloadedBytes: Long = 0,
        val apk: File? = null,
        val needPermission: Boolean = false,
        val dismissed: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var dlJob: Job? = null

    init {
        check()
    }

    fun check() {
        val st = _state.value
        if (checkJob?.isActive == true || dlJob?.isActive == true) return
        if (st.phase == Phase.READY) {
            if (st.apk?.exists() == true) return
            _state.update { it.copy(apk = null, phase = Phase.AVAILABLE) }
        }
        checkJob = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.CHECKING, error = null) }
            try {
                val json = withContext(Dispatchers.IO) {
                    fetchJson(AppUpdater.LATEST_URL)
                }
                val rel = AppUpdater.parseRelease(json)
                val newer = rel != null &&
                    AppUpdater.compareVersions(rel.version, BuildConfig.VERSION_NAME) > 0
                _state.update {
                    it.copy(
                        phase = when {
                            rel == null -> Phase.ERROR
                            newer -> Phase.AVAILABLE
                            else -> Phase.UP_TO_DATE
                        },
                        release = rel,
                        error = if (rel == null) "Không đọc được thông tin bản mới" else null,
                        dismissed = if (newer) it.dismissed else false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = Phase.ERROR, error = "Không kiểm tra được cập nhật")
                }
            }
        }
    }

    fun download() {
        val st = _state.value
        if (st.phase != Phase.AVAILABLE || st.release == null) return
        if (dlJob?.isActive == true || checkJob?.isActive == true) return
        if (st.apk != null) return
        val rel = st.release
        dlJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = Phase.DOWNLOADING, downloadedBytes = 0, error = null)
            }
            val dest = File(
                AppUpdater.updateDir(getApplication()), "HKey-${rel.version}.apk"
            )
            try {
                val f = AppUpdater.download(
                    rel.apkUrl, rel.apkSize, rel.sha256, dest, openStream
                ) { n -> _state.update { it.copy(downloadedBytes = n) } }
                _state.update {
                    it.copy(phase = Phase.READY, apk = f, needPermission = false)
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = Phase.AVAILABLE, downloadedBytes = 0) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = Phase.AVAILABLE,
                        downloadedBytes = 0,
                        error = "Tải về thất bại"
                    )
                }
            }
        }
    }

    fun install() {
        val ctx = getApplication<Application>()
        val apk = _state.value.apk ?: return
        if (!apk.exists()) {
            _state.update { it.copy(apk = null, phase = Phase.AVAILABLE) }
            return
        }
        try {
            if (!AppUpdater.canInstall(ctx)) {
                ctx.startActivity(AppUpdater.manageUnknownSourcesIntent(ctx))
                _state.update { it.copy(needPermission = true) }
                return
            }
            ctx.startActivity(AppUpdater.installIntent(ctx, apk))
            _state.update { it.copy(needPermission = false) }
        } catch (e: ActivityNotFoundException) {
            _state.update { it.copy(error = "Không mở được trình cài đặt") }
        } catch (e: SecurityException) {
            _state.update { it.copy(error = "Thiếu quyền cài đặt ứng dụng") }
        }
    }

    fun dismiss() {
        _state.update { it.copy(dismissed = true) }
    }
}
