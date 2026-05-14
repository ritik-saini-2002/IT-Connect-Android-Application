package com.saini.ritik.contact

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.appupdate.AppUpdateChecker
import com.saini.ritik.appupdate.AppUpdateManager
import com.saini.ritik.appupdate.UpdateInfo
import com.saini.ritik.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker : AppUpdateChecker,
    private val updateManager : AppUpdateManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateCheckState.Checking
            val session = authRepository.getSession()
            if (session == null) {
                _updateState.value = UpdateCheckState.Error("Not signed in")
                return@launch
            }
            try {
                val info = updateChecker.checkForUpdate(
                    currentVersionName = com.saini.ritik.BuildConfig.VERSION_NAME,
                    userToken          = session.token
                )
                _updateState.value = if (info == null) UpdateCheckState.UpToDate
                else UpdateCheckState.UpdateAvailable(info)
            } catch (e: Exception) {
                _updateState.value = UpdateCheckState.Error(
                    "Could not reach server: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun downloadAndInstall(
        info   : UpdateInfo,
        onError: (String) -> Unit
    ) {
        val session = authRepository.getSession() ?: run {
            onError("Not signed in"); return
        }
        _updateState.value = UpdateCheckState.Downloading(0f)
        updateManager.downloadAndInstall(
            url        = info.downloadUrl,
            userToken  = session.token,
            onProgress = { progress ->
                _updateState.value = UpdateCheckState.Downloading(progress)
            },
            onError = { msg ->
                _updateState.value = UpdateCheckState.Error(msg)
                onError(msg)
            }
        )
    }
}