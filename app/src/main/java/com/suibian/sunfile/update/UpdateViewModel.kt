package com.suibian.sunfile.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suibian.sunfile.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Checking(val silent: Boolean = false) : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(
        val info: ReleaseInfo,
        val progress: Float,
        val downloaded: Long,
        val total: Long,
        val speed: Long,
        val route: String,
        val attempt: Int,
        val routeCount: Int
    ) : UpdateState

    data class Ready(
        val info: ReleaseInfo,
        val apk: File,
        val needPermission: Boolean
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state


    fun check(silent: Boolean = false, useAccelerator: Boolean = true) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = UpdateState.Checking(silent)
            val result = runCatching { GithubApi.latestRelease() }
            result.onSuccess { info ->
                _state.value = when {
                    info == null -> if (silent) UpdateState.Idle else UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    GithubApi.isNewer(info.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(info)
                    else -> if (silent) UpdateState.Idle else UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                }
            }.onFailure { e ->
                _state.value = if (silent) UpdateState.Idle
                else UpdateState.Error(e.message ?: "检查更新失败，请检查网络")
            }
        }
    }

    fun download(info: ReleaseInfo, useAccelerator: Boolean) {
        if (_state.value is UpdateState.Downloading) return
        viewModelScope.launch {
            val downloader = ApkDownloader(getApplication())
            _state.value = UpdateState.Downloading(
                info = info,
                progress = 0f,
                downloaded = 0,
                total = info.size,
                speed = 0,
                route = "正在连接…",
                attempt = 1,
                routeCount = if (useAccelerator) 5 else 1
            )
            runCatching {
                downloader.download(
                    info = info,
                    useAccelerator = useAccelerator,
                    onStart = { index, count, label ->
                        _state.value = UpdateState.Downloading(
                            info = info,
                            progress = 0f,
                            downloaded = 0,
                            total = info.size,
                            speed = 0,
                            route = label,
                            attempt = index + 1,
                            routeCount = count
                        )
                    },
                    onProgress = { downloaded, total, speed ->
                        val cur = _state.value
                        if (cur is UpdateState.Downloading) {
                            _state.value = cur.copy(
                                progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                downloaded = downloaded,
                                total = total,
                                speed = speed
                            )
                        }
                    }
                )
            }.onSuccess { apk ->
                val needPermission = !Installer.canInstall(getApplication())
                _state.value = UpdateState.Ready(info, apk, needPermission)
                if (!needPermission) {
                    Installer.install(getApplication(), apk)
                }
            }.onFailure { e ->
                _state.value = UpdateState.Error(e.message ?: "下载失败，请稍后重试")
            }
        }
    }

    /** 已下载完成后手动触发安装 */
    fun install(apk: File) {
        val ok = Installer.install(getApplication(), apk)
        val cur = _state.value
        if (cur is UpdateState.Ready) {
            _state.value = cur.copy(needPermission = !ok)
        }
    }

    fun openPermissionSettings() {
        Installer.openInstallPermission(getApplication())
    }

    fun dismiss() {
        val cur = _state.value
        if (cur is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }
}
